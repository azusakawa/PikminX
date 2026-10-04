package com.pikminx.helper;

/**
 *  派遣頁面順序的純狀態機。
 *
 * <p>畫面辨識與手勢由服務提供；此類別負責完整掃描、略過結果、兩幀確認與
 * 不允許跳頁的轉移，避免舊 OCR 回呼在錯誤頁面補點。</p>
 */
final class ExpeditionDispatchSession {
    enum Stage {
        LIST_SEARCH,
        DETAIL,
        SELECTION,
        WAIT_RESULT,
        VERIFY_RETURN
    }

    enum Confirmation {
        WAIT,
        READY,
        STAGE_TIMEOUT
    }

    enum CandidateRetry { WAIT, REACQUIRE, QUARANTINED }
    enum ResultCloseDecision { WAIT, RETRY, EXHAUSTED }
    enum Outcome { RUNNING, COMPLETE, COMPLETE_WITH_SKIPS, ABORTED }
    enum ListDecision { WAIT, EXPAND, SCROLL_UP, SCAN, COMPLETE, ABORT }

    private static final long STAGE_TIMEOUT_MILLIS = 24_000L;
    private static final int REQUIRED_DESTINATION_FRAMES = 2;
    private static final long DETAIL_TAP_RETRY_DELAY_MILLIS = 3_500L;
    private static final int MAX_DETAIL_TAP_ATTEMPTS = 2;
    private static final long RESULT_CLOSE_RETRY_DELAY_MILLIS = 3_500L;
    private static final int MAX_RESULT_CLOSE_ATTEMPTS = 3;

    private Outcome outcome = Outcome.RUNNING;
    private boolean normalizing = true;
    private boolean awaitingScroll;
    private long scrollBaseline;
    private boolean scrollStartedAtBottom;
    private int missingBottomFrames;
    private long lastListCapture = -1;
    private int repeatedViewportFrames;
    private int scrollbarSettleFrames;
    private static final int SCROLLBAR_SETTLE_FRAMES = 6;
    private int listScrolls;
    private int skippedCount;
    private final java.util.Set<String> quarantine = new java.util.HashSet<>();
    private String selectingCandidate = "";
    private long candidateFirstAttempt;
    private long candidateLastAttempt;
    private long viewportEpoch;
    private static final int EMERGENCY_SCROLL_LIMIT = 128;
    private Stage stage = Stage.LIST_SEARCH;
    private long stageStartedAt;
    private String pendingKey = "";
    private int matchingFrames;
    private int completedCount;
    private boolean transitionPending;
    private long completedGoCapture = -1;
    private ExpeditionScreenAnalyzer.Screen pendingDestinationScreen =
            ExpeditionScreenAnalyzer.Screen.UNKNOWN;
    private int matchingDestinationFrames;
    private int detailTapAttempts;
    private long detailTapAt;
    private int resultCloseAttempts;
    private long resultCloseAt;
    private long resultCloseCapture = -1L;

    ExpeditionDispatchSession(long nowMillis) {
        stageStartedAt = nowMillis;
    }

    Stage stage() {
        return stage;
    }

    int completedCount() {
        return completedCount;
    }

    Outcome outcome() { return outcome; }
    int skippedCount() { return skippedCount; }
    long viewportEpoch() { return viewportEpoch; }
    boolean normalizing() { return normalizing; }
    boolean complete() { return outcome == Outcome.COMPLETE || outcome == Outcome.COMPLETE_WITH_SKIPS; }

    /** Only fresh frames after an admitted completed scroll can establish a repeated viewport. */
    ListDecision observeList(long capture, long signature, boolean expanded, boolean topVisible, boolean bottomVisible, boolean actionableVisible, long nowMillis) {
        return observeList(capture, signature, expanded, true,
                topVisible, bottomVisible, actionableVisible, nowMillis);
    }

