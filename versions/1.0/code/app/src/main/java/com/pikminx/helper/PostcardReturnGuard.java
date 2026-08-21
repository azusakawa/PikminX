package com.pikminx.helper;

/**
 * Requires the previous-postcard black bubble to remain visible before the next round starts.
 * No OCR text or fallback flower probing is allowed in this transition.
 */
final class PostcardReturnGuard {
    enum Decision {
        OPEN_PREVIOUS_FLOWER,
        WAIT,
        FAILED
    }

    private static final int REQUIRED_VISIBLE_FRAMES = 2;
    private static final int MAX_MISSING_FRAMES = 8;
    private int visibleFrames;
    private int missingFrames;

    Decision observe(boolean returningFromReceipt, boolean bubbleVisible) {
        if (!returningFromReceipt) {
            reset();
            return Decision.WAIT;
        }
        if (bubbleVisible) {
            missingFrames = 0;
            visibleFrames++;
            return visibleFrames >= REQUIRED_VISIBLE_FRAMES
                    ? Decision.OPEN_PREVIOUS_FLOWER
                    : Decision.WAIT;
        }
        visibleFrames = 0;
        missingFrames++;
        return missingFrames >= MAX_MISSING_FRAMES
                ? Decision.FAILED
                : Decision.WAIT;
    }

    void reset() {
        visibleFrames = 0;
        missingFrames = 0;
    }
}
