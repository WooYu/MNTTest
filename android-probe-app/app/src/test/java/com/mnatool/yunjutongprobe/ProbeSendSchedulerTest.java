package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProbeSendSchedulerTest {

    @Test
    public void capCatchUp_resetsScheduleWhenLagTooLarge() {
        ProbeRecvStats stats = new ProbeRecvStats();
        long intervalNs = 500_000L;
        long nextNs = 1_000L;
        long nowNs = nextNs + 60_000_000L;

        long adjusted = ProbeSendScheduler.capCatchUp(nowNs, nextNs, intervalNs, stats);
        assertEquals(nowNs, adjusted);
        assertEquals(1, stats.toJson().optInt("catchUpResetCount"));
    }

    @Test
    public void capCatchUp_keepsScheduleWhenLagSmall() {
        long intervalNs = 500_000L;
        long nextNs = 900_000L;
        long nowNs = 1_000_000L;

        long adjusted = ProbeSendScheduler.capCatchUp(nowNs, nextNs, intervalNs, null);
        assertEquals(nextNs, adjusted);
    }
}
