package com.pikminx.helper;

/** Identifies postcard states that can use the previous-flower map bubble detector. */
final class PostcardBubbleDetectionPolicy {
    private PostcardBubbleDetectionPolicy() {}

    static boolean shouldDetect(
            PostcardMatcher.Page page,
            PostcardAutomation.Step step,
            boolean receiveTapped) {
        if (page == null || step == null) {
            return false;
        }
        boolean mapAllowed = step == PostcardAutomation.Step.FIND_FLOWER
                || step == PostcardAutomation.Step.OPEN_FLOWER
                || step == PostcardAutomation.Step.WAIT_RECEIPT_EXIT;
        boolean returnedBubbleNeeded = receiveTapped
                && page != PostcardMatcher.Page.POSTCARD_RECEIVED;
        return returnedBubbleNeeded
                || mapAllowed && (page == PostcardMatcher.Page.UNKNOWN
                        || page == PostcardMatcher.Page.MAP);
    }
}
