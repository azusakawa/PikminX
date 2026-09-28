package com.pikminx.helper;

import java.util.List;
import java.util.Objects;

/** Owns admission, dispatch and terminal ownership of screenshot transactions. */
final class CaptureCoordinator<R, F> {
    enum Presentation { NONE, RETURN_REWARD }
    enum FailureKind { TRANSIENT, PERMANENT, TIMEOUT, CANCELLED, COPY, STALE }

    record Request<R>(R value, List<ScreenshotOverlayMask.Region> overlayRegions, long generation,
            CaptureGeometry.Mode mode, CaptureGeometry.Bounds expectedBounds,
            CaptureGeometry.Bounds targetBounds, int displayId, int windowId,
            long captureSequence, long admissionEpoch, Presentation presentation,
            long preparationDelayMillis) {
        Request {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(expectedBounds, "expectedBounds");
            Objects.requireNonNull(presentation, "presentation");
            overlayRegions = overlayRegions == null ? List.of() : List.copyOf(overlayRegions);
            if (generation < 0 || displayId < 0 || windowId < -1 || captureSequence < 0
                    || admissionEpoch < 0 || preparationDelayMillis < 0) {
                throw new IllegalArgumentException("invalid capture request identity");
            }
            if (mode == CaptureGeometry.Mode.WINDOW && targetBounds == null) {
                throw new IllegalArgumentException("window capture requires target bounds");
            }
        }

        Request<R> withCaptureSequence(long sequence) {
            return new Request<>(
                    value,
                    overlayRegions,
                    generation,
                    mode,
                    expectedBounds,
                    targetBounds,
                    displayId,
                    windowId,
                    sequence,
                    admissionEpoch,
                    presentation,
                    preparationDelayMillis);
        }
    }

    record Frame<F>(F value, int width, int height, long capturedAtUptimeMillis) {
        Frame {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("invalid frame size");
        }
    }

    record Outcome<R, F>(long id, Request<R> request, Frame<F> frame, int errorCode,
            FailureKind failure, CaptureGeometry geometry, boolean retried) {
        boolean successful() { return frame != null && failure == null; }
    }

    record Snapshot(int depth, long highWater, long enqueuedCount, long completedCount,
            long lateCallbackCount) {}

    interface Listener<R, F> {
        void completed(Outcome<R, F> outcome);
        default void retrying(Request<R> request, FailureKind failure, long delayMillis) {}
        default void retrying(Request<R> request, FailureKind failure, int errorCode,
                long delayMillis) { retrying(request, failure, delayMillis); }
    }

    interface Platform<R, F> {
        Object prepareOverlay(Request<R> request);
        void restoreOverlay(Request<R> request, Object token);
        void dispatch(Request<R> request, Callback<F> callback);
        Object schedule(long delayMillis, Runnable timeout);
        void cancel(Object handle);
    }

    interface Callback<F> {
        boolean success(long captureSequence, long generation, long admissionEpoch, Frame<F> frame);
        boolean failure(FailureKind kind);
        default boolean failure(FailureKind kind, int errorCode) { return failure(kind); }
    }

    private final ScreenshotRequestQueue<Request<R>> queue = new ScreenshotRequestQueue<>();
    private final Platform<R, F> platform;
    private final Listener<R, F> listener;
    private final long timeoutMillis;
    private Object timeout;
    private Object preparation;
    private Object presentationToken;
    private boolean presentationPrepared;
    private int failures;
    private long latestCaptureSequence;
    private boolean stopped;

