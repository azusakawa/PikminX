package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class H10aAnalysisAdmissionTest {
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(0, 0, 1080, 2400);
    private static final long MAX_FRAME_AGE_MILLIS = 3_000L;

    @Test
    public void acceptsCurrentFreshWorkerResult() {
        H10aAnalysisAdmission.Decision decision = evaluate(
                frame(), current(7L, 42L, 9L, 1_400L, GAME_PACKAGE, WINDOW, 12, 4L));

        assertTrue(decision.allowed());
        assertEquals(H10aAnalysisAdmission.RejectionReason.NONE, decision.reason());
        assertEquals(ActionAdmission.RejectionReason.NONE, decision.actionReason());
    }

    @Test
    public void rejectsOlderGenerationBeforeWorkerResultCanBeApplied() {
        H10aAnalysisAdmission.Decision decision = evaluate(
                frame(), current(8L, 42L, 9L, 1_400L, GAME_PACKAGE, WINDOW, 12, 4L));

        assertFalse(decision.allowed());
        assertEquals(H10aAnalysisAdmission.RejectionReason.ACTION_ADMISSION, decision.reason());
        assertEquals(ActionAdmission.RejectionReason.GENERATION, decision.actionReason());
    }

    @Test
    public void rejectsNewerCaptureSequenceAndEpoch() {
        H10aAnalysisAdmission.Decision newerCapture = evaluate(
                frame(), current(7L, 43L, 9L, 1_400L, GAME_PACKAGE, WINDOW, 12, 4L));
        H10aAnalysisAdmission.Decision invalidatedEpoch = evaluate(
                frame(), current(7L, 42L, 9L, 1_400L, GAME_PACKAGE, WINDOW, 12, 5L));

        assertEquals(ActionAdmission.RejectionReason.CAPTURE_SEQUENCE,
                newerCapture.actionReason());
        assertEquals(ActionAdmission.RejectionReason.ADMISSION_EPOCH,
                invalidatedEpoch.actionReason());
    }

    @Test
    public void rejectsExpiredFrameAndPausedAutomation() {
        H10aAnalysisAdmission.Decision expired = evaluate(
                frame(), current(7L, 42L, 9L, 4_001L, GAME_PACKAGE, WINDOW, 12, 4L));
        H10aAnalysisAdmission.Decision paused = evaluate(
                frame(), new H10aAnalysisAdmission.Current(
                        new ActionAdmission.CurrentState(
                                false, 7L, 42L, 9L, 1_400L,
                                GAME_PACKAGE, WINDOW, 12, 4L),
                        "PLANTING", "MONITORING"));

        assertEquals(ActionAdmission.RejectionReason.CAPTURE_AGE, expired.actionReason());
        assertEquals(ActionAdmission.RejectionReason.NOT_RUNNING, paused.actionReason());
    }

    @Test
    public void rejectsMissingOrChangedForegroundWindow() {
        H10aAnalysisAdmission.Decision rootUnavailable = evaluate(
                frame(), current(7L, 42L, 9L, 1_400L, null, null, -1, 4L));
        H10aAnalysisAdmission.Decision changedBounds = evaluate(
                frame(), current(
                        7L, 42L, 9L, 1_400L, GAME_PACKAGE,
                        new CaptureGeometry.Bounds(0, 0, 1080, 2300), 12, 4L));

        assertEquals(ActionAdmission.RejectionReason.PACKAGE,
                rootUnavailable.actionReason());
        assertEquals(ActionAdmission.RejectionReason.WINDOW_BOUNDS,
                changedBounds.actionReason());
    }

    @Test
    public void rejectsWorkflowStateChangeWithoutMutatingState() {
        H10aAnalysisAdmission.Decision decision = evaluate(
                frame(), new H10aAnalysisAdmission.Current(
                        current(7L, 42L, 9L, 1_400L, GAME_PACKAGE, WINDOW, 12, 4L),
                        "PLANTING", "WAITING_START"));

        assertFalse(decision.allowed());
        assertEquals(H10aAnalysisAdmission.RejectionReason.WORKFLOW_STATE,
                decision.reason());
        assertEquals(ActionAdmission.RejectionReason.NONE, decision.actionReason());
    }

    private static H10aAnalysisAdmission.Decision evaluate(
            ActionAdmission.FrameContext frame,
            ActionAdmission.CurrentState current) {
        return H10aAnalysisAdmission.evaluate(
                new H10aAnalysisAdmission.Context(frame, "PLANTING", "MONITORING"),
                new H10aAnalysisAdmission.Current(current, "PLANTING", "MONITORING"),
                MAX_FRAME_AGE_MILLIS);
    }

    private static H10aAnalysisAdmission.Decision evaluate(
            ActionAdmission.FrameContext frame,
            H10aAnalysisAdmission.Current current) {
        return H10aAnalysisAdmission.evaluate(
                new H10aAnalysisAdmission.Context(frame, "PLANTING", "MONITORING"),
                current,
                MAX_FRAME_AGE_MILLIS);
    }

    private static ActionAdmission.FrameContext frame() {
        return new ActionAdmission.FrameContext(
                7L, 42L, 9L, 1_000L, GAME_PACKAGE, WINDOW, 12, 4L);
    }

    private static ActionAdmission.CurrentState current(
            long generation,
            long captureSequence,
            long ocrRequestSequence,
            long nowUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds bounds,
            int windowId,
            long epoch) {
        return new ActionAdmission.CurrentState(
                true,
                generation,
                captureSequence,
                ocrRequestSequence,
                nowUptimeMillis,
                packageName,
                bounds,
                windowId,
                epoch);
    }
}
