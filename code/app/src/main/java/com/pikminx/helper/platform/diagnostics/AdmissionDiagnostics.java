package com.pikminx.helper.platform.diagnostics;

import com.pikminx.helper.ActionAdmission;
import com.pikminx.helper.BoundedLatencyStats;
import java.util.Arrays;

/** Bounded, local-only admission and latency diagnostics. */
public final class AdmissionDiagnostics {
    public AdmissionDiagnostics() {}
    private static final long[] LATENCY_BUCKET_UPPER_BOUNDS = {
        100L, 250L, 500L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L,
        BoundedLatencyStats.MAX_TRACKED_MILLIS
    };

    private final long[] ocrLatencyBuckets = new long[LATENCY_BUCKET_UPPER_BOUNDS.length];
    private final long[] admissionLatencyBuckets =
            new long[LATENCY_BUCKET_UPPER_BOUNDS.length];
    private BoundedLatencyStats ocrLatencyStats = new BoundedLatencyStats();
    private BoundedLatencyStats admissionLatencyStats = new BoundedLatencyStats();
    private long ocrCompletionCount;
    private long captureOnlyAdmissionCount;
    private long admissionCheckCount;
    private long admittedCount;
    private long rejectedCount;
    private long consecutiveStaleFrameRejects;
    private long lastCaptureToOcrMillis = -1L;
    private long lastCaptureToAdmissionMillis = -1L;
    private String lastOcrProfile = "";
    private String lastAdmissionProfile = "";
    private long lastCaptureSequence = -1L;
    private long lastOcrRequestSequence = -1L;
    private ActionAdmission.RejectionReason lastRejectionReason =
            ActionAdmission.RejectionReason.NONE;
    private ActionAdmission.RejectionReason lastLoggedReason =
            ActionAdmission.RejectionReason.NONE;

    public record Event(Snapshot snapshot, boolean shouldLog) {}

    public synchronized Event recordOcr(
            String profile,
            ActionAdmission.FrameContext context,
            long ocrCompletedAtUptimeMillis,
            long admissionAtUptimeMillis,
            ActionAdmission.Decision decision) {
        ocrCompletionCount = increment(ocrCompletionCount);
        lastOcrProfile = profile == null ? "" : profile;
        lastCaptureToOcrMillis = latency(context, ocrCompletedAtUptimeMillis);
        addLatency(ocrLatencyBuckets, ocrLatencyStats, lastCaptureToOcrMillis);
        return recordAdmission(
                profile, context, admissionAtUptimeMillis, decision, true);
    }

    public synchronized Event recordCaptureOnly(
            String profile,
            ActionAdmission.FrameContext context,
            long admissionAtUptimeMillis,
            ActionAdmission.Decision decision) {
        captureOnlyAdmissionCount = increment(captureOnlyAdmissionCount);
        return recordAdmission(
                profile, context, admissionAtUptimeMillis, decision, false);
    }

    public synchronized Event recordActionCheck(
            String profile,
            ActionAdmission.FrameContext context,
            long admissionAtUptimeMillis,
            ActionAdmission.Decision decision) {
        return recordAdmission(
                profile, context, admissionAtUptimeMillis, decision, false);
    }

    public synchronized void reset() {
        Arrays.fill(ocrLatencyBuckets, 0L);
        Arrays.fill(admissionLatencyBuckets, 0L);
        ocrLatencyStats = new BoundedLatencyStats();
        admissionLatencyStats = new BoundedLatencyStats();
        ocrCompletionCount = 0L;
        captureOnlyAdmissionCount = 0L;
        admissionCheckCount = 0L;
        admittedCount = 0L;
        rejectedCount = 0L;
        consecutiveStaleFrameRejects = 0L;
        lastCaptureToOcrMillis = -1L;
        lastCaptureToAdmissionMillis = -1L;
        lastOcrProfile = "";
        lastAdmissionProfile = "";
        lastCaptureSequence = -1L;
        lastOcrRequestSequence = -1L;
        lastRejectionReason = ActionAdmission.RejectionReason.NONE;
        lastLoggedReason = ActionAdmission.RejectionReason.NONE;
    }

    public synchronized Snapshot snapshot() {
        return buildSnapshot();
    }

