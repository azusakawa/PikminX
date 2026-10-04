package com.pikminx.helper;

/** One user-anchored screen region that gates return-reward detection for a run. */
final class ReturnRewardRoi {
    private static final float WIDTH_RATIO = 0.52f;
    private static final float HEIGHT_RATIO = 0.24f;
    private static final float DEFAULT_LEFT_RATIO = 0.18f;
    private static final float DEFAULT_TOP_RATIO = 0.52f;
    private static final float DEFAULT_RIGHT_RATIO = 0.82f;
    private static final float DEFAULT_BOTTOM_RATIO = 0.70f;

    private CaptureGeometry.Bounds screenBounds;
    private CaptureGeometry.Bounds armedGameBounds;

    boolean armFromScreenTap(
            int screenX, int screenY, CaptureGeometry.Bounds gameBounds) {
        if (screenBounds != null
                || gameBounds == null
                || screenX < gameBounds.left()
                || screenX >= gameBounds.right()
                || screenY < gameBounds.top()
                || screenY >= gameBounds.bottom()) {
            return false;
        }
        int width = Math.max(1, Math.round(gameBounds.width() * WIDTH_RATIO));
        int height = Math.max(1, Math.round(gameBounds.height() * HEIGHT_RATIO));
        int left = clamp(
                screenX - width / 2,
                gameBounds.left(),
                gameBounds.right() - width);
        int top = clamp(
                screenY - height / 2,
                gameBounds.top(),
                gameBounds.bottom() - height);
        screenBounds = new CaptureGeometry.Bounds(left, top, left + width, top + height);
        armedGameBounds = gameBounds;
        return true;
    }

    boolean armFromGameBounds(CaptureGeometry.Bounds gameBounds) {
        if (screenBounds != null || gameBounds == null) {
            return false;
        }
        int width = gameBounds.width();
        int height = gameBounds.height();
        screenBounds = new CaptureGeometry.Bounds(
                gameBounds.left() + Math.round(width * DEFAULT_LEFT_RATIO),
                gameBounds.top() + Math.round(height * DEFAULT_TOP_RATIO),
                gameBounds.left() + Math.round(width * DEFAULT_RIGHT_RATIO),
                gameBounds.top() + Math.round(height * DEFAULT_BOTTOM_RATIO));
        armedGameBounds = gameBounds;
        return true;
    }

    boolean isArmed() {
        return screenBounds != null;
    }

    CaptureGeometry.Bounds screenBounds() {
        return screenBounds;
    }

    boolean matchesGameBounds(CaptureGeometry.Bounds gameBounds) {
        return armedGameBounds != null && armedGameBounds.equals(gameBounds);
    }

    ReturnRewardDetector.Region detectorRegion(CaptureGeometry geometry) {
        if (screenBounds == null || geometry == null) {
            return null;
        }
        CaptureGeometry.Bounds source = geometry.expectedSourceBoundsOnScreen();
        CaptureGeometry.Bounds effective = screenBounds.intersection(source);
        CaptureGeometry.Bounds target = geometry.targetWindowBoundsOnScreen();
        if (effective != null && target != null) {
            effective = effective.intersection(target);
        }
        if (effective == null) {
            return null;
        }
        int left = toBitmapX(effective.left(), source, geometry);
        int top = toBitmapY(effective.top(), source, geometry);
        int right = toBitmapX(effective.right(), source, geometry);
        int bottom = toBitmapY(effective.bottom(), source, geometry);
        return right > left && bottom > top
                ? new ReturnRewardDetector.Region(left, top, right, bottom)
                : null;
    }

    ScreenCoordinateTransform.ScreenshotRect postcardOcrRegion(
            CaptureGeometry geometry) {
        ReturnRewardDetector.Region reward = detectorRegion(geometry);
        ScreenCoordinateTransform.ScreenshotRect target = geometry == null
                ? null : ScreenCoordinateTransform.targetWindowInScreenshot(geometry);
        if (reward == null || target == null) {
            return null;
        }
        int left = Math.max(reward.left(), target.left());
        int right = Math.min(reward.right(), target.right());
        int top = Math.max(reward.top(), target.top());
        int extension = Math.max(1, (reward.bottom() - reward.top()) / 2);
        int bottom = Math.min(target.bottom(), reward.bottom() + extension);
        return right > left && bottom > top
                ? new ScreenCoordinateTransform.ScreenshotRect(left, top, right, bottom)
                : null;
    }

    void reset() {
        screenBounds = null;
        armedGameBounds = null;
    }

    private static int toBitmapX(
            int screenX, CaptureGeometry.Bounds source, CaptureGeometry geometry) {
        return clamp(
                Math.round((screenX - source.left()) / geometry.scaleX()),
                0,
                geometry.bitmapWidth());
    }

    private static int toBitmapY(
            int screenY, CaptureGeometry.Bounds source, CaptureGeometry geometry) {
        return clamp(
                Math.round((screenY - source.top()) / geometry.scaleY()),
                0,
                geometry.bitmapHeight());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
