package com.mnatool.yunjutongprobe;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class UdpProbeRunner implements ProbeRunner {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, ProbeSample> samples = new ConcurrentHashMap<>();
    private final AtomicInteger highestReceivedSeq = new AtomicInteger(-1);
    private volatile DatagramSocket socket;
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
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
    }

    private void runInternal(ProbeConfig config, ProbeCallback callback) {
        long timeoutNs = config.timeoutMs * 1_000_000L;
        Thread receiver = null;
        Throwable failure = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(100);
            InetAddress address = InetAddress.getByName(config.host);
            callback.onEvent("UDP socket 已创建，本地端口 " + socket.getLocalPort());

            receiver = new Thread(() -> receiveLoop(callback), "udp-probe-receiver");
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
                byte[] payload = ProbePayloadCodec.buildPayload(config, sentSeq, sendNs, sendMs, vpnActive);
                boolean compactPayload = ProbePayloadCodec.usesCompactPayload(config);
                ProbeSample sample = new ProbeSample(
                        config.runId,
                        sentSeq,
                        sendNs,
                        sendMs,
                        payload.length,
                        vpnActive,
                        compactPayload
                );
                samples.put(sentSeq, sample);
                socket.send(new DatagramPacket(payload, payload.length, address, config.port));
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
                if (snapshotSamples().size() >= config.count && snapshot(timeoutNs, false).received >= config.count) {
                    break;
                }
                sleepNs(100_000_000L);
            }
        } catch (Exception exc) {
            if (running.get()) {
                failure = exc;
                callback.onEvent("UDP 失败: [" + exc.getClass().getSimpleName() + "] " + exc.getMessage());
            }
        } finally {
            running.set(false);
            DatagramSocket current = socket;
            if (current != null && !current.isClosed()) {
                current.close();
            }
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

    private void receiveLoop(ProbeCallback callback) {
        byte[] buffer = new byte[65507];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                long recvNs = System.nanoTime();
                long recvMs = System.currentTimeMillis();
                String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String runId = currentRunId();
                ProbePayloadCodec.ParsedEcho echo = ProbePayloadCodec.parseEchoPayload(text, runId);
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
            } catch (Exception exc) {
                if (running.get()) {
                    callback.onEvent("接收异常: " + exc.getMessage());
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
