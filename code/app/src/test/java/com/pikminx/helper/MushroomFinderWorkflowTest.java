package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Integration-shaped JVM coverage for the production Mushroom Finder coordination rules. */
public final class MushroomFinderWorkflowTest {
    @Test
    public void startAndValidPagePropagateMultipleResultsToTerminalState() {
        Harness harness = new Harness();
        harness.start();

        MushroomDetectionResult result = harness.analyze(
                List.of(token("蘑菇", 70, 1010), token("今天還剩下 3 次", 70, 1070)),
                resultWithHits(2));

        assertEquals(MushroomWorkflowState.RESULTS, harness.state());
        assertEquals(2, result.stats().outputCount());
        assertEquals(2, harness.displayedHitCount());
    }

    @Test
    public void validPageWithNoHitsIsStillAVisibleCompletedResult() {
        Harness harness = new Harness();
        harness.start();

        MushroomDetectionResult result = harness.analyze(
                List.of(token("派皮克敏出去探險吧", 340, 1480)), resultWithHits(0));

        assertTrue(harness.pageEligible());
        assertEquals(0, result.hits().size());
        assertEquals(MushroomWorkflowState.RESULTS, harness.state());
    }

    @Test
    public void invalidPageDoesNotRunDetectorOrReturnFalsePositiveHits() {
        Harness harness = new Harness();
        harness.start();

        MushroomDetectionResult result = harness.analyze(
                List.of(token("推數5朵蘑菇", 75, 1460), token("步數", 560, 1560)),
                resultWithHits(2));

        assertFalse(harness.pageEligible());
        assertEquals(MushroomWorkflowState.INVALID_PAGE, harness.state());
        assertEquals(0, harness.detectorRuns());
        assertEquals(0, result.hits().size());
    }

    @Test
    public void rescanCreatesFreshCaptureEpochAndStopIsTerminal() {
        Harness harness = new Harness();
        harness.start();
        harness.analyze(List.of(token("蘑菇", 70, 1010), token("今天還剩下 1 次", 70, 1070)),
                resultWithHits(1));
        long firstEpoch = harness.epoch();

        harness.rescan();
        assertEquals(MushroomWorkflowState.RETRY_WAIT, harness.state());
        assertTrue(harness.epoch() > firstEpoch);
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(harness.state()));

