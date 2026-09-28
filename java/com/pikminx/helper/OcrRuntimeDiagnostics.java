package com.pikminx.helper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Local-only OCR timing and bounded work counters.
 *
 * <p>This class intentionally stores timestamps, identifiers and small immutable summaries only.
 * It never retains a Bitmap, Android window object or OCR result.</p>
 */
final class OcrRuntimeDiagnostics {
    enum Metric {
        OCR_QUEUE_WAIT,
        RECOGNIZER_LATENCY,
        TOTAL_ML_KIT_LATENCY,
        CALLBACK_EXECUTOR_DELAY,
        TOKEN_PARSING,
        MAIN_EXECUTOR_DELAY,
        HANDLE_TOKENS,
        CAPTURE_TO_OCR,
        CAPTURE_TO_ADMISSION,
        CAPTURE_TO_GESTURE
    }

    enum Counter {
        SCREENSHOTS,
        OCR_TRANSACTIONS,
        RECOGNIZER_CALLS,
        GESTURES_DISPATCHED,
        STALE_CALLBACKS,
        OCR_TIMEOUTS,
        REJECTED_QUEUE_SUBMISSIONS
    }

    private enum Event {
        CAPTURE,
        TRANSACTION_CREATED,
        QUEUE_ENTER,
        WORKER_START,
        ALL_RECOGNIZERS_COMPLETE,
        OCR_COMPLETION,
        MAIN_EXECUTOR_ENQUEUE,
        MAIN_EXECUTOR_START,
        HANDLE_TOKENS_START,
        HANDLE_TOKENS_END,
        ADMISSION,
        GESTURE_DISPATCH,
        TIMEOUT,
        FINAL_COMPLETION
    }

    static final long SLOW_TRANSACTION_DIAGNOSTIC_THRESHOLD_MILLIS = 3_000L;
    private static final int MAX_ACTIVE_TIMELINES = 8;
    private static final int MAX_COMPLETED_TIMELINES = 32;
    private static final int MAX_GESTURE_STAMPS = 8;

    private final EnumMap<Metric, StageStats> metrics = new EnumMap<>(Metric.class);
    private final EnumMap<Counter, Long> counters = new EnumMap<>(Counter.class);
    private final LinkedHashMap<OcrScan.TransactionId, MutableTimeline> activeTimelines =
            new LinkedHashMap<>();
    private final ArrayDeque<TimelineSnapshot> completedTimelines = new ArrayDeque<>();
    private final LinkedHashMap<String, StageStats> recognizerMetrics = new LinkedHashMap<>();
    private long periodStartedAtUptimeMillis;
    private long screenshotQueueDepth;
    private long screenshotQueueHighWater;
    private long ocrQueueDepth;
    private long ocrQueueHighWater;
    private long activeOcrTransactions;
    private long activeRecognizers;
    private final LongSupplier uptimeMillis;

    OcrRuntimeDiagnostics() {
        this(() -> android.os.SystemClock.uptimeMillis());
    }

    OcrRuntimeDiagnostics(LongSupplier uptimeMillis) {
        this.uptimeMillis = uptimeMillis == null ? () -> 0L : uptimeMillis;
        reset(0L);
    }

    synchronized void reset() {
        reset(0L);
    }

    synchronized void reset(long nowUptimeMillis) {
        periodStartedAtUptimeMillis = Math.max(0L, nowUptimeMillis);
        metrics.clear();
        counters.clear();
        activeTimelines.clear();
        completedTimelines.clear();
        recognizerMetrics.clear();
        for (Metric metric : Metric.values()) {
            metrics.put(metric, new StageStats());
        }
        for (Counter counter : Counter.values()) {
            counters.put(counter, 0L);
        }
        screenshotQueueDepth = 0L;
        screenshotQueueHighWater = 0L;
        ocrQueueDepth = 0L;
        ocrQueueHighWater = 0L;
        activeOcrTransactions = 0L;
        activeRecognizers = 0L;
    }

    synchronized void recordScreenshot(
            long nowUptimeMillis, long queueDepth, long queueHighWater) {
        increment(Counter.SCREENSHOTS);
        recordScreenshotQueueState(queueDepth, queueHighWater);
    }

