package com.mnatool.yunjutongprobe.runner.mqtt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MqttDefaultProfileTest {
    @Test
    public void containsApprovedChinaTestSenderDefaults() {
        assertEquals(2, MqttDefaultProfile.PROTOCOL_INDEX);
        assertEquals("113.133.169.192", MqttDefaultProfile.HOST);
        assertEquals("1883", MqttDefaultProfile.PORT);
        assertEquals("test", MqttDefaultProfile.ENV);
        assertEquals("V37G00000108", MqttDefaultProfile.CLIENT_ID);
        assertEquals("V37C00000133", MqttDefaultProfile.PUBLISH_TOPIC);
        assertEquals("V37G00000108", MqttDefaultProfile.SUBSCRIBE_TOPIC);
        assertEquals("111159", MqttDefaultProfile.DEVICE_PASSWORD);
        assertEquals("98:26:ad:23:28:2b", MqttDefaultProfile.DEVICE_MAC);
    }

    @Test
    public void migratesOnlyVersionsOlderThanCurrentProfile() {
        assertTrue(MqttDefaultProfile.requiresMigration(0));
        assertFalse(MqttDefaultProfile.requiresMigration(MqttDefaultProfile.VERSION));
        assertFalse(MqttDefaultProfile.requiresMigration(MqttDefaultProfile.VERSION + 1));
    }
}