    ListDecision observeList(long capture, long signature, boolean expanded,
            boolean scrollbarVisible, boolean topVisible, boolean bottomVisible,
            boolean actionableVisible, long nowMillis) {
        if (outcome == Outcome.ABORTED) return ListDecision.ABORT;
        if (complete()) return ListDecision.COMPLETE;
        // A list-vision callback may finish after the workflow has advanced through
        // DETAIL into PIKMIN_SELECTION. It is still a fresh capture and the same run,
        // but it no longer owns the list preflight; never let it resurrect EXPAND.
        if (stage != Stage.LIST_SEARCH || transitionPending) return ListDecision.WAIT;
        if (capture <= lastListCapture) return ListDecision.WAIT;
        lastListCapture = capture;
        if (timedOut(nowMillis) || listScrolls >= EMERGENCY_SCROLL_LIMIT) {
            abort(); return ListDecision.ABORT;
        }
        if (!expanded) return ListDecision.EXPAND;
        if (awaitingScroll && listScrolls > 0 && !scrollbarVisible) {
            // The game temporarily hides its scrollbar after a large pull. Wait for
            // the bounce to settle before deciding whether content actually moved.
            scrollbarSettleFrames++;
            if (signature == scrollBaseline) {
                if (++repeatedViewportFrames >= 2) {
                    awaitingScroll = false;
                    repeatedViewportFrames = 0;
                    scrollbarSettleFrames = 0;
                    if (normalizing) {
                        normalizing = false;
                        viewportEpoch++;
                        recordProgress(nowMillis);
                        return ListDecision.SCAN;
                    }
                    outcome = skippedCount == 0
                            ? Outcome.COMPLETE : Outcome.COMPLETE_WITH_SKIPS;
                    return ListDecision.COMPLETE;
                }
            } else {
                repeatedViewportFrames = 0;
            }
            if (scrollbarSettleFrames <= SCROLLBAR_SETTLE_FRAMES) return ListDecision.WAIT;
            scrollbarSettleFrames = 0;
        } else {
            scrollbarSettleFrames = 0;
        }
        if (normalizing && topVisible) {
            normalizing = false; awaitingScroll = false; repeatedViewportFrames = 0;
            viewportEpoch++; recordProgress(nowMillis);
            return ListDecision.SCAN;
        }
        if (awaitingScroll) {
            if (!normalizing && actionableVisible) {
                awaitingScroll = false; repeatedViewportFrames = 0;
                viewportEpoch++; recordProgress(nowMillis); return ListDecision.SCAN;
            }
            // A completed gesture can still leave one capture in the game's edge bounce.
            // Require consecutive missing-end evidence before abandoning a confirmed bottom.
            if (!normalizing && scrollStartedAtBottom && !bottomVisible && signature != scrollBaseline) {
                repeatedViewportFrames = 0;
                if (++missingBottomFrames < 2) return ListDecision.WAIT;
            } else {
                missingBottomFrames = 0;
            }
            if (signature == scrollBaseline || !normalizing && scrollStartedAtBottom && bottomVisible) {
                if (++repeatedViewportFrames < 2) return ListDecision.WAIT;
                awaitingScroll = false; repeatedViewportFrames = 0;
                if (normalizing) {
                    normalizing = false; viewportEpoch++; recordProgress(nowMillis);
                    return ListDecision.SCAN;
                }
                outcome = skippedCount == 0 ? Outcome.COMPLETE : Outcome.COMPLETE_WITH_SKIPS;
                return ListDecision.COMPLETE;
            }
            awaitingScroll = false; repeatedViewportFrames = 0;
            viewportEpoch++; recordProgress(nowMillis);
        }
        return normalizing ? ListDecision.SCROLL_UP : ListDecision.SCAN;
    }

    /** Called only by the current run's successful gesture callback, never on a rejected action. */
    void recordListScroll(long signature, boolean atBottom) {
        if (stage != Stage.LIST_SEARCH || outcome != Outcome.RUNNING || transitionPending) return;
        awaitingScroll = true; scrollBaseline = signature; scrollStartedAtBottom = atBottom; repeatedViewportFrames = 0; missingBottomFrames = 0;
        listScrolls++;
    }

    void recordSkippedCandidate(long nowMillis) {
        skippedCount++;
        recordProgress(nowMillis);
    }

    boolean isQuarantined(String fingerprint) { return quarantine.contains(fingerprint); }

    boolean beginCandidateSelection(String fingerprint, long nowMillis) {
        if (outcome != Outcome.RUNNING || stage != Stage.LIST_SEARCH || fingerprint == null || fingerprint.isEmpty()
                || isQuarantined(fingerprint)) return false;
        if (!fingerprint.equals(selectingCandidate)) {
            selectingCandidate = fingerprint; candidateFirstAttempt = nowMillis;
        }
        candidateLastAttempt = nowMillis;
        beginTransition(nowMillis);
        return true;
    }

