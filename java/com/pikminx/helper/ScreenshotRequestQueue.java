package com.pikminx.helper;

import java.util.ArrayDeque;

/** Serializes screenshot requests and rejects callbacks that no longer own the active request. */
final class ScreenshotRequestQueue<T> {
    record Entry<T>(long id, T request) {}

    private final ArrayDeque<Entry<T>> pending = new ArrayDeque<>();
    private long nextId;
    private Entry<T> active;

    void enqueue(T request) {
        pending.addLast(new Entry<>(++nextId, request));
    }

    Entry<T> startNext() {
        if (active == null) {
            active = pending.pollFirst();
        }
        return active;
    }

    Entry<T> active() {
        return active;
    }

    boolean finish(long id) {
        if (active == null || active.id() != id) {
            return false;
        }
        active = null;
        return true;
    }

    void clear() {
        pending.clear();
        active = null;
    }
}
