package com.pikminx.helper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A small reference-counted gate for resources used by asynchronous analysis. */
final class DeferredCleanup {
    private final AtomicInteger holds = new AtomicInteger();
    private final AtomicBoolean cleanupRequested = new AtomicBoolean();
    private final AtomicBoolean cleaned = new AtomicBoolean();
    private final Runnable cleanup;

    DeferredCleanup(Runnable cleanup) {
        this.cleanup = cleanup == null ? () -> { } : cleanup;
    }

    synchronized boolean tryRetain() {
        if (cleanupRequested.get() || cleaned.get()) {
            return false;
        }
        holds.incrementAndGet();
        return true;
    }

    void release() {
        boolean shouldClean = false;
        synchronized (this) {
            int remaining = holds.get();
            if (remaining <= 0) {
                return;
            }
            holds.decrementAndGet();
            if (holds.get() == 0 && cleanupRequested.get()) {
                shouldClean = cleaned.compareAndSet(false, true);
            }
        }
        if (shouldClean) {
            cleanup.run();
        }
    }

    void requestCleanup() {
        boolean shouldClean = false;
        synchronized (this) {
            cleanupRequested.set(true);
            if (holds.get() == 0) {
                shouldClean = cleaned.compareAndSet(false, true);
            }
        }
        if (shouldClean) {
            cleanup.run();
        }
    }

    int holdCount() {
        return holds.get();
    }

    boolean isCleanupRequested() {
        return cleanupRequested.get();
    }

    boolean isCleaned() {
        return cleaned.get();
    }
}
