package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScreenshotRequestQueueTest {
    @Test
    public void serializesRequestsAndRejectsLateCallbacks() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        queue.enqueue("first");
        queue.enqueue("second");
        assertEquals(2, queue.depth());
        assertEquals(2L, queue.highWater());

        ScreenshotRequestQueue.Entry<String> first = queue.startNext();
        assertSame(first, queue.startNext());
        assertFalse(queue.finish(first.id() + 1));
        assertTrue(queue.finish(first.id()));

        ScreenshotRequestQueue.Entry<String> second = queue.startNext();
        assertTrue(second.id() > first.id());
        queue.clear();
        assertFalse(queue.finish(second.id()));
        assertNull(queue.startNext());
        assertEquals(0, queue.depth());
        assertEquals(2L, queue.snapshot().lateCallbackCount());
    }

    @Test
    public void resetStatsStartsAFreshHighWaterWindow() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        queue.enqueue("first");
        queue.startNext();
        queue.resetStats();
        queue.finish(queue.active().id());

        assertEquals(1L, queue.highWater());
        assertEquals(1L, queue.snapshot().completedCount());
    }

    @Test
    public void dispatchClaimAllowsOnePlatformDispatchPerLogicalRequest() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        int activeTransitions = 0;
        int platformDispatches = 0;
        int completions = 0;

        queue.enqueue("request-A");
        ScreenshotRequestQueue.Entry<String> first = queue.claimNext();
        if (first != null) {
            activeTransitions++;
            platformDispatches++;
        }
        long logicalRequestId = first.id();
        assertEquals("request-A", first.request());
        assertEquals(1L, logicalRequestId);
        assertSame(first, queue.active());
        assertNull(queue.claimNext()); // forced/reentrant dispatch cannot claim A twice

        if (queue.finish(first.id())) {
            completions++;
        }
        assertFalse(queue.finish(first.id())); // duplicate callback is not a completion

        assertEquals(1L, queue.snapshot().enqueuedCount());
        assertEquals(1, activeTransitions);
        assertEquals(1, platformDispatches);
        assertEquals(1, completions);
        assertEquals(1L, queue.snapshot().completedCount());
    }

    @Test
    public void lateCallbackCannotRestoreCapturePresentationForNewActiveRequest() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        int presentationRestores = 0;
        boolean presentationHidden = false;

        queue.enqueue("request-A");
        ScreenshotRequestQueue.Entry<String> first = queue.claimNext();
        presentationHidden = true;
        assertTrue(queue.finish(first.id())); // A terminal path owns A's restoration.
        presentationHidden = false;
        presentationRestores++;

        queue.enqueue("request-B");
        ScreenshotRequestQueue.Entry<String> second = queue.claimNext();
        presentationHidden = true;
        if (queue.finish(first.id())) { // A arrives late while B owns the capture.
            presentationHidden = false;
            presentationRestores++;
        }

        assertSame(second, queue.active());
        assertTrue(presentationHidden);
        assertEquals(1, presentationRestores);
        assertTrue(queue.finish(second.id()));
        presentationHidden = false;
        presentationRestores++;
        assertFalse(presentationHidden);
        assertEquals(2, presentationRestores);
        assertEquals(1L, queue.snapshot().lateCallbackCount());
    }

    @Test
    public void clearResetsDispatchClaim() {
        ScreenshotRequestQueue<String> queue = new ScreenshotRequestQueue<>();
        queue.enqueue("discarded");
        assertEquals("discarded", queue.claimNext().request());
        queue.clear();

        queue.enqueue("next");
        assertEquals("next", queue.claimNext().request());
    }
}
