package com.pikminx.helper;

import com.pikminx.helper.platform.diagnostics.AdmissionDiagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdmissionDiagnosticsTest {
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);

    @Test
    public void recordsOcrAdmissionLatencyReasonAndSequences() {
        AdmissionDiagnostics diagnostics = new AdmissionDiagnostics();
        ActionAdmission.FrameContext frame = new ActionAdmission.FrameContext(
                7L, 42L, 17L, 1_000L, "com.nianticlabs.pikmin", WINDOW, 12, 0L);
        ActionAdmission.Decision decision = new ActionAdmission.Decision(
                false, ActionAdmission.RejectionReason.CAPTURE_AGE);

        AdmissionDiagnostics.Event event = diagnostics.recordOcr(
                "FULL_CHINESE", frame, 1_300L, 1_450L, decision);
        AdmissionDiagnostics.Snapshot snapshot = event.snapshot();

        assertTrue(event.shouldLog());
        assertEquals(1L, snapshot.ocrCompletionCount());
        assertEquals(1L, snapshot.admissionCheckCount());
        assertEquals(1L, snapshot.rejectedCount());
        assertEquals(1L, snapshot.consecutiveStaleFrameRejects());
        assertEquals(300L, snapshot.lastCaptureToOcrMillis());
        assertEquals(450L, snapshot.lastCaptureToAdmissionMillis());
        assertEquals("FULL_CHINESE", snapshot.lastOcrProfile());
        assertEquals(42L, snapshot.lastCaptureSequence());
        assertEquals(17L, snapshot.lastOcrRequestSequence());
        assertEquals(ActionAdmission.RejectionReason.CAPTURE_AGE,
                snapshot.lastRejectionReason());
    }

    @Test
    public void allowedFrameResetsConsecutiveStaleRejectsAndKeepsSamples() {
        AdmissionDiagnostics diagnostics = new AdmissionDiagnostics();
        ActionAdmission.FrameContext first = new ActionAdmission.FrameContext(
                7L, 42L, 17L, 1_000L, "com.nianticlabs.pikmin", WINDOW, 12, 0L);
        ActionAdmission.FrameContext second = new ActionAdmission.FrameContext(
                7L, 43L, 18L, 2_000L, "com.nianticlabs.pikmin", WINDOW, 12, 0L);

        diagnostics.recordOcr(
                "FULL_MULTILINGUAL", first, 1_300L, 1_350L,
                new ActionAdmission.Decision(
                        false, ActionAdmission.RejectionReason.CAPTURE_SEQUENCE));
        AdmissionDiagnostics.Event event = diagnostics.recordOcr(
                "FULL_CHINESE", second, 2_100L, 2_150L,
                new ActionAdmission.Decision(
                        true, ActionAdmission.RejectionReason.NONE));

        AdmissionDiagnostics.Snapshot snapshot = event.snapshot();
        assertEquals(2L, snapshot.ocrCompletionCount());
        assertEquals(2L, snapshot.admissionCheckCount());
        assertEquals(1L, snapshot.admittedCount());
        assertEquals(1L, snapshot.rejectedCount());
        assertEquals(0L, snapshot.consecutiveStaleFrameRejects());
        assertEquals(43L, snapshot.lastCaptureSequence());
        assertEquals("FULL_CHINESE", snapshot.lastOcrProfile());
    }

    @Test
    public void captureOnlyAdmissionIsRecordedWithoutOcrCompletion() {
        AdmissionDiagnostics diagnostics = new AdmissionDiagnostics();
        ActionAdmission.FrameContext frame = new ActionAdmission.FrameContext(
                7L, 44L, 0L, 3_000L, "com.nianticlabs.pikmin", WINDOW, 12, 1L);

        AdmissionDiagnostics.Event event = diagnostics.recordCaptureOnly(
                "CAPTURE_ONLY", frame, 3_120L,
                new ActionAdmission.Decision(true, ActionAdmission.RejectionReason.NONE));

        assertEquals(0L, event.snapshot().ocrCompletionCount());
        assertEquals(1L, event.snapshot().captureOnlyAdmissionCount());
        assertEquals(1L, event.snapshot().admittedCount());
        assertEquals(120L, event.snapshot().lastCaptureToAdmissionMillis());
    }
}