    /** A fresh confirmed LIST frame permits reacquisition, never reuse of the old tap bounds. */
    CandidateRetry candidateStillOnList(long nowMillis) {
        if (stage != Stage.LIST_SEARCH || !transitionPending || selectingCandidate.isEmpty())
            return CandidateRetry.WAIT;
        if (nowMillis - candidateFirstAttempt >= STAGE_TIMEOUT_MILLIS) {
            quarantine.add(selectingCandidate);
            transitionPending = false; selectingCandidate = ""; resetDestinationEvidence();
            recordSkippedCandidate(nowMillis);
            return CandidateRetry.QUARANTINED;
        }
        if (nowMillis - candidateLastAttempt < DETAIL_TAP_RETRY_DELAY_MILLIS) return CandidateRetry.WAIT;
        transitionPending = false; resetDestinationEvidence();
        return CandidateRetry.REACQUIRE;
    }

    void abort() { outcome = Outcome.ABORTED; }
    boolean timedOut(long nowMillis) { return nowMillis - stageStartedAt >= STAGE_TIMEOUT_MILLIS; }

    boolean transitionPending() {
        return transitionPending;
    }

    /** Starts a bounded wait for the destination screen without treating the gesture as progress. */
    void beginTransition(long nowMillis) {
        transitionPending = true;
        stageStartedAt = nowMillis;
        pendingKey = "";
        matchingFrames = 0;
        resetDestinationEvidence();
    }

    /** Records the initial detail-page tap or its single bounded retry. */
    boolean beginDetailTapTransition(long nowMillis) {
        if (stage != Stage.DETAIL || detailTapAttempts >= MAX_DETAIL_TAP_ATTEMPTS) {
            return false;
        }
        detailTapAttempts++;
        detailTapAt = nowMillis;
        beginTransition(nowMillis);
        return true;
    }

    /** Retry only when the same detail page remains visible after the gesture settle time. */
    boolean shouldRetryDetailTap(
            ExpeditionScreenAnalyzer.Screen screen,
            boolean detailActionVisible,
            long nowMillis) {
        return stage == Stage.DETAIL
                && transitionPending
                && screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                && detailActionVisible
                && detailTapAttempts > 0
                && detailTapAttempts < MAX_DETAIL_TAP_ATTEMPTS
                && nowMillis - detailTapAt >= DETAIL_TAP_RETRY_DELAY_MILLIS;
    }

    int detailTapAttempts() {
        return detailTapAttempts;
    }

    /** Records one admitted result-close tap; every retry must use a newer capture. */
    boolean beginResultCloseAttempt(long sourceCapture, long nowMillis) {
        if (outcome != Outcome.RUNNING || stage != Stage.WAIT_RESULT
                || resultCloseAttempts >= MAX_RESULT_CLOSE_ATTEMPTS
                || resultCloseAttempts > 0 && sourceCapture <= resultCloseCapture) {
            return false;
        }
        resultCloseAttempts++;
        resultCloseCapture = sourceCapture;
        resultCloseAt = nowMillis;
        beginTransition(nowMillis);
        return true;
    }

    /** A persistent green result X may be retried only after a fresh settled frame. */
    ResultCloseDecision observePendingResultClose(
            boolean closeVisible, long capture, long nowMillis) {
        if (stage != Stage.WAIT_RESULT || !transitionPending || resultCloseAttempts == 0
                || !closeVisible || capture <= resultCloseCapture
                || nowMillis - resultCloseAt < RESULT_CLOSE_RETRY_DELAY_MILLIS) {
            return ResultCloseDecision.WAIT;
        }
        return resultCloseAttempts >= MAX_RESULT_CLOSE_ATTEMPTS
                ? ResultCloseDecision.EXHAUSTED : ResultCloseDecision.RETRY;
    }

    int resultCloseAttempts() {
        return resultCloseAttempts;
    }

    /** 同一頁內的有效操作也算進度，避免長流程被固定頁面逾時中止。 */
    void recordProgress(long nowMillis) {
        stageStartedAt = nowMillis;
        pendingKey = "";
        matchingFrames = 0;
    }

    Confirmation confirm(String key, long nowMillis) {
        return confirm(key, nowMillis, 2);
    }

    /**
     * The detail-page action is already gated by the verified detail screen and final
     * ActionAdmission, so the first valid OCR hit may drive the tap immediately.
     */
    Confirmation confirmDetailAction(String key, long nowMillis) {
        return confirm(key, nowMillis, 1);
    }

    Confirmation confirm(String key, long nowMillis, int requiredFrames) {
        if (timedOut(nowMillis)) {
            return Confirmation.STAGE_TIMEOUT;
        }
        String safeKey = key == null ? "" : key;
        if (safeKey.isEmpty()) {
            pendingKey = "";
            matchingFrames = 0;
            return Confirmation.WAIT;
        }
        if (!safeKey.equals(pendingKey)) {
            pendingKey = safeKey;
            matchingFrames = 1;
            return requiredFrames <= 1 ? Confirmation.READY : Confirmation.WAIT;
        }
        matchingFrames++;
        return matchingFrames >= Math.max(1, requiredFrames)
                ? Confirmation.READY : Confirmation.WAIT;
    }

