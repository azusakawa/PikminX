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
                guard.observe(first, WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(same, WIDTH, HEIGHT));
    }

    @Test
    public void completesOnlyAfterSixConsecutiveEmptyScreens() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        for (int index = 0; index < 5; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(null, WIDTH, HEIGHT));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                guard.observe(null, WIDTH, HEIGHT));
    }

    @Test
    public void visibleTargetResetsTheEmptySequence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        for (int index = 0; index < 5; index++) {
            guard.observe(null, WIDTH, HEIGHT);
        }
        guard.observe(target(214, 525), WIDTH, HEIGHT);

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(null, WIDTH, HEIGHT));
    }

    @Test
    public void rechecksAfterEveryCollectedTargetUntilNoneRemain() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();

        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(target(214, 525), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(target(218, 529), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                guard.observe(target(210, 520), WIDTH, HEIGHT));
        assertEquals(ReturnRewardScanGuard.Decision.TARGET_CONFIRMED,
                guard.observe(target(214, 524), WIDTH, HEIGHT));
        for (int index = 0; index < 5; index++) {
            assertEquals(ReturnRewardScanGuard.Decision.WAIT,
                    guard.observe(null, WIDTH, HEIGHT));
        }
        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE,
                guard.observe(null, WIDTH, HEIGHT));
    }

    private static ReturnRewardDetector.Target target(int x, int y) {
        return new ReturnRewardDetector.Target(x, y, 110, 130, 0.8f);
    }
}
