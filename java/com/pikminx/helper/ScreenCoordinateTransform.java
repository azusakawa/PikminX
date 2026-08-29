package com.pikminx.helper;

/** Maps one screenshot's local points to the absolute coordinates required by gestures. */
final class ScreenCoordinateTransform {
    record Point(int x, int y) {}
    record ScreenshotRect(int left, int top, int right, int bottom) {
        ScreenshotRect {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Screenshot rect must have positive size");
            }
        }

        int width() { return right - left; }
        int height() { return bottom - top; }
    }

    static Point toScreen(int x, int y, CaptureGeometry geometry) {
        CaptureGeometry.Bounds bounds = geometry.expectedSourceBoundsOnScreen();
        return new Point(
                clamp(
                        bounds.left() + Math.round(x * geometry.scaleX()),
                        bounds.left(),
                        bounds.right() - 1),
                clamp(
                        bounds.top() + Math.round(y * geometry.scaleY()),
                        bounds.top(),
                        bounds.bottom() - 1));
    }

    /** Maps the capture-time target window into this screenshot's local coordinate space. */
    static ScreenshotRect targetWindowInScreenshot(CaptureGeometry geometry) {
        CaptureGeometry.Bounds target = geometry.targetWindowBoundsOnScreen();
        if (target == null) {
            return null;
        }
        CaptureGeometry.Bounds source = geometry.expectedSourceBoundsOnScreen();
        CaptureGeometry.Bounds effective = target.intersection(source);
        if (effective == null) {
            return null;
        }
        int left = toScreenshotX(effective.left(), source, geometry);
        int top = toScreenshotY(effective.top(), source, geometry);
        int right = toScreenshotX(effective.right(), source, geometry);
        int bottom = toScreenshotY(effective.bottom(), source, geometry);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new ScreenshotRect(left, top, right, bottom);
    }

    private static int toScreenshotX(
            int screenX, CaptureGeometry.Bounds source, CaptureGeometry geometry) {
        return clamp(
                Math.round((screenX - source.left()) / geometry.scaleX()),
                0,
                geometry.bitmapWidth());
    }

    private static int toScreenshotY(
            int screenY, CaptureGeometry.Bounds source, CaptureGeometry geometry) {
        return clamp(
                Math.round((screenY - source.top()) / geometry.scaleY()),
                0,
                geometry.bitmapHeight());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private ScreenCoordinateTransform() {}
}
