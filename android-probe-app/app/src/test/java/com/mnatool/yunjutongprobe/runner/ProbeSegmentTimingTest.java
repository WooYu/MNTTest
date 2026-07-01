package com.mnatool.yunjutongprobe.runner;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;


public class ProbeSegmentTimingTest {

    @Test
    public void formatEchoRttLog_usesWallClockWhenAvailable() {
        ProbeSample sample = new ProbeSample("run", 1, 1000L, 1_000_000L, 200, false);
        sample.clientRecvNs = 8_000_000L;
        sample.clientRecvMs = 1_000_007L;
        sample.serverRecvNs = 50L;
        sample.serverSendNs = 100L;
        sample.serverRecvMs = 1_000_003L;
        sample.serverSendMs = 1_000_004L;

        String log = ProbeSegmentTiming.formatEchoRttLog(1, sample, 7.0);
        assertTrue(log.contains("out=3.0"));
        assertTrue(log.contains("echo=1.00"));
        assertTrue(log.contains("ret=3.0"));
        assertFalse(log.contains("(ns)"));
        assertFalse(log.contains("seg?"));
    }

    @Test
    public void formatEchoRttLog_fallsBackToNanoWhenWallClockMissing() {
        ProbeSample sample = new ProbeSample("run", 1, 1_000_000L, 1_000_000L, 200, false);
        sample.clientRecvNs = 8_000_000L;
        sample.serverRecvNs = 5_000_000L;
        sample.serverSendNs = 5_000_350L;

        String log = ProbeSegmentTiming.formatEchoRttLog(1, sample, 7.0);
        assertTrue(log.contains("(ns)"));
    }

    @Test
    public void applyEchoTimestamps_readsWallClockFields() throws Exception {
        JSONObject ack = new JSONObject();
        ack.put("serverRecvNs", 10L);
        ack.put("serverSendNs", 20L);
        ack.put("serverRecvMs", 100L);
        ack.put("serverSendMs", 101L);
        ProbeSample sample = new ProbeSample("run", 0, 0, 0, 0, false);
        ProbeSegmentTiming.applyEchoTimestamps(sample, ack, 105L);
        org.junit.Assert.assertEquals(100L, sample.serverRecvMs);
        org.junit.Assert.assertEquals(101L, sample.serverSendMs);
        org.junit.Assert.assertEquals(105L, sample.clientRecvMs);
    }
}
