package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ActionAdmissionTest {
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);
    private static final long MAX_FRAME_AGE_MILLIS = 3_000L;

    @Test
    public void allowsFreshFrameInCurrentWindowAndGeneration() {
        assertTrue(ActionAdmission.allows(frame(), current(), MAX_FRAME_AGE_MILLIS));
    }

    @Test
    public void rejectsOldGeneration() {
        assertRejected(ActionAdmission.RejectionReason.GENERATION, frame(),
                current(8L, 42L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 12));
    }

    @Test
    public void rejectsInvalidContext() {
        assertRejected(ActionAdmission.RejectionReason.INVALID_CONTEXT, null, current());
    }

    @Test
    public void rejectsExpiredCapture() {
        assertRejected(ActionAdmission.RejectionReason.CAPTURE_AGE, frame(),
                current(7L, 42L, 17L, 4_001L, GAME_PACKAGE, WINDOW, 12));
    }

    @Test
    public void rejectsFrameWhenNewerCaptureSequenceExists() {
        assertRejected(ActionAdmission.RejectionReason.CAPTURE_SEQUENCE, frame(),
                current(7L, 43L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 12));
    }

    @Test
    public void rejectsWhenForegroundChangedToAnotherApp() {
        assertRejected(ActionAdmission.RejectionReason.PACKAGE, frame(),
                current(7L, 42L, 17L, 1_400L, "com.example.other", WINDOW, 12));
    }

    @Test
    public void rejectsWhenActiveRootOrWindowIsUnavailable() {
        assertRejected(ActionAdmission.RejectionReason.WINDOW_UNAVAILABLE, frame(),
                current(7L, 42L, 17L, 1_400L, GAME_PACKAGE, null, -1));
    }

    @Test
    public void rejectsWhenWindowBoundsChangedAfterCapture() {
        CaptureGeometry.Bounds changed =
                new CaptureGeometry.Bounds(40, 120, 1120, 2520);
        assertRejected(ActionAdmission.RejectionReason.WINDOW_BOUNDS, frame(),
                current(7L, 42L, 17L, 1_400L, GAME_PACKAGE, changed, 12));
    }

    @Test
    public void rejectsWhenWindowIdentityChangedAfterCapture() {
        assertRejected(ActionAdmission.RejectionReason.WINDOW_ID, frame(),
                current(7L, 42L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 13));
    }

    @Test
    public void rejectsWhenAutomationWasPaused() {
        assertRejected(ActionAdmission.RejectionReason.NOT_RUNNING, frame(),
                new ActionAdmission.CurrentState(
                        false, 7L, 42L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 12));
    }

    @Test
    public void rejectsWhenNewerOcrTransactionExists() {
        assertRejected(ActionAdmission.RejectionReason.OCR_SEQUENCE, frame(),
                current(7L, 42L, 18L, 1_400L, GAME_PACKAGE, WINDOW, 12));
    }

    @Test
    public void rejectsAfterActionContextWasInvalidated() {
        assertRejected(ActionAdmission.RejectionReason.ADMISSION_EPOCH, frame(),
                new ActionAdmission.CurrentState(
                        true, 7L, 42L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 12, 1L));
    }

    @Test
    public void allowsFreshFrameAfterNormalGameplayTransition() {
        ActionAdmission.FrameContext next = new ActionAdmission.FrameContext(
                7L, 43L, 18L, 2_000L, GAME_PACKAGE, WINDOW, 12);
        ActionAdmission.CurrentState current = current(
                7L, 43L, 18L, 2_400L, GAME_PACKAGE, WINDOW, 12);

        assertTrue(ActionAdmission.allows(next, current, MAX_FRAME_AGE_MILLIS));
    }

    @Test
    public void allowsFreshCaptureOnlyContextForAStageHandoff() {
        ActionAdmission.FrameContext captureOnly = new ActionAdmission.FrameContext(
                7L, 43L, 0L, 2_000L, GAME_PACKAGE, WINDOW, 12, 1L);
        ActionAdmission.CurrentState current = new ActionAdmission.CurrentState(
                true, 7L, 43L, 18L, 2_400L, GAME_PACKAGE, WINDOW, 12, 1L);

        assertTrue(ActionAdmission.allows(captureOnly, current, MAX_FRAME_AGE_MILLIS));
    }

    @Test
    public void focusedDispatchRecoveryContextStillRequiresCurrentCapture() {
        ActionAdmission.FrameContext captureOnly = new ActionAdmission.FrameContext(
                7L, 42L, 0L, 1_000L, GAME_PACKAGE, WINDOW, 12);

        assertFalse(ActionAdmission.allows(
                captureOnly,
                current(7L, 43L, 18L, 1_400L, GAME_PACKAGE, WINDOW, 12),
                MAX_FRAME_AGE_MILLIS));
    }

    @Test
    public void focusedDispatchRecoveryContextCanPassWithFreshWindowState() {
        ActionAdmission.FrameContext captureOnly = new ActionAdmission.FrameContext(
                7L, 42L, 0L, 1_000L, GAME_PACKAGE, WINDOW, 12);

        assertTrue(ActionAdmission.allows(
                captureOnly,
                current(7L, 42L, 18L, 1_400L, GAME_PACKAGE, WINDOW, 12),
                MAX_FRAME_AGE_MILLIS));
    }

    private static ActionAdmission.FrameContext frame() {
        return new ActionAdmission.FrameContext(
                7L, 42L, 17L, 1_000L, GAME_PACKAGE, WINDOW, 12);
    }

    private static ActionAdmission.CurrentState current() {
        return current(7L, 42L, 17L, 1_400L, GAME_PACKAGE, WINDOW, 12);
    }

    private static ActionAdmission.CurrentState current(
            long generation,
            long captureSequence,
            long ocrRequestSequence,
            long nowUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds windowBounds,
            int windowId) {
        return new ActionAdmission.CurrentState(
                true,
                generation,
                captureSequence,
                ocrRequestSequence,
                nowUptimeMillis,
                packageName,
                windowBounds,
                windowId);
    }

    private static void assertRejected(
            ActionAdmission.RejectionReason reason,
            ActionAdmission.FrameContext frame,
            ActionAdmission.CurrentState current) {
        ActionAdmission.Decision decision = ActionAdmission.evaluate(
                frame, current, MAX_FRAME_AGE_MILLIS);
        assertFalse(decision.allowed());
        assertEquals(reason, decision.reason());
    }
}