    public synchronized String summary() {
        Snapshot snapshot = buildSnapshot();
        return "ocr=" + snapshot.ocrCompletionCount()
                + " captureOnly=" + snapshot.captureOnlyAdmissionCount()
                + " checks=" + snapshot.admissionCheckCount()
                + " admitted=" + snapshot.admittedCount()
                + " rejected=" + snapshot.rejectedCount()
                + " stale=" + snapshot.consecutiveStaleFrameRejects()
                + " ocrP95<=" + snapshot.ocrP95UpperBoundMillis()
                + "ms ocrP99<=" + snapshot.ocrP99UpperBoundMillis()
                + "ms admissionP95<=" + snapshot.admissionP95UpperBoundMillis()
                + "ms admissionP99<=" + snapshot.admissionP99UpperBoundMillis()
                + "ms ocrLatency{" + snapshot.ocrLatency().compact() + "}"
                + " admissionLatency{" + snapshot.admissionLatency().compact() + "}"
                + " lastReason=" + snapshot.lastRejectionReason()
                + " profile=" + snapshot.lastOcrProfile()
                + " context=" + snapshot.lastAdmissionProfile()
                + " capture=" + snapshot.lastCaptureSequence()
                + " transaction=" + snapshot.lastOcrRequestSequence();
    }

    private Event recordAdmission(
            String profile,
            ActionAdmission.FrameContext context,
            long admissionAtUptimeMillis,
            ActionAdmission.Decision decision,
            boolean frameAdmission) {
        ActionAdmission.Decision safeDecision = decision == null
                ? new ActionAdmission.Decision(
                        false, ActionAdmission.RejectionReason.INVALID_CONTEXT)
                : decision;
        admissionCheckCount = increment(admissionCheckCount);
        if (safeDecision.allowed()) {
            admittedCount = increment(admittedCount);
            if (frameAdmission) {
                consecutiveStaleFrameRejects = 0L;
            }
        } else {
            rejectedCount = increment(rejectedCount);
            lastRejectionReason = safeDecision.reason();
            if (frameAdmission) {
                if (isStaleFrameReason(safeDecision.reason())) {
                    consecutiveStaleFrameRejects = increment(consecutiveStaleFrameRejects);
                } else {
                    consecutiveStaleFrameRejects = 0L;
                }
            }
        }
        lastAdmissionProfile = profile == null ? "" : profile;
        if (context != null) {
            lastCaptureSequence = context.captureSequence();
            lastOcrRequestSequence = context.ocrRequestSequence();
        } else {
            lastCaptureSequence = -1L;
            lastOcrRequestSequence = -1L;
        }
        lastCaptureToAdmissionMillis = latency(context, admissionAtUptimeMillis);
        addLatency(
                admissionLatencyBuckets,
                admissionLatencyStats,
                lastCaptureToAdmissionMillis);
        boolean shouldLog = !safeDecision.allowed()
                && (safeDecision.reason() != lastLoggedReason
                || (isStaleFrameReason(safeDecision.reason())
                && consecutiveStaleFrameRejects > 0L
                && (consecutiveStaleFrameRejects == 1L
                || consecutiveStaleFrameRejects % 5L == 0L)));
        if (shouldLog) {
            lastLoggedReason = safeDecision.reason();
        }
        return new Event(buildSnapshot(), shouldLog);
    }

    private Snapshot buildSnapshot() {
        return new Snapshot(
                ocrCompletionCount,
                captureOnlyAdmissionCount,
                admissionCheckCount,
                admittedCount,
                rejectedCount,
                consecutiveStaleFrameRejects,
                lastCaptureToOcrMillis,
                lastCaptureToAdmissionMillis,
                lastOcrProfile,
                lastAdmissionProfile,
                lastCaptureSequence,
                lastOcrRequestSequence,
                lastRejectionReason,
                Arrays.copyOf(ocrLatencyBuckets, ocrLatencyBuckets.length),
                Arrays.copyOf(admissionLatencyBuckets, admissionLatencyBuckets.length),
                ocrLatencyStats.snapshot(),
                admissionLatencyStats.snapshot());
    }

    private static long latency(ActionAdmission.FrameContext context, long endUptimeMillis) {
        if (context == null
                || context.capturedAtUptimeMillis() < 0L
                || endUptimeMillis < context.capturedAtUptimeMillis()) {
            return -1L;
        }
        return endUptimeMillis - context.capturedAtUptimeMillis();
    }

