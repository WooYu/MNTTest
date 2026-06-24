package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals("1000", lab.packetBytes);
        assertEquals("60000", lab.timeoutMs);
    }

    @Test
    public void migratesOnlyVersionsOlderThanCurrentDefaults() {
        assertEquals(2, ProbeDefaults.VERSION);
        assertTrue(ProbeDefaults.requiresMigration(0));
        assertTrue(ProbeDefaults.requiresMigration(1));
        assertFalse(ProbeDefaults.requiresMigration(ProbeDefaults.VERSION));
    }
}
