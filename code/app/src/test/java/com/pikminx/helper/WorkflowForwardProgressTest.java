package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Checks that workflow rejection paths end in a scan, explicit stop, or success state. */
public final class WorkflowForwardProgressTest {
    @Test
    public void plantingWrongScreenIsAnExplicitStop() {
        assertEquals(
                PlantingFlowPolicy.EntryAction.STOP_WRONG_SCREEN,
                PlantingFlowPolicy.entryAction(PlantingScreenAnalyzer.Screen.HOME));
    }

    @Test
    public void plantingLowCountEitherSearchesNextOrStopsAtTheEnd() {
        PlantingFlowPolicy.LowCountDecision next = PlantingFlowPolicy.afterConfirmedLowCount(
                List.of("白色花瓣", "黃色花瓣"), "白色花瓣");
        PlantingFlowPolicy.LowCountDecision last = PlantingFlowPolicy.afterConfirmedLowCount(
                List.of("白色花瓣", "黃色花瓣"), "黃色花瓣");

        assertEquals(PlantingFlowPolicy.LowCountAction.SEARCH_NEXT, next.action());
        assertEquals(PlantingFlowPolicy.LowCountAction.STOP_PLANTING, last.action());
    }

    @Test
    public void feedHoldAdmissionFailureLeavesNoPermanentHoldState() {
        FeedHoldLifecycle hold = new FeedHoldLifecycle();
        assertTrue(hold.beginStart());
        assertTrue(hold.markContinuedStrokeActive());
        assertTrue(hold.beginAbort());

        assertTrue(hold.abortWithoutTerminal());
        assertTrue(hold.isIdle());
        assertFalse(hold.isContinuedStrokeActive());
    }

    @Test
    public void dispatchTimeoutIsAnExplicitStopAndVerifiedStageCanAdvance() {
        ExpeditionDispatchSession timedOut = new ExpeditionDispatchSession(1_000L);
        assertEquals(
                ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT,
                timedOut.confirm("", 25_000L));

        ExpeditionDispatchSession progressing = new ExpeditionDispatchSession(1_000L);
        assertFalse(progressing.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_100L));
        assertTrue(progressing.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_200L));
        assertEquals(ExpeditionDispatchSession.Stage.DETAIL, progressing.stage());
    }

    @Test
    public void postcardReceiptCanRetryOrConfirmAndReturnToNextItem() {
        PostcardAutomation automation = new PostcardAutomation();
        automation.start(1, "紅色花瓣", 1);
        automation.markReceiveTapped();
        assertEquals(PostcardAutomation.Step.WAIT_RECEIPT_EXIT, automation.step());

        automation.retryReceive();
        assertEquals(PostcardAutomation.Step.RECEIVE, automation.step());
        automation.markReceiveTapped();
        assertTrue(automation.confirmReceiptExit());
        assertTrue(automation.isComplete());
        assertEquals(PostcardAutomation.Step.FIND_FLOWER, automation.step());
    }

    @Test
    public void returnRewardEventuallyReachesCompleteAfterBoundedEmptyEvidence() {
        ReturnRewardScanGuard guard = new ReturnRewardScanGuard();
        ReturnRewardScanGuard.Decision decision = ReturnRewardScanGuard.Decision.WAIT;
        for (int index = 0; index < 6; index++) {
            decision = guard.observe(PostcardMatcher.Page.MAP, null, 432, 936);
        }

        assertEquals(ReturnRewardScanGuard.Decision.COMPLETE, decision);
    }

    @Test
    public void rejectedPipelineOwnersCanAllBeClearedWithoutStayingPending() {
        ScreenshotRequestQueue<String> screenshots = new ScreenshotRequestQueue<>();
        screenshots.enqueue("old");
        ScreenshotRequestQueue.Entry<String> activeScreenshot = screenshots.startNext();
        assertTrue(screenshots.finish(activeScreenshot.id()));
        assertNull(screenshots.active());

        OcrScanner.TransactionRegistry transactions = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction transaction = transactions.begin(1L, 1L);
        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.CANCELLED));
        assertTrue(transactions.clear(transaction));
        assertNull(transactions.active());
    }
}
