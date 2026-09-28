package com.pikminx.helper;

/**
 * Immutable mapping assumption for one accessibility screenshot.
 *
 * <p>{@link android.accessibilityservice.AccessibilityService.ScreenshotResult} does not expose
 * the buffer origin or bounds. {@code expectedSourceBoundsOnScreen} is therefore the geometry
 * this app uses to map this bitmap, not API-confirmed screenshot metadata.</p>
 */
public final class CaptureGeometry {
    public enum Mode { WINDOW, DISPLAY }

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Bounds must have positive size");
            }
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }

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
    private final int windowId;
    private final long captureSequence;
    private final long capturedAtUptimeMillis;

    CaptureGeometry(Mode mode, int bitmapWidth, int bitmapHeight,
            Bounds expectedSourceBoundsOnScreen, Bounds targetWindowBoundsOnScreen,
            int displayId, long captureSequence, long capturedAtUptimeMillis) {
        this(
                mode,
                bitmapWidth,
                bitmapHeight,
                expectedSourceBoundsOnScreen,
                targetWindowBoundsOnScreen,
                displayId,
                -1,
                captureSequence,
                capturedAtUptimeMillis);
    }

    CaptureGeometry(Mode mode, int bitmapWidth, int bitmapHeight,
            Bounds expectedSourceBoundsOnScreen, Bounds targetWindowBoundsOnScreen,
            int displayId, int windowId, long captureSequence, long capturedAtUptimeMillis) {
        if (mode == null || expectedSourceBoundsOnScreen == null) {
            throw new IllegalArgumentException("Capture mode and expected bounds are required");
        }
        if (bitmapWidth <= 0
                || bitmapHeight <= 0
                || displayId < 0
                || windowId < -1
                || captureSequence < 1) {
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
        this.windowId = windowId;
        this.captureSequence = captureSequence;
        this.capturedAtUptimeMillis = capturedAtUptimeMillis;
    }

    public Mode mode() { return mode; }
    public int bitmapWidth() { return bitmapWidth; }
    public int bitmapHeight() { return bitmapHeight; }
    public Bounds expectedSourceBoundsOnScreen() { return expectedSourceBoundsOnScreen; }
    public Bounds targetWindowBoundsOnScreen() { return targetWindowBoundsOnScreen; }
    public int displayId() { return displayId; }
    public int windowId() { return windowId; }
    public long captureSequence() { return captureSequence; }
    public long capturedAtUptimeMillis() { return capturedAtUptimeMillis; }
    public float scaleX() { return expectedSourceBoundsOnScreen.width() / (float) bitmapWidth; }
    public float scaleY() { return expectedSourceBoundsOnScreen.height() / (float) bitmapHeight; }

    boolean matchesBitmap(int width, int height) {
        return width == bitmapWidth && height == bitmapHeight;
    }

    boolean isActionSafe(OcrScan.Transform transform) {
        if (transform == null
                || !matchesBitmap(transform.sourceWidth(), transform.sourceHeight())
                || transform.captureSequence() != captureSequence) {
            return false;
        }
        if (mode == Mode.WINDOW && !expectedSourceBoundsOnScreen.equals(targetWindowBoundsOnScreen)) {
            return false;
        }
        if (transform.roiBasis() != OcrScan.RoiBasis.TARGET_WINDOW) {
            return true;
        }
        if (transform.fallbackToScreenshot() || targetWindowBoundsOnScreen == null) {
            return false;
        }
        ScreenCoordinateTransform.ScreenshotRect target =
                ScreenCoordinateTransform.targetWindowInScreenshot(this);
        return target != null
                && transform.basisLeft() == target.left()
                && transform.basisTop() == target.top()
                && transform.basisRight() == target.right()
                && transform.basisBottom() == target.bottom();
    }
}
