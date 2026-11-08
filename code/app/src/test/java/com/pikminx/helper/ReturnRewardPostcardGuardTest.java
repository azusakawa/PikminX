package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ReturnRewardPostcardGuardTest {
    @Test
    public void latchedPostcardNeedsTwoAbsentFramesToClear() {
        ReturnRewardPostcardGuard guard = new ReturnRewardPostcardGuard();

        assertEquals(
                PostcardMatcher.ReceiptEvidence.PRESENT,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.PRESENT));
        assertEquals(
                PostcardMatcher.ReceiptEvidence.UNKNOWN,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.ABSENT));
        assertEquals(
                PostcardMatcher.ReceiptEvidence.ABSENT,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.ABSENT));
    }

    @Test
    public void unknownEvidenceBreaksTheAbsentSequence() {
        ReturnRewardPostcardGuard guard = new ReturnRewardPostcardGuard();
        guard.observeEvidence(PostcardMatcher.ReceiptEvidence.PRESENT);

        assertEquals(
                PostcardMatcher.ReceiptEvidence.UNKNOWN,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.ABSENT));
        assertEquals(
                PostcardMatcher.ReceiptEvidence.UNKNOWN,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.UNKNOWN));
        assertEquals(
                PostcardMatcher.ReceiptEvidence.UNKNOWN,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.ABSENT));
        assertEquals(
                PostcardMatcher.ReceiptEvidence.ABSENT,
                guard.observeEvidence(PostcardMatcher.ReceiptEvidence.ABSENT));
    }

    @Test
    public void unresolvedPostcardEvidenceHasABoundedLimit() {
        ReturnRewardPostcardGuard guard = new ReturnRewardPostcardGuard();

        for (int frame = 0; frame < 7; frame++) {
            assertFalse(guard.unresolvedLimitReached(true));
        }
        assertTrue(guard.unresolvedLimitReached(true));
        assertFalse(guard.unresolvedLimitReached(false));
        assertFalse(guard.unresolvedLimitReached(true));
    }

}
