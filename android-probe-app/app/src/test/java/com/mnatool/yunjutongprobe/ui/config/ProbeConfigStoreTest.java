package com.mnatool.yunjutongprobe.ui.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.WeakNetProfile;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


public class ProbeConfigStoreTest {
    @Test
    public void parseIntBoundedAcceptsMinAndMax() {
        assertEquals(1, ProbeConfigStore.parseIntBounded("1", "速率", 1, 8000));
        assertEquals(8000, ProbeConfigStore.parseIntBounded("8000", "速率", 1, 8000));
        assertEquals(100, ProbeConfigStore.parseIntBounded("100", "包大小", 1, 2000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseIntBoundedRejectsBelowMin() {
        ProbeConfigStore.parseIntBounded("0", "速率", 1, 8000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseIntBoundedRejectsAboveMax() {
        ProbeConfigStore.parseIntBounded("8001", "速率", 1, 8000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseIntBoundedRejectsNonNumeric() {
        ProbeConfigStore.parseIntBounded("abc", "发包数量", 1, 100000);
    }

    @Test
    public void clampUtility() {
        assertEquals(0, ProbeConfigStore.clamp(-5, 0, 3));
        assertEquals(3, ProbeConfigStore.clamp(10, 0, 3));
        assertEquals(2, ProbeConfigStore.clamp(2, 0, 3));
    }

    @Test
    public void defaultPortForProtocols() {
        assertEquals(ProbeConstants.Network.UDP_ECHO_PORT,
                ProbeConfigStore.defaultPortFor(ProbeConfig.Protocol.UDP));
        assertEquals(ProbeConstants.Network.TCP_ECHO_PORT,
                ProbeConfigStore.defaultPortFor(ProbeConfig.Protocol.TCP));
        assertEquals(ProbeConstants.Network.MQTT_BROKER_PORT,
                ProbeConfigStore.defaultPortFor(ProbeConfig.Protocol.MQTT));
    }

    @Test
    public void weakNetProfileFromInputsBuildsProfile() {
        WeakNetProfile profile = ProbeConfigStore.weakNetProfileFromInputs(
                "Clumsy", "10", "30", "5", "outbound");
        assertEquals("Clumsy", profile.tool);
        assertEquals("10", profile.lossPercent);
        assertTrue(profile.isActive());
    }

    @Test
    public void readWeakNetProfileReturnsEmptyWhenSceneOff() {
        ProbeConfigStore store = new ProbeConfigStore();
        ConfigPageViews views = new ConfigPageViews();
        views.weakNetToolSpinner = new android.widget.Spinner(null);
        views.weakNetSceneSwitch = new android.widget.Switch(null);
        assertFalse(views.weakNetSceneSwitch.isChecked());
        assertFalse(store.readWeakNetProfile(views).isActive());
    }

    @Test
    public void readWeakNetProfileReturnsEmptyWhenSpinnerMissing() {
        ProbeConfigStore store = new ProbeConfigStore();
        ConfigPageViews views = new ConfigPageViews();
        assertFalse(store.readWeakNetProfile(views).isActive());
    }
}
