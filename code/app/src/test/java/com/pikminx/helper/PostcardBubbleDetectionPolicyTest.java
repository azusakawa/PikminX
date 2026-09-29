package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PostcardBubbleDetectionPolicyTest {
    @Test
    public void onlyScansTheMapBubbleForMapRelevantPostcardStates() {
        assertTrue(PostcardBubbleDetectionPolicy.shouldDetect(
                PostcardMatcher.Page.UNKNOWN,
                PostcardAutomation.Step.FIND_FLOWER,
                false));
        assertTrue(PostcardBubbleDetectionPolicy.shouldDetect(
                PostcardMatcher.Page.MAP,
                PostcardAutomation.Step.WAIT_RECEIPT_EXIT,
                false));
        assertTrue(PostcardBubbleDetectionPolicy.shouldDetect(
                PostcardMatcher.Page.FLOWER_DETAIL,
                PostcardAutomation.Step.WAIT_RECEIPT_EXIT,
                true));
        assertFalse(PostcardBubbleDetectionPolicy.shouldDetect(
                PostcardMatcher.Page.POSTCARD_RECEIVED,
                PostcardAutomation.Step.USE_PETALS,
                false));
    }
}
