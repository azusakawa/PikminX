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
                guard.observe(PostcardMatcher.Page.UNKNOWN, first, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(PostcardMatcher.Page.UNKNOWN, same, WIDTH, HEIGHT));
    }

    @Test
    public void sameCenterWithDifferentGeometryRestartsConfirmation() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target narrow = target(214, 525, 60, 150);
        ReturnRewardDetector.Target fruit = target(214, 525, 120, 100);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, narrow, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, fruit, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(PostcardMatcher.Page.UNKNOWN, fruit, WIDTH, HEIGHT));
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
    public void falseTargetThenPikminDetailRecoversBeforeFreshRescan() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target decoration = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.DETAIL_RECOVERY,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        true,
                        true,
                        false));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        true));
        assertEquals(ReturnRewardScanGuard.Decision.RECOVERY_COMPLETE,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        true));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(250, 555), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(254, 559), WIDTH, HEIGHT));
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
                        true,
                        false));
    }

    @Test
    public void visualSquadCloseupWithoutPikminDetailStillCompletes() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        true,
                        false));
        assertEquals(ReturnRewardScanGuard.Decision.SQUAD_COMPLETE,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        true,
                        false));
    }

    @Test
    public void completesOnlyAfterSixConsecutiveEmptyScreens() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        for (int index = 0; index < 5; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
    }

    @Test
    public void completesAfterStableReturnSceneIsEmptyWithoutMapPage() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        for (int index = 0; index < 5; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(
                            PostcardMatcher.Page.UNKNOWN,
                            null,
                            WIDTH,
                            HEIGHT,
                            false,
                            false,
                            true));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        false,
                        false,
                        true));
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

        for (int index = 0; index < 5; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
        }
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
    }

    @Test
    public void visibleTargetResetsTheEmptySequence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        for (int index = 0; index < 5; index++) {
            guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT);
        }
        guard.observe(PostcardMatcher.Page.UNKNOWN, target(214, 525), WIDTH, HEIGHT);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
    }

    @Test
    public void waitsForCollectedTargetToDisappearBeforeConfirmingTheNextItem() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(214, 525), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(218, 529), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(210, 520), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(214, 524), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(250, 555), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(254, 559), WIDTH, HEIGHT));
        for (int index = 0; index < 7; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                guard.observe(PostcardMatcher.Page.MAP, null, WIDTH, HEIGHT));
    }

    @Test
    public void singleEmptyFrameDoesNotReleaseCollectedTarget() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        guard.observe(PostcardMatcher.Page.UNKNOWN, target(214, 525), WIDTH, HEIGHT);
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(218, 529), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, null, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(214, 525), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN, target(218, 529), WIDTH, HEIGHT));
    }

    @Test
    public void persistentTargetCannotRearmWithoutDisappearing() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target sameCenter = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        for (int index = 0; index < 10; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(
                            PostcardMatcher.Page.UNKNOWN,
                            sameCenter,
                            WIDTH,
                            HEIGHT));
        }
    }

    @Test
    public void detailRecoveryBackRequestsAreBounded() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target falseTarget = target(214, 525);
        guard.observe(PostcardMatcher.Page.UNKNOWN, falseTarget, WIDTH, HEIGHT);
        guard.observe(PostcardMatcher.Page.UNKNOWN, falseTarget, WIDTH, HEIGHT);

        for (int index = 0; index < 3; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.DETAIL_RECOVERY,
                    guard.observe(
                            PostcardMatcher.Page.UNKNOWN,
                            null,
                            WIDTH,
                            HEIGHT,
                            true,
                            true,
                            false));
        }
        assertEquals(ReturnRewardScanGuard.Decision.RECOVERY_FAILED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        null,
                        WIDTH,
                        HEIGHT,
                        true,
                        true,
                        false));
    }

    private static ReturnRewardDetector.Target target(int x, int y) {
        return target(x, y, 110, 130);
    }

    private static ReturnRewardDetector.Target target(
            int x, int y, int width, int height) {
        return new ReturnRewardDetector.Target(x, y, width, height, 0.8f);
    }
}
