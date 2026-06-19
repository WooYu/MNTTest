package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

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

    @Override
    public void start(ProbeConfig config, ProbeCallback callback) {
        if (!running.compareAndSet(false, true)) {
            callback.onEvent("已有测试在运行");
            return;
        }
        samples.clear();
        highestReceivedSeq.set(-1);
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
            for (int seq = 0; seq < config.count && running.get(); seq++) {
                long nowNs = System.nanoTime();
                if (nowNs < nextNs) {
                    sleepNs(nextNs - nowNs);
                }

                boolean vpnActive = config.vpnActiveAtStart;
                long sendNs = System.nanoTime();
                byte[] payload = ProbePayloadCodec.buildPayload(config, seq, sendNs, System.currentTimeMillis(), vpnActive);
                ProbeSample sample = new ProbeSample(
                        config.runId,
                        seq,
                        sendNs,
                        System.currentTimeMillis(),
                        payload.length,
                        vpnActive
                );
                samples.put(seq, sample);
                socket.send(new DatagramPacket(payload, payload.length, address, config.port));
                nextNs += intervalNs;

                long metricsNow = System.nanoTime();
                if (metricsNow - lastMetricsNs >= 250_000_000L) {
                    callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
                    lastMetricsNs = metricsNow;
                }
            }

            long waitUntilNs = System.nanoTime() + timeoutNs;
            while (running.get() && System.nanoTime() < waitUntilNs) {
                callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
                if (snapshotSamples().size() >= config.count && snapshot(timeoutNs, false).received >= config.count) {
                    break;
                }
                sleepNs(100_000_000L);
            }
        } catch (Exception exc) {
            callback.onEvent("测试异常: " + exc.getMessage());
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
            callback.onMetrics(finalMetrics, snapshotSamples());
            callback.onFinished(finalMetrics, snapshotSamples());
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
                String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                JSONObject ack = new JSONObject(text);
                int seq = ack.optInt("seq", -1);
                ProbeSample sample = samples.get(seq);
                if (sample == null) {
                    continue;
                }
                if (sample.clientRecvNs > 0) {
                    sample.duplicate = true;
                    continue;
                }
                sample.clientRecvNs = recvNs;
                sample.serverRecvNs = ack.optLong("serverRecvNs", 0);
                sample.serverSendNs = ack.optLong("serverSendNs", 0);
                int previousHigh = highestReceivedSeq.getAndUpdate(old -> Math.max(old, seq));
                sample.reordered = previousHigh > seq;
            } catch (Exception exc) {
                if (running.get()) {
                    callback.onEvent("接收异常: " + exc.getMessage());
                }
            }
        }
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
