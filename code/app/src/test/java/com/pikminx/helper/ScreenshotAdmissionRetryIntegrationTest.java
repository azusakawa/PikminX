package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Exercises screenshot failure budget independently from stale-frame admission recovery. */
public final class ScreenshotAdmissionRetryIntegrationTest {
    @Test
    public void transientScreenshotFailuresUseTheBounded500To4000ScheduleThenStop() {
        RetryHarness harness = new RetryHarness();

        assertEquals(500L, harness.screenshotFailure().delayMillis());
        assertEquals(1_000L, harness.screenshotFailure().delayMillis());
        assertEquals(2_000L, harness.screenshotFailure().delayMillis());
        assertEquals(4_000L, harness.screenshotFailure().delayMillis());
        assertFalse(harness.screenshotFailure().retry());
        assertTrue(harness.stopped());
    }

    @Test
    public void successfulScreenshotResetsFailureBudget() {
        RetryHarness harness = new RetryHarness();
        harness.screenshotFailure();
        harness.screenshotFailure();
        harness.screenshotFailure();

        harness.screenshotSuccess();

        assertEquals(500L, harness.screenshotFailure().delayMillis());
        assertEquals(1, harness.screenshotFailureCount());
    }

    @Test
    public void ocrAndAdmissionRejectionDoNotConsumeScreenshotFailureBudget() {
        RetryHarness harness = new RetryHarness();
        harness.screenshotFailure();
        harness.screenshotFailure();
        harness.ocrFailure();
        harness.staleAdmissionReject();

        assertEquals(2, harness.screenshotFailureCount());
        assertEquals(2_000L, harness.screenshotFailure().delayMillis());
    }

    @Test
    public void staleAdmissionUsesOne1500MillisecondRescanWithoutAHotLoop() {
        RetryHarness harness = new RetryHarness();

        harness.staleAdmissionReject();
        assertEquals(1, harness.pendingRescanCount());
        assertEquals(1_500L, harness.nextDelayMillis());
        harness.advanceBy(1_499L);
        assertEquals(0, harness.rescanCount());
        harness.advanceBy(1L);
        assertEquals(1, harness.rescanCount());

        harness.staleAdmissionReject();
        assertEquals(1, harness.pendingRescanCount());
        assertEquals(1_500L, harness.nextDelayMillis());
        assertEquals(1, harness.rescanCount());
    }

    @Test
    public void pauseClearsPendingRescanAndStopsFutureRetry() {
        RetryHarness harness = new RetryHarness();
        harness.staleAdmissionReject();
        harness.screenshotFailure();

        harness.pause();
        harness.advanceBy(10_000L);

        assertEquals(0, harness.rescanCount());
        assertEquals(0, harness.pendingRescanCount());
        assertFalse(harness.running());
        assertEquals(0, harness.screenshotFailureCount());
    }

    @Test
    public void lateScreenshotCallbackCannotConsumeACompletedRequestOrScheduleRetry() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        queue.enqueue("capture");
        ScreenshotRequestQueue.Entry<String> active = queue.startNext();
        RetryHarness harness = new RetryHarness();

        assertFalse(queue.finish(active.id() + 1L));
        assertEquals(0, harness.screenshotFailureCount());
        assertTrue(queue.finish(active.id()));
        assertFalse(queue.finish(active.id()));
    }

    private static final class RetryHarness {
        private final WorkflowTestSupport.FakeClock clock = new WorkflowTestSupport.FakeClock(3_001L);
        private final WorkflowTestSupport.FakeScheduler scheduler =
                new WorkflowTestSupport.FakeScheduler(clock);
        private boolean running = true;
        private boolean stopped;
        private int screenshotFailureCount;
        private int rescanCount;
        private final Runnable rescanTask = () -> {
            if (running) {
                rescanCount++;
            }
        };

        ScreenshotRetryPolicy.Decision screenshotFailure() {
            if (!running) {
                return new ScreenshotRetryPolicy.Decision(false, 0L);
            }
            ScreenshotRetryPolicy.Decision decision = ScreenshotRetryPolicy.afterFailure(
                    ScreenshotRetryPolicy.FailureType.TRANSIENT,
                    ++screenshotFailureCount);
            if (decision.retry()) {
                schedule(decision.delayMillis());
            } else {
                stopped = true;
                running = false;
                scheduler.clear();
            }
            return decision;
        }

        void screenshotSuccess() {
            screenshotFailureCount = 0;
        }

        void ocrFailure() {
            // OCR failure follows scheduleNext; it is deliberately not screenshot budget state.
        }

        void staleAdmissionReject() {
            if (!running) {
                return;
            }
            ActionAdmission.Decision decision = ActionAdmission.evaluate(
                    WorkflowTestSupport.frame(
                            1L,
                            1L,
                            1L,
                            0L,
                            WorkflowTestSupport.GAME_PACKAGE,
                            WorkflowTestSupport.GAME_BOUNDS,
                            WorkflowTestSupport.GAME_WINDOW_ID,
                            0L),
                    WorkflowTestSupport.current(
                            true,
                            1L,
                            1L,
                            1L,
                            clock.uptimeMillis(),
                            WorkflowTestSupport.GAME_PACKAGE,
                            WorkflowTestSupport.GAME_BOUNDS,
                            WorkflowTestSupport.GAME_WINDOW_ID,
                            0L),
                    WorkflowTestSupport.MAX_ACTIONABLE_FRAME_AGE_MILLIS);
            if (decision.allowed()) {
                throw new AssertionError("Test frame was expected to be stale");
            }
            schedule(1_500L);
        }

        void pause() {
            running = false;
            screenshotFailureCount = 0;
            scheduler.clear();
        }

        void advanceBy(long durationMillis) {
            scheduler.advanceBy(durationMillis);
        }

        private void schedule(long delayMillis) {
            scheduler.removeCallbacks(rescanTask);
            scheduler.postDelayed(rescanTask, delayMillis);
        }

        boolean running() { return running; }
        boolean stopped() { return stopped; }
        int screenshotFailureCount() { return screenshotFailureCount; }
        int rescanCount() { return rescanCount; }
        int pendingRescanCount() { return scheduler.pendingTaskCount(); }
        long nextDelayMillis() { return scheduler.nextDelayMillis(); }
    }
}
