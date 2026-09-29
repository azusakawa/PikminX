package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Verifies the fresh-context handoff required by long multi-stage gestures. */
public final class MultiStageGestureWorkflowTest {
    @Test
    public void plantingScrollCannotAuthorizeTheNextStageWithItsOriginalFrame() {
        FrameTimeline timeline = new FrameTimeline();
        ActionAdmission.FrameContext original = timeline.captureOcr();
        assertTrue(timeline.admit(original).allowed());

        timeline.clock.advanceBy(900L);
        ActionAdmission.FrameContext freshCaptureOnly = timeline.captureOnly();

        assertEquals(ActionAdmission.RejectionReason.CAPTURE_SEQUENCE,
                timeline.admit(original).reason());
        assertTrue(timeline.admit(freshCaptureOnly).allowed());
        assertEquals(0L, freshCaptureOnly.ocrRequestSequence());
    }

    @Test
    public void feedZoomRequiresFreshContextAfterSettleDelay() {
        FrameTimeline timeline = new FrameTimeline();
        ActionAdmission.FrameContext initial = timeline.captureOcr();
        assertTrue(timeline.admit(initial).allowed());

        timeline.clock.advanceBy(1_000L);
        ActionAdmission.FrameContext afterSettle = timeline.captureOnly();

        assertFalse(timeline.admit(initial).allowed());
        assertTrue(timeline.admit(afterSettle).allowed());
    }

    @Test
    public void feedHoldContinuationUsesNewOcrContextAndNotTheStartingFrame() {
        FrameTimeline timeline = new FrameTimeline();
        FeedHoldLifecycle hold = new FeedHoldLifecycle();
        assertTrue(hold.beginStart());
        assertTrue(hold.markContinuedStrokeActive());

        ActionAdmission.FrameContext start = timeline.captureOcr();
        assertTrue(timeline.admit(start).allowed());
        timeline.clock.advanceBy(2_200L);
        ActionAdmission.FrameContext continuation = timeline.captureOcr();

        assertEquals(ActionAdmission.RejectionReason.CAPTURE_SEQUENCE,
                timeline.admit(start).reason());
        assertTrue(timeline.admit(continuation).allowed());
        assertTrue(hold.canContinue());
    }

    @Test
    public void expiredStageFrameMustBeRescannedBeforeAnotherAction() {
        FrameTimeline timeline = new FrameTimeline();
        ActionAdmission.FrameContext original = timeline.captureOcr();
        timeline.clock.advanceBy(WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS + 1L);

        assertEquals(ActionAdmission.RejectionReason.CAPTURE_AGE,
                timeline.admit(original).reason());
        ActionAdmission.FrameContext fresh = timeline.captureOnly();
        assertTrue(timeline.admit(fresh).allowed());
    }

