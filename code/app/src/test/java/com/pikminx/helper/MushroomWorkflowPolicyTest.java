package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MushroomWorkflowPolicyTest {
    @Test
    public void captureAndAnalysisFollowForwardProgressStates() {
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(MushroomWorkflowState.STARTING));
        assertEquals(
                MushroomWorkflowState.CAPTURING,
                MushroomWorkflowPolicy.afterCaptureRequested(MushroomWorkflowState.STARTING));
        assertEquals(
                MushroomWorkflowState.CAPTURING,
                MushroomWorkflowPolicy.afterCaptureRequested(MushroomWorkflowState.CAPTURING));
        assertTrue(MushroomWorkflowPolicy.canDeliverAnalysis(MushroomWorkflowState.ANALYZING));
        assertFalse(MushroomWorkflowPolicy.canRequestCapture(MushroomWorkflowState.ANALYZING));
    }

    @Test
    public void rejectionReturnsToRetryWaitWithoutMakingTerminalStatesRunnable() {
        assertEquals(
                MushroomWorkflowState.RETRY_WAIT,
                MushroomWorkflowPolicy.afterRejectedAnalysis(MushroomWorkflowState.ANALYZING));
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(MushroomWorkflowState.RETRY_WAIT));
        assertFalse(MushroomWorkflowPolicy.canRequestCapture(MushroomWorkflowState.STOPPED));
        assertTrue(MushroomWorkflowPolicy.isTerminal(MushroomWorkflowState.STOPPING));
    }

    @Test
    public void waitingForGameInvalidPageAndResultsCanResumeWithBoundedScan() {
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(
                MushroomWorkflowState.WAITING_FOR_GAME));
        assertEquals(
                MushroomWorkflowState.CAPTURING,
                MushroomWorkflowPolicy.afterCaptureRequested(
                        MushroomWorkflowState.WAITING_FOR_GAME));
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(
                MushroomWorkflowState.INVALID_PAGE));
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(
                MushroomWorkflowState.RESULTS));
        assertFalse(MushroomWorkflowPolicy.canRequestCapture(
                MushroomWorkflowState.ERROR));
    }

    @Test
    public void patrolOwnershipOnlyResumesTheScannerThatWasAlreadyEnabled() {
        assertTrue(MushroomWorkflowPolicy.shouldResumeNormalScan(
                true, MushroomPatrolController.State.COMPLETED));
        assertTrue(MushroomWorkflowPolicy.shouldResumeNormalScan(
                true, MushroomPatrolController.State.ERROR));
        assertFalse(MushroomWorkflowPolicy.shouldResumeNormalScan(
                false, MushroomPatrolController.State.STOPPED));
        assertTrue(MushroomWorkflowPolicy.shouldStopPatrolOwnedEngine(
                true, false, MushroomPatrolController.State.STOPPED));
        assertFalse(MushroomWorkflowPolicy.shouldStopPatrolOwnedEngine(
                false, true, MushroomPatrolController.State.COMPLETED));
    }
}
