package com.mnatool.yunjutongprobe;

final class ProbeMetrics {
    final int sent;
    final int received;
    final int lost;
    final int duplicate;
    final int reordered;
    final int maxBurstLoss;
    final double lossRate;
    final double avgRttMs;
    final double p50RttMs;
    final double p95RttMs;
    final double p99RttMs;
    final double minRttMs;
    final double maxRttMs;
    final double jitterMs;
    final double latestRttMs;
    final boolean finalResult;

    ProbeMetrics(
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

    static ProbeMetrics empty() {
        return new ProbeMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}
