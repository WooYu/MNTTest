package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProbeFlowStateTest {
    @Test
    public void followsConfigRunningResultConfigFlow() {
        ProbeFlowState flow = new ProbeFlowState();

        assertEquals(ProbeFlowState.Page.CONFIG, flow.page());
        assertTrue(flow.begin("run-a"));
        assertEquals(ProbeFlowState.Page.RUNNING, flow.page());
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.COMPLETED, null));
        assertEquals(ProbeFlowState.Page.RESULT, flow.page());
        assertEquals(ProbeFlowState.Outcome.COMPLETED, flow.outcome());

        flow.resetForRetest();
        assertEquals(ProbeFlowState.Page.CONFIG, flow.page());
    }

    @Test
    public void rejectsDuplicateStartsAndLateCallbacks() {
        ProbeFlowState flow = new ProbeFlowState();

        assertTrue(flow.begin("run-a"));
        assertFalse(flow.begin("run-b"));
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.STOPPED, null));
        assertFalse(flow.accepts("run-a"));
        assertFalse(flow.finish("run-a", ProbeFlowState.Outcome.COMPLETED, null));
    }

    @Test
    public void storesFailureForResultPage() {
        ProbeFlowState flow = new ProbeFlowState();

        flow.begin("run-a");
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.FAILED, "连接被拒绝"));
        assertEquals("连接被拒绝", flow.message());
    }
}
