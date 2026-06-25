package com.mnatool.yunjutongprobe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class TcpProbeRunner implements ProbeRunner {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, ProbeSample> samples = new ConcurrentHashMap<>();
    private final AtomicInteger highestReceivedSeq = new AtomicInteger(-1);
    private volatile Socket socket;
    private volatile ProbeRecvStats activeRecvStats;

    @Override
    public void start(ProbeConfig config, ProbeCallback callback) {
        if (!running.compareAndSet(false, true)) {
            callback.onEvent("已有测试在运行");
            return;
        }
        samples.clear();
        highestReceivedSeq.set(-1);
        activeRecvStats = null;
        executor.execute(() -> runInternal(config, callback));
    }

    @Override
    public void stop() {
        running.set(false);
        closeSocket();
    }

    private void runInternal(ProbeConfig config, ProbeCallback callback) {
        long timeoutNs = config.timeoutMs * 1_000_000L;
        Thread receiver = null;
        Throwable failure = null;
        try {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(config.host, config.port), config.timeoutMs);
            socket.setSoTimeout(100);
            callback.onEvent("TCP 已连接，本地端口 " + socket.getLocalPort());

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            receiver = new Thread(() -> receiveLoop(reader, callback), "tcp-probe-receiver");
            receiver.start();

            long intervalNs = 1_000_000_000L / Math.max(1, config.pps);
            long nextNs = System.nanoTime();
            long lastMetricsNs = 0;
            ProbePerfStats perf = new ProbePerfStats(config.pps);
            ProbeRecvStats recvStats = new ProbeRecvStats();
            activeRecvStats = recvStats;
            int sentSeq = 0;
            for (sentSeq = 0; sentSeq < config.count && running.get(); sentSeq++) {
                long nowNs = System.nanoTime();
                nextNs = ProbeSendScheduler.capCatchUp(nowNs, nextNs, intervalNs, recvStats);
                if (nowNs < nextNs) {
                    sleepNs(nextNs - nowNs);
                }
                if (recvStats.checkStall(System.nanoTime(), true, sentSeq, callback)) {
                    recvStats.markPublishStoppedEarly(sentSeq);
                    callback.onEvent("收包停滞，提前结束发包（已发 " + sentSeq + " / " + config.count + "）");
                    break;
                }

                boolean vpnActive = config.vpnActiveAtStart;
                long sendNs = System.nanoTime();
                perf.beforeSend(sendNs, nextNs);
                long sendMs = System.currentTimeMillis();
                String line = ProbePayloadCodec.buildLine(config, sentSeq, sendNs, sendMs, vpnActive);
                boolean compactPayload = ProbePayloadCodec.usesCompactPayload(config);
                ProbeSample sample = new ProbeSample(
                        config.runId,
                        sentSeq,
                        sendNs,
                        sendMs,
                        line.getBytes(StandardCharsets.UTF_8).length,
                        vpnActive,
                        compactPayload
                );
                samples.put(sentSeq, sample);
                writer.write(line);
                writer.flush();
                perf.afterSend();
                nextNs += intervalNs;

                long metricsNow = System.nanoTime();
                recvStats.checkStall(metricsNow, true, sentSeq, callback);
                if (metricsNow - lastMetricsNs >= 1_000_000_000L) {
                    callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
                    lastMetricsNs = metricsNow;
                }
            }
            perf.markEnd(System.nanoTime());
            if (perf.belowTarget()) {
                callback.onEvent(perf.warningText());
            }
            callback.onPerfStats(perf);
            callback.onRecvStats(recvStats);

            long waitUntilNs = System.nanoTime() + timeoutNs;
            while (running.get() && System.nanoTime() < waitUntilNs) {
                recvStats.checkStall(System.nanoTime(), false, sentSeq, callback);
                callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
                if (snapshot(timeoutNs, false).received >= config.count) {
                    break;
                }
                sleepNs(100_000_000L);
            }
        } catch (Exception exc) {
            if (running.get()) {
                failure = exc;
                callback.onEvent("TCP 失败: [" + exc.getClass().getSimpleName() + "] " + exc.getMessage());
            }
        } finally {
            running.set(false);
            closeSocket();
            if (receiver != null) {
                try {
                    receiver.join(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            ProbeMetrics finalMetrics = snapshot(timeoutNs, true);
            List<ProbeSample> finalSamples = snapshotSamples();
            callback.onMetrics(finalMetrics, finalSamples);
            if (failure == null) {
                callback.onFinished(finalMetrics, finalSamples);
            } else {
                callback.onFailed(failure, finalMetrics, finalSamples);
            }
            callback.onEvent("测试结束");
            executor.shutdown();
        }
    }

    private void receiveLoop(BufferedReader reader, ProbeCallback callback) {
        while (running.get()) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    callback.onEvent("TCP 连接已关闭");
                    running.set(false);
                    break;
                }
                long recvNs = System.nanoTime();
                long recvMs = System.currentTimeMillis();
                ProbePayloadCodec.ParsedEcho echo = ProbePayloadCodec.parseEchoPayload(line, currentRunId());
                if (echo == null) {
                    continue;
                }
                int seq = echo.seq;
                ProbeSample sample = samples.get(seq);
                if (sample == null) {
                    continue;
                }
                if (sample.clientRecvNs > 0) {
                    sample.duplicate = true;
                    continue;
                }
                sample.clientRecvNs = recvNs;
                sample.clientRecvMs = recvMs;
                if (!echo.compact && echo.jsonAck != null) {
                    ProbeSegmentTiming.applyEchoTimestamps(sample, echo.jsonAck, recvMs);
                }
                int previousHigh = highestReceivedSeq.getAndUpdate(old -> Math.max(old, seq));
                sample.reordered = previousHigh > seq;
                ProbeRecvStats stats = activeRecvStats;
                if (stats != null) {
                    stats.onRecvAdvance(seq, recvNs);
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // Continue so stop() can close the socket quickly.
            } catch (Exception exc) {
                if (running.get()) {
                    callback.onEvent("TCP 接收异常: " + exc.getMessage());
                }
            }
        }
    }

    private String currentRunId() {
        for (ProbeSample sample : samples.values()) {
            return sample.runId;
        }
        return "";
    }

    private ProbeMetrics snapshot(long timeoutNs, boolean finalResult) {
        return MetricsCalculator.calculate(snapshotSamples(), System.nanoTime(), timeoutNs, finalResult);
    }

    private List<ProbeSample> snapshotSamples() {
        return new ArrayList<>(samples.values());
    }

    private void closeSocket() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void sleepNs(long ns) {
        if (ns <= 0) {
            return;
        }
        long ms = ns / 1_000_000L;
        int extraNs = (int) (ns % 1_000_000L);
        try {
            Thread.sleep(ms, extraNs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
