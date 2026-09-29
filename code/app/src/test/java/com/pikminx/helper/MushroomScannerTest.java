package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;

/** Focused page-gate, bounded-job, and stale-delivery regressions for MushroomScanner. */
public final class MushroomScannerTest {
    @Test
    public void rejectedPageNeverInvokesTheDetector() {
        FakeWorker worker = new FakeWorker();
        FakeMainThread main = new FakeMainThread();
        MushroomScanner scanner = new MushroomScanner(worker, main);
        RecordingListener listener = new RecordingListener();
        FakeFrame frame = new FakeFrame();
        int[] detectorCalls = {0};

        boolean submitted = scanner.scan(
                session(),
                frame,
                null,
                List.of(token("步數 5 朵蘑菇", 70, 1460)),
                1080,
                2400,
                true,
                null,
                () -> {
                    detectorCalls[0]++;
                    return result();
                },
                listener);

        assertFalse(submitted);
        assertEquals(0, detectorCalls[0]);
        assertEquals(1, listener.pageRejectedCount);
        assertEquals(0, listener.resultCount);
    }

    @Test
    public void eligiblePageRunsOneBoundedAnalysisAndPublishesExactlyOnce() {
        FakeWorker worker = new FakeWorker();
        FakeMainThread main = new FakeMainThread();
        MushroomScanner scanner = new MushroomScanner(worker, main);
        RecordingListener listener = new RecordingListener();
        FakeFrame frame = new FakeFrame();
        int[] detectorCalls = {0};

        assertTrue(scanner.scan(
                session(),
                frame,
                null,
                eligibleTokens(),
                1080,
                2400,
                true,
                null,
                () -> {
                    detectorCalls[0]++;
                    return result();
                },
                listener));
        assertFalse(scanner.scan(
                session(),
                new FakeFrame(),
                null,
                eligibleTokens(),
                1080,
                2400,
                true,
                null,
                () -> result(),
                listener));

        worker.runNext();
        main.runNext();

        assertEquals(1, detectorCalls[0]);
        assertEquals(1, listener.analysisStartedCount);
        assertEquals(1, listener.resultCount);
        assertEquals(0, listener.staleCount);
        assertEquals(2, frame.retainCount);
        assertEquals(2, frame.releaseCount);
        assertEquals("ANALYSIS_COMPLETE", frame.finishOutcome);
    }

    @Test
    public void stoppedSessionRejectsLateWorkerResultWithoutPublication() {
        FakeWorker worker = new FakeWorker();
        FakeMainThread main = new FakeMainThread();
        MushroomScanner scanner = new MushroomScanner(worker, main);
        RecordingListener listener = new RecordingListener();
        FakeFrame frame = new FakeFrame();

        assertTrue(scanner.scan(
                session(),
                frame,
                null,
                eligibleTokens(),
                1080,
                2400,
                true,
                null,
                MushroomScannerTest::result,
                listener));
        worker.runNext();
        listener.current = false;
        main.runNext();

        assertEquals(0, listener.resultCount);
        assertEquals(1, listener.staleCount);
        assertEquals("ANALYSIS_STALE", frame.finishOutcome);
    }

    @Test
    public void cancelRejectsAnAlreadyPostedResultAndReleasesBothSourceHolds() {
        FakeWorker worker = new FakeWorker();
        FakeMainThread main = new FakeMainThread();
        MushroomScanner scanner = new MushroomScanner(worker, main);
        RecordingListener listener = new RecordingListener();
        FakeFrame frame = new FakeFrame();

        assertTrue(scanner.scan(
                session(),
                frame,
                null,
                eligibleTokens(),
                1080,
                2400,
                true,
                null,
                MushroomScannerTest::result,
                listener));
        worker.runNext();
        scanner.cancel();
        main.runNext();

        assertEquals(0, listener.resultCount);
        assertEquals(1, listener.staleCount);
        assertEquals(2, frame.retainCount);
        assertEquals(2, frame.releaseCount);
        assertEquals("ANALYSIS_STALE", frame.finishOutcome);
    }

    private static MushroomWorkflowCoordinator.Session session() {
        return new MushroomWorkflowCoordinator.Session(
                7L, 3L, 41L, MushroomWorkflowCoordinator.Origin.NORMAL);
    }

    private static List<PetalMatcher.Token> eligibleTokens() {
        return List.of(token("蘑菇", 70, 1010), token("今天還剩下 3 次", 70, 1070));
    }

    private static PetalMatcher.Token token(String text, int left, int top) {
        return new PetalMatcher.Token(text, left, top, left + 120, top + 48);
    }

    private static MushroomDetectionResult result() {
        return new MushroomDetectionResult(
                List.of(), new MushroomDetectionResult.Stats(0, 0, 0, 0, 1080, 2400, 1L));
    }

    private static final class FakeWorker implements MushroomScanner.Worker {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public boolean submit(Runnable action) {
            tasks.add(action);
            return true;
        }

        @Override
        public int depth() {
            return tasks.size();
        }

        @Override
        public int highWater() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class FakeMainThread implements MushroomScanner.MainThread {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public boolean post(Runnable action) {
            tasks.add(action);
            return true;
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class FakeFrame implements MushroomScanner.FrameHandle {
        int retainCount;
        int releaseCount;
        String finishOutcome;

        @Override
        public boolean deferPostProcessing() {
            return true;
        }

        @Override
        public void cancelPostProcessingDeferral() { }

        @Override
        public boolean retainSourceForAnalysis() {
            retainCount++;
            return true;
        }

        @Override
        public void releaseSourceForAnalysis() {
            releaseCount++;
        }

        @Override
        public void finishDeferredPostProcessing(String outcome) {
            finishOutcome = outcome;
        }
    }

    private static final class RecordingListener implements MushroomScanner.Listener {
        boolean current = true;
        int pageRejectedCount;
        int analysisStartedCount;
        int resultCount;
        int staleCount;

        @Override
        public boolean isCurrent(MushroomWorkflowCoordinator.Session session) {
            return current;
        }

        @Override
        public void onPageRejected(
                MushroomWorkflowCoordinator.Session session, MushroomPageGate.Decision decision) {
            pageRejectedCount++;
        }

        @Override
        public void onAnalysisStarted(MushroomWorkflowCoordinator.Session session) {
            analysisStartedCount++;
        }

        @Override
        public void onAnalysisResult(
                MushroomWorkflowCoordinator.Session session,
                OcrScan.Frame frame,
                MushroomDetectionResult result,
                RuntimeException error) {
            resultCount++;
        }

        @Override
        public void onAnalysisUnavailable(MushroomWorkflowCoordinator.Session session) { }

        @Override
        public void onStaleResult(MushroomWorkflowCoordinator.Session session) {
            staleCount++;
        }

        @Override
        public void onQueueState(int depth, int highWater) { }

        @Override
        public void onQueueRejected(MushroomWorkflowCoordinator.Session session) { }
    }
}
