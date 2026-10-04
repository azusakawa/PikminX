package com.pikminx.helper;

/** Stabilizes postcard evidence and bounds recovery from missing action controls. */
final class ReturnRewardPostcardGuard {
    private static final int REQUIRED_ABSENT_FRAMES = 2;
    private static final int MAX_UNRESOLVED_FRAMES = 8;

    private boolean evidenceLatched;
    private int absentFrames;
    private int unresolvedFrames;

    PostcardMatcher.ReceiptEvidence observeEvidence(
            PostcardMatcher.ReceiptEvidence evidence) {
        if (evidence == PostcardMatcher.ReceiptEvidence.PRESENT) {
            evidenceLatched = true;
            absentFrames = 0;
            return evidence;
        }
        if (evidence == PostcardMatcher.ReceiptEvidence.UNKNOWN) {
            evidenceLatched = true;
            absentFrames = 0;
            return evidence;
        }
        if (!evidenceLatched) {
            return PostcardMatcher.ReceiptEvidence.ABSENT;
        }
        if (++absentFrames < REQUIRED_ABSENT_FRAMES) {
            return PostcardMatcher.ReceiptEvidence.UNKNOWN;
        }
        evidenceLatched = false;
        absentFrames = 0;
        return PostcardMatcher.ReceiptEvidence.ABSENT;
    }

    boolean unresolvedLimitReached(boolean unresolved) {
        if (!unresolved) {
            unresolvedFrames = 0;
            return false;
        }
        return ++unresolvedFrames >= MAX_UNRESOLVED_FRAMES;
    }

    void reset() {
        evidenceLatched = false;
        absentFrames = 0;
        unresolvedFrames = 0;
    }
}
