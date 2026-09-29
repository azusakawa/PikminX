package com.pikminx.helper;

/** Keeps overlay removal independent from View attachment timing. */
final class OverlayWindowPolicy {
    private OverlayWindowPolicy() {}

    static boolean shouldAttemptRemoval(boolean hasView, boolean hasWindowManager) {
        return hasView && hasWindowManager;
    }

    static boolean shouldDismissNumberKeyboard(
            boolean actionDown,
            boolean numberFieldFocused,
            boolean touchedNumberField) {
        return actionDown && numberFieldFocused && !touchedNumberField;
    }

    static int horizontalNoticeX(
            int iconX,
            int iconSize,
            int noticeWidth,
            int screenWidth,
            int gap,
            int edge) {
        int right = iconX + iconSize + gap;
        return right + noticeWidth <= screenWidth - edge
                ? Math.max(edge, right)
                : Math.max(edge, iconX - gap - noticeWidth);
    }

    static int centeredNoticeY(
            int iconY,
            int iconSize,
            int noticeHeight,
            int screenHeight,
            int edge) {
        int centered = iconY + (iconSize - noticeHeight) / 2;
        return Math.max(edge, Math.min(centered, screenHeight - noticeHeight - edge));
    }
}
