package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PetalAccessibilityServiceOverlayTest {
    @Test
    public void pendingOverlayIsRemovedBeforeItsReplacement() {
        assertTrue(OverlayWindowPolicy.shouldAttemptRemoval(true, true));
        assertFalse(OverlayWindowPolicy.shouldAttemptRemoval(false, true));
        assertFalse(OverlayWindowPolicy.shouldAttemptRemoval(true, false));
    }
}
