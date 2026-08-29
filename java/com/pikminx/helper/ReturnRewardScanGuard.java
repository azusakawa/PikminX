package com.pikminx.helper;

/** Confirms a target, then waits for it to disappear before accepting the next item. */
final class ReturnRewardScanGuard {
    enum Decision {
        WAIT,
        POSTCARD,
        TARGET_CONFIRMED,
        COMPLETE
    }

    private static final int REQUIRED_TARGET_SCREENS = 2;
    private static final int REQUIRED_CLEAR_SCREENS = 2;
    private static final int REQUIRED_EMPTY_SCREENS = 6;
    private ReturnRewardDetector.Target pending;
    private int targetScreens;
    private int targetClearScreens;
    private int emptyScreens;
    private boolean awaitingTargetClear;

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight) {
        return observe(page, target, screenWidth, screenHeight, false);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget) {
        if (page == PostcardMatcher.Page.POSTCARD_RECEIVED) {
            reset();
            return Decision.POSTCARD;
        }
        if (target == null) {
            pending = null;
            targetScreens = 0;
            if (awaitingTargetClear) {
                if (++targetClearScreens >= REQUIRED_CLEAR_SCREENS) {
                    targetClearScreens = 0;
                    emptyScreens = 0;
                    awaitingTargetClear = false;
                }
                return Decision.WAIT;
            }
            if (page != PostcardMatcher.Page.MAP) {
                emptyScreens = 0;
                return Decision.WAIT;
            }
            return ++emptyScreens >= REQUIRED_EMPTY_SCREENS
                    ? Decision.COMPLETE : Decision.WAIT;
        }
        emptyScreens = 0;
        if (awaitingTargetClear) {
            targetClearScreens = 0;
            if (!allowPersistentTarget) {
                return Decision.WAIT;
            }
            awaitingTargetClear = false;
            pending = null;
            targetScreens = 0;
        }
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
        targetClearScreens = 0;
        awaitingTargetClear = true;
        return Decision.TARGET_CONFIRMED;
    }

    void reset() {
        pending = null;
        targetScreens = 0;
        targetClearScreens = 0;
        emptyScreens = 0;
        awaitingTargetClear = false;
    }
}
