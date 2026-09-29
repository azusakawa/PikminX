package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class NodeActionAdmissionTest {
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);

    @Test
    public void allowedFocusAndSetTextRunWithoutChangingPayload() {
        ActionAdmission.Decision decision = ActionAdmission.evaluate(
                frame(), current(), 500L);
        AtomicInteger focusCount = new AtomicInteger();
        AtomicInteger setTextCount = new AtomicInteger();
        AtomicReference<String> text = new AtomicReference<>("existing text");
        String payload = "Search Pikmin";

        assertTrue(ActionAdmission.performIfAllowed(decision, () -> {
            focusCount.incrementAndGet();
            return true;
        }));
        assertTrue(ActionAdmission.performIfAllowed(decision, () -> {
            setTextCount.incrementAndGet();
            text.set(payload);
            return true;
        }));

        assertEquals(1, focusCount.get());
        assertEquals(1, setTextCount.get());
        assertEquals(payload, text.get());
    }

    @Test
    public void invalidContextNeverRunsNodeAction() {
        ActionAdmission.FrameContext frame = frame();
        ActionAdmission.CurrentState current = current();
        AtomicInteger count = new AtomicInteger();

        assertRejected(frame, current(8L, 1L, 0L, 100L, GAME_PACKAGE, WINDOW, 12, 0L), count);
        assertRejected(frame, current(7L, 2L, 0L, 100L, GAME_PACKAGE, WINDOW, 12, 0L), count);
        assertRejected(frame, current(7L, 1L, 0L, 100L, GAME_PACKAGE, WINDOW, 12, 1L), count);
        assertRejected(frame, current(7L, 1L, 0L, 100L, "other.package", WINDOW, 12, 0L), count);
        assertRejected(frame, current(7L, 1L, 0L, 100L, GAME_PACKAGE, null, 12, 0L), count);
        assertRejected(frame, current(7L, 1L, 0L, 100L, GAME_PACKAGE,
                new CaptureGeometry.Bounds(40, 120, 1120, 2520), 12, 0L), count);
        assertRejected(frame, current(7L, 1L, 0L, 100L, GAME_PACKAGE, WINDOW, 13, 0L), count);
        assertRejected(frame(0L), current, 50L, count);

        assertEquals(0, count.get());
    }

    private static void assertRejected(
            ActionAdmission.FrameContext frame,
            ActionAdmission.CurrentState current,
            AtomicInteger count) {
        assertRejected(frame, current, 500L, count);
    }

    private static void assertRejected(
            ActionAdmission.FrameContext frame,
            ActionAdmission.CurrentState current,
            long maxAge,
            AtomicInteger count) {
        ActionAdmission.Decision decision = ActionAdmission.evaluate(frame, current, maxAge);
        assertFalse(decision.allowed());
        assertFalse(ActionAdmission.performIfAllowed(decision, () -> {
            count.incrementAndGet();
            return true;
        }));
    }

    private static ActionAdmission.FrameContext frame() {
        return frame(100L);
    }

    private static ActionAdmission.FrameContext frame(long capturedAt) {
        return new ActionAdmission.FrameContext(
                7L, 1L, 0L, capturedAt, GAME_PACKAGE, WINDOW, 12, 0L);
    }

    private static ActionAdmission.CurrentState current() {
        return current(7L, 1L, 0L, 100L, GAME_PACKAGE, WINDOW, 12, 0L);
    }

    private static ActionAdmission.CurrentState current(
            long generation,
            long newestCapture,
            long latestOcr,
            long now,
            String packageName,
            CaptureGeometry.Bounds bounds,
            int windowId,
            long epoch) {
        return new ActionAdmission.CurrentState(
                true, generation, newestCapture, latestOcr, now,
                packageName, bounds, windowId, epoch);
    }
}
