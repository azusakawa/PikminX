package com.pikminx.helper;

/**
 * Immutable mapping assumption for one accessibility screenshot.
 *
 * <p>{@link android.accessibilityservice.AccessibilityService.ScreenshotResult} does not expose
 * the buffer origin or bounds. {@code expectedSourceBoundsOnScreen} is therefore the geometry
 * this app uses to map this bitmap, not API-confirmed screenshot metadata.</p>
 */
final class CaptureGeometry {
    enum Mode { WINDOW, DISPLAY }

    record Bounds(int left, int top, int right, int bottom) {
        Bounds {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Bounds must have positive size");
            }
        }

        int width() { return right - left; }
        int height() { return bottom - top; }

        Bounds intersection(Bounds other) {
            int intersectionLeft = Math.max(left, other.left);
            int intersectionTop = Math.max(top, other.top);
            int intersectionRight = Math.min(right, other.right);
            int intersectionBottom = Math.min(bottom, other.bottom);
            if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
                return null;
            }
            return new Bounds(
                    intersectionLeft, intersectionTop, intersectionRight, intersectionBottom);
        }
    }

    private final Mode mode;
    private final int bitmapWidth;
    private final int bitmapHeight;
    private final Bounds expectedSourceBoundsOnScreen;
    private final Bounds targetWindowBoundsOnScreen;
    private final int displayId;
    private final long captureSequence;
    private final long capturedAtUptimeMillis;

    CaptureGeometry(Mode mode, int bitmapWidth, int bitmapHeight,
            Bounds expectedSourceBoundsOnScreen, Bounds targetWindowBoundsOnScreen,
            int displayId, long captureSequence, long capturedAtUptimeMillis) {
        if (mode == null || expectedSourceBoundsOnScreen == null) {
            throw new IllegalArgumentException("Capture mode and expected bounds are required");
        }
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || displayId < 0 || captureSequence < 1) {
            throw new IllegalArgumentException("Invalid capture geometry");
        }
        if (mode == Mode.WINDOW && targetWindowBoundsOnScreen == null) {
            throw new IllegalArgumentException("Window capture requires window bounds");
        }
        this.mode = mode;
        this.bitmapWidth = bitmapWidth;
        this.bitmapHeight = bitmapHeight;
        this.expectedSourceBoundsOnScreen = expectedSourceBoundsOnScreen;
        this.targetWindowBoundsOnScreen = targetWindowBoundsOnScreen;
        this.displayId = displayId;
        this.captureSequence = captureSequence;
        this.capturedAtUptimeMillis = capturedAtUptimeMillis;
    }

    Mode mode() { return mode; }
    int bitmapWidth() { return bitmapWidth; }
    int bitmapHeight() { return bitmapHeight; }
    Bounds expectedSourceBoundsOnScreen() { return expectedSourceBoundsOnScreen; }
    Bounds targetWindowBoundsOnScreen() { return targetWindowBoundsOnScreen; }
    int displayId() { return displayId; }
    long captureSequence() { return captureSequence; }
    long capturedAtUptimeMillis() { return capturedAtUptimeMillis; }
    float scaleX() { return expectedSourceBoundsOnScreen.width() / (float) bitmapWidth; }
    float scaleY() { return expectedSourceBoundsOnScreen.height() / (float) bitmapHeight; }
}