    synchronized void recordScreenshotQueueState(long queueDepth, long queueHighWater) {
        screenshotQueueDepth = Math.max(0L, queueDepth);
        screenshotQueueHighWater = Math.max(
                screenshotQueueHighWater, Math.max(screenshotQueueDepth, queueHighWater));
    }

    synchronized void begin(
            OcrScan.TransactionId id,
            String profile,
            long captureAtUptimeMillis,
            long transactionCreatedAtUptimeMillis) {
        if (id == null) {
            return;
        }
        MutableTimeline previous = activeTimelines.remove(id);
        if (previous != null) {
            closeOpenRecognizers(previous);
            activeOcrTransactions = decrement(activeOcrTransactions);
        }
        while (activeTimelines.size() >= MAX_ACTIVE_TIMELINES) {
            Map.Entry<OcrScan.TransactionId, MutableTimeline> eldest =
                    activeTimelines.entrySet().iterator().next();
            closeOpenRecognizers(eldest.getValue());
            activeTimelines.remove(eldest.getKey());
            activeOcrTransactions = decrement(activeOcrTransactions);
        }
        MutableTimeline timeline = new MutableTimeline(
                id,
                profile == null ? "" : profile,
                captureAtUptimeMillis,
                transactionCreatedAtUptimeMillis);
        activeTimelines.put(id, timeline);
        activeOcrTransactions = increment(activeOcrTransactions);
        increment(Counter.OCR_TRANSACTIONS);
        Stamp capture = stamp(captureAtUptimeMillis, "capture");
        if (capture != null) {
            timeline.events.put(Event.CAPTURE, capture);
        }
        Stamp created = stamp(transactionCreatedAtUptimeMillis);
        if (created != null) {
            timeline.events.put(Event.TRANSACTION_CREATED, created);
        }
    }

    synchronized void markQueueEnter(OcrScan.TransactionId id) {
        markEvent(id, Event.QUEUE_ENTER);
    }

