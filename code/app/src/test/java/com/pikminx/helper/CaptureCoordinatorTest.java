package com.pikminx.helper;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class CaptureCoordinatorTest {
    private static final CaptureGeometry.Bounds B = new CaptureGeometry.Bounds(0, 0, 100, 100);

    private static final class Fake implements CaptureCoordinator.Platform<String, String> {
        final List<CaptureCoordinator.Callback<String>> callbacks = new ArrayList<>();
        final List<String> prepared = new ArrayList<>();
        final List<String> restored = new ArrayList<>();
        final List<Runnable> scheduled = new ArrayList<>();
        int dispatches;
        public Object prepareOverlay(CaptureCoordinator.Request<String> r) { prepared.add(r.value()); return r.value(); }
        public void restoreOverlay(CaptureCoordinator.Request<String> r, Object token) { restored.add((String) token); }
        public void dispatch(CaptureCoordinator.Request<String> r, CaptureCoordinator.Callback<String> c) { dispatches++; callbacks.add(c); }
        public Object schedule(long delay, Runnable callback) { scheduled.add(callback); return callback; }
        public void cancel(Object handle) { scheduled.remove(handle); }
        void runScheduled() { Runnable r = scheduled.remove(0); r.run(); }
        boolean success(int i, CaptureCoordinator.Request<String> r, String value) {
            return callbacks.get(i).success(r.captureSequence(), r.generation(), r.admissionEpoch(),
                    new CaptureCoordinator.Frame<>(value, 100, 100, 1));
        }
    }

    private CaptureCoordinator.Request<String> request(String value, long sequence) {
        return new CaptureCoordinator.Request<>(value, List.of(new ScreenshotOverlayMask.Region(0, 0, 10, 10)), 7, CaptureGeometry.Mode.WINDOW,
                B, B, 0, 2, sequence, 9, CaptureCoordinator.Presentation.MUSHROOM, 0);
    }

    @Test public void fifoAndExactlyOnceSuccess() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        CaptureCoordinator.Request<String> a = request("A", 1), b = request("B", 2);
        c.enqueue(a); c.enqueue(b); assertEquals(1, fake.dispatches);
        fake.success(0, a, "frame-A"); assertEquals(List.of("A"), fake.restored);
        assertEquals(2, fake.dispatches); assertEquals(1, c.snapshot().depth());
        assertFalse(fake.callbacks.get(0).success(1, 7, 9,
                new CaptureCoordinator.Frame<>("late", 100, 100, 1)));
        assertEquals(1, out.size()); assertEquals(List.of("A"), fake.restored);
        fake.success(1, b, "frame-B");
        assertEquals(2, out.size()); assertEquals(List.of("A", "B"), fake.restored);
    }

    @Test public void transientFailureRetriesAndPermanentFailureCompletes() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake,
                new CaptureCoordinator.Listener<>() { public void completed(CaptureCoordinator.Outcome<String, String> o) { out.add(o); }
                    public void retrying(CaptureCoordinator.Request<String> r, CaptureCoordinator.FailureKind k, long d) { delays.add(d); } }, 100);
        CaptureCoordinator.Request<String> a = request("A", 1); c.enqueue(a);
        fake.callbacks.get(0).failure(CaptureCoordinator.FailureKind.TRANSIENT); assertEquals(1, delays.size());
        assertEquals(1, fake.dispatches); assertEquals(1, out.size());
        c.enqueue(request("A-retry", 2)); assertEquals(2, fake.dispatches);
        fake.callbacks.get(1).failure(CaptureCoordinator.FailureKind.PERMANENT);
        assertEquals(2, out.size()); assertEquals(CaptureCoordinator.FailureKind.PERMANENT, out.get(1).failure());
    }

    @Test public void timeoutEventuallyFailsAndClearStopsActive() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        for (int i = 0; i < ScreenshotRetryPolicy.MAX_CONSECUTIVE_FAILURES; i++) {
            c.enqueue(request("A" + i, i + 1));
            fake.runScheduled();
        }
        assertFalse(out.isEmpty()); assertEquals(CaptureCoordinator.FailureKind.TIMEOUT, out.get(0).failure());
        c.enqueue(request("B", 2)); c.clear(); assertEquals(
                CaptureCoordinator.FailureKind.CANCELLED, out.get(out.size() - 1).failure());
        c.stop(); assertTrue(c.isStopped());
    }

    @Test public void staleIdentityDoesNotCompleteOrRestore() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        CaptureCoordinator.Request<String> a = request("A", 1); c.enqueue(a);
        assertFalse(fake.callbacks.get(0).success(1, 8, 9,
                new CaptureCoordinator.Frame<>("wrong-generation", 100, 100, 1)));
        assertFalse(fake.callbacks.get(0).success(1, 7, 10,
                new CaptureCoordinator.Frame<>("wrong-epoch", 100, 100, 1)));
        assertFalse(fake.callbacks.get(0).success(99, 7, 9,
                new CaptureCoordinator.Frame<>("bad", 100, 100, 1)));
        assertTrue(out.isEmpty()); assertTrue(fake.restored.isEmpty()); assertEquals(1, fake.dispatches);
    }

    @Test public void coordinatorAllocatesCaptureSequenceWhenRequestLeavesItUnset() {
        Fake fake = new Fake();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, outcome -> { }, 100);
        c.enqueue(new CaptureCoordinator.Request<>("A", List.of(), 7, CaptureGeometry.Mode.WINDOW,
                B, B, 0, 2, 0, 9, CaptureCoordinator.Presentation.NONE, 0));
        assertEquals(1, c.latestCaptureSequence());
        assertTrue(fake.callbacks.get(0).success(1, 7, 9,
                new CaptureCoordinator.Frame<>("A", 100, 100, 1)));
        c.enqueue(new CaptureCoordinator.Request<>("B", List.of(), 7, CaptureGeometry.Mode.WINDOW,
                B, B, 0, 2, 0, 9, CaptureCoordinator.Presentation.NONE, 0));
        assertEquals(2, c.latestCaptureSequence());
    }

    @Test public void clearCancelsPreparedCaptureAndStopRejectsLaterRequests() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        CaptureCoordinator.Request<String> request = new CaptureCoordinator.Request<>(
                "A", List.of(), 7, CaptureGeometry.Mode.WINDOW, B, B, 0, 2, 1, 9,
                CaptureCoordinator.Presentation.RETURN_REWARD, 50);
        c.enqueue(request);
        c.clear();
        assertEquals(0, fake.dispatches);
        assertEquals(List.of("A"), fake.restored);
        assertEquals(CaptureCoordinator.FailureKind.CANCELLED, out.get(0).failure());
        c.stop();
        assertEquals(-1, c.enqueue(request("B", 2)));
        assertEquals(0, fake.dispatches);
    }

    @Test public void preparationDelayDefersDispatchAndTimeout() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        c.enqueue(new CaptureCoordinator.Request<>("A", List.of(), 7, CaptureGeometry.Mode.WINDOW,
                B, B, 0, 2, 1, 9, CaptureCoordinator.Presentation.RETURN_REWARD, 50));
        assertEquals(0, fake.dispatches); assertEquals(1, fake.scheduled.size());
        fake.runScheduled(); assertEquals(1, fake.dispatches); assertEquals(1, fake.scheduled.size());
    }

    @Test public void platformErrorCodeReachesPermanentOutcome() {
        Fake fake = new Fake(); List<CaptureCoordinator.Outcome<String, String>> out = new ArrayList<>();
        CaptureCoordinator<String, String> c = new CaptureCoordinator<>(fake, out::add, 100);
        c.enqueue(request("A", 1));
        fake.callbacks.get(0).failure(CaptureCoordinator.FailureKind.PERMANENT, 23);
        assertEquals(23, out.get(0).errorCode());
        assertEquals(CaptureCoordinator.FailureKind.PERMANENT, out.get(0).failure());
    }
}
