package com.pikminx.helper;

import com.pikminx.helper.platform.diagnostics.WorkflowDiagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WorkflowDiagnosticsTest {
    @Test
    public void keepsBoundedLatencyAndWorkflowCounters() {
        WorkflowDiagnostics diagnostics = new WorkflowDiagnostics();

        diagnostics.recordCount(WorkflowDiagnostics.Counter.SCREENSHOT_REQUESTS);
        diagnostics.recordCount(WorkflowDiagnostics.Counter.OCR_TRANSACTIONS, 2L);
        diagnostics.recordDuration(WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK, 140L);
        diagnostics.recordDuration(WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK, 2_400L);
        diagnostics.recordAdmission(new ActionAdmission.Decision(
                true, ActionAdmission.RejectionReason.NONE));
        diagnostics.recordAdmission(new ActionAdmission.Decision(
                false, ActionAdmission.RejectionReason.CAPTURE_AGE));

        WorkflowDiagnostics.Snapshot snapshot = diagnostics.snapshot();

        assertEquals(1L, snapshot.count(WorkflowDiagnostics.Counter.SCREENSHOT_REQUESTS));
        assertEquals(2L, snapshot.count(WorkflowDiagnostics.Counter.OCR_TRANSACTIONS));
        assertEquals(1L, snapshot.count(WorkflowDiagnostics.Counter.ACTIONS_ADMITTED));
        assertEquals(1L, snapshot.count(WorkflowDiagnostics.Counter.ADMISSION_REJECTIONS));
        assertEquals(2L, snapshot.metric(
                WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK).count());
        assertEquals(3_000L, snapshot.metric(
                WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK).p99UpperBoundMillis());
        assertEquals(140L, snapshot.metric(
                WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK).p50Millis());
        assertEquals(2_400L, snapshot.metric(
                WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK).p95Millis());
        assertTrue(!snapshot.metric(
                WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK).lastThreadName().isBlank());
        assertTrue(diagnostics.summary().contains("screenshots=1"));
    }

    @Test
    public void overflowIsFiniteAndReportedSeparately() {
        WorkflowDiagnostics diagnostics = new WorkflowDiagnostics();
        diagnostics.recordDuration(
                WorkflowDiagnostics.Metric.OCR_CALLBACK,
                BoundedLatencyStats.MAX_TRACKED_MILLIS + 1L);

        WorkflowDiagnostics.MetricSnapshot metric = diagnostics.snapshot().metric(
                WorkflowDiagnostics.Metric.OCR_CALLBACK);

        assertEquals(BoundedLatencyStats.MAX_TRACKED_MILLIS, metric.maxMillis());
        assertEquals(1L, metric.overflowCount());
        assertTrue(!diagnostics.summary().contains("9223372036854775807"));
    }

    @Test
    public void resetClearsOnlyLocalDiagnostics() {
        WorkflowDiagnostics diagnostics = new WorkflowDiagnostics();
        diagnostics.recordCount(WorkflowDiagnostics.Counter.ACTIONS_DISPATCHED);
        diagnostics.recordDuration(WorkflowDiagnostics.Metric.BITMAP_COPY, 20L);

        diagnostics.reset();

        assertEquals(0L, diagnostics.snapshot().count(
                WorkflowDiagnostics.Counter.ACTIONS_DISPATCHED));
        assertEquals(0L, diagnostics.snapshot().metric(
                WorkflowDiagnostics.Metric.BITMAP_COPY).count());
    }
}
