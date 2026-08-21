package com.pikminx.helper;

/** Requires two matching targets before a tap and six empty screens before completion. */
final class ReturnRewardScanGuard {
    enum Decision {
        WAIT,
        TARGET_CONFIRMED,
        COMPLETE
    }

    private static final int REQUIRED_TARGET_SCREENS = 2;
    private static final int REQUIRED_EMPTY_SCREENS = 6;
    private ReturnRewardDetector.Target pending;
    private int targetScreens;
    private int emptyScreens;

    Decision observe(
            ReturnRewardDetector.Target target, int screenWidth, int screenHeight) {
        if (target == null) {
            pending = null;
            targetScreens = 0;
            return ++emptyScreens >= REQUIRED_EMPTY_SCREENS
                    ? Decision.COMPLETE : Decision.WAIT;
        }
        emptyScreens = 0;
        if (target.samePosition(pending, screenWidth, screenHeight)) {
            targetScreens++;
        } else {
            pending = target;
            targetScreens = 1;
        }
        if (targetScreens < REQUIRED_TARGET_SCREENS) {
            return Decision.WAIT;
        }
        pending = null;
        targetScreens = 0;
        return Decision.TARGET_CONFIRMED;
    }

    void reset() {
        pending = null;
        targetScreens = 0;
        emptyScreens = 0;
    }
}
