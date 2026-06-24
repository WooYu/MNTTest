package com.mnatool.yunjutongprobe;

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
        List<ProbeSample> samples = List.of(
                sample(1, true, 50_000_000L),
                sample(2, true, 60_000_000L),
                sample(3, false, 0L)
        );

        ProbeMetrics running = MetricsCalculator.calculate(samples, System.nanoTime(), TIMEOUT_NS, false);

        assertEquals(3, running.sent);
        assertEquals(2, running.received);
        assertEquals(0, running.lost);
        assertEquals(0.0, running.lossRate, 0.0001);
        assertEquals(false, running.finalResult);
    }

    @Test
    public void finalResultCountsUnreceivedAsLost() {
        List<ProbeSample> samples = List.of(
                sample(1, true, 50_000_000L),
                sample(2, true, 60_000_000L),
                sample(3, false, 0L)
        );

        ProbeMetrics finalMetrics = MetricsCalculator.calculate(samples, System.nanoTime(), TIMEOUT_NS, true);

        assertEquals(3, finalMetrics.sent);
        assertEquals(2, finalMetrics.received);
        assertEquals(1, finalMetrics.lost);
        assertEquals(1.0 / 3.0, finalMetrics.lossRate, 0.0001);
        assertTrue(finalMetrics.finalResult);
    }

    @Test
    public void finalResultTracksBurstLoss() {
        List<ProbeSample> samples = List.of(
                sample(1, false, 0L),
                sample(2, false, 0L),
                sample(3, true, 40_000_000L),
                sample(4, false, 0L)
        );

        ProbeMetrics metrics = MetricsCalculator.calculate(samples, System.nanoTime(), TIMEOUT_NS, true);

        assertEquals(3, metrics.lost);
        assertEquals(2, metrics.maxBurstLoss);
    }

    @Test
    public void computesRttPercentilesForReceivedSamples() {
        List<ProbeSample> samples = List.of(
                sample(1, true, 10_000_000L),
                sample(2, true, 20_000_000L),
                sample(3, true, 30_000_000L),
                sample(4, true, 100_000_000L)
        );

        ProbeMetrics metrics = MetricsCalculator.calculate(samples, System.nanoTime(), TIMEOUT_NS, true);

        assertEquals(4, metrics.received);
        assertEquals(0, metrics.lost);
        assertEquals(10.0, metrics.minRttMs, 0.01);
        assertEquals(100.0, metrics.maxRttMs, 0.01);
        assertEquals(40.0, metrics.avgRttMs, 0.01);
        assertEquals(30.0, metrics.p50RttMs, 0.01);
    }

    private static ProbeSample sample(int seq, boolean received, long rttNs) {
        ProbeSample sample = new ProbeSample("run-1", seq, 1_000_000_000L, 1L, 200, false);
        if (received) {
            sample.clientRecvNs = sample.clientSendNs + rttNs;
        }
        return sample;
    }
}
