package com.pikminx.helper;

/** Requires stable post-receipt map evidence before the next round starts. */
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

    Decision observe(boolean returningFromReceipt, boolean returnEvidence) {
        if (!returningFromReceipt) {
            reset();
            return Decision.WAIT;
        }
        if (returnEvidence) {
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
