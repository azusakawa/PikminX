package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PostcardPageRecoveryTest {
    @Test
    public void unknownFrameKeepsPetalSelectionWhileSearchingPetals() {
        assertTrue(
                PostcardPageRecovery.shouldRetryStableFrame(
                        PostcardMatcher.Page.UNKNOWN,
                        PostcardAutomation.Step.SELECT_PETAL));
    }

    @Test
    public void unknownFrameKeepsPetalSelectionWhileWaitingForNext() {
        assertTrue(
                PostcardPageRecovery.shouldRetryStableFrame(
                        PostcardMatcher.Page.UNKNOWN,
                        PostcardAutomation.Step.NEXT));
    }

    @Test
    public void unrelatedUnknownFrameRemainsUnknown() {
        assertFalse(
                PostcardPageRecovery.shouldRetryStableFrame(
                        PostcardMatcher.Page.UNKNOWN,
                        PostcardAutomation.Step.FIND_FLOWER));
    }

    @Test
    public void knownPetalFrameDoesNotDelayActions() {
        assertFalse(
                PostcardPageRecovery.shouldRetryStableFrame(
                        PostcardMatcher.Page.PETAL_SELECTION,
                        PostcardAutomation.Step.SELECT_PETAL));
    }
}
