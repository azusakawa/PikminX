package com.pikminx.helper.platform.diagnostics;

import com.pikminx.helper.ActionAdmission;
import com.pikminx.helper.BoundedLatencyStats;
import java.util.EnumMap;
import java.util.Map;

/** Bounded, local-only workflow counters and latency samples for later performance work. */
public final class WorkflowDiagnostics {
    public enum Metric {
        SCREENSHOT_CALLBACK,
        BITMAP_COPY,
        OCR_CALLBACK,
        TOKEN_PARSING,
        PIXEL_DETECTOR,
        MAP_DETECTOR,
        TEMPLATE_DETECTOR,
        ACCESSIBILITY_TRAVERSAL,
        FRAME_ANALYSIS,
        IMAGE_SAMPLING,
        STATE_CLASSIFICATION,
        COORDINATE_CONVERSION,
        WORKFLOW_STATE_MUTATION,
        ACTION_DECISION,
        GESTURE_PREPARATION,
        CAPTURE_TO_OCR,
        CAPTURE_TO_ADMISSION,
        CAPTURE_TO_GESTURE
    }

    public enum Counter {
        SCREENSHOT_REQUESTS,
        OCR_TRANSACTIONS,
        ACTIONS_DISPATCHED,
        ACTIONS_ADMITTED,
        ADMISSION_REJECTIONS,
        NECTAR_FULL_MATCHES,
        NECTAR_CACHE_HITS,
        FRAME_ANALYSIS_SUBMISSIONS,
        FRAME_ANALYSIS_COMPLETIONS,
        FRAME_ANALYSIS_STALE_RESULTS,
        FRAME_ANALYSIS_REJECTED_SUBMISSIONS
    }

    private static final long[] LATENCY_BUCKET_UPPER_BOUNDS = {
        100L, 250L, 500L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L,
        BoundedLatencyStats.MAX_TRACKED_MILLIS
    };

    private final EnumMap<Metric, BoundedLatencyStats> latencyStats =
            new EnumMap<>(Metric.class);
    private final EnumMap<Metric, ThreadAttribution> lastAttribution =
            new EnumMap<>(Metric.class);
    private final EnumMap<Counter, Long> counters = new EnumMap<>(Counter.class);
    private long analysisQueueDepth;
    private long analysisQueueHighWater;

    public WorkflowDiagnostics() {
        reset();
    }

    public synchronized void reset() {
        latencyStats.clear();
        lastAttribution.clear();
        counters.clear();
        for (Metric metric : Metric.values()) {
            latencyStats.put(metric, new BoundedLatencyStats());
            lastAttribution.put(metric, ThreadAttribution.NONE);
        }
        for (Counter counter : Counter.values()) {
            counters.put(counter, 0L);
        }
        analysisQueueDepth = 0L;
        analysisQueueHighWater = 0L;
    }

    public synchronized void recordCount(Counter counter) {
        recordCount(counter, 1L);
    }

    public synchronized void recordCount(Counter counter, long amount) {
        if (counter == null || amount <= 0L) {
            return;
        }
        long current = counters.getOrDefault(counter, 0L);
        long next = current > BoundedLatencyStats.MAX_COUNT - amount
                ? BoundedLatencyStats.MAX_COUNT : current + amount;
        counters.put(counter, next);
    }

    public synchronized void recordAdmission(ActionAdmission.Decision decision) {
        if (decision != null && decision.allowed()) {
            recordCount(Counter.ACTIONS_ADMITTED);
        } else {
            recordCount(Counter.ADMISSION_REJECTIONS);
        }
    }

    public synchronized void recordAnalysisQueueState(long depth, long highWater) {
        analysisQueueDepth = Math.max(0L, depth);
        analysisQueueHighWater = Math.max(
                analysisQueueHighWater,
                Math.max(analysisQueueDepth, Math.max(0L, highWater)));
    }

    public synchronized void recordDuration(Metric metric, long durationMillis) {
        recordDuration(metric, durationMillis, Thread.currentThread());
    }

    public synchronized void recordDuration(
            Metric metric, long durationMillis, String threadName, boolean mainThread) {
        if (metric == null || durationMillis < 0L) {
            return;
        }
        BoundedLatencyStats stats = latencyStats.get(metric);
        if (stats == null) {
            return;
        }
        stats.add(durationMillis);
        lastAttribution.put(
                metric,
                new ThreadAttribution(
                        threadName == null || threadName.isBlank() ? "unknown" : threadName,
                        mainThread));
    }

    private void recordDuration(Metric metric, long durationMillis, Thread thread) {
        recordDuration(
                metric,
                durationMillis,
                thread == null ? "unknown" : thread.getName(),
                thread != null && "main".equals(thread.getName()));
    }

