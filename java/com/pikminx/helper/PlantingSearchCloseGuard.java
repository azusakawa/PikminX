package com.pikminx.helper;

/** Search-page closure never authorizes a system Back action. */
final class PlantingSearchCloseGuard {
    enum Action { WAIT, TAP_CLOSE, COMPLETE, FAIL }
    private final int maximumFrames;
    private int frames;
    private int closedFrames;
    private boolean closeTapped;

    PlantingSearchCloseGuard(int maximumFrames) {
        this.maximumFrames = maximumFrames;
    }

    Action observe(boolean gameForeground, boolean keyboardVisible,
            boolean closeVisible, boolean menuWithSearchButton) {
        if (!gameForeground || ++frames > maximumFrames) {
            return Action.FAIL;
        }
        if (keyboardVisible) {
            closedFrames = 0;
            return Action.WAIT;
        }
        if (closeVisible) {
            closedFrames = 0;
            // X may only clear text on the first tap; wait a frame before a bounded retry.
            if (closeTapped) {
                closeTapped = false;
                return Action.WAIT;
            }
            closeTapped = true;
            return Action.TAP_CLOSE;
        }
        if (!menuWithSearchButton) {
            closedFrames = 0;
            return Action.WAIT;
        }
        return ++closedFrames >= 2 ? Action.COMPLETE : Action.WAIT;
    }

    void reset() {
        frames = 0;
        closedFrames = 0;
        closeTapped = false;
    }
}
