package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Regression coverage for bounded OCR ownership and stale callback handling. */
public final class OcrQueueBackpressureTest {
    @Test
    public void onlyOneOcrTransactionCanOwnThePipelineAtATime() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction first = registry.begin(1L, 1L);

        assertThrows(IllegalStateException.class, () -> registry.begin(1L, 2L));
        assertTrue(registry.clear(first));
        OcrScanner.Transaction second = registry.begin(1L, 2L);
        assertSame(second, registry.active());
    }

    @Test
    public void cancelledCallbackIsDiscardedBeforeParsing() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));
        AtomicInteger parsed = new AtomicInteger();

        assertTrue(OcrScanner.canProcessCallback(transaction, () -> true));
        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.CANCELLED));
        if (OcrScanner.canProcessCallback(transaction, () -> true)) {
            parsed.incrementAndGet();
        }

        assertEquals(0, parsed.get());
        assertFalse(OcrScanner.canProcessCallback(transaction, () -> true));
    }

    @Test
    public void timeoutThenLateSuccessHasNoSecondDelivery() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(2L, 3L, 4L));

        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.TIMEOUT));
        assertFalse(OcrScanner.canProcessCallback(transaction, () -> true));
        assertFalse(transaction.tryFinish(OcrScanner.TerminalState.SUCCESS));
        assertEquals(OcrScanner.TerminalState.TIMEOUT, transaction.state());
    }

    @Test
    public void oldGenerationCannotOverwriteNewUsefulOcr() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction old = registry.begin(3L, 8L);
        assertTrue(registry.clear(old));
        OcrScanner.Transaction current = registry.begin(4L, 9L);

        assertFalse(registry.acceptsCallback(old));
        assertTrue(registry.acceptsCallback(current));
        assertSame(current, registry.active());
    }

    @Test
    public void pauseAndDestroyStyleCleanupLeavesNoActiveRecognition() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction transaction = registry.begin(5L, 10L);

        transaction.tryFinish(OcrScanner.TerminalState.CANCELLED);
        assertTrue(registry.clear(transaction));
        assertFalse(registry.acceptsCallback(transaction));
        assertNull(registry.active());
    }

    @Test
    public void newerUsefulCallbackCannotBeOverwrittenByOlderLateCallback() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction old = registry.begin(6L, 11L);
        assertTrue(old.tryFinish(OcrScanner.TerminalState.TIMEOUT));
        assertTrue(registry.clear(old));
        OcrScanner.Transaction current = registry.begin(6L, 12L);
        AtomicInteger delivered = new AtomicInteger();

        if (OcrScanner.canProcessCallback(old, () -> true)) {
            delivered.incrementAndGet();
        }
        if (OcrScanner.canProcessCallback(current, () -> true)) {
            delivered.incrementAndGet();
        }

        assertEquals(1, delivered.get());
        assertSame(current, registry.active());
    }

    @Test
    public void resourceLeaseWaitsForAllLateTasksAndReleasesOnlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        OcrScanner.TaskResourceLease lease = new OcrScanner.TaskResourceLease(
                3, releases::incrementAndGet);

        lease.releaseAfterTasks();
        assertFalse(lease.released());
        assertFalse(lease.taskComplete());
        assertFalse(lease.taskComplete());
        assertTrue(lease.taskComplete());
        lease.releaseAfterTasks();

        assertTrue(lease.released());
        assertEquals(1, releases.get());
    }

    @Test
    public void screenshotQueueKeepsCaptureBackpressureSerial() {
        ScreenshotRequestQueue<Integer> queue = new ScreenshotRequestQueue<>();
        for (int index = 0; index < 8; index++) {
            queue.enqueue(index);
        }

        ScreenshotRequestQueue.Entry<Integer> active = queue.startNext();
        assertEquals(Integer.valueOf(0), active.request());
        assertSame(active, queue.startNext());
        assertTrue(queue.finish(active.id()));
        ScreenshotRequestQueue.Entry<Integer> next = queue.startNext();
        assertEquals(Integer.valueOf(1), next.request());
        queue.clear();
        assertNull(queue.active());
    }

    @Test
    public void callbackExecutorHasOneWorkerAndAClosedQueueCapacity() {
        assertEquals(1, OcrScanner.OCR_CALLBACK_WORKER_COUNT);
        assertTrue(OcrScanner.OCR_CALLBACK_QUEUE_CAPACITY > 0);
    }
}
