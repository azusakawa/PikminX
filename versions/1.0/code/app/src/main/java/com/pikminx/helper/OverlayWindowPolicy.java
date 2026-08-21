package com.pikminx.helper;

/** Keeps overlay removal independent from View attachment timing. */
final class OverlayWindowPolicy {
    private OverlayWindowPolicy() {}

    static boolean shouldAttemptRemoval(boolean hasView, boolean hasWindowManager) {
        return hasView && hasWindowManager;
    }
}
