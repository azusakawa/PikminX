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
    public void pikminDetailCompletesAfterTwoFramesInsteadOfConfirmingDecoration() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target decoration = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT,
                        false,
                        true));
        assertEquals(ReturnRewardScanGuard.Decision.SQUAD_COMPLETE,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        decoration,
                        WIDTH,
                        HEIGHT,
                        false,
                        true));
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
    public void persistentTargetCanBeReconfirmedAfterAnimationSettle() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardDetector.Target sameCenter = target(214, 525);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(PostcardMatcher.Page.UNKNOWN, sameCenter, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        sameCenter,
                        WIDTH,
                        HEIGHT,
                        true));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(
                        PostcardMatcher.Page.UNKNOWN,
                        sameCenter,
                        WIDTH,
                        HEIGHT,
                        true));
    }

    private static ReturnRewardDetector.Target target(int x, int y) {
        return new ReturnRewardDetector.Target(x, y, 110, 130, 0.8f);
    }
}
