package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedHoldLifecycleTest {
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);

    @Test
    public void initialContinuedStrokeMovesToActiveState() {
        FeedHoldLifecycle lifecycle = new FeedHoldLifecycle();

        assertTrue(lifecycle.beginStart());
        assertTrue(lifecycle.isStarting());
        assertTrue(lifecycle.markContinuedStrokeActive());
        assertTrue(lifecycle.isContinuedStrokeActive());
        assertFalse(lifecycle.isStarting());
    }

    @Test
    public void successfulContinuationRequiresAndResolvesTerminalCleanup() {
        FeedHoldLifecycle lifecycle = activeLifecycle();

        assertTrue(lifecycle.beginTerminalCleanup());
        assertTrue(lifecycle.isCleanupPending());
        assertTrue(lifecycle.resolveTerminalCleanup());
        assertTrue(lifecycle.isIdle());
    }

    @Test
    public void admissionRejectionKeepsStrokeActiveUntilCleanupResolution() {
        FeedHoldLifecycle lifecycle = activeLifecycle();
        ActionAdmission.FrameContext frame = context(7L, GAME_PACKAGE, WINDOW, 12);
        ActionAdmission.Decision admission = ActionAdmission.evaluate(
                frame,
                new ActionAdmission.CurrentState(
                        true, 7L, 1L, 1L, 4_001L, GAME_PACKAGE, WINDOW, 12, 0L),
                3_000L);

        assertFalse(admission.allowed());
        assertEquals(ActionAdmission.RejectionReason.CAPTURE_AGE, admission.reason());
        assertTrue(lifecycle.beginAbort());
        assertTrue(lifecycle.isContinuedStrokeActive());
        assertTrue(lifecycle.isCleanupPending());
        assertFalse(lifecycle.beginAbort());
        assertTrue(lifecycle.abortWithoutTerminal());
        assertTrue(lifecycle.isIdle());
    }

    @Test
    public void pauseCleanupIsIdempotentAndResumeCanStartAgain() {
        FeedHoldLifecycle lifecycle = activeLifecycle();

        assertTrue(lifecycle.beginAbort());
        assertTrue(lifecycle.abortWithoutTerminal());
        assertFalse(lifecycle.abortWithoutTerminal());
        assertTrue(lifecycle.beginStart());
        assertTrue(lifecycle.markContinuedStrokeActive());
    }

    @Test
    public void generationChangeCannotAuthorizeTerminalCleanup() {
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                context(7L, GAME_PACKAGE, WINDOW, 12),
                context(8L, GAME_PACKAGE, WINDOW, 12)));
    }

    @Test
    public void rootUnavailableCannotAuthorizeTerminalCleanup() {
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                context(7L, GAME_PACKAGE, WINDOW, 12), null));
    }

    @Test
    public void packageOrWindowChangeCannotAuthorizeTerminalCleanup() {
        ActionAdmission.FrameContext original = context(7L, GAME_PACKAGE, WINDOW, 12);
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                original, context(7L, "com.example.other", WINDOW, 12)));
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                original,
                context(7L, GAME_PACKAGE,
                        new CaptureGeometry.Bounds(40, 120, 1120, 2520), 12)));
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                original, context(7L, GAME_PACKAGE, WINDOW, 13)));
    }

    @Test
    public void normalTransitionDoesNotDeadlockTheLifecycle() {
        FeedHoldLifecycle lifecycle = activeLifecycle();

        assertTrue(lifecycle.beginTerminalCleanup());
        assertTrue(lifecycle.resolveTerminalCleanup());
        assertFalse(lifecycle.resolveTerminalCleanup());
        assertTrue(lifecycle.beginStart());
        assertTrue(lifecycle.markContinuedStrokeActive());
    }

    private static FeedHoldLifecycle activeLifecycle() {
        FeedHoldLifecycle lifecycle = new FeedHoldLifecycle();
        assertTrue(lifecycle.beginStart());
        assertTrue(lifecycle.markContinuedStrokeActive());
        return lifecycle;
    }

    private static ActionAdmission.FrameContext context(
            long generation, String packageName, CaptureGeometry.Bounds bounds, int windowId) {
        return new ActionAdmission.FrameContext(
                generation, 1L, 1L, 1_000L, packageName, bounds, windowId, 0L);
    }
}
