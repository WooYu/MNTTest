package com.mnatool.yunjutongprobe;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProbeRecvStatsTest {

    @Test
    public void checkStall_warnsAndStopsPublishWhenStalled() {
        ProbeRecvStats stats = new ProbeRecvStats();
        List<String> events = new ArrayList<>();
        ProbeCallback callback = new ProbeCallback() {
            @Override
            public void onEvent(String message) {
                events.add(message);
            }

            @Override
            public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
            }

            @Override
            public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
            }
        };

        long baseNs = System.nanoTime();
        stats.onRecvAdvance(10, baseNs);
        long stalledNs = baseNs + (ProbeRecvStats.STALL_THRESHOLD_MS + 1) * 1_000_000L;

        assertTrue(stats.checkStall(stalledNs, true, 500, callback));
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("收包停滞"));
        assertTrue(stats.toJson().optBoolean("recvStallDetected"));
        assertTrue(stats.toJson().optBoolean("echoDisconnectedSuspected"));
    }

    @Test
    public void checkStall_doesNotStopDuringWaitPhase() {
        ProbeRecvStats stats = new ProbeRecvStats();
        AtomicBoolean called = new AtomicBoolean(false);
        ProbeCallback callback = new ProbeCallback() {
            @Override
            public void onEvent(String message) {
                called.set(true);
            }

            @Override
            public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
            }

            @Override
            public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
            }
        };

        long baseNs = System.nanoTime();
        stats.onRecvAdvance(3, baseNs);
        long stalledNs = baseNs + (ProbeRecvStats.STALL_THRESHOLD_MS + 1) * 1_000_000L;

        assertFalse(stats.checkStall(stalledNs, false, 100, callback));
        assertTrue(called.get());
    }
}
