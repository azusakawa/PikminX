package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExpeditionDispatchSessionTest {
    @Test
    public void requiresTwoMatchingFramesBeforeAction() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(2, 1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("fruit:apple", 1_100L));
        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirm("fruit:apple", 1_200L));
    }

    @Test
    public void fixedGridTapCanProceedOnTheFirstVerifiedSelectionFrame() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirm("pikmin-slot-1", 1_100L, 1));
    }

    @Test
    public void rejectsSkippedPageTransition() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertFalse(session.advance(
                ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.SELECTION,
                1_100L));
        assertEquals(ExpeditionDispatchSession.Stage.LIST_SEARCH, session.stage());
    }

    @Test
    public void countsOnlyVerifiedReturnToList() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(2, 1_000L);
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.DETAIL, 1_100L));
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.DETAIL,
                ExpeditionDispatchSession.Stage.SELECTION, 1_200L));
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.SELECTION,
                ExpeditionDispatchSession.Stage.WAIT_RESULT, 1_300L));
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.WAIT_RESULT,
                ExpeditionDispatchSession.Stage.VERIFY_RETURN, 1_400L));

        assertTrue(session.recordReturnedToList(1_500L));
        assertFalse(session.complete());
        assertEquals(1, session.completedCount());
        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP,
                session.observeListForBottom(false, 1_600L));
    }

    @Test
    public void settlesAtBottomBeforeScanningTowardTheListStart() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(2, 1_000L);

        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP,
                session.observeListForBottom(false, 1_100L));
        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.READY,
                session.observeListForBottom(true, 1_200L));
        assertEquals(ExpeditionDispatchSession.ListScanDecision.WAIT,
                session.recordListMiss(false, 1_400L));
        assertEquals(ExpeditionDispatchSession.ListScanDecision.SCROLL,
                session.recordListMiss(false, 1_500L));
        assertEquals(ExpeditionDispatchSession.ListScanDecision.WAIT,
                session.recordListMiss(true, 1_600L));
        assertEquals(ExpeditionDispatchSession.ListScanDecision.AT_LIST_START,
                session.recordListMiss(true, 1_700L));
    }

    @Test
    public void changingOcrSignatureDoesNotTrapAnExpandedListAtBottom() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP,
                session.observeListForBottom(false, 1_100L));
        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.READY,
                session.observeListForBottom(true, 1_200L));
    }

    @Test
    public void alreadyExpandedListSkipsTheRevealSwipeLikeAutoCool() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.READY,
                session.observeListForBottom(true, 1_100L));
    }

    @Test
    public void bottomSettleStopsAfterFourUnconfirmedSwipes() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP,
                    session.observeListForBottom(false, 1_100L + attempt * 100L));
        }
        assertEquals(ExpeditionDispatchSession.BottomSettleDecision.FAILED,
                session.observeListForBottom(false, 1_500L));
    }

    @Test
    public void acceptedListScrollRenewsTheStageTimeout() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(2, 1_000L);

        session.recordListMiss(false, 20_000L);
        session.recordListMiss(false, 20_100L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("", 30_000L));
    }

    @Test
    public void acceptedPikminTapsKeepLongManualSelectionAlive() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);
        session.advance(ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.DETAIL, 1_100L);
        session.advance(ExpeditionDispatchSession.Stage.DETAIL,
                ExpeditionDispatchSession.Stage.SELECTION, 1_200L);

        session.recordProgress(20_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("", 30_000L));
    }

    @Test
    public void activeDispatchHasNoFiveMinuteRunLimit() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        session.recordProgress(301_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("", 301_100L));
    }

    @Test
    public void stopsAStageThatDoesNotChange() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT,
                session.confirm("", 25_000L));
    }

    @Test
    public void advancesOnlyAfterTheDestinationScreenIsObserved() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1, 1_000L);

        assertTrue(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_100L));
        assertFalse(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_200L));
        assertEquals(ExpeditionDispatchSession.Stage.DETAIL, session.stage());
        assertTrue(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION, 1_300L));
        assertEquals(ExpeditionDispatchSession.Stage.SELECTION, session.stage());
    }
}
