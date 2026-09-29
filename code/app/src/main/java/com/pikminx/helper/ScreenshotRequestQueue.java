package com.pikminx.helper;

import java.util.ArrayDeque;

/** Serializes screenshot requests and rejects callbacks that no longer own the active request. */
final class ScreenshotRequestQueue<T> {
    record Entry<T>(long id, T request) {}

    private final ArrayDeque<Entry<T>> pending = new ArrayDeque<>();
    private long nextId;
    private Entry<T> active;
    private boolean dispatchClaimed;
    private long highWater;
    private long enqueuedCount;
    private long completedCount;
    private long lateCallbackCount;

    void enqueue(T request) {
        pending.addLast(new Entry<>(++nextId, request));
        enqueuedCount = boundedIncrement(enqueuedCount);
        highWater = Math.max(highWater, depth());
    }

    Entry<T> startNext() {
        if (active == null) {
            active = pending.pollFirst();
        }
        return active;
    }

    /** Returns the active entry once for platform dispatch; forced reentry is rejected. */
    Entry<T> claimNext() {
        Entry<T> entry = startNext();
        if (entry == null || dispatchClaimed) {
            return null;
        }
        dispatchClaimed = true;
        return entry;
    }

    Entry<T> active() {
        return active;
    }

    boolean finish(long id) {
        if (active == null || active.id() != id) {
            lateCallbackCount = boundedIncrement(lateCallbackCount);
            return false;
        }
        active = null;
        dispatchClaimed = false;
        completedCount = boundedIncrement(completedCount);
        return true;
    }

    void clear() {
        pending.clear();
        active = null;
        dispatchClaimed = false;
    }

    int depth() {
        return pending.size() + (active == null ? 0 : 1);
    }

    long highWater() {
        return highWater;
    }

    Snapshot snapshot() {
        return new Snapshot(depth(), highWater, enqueuedCount, completedCount, lateCallbackCount);
    }

    void resetStats() {
        highWater = depth();
        enqueuedCount = 0L;
        completedCount = 0L;
        lateCallbackCount = 0L;
    }

    private static long boundedIncrement(long value) {
        return value >= BoundedLatencyStats.MAX_COUNT
                ? BoundedLatencyStats.MAX_COUNT : value + 1L;
    }

    record Snapshot(
            int depth,
            long highWater,
            long enqueuedCount,
            long completedCount,
            long lateCallbackCount) {}
}
