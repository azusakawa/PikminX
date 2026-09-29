package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExpeditionDispatchSessionTest {
    @Test
    public void postGoRequiresCompletedGestureAndANewerGreenCloseFrame() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.advance(ExpeditionDispatchSession.Stage.LIST_SEARCH, ExpeditionDispatchSession.Stage.DETAIL, 1_100);
        session.advance(ExpeditionDispatchSession.Stage.DETAIL, ExpeditionDispatchSession.Stage.SELECTION, 1_200);
        assertFalse(session.advanceForVerifiedScreen(ExpeditionScreenAnalyzer.Screen.RESULT, 1_300));
        assertFalse(session.advanceForVerifiedScreen(ExpeditionScreenAnalyzer.Screen.RESULT, 1_400));
        assertFalse(session.observePostGo(true, 10, 1_500));
        session.beginTransition(1_600);
        assertFalse(session.observePostGo(true, 11, 1_700));
        session.recordGoGestureCompleted(10);
        assertFalse(session.observePostGo(true, 10, 1_800));
        assertFalse(session.observePostGo(false, 11, 1_900));
        assertTrue(session.observePostGo(true, 12, 2_000));
        assertEquals(ExpeditionDispatchSession.Stage.WAIT_RESULT, session.stage());
        assertFalse(session.observePostGo(true, 13, 2_100));
    }

    @Test
    public void candidateRetriesUseElapsedNoProgressAndQuarantineOnlyThisRun() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertTrue(session.beginCandidateSelection("FRUIT:apple:local-card", 1_100L));
        assertEquals(ExpeditionDispatchSession.CandidateRetry.WAIT, session.candidateStillOnList(2_000L));
        for (long now = 4_600L; now < 25_100L; now += 3_500L) {
            assertEquals(ExpeditionDispatchSession.CandidateRetry.REACQUIRE, session.candidateStillOnList(now));
            assertTrue(session.beginCandidateSelection("FRUIT:apple:local-card", now));
        }
        assertEquals(ExpeditionDispatchSession.CandidateRetry.QUARANTINED, session.candidateStillOnList(25_100L));
        assertFalse(session.beginCandidateSelection("FRUIT:apple:local-card", 25_200L));
        assertTrue(session.beginCandidateSelection("SEEDLING:red:other-card", 25_300L));
        assertEquals(1, session.skippedCount());
        assertFalse(new ExpeditionDispatchSession(30_000L).isQuarantined("FRUIT:apple:local-card"));
    }

    @Test
    public void requiresTwoMatchingFramesBeforeAction() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("fruit:apple", 1_100L));
        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirm("fruit:apple", 1_200L));
    }

    @Test
    public void fixedGridTapCanProceedOnTheFirstVerifiedSelectionFrame() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirm("pikmin-slot-1", 1_100L, 1));
    }

    @Test
    public void detailActionCanProceedOnTheFirstVerifiedActionFrame() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertTrue(session.advance(
                ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.DETAIL,
                1_100L));

        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirmDetailAction("DETAIL_ACTION:INITIAL:9:8", 1_200L));
        assertTrue(session.beginDetailTapTransition(1_200L));
    }

    @Test
    public void rejectsSkippedPageTransition() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertFalse(session.advance(
                ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.SELECTION,
                1_100L));
        assertEquals(ExpeditionDispatchSession.Stage.LIST_SEARCH, session.stage());
    }

    @Test
    public void countsOnlyVerifiedReturnToList() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
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
        assertTrue(session.normalizing());
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(20, 55, true, false, false, false, 1_600L));
    }

    @Test
    public void returnToListRequiresTwoConsecutiveFrames() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.DETAIL, 1_100L));
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.DETAIL,
                ExpeditionDispatchSession.Stage.SELECTION, 1_200L));
        assertTrue(session.advance(ExpeditionDispatchSession.Stage.SELECTION,
                ExpeditionDispatchSession.Stage.WAIT_RESULT, 1_300L));
        assertFalse(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST, 1_400L));
        assertTrue(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST, 1_450L));

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("RETURN:EXPLORE_LIST", 1_500L));
        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("", 1_600L));
        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("RETURN:EXPLORE_LIST", 1_700L));
        assertEquals(ExpeditionDispatchSession.Confirmation.READY,
                session.confirm("RETURN:EXPLORE_LIST", 1_800L));
    }

    @Test
    public void missingScrollbarSettlesBeforeTopNormalizationContinues() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(1, 100, true, false, false, false, 1_100L));
        session.recordListScroll(100, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 200, true, false,
                        false, false, false, 1_200L));
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(3, 300, true, false,
                        false, false, false, 1_300L));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(4, 300, true, true,
                        false, false, false, 1_400L));
        session.recordListScroll(300, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(5, 400, true, false,
                        false, false, false, 1_500L));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(6, 500, true, true,
                        true, false, false, 1_600L));
        assertFalse(session.normalizing());
    }

    @Test
    public void missingScrollbarWaitsForSettledRepeatedBottomBeforeCompleting() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(1, 100, true, true, false, false, 1_100L));
        session.recordListScroll(100, false);

        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 200, true, false,
                        false, false, false, 1_200L));
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(3, 100, true, false,
                        false, false, false, 1_300L));
        assertEquals(ExpeditionDispatchSession.ListDecision.COMPLETE,
                session.observeList(4, 100, true, false,
                        false, false, false, 1_400L));
        assertTrue(session.complete());
    }

    @Test
    public void normalizesBeforeScanningAndARepeatedUnscrolledFrameCannotComplete() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertEquals(ExpeditionDispatchSession.ListDecision.EXPAND,
                session.observeList(1, 100, false, false, false, false, 1_100L));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(2, 100, true, false, false, false, 1_200L));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(3, 200, true, true, false, false, 1_300L));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(4, 200, true, false, false, false, 1_400L));
        assertFalse(session.complete());
    }

    @Test
    public void successfulDispatchDiscardsTheOldSweepAndItsEmergencyBudget() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        session.observeList(1, 1, true, true, false, false, 1_000L);
        for (int i = 1; i <= 127; i++) {
            session.recordListScroll(i, false);
            assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                    session.observeList(i + 1, i + 1, true, false, false, false, 1_000L + i * 100));
        }
        session.advance(ExpeditionDispatchSession.Stage.LIST_SEARCH, ExpeditionDispatchSession.Stage.DETAIL, 14_000);
        session.advance(ExpeditionDispatchSession.Stage.DETAIL, ExpeditionDispatchSession.Stage.SELECTION, 14_100);
        session.advance(ExpeditionDispatchSession.Stage.SELECTION, ExpeditionDispatchSession.Stage.WAIT_RESULT, 14_200);
        session.advance(ExpeditionDispatchSession.Stage.WAIT_RESULT, ExpeditionDispatchSession.Stage.VERIFY_RETURN, 14_300);
        assertTrue(session.recordReturnedToList(14_400));
        assertTrue(session.normalizing());
        assertFalse(session.complete());
        session.observeList(200, 200, true, true, false, false, 14_500);
        session.recordListScroll(200, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(201, 201, true, false, false, false, 14_600));
    }

    @Test
    public void bottomThumbCanConfirmAnAnimatedEndButNotSkipANewBottomViewport() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, false, false, 1_100);
        session.recordListScroll(100, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(2, 200, true, false, true, false, 1_200));
        session.recordListScroll(200, true);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(3, 201, true, false, true, false, 1_300));
        assertEquals(ExpeditionDispatchSession.ListDecision.COMPLETE,
                session.observeList(4, 202, true, false, true, false, 1_400));
    }

    @Test
    public void oneBouncingFrameDoesNotDiscardConfirmedBottomButNewContentCan() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, true, false, 1_100);
        session.recordListScroll(100, true);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 200, true, false, false, false, 1_200));
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(3, 201, true, false, true, false, 1_300));
        assertEquals(ExpeditionDispatchSession.ListDecision.COMPLETE,
                session.observeList(4, 202, true, false, true, false, 1_400));

        ExpeditionDispatchSession changed = new ExpeditionDispatchSession(1_000);
        changed.observeList(1, 100, true, true, true, false, 1_100);
        changed.recordListScroll(100, true);
        changed.observeList(2, 200, true, false, false, false, 1_200);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                changed.observeList(3, 201, true, false, false, false, 1_300));
        assertFalse(changed.complete());
    }

    @Test
    public void repeatedContentStillConfirmsEndWhenScrollbarDisappears() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, true, false, 1_100);
        session.recordListScroll(100, true);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 100, true, false, false, false, 1_200));
        assertEquals(ExpeditionDispatchSession.ListDecision.COMPLETE,
                session.observeList(3, 100, true, false, false, false, 1_300));
    }

    @Test
    public void returnRevealScrollNormalizesToTopBeforeFreshSweep() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, false, false, 1_100);
        session.recordReturnRevealScroll(1_150);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(2, 200, true, false, false, false, 1_200));
        assertTrue(session.normalizing());
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(3, 300, true, true, false, false, 1_300));
        assertFalse(session.normalizing());
    }

    @Test
    public void unchangedBottomDuringNormalizationFallsBackToScanWithoutCompleting() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, true, false, 1_100);
        session.recordReturnRevealScroll(1_150);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(2, 100, true, false, true, false, 1_200));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCROLL_UP,
                session.observeList(3, 100, true, false, true, false, 1_300));
        assertFalse(session.complete());
        session.recordListScroll(100, true);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(4, 100, true, false, true, false, 1_400));
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(5, 100, true, false, true, false, 1_500));
        assertFalse(session.complete());
    }

    @Test
    public void newlyActionableCandidatePreventsEndConfirmation() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000);
        session.observeList(1, 100, true, true, true, false, 1_100);
        session.recordListScroll(100, true);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(2, 100, true, false, true, true, 1_200));
        assertFalse(session.complete());
    }

    @Test
    public void endRequiresTwoFreshRepeatedFramesAfterAnAdmittedScroll() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        session.observeList(1, 100, true, true, false, false, 1_100L);
        session.recordListScroll(100, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 100, true, false, false, false, 1_200L));
        assertEquals(ExpeditionDispatchSession.ListDecision.WAIT,
                session.observeList(2, 100, true, false, false, false, 1_300L));
        assertFalse(session.complete());
        assertEquals(ExpeditionDispatchSession.ListDecision.COMPLETE,
                session.observeList(3, 100, true, false, false, false, 1_400L));
        assertEquals(ExpeditionDispatchSession.Outcome.COMPLETE, session.outcome());
    }

    @Test
    public void changedViewportContinuesScanningAndUncertainCardsBecomeSkips() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        session.observeList(1, 100, true, true, false, false, 1_100L);
        long epoch = session.viewportEpoch();
        session.recordSkippedCandidate(1_200L);
        session.recordListScroll(100, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(2, 200, true, false, false, false, 20_000L));
        assertTrue(session.viewportEpoch() > epoch);
        assertFalse(session.complete());
        session.recordListScroll(200, false);
        session.observeList(3, 200, true, false, false, false, 30_000L);
        session.observeList(4, 200, true, false, false, false, 31_000L);
        assertEquals(ExpeditionDispatchSession.Outcome.COMPLETE_WITH_SKIPS, session.outcome());
        assertEquals(1, session.skippedCount());
    }

    @Test
    public void repeatedTopViewportStartsADownwardSweepInsteadOfFinishing() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        session.observeList(1, 100, true, false, false, false, 1_100L);
        session.recordListScroll(100, false);
        session.observeList(2, 100, true, false, false, false, 1_200L);
        assertEquals(ExpeditionDispatchSession.ListDecision.SCAN,
                session.observeList(3, 100, true, false, false, false, 1_300L));
        assertFalse(session.normalizing());
        assertFalse(session.complete());
    }

    @Test
    public void gestureAcceptanceAloneCannotRenewNoProgressForever() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        session.observeList(1, 100, true, false, false, false, 1_100L);
        session.recordListScroll(100, false);
        assertEquals(ExpeditionDispatchSession.ListDecision.ABORT,
                session.observeList(2, 200, true, false, false, false, 25_000L));
        assertEquals(ExpeditionDispatchSession.Outcome.ABORTED, session.outcome());
    }

    @Test
    public void acceptedPikminTapsKeepLongManualSelectionAlive() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
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
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        session.recordProgress(301_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.WAIT,
                session.confirm("", 301_100L));
    }

    @Test
    public void stopsAStageThatDoesNotChange() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertEquals(ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT,
                session.confirm("", 25_000L));
    }

    @Test
    public void advancesOnlyAfterTwoDestinationFramesAreObserved() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);

        assertFalse(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_100L));
        assertTrue(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, 1_200L));
        assertEquals(ExpeditionDispatchSession.Stage.DETAIL, session.stage());
        assertFalse(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION, 1_300L));
        assertTrue(session.advanceForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION, 1_400L));
        assertEquals(ExpeditionDispatchSession.Stage.SELECTION, session.stage());
    }

    @Test
    public void detailTapAllowsOnlyOneDelayedRetryWhileDetailRemainsVisible() {
        ExpeditionDispatchSession session = new ExpeditionDispatchSession(1_000L);
        assertTrue(session.advance(
                ExpeditionDispatchSession.Stage.LIST_SEARCH,
                ExpeditionDispatchSession.Stage.DETAIL,
                1_100L));

        assertTrue(session.beginDetailTapTransition(1_200L));
        assertFalse(session.shouldRetryDetailTap(
                ExpeditionScreenAnalyzer.Screen.DETAIL, true, 4_699L));
        assertFalse(session.shouldRetryDetailTap(
                ExpeditionScreenAnalyzer.Screen.DETAIL, false, 4_700L));
        assertTrue(session.shouldRetryDetailTap(
                ExpeditionScreenAnalyzer.Screen.DETAIL, true, 4_700L));

        assertTrue(session.beginDetailTapTransition(4_700L));
        assertFalse(session.shouldRetryDetailTap(
                ExpeditionScreenAnalyzer.Screen.DETAIL, true, 8_200L));
        assertFalse(session.shouldRetryDetailTap(
                ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION, true, 8_200L));
    }
}
