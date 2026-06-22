package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProbeDefaultsTest {
    @Test
    public void planAUsesRecommendedMqttProbeDefaults() {
        assertEquals("300", ProbeDefaults.COUNT);
        assertEquals("5", ProbeDefaults.PPS);
        assertEquals("200", ProbeDefaults.PACKET_BYTES);
        assertEquals("5000", ProbeDefaults.TIMEOUT_MS);
    }

    @Test
    public void migratesOnlyVersionsOlderThanCurrentDefaults() {
        assertTrue(ProbeDefaults.requiresMigration(0));
        assertFalse(ProbeDefaults.requiresMigration(ProbeDefaults.VERSION));
    }
}
