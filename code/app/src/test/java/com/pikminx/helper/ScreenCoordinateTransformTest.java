package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScreenCoordinateTransformTest {
    @Test
    public void mapsWindowScreenshotInsideExpectedWindowBounds() {
        CaptureGeometry geometry = windowGeometry(
                1080, 2400, new CaptureGeometry.Bounds(100, 180, 1180, 2580), 1);

        assertEquals(new ScreenCoordinateTransform.Point(100, 180),
                ScreenCoordinateTransform.toScreen(0, 0, geometry));
        assertEquals(new ScreenCoordinateTransform.Point(640, 1380),
                ScreenCoordinateTransform.toScreen(540, 1200, geometry));
        assertEquals(new ScreenCoordinateTransform.Point(1179, 2579),
                ScreenCoordinateTransform.toScreen(1080, 2400, geometry));
    }

    @Test
    public void scalesDisplayScreenshotToPhysicalDisplay() {
        CaptureGeometry geometry = displayGeometry(
                720, 1600, new CaptureGeometry.Bounds(0, 0, 1080, 2400), 1);

        assertEquals(new ScreenCoordinateTransform.Point(540, 1200),
                ScreenCoordinateTransform.toScreen(360, 800, geometry));
    }

    @Test
    public void preservesNonZeroScreenshotOrigin() {
        CaptureGeometry geometry = windowGeometry(
                1000, 2200, new CaptureGeometry.Bounds(40, 120, 1040, 2320), 1);

        assertEquals(new ScreenCoordinateTransform.Point(40, 120),
                ScreenCoordinateTransform.toScreen(0, 0, geometry));
        assertEquals(new ScreenCoordinateTransform.Point(540, 1220),
                ScreenCoordinateTransform.toScreen(500, 1100, geometry));
        assertEquals(new ScreenCoordinateTransform.Point(1039, 2319),
                ScreenCoordinateTransform.toScreen(1000, 2200, geometry));
    }

    @Test
    public void keepsSameResolutionDisplayMappingIdentity() {
        CaptureGeometry geometry = displayGeometry(
                1080, 2400, new CaptureGeometry.Bounds(0, 0, 1080, 2400), 1);

        assertEquals(new ScreenCoordinateTransform.Point(540, 1200),
                ScreenCoordinateTransform.toScreen(540, 1200, geometry));
    }

    @Test
    public void feedSearchUsesDisplayBitmapPointWhenGameWindowMatchesCaptureSize() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 1080, 2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2160),
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                0, 1L, 100L);

        assertEquals(new ScreenCoordinateTransform.Point(980, 474),
                ScreenCoordinateTransform.feedSearchPointToScreen(980, 474, geometry));
    }

    @Test
    public void feedSearchKeepsScaledMappingWhenTargetDoesNotMatchCaptureSize() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                0, 1L, 100L);

        assertEquals(new ScreenCoordinateTransform.Point(540, 1200),
                ScreenCoordinateTransform.feedSearchPointToScreen(360, 800, geometry));
    }

    @Test
    public void captureGeometryDoesNotShareMutableMappingState() {
        CaptureGeometry captureA = windowGeometry(
                1080, 2400, new CaptureGeometry.Bounds(100, 180, 1180, 2580), 1);
        CaptureGeometry captureB = displayGeometry(
                720, 1600, new CaptureGeometry.Bounds(0, 0, 1080, 2400), 2);

        assertEquals(new ScreenCoordinateTransform.Point(540, 1200),
                ScreenCoordinateTransform.toScreen(360, 800, captureB));
        assertEquals(new ScreenCoordinateTransform.Point(640, 1380),
                ScreenCoordinateTransform.toScreen(540, 1200, captureA));
    }

    @Test
    public void inverseMapsAndIntersectsTargetWindowInScreenshotSpace() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(-50, 120, 1080, 2280),
                0, 1L, 100L);

        assertEquals(new ScreenCoordinateTransform.ScreenshotRect(0, 80, 720, 1520),
                ScreenCoordinateTransform.targetWindowInScreenshot(geometry));
    }

    private static CaptureGeometry windowGeometry(
            int width, int height, CaptureGeometry.Bounds bounds, long sequence) {
        return new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW, width, height, bounds, bounds, 0, sequence, 100L);
    }

    private static CaptureGeometry displayGeometry(
            int width, int height, CaptureGeometry.Bounds bounds, long sequence) {
        return new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, width, height, bounds, null, 0, sequence, 100L);
    }
}
