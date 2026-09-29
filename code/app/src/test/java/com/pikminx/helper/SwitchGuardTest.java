package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 驗證切花冷卻與連續確認保護，避免 OCR 誤判重複點擊。 */
public final class SwitchGuardTest {
    @Test
    public void requiresTwoConsecutiveLowReadings() {
        SwitchGuard guard = new SwitchGuard();
        assertFalse(guard.shouldSwitch(49, 50, 1_000));
        assertEquals(1, guard.confirmations());
        assertTrue(guard.shouldSwitch(48, 50, 6_000));
    }

    @Test
    public void switchesOnlyWhenRemainingIsStrictlyBelowThreshold() {
        assertTrue(SwitchGuard.isBelowThreshold(1_199, 1_200));
        assertFalse(SwitchGuard.isBelowThreshold(1_200, 1_200));
        assertFalse(SwitchGuard.isBelowThreshold(1_201, 1_200));
    }

    @Test
    public void blocksSwitchesForThirtySecondsAfterSuccess() {
        SwitchGuard guard = new SwitchGuard();
        guard.shouldSwitch(49, 50, 1_000);
        guard.shouldSwitch(48, 50, 6_000);
        guard.markSwitched(6_000);

        assertEquals(25_000, guard.cooldownRemainingMillis(11_000));
        assertFalse(guard.shouldSwitch(1, 50, 11_000));
        assertFalse(guard.shouldSwitch(1, 50, 36_001));
        assertTrue(guard.shouldSwitch(1, 50, 41_001));
    }

    @Test
    public void commitsRequestedFlowerOnlyAfterVisualConfirmation() {
        SwitchGuard guard = new SwitchGuard();
        guard.requestSwitch("紅色花瓣");

        assertEquals("紅色花瓣", guard.pendingFlower());
        assertFalse(guard.confirmSwitch("黃色花瓣", 1_000));
        assertEquals(0, guard.cooldownRemainingMillis(1_000));
        assertTrue(guard.confirmSwitch("紅色花瓣", 2_000));
        assertEquals("", guard.pendingFlower());
        assertEquals(30_000, guard.cooldownRemainingMillis(2_000));
    }
}
