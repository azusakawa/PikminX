package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class CaptureGeometryTest {
    @Test
    public void displayMappingAssumptionIsSafeForFullFrame() {
        CaptureGeometry geometry = displayGeometry(1L, null);
        OcrScan.TransactionId id = new OcrScan.TransactionId(1L, 1L, 1L);
        OcrScan.Frame frame = frame(
                id,
                OcrScan.Profile.FULL_CHINESE,
                OcrScan.Transform.create(
                        OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry),
                geometry,
                true);

        assertTrue(frame.canDriveAction(id));
    }

    @Test
    public void targetWindowFallbackCannotReachActionConsumer() {
        CaptureGeometry geometry = displayGeometry(1L, null);
        OcrScan.TransactionId id = new OcrScan.TransactionId(1L, 1L, 1L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.PETAL_LIST, 1080, 2400, geometry);
        OcrScan.Frame frame = frame(
                id, OcrScan.Profile.PETAL_LIST, transform, geometry, true);
        AtomicInteger actions = new AtomicInteger();

        if (frame.canDriveAction(id)) {
            actions.incrementAndGet();
        }

        assertTrue(transform.fallbackToScreenshot());
        assertEquals(0, actions.get());
    }

    @Test
    public void transformFromAnotherCaptureCannotReachActionConsumer() {
        CaptureGeometry first = displayGeometry(
                1L, new CaptureGeometry.Bounds(0, 100, 1080, 2200));
        CaptureGeometry second = displayGeometry(
                2L, new CaptureGeometry.Bounds(0, 100, 1080, 2200));
        OcrScan.TransactionId secondId = new OcrScan.TransactionId(1L, 2L, 2L);
        OcrScan.Frame frame = frame(
                secondId,
                OcrScan.Profile.PETAL_LIST,
                OcrScan.Transform.create(
                        OcrScan.Profile.PETAL_LIST, 1080, 2400, first),
                second,
                true);

        assertFalse(frame.canDriveAction(secondId));
    }

    @Test
    public void mismatchedIdentityAndIncompleteFrameCannotReachActionConsumer() {
        CaptureGeometry geometry = displayGeometry(3L, null);
        OcrScan.TransactionId frameId = new OcrScan.TransactionId(2L, 3L, 8L);
        OcrScan.Frame frame = frame(
                frameId,
                OcrScan.Profile.FULL_CHINESE,
                OcrScan.Transform.create(
                        OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry),
                geometry,
                false);

        assertFalse(frame.canDriveAction(new OcrScan.TransactionId(2L, 3L, 9L)));
        assertFalse(frame.canDriveAction(frameId));
    }

    @Test
    public void inconsistentWindowBoundsAreNotActionSafe() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW,
                1080,
                2400,
                new CaptureGeometry.Bounds(50, 100, 1130, 2500),
                new CaptureGeometry.Bounds(60, 100, 1140, 2500),
                0,
                4L,
                100L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry);

        assertFalse(geometry.isActionSafe(transform));
        assertFalse(geometry.matchesBitmap(720, 1600));
    }

    private static OcrScan.Frame frame(
            OcrScan.TransactionId id,
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry geometry,
            boolean complete) {
        return new OcrScan.Frame(
                id, profile, transform, List.of(), 0L, null, geometry, complete);
    }

    private static CaptureGeometry displayGeometry(
            long captureSequence, CaptureGeometry.Bounds targetWindow) {
        return new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                1080,
                2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                targetWindow,
                0,
                captureSequence,
                100L);
    }
}
