package com.pikminx.helper;

/** One user-anchored screen region that gates return-reward detection for a run. */
final class ReturnRewardRoi {
    private static final float WIDTH_RATIO = 0.52f;
    private static final float HEIGHT_RATIO = 0.24f;

    private CaptureGeometry.Bounds screenBounds;

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
        return true;
    }

    boolean isArmed() {
        return screenBounds != null;
    }

    CaptureGeometry.Bounds screenBounds() {
        return screenBounds;
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

    void reset() {
        screenBounds = null;
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
