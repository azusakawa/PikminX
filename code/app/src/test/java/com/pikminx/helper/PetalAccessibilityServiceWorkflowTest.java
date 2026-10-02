package com.pikminx.helper;

import com.pikminx.helper.platform.diagnostics.AdmissionDiagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Integration-shaped JVM coverage for the service coordination boundary.
 *
 * <p>The harness models only Android delivery seams. It uses the production admission,
 * screenshot-queue, OCR-transaction and diagnostic policies; Android framework gesture
 * dispatch itself remains an instrumentation/device concern.</p>
 */
public final class PetalAccessibilityServiceWorkflowTest {
    @Test
    public void freshCaptureOcrAdmissionThenGestureMakesForwardProgress() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();

        ServiceWorkflowHarness.Capture capture = harness.capture();
        ServiceWorkflowHarness.OcrRequest ocr = harness.startOcr(capture);
        ActionAdmission.FrameContext frame = harness.completeOcr(ocr);

        assertNotNull(frame);
        assertTrue(harness.tryGesture(frame));
        assertEquals(1, harness.gestureCount());
        assertEquals(1, harness.parsedOcrCount());
        assertFalse(harness.busy());
    }

    @Test
    public void staleFrameRejectsThenOne1500MillisecondRescanCanSucceed() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ServiceWorkflowHarness.Capture capture = harness.capture();
        ServiceWorkflowHarness.OcrRequest ocr = harness.startOcr(capture);
        ActionAdmission.FrameContext oldFrame = harness.completeOcr(ocr);

        harness.advanceClockBy(WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS + 1L);
        assertFalse(harness.tryGesture(oldFrame));
        assertEquals(1, harness.admissionRejectCount());
        assertEquals(1, harness.pendingRescanCount());
        assertEquals(1_500L, harness.nextScheduledDelayMillis());

        harness.advanceClockBy(1_499L);
        assertEquals(0, harness.rescanCount());
        harness.advanceClockBy(1L);
        assertEquals(1, harness.rescanCount());

        ServiceWorkflowHarness.Capture freshCapture = harness.capture();
        ActionAdmission.FrameContext freshFrame = harness.completeOcr(harness.startOcr(freshCapture));
        assertTrue(harness.tryGesture(freshFrame));
        assertEquals(1, harness.gestureCount());
    }

    @Test
    public void foregroundPackageOrWindowChangesNeverDispatchGesture() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext frame = harness.completeOcr(
                harness.startOcr(harness.capture()));

        harness.setPackageName("com.example.other");
        assertFalse(harness.tryGesture(frame));
        assertEquals(ActionAdmission.RejectionReason.PACKAGE, harness.lastRejectionReason());
        harness.restoreGameWindow();
        frame = harness.completeOcr(harness.startOcr(harness.capture()));
        harness.setWindowId(13);
        assertFalse(harness.tryGesture(frame));
        assertEquals(ActionAdmission.RejectionReason.WINDOW_ID, harness.lastRejectionReason());
        harness.restoreGameWindow();
        frame = harness.completeOcr(harness.startOcr(harness.capture()));
        harness.setBounds(new CaptureGeometry.Bounds(40, 120, 1120, 2520));
        assertFalse(harness.tryGesture(frame));
        assertEquals(ActionAdmission.RejectionReason.WINDOW_BOUNDS, harness.lastRejectionReason());

        assertEquals(0, harness.gestureCount());
    }

    @Test
    public void unavailableRootCannotBeAuthorizedByRecentPackageHistory() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext frame = harness.completeOcr(
                harness.startOcr(harness.capture()));

        harness.setWindowUnavailable();
        assertFalse(harness.tryGesture(frame));
        assertEquals(ActionAdmission.RejectionReason.PACKAGE, harness.lastRejectionReason());
        assertEquals(0, harness.gestureCount());
    }

    @Test
    public void pauseBetweenOcrAndGestureRejectsTheFrame() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext frame = harness.completeOcr(
                harness.startOcr(harness.capture()));

        harness.pause();

        assertFalse(harness.tryGesture(frame));
        assertEquals(0, harness.gestureCount());
        assertFalse(harness.busy());
        assertEquals(0, harness.pendingRescanCount());
    }

    @Test
    public void generationChangeDuringOcrDropsLateCallbackBeforeParsing() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ServiceWorkflowHarness.OcrRequest oldOcr = harness.startOcr(harness.capture());

        harness.restart();

        assertNull(harness.completeOcr(oldOcr));
        assertEquals(0, harness.parsedOcrCount());
        assertEquals(0, harness.gestureCount());

        ActionAdmission.FrameContext freshFrame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        assertTrue(harness.tryGesture(freshFrame));
    }

    @Test
    public void requestEpochIsRetainedThroughOcrAndRejectsChangedEpochBeforeWorkflowHandling() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ServiceWorkflowHarness.OcrRequest request = harness.startOcr(harness.capture());

        harness.invalidateAdmissionEpoch();

        assertNull(harness.completeOcr(request));
        assertEquals(ActionAdmission.RejectionReason.ADMISSION_EPOCH,
                harness.lastRejectionReason());
        assertEquals(0, harness.parsedOcrCount());
        assertEquals(0, harness.gestureCount());
    }

    @Test
    public void destroyClearsPendingScreenshotAndScheduledAction() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        harness.capture();
        assertTrue(harness.pendingScreenshotCount() > 0);

        harness.destroy();
        harness.advanceClockBy(10_000L);

        assertEquals(0, harness.pendingScreenshotCount());
        assertEquals(0, harness.rescanCount());
        assertEquals(0, harness.gestureCount());
        assertFalse(harness.running());
    }

    @Test
    public void repeatedStartStopDoesNotLeaveDuplicateScanTasks() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();

        harness.start();
        assertEquals(1, harness.pendingTaskCount());
        harness.stop();
        assertEquals(0, harness.pendingTaskCount());
        harness.start();
        assertEquals(1, harness.pendingTaskCount());
        harness.advanceClockBy(200L);
        assertEquals(1, harness.rescanCount());
    }

    @Test
    public void admissionRejectionClearsBusyAndSchedulesOnlyOneRescan() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext frame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        harness.setPackageName("com.example.other");
        harness.setBusy(true);

        assertFalse(harness.tryGesture(frame));
        assertFalse(harness.busy());
        assertEquals(1, harness.pendingRescanCount());
        assertFalse(harness.tryGesture(frame));
        assertEquals(1, harness.pendingRescanCount());
    }

    @Test
    public void rejectedFrameCannotBeReusedAfterEpochOrCaptureChanges() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext oldFrame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        harness.setPackageName("com.example.other");
        assertFalse(harness.tryGesture(oldFrame));

        harness.restoreGameWindow();
        assertFalse(harness.tryGesture(oldFrame));
        assertEquals(0, harness.gestureCount());
        ActionAdmission.FrameContext freshFrame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        assertTrue(harness.tryGesture(freshFrame));
        assertEquals(1, harness.gestureCount());
    }

    @Test
    public void returnRewardTargetFromOlderCaptureCannotAuthorizeTap() {
        ServiceWorkflowHarness harness = new ServiceWorkflowHarness();
        harness.start();
        ActionAdmission.FrameContext targetFrame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        assertNotNull(harness.completeOcr(harness.startOcr(harness.capture())));

        assertFalse(harness.tryGesture(targetFrame));
        assertEquals(0, harness.gestureCount());
        ActionAdmission.FrameContext freshFrame = harness.completeOcr(
                harness.startOcr(harness.capture()));
        assertTrue(harness.tryGesture(freshFrame));
        assertEquals(1, harness.gestureCount());
    }

    private static final class ServiceWorkflowHarness {
        private record Capture(
                long generation,
                long sequence,
                long capturedAt,
                long requestId,
                long admissionEpoch) {}
        private record OcrRequest(OcrScanner.Transaction transaction, Capture capture) {}

        private final WorkflowTestSupport.FakeClock clock = new WorkflowTestSupport.FakeClock();
        private final WorkflowTestSupport.FakeScheduler scheduler =
                new WorkflowTestSupport.FakeScheduler(clock);
        private final ScreenshotRequestQueue<Long> screenshotQueue = new ScreenshotRequestQueue<>();
        private final OcrScanner.TransactionRegistry ocrRegistry =
                new OcrScanner.TransactionRegistry();
        private final AdmissionDiagnostics admissionDiagnostics = new AdmissionDiagnostics();
        private final Runnable scanTask = this::runScheduledScan;
        private long generation;
        private long captureSequence;
        private long latestOcrRequestSequence;
        private long admissionEpoch;
        private boolean running;
        private boolean destroyed;
        private boolean busy;
        private String packageName = WorkflowTestSupport.GAME_PACKAGE;
        private CaptureGeometry.Bounds bounds = WorkflowTestSupport.GAME_BOUNDS;
        private int windowId = WorkflowTestSupport.GAME_WINDOW_ID;
        private int parsedOcrCount;
        private int gestureCount;
        private int rescanCount;
        private int admissionRejectCount;
        private ActionAdmission.RejectionReason lastRejectionReason =
                ActionAdmission.RejectionReason.NONE;

        void start() {
            if (destroyed) {
                return;
            }
            if (running) {
                return;
            }
            generation++;
            running = true;
            busy = false;
            packageName = WorkflowTestSupport.GAME_PACKAGE;
            bounds = WorkflowTestSupport.GAME_BOUNDS;
            windowId = WorkflowTestSupport.GAME_WINDOW_ID;
            scheduler.removeCallbacks(scanTask);
            scheduler.postDelayed(scanTask, 200L);
        }

        void stop() {
            running = false;
            busy = false;
            generation++;
            latestOcrRequestSequence = 0L;
            admissionEpoch++;
            screenshotQueue.clear();
            OcrScanner.Transaction active = ocrRegistry.active();
            if (active != null) {
                active.tryFinish(OcrScanner.TerminalState.CANCELLED);
                ocrRegistry.clear(active);
            }
            scheduler.clear();
        }

        void pause() {
            stop();
        }

        void restart() {
            stop();
            start();
        }

        void destroy() {
            stop();
            destroyed = true;
        }

        Capture capture() {
            if (!running || busy) {
                throw new AssertionError("Capture was requested while unavailable");
            }
            // A direct test capture consumes the initial delayed scan that it represents.
            scheduler.removeCallbacks(scanTask);
            busy = true;
            long sequence = ++captureSequence;
            screenshotQueue.enqueue(sequence);
            ScreenshotRequestQueue.Entry<Long> entry = screenshotQueue.startNext();
            if (entry == null) {
                throw new AssertionError("Screenshot queue did not start the request");
            }
            return new Capture(
                    generation,
                    sequence,
                    clock.uptimeMillis(),
                    entry.id(),
                    admissionEpoch);
        }

        OcrRequest startOcr(Capture capture) {
            if (capture == null) {
                throw new AssertionError("Capture is required");
            }
            if (!screenshotQueue.finish(capture.requestId())) {
                throw new AssertionError("Screenshot callback was not the active request");
            }
            OcrScanner.Transaction transaction = ocrRegistry.begin(
                    capture.generation(), capture.sequence());
            return new OcrRequest(transaction, capture);
        }

        ActionAdmission.FrameContext completeOcr(OcrRequest request) {
            if (request == null || !OcrScanner.canProcessCallback(
                    request.transaction(),
                    () -> ocrRegistry.acceptsCallback(request.transaction()))) {
                return null;
            }
            if (!request.transaction().tryFinish(OcrScanner.TerminalState.SUCCESS)
                    || !ocrRegistry.clear(request.transaction())) {
                return null;
            }
            latestOcrRequestSequence = request.transaction().id().ocrRequestSequence();
            busy = false;
            ActionAdmission.FrameContext frame = WorkflowTestSupport.frame(
                    request.transaction().id().runGeneration(),
                    request.transaction().id().captureSequence(),
                    request.transaction().id().ocrRequestSequence(),
                    request.capture().capturedAt(),
                    WorkflowTestSupport.GAME_PACKAGE,
                    request.capture().sequence() == captureSequence ? bounds
                            : WorkflowTestSupport.GAME_BOUNDS,
                    windowId,
                    request.capture().admissionEpoch());
            ActionAdmission.Decision admission = ActionAdmission.evaluate(
                    frame,
                    WorkflowTestSupport.current(
                            running && !destroyed,
                            generation,
                            captureSequence,
                            latestOcrRequestSequence,
                            clock.uptimeMillis(),
                            packageName,
                            bounds,
                            windowId,
                            admissionEpoch),
                    WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS);
            if (!admission.allowed()) {
                lastRejectionReason = admission.reason();
                admissionRejectCount++;
                return null;
            }
            parsedOcrCount++;
            return frame;
        }

        boolean tryGesture(ActionAdmission.FrameContext frame) {
            ActionAdmission.Decision decision = ActionAdmission.evaluate(
                    frame,
                    WorkflowTestSupport.current(
                            running && !destroyed,
                            generation,
                            captureSequence,
                            latestOcrRequestSequence,
                            clock.uptimeMillis(),
                            packageName,
                            bounds,
                            windowId,
                            admissionEpoch),
                    WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS);
            admissionDiagnostics.recordActionCheck(
                    "workflow-test", frame, clock.uptimeMillis(), decision);
            if (decision.allowed()) {
                gestureCount++;
                busy = false;
                lastRejectionReason = ActionAdmission.RejectionReason.NONE;
                return true;
            }
            lastRejectionReason = decision.reason();
            admissionRejectCount++;
            dropStaleAction();
            return false;
        }

        private void dropStaleAction() {
            busy = false;
            if (!running || destroyed) {
                return;
            }
            admissionEpoch++;
            scheduler.removeCallbacks(scanTask);
            scheduler.postDelayed(scanTask, 1_500L);
        }

        private void runScheduledScan() {
            if (running && !destroyed && !busy) {
                rescanCount++;
            }
        }

        void advanceClockBy(long durationMillis) {
            scheduler.advanceBy(durationMillis);
        }

        void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        void setWindowUnavailable() {
            packageName = null;
            bounds = null;
            windowId = -1;
        }

        void setWindowId(int windowId) {
            this.windowId = windowId;
        }

        void setBounds(CaptureGeometry.Bounds bounds) {
            this.bounds = bounds;
        }

        void restoreGameWindow() {
            packageName = WorkflowTestSupport.GAME_PACKAGE;
            bounds = WorkflowTestSupport.GAME_BOUNDS;
            windowId = WorkflowTestSupport.GAME_WINDOW_ID;
        }

        void setBusy(boolean busy) {
            this.busy = busy;
        }

        void invalidateAdmissionEpoch() {
            admissionEpoch++;
        }

        boolean running() { return running; }
        boolean busy() { return busy; }
        int gestureCount() { return gestureCount; }
        int parsedOcrCount() { return parsedOcrCount; }
        int admissionRejectCount() { return admissionRejectCount; }
        int rescanCount() { return rescanCount; }
        ActionAdmission.RejectionReason lastRejectionReason() { return lastRejectionReason; }
        int pendingScreenshotCount() {
            return screenshotQueue.active() == null ? 0 : 1;
        }
        int pendingRescanCount() { return scheduler.nextDelayMillis() < 0L ? 0 : 1; }
        int pendingTaskCount() { return scheduler.pendingTaskCount(); }
        long nextScheduledDelayMillis() { return scheduler.nextDelayMillis(); }
    }
}