    boolean advance(Stage expected, Stage next, long nowMillis) {
        if (outcome != Outcome.RUNNING || stage != expected || !allowed(expected, next)) {
            return false;
        }
        stage = next;
        if (next == Stage.SELECTION) completedGoCapture = -1;
        if (next == Stage.WAIT_RESULT) resetResultCloseAttempts();
        stageStartedAt = nowMillis;
        pendingKey = "";
        matchingFrames = 0;
        transitionPending = false;
        resetDestinationEvidence();
        if (next == Stage.DETAIL) selectingCandidate = "";
        if (next == Stage.DETAIL || expected == Stage.DETAIL) {
            resetDetailTap();
        }
        return true;
    }

    private void resetDetailTap() {
        detailTapAttempts = 0;
        detailTapAt = 0L;
    }

    private void resetResultCloseAttempts() {
        resultCloseAttempts = 0;
        resultCloseAt = 0L;
        resultCloseCapture = -1L;
    }

    /** 手勢成功只代表 Android 接受輸入；看到目的頁後才推進派遣階段。 */
    void recordGoGestureCompleted(long sourceCapture) {
        if (outcome == Outcome.RUNNING && stage == Stage.SELECTION && transitionPending)
            completedGoCapture = sourceCapture;
    }

    boolean observePostGo(boolean greenCloseVisible, long capture, long nowMillis) {
        return stage == Stage.SELECTION && transitionPending && completedGoCapture > 0
                && capture > completedGoCapture && greenCloseVisible
                && advance(Stage.SELECTION, Stage.WAIT_RESULT, nowMillis);
    }

    boolean advanceForVerifiedScreen(
            ExpeditionScreenAnalyzer.Screen screen, long nowMillis) {
        return advanceForVerifiedScreen(screen, false, nowMillis);
    }

    boolean advanceForVerifiedScreen(
            ExpeditionScreenAnalyzer.Screen screen,
            boolean resultCloseVisible,
            long nowMillis) {
        if (stage == Stage.WAIT_RESULT && resultCloseVisible) {
            resetDestinationEvidence();
            return false;
        }
        Stage next = switch (stage) {
            case LIST_SEARCH -> screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                    ? Stage.DETAIL : null;
            case DETAIL -> screen == ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION
                    ? Stage.SELECTION : null;
            case SELECTION -> null; // Only fresh post-GO green-close evidence can advance.
            case WAIT_RESULT -> screen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST
                    ? Stage.VERIFY_RETURN : null;
            case VERIFY_RETURN -> null;
        };
        if (next == null) {
            resetDestinationEvidence();
            return false;
        }
        if (screen != pendingDestinationScreen) {
            pendingDestinationScreen = screen;
            matchingDestinationFrames = 1;
            return false;
        }
        matchingDestinationFrames++;
        return matchingDestinationFrames >= REQUIRED_DESTINATION_FRAMES
                && advance(stage, next, nowMillis);
    }

    boolean recordReturnedToList(long nowMillis) {
        if (stage != Stage.VERIFY_RETURN) {
            return false;
        }
        completedCount++;
        stage = Stage.LIST_SEARCH;
        stageStartedAt = nowMillis;
        pendingKey = "";
        matchingFrames = 0;
        transitionPending = false;
        resetDestinationEvidence();
        // Resume the same top-to-bottom sweep. Only transient gesture-settle state
        // is cleared; the current viewport, scroll budget and stale-frame boundary remain.
        awaitingScroll = false;
        scrollStartedAtBottom = false;
        repeatedViewportFrames = 0;
        missingBottomFrames = 0;
        scrollbarSettleFrames = 0;
        return true;
    }

    private void resetDestinationEvidence() {
        pendingDestinationScreen = ExpeditionScreenAnalyzer.Screen.UNKNOWN;
        matchingDestinationFrames = 0;
    }

    private static boolean allowed(Stage from, Stage to) {
        return switch (from) {
            case LIST_SEARCH -> to == Stage.DETAIL;
            case DETAIL -> to == Stage.SELECTION;
            case SELECTION -> to == Stage.WAIT_RESULT;
            case WAIT_RESULT -> to == Stage.VERIFY_RETURN;
            case VERIFY_RETURN -> to == Stage.LIST_SEARCH;
        };
    }
}
