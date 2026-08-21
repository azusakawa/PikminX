package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PostcardReturnGuardTest {
    @Test
    public void visibleBlackBubbleRequiresTwoStableFrames() {
        PostcardReturnGuard guard = new PostcardReturnGuard();

        assertEquals(PostcardReturnGuard.Decision.WAIT, guard.observe(true, true));
        assertEquals(PostcardReturnGuard.Decision.OPEN_PREVIOUS_FLOWER,
                guard.observe(true, true));
    }

    @Test
    public void missingBubbleStopsWithoutFallingBackToMapObjectClicks() {
        PostcardReturnGuard guard = new PostcardReturnGuard();

        for (int index = 0; index < 7; index++) {
            assertEquals(PostcardReturnGuard.Decision.WAIT, guard.observe(true, false));
        }
        assertEquals(PostcardReturnGuard.Decision.FAILED, guard.observe(true, false));
    }
}
