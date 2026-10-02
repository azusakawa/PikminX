package com.pikminx.helper;

/** Confirms a target, then waits for it to disappear before accepting the next item. */
final class ReturnRewardScanGuard {
    enum Decision {
        WAIT,
        POSTCARD,
        TARGET_CONFIRMED,
        DETAIL_RECOVERY,
        RECOVERY_COMPLETE,
        RECOVERY_FAILED,
        SQUAD_COMPLETE,
        COMPLETE
    }

    private static final int REQUIRED_TARGET_SCREENS = 2;
    private static final int REQUIRED_SQUAD_CLOSEUP_SCREENS = 2;
    private static final int REQUIRED_CLEAR_SCREENS = 2;
    private static final int REQUIRED_EMPTY_SCREENS = 6;
    private static final int MAX_DETAIL_RECOVERY_ATTEMPTS = 3;
    private static final int REQUIRED_RECOVERY_SCREENS = 2;
    private static final int MAX_RECOVERY_SCREENS = 6;
    private ReturnRewardDetector.Target pending;
    private int targetScreens;
    private int targetClearScreens;
    private int emptyScreens;
    private int squadCloseupScreens;
    private int detailRecoveryAttempts;
    private int recoveryScreens;
    private int stableRecoveryScreens;
    private boolean awaitingTargetClear;
    private boolean recoveringDetail;

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight) {
        return observe(page, target, screenWidth, screenHeight, false, false, false);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean pikminDetailOpen,
            boolean squadCloseup,
            boolean stableReturnScene) {
        if (page == PostcardMatcher.Page.POSTCARD_RECEIVED) {
            reset();
            return Decision.POSTCARD;
        }
        if (pikminDetailOpen) {
            clearNormalCandidates();
            squadCloseupScreens = 0;
            if (!awaitingTargetClear && !recoveringDetail) {
                return Decision.WAIT;
            }
            if (!recoveringDetail) {
                recoveringDetail = true;
                awaitingTargetClear = false;
                targetClearScreens = 0;
                detailRecoveryAttempts = 0;
                recoveryScreens = 0;
            }
            stableRecoveryScreens = 0;
            if (detailRecoveryAttempts >= MAX_DETAIL_RECOVERY_ATTEMPTS) {
                return Decision.RECOVERY_FAILED;
            }
            detailRecoveryAttempts++;
            return Decision.DETAIL_RECOVERY;
        }
        if (recoveringDetail) {
            clearNormalCandidates();
            squadCloseupScreens = 0;
            recoveryScreens++;
            stableRecoveryScreens = stableReturnScene ? stableRecoveryScreens + 1 : 0;
            if (stableRecoveryScreens >= REQUIRED_RECOVERY_SCREENS) {
                resetRecovery();
                return Decision.RECOVERY_COMPLETE;
            }
            return recoveryScreens >= MAX_RECOVERY_SCREENS
                    ? Decision.RECOVERY_FAILED : Decision.WAIT;
        }
        if (awaitingTargetClear) {
            clearNormalCandidates();
            squadCloseupScreens = 0;
            if (target == null) {
                if (++targetClearScreens >= REQUIRED_CLEAR_SCREENS) {
                    targetClearScreens = 0;
                    awaitingTargetClear = false;
                }
            } else {
                targetClearScreens = 0;
            }
            return Decision.WAIT;
        }
        if (squadCloseup) {
            clearNormalCandidates();
            return ++squadCloseupScreens >= REQUIRED_SQUAD_CLOSEUP_SCREENS
                    ? Decision.SQUAD_COMPLETE : Decision.WAIT;
        }
        squadCloseupScreens = 0;
        if (target == null) {
            pending = null;
            targetScreens = 0;
            if (page != PostcardMatcher.Page.MAP && !stableReturnScene) {
                emptyScreens = 0;
                return Decision.WAIT;
            }
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
        targetClearScreens = 0;
        awaitingTargetClear = true;
        return Decision.TARGET_CONFIRMED;
    }

    private void clearNormalCandidates() {
        pending = null;
        targetScreens = 0;
        emptyScreens = 0;
    }

    private void resetRecovery() {
        detailRecoveryAttempts = 0;
        recoveryScreens = 0;
        stableRecoveryScreens = 0;
        recoveringDetail = false;
    }

    void reset() {
        clearNormalCandidates();
        squadCloseupScreens = 0;
        targetClearScreens = 0;
        awaitingTargetClear = false;
        resetRecovery();
    }
}