    private static void addLatency(
            long[] buckets, BoundedLatencyStats stats, long latencyMillis) {
        if (latencyMillis < 0L) {
            return;
        }
        stats.add(latencyMillis);
        for (int index = 0; index < LATENCY_BUCKET_UPPER_BOUNDS.length; index++) {
            if (latencyMillis <= LATENCY_BUCKET_UPPER_BOUNDS[index]) {
                buckets[index] = increment(buckets[index]);
                return;
            }
        }
        buckets[buckets.length - 1] = increment(buckets[buckets.length - 1]);
    }

    private static long increment(long value) {
        return value >= BoundedLatencyStats.MAX_COUNT ? BoundedLatencyStats.MAX_COUNT : value + 1L;
    }

    private static boolean isStaleFrameReason(ActionAdmission.RejectionReason reason) {
        return reason != null && switch (reason) {
            case GENERATION,
                    CAPTURE_SEQUENCE,
                    OCR_SEQUENCE,
                    ADMISSION_EPOCH,
                    CAPTURE_AGE,
                    PACKAGE,
                    WINDOW_UNAVAILABLE,
                    WINDOW_BOUNDS,
                    WINDOW_ID,
                    FRAME_NOT_ACTION_SAFE -> true;
            default -> false;
        };
    }

    public record Snapshot(
            long ocrCompletionCount,
            long captureOnlyAdmissionCount,
            long admissionCheckCount,
            long admittedCount,
            long rejectedCount,
            long consecutiveStaleFrameRejects,
            long lastCaptureToOcrMillis,
            long lastCaptureToAdmissionMillis,
            String lastOcrProfile,
            String lastAdmissionProfile,
            long lastCaptureSequence,
            long lastOcrRequestSequence,
            ActionAdmission.RejectionReason lastRejectionReason,
            long[] ocrLatencyBuckets,
            long[] admissionLatencyBuckets,
            BoundedLatencyStats.Snapshot ocrLatency,
            BoundedLatencyStats.Snapshot admissionLatency) {
        public Snapshot {
            ocrLatencyBuckets = Arrays.copyOf(ocrLatencyBuckets, ocrLatencyBuckets.length);
            admissionLatencyBuckets = Arrays.copyOf(
                    admissionLatencyBuckets, admissionLatencyBuckets.length);
        }

        long ocrP50Millis() {
            return ocrLatency.p50Millis();
        }

        long ocrP95Millis() {
            return ocrLatency.p95Millis();
        }

        long ocrP99Millis() {
            return ocrLatency.p99Millis();
        }

        long ocrMaxMillis() {
            return ocrLatency.maxMillis();
        }

        long admissionP50Millis() {
            return admissionLatency.p50Millis();
        }

        long admissionP95Millis() {
            return admissionLatency.p95Millis();
        }

        long admissionP99Millis() {
            return admissionLatency.p99Millis();
        }

        long admissionMaxMillis() {
            return admissionLatency.maxMillis();
        }

        long ocrP95UpperBoundMillis() {
            return percentileUpperBound(ocrLatencyBuckets, 95L);
        }

        long ocrP99UpperBoundMillis() {
            return percentileUpperBound(ocrLatencyBuckets, 99L);
        }

        long admissionP95UpperBoundMillis() {
            return percentileUpperBound(admissionLatencyBuckets, 95L);
        }

        long admissionP99UpperBoundMillis() {
            return percentileUpperBound(admissionLatencyBuckets, 99L);
        }

        private static long percentileUpperBound(long[] buckets, long percentile) {
            long sampleCount = 0L;
            for (long bucket : buckets) {
                sampleCount = sampleCount > BoundedLatencyStats.MAX_COUNT - bucket
                        ? BoundedLatencyStats.MAX_COUNT : sampleCount + bucket;
            }
            if (sampleCount <= 0L) {
                return -1L;
            }
            long target = Math.max(1L, (sampleCount * percentile + 99L) / 100L);
            long seen = 0L;
            for (int index = 0; index < buckets.length; index++) {
                seen = seen > BoundedLatencyStats.MAX_COUNT - buckets[index]
                        ? BoundedLatencyStats.MAX_COUNT : seen + buckets[index];
                if (seen >= target) {
                    return LATENCY_BUCKET_UPPER_BOUNDS[index];
                }
            }
            return BoundedLatencyStats.MAX_TRACKED_MILLIS;
        }
    }
}