    @Test
    public void holdAdmissionFailureAbortsWithoutTerminalGestureWhenWindowChanges() {
        FrameTimeline timeline = new FrameTimeline();
        FeedHoldLifecycle hold = new FeedHoldLifecycle();
        assertTrue(hold.beginStart());
        assertTrue(hold.markContinuedStrokeActive());
        ActionAdmission.FrameContext original = timeline.captureOcr();

        timeline.setPackageName("com.example.other");
        assertFalse(timeline.admit(original).allowed());
        assertTrue(hold.beginAbort());
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(original, timeline.currentContext()));
        assertTrue(hold.abortWithoutTerminal());
        assertTrue(hold.isIdle());
        assertFalse(hold.resolveTerminalCleanup());
    }

    @Test
    public void holdCleanupIsDeterministicForRootNullPackageChangeAndDuplicateCalls() {
        FrameTimeline timeline = new FrameTimeline();
        FeedHoldLifecycle hold = new FeedHoldLifecycle();
        assertTrue(hold.beginStart());
        assertTrue(hold.markContinuedStrokeActive());
        ActionAdmission.FrameContext original = timeline.captureOcr();

        timeline.setWindowUnavailable();
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(original, timeline.currentContext()));
        assertTrue(hold.beginAbort());
        assertTrue(hold.abortWithoutTerminal());
        assertFalse(hold.abortWithoutTerminal());
        assertFalse(hold.isContinuedStrokeActive());

        FeedHoldLifecycle packageChangedHold = new FeedHoldLifecycle();
        assertTrue(packageChangedHold.beginStart());
        assertTrue(packageChangedHold.markContinuedStrokeActive());
        ActionAdmission.FrameContext packageFrame = timeline.captureOcr();
        timeline.setPackageName("com.example.other");
        assertFalse(FeedHoldLifecycle.sameWindowForCleanup(
                packageFrame, timeline.currentContext()));
        assertTrue(packageChangedHold.beginAbort());
        assertTrue(packageChangedHold.abortWithoutTerminal());
        assertFalse(packageChangedHold.resolveTerminalCleanup());
    }

    @Test
    public void generationInvalidationDoesNotDeadlockAValidNextStage() {
        FrameTimeline timeline = new FrameTimeline();
        FeedHoldLifecycle hold = new FeedHoldLifecycle();
        assertTrue(hold.beginStart());
        assertTrue(hold.markContinuedStrokeActive());
        ActionAdmission.FrameContext old = timeline.captureOcr();

        timeline.newGeneration();
        assertEquals(ActionAdmission.RejectionReason.GENERATION,
                timeline.admit(old).reason());

        ActionAdmission.FrameContext fresh = timeline.captureOcr();
        assertTrue(timeline.admit(fresh).allowed());
        assertTrue(hold.beginTerminalCleanup());
        assertTrue(hold.resolveTerminalCleanup());
        assertTrue(hold.isIdle());
    }

    private static final class FrameTimeline {
        private final WorkflowTestSupport.FakeClock clock = new WorkflowTestSupport.FakeClock();
        private long generation = 1L;
        private long captureSequence;
        private long latestOcrSequence;
        private long admissionEpoch;
        private String packageName = WorkflowTestSupport.GAME_PACKAGE;
        private CaptureGeometry.Bounds bounds = WorkflowTestSupport.GAME_BOUNDS;
        private int windowId = WorkflowTestSupport.GAME_WINDOW_ID;

        ActionAdmission.FrameContext captureOcr() {
            long capture = ++captureSequence;
            long ocr = ++latestOcrSequence;
            return context(capture, ocr);
        }

        ActionAdmission.FrameContext captureOnly() {
            return context(++captureSequence, 0L);
        }

        ActionAdmission.Decision admit(ActionAdmission.FrameContext frame) {
            return ActionAdmission.evaluate(
                    frame,
                    WorkflowTestSupport.current(
                            true,
                            generation,
                            captureSequence,
                            latestOcrSequence,
                            clock.uptimeMillis(),
                            packageName,
                            bounds,
                            windowId,
                            admissionEpoch),
                    WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS);
        }

        ActionAdmission.FrameContext currentContext() {
            return WorkflowTestSupport.frame(
                    generation,
                    Math.max(1L, captureSequence),
                    Math.max(0L, latestOcrSequence),
                    clock.uptimeMillis(),
                    packageName,
                    bounds,
                    windowId,
                    admissionEpoch);
        }

        void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        void setWindowUnavailable() {
            packageName = null;
            bounds = null;
            windowId = -1;
        }

        void newGeneration() {
            generation++;
            captureSequence = 0L;
            latestOcrSequence = 0L;
        }

        private ActionAdmission.FrameContext context(long capture, long ocr) {
            return WorkflowTestSupport.frame(
                    generation,
                    capture,
                    ocr,
                    clock.uptimeMillis(),
                    WorkflowTestSupport.GAME_PACKAGE,
                    bounds,
                    windowId,
                    admissionEpoch);
        }
    }
}
