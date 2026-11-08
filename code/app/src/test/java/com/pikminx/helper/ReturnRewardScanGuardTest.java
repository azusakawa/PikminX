package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ReturnRewardScanGuardTest {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 936;

    @Test
    public void confirmsOnlyTwoMatchingTargets() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target first = target(214, 525);
        ReturnRewardDetector.Target same = target(219, 529);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, first));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, same));
    }

    @Test
    public void matchingTargetsConfirmDespiteAnimatedBackground() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target target = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                animatedTarget(guard, target, 1_000L));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                animatedTarget(guard, target, 2_000L));
    }

    @Test
    public void animatedDetectorMissDoesNotDiscardPendingTarget() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target target = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                animatedTarget(guard, target, 1_000L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        false,
                        2_000L));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                animatedTarget(guard, target, 3_000L));
    }

    @Test
    public void animatedDetectorMissDoesNotReleaseCollectedTarget() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target target = target(214, 525);
        animatedTarget(guard, target, 1_000L);
        animatedTarget(guard, target, 2_000L);

        for (long nowMillis = 3_000L; nowMillis <= 4_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(
                            PostcardMatcher.Page.UNKNOWN,
                            null,
                            WIDTH,
                            HEIGHT,
                            false,
                            false,
                            false,
                            nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target));
    }

    @Test
    public void postcardPageWinsEvenWhenPixelsStillLookLikeAReward() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.POSTCARD,
                guard.observe(
                        PostcardMatcher.Page.POSTCARD_RECEIVED,
                        target(214, 525),
                        WIDTH,
                        HEIGHT));
    }

    @Test
    public void pikminDetailWaitsForConfirmedRewardToClearBeforeCompleting() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, reward));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, reward));

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                squadAt(guard, null, 1_000L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                squadAt(guard, null, 2_000L));
        for (long nowMillis = 3_000L; nowMillis <= 7_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    squadAt(guard, null, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.SQUAD_COMPLETE,
                squadAt(guard, null, 8_000L));
    }

    @Test
    public void postcardStillWinsOverPikminDetailEvidence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.POSTCARD,
                guard.observe(
                        PostcardMatcher.Page.POSTCARD_RECEIVED,
                        target(214, 525),
                        WIDTH,
                        HEIGHT,
                        false,
                        true));
    }

    @Test
    public void postcardEvidenceAllowsSubsequentAllClear() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.POSTCARD,
                guard.observe(
                        PostcardMatcher.Page.POSTCARD_RECEIVED,
                        null,
                        WIDTH,
                        HEIGHT));
        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                emptyAt(guard, PostcardMatcher.Page.MAP, 6_000L));
    }

    @Test
    public void completesOnlyAfterSixEmptyScreensAcrossFiveSecondDwell() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                emptyAt(guard, PostcardMatcher.Page.MAP, 6_000L));
    }

    @Test
    public void rapidEmptyFramesBeforeAnyRewardCannotComplete() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        for (long nowMillis = 1_000L; nowMillis <= 2_100L; nowMillis += 100L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis));
        }
    }

    @Test
    public void rapidEmptyFramesAfterConfirmedRewardCannotComplete() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);
        stableTarget(guard, reward);
        stableTarget(guard, reward);
        clearConfirmedTarget(guard);

        for (int index = 0; index < 6; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
        }
    }

    @Test
    public void unstableMapFramesCannotCompleteAfterConfirmedReward() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);
        stableTarget(guard, reward);
        stableTarget(guard, reward);
        clearConfirmedTarget(guard);

        for (long nowMillis = 1_000L; nowMillis <= 12_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(
                            PostcardMatcher.Page.MAP,
                            null,
                            WIDTH,
                            HEIGHT,
                            false,
                            false,
                            false,
                            nowMillis));
        }
    }

    @Test
    public void rapidSquadCloseupFramesCannotComplete() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        for (long nowMillis = 1_000L; nowMillis <= 1_500L; nowMillis += 100L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    squadAt(guard, null, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.SQUAD_COMPLETE,
                squadAt(guard, null, 6_000L));
    }

    @Test
    public void visualSquadCloseupDoesNotConfirmTargetDecoration() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target decoration = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                squadAt(guard, decoration, 1_000L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                squadAt(guard, decoration, 2_000L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        true,
                        3_000L));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        true,
                        4_000L));
    }

    @Test
    public void completesAfterStableReturnSceneIsEmptyWithoutMapPage() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);
        stableTarget(guard, reward);
        stableTarget(guard, reward);
        clearConfirmedTarget(guard);

        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.UNKNOWN, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                emptyAt(guard, PostcardMatcher.Page.UNKNOWN, 6_000L));
    }

    @Test
    public void unknownScreensNeverComplete() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        for (int index = 0; index < 12; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        }
    }

    @Test
    public void unknownScreenBreaksConsecutiveMapEmptySequence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);
        stableTarget(guard, reward);
        stableTarget(guard, reward);
        clearConfirmedTarget(guard);

        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.MAP, 6_000L));
    }

    @Test
    public void visibleTargetResetsTheEmptySequence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target reward = target(214, 525);
        stableTarget(guard, reward);
        stableTarget(guard, reward);
        clearConfirmedTarget(guard);
        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis);
        }
        stableTarget(guard, target(214, 525));

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.MAP, 6_000L));
    }

    @Test
    public void waitsForCollectedTargetToDisappearBeforeConfirmingTheNextItem() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(214, 525)));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, target(218, 529)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(210, 520)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(214, 524)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.UNKNOWN, 0L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.UNKNOWN, 0L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(250, 555)));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, target(254, 559)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.MAP, 0L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.MAP, 0L));
        for (long nowMillis = 1_000L; nowMillis <= 5_000L; nowMillis += 1_000L) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    emptyAt(guard, PostcardMatcher.Page.MAP, nowMillis));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                emptyAt(guard, PostcardMatcher.Page.MAP, 6_000L));
    }

    @Test
    public void singleEmptyFrameDoesNotReleaseCollectedTarget() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        stableTarget(guard, target(214, 525));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, target(218, 529)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(214, 525)));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target(218, 529)));
    }

    @Test
    public void persistentTargetCanBeRetriedOnceThenStops() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target sameCenter = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, sameCenter));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, sameCenter));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, sameCenter));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, sameCenter, true));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                stableTarget(guard, sameCenter, true));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_RETRY_EXHAUSTED,
                stableTarget(guard, sameCenter, true));
    }

    @Test
    public void clearedTargetResetsPersistentRetryBudget() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target first = target(214, 525);
        ReturnRewardDetector.Target next = target(250, 555);

        stableTarget(guard, first);
        stableTarget(guard, first);
        stableTarget(guard, first, true);
        stableTarget(guard, first, true);
        clearConfirmedTarget(guard);

        stableTarget(guard, next);
        stableTarget(guard, next);
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, next, true));
    }

    @Test
    public void ambiguousSquadTargetRequiresTwoConsecutiveScreens() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target target = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                ambiguousSquadTarget(guard, target));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                stableTarget(guard, target));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                ambiguousSquadTarget(guard, target));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_AMBIGUOUS,
                ambiguousSquadTarget(guard, target));
    }

    private static ReturnRewardDetector.Target target(int x, int y) {
        return new ReturnRewardDetector.Target(x, y, 110, 130, 0.8f);
    }

    private static ReturnRewardScanGuard.Decision stableTarget(
            ReturnRewardScanGuard guard, ReturnRewardDetector.Target target) {
        return stableTarget(guard, target, false);
    }

    private static ReturnRewardScanGuard.Decision stableTarget(
            ReturnRewardScanGuard guard,
            ReturnRewardDetector.Target target,
            boolean allowPersistentTarget) {
        return guard.observe(
                PostcardMatcher.Page.UNKNOWN,
                target,
                WIDTH,
                HEIGHT,
                allowPersistentTarget,
                false,
                true);
    }

    private static ReturnRewardScanGuard.Decision animatedTarget(
            ReturnRewardScanGuard guard,
            ReturnRewardDetector.Target target,
            long nowMillis) {
        return guard.observe(
                PostcardMatcher.Page.UNKNOWN,
                target,
                WIDTH,
                HEIGHT,
                false,
                false,
                false,
                nowMillis);
    }

    private static ReturnRewardScanGuard.Decision ambiguousSquadTarget(
            ReturnRewardScanGuard guard,
            ReturnRewardDetector.Target target) {
        return guard.observe(
                PostcardMatcher.Page.UNKNOWN,
                target,
                WIDTH,
                HEIGHT,
                false,
                false,
                false,
                true);
    }

    private static void clearConfirmedTarget(ReturnRewardScanGuard guard) {
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.UNKNOWN, 0L));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                emptyAt(guard, PostcardMatcher.Page.UNKNOWN, 0L));
    }

    private static ReturnRewardScanGuard.Decision emptyAt(
            ReturnRewardScanGuard guard, PostcardMatcher.Page page, long nowMillis) {
        return guard.observe(
                page,
                null,
                WIDTH,
                HEIGHT,
                false,
                false,
                true,
                nowMillis);
    }

    private static ReturnRewardScanGuard.Decision squadAt(
            ReturnRewardScanGuard guard,
            ReturnRewardDetector.Target target,
            long nowMillis) {
        return guard.observe(
                PostcardMatcher.Page.UNKNOWN,
                target,
                WIDTH,
                HEIGHT,
                false,
                true,
                true,
                nowMillis);
    }
}