    synchronized void markWorkerStart(OcrScan.TransactionId id) {
        if (!markEvent(id, Event.WORKER_START)) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline != null) {
            addMetric(
                    Metric.OCR_QUEUE_WAIT,
                    timeline.events.get(Event.QUEUE_ENTER),
                    timeline.events.get(Event.WORKER_START),
                    metrics.get(Metric.OCR_QUEUE_WAIT));
        }
    }

    synchronized void markRecognizerStart(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.computeIfAbsent(
                safeRecognizerType(recognizerType), ignored -> new RecognizerSpan());
        if (span.start == null) {
            span.start = stamp(now());
            span.open = true;
            activeRecognizers = increment(activeRecognizers);
            increment(Counter.RECOGNIZER_CALLS);
        }
    }

    synchronized void markRecognizerEnd(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.get(safeRecognizerType(recognizerType));
        if (span == null || span.start == null || !span.open) {
            return;
        }
        span.end = stamp(now());
        span.open = false;
        activeRecognizers = decrement(activeRecognizers);
        addMetric(
                Metric.RECOGNIZER_LATENCY,
                span.start,
                span.end,
                recognizerMetrics.computeIfAbsent(safeRecognizerType(recognizerType),
                        ignored -> new StageStats()));
    }

    synchronized void markAllRecognizersComplete(OcrScan.TransactionId id) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished
                || timeline.events.containsKey(Event.ALL_RECOGNIZERS_COMPLETE)) {
            return;
        }
        Stamp end = stamp(now());
        timeline.events.put(Event.ALL_RECOGNIZERS_COMPLETE, end);
        Stamp start = null;
        for (RecognizerSpan span : timeline.recognizers.values()) {
            if (span.start != null
                    && (start == null
                    || span.start.uptimeMillis() < start.uptimeMillis())) {
                start = span.start;
            }
        }
        addMetric(Metric.TOTAL_ML_KIT_LATENCY, start, end, metrics.get(Metric.TOTAL_ML_KIT_LATENCY));
    }

    synchronized void markCallbackEnqueue(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.computeIfAbsent(
                safeRecognizerType(recognizerType), ignored -> new RecognizerSpan());
        if (span.callbackEnqueue == null) {
            span.callbackEnqueue = stamp(now());
        }
    }

    synchronized void markCallbackStart(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.computeIfAbsent(
                safeRecognizerType(recognizerType), ignored -> new RecognizerSpan());
        if (span.callbackStart == null) {
            span.callbackStart = stamp(now());
            addMetric(
                    Metric.CALLBACK_EXECUTOR_DELAY,
                    span.callbackEnqueue,
                    span.callbackStart,
                    metrics.get(Metric.CALLBACK_EXECUTOR_DELAY));
        }
    }

    synchronized void markTokenParsingStart(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.computeIfAbsent(
                safeRecognizerType(recognizerType), ignored -> new RecognizerSpan());
        if (span.tokenStart == null) {
            span.tokenStart = stamp(now());
        }
    }

    synchronized void markTokenParsingEnd(OcrScan.TransactionId id, String recognizerType) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished) {
            return;
        }
        RecognizerSpan span = timeline.recognizers.get(safeRecognizerType(recognizerType));
        if (span == null || span.tokenStart == null || span.tokenEnd != null) {
            return;
        }
        span.tokenEnd = stamp(now());
        addMetric(
                Metric.TOKEN_PARSING,
                span.tokenStart,
                span.tokenEnd,
                metrics.get(Metric.TOKEN_PARSING));
    }

    synchronized void markOcrCompletion(OcrScan.TransactionId id) {
        if (!markEvent(id, Event.OCR_COMPLETION)) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline != null) {
            addMetric(
                    Metric.CAPTURE_TO_OCR,
                    timeline.events.get(Event.CAPTURE),
                    timeline.events.get(Event.OCR_COMPLETION),
                    metrics.get(Metric.CAPTURE_TO_OCR));
        }
    }

    synchronized void markMainExecutorEnqueue(OcrScan.TransactionId id) {
        markEvent(id, Event.MAIN_EXECUTOR_ENQUEUE);
    }

    synchronized void markMainExecutorStart(OcrScan.TransactionId id) {
        if (!markEvent(id, Event.MAIN_EXECUTOR_START)) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline != null) {
            addMetric(
                    Metric.MAIN_EXECUTOR_DELAY,
                    timeline.events.get(Event.MAIN_EXECUTOR_ENQUEUE),
                    timeline.events.get(Event.MAIN_EXECUTOR_START),
                    metrics.get(Metric.MAIN_EXECUTOR_DELAY));
        }
    }

    synchronized void markHandleTokensStart(OcrScan.TransactionId id) {
        markEvent(id, Event.HANDLE_TOKENS_START);
    }

    synchronized void markHandleTokensEnd(OcrScan.TransactionId id) {
        if (!markEvent(id, Event.HANDLE_TOKENS_END)) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline != null) {
            addMetric(
                    Metric.HANDLE_TOKENS,
                    timeline.events.get(Event.HANDLE_TOKENS_START),
                    timeline.events.get(Event.HANDLE_TOKENS_END),
                    metrics.get(Metric.HANDLE_TOKENS));
        }
    }

    synchronized void markAdmission(OcrScan.TransactionId id) {
        if (!markEvent(id, Event.ADMISSION)) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline != null) {
            addMetric(
                    Metric.CAPTURE_TO_ADMISSION,
                    timeline.events.get(Event.CAPTURE),
                    timeline.events.get(Event.ADMISSION),
                    metrics.get(Metric.CAPTURE_TO_ADMISSION));
        }
    }

    synchronized void markGesture(
            long runGeneration,
            long captureSequence,
            long ocrRequestSequence,
            boolean sent) {
        if (sent) {
            increment(Counter.GESTURES_DISPATCHED);
        }
        if (runGeneration < 1L || captureSequence < 1L || ocrRequestSequence < 1L) {
            return;
        }
        MutableTimeline timeline = activeTimelines.get(
                new OcrScan.TransactionId(runGeneration, captureSequence, ocrRequestSequence));
        if (timeline == null || timeline.finished) {
            return;
        }
        Stamp dispatch = stamp(now());
        if (timeline.gestureDispatches.size() < MAX_GESTURE_STAMPS) {
            timeline.gestureDispatches.add(dispatch);
        }
        addMetric(
                Metric.CAPTURE_TO_GESTURE,
                timeline.events.get(Event.CAPTURE),
                dispatch,
                metrics.get(Metric.CAPTURE_TO_GESTURE));
    }

    synchronized void markTimeout(OcrScan.TransactionId id) {
        if (markEvent(id, Event.TIMEOUT)) {
            increment(Counter.OCR_TIMEOUTS);
        }
    }

    synchronized void markStaleCallback() {
        increment(Counter.STALE_CALLBACKS);
    }

    synchronized void markRejectedQueueSubmission() {
        increment(Counter.REJECTED_QUEUE_SUBMISSIONS);
    }

    synchronized void recordOcrQueueDepth(long depth) {
        ocrQueueDepth = Math.max(0L, depth);
        ocrQueueHighWater = Math.max(ocrQueueHighWater, ocrQueueDepth);
    }

    private synchronized void incrementOcrQueueDepth() {
        ocrQueueDepth = increment(ocrQueueDepth);
        ocrQueueHighWater = Math.max(ocrQueueHighWater, ocrQueueDepth);
    }

    private synchronized void decrementOcrQueueDepth() {
        ocrQueueDepth = Math.max(0L, ocrQueueDepth - 1L);
    }

    synchronized Executor executorForRecognizer(
            OcrScan.TransactionId id, String recognizerType, Executor delegate) {
        return command -> {
            if (command == null) {
                throw new NullPointerException("command");
            }
            markCallbackEnqueue(id, recognizerType);
            AtomicBoolean started = new AtomicBoolean();
            try {
                incrementOcrQueueDepth();
                delegate.execute(() -> {
                    started.set(true);
                    decrementOcrQueueDepth();
                    markCallbackStart(id, recognizerType);
                    command.run();
                });
            } catch (RuntimeException error) {
                if (!started.get()) {
                    decrementOcrQueueDepth();
                    markRejectedQueueSubmission();
                }
                throw error;
            }
        };
    }

    synchronized Completion finish(
            OcrScan.TransactionId id, String outcome, long finalAtUptimeMillis) {
        MutableTimeline timeline = activeTimelines.remove(id);
        if (timeline == null || timeline.finished) {
            return Completion.NONE;
        }
        timeline.finished = true;
        closeOpenRecognizers(timeline);
        activeOcrTransactions = decrement(activeOcrTransactions);
        Stamp finalCompletion = stamp(finalAtUptimeMillis);
        timeline.events.put(Event.FINAL_COMPLETION, finalCompletion);
        TimelineSnapshot snapshot = timeline.snapshot(outcome == null ? "UNKNOWN" : outcome);
        completedTimelines.addLast(snapshot);
        while (completedTimelines.size() > MAX_COMPLETED_TIMELINES) {
            completedTimelines.removeFirst();
        }
        Stamp capture = snapshot.events().get(Event.CAPTURE);
        long total = duration(capture, finalCompletion);
        boolean timedOut = snapshot.events().containsKey(Event.TIMEOUT)
                || "TIMEOUT".equals(outcome);
        boolean slow = total >= SLOW_TRANSACTION_DIAGNOSTIC_THRESHOLD_MILLIS;
        return new Completion(snapshot, timedOut || slow, total);
    }

    synchronized Snapshot snapshot(long nowUptimeMillis) {
        EnumMap<Metric, MetricSnapshot> metricSnapshots = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            metricSnapshots.put(metric, metrics.get(metric).snapshot());
        }
        LinkedHashMap<String, MetricSnapshot> recognizerSnapshots = new LinkedHashMap<>();
        for (Map.Entry<String, StageStats> entry : recognizerMetrics.entrySet()) {
            recognizerSnapshots.put(entry.getKey(), entry.getValue().snapshot());
        }
        long elapsed = Math.max(0L, nowUptimeMillis - periodStartedAtUptimeMillis);
        return new Snapshot(
                metricSnapshots,
                recognizerSnapshots,
                new EnumMap<>(counters),
                screenshotQueueDepth,
                screenshotQueueHighWater,
                ocrQueueDepth,
                ocrQueueHighWater,
                activeOcrTransactions,
                activeRecognizers,
                elapsed,
                completedTimelines.size());
    }

    String summary() {
        return summary(android.os.SystemClock.uptimeMillis());
    }

    synchronized String summary(long nowUptimeMillis) {
        Snapshot snapshot = snapshot(nowUptimeMillis);
        StringBuilder result = new StringBuilder()
                .append("queueDepth=").append(snapshot.ocrQueueDepth())
                .append(" queueHighWater=").append(snapshot.ocrQueueHighWater())
                .append(" screenshotQueueDepth=").append(snapshot.screenshotQueueDepth())
                .append(" screenshotQueueHighWater=").append(snapshot.screenshotQueueHighWater())
                .append(" activeOcr=").append(snapshot.activeOcrTransactions())
                .append(" activeRecognizers=").append(snapshot.activeRecognizers())
                .append(" completedTimelines=").append(snapshot.completedTimelineCount())
                .append(" rates={screenshots=").append(snapshot.rate(Counter.SCREENSHOTS))
                .append(" ocr=").append(snapshot.rate(Counter.OCR_TRANSACTIONS))
                .append(" recognizers=").append(snapshot.rate(Counter.RECOGNIZER_CALLS))
                .append(" gestures=").append(snapshot.rate(Counter.GESTURES_DISPATCHED))
                .append(" screenshotsPerAction=").append(snapshot.screenshotsPerAction())
                .append(" ocrPerAction=").append(snapshot.ocrPerAction()).append('}')
                .append(" counters={");
        for (Counter counter : Counter.values()) {
            if (counter != Counter.SCREENSHOTS) {
                result.append(counter.name()).append('=').append(snapshot.count(counter)).append(' ');
            }
        }
        result.append('}');
        for (Metric metric : Metric.values()) {
            result.append(' ').append(metric.name()).append('{')
                    .append(snapshot.metric(metric).compact()).append('}');
        }
        result.append(" recognizers={");
        for (Map.Entry<String, MetricSnapshot> entry : snapshot.recognizers().entrySet()) {
            result.append(entry.getKey()).append('{').append(entry.getValue().compact()).append('}');
        }
        return result.append('}').toString();
    }

    private boolean markEvent(OcrScan.TransactionId id, Event event) {
        MutableTimeline timeline = activeTimelines.get(id);
        if (timeline == null || timeline.finished || timeline.events.containsKey(event)) {
            return false;
        }
        timeline.events.put(event, stamp(now()));
        return true;
    }

    private void increment(Counter counter) {
        long current = counters.getOrDefault(counter, 0L);
        counters.put(counter, current >= BoundedLatencyStats.MAX_COUNT
                ? BoundedLatencyStats.MAX_COUNT : current + 1L);
    }

    private static long increment(long value) {
        return value >= BoundedLatencyStats.MAX_COUNT
                ? BoundedLatencyStats.MAX_COUNT : value + 1L;
    }

    private static long decrement(long value) {
        return Math.max(0L, value - 1L);
    }

    private void closeOpenRecognizers(MutableTimeline timeline) {
        for (RecognizerSpan span : timeline.recognizers.values()) {
            if (span.open) {
                span.open = false;
                activeRecognizers = decrement(activeRecognizers);
            }
        }
    }

    private static void addMetric(
            Metric metric, Stamp start, Stamp end, StageStats destination) {
        if (metric == null || destination == null) {
            return;
        }
        if (start == null || end == null || end.uptimeMillis() < start.uptimeMillis()) {
            return;
        }
        destination.add(
                end.uptimeMillis() - start.uptimeMillis(),
                end.threadName(),
                end.mainThread());
    }

    private static String safeRecognizerType(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return switch (value) {
            case "LATIN", "CHINESE", "DEVANAGARI", "JAPANESE", "KOREAN" -> value;
            default -> "other";
        };
    }

    private static Stamp stamp(long uptimeMillis) {
        return stamp(uptimeMillis, Thread.currentThread().getName());
    }

    private static Stamp stamp(long uptimeMillis, String threadName) {
        if (uptimeMillis < 0L) {
            return null;
        }
        String safeThreadName = threadName == null || threadName.isBlank()
                ? "unknown" : threadName;
        return new Stamp(uptimeMillis, safeThreadName, "main".equals(safeThreadName));
    }

    private static long duration(Stamp start, Stamp end) {
        if (start == null || end == null || end.uptimeMillis() < start.uptimeMillis()) {
            return -1L;
        }
        long duration = end.uptimeMillis() - start.uptimeMillis();
        return Math.min(duration, BoundedLatencyStats.MAX_TRACKED_MILLIS);
    }

    record Stamp(long uptimeMillis, String threadName, boolean mainThread) {}

    record Completion(TimelineSnapshot timeline, boolean shouldLogTimeline, long totalLatencyMillis) {
        private static final Completion NONE = new Completion(null, false, -1L);

        String compactTimeline() {
            return timeline == null ? "" : timeline.compact();
        }
    }

    record TimelineSnapshot(
            OcrScan.TransactionId id,
            String profile,
            Map<Event, Stamp> events,
            Map<String, RecognizerSnapshot> recognizers,
            List<Stamp> gestureDispatches,
            String outcome) {
        TimelineSnapshot {
            events = Collections.unmodifiableMap(new EnumMap<>(events));
            recognizers = Collections.unmodifiableMap(new LinkedHashMap<>(recognizers));
            gestureDispatches = List.copyOf(gestureDispatches);
        }

        String compact() {
            StringBuilder result = new StringBuilder()
                    .append("transaction=").append(id.ocrRequestSequence())
                    .append(" capture=").append(id.captureSequence())
                    .append(" generation=").append(id.runGeneration())
                    .append(" profile=").append(profile)
                    .append(" outcome=").append(outcome)
                    .append(" events=");
            for (Map.Entry<Event, Stamp> event : events.entrySet()) {
                result.append(event.getKey().name()).append('@')
                        .append(event.getValue().uptimeMillis()).append('(')
                        .append(event.getValue().threadName()).append('/')
                        .append(event.getValue().mainThread() ? "main" : "worker").append(") ");
            }
            result.append("recognizers=");
            for (Map.Entry<String, RecognizerSnapshot> recognizer : recognizers.entrySet()) {
                result.append(recognizer.getKey()).append('{')
                        .append(recognizer.getValue().compact()).append('}');
            }
            result.append(" gestures=").append(gestureDispatches.size());
            return result.toString().trim();
        }
    }

    record RecognizerSnapshot(
            Stamp start,
            Stamp end,
            Stamp callbackEnqueue,
            Stamp callbackStart,
            Stamp tokenStart,
            Stamp tokenEnd) {
        String compact() {
            return "start=" + time(start)
                    + " end=" + time(end)
                    + " callbackEnqueue=" + time(callbackEnqueue)
                    + " callbackStart=" + time(callbackStart)
                    + " tokenStart=" + time(tokenStart)
                    + " tokenEnd=" + time(tokenEnd);
        }

        private static String time(Stamp stamp) {
            return stamp == null ? "NA" : stamp.uptimeMillis() + "@" + stamp.threadName();
        }
    }

    record MetricSnapshot(
            long count,
            long lastMillis,
            long p50Millis,
            long p95Millis,
            long p99Millis,
            long maxMillis,
            long overflowCount,
            long retainedCount,
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

    record Snapshot(
            Map<Metric, MetricSnapshot> metrics,
            Map<String, MetricSnapshot> recognizers,
            Map<Counter, Long> counters,
            long screenshotQueueDepth,
            long screenshotQueueHighWater,
            long ocrQueueDepth,
            long ocrQueueHighWater,
            long activeOcrTransactions,
            long activeRecognizers,
            long elapsedMillis,
            long completedTimelineCount) {
        Snapshot {
            metrics = Map.copyOf(metrics);
            recognizers = Map.copyOf(recognizers);
            counters = Map.copyOf(counters);
        }

        MetricSnapshot metric(Metric metric) {
            MetricSnapshot snapshot = metrics.get(metric);
            return snapshot == null
                    ? new MetricSnapshot(0L, -1L, -1L, -1L, -1L, -1L, 0L, 0L, "", false)
                    : snapshot;
        }

        long count(Counter counter) {
            return counters.getOrDefault(counter, 0L);
        }

        String rate(Counter counter) {
            long count = count(counter);
            if (count <= 0L || elapsedMillis <= 0L) {
                return "NA";
            }
            return formatRate(count * 1_000d / elapsedMillis);
        }

        String screenshotsPerAction() {
            return ratio(Counter.SCREENSHOTS, Counter.GESTURES_DISPATCHED);
        }

        String ocrPerAction() {
            return ratio(Counter.OCR_TRANSACTIONS, Counter.GESTURES_DISPATCHED);
        }

        private String ratio(Counter numerator, Counter denominator) {
            long denominatorCount = count(denominator);
            return denominatorCount <= 0L
                    ? "NA" : formatRate(count(numerator) / (double) denominatorCount);
        }

        private static String formatRate(double value) {
            return String.format(java.util.Locale.US, "%.3f", value);
        }
    }

    private static final class StageStats {
        private final BoundedLatencyStats samples = new BoundedLatencyStats();
        private String lastThreadName = "";
        private boolean lastMainThread;

        void add(long durationMillis, String threadName, boolean mainThread) {
            samples.add(durationMillis);
            lastThreadName = threadName == null || threadName.isBlank() ? "unknown" : threadName;
            lastMainThread = mainThread;
        }

        MetricSnapshot snapshot() {
            BoundedLatencyStats.Snapshot snapshot = samples.snapshot();
            return new MetricSnapshot(
                    snapshot.count(),
                    snapshot.lastMillis(),
                    snapshot.p50Millis(),
                    snapshot.p95Millis(),
                    snapshot.p99Millis(),
                    snapshot.maxMillis(),
                    snapshot.overflowCount(),
                    snapshot.retainedCount(),
                    lastThreadName,
                    lastMainThread);
        }
    }

    private static final class MutableTimeline {
        private final OcrScan.TransactionId id;
        private final String profile;
        private final long captureAtUptimeMillis;
        private final long transactionCreatedAtUptimeMillis;
        private final EnumMap<Event, Stamp> events = new EnumMap<>(Event.class);
        private final LinkedHashMap<String, RecognizerSpan> recognizers = new LinkedHashMap<>();
        private final ArrayList<Stamp> gestureDispatches = new ArrayList<>();
        private boolean finished;

        MutableTimeline(
                OcrScan.TransactionId id,
                String profile,
                long captureAtUptimeMillis,
                long transactionCreatedAtUptimeMillis) {
            this.id = id;
            this.profile = profile;
            this.captureAtUptimeMillis = captureAtUptimeMillis;
            this.transactionCreatedAtUptimeMillis = transactionCreatedAtUptimeMillis;
        }

        TimelineSnapshot snapshot(String outcome) {
            LinkedHashMap<String, RecognizerSnapshot> recognizerSnapshots = new LinkedHashMap<>();
            for (Map.Entry<String, RecognizerSpan> entry : recognizers.entrySet()) {
                RecognizerSpan span = entry.getValue();
                recognizerSnapshots.put(
                        entry.getKey(),
                        new RecognizerSnapshot(
                                span.start,
                                span.end,
                                span.callbackEnqueue,
                                span.callbackStart,
                                span.tokenStart,
                                span.tokenEnd));
            }
            return new TimelineSnapshot(
                    id, profile, events, recognizerSnapshots, gestureDispatches, outcome);
        }
    }

    private static final class RecognizerSpan {
        private Stamp start;
        private Stamp end;
        private Stamp callbackEnqueue;
        private Stamp callbackStart;
        private Stamp tokenStart;
        private Stamp tokenEnd;
        private boolean open;
    }

    private long now() {
        try {
            return uptimeMillis.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
