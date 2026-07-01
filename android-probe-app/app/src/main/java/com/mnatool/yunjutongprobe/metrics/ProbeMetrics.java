package com.mnatool.yunjutongprobe.metrics;

public class ProbeMetrics {
    public final int sent;
    public final int received;
    public final int lost;
    public final int duplicate;
    public final int reordered;
    public final int maxBurstLoss;
    public final double lossRate;
    public final double avgRttMs;
    public final double p50RttMs;
    public final double p95RttMs;
    public final double p99RttMs;
    public final double minRttMs;
    public final double maxRttMs;
    public final double jitterMs;
    public final double latestRttMs;
    public final boolean finalResult;

    public ProbeMetrics(
            int sent,
            int received,
            int lost,
            int duplicate,
            int reordered,
            int maxBurstLoss,
            double lossRate,
            double avgRttMs,
            double p50RttMs,
            double p95RttMs,
            double p99RttMs,
            double minRttMs,
            double maxRttMs,
            double jitterMs,
            double latestRttMs,
            boolean finalResult
    ) {
        this.sent = sent;
        this.received = received;
        this.lost = lost;
        this.duplicate = duplicate;
        this.reordered = reordered;
        this.maxBurstLoss = maxBurstLoss;
        this.lossRate = lossRate;
        this.avgRttMs = avgRttMs;
        this.p50RttMs = p50RttMs;
        this.p95RttMs = p95RttMs;
        this.p99RttMs = p99RttMs;
        this.minRttMs = minRttMs;
        this.maxRttMs = maxRttMs;
        this.jitterMs = jitterMs;
        this.latestRttMs = latestRttMs;
        this.finalResult = finalResult;
    }

    public static ProbeMetrics empty() {
        return new ProbeMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}
