package com.mnatool.yunjutongprobe.runner.mqtt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MqttRelayServerCatalogTest {
    @Test
    public void defaultHostMatchesXiAnRelay() {
        assertEquals("113.133.169.192", MqttRelayServerCatalog.defaultHost());
        assertEquals(MqttDefaultProfile.HOST, MqttRelayServerCatalog.defaultHost());
    }

    @Test
    public void allRelayServersSharePort1883() {
        assertEquals(MqttDefaultProfile.PORT, MqttRelayServerCatalog.PORT);
    }

    @Test
    public void catalogContainsMergedScreenshotEntries() {
        assertTrue(MqttRelayServerCatalog.indexOfHost("113.133.169.192") >= 0);
        assertTrue(MqttRelayServerCatalog.indexOfHost("8.138.127.94") >= 0);
        assertTrue(MqttRelayServerCatalog.indexOfHost("175.6.33.199") >= 0);
        assertTrue(MqttRelayServerCatalog.indexOfHost("guangzhoumqtt.autel.com") >= 0);
        assertTrue(MqttRelayServerCatalog.indexOfHost("54.254.252.122") >= 0);
    }

    @Test
    public void indexOfHostFallsBackToDefaultForUnknownHost() {
        assertEquals(MqttRelayServerCatalog.defaultIndex(),
                MqttRelayServerCatalog.indexOfHost("not-a-relay-host"));
    }

    @Test
    public void labelsIncludeCityAndHost() {
        String label = MqttRelayServerCatalog.labelAt(0);
        assertEquals("西安 · 113.133.169.192", label);
    }
}
