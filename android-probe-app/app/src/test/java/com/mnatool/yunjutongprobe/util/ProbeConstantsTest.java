package com.mnatool.yunjutongprobe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.mnatool.yunjutongprobe.runner.ProbeRecvStats;


public class ProbeConstantsTest {
    @Test
    public void highPpsThresholdsAlignWithProbeDefaults() {
        assertTrue(ProbeDefaults.isHighPpsRun(
                ProbeConstants.HighPps.COUNT_THRESHOLD_PKT, 1));
        assertTrue(ProbeDefaults.isHighPpsRun(
                1, ProbeConstants.HighPps.PPS_THRESHOLD));
        assertTrue(!ProbeDefaults.isHighPpsRun(
                ProbeConstants.HighPps.COUNT_THRESHOLD_PKT - 1,
                ProbeConstants.HighPps.PPS_THRESHOLD - 1));
    }

    @Test
    public void recvStallMatchesRecvStatsAlias() {
        assertEquals(ProbeConstants.Timing.RECV_STALL_THRESHOLD_MS, ProbeRecvStats.STALL_THRESHOLD_MS);
    }

    @Test
    public void defaultPortsAreDocumented() {
        assertEquals(9001, ProbeConstants.Network.UDP_ECHO_PORT);
        assertEquals(9002, ProbeConstants.Network.TCP_ECHO_PORT);
        assertEquals(1883, ProbeConstants.Network.MQTT_BROKER_PORT);
    }

    @Test
    public void unitConversionsAreConsistent() {
        assertEquals(ProbeConstants.Units.NS_PER_S,
                ProbeConstants.Units.NS_PER_MS * ProbeConstants.Units.MS_PER_S);
    }
}
