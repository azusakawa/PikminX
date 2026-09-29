package com.pikminx.helper;

/** Holds the petal-flow state when one OCR frame loses the page header. */
final class PostcardPageRecovery {
    private PostcardPageRecovery() {}

    static boolean shouldRetryStableFrame(
            PostcardMatcher.Page detectedPage, PostcardAutomation.Step step) {
        return detectedPage == PostcardMatcher.Page.UNKNOWN && isPetalFlow(step);
    }

    private static boolean isPetalFlow(PostcardAutomation.Step step) {
        return step == PostcardAutomation.Step.OPEN_PETAL_SEARCH
                || step == PostcardAutomation.Step.ENTER_PETAL_SEARCH
                || step == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD
                || step == PostcardAutomation.Step.SELECT_PETAL
                || step == PostcardAutomation.Step.TAP_NEXT
                || step == PostcardAutomation.Step.NEXT
                || step == PostcardAutomation.Step.OPEN_SORT;
    }
}
