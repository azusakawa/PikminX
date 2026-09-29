package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class OcrRuntimeDiagnosticsTest {
    @Test
    public void recordsOrderedTimelineAndFiniteStageMetrics() {
        AtomicLong now = new AtomicLong(100L);
        OcrRuntimeDiagnostics diagnostics = new OcrRuntimeDiagnostics(now::get);
        OcrScan.TransactionId id = new OcrScan.TransactionId(4L, 8L, 12L);

        diagnostics.begin(id, "FULL_CHINESE", 90L, 100L);
        now.set(110L);
        diagnostics.markQueueEnter(id);
        now.set(120L);
        diagnostics.markWorkerStart(id);
        diagnostics.markRecognizerStart(id, "CHINESE");
        now.set(125L);
        diagnostics.markCallbackEnqueue(id, "CHINESE");
        now.set(130L);
        diagnostics.markCallbackStart(id, "CHINESE");
        diagnostics.markRecognizerEnd(id, "CHINESE");
        now.set(140L);
        diagnostics.markTokenParsingStart(id, "CHINESE");
        now.set(145L);
        diagnostics.markTokenParsingEnd(id, "CHINESE");
        diagnostics.markAllRecognizersComplete(id);
        now.set(150L);
        diagnostics.markMainExecutorEnqueue(id);
        now.set(155L);
        diagnostics.markMainExecutorStart(id);
        diagnostics.markOcrCompletion(id);
        now.set(160L);
        diagnostics.markAdmission(id);
        diagnostics.markHandleTokensStart(id);
        now.set(170L);
        diagnostics.markHandleTokensEnd(id);
        diagnostics.markGesture(4L, 8L, 12L, true);
        OcrRuntimeDiagnostics.Completion completion = diagnostics.finish(id, "SUCCESS", 180L);

        assertFalse(completion.shouldLogTimeline());
        assertTrue(completion.compactTimeline().indexOf("CAPTURE@90")
                < completion.compactTimeline().indexOf("FINAL_COMPLETION@180"));
        assertEquals(10L, diagnostics.snapshot(200L)
                .metric(OcrRuntimeDiagnostics.Metric.OCR_QUEUE_WAIT).p50Millis());
        assertEquals(10L, diagnostics.snapshot(200L)
                .metric(OcrRuntimeDiagnostics.Metric.HANDLE_TOKENS).p50Millis());
        assertEquals(1L, diagnostics.snapshot(200L)
                .count(OcrRuntimeDiagnostics.Counter.GESTURES_DISPATCHED));
    }

    @Test
    public void timeoutReturnsOneFullTimelineAndCountsTimeout() {
        AtomicLong now = new AtomicLong(100L);
        OcrRuntimeDiagnostics diagnostics = new OcrRuntimeDiagnostics(now::get);
        OcrScan.TransactionId id = new OcrScan.TransactionId(2L, 3L, 4L);
        diagnostics.begin(id, "FULL_MULTILINGUAL", 100L, 101L);
        now.set(102L);
        diagnostics.markQueueEnter(id);
        now.set(103L);
        diagnostics.markTimeout(id);
        OcrRuntimeDiagnostics.Completion completion = diagnostics.finish(id, "TIMEOUT", 6_200L);

        assertTrue(completion.shouldLogTimeline());
        assertTrue(completion.compactTimeline().contains("transaction=4"));
        assertTrue(completion.compactTimeline().contains("generation=2"));
        assertTrue(completion.compactTimeline().contains("TIMEOUT"));
        assertEquals(1L, diagnostics.snapshot(6_300L)
                .count(OcrRuntimeDiagnostics.Counter.OCR_TIMEOUTS));
        assertEquals(0L, diagnostics.snapshot(6_300L).activeOcrTransactions());
    }

    @Test
    public void queueHighWaterAndStaleCallbacksRemainBounded() {
        AtomicLong now = new AtomicLong(10L);
        OcrRuntimeDiagnostics diagnostics = new OcrRuntimeDiagnostics(now::get);
        OcrScan.TransactionId id = new OcrScan.TransactionId(1L, 1L, 1L);
        diagnostics.begin(id, "FULL_CHINESE", 1L, 2L);
        ArrayList<Runnable> queued = new ArrayList<>();
        java.util.concurrent.Executor delegate = queued::add;
        java.util.concurrent.Executor executor = diagnostics.executorForRecognizer(
                id, "CHINESE", delegate);
        executor.execute(() -> { });
        executor.execute(() -> { });
        diagnostics.markStaleCallback();

        OcrRuntimeDiagnostics.Snapshot before = diagnostics.snapshot(20L);
        assertEquals(2L, before.ocrQueueDepth());
        assertEquals(2L, before.ocrQueueHighWater());
        assertEquals(1L, before.count(OcrRuntimeDiagnostics.Counter.STALE_CALLBACKS));
        assertNotNull(queued.get(0));

        queued.get(0).run();
        queued.get(1).run();
        assertEquals(0L, diagnostics.snapshot(30L).ocrQueueDepth());
    }

    @Test
    public void screenshotQueueGaugeReturnsToZeroAfterCompletion() {
        OcrRuntimeDiagnostics diagnostics = new OcrRuntimeDiagnostics(() -> 10L);

        diagnostics.recordScreenshot(10L, 1L, 1L);
        assertEquals(1L, diagnostics.snapshot(10L).screenshotQueueDepth());
        diagnostics.recordScreenshotQueueState(0L, 1L);

        OcrRuntimeDiagnostics.Snapshot snapshot = diagnostics.snapshot(20L);
        assertEquals(0L, snapshot.screenshotQueueDepth());
        assertEquals(1L, snapshot.screenshotQueueHighWater());
    }

    @Test
    public void resetClearsTimelineCountersAndMetrics() {
        OcrRuntimeDiagnostics diagnostics = new OcrRuntimeDiagnostics(() -> 10L);
        OcrScan.TransactionId id = new OcrScan.TransactionId(1L, 1L, 1L);
        diagnostics.begin(id, "FULL_CHINESE", 1L, 2L);
        diagnostics.markStaleCallback();
        diagnostics.reset(20L);

        OcrRuntimeDiagnostics.Snapshot snapshot = diagnostics.snapshot(30L);
        assertEquals(0L, snapshot.count(OcrRuntimeDiagnostics.Counter.STALE_CALLBACKS));
        assertEquals(0L, snapshot.activeOcrTransactions());
        assertEquals(0L, snapshot.metric(
                OcrRuntimeDiagnostics.Metric.OCR_QUEUE_WAIT).count());
    }
}