    public synchronized void recordSinceCapture(
            Metric metric,
            ActionAdmission.FrameContext context,
            long endUptimeMillis) {
        if (context == null
                || context.capturedAtUptimeMillis() < 0L
                || endUptimeMillis < context.capturedAtUptimeMillis()) {
            return;
        }
        recordDuration(metric, endUptimeMillis - context.capturedAtUptimeMillis());
    }

    public synchronized Snapshot snapshot() {
        EnumMap<Metric, MetricSnapshot> metricSnapshots = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            BoundedLatencyStats.Snapshot stats = latencyStats.get(metric).snapshot();
            ThreadAttribution attribution = lastAttribution.getOrDefault(
                    metric, ThreadAttribution.NONE);
            metricSnapshots.put(
                    metric,
                    new MetricSnapshot(
                            stats.count(),
                            stats.lastMillis(),
                            stats.p50Millis(),
                            stats.p95Millis(),
                            stats.p99Millis(),
                            stats.maxMillis(),
                            stats.overflowCount(),
                            stats.retainedCount(),
                            upperBound(stats.p95Millis()),
                            upperBound(stats.p99Millis()),
                            attribution.threadName(),
                            attribution.mainThread()));
        }
        return new Snapshot(metricSnapshots, new EnumMap<>(counters));
    }

    public synchronized String summary() {
        Snapshot snapshot = snapshot();
        StringBuilder result = new StringBuilder()
                .append("screenshots=")
                .append(snapshot.count(Counter.SCREENSHOT_REQUESTS))
                .append(" ocr=")
                .append(snapshot.count(Counter.OCR_TRANSACTIONS))
                .append(" dispatched=")
                .append(snapshot.count(Counter.ACTIONS_DISPATCHED))
                .append(" admitted=")
                .append(snapshot.count(Counter.ACTIONS_ADMITTED))
                .append(" rejected=")
                .append(snapshot.count(Counter.ADMISSION_REJECTIONS))
                .append(" nectarFull=")
                .append(snapshot.count(Counter.NECTAR_FULL_MATCHES))
                .append(" nectarCache=")
                .append(snapshot.count(Counter.NECTAR_CACHE_HITS))
                .append(" analysisQueue=")
                .append(analysisQueueDepth)
                .append('/')
                .append(analysisQueueHighWater);
        for (Metric metric : Metric.values()) {
            result.append(' ').append(metric.name()).append('{')
                    .append(snapshot.metric(metric).compact()).append('}');
        }
        return result.toString();
    }

    private static long upperBound(long value) {
        if (value < 0L) {
            return -1L;
        }
        for (long bound : LATENCY_BUCKET_UPPER_BOUNDS) {
            if (value <= bound) {
                return bound;
            }
        }
        return BoundedLatencyStats.MAX_TRACKED_MILLIS;
    }

    private record ThreadAttribution(String threadName, boolean mainThread) {
        private static final ThreadAttribution NONE = new ThreadAttribution("", false);
    }

    public record MetricSnapshot(
            long count,
            long lastMillis,
            long p50Millis,
            long p95Millis,
            long p99Millis,
            long maxMillis,
            long overflowCount,
            long retainedCount,
            long p95UpperBoundMillis,
            long p99UpperBoundMillis,
            String lastThreadName,
            boolean lastMainThread) {
        String compact() {
            return "n=" + count
                    + " p50=" + format(p50Millis)
                    + " p95=" + format(p95Millis)
                    + " p99=" + format(p99Millis)
                    + " max=" + format(maxMillis)
                    + " overflow=" + overflowCount
                    + (retainedCount < count ? " retained=" + retainedCount : "")
                    + " thread=" + (lastThreadName == null || lastThreadName.isBlank()
                            ? "NA" : lastThreadName)
                    + " main=" + lastMainThread;
        }

        private static String format(long value) {
            return value < 0L ? "NA" : value + "ms";
        }
    }

    public record Snapshot(
            Map<Metric, MetricSnapshot> metrics,
            Map<Counter, Long> counters) {
        public Snapshot {
            metrics = Map.copyOf(metrics);
            counters = Map.copyOf(counters);
        }

        public MetricSnapshot metric(Metric metric) {
            MetricSnapshot snapshot = metrics.get(metric);
            return snapshot == null
                    ? new MetricSnapshot(
                            0L, -1L, -1L, -1L, -1L, -1L, 0L, 0L,
                            -1L, -1L, "", false)
                    : snapshot;
        }

        public long count(Counter counter) {
            return counters.getOrDefault(counter, 0L);
        }
    }
}
