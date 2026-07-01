package com.mnatool.yunjutongprobe.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProbeSessionCoordinatorTest {
    @Test
    public void naturalCompleteGoesToPendingConfirm() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        assertTrue(session.begin("run-a"));
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.COMPLETE_PENDING_CONFIRM,
                session.onRunnerFinished("run-a", false));
        assertFalse(session.flow().awaitingConfirm());
    }

    @Test
    public void stopRequestedFinishesStopped() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.begin("run-b");
        session.requestStop();
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.FINISH_STOPPED,
                session.onRunnerFinished("run-b", false));
    }

    @Test
    public void cancelDiscardsFinishedResult() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.begin("run-c");
        session.requestCancel();
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.CANCEL_RUN,
                session.onRunnerFinished("run-c", false));
    }

    @Test
    public void lateCallbackIgnoredAfterFinish() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.begin("run-d");
        assertTrue(session.flow().finish("run-d", ProbeFlowState.Outcome.STOPPED, null));
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.IGNORE,
                session.onRunnerFinished("run-d", false));
    }

    @Test
    public void responderStopUsesResponderStopped() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.begin("run-e");
        session.requestStop();
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.FINISH_RESPONDER_STOPPED,
                session.onRunnerFinished("run-e", true));
    }

    @Test
    public void failureMapsToFinishFailedUnlessCancel() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.begin("run-f");
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.FINISH_FAILED,
                session.onRunnerFailed("run-f"));
        assertTrue(session.flow().finish("run-f", ProbeFlowState.Outcome.FAILED, "err"));
        session.flow().resetForRetest();
        session.begin("run-g");
        session.requestCancel();
        assertEquals(ProbeSessionCoordinator.RunnerFinishAction.CANCEL_RUN,
                session.onRunnerFailed("run-g"));
    }

    @Test
    public void prepareForStartClearsFlags() {
        ProbeSessionCoordinator session = new ProbeSessionCoordinator();
        session.requestStop();
        session.requestCancel();
        session.prepareForStart();
        assertFalse(session.isStopRequested());
        assertFalse(session.isCancelRequested());
    }
}
