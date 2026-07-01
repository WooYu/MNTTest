package com.mnatool.yunjutongprobe.metrics;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetricsCalculatorTest {
    private static final long TIMEOUT_NS = 5_000_000_000L;

    @Test
    public void emptySamplesReturnsEmptyMetrics() {
        ProbeMetrics metrics = MetricsCalculator.calculate(new ArrayList<>(), 0L, TIMEOUT_NS, true);
        assertEquals(0, metrics.sent);
        assertEquals(0, metrics.received);
        assertEquals(0, metrics.lost);
        assertEquals(0.0, metrics.lossRate, 0.0001);
    }

    @Test
    public void inFlightDoesNotCountUnreceivedAsLost() {
        long nowNs = 50_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, true, 50_000_000L, nowNs - 10_000_000_000L),
                sample(2, true, 60_000_000L, nowNs - 9_000_000_000L),
                sample(3, false, 0L, nowNs - 1_000_000_000L)
        );

        ProbeMetrics running = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, false);

        assertEquals(3, running.sent);
        assertEquals(2, running.received);
        assertEquals(0, running.lost);
        assertEquals(0.0, running.lossRate, 0.0001);
        assertEquals(false, running.finalResult);
    }

    @Test
    public void runningCountsTimedOutUnreceivedAsLost() {
        long nowNs = 50_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, true, 50_000_000L, nowNs - 20_000_000_000L),
                sample(2, false, 0L, nowNs - 10_000_000_000L)
        );

        ProbeMetrics running = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, false);

        assertEquals(2, running.sent);
        assertEquals(1, running.received);
        assertEquals(1, running.lost);
        assertEquals(0.5, running.lossRate, 0.0001);
        assertEquals(1, running.maxBurstLoss);
    }

    @Test
    public void runningBurstResetsAcrossInFlightGap() {
        long nowNs = 100_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, false, 0L, nowNs - 20_000_000_000L),
                sample(2, false, 0L, nowNs - 1_000_000_000L),
                sample(3, false, 0L, nowNs - 15_000_000_000L)
        );

        ProbeMetrics running = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, false);

        assertEquals(2, running.lost);
        assertEquals(1, running.maxBurstLoss);
    }

    @Test
    public void finalResultCountsUnreceivedAsLost() {
        long nowNs = 50_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, true, 50_000_000L, nowNs - 10_000_000_000L),
                sample(2, true, 60_000_000L, nowNs - 9_000_000_000L),
                sample(3, false, 0L, nowNs - 1_000_000_000L)
        );

        ProbeMetrics finalMetrics = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, true);

        assertEquals(3, finalMetrics.sent);
        assertEquals(2, finalMetrics.received);
        assertEquals(1, finalMetrics.lost);
        assertEquals(1.0 / 3.0, finalMetrics.lossRate, 0.0001);
        assertTrue(finalMetrics.finalResult);
    }

    @Test
    public void finalResultTracksBurstLoss() {
        long nowNs = 50_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, false, 0L, nowNs - 10_000_000_000L),
                sample(2, false, 0L, nowNs - 9_000_000_000L),
                sample(3, true, 40_000_000L, nowNs - 8_000_000_000L),
                sample(4, false, 0L, nowNs - 7_000_000_000L)
        );

        ProbeMetrics metrics = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, true);

        assertEquals(3, metrics.lost);
        assertEquals(2, metrics.maxBurstLoss);
    }

    @Test
    public void computesRttPercentilesForReceivedSamples() {
        long nowNs = 50_000_000_000L;
        List<ProbeSample> samples = List.of(
                sample(1, true, 10_000_000L, nowNs - 10_000_000_000L),
                sample(2, true, 20_000_000L, nowNs - 9_000_000_000L),
                sample(3, true, 30_000_000L, nowNs - 8_000_000_000L),
                sample(4, true, 100_000_000L, nowNs - 7_000_000_000L)
        );

        ProbeMetrics metrics = MetricsCalculator.calculate(samples, nowNs, TIMEOUT_NS, true);

        assertEquals(4, metrics.received);
        assertEquals(0, metrics.lost);
        assertEquals(10.0, metrics.minRttMs, 0.01);
        assertEquals(100.0, metrics.maxRttMs, 0.01);
        assertEquals(40.0, metrics.avgRttMs, 0.01);
        assertEquals(30.0, metrics.p50RttMs, 0.01);
    }

    private static ProbeSample sample(int seq, boolean received, long rttNs, long clientSendNs) {
        ProbeSample sample = new ProbeSample("run-1", seq, clientSendNs, clientSendNs / 1_000_000L, 200, false);
        if (received) {
            sample.clientRecvNs = sample.clientSendNs + rttNs;
        }
        return sample;
    }
}