        harness.stop();
        assertEquals(MushroomWorkflowState.STOPPED, harness.state());
        assertFalse(harness.running());
        assertFalse(MushroomWorkflowPolicy.canRequestCapture(harness.state()));
    }

    @Test
    public void staleResultAfterGenerationChangeCannotReopenResults() {
        Harness harness = new Harness();
        harness.start();
        long oldGeneration = harness.generation();
        harness.restart();

        assertFalse(harness.deliver(oldGeneration, resultWithHits(1)));
        assertEquals(MushroomWorkflowState.STARTING, harness.state());
        assertEquals(0, harness.displayedHitCount());
    }

    @Test
    public void homeOrUnavailableWindowRejectsActionAdmissionAndAllowsRecovery() {
        Harness harness = new Harness();
        harness.start();
        ActionAdmission.FrameContext frame = harness.captureContext();

        harness.setWindowUnavailable();
        ActionAdmission.Decision rejected = harness.admit(frame);
        assertFalse(rejected.allowed());
        assertEquals(ActionAdmission.RejectionReason.PACKAGE, rejected.reason());
        assertEquals(0, harness.gestureCount());

        harness.restoreWindow();
        harness.rescan();
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(harness.state()));
    }

    @Test
    public void waitingForGameCanResumeWithoutPermanentBusyState() {
        Harness harness = new Harness();
        harness.start();
        harness.setWindowUnavailable();
        harness.waitForGame();
        assertEquals(MushroomWorkflowState.WAITING_FOR_GAME, harness.state());
        assertFalse(harness.busy());
        harness.restoreWindow();
        assertTrue(MushroomWorkflowPolicy.canRequestCapture(harness.state()));
    }

    private static MushroomDetectionResult resultWithHits(int count) {
        return new MushroomDetectionResult(
                List.of(),
                new MushroomDetectionResult.Stats(1, 10, count, count, 432, 936, 1L));
    }

    private static PetalMatcher.Token token(String text, int left, int top) {
        return new PetalMatcher.Token(text, left, top, left + 120, top + 48);
    }

    private static final class Harness {
        private final CaptureGeometry.Bounds gameBounds =
                new CaptureGeometry.Bounds(40, 100, 1120, 2500);
        private long generation;
        private long captureSequence;
        private long epoch;
        private boolean running;
        private boolean busy;
        private MushroomWorkflowState state = MushroomWorkflowState.IDLE;
        private boolean pageEligible;
        private int detectorRuns;
        private int displayedHitCount;
        private int gestureCount;
        private String packageName = "com.nianticlabs.pikmin";
        private CaptureGeometry.Bounds currentBounds = gameBounds;
        private int windowId = 12;

        void start() {
            generation++;
            running = true;
            busy = false;
            state = MushroomWorkflowState.STARTING;
            captureSequence++;
        }

        void restart() {
            generation++;
            running = true;
            busy = false;
            state = MushroomWorkflowState.STARTING;
            displayedHitCount = 0;
        }

        MushroomDetectionResult analyze(
                List<PetalMatcher.Token> tokens, MushroomDetectionResult detectorResult) {
            busy = true;
            state = MushroomWorkflowState.ANALYZING;
            MushroomPageGate.Decision decision = MushroomPageGate.evaluate(tokens);
            pageEligible = decision.eligible();
            if (!pageEligible) {
                busy = false;
                state = MushroomWorkflowState.INVALID_PAGE;
                return new MushroomDetectionResult(List.of(), detectorResult.stats());
            }
            detectorRuns++;
            busy = false;
            displayedHitCount = detectorResult.stats().outputCount();
            state = MushroomWorkflowState.RESULTS;
            return detectorResult;
        }

        ActionAdmission.FrameContext captureContext() {
            captureSequence++;
            busy = true;
            return new ActionAdmission.FrameContext(
                    generation,
                    captureSequence,
                    1L,
                    100L,
                    "com.nianticlabs.pikmin",
                    gameBounds,
                    12,
                    epoch);
        }

        ActionAdmission.Decision admit(ActionAdmission.FrameContext frame) {
            ActionAdmission.Decision decision = ActionAdmission.evaluate(
                    frame,
                    new ActionAdmission.CurrentState(
                            running,
                            generation,
                            captureSequence,
                            1L,
                            100L,
                            packageName,
                            currentBounds,
                            windowId,
                            epoch),
                    3_000L);
            if (decision.allowed()) {
                gestureCount++;
            }
            return decision;
        }

        boolean deliver(long resultGeneration, MushroomDetectionResult result) {
            if (!running || resultGeneration != generation) {
                return false;
            }
            displayedHitCount = result.stats().outputCount();
            state = MushroomWorkflowState.RESULTS;
            return true;
        }

        void rescan() {
            if (!running) {
                return;
            }
            epoch++;
            busy = false;
            state = MushroomWorkflowState.RETRY_WAIT;
            captureSequence++;
        }

        void stop() {
            running = false;
            busy = false;
            generation++;
            epoch++;
            state = MushroomWorkflowState.STOPPED;
        }

        void waitForGame() {
            busy = false;
            state = MushroomWorkflowState.WAITING_FOR_GAME;
        }

        void setWindowUnavailable() {
            packageName = null;
            currentBounds = null;
            windowId = -1;
        }

        void restoreWindow() {
            packageName = "com.nianticlabs.pikmin";
            currentBounds = gameBounds;
            windowId = 12;
        }

        long generation() { return generation; }
        long epoch() { return epoch; }
        boolean running() { return running; }
        boolean busy() { return busy; }
        boolean pageEligible() { return pageEligible; }
        int detectorRuns() { return detectorRuns; }
        int displayedHitCount() { return displayedHitCount; }
        int gestureCount() { return gestureCount; }
        MushroomWorkflowState state() { return state; }
    }
}
