package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class DeferredCleanupTest {
    @Test
    public void cleanupWaitsForWorkerAndMainHoldsAndRunsOnce() {
        AtomicInteger cleanupCount = new AtomicInteger();
        DeferredCleanup cleanup = new DeferredCleanup(cleanupCount::incrementAndGet);

        assertTrue(cleanup.tryRetain());
        assertTrue(cleanup.tryRetain());
        cleanup.requestCleanup();
        assertEquals(0, cleanupCount.get());

        cleanup.release();
        assertEquals(0, cleanupCount.get());
        cleanup.release();
        assertEquals(1, cleanupCount.get());
        cleanup.release();
        cleanup.requestCleanup();
        assertEquals(1, cleanupCount.get());
        assertFalse(cleanup.tryRetain());
    }

    @Test
    public void cleanupIsMarkedBeforeAnExceptionAndIsNotRetried() {
        AtomicInteger cleanupCount = new AtomicInteger();
        DeferredCleanup cleanup = new DeferredCleanup(() -> {
            cleanupCount.incrementAndGet();
            throw new IllegalStateException("test cleanup failure");
        });

        assertTrue(cleanup.tryRetain());
        cleanup.release();
        try {
            cleanup.requestCleanup();
        } catch (IllegalStateException expected) {
            // The owner observes the cleanup failure, but the gate remains resolved.
        }
        cleanup.requestCleanup();

        assertEquals(1, cleanupCount.get());
        assertTrue(cleanup.isCleaned());
        assertFalse(cleanup.tryRetain());
    }
}
