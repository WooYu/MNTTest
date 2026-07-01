package com.mnatool.yunjutongprobe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProbeDefaultsTest {
    @Test
    public void defaultPresetIsFieldKilo() {
        assertEquals(ProbeDefaults.Preset.FIELD, ProbeDefaults.DEFAULT_PRESET);
        assertEquals("1000", ProbeDefaults.COUNT);
        assertEquals("10", ProbeDefaults.PPS);
        assertEquals("200", ProbeDefaults.PACKET_BYTES);
        assertEquals("5000", ProbeDefaults.TIMEOUT_MS);
    }

    @Test
    public void fieldPresetMatchesFieldKilo() {
        ProbeDefaults.Preset field = ProbeDefaults.Preset.FIELD;
        assertEquals("1000", field.count);
        assertEquals("10", field.pps);
        assertEquals("200", field.packetBytes);
        assertEquals("5000", field.timeoutMs);
    }

    @Test
    public void labPresetMatchesHundredThousand() {
        ProbeDefaults.Preset lab = ProbeDefaults.Preset.LAB;
        assertEquals("100000", lab.count);
        assertEquals("2000", lab.pps);
        assertEquals("100", lab.packetBytes);
        assertEquals("60000", lab.timeoutMs);
    }

    @Test
    public void migratesOnlyVersionsOlderThanCurrentDefaults() {
        assertEquals(4, ProbeDefaults.VERSION);
        assertTrue(ProbeDefaults.requiresMigration(0));
        assertTrue(ProbeDefaults.requiresMigration(1));
        assertTrue(ProbeDefaults.requiresMigration(2));
        assertTrue(ProbeDefaults.requiresMigration(3));
        assertFalse(ProbeDefaults.requiresMigration(ProbeDefaults.VERSION));
    }

    @Test
    public void recordEchoSeqSkippedForHighPpsRun() {
        assertTrue(ProbeDefaults.recordEchoSeqForConfig(1000, 10, 200, 5000));
        assertFalse(ProbeDefaults.recordEchoSeqForConfig(100000, 2000, 100, 60000));
        assertTrue(ProbeDefaults.recordEchoSeqForConfig(10000, 499, 1000, 60000));
        assertFalse(ProbeDefaults.recordEchoSeqForConfig(1000, 500, 200, 5000));
        assertFalse(ProbeDefaults.recordEchoSeqForConfig(50000, 10, 200, 5000));
    }

    @Test
    public void isHighPpsRunMatchesProbeRunLibThreshold() {
        assertFalse(ProbeDefaults.isHighPpsRun(1000, 10));
        assertTrue(ProbeDefaults.isHighPpsRun(100000, 500));
        assertTrue(ProbeDefaults.isHighPpsRun(1000, 500));
        assertTrue(ProbeDefaults.isHighPpsRun(50000, 10));
    }

    @Test
    public void detectPresetReturnsNullForCustomParams() {
        assertEquals(ProbeDefaults.Preset.FIELD, ProbeDefaults.detectPreset("1000", "10", "200", "5000"));
        assertEquals(ProbeDefaults.Preset.LAB, ProbeDefaults.detectPreset("100000", "2000", "100", "60000"));
        assertNull(ProbeDefaults.detectPreset("5000", "50", "300", "10000"));
    }
}