    CaptureCoordinator(Platform<R, F> platform, Listener<R, F> listener, long timeoutMillis) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.listener = Objects.requireNonNull(listener, "listener");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeout must be positive");
        this.timeoutMillis = timeoutMillis;
    }

    long enqueue(Request<R> request) {
        if (stopped) return -1;
        Request<R> admitted = Objects.requireNonNull(request, "request");
        if (admitted.captureSequence() == 0) {
            if (latestCaptureSequence == Long.MAX_VALUE) {
                throw new IllegalStateException("capture sequence exhausted");
            }
            admitted = admitted.withCaptureSequence(++latestCaptureSequence);
        } else {
            latestCaptureSequence = Math.max(latestCaptureSequence, admitted.captureSequence());
        }
        queue.enqueue(admitted);
        dispatchIfIdle();
        return queue.snapshot().enqueuedCount();
    }

    /** Starts the next FIFO request exactly once through ScreenshotRequestQueue.claimNext(). */
    private void dispatchIfIdle() {
        if (stopped || queue.active() != null) return;
        ScreenshotRequestQueue.Entry<Request<R>> entry = queue.claimNext();
        if (entry == null) return;
        Request<R> request = entry.request();
        presentationToken = platform.prepareOverlay(request);
        presentationPrepared = true;
        if (request.preparationDelayMillis() > 0) {
            preparation = platform.schedule(request.preparationDelayMillis(),
                    () -> dispatchInitial(entry.id(), request));
            return;
        }
        dispatchInitial(entry.id(), request);
    }

    private void dispatchInitial(long id, Request<R> request) {
        preparation = null;
        if (stopped || queue.active() == null || queue.active().id() != id) return;
        timeout = platform.schedule(timeoutMillis, () -> timeout(id));
        try {
            platform.dispatch(request, callback(id, request));
        } catch (RuntimeException ex) {
            fail(id, request, FailureKind.TRANSIENT, -1);
        }
    }

    private Callback<F> callback(long id, Request<R> request) {
        return new Callback<>() {
            public boolean success(long sequence, long generation, long epoch, Frame<F> frame) {
                if (queue.active() == null || queue.active().id() != id) return false;
                if (sequence != request.captureSequence() || generation != request.generation()
                        || epoch != request.admissionEpoch()) {
                    return false;
                }
                complete(id, request, frame);
                return true;
            }
            public boolean failure(FailureKind kind) { return failure(kind, -1); }
            public boolean failure(FailureKind kind, int errorCode) {
                if (queue.active() == null || queue.active().id() != id) return false;
                fail(id, request, kind == null ? FailureKind.TRANSIENT : kind, errorCode);
                return true;
            }
        };
    }

    private void timeout(long id) { if (queue.active() != null && queue.active().id() == id)
        fail(id, queue.active().request(), FailureKind.TIMEOUT, -1); }

    private void fail(long id, Request<R> request, FailureKind kind) {
        fail(id, request, kind, -1);
    }

    private void fail(long id, Request<R> request, FailureKind kind, int errorCode) {
        if (queue.active() == null || queue.active().id() != id) return;
        if (kind == FailureKind.TRANSIENT
                || kind == FailureKind.TIMEOUT
                || kind == FailureKind.COPY) {
            ScreenshotRetryPolicy.Decision decision = ScreenshotRetryPolicy.afterFailure(
                    ScreenshotRetryPolicy.FailureType.TRANSIENT, ++failures);
            if (decision.retry()) {
                listener.retrying(request, kind, errorCode, decision.delayMillis());
                terminal(id, request, null, errorCode, kind, true, false);
                return;
            }
        }
        terminal(id, request, null, errorCode, kind, false, true);
    }

    private void complete(long id, Request<R> request, Frame<F> frame) {
        terminal(id, request, frame, -1, null, false, true);
    }

    private void terminal(long id, Request<R> request, Frame<F> frame, int errorCode,
            FailureKind failure, boolean retryScheduled, boolean resetFailures) {
        if (!queue.finish(id)) return;
        cancelTimeout();
        if (presentationPrepared) platform.restoreOverlay(request, presentationToken);
        presentationToken = null;
        presentationPrepared = false;
        listener.completed(new Outcome<>(id, request, frame, errorCode, failure,
                frame == null ? null : new CaptureGeometry(request.mode(), frame.width(), frame.height(),
                        request.expectedBounds(), request.targetBounds(), request.displayId(), request.windowId(),
                        request.captureSequence(), frame.capturedAtUptimeMillis()), retryScheduled));
        if (resetFailures) failures = 0;
        dispatchIfIdle();
    }

    void clear() {
        cancelTimeout();
        if (preparation != null) platform.cancel(preparation);
        preparation = null;
        Request<R> request = queue.active() == null ? null : queue.active().request();
        long id = queue.active() == null ? -1 : queue.active().id();
        boolean cancelled = queue.active() != null && queue.finish(id);
        if (cancelled && presentationPrepared) platform.restoreOverlay(request, presentationToken);
        queue.clear();
        presentationToken = null;
        presentationPrepared = false;
        if (cancelled) listener.completed(new Outcome<>(id, request, null, -1,
                FailureKind.CANCELLED, null, false));
    }
    void stop() { stopped = true; clear(); }
    boolean isStopped() { return stopped; }
    long latestCaptureSequence() { return latestCaptureSequence; }
    void resetStats() {
        failures = 0;
        queue.resetStats();
    }
    Snapshot snapshot() {
        ScreenshotRequestQueue.Snapshot snapshot = queue.snapshot();
        return new Snapshot(
                snapshot.depth(),
                snapshot.highWater(),
                snapshot.enqueuedCount(),
                snapshot.completedCount(),
                snapshot.lateCallbackCount());
    }
    private void cancelTimeout() { if (timeout != null) platform.cancel(timeout); timeout = null; }
}
