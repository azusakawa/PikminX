package com.pikminx.helper;

/** Confirms a target, then waits for it to disappear before accepting the next item. */
final class ReturnRewardScanGuard {
    enum Decision {
        WAIT,
        POSTCARD,
        TARGET_CONFIRMED,
        TARGET_AMBIGUOUS,
        TARGET_RETRY_EXHAUSTED,
        SQUAD_COMPLETE,
        COMPLETE
    }

    private static final int REQUIRED_TARGET_SCREENS = 2;
    private static final int REQUIRED_SQUAD_CLOSEUP_SCREENS = 2;
    private static final int REQUIRED_CLEAR_SCREENS = 2;
    private static final int REQUIRED_EMPTY_SCREENS = 6;
    // Preserve the original five-second completion horizon if callbacks arrive unusually fast.
    private static final long REQUIRED_COMPLETION_DWELL_MILLIS = 5_000L;
    private ReturnRewardDetector.Target pending;
    private int targetScreens;
    private int targetClearScreens;
    private int emptyScreens;
    private long emptyStartedMillis = -1L;
    private int squadCloseupScreens;
    private long squadCloseupStartedMillis = -1L;
    private boolean awaitingTargetClear;
    private boolean persistentTargetRetried;
    private int ambiguousSquadTargetScreens;

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
        return observe(
                page,
                target,
                screenWidth,
                screenHeight,
                allowPersistentTarget,
                false);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget,
            boolean squadCloseup) {
        return observe(
                page,
                target,
                screenWidth,
                screenHeight,
                allowPersistentTarget,
                squadCloseup,
                false);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget,
            boolean squadCloseup,
            boolean stableReturnScene) {
        return observe(
                page,
                target,
                screenWidth,
                screenHeight,
                allowPersistentTarget,
                squadCloseup,
                stableReturnScene,
                false,
                System.nanoTime() / 1_000_000L);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget,
            boolean squadCloseup,
            boolean stableReturnScene,
            boolean ambiguousSquadTarget) {
        return observe(
                page,
                target,
                screenWidth,
                screenHeight,
                allowPersistentTarget,
                squadCloseup,
                stableReturnScene,
                ambiguousSquadTarget,
                System.nanoTime() / 1_000_000L);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget,
            boolean squadCloseup,
            boolean stableReturnScene,
            long nowMillis) {
        return observe(
                page,
                target,
                screenWidth,
                screenHeight,
                allowPersistentTarget,
                squadCloseup,
                stableReturnScene,
                false,
                nowMillis);
    }

    Decision observe(
            PostcardMatcher.Page page,
            ReturnRewardDetector.Target target,
            int screenWidth,
            int screenHeight,
            boolean allowPersistentTarget,
            boolean squadCloseup,
            boolean stableReturnScene,
            boolean ambiguousSquadTarget,
            long nowMillis) {
        if (page == PostcardMatcher.Page.POSTCARD_RECEIVED) {
            reset();
            return Decision.POSTCARD;
        }
        if (ambiguousSquadTarget) {
            pending = null;
            targetScreens = 0;
            targetClearScreens = 0;
            resetEmptyEvidence();
            resetSquadCloseupEvidence();
            return ++ambiguousSquadTargetScreens >= REQUIRED_TARGET_SCREENS
                    ? Decision.TARGET_AMBIGUOUS : Decision.WAIT;
        }
        ambiguousSquadTargetScreens = 0;
        if (squadCloseup) {
            pending = null;
            targetScreens = 0;
            resetEmptyEvidence();
            if (!stableReturnScene) {
                targetClearScreens = 0;
                resetSquadCloseupEvidence();
                return Decision.WAIT;
            }
            if (awaitingTargetClear) {
                resetSquadCloseupEvidence();
                if (++targetClearScreens >= REQUIRED_CLEAR_SCREENS) {
                    targetClearScreens = 0;
                    awaitingTargetClear = false;
                    persistentTargetRetried = false;
                }
                return Decision.WAIT;
            }
            targetClearScreens = 0;
            if (squadCloseupScreens == 0) {
                squadCloseupStartedMillis = nowMillis;
            }
            return ++squadCloseupScreens >= REQUIRED_SQUAD_CLOSEUP_SCREENS
                    && nowMillis - squadCloseupStartedMillis
                            >= REQUIRED_COMPLETION_DWELL_MILLIS
                    ? Decision.SQUAD_COMPLETE : Decision.WAIT;
        }
        resetSquadCloseupEvidence();
        if (target == null) {
            if (!stableReturnScene) {
                resetEmptyEvidence();
                return Decision.WAIT;
            }
            pending = null;
            targetScreens = 0;
            if (awaitingTargetClear) {
                if (++targetClearScreens >= REQUIRED_CLEAR_SCREENS) {
                    targetClearScreens = 0;
                    resetEmptyEvidence();
                    awaitingTargetClear = false;
                    persistentTargetRetried = false;
                }
                return Decision.WAIT;
            }
            persistentTargetRetried = false;
            if (emptyScreens == 0) {
                emptyStartedMillis = nowMillis;
            }
            return ++emptyScreens >= REQUIRED_EMPTY_SCREENS
                    && nowMillis - emptyStartedMillis >= REQUIRED_COMPLETION_DWELL_MILLIS
                    ? Decision.COMPLETE : Decision.WAIT;
        }
        resetEmptyEvidence();
        if (awaitingTargetClear) {
            targetClearScreens = 0;
            if (!allowPersistentTarget) {
                return Decision.WAIT;
            }
            if (persistentTargetRetried) {
                return Decision.TARGET_RETRY_EXHAUSTED;
            }
            persistentTargetRetried = true;
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

    private void resetEmptyEvidence() {
        emptyScreens = 0;
        emptyStartedMillis = -1L;
    }

    private void resetSquadCloseupEvidence() {
        squadCloseupScreens = 0;
        squadCloseupStartedMillis = -1L;
    }

    void reset() {
        pending = null;
        targetScreens = 0;
        targetClearScreens = 0;
        resetEmptyEvidence();
        resetSquadCloseupEvidence();
        awaitingTargetClear = false;
        persistentTargetRetried = false;
        ambiguousSquadTargetScreens = 0;
    }
}
