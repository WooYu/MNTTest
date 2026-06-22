package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WeakNetProfileTest {
    @Test
    public void emptyProfileIsInactive() {
        WeakNetProfile profile = WeakNetProfile.empty();
        assertFalse(profile.isActive());
        assertEquals("无弱网模拟", profile.displaySummary());
    }

    @Test
    public void clumsyProfileBuildsReadableSummary() {
        WeakNetProfile profile = new WeakNetProfile("Clumsy", "10", "30", "10", "outbound 113.133.169.192");
        assertTrue(profile.isActive());
        assertEquals("Clumsy，丢包 10%，延迟 +30ms，抖动 10ms，outbound 113.133.169.192", profile.displaySummary());
    }
}
