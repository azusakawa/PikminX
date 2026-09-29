package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PetalAccessibilityServiceOverlayTest {
    @Test
    public void pendingOverlayIsRemovedBeforeItsReplacement() {
        assertTrue(OverlayWindowPolicy.shouldAttemptRemoval(true, true));
        assertFalse(OverlayWindowPolicy.shouldAttemptRemoval(false, true));
        assertFalse(OverlayWindowPolicy.shouldAttemptRemoval(true, false));
    }

    @Test
    public void delayedStartFromAnOlderGenerationCannotRun() {
        assertTrue(AutomationStartGuard.isCurrent(8L, 8L));
        assertFalse(AutomationStartGuard.isCurrent(8L, 9L));
    }

    @Test
    public void numberKeyboardDismissesOnlyForOutsideTouchWithNumberFocus() {
        assertTrue(OverlayWindowPolicy.shouldDismissNumberKeyboard(true, true, false));
        assertFalse(OverlayWindowPolicy.shouldDismissNumberKeyboard(false, true, false));
        assertFalse(OverlayWindowPolicy.shouldDismissNumberKeyboard(true, false, false));
        assertFalse(OverlayWindowPolicy.shouldDismissNumberKeyboard(true, true, true));
    }

    @Test
    public void noticeStaysBesideAndCenteredOnIcon() {
        assertEquals(62, OverlayWindowPolicy.horizontalNoticeX(
                8, 50, 190, 432, 4, 8));
        assertEquals(198, OverlayWindowPolicy.horizontalNoticeX(
                392, 50, 190, 432, 4, 8));
        assertEquals(105, OverlayWindowPolicy.centeredNoticeY(
                100, 50, 40, 900, 8));
        assertEquals(852, OverlayWindowPolicy.centeredNoticeY(
                890, 50, 40, 900, 8));
    }
}
