package com.pikminx.helper;

/**
 *  派遣頁面順序的純狀態機。
 *
 * <p>畫面辨識與手勢由服務提供；此類別只負責兩幀確認、逾時、完成次數與
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

    enum ListScanDecision {
        WAIT,
        SCROLL,
        AT_LIST_START
    }

    enum BottomSettleDecision {
        SWIPE_UP,
        READY,
        FAILED
    }

    private static final long STAGE_TIMEOUT_MILLIS = 24_000L;
    private static final int REQUIRED_DESTINATION_FRAMES = 2;
    private static final long DETAIL_TAP_RETRY_DELAY_MILLIS = 3_500L;
    private static final int MAX_DETAIL_TAP_ATTEMPTS = 2;

    private final int targetCount;
    private Stage stage = Stage.LIST_SEARCH;
    private long stageStartedAt;
    private String pendingKey = "";
    private int matchingFrames;
    private int completedCount;
    private int listMissFrames;
    private static final int MAX_BOTTOM_SETTLE_SWIPES = 4;
    private int bottomSwipeAttempts;
    private boolean bottomSettled;
    private boolean transitionPending;
    private ExpeditionScreenAnalyzer.Screen pendingDestinationScreen =
            ExpeditionScreenAnalyzer.Screen.UNKNOWN;
    private int matchingDestinationFrames;
    private boolean postReturnRevealPending;
    private int detailTapAttempts;
    private long detailTapAt;

    ExpeditionDispatchSession(int targetCount, long nowMillis) {
        this.targetCount = Math.max(1, Math.min(99, targetCount));
        stageStartedAt = nowMillis;
    }

    Stage stage() {
        return stage;
    }

    int completedCount() {
        return completedCount;
    }

    int targetCount() {
        return targetCount;
    }

    boolean complete() {
        return completedCount >= targetCount;
    }

    BottomSettleDecision observeListForBottom(
            boolean panelExpanded,
            long nowMillis) {
        if (postReturnRevealPending) {
            postReturnRevealPending = false;
            stageStartedAt = nowMillis;
            return BottomSettleDecision.SWIPE_UP;
        }
        if (bottomSettled) {
            return BottomSettleDecision.READY;
        }
        if (panelExpanded) {
            bottomSettled = true;
            listMissFrames = 0;
            return BottomSettleDecision.READY;
        }
        if (bottomSwipeAttempts >= MAX_BOTTOM_SETTLE_SWIPES) {
            return BottomSettleDecision.FAILED;
        }
        bottomSwipeAttempts++;
        stageStartedAt = nowMillis;
        return BottomSettleDecision.SWIPE_UP;
    }

    ListScanDecision recordListMiss(boolean listStartVisible, long nowMillis) {
        if (++listMissFrames < 2) {
            return ListScanDecision.WAIT;
        }
        listMissFrames = 0;
        if (listStartVisible) {
            return ListScanDecision.AT_LIST_START;
        }
        stageStartedAt = nowMillis;
        return ListScanDecision.SCROLL;
    }

    void recordListTargetFound() {
        listMissFrames = 0;
    }

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
            ExpeditionScreenAnalyzer.Screen screen, long nowMillis) {
        return stage == Stage.DETAIL
                && transitionPending
                && screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                && detailTapAttempts > 0
                && detailTapAttempts < MAX_DETAIL_TAP_ATTEMPTS
                && nowMillis - detailTapAt >= DETAIL_TAP_RETRY_DELAY_MILLIS;
    }

    int detailTapAttempts() {
        return detailTapAttempts;
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

    Confirmation confirm(String key, long nowMillis, int requiredFrames) {
        if (nowMillis - stageStartedAt >= STAGE_TIMEOUT_MILLIS) {
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
        if (stage != expected || !allowed(expected, next)) {
            return false;
        }
        stage = next;
        stageStartedAt = nowMillis;
        pendingKey = "";
        matchingFrames = 0;
        transitionPending = false;
        resetDestinationEvidence();
        resetListScan();
        if (next == Stage.DETAIL || expected == Stage.DETAIL) {
            resetDetailTap();
        }
        return true;
    }

    private void resetListScan() {
        listMissFrames = 0;
        bottomSwipeAttempts = 0;
        bottomSettled = false;
    }

    private void resetDetailTap() {
        detailTapAttempts = 0;
        detailTapAt = 0L;
    }

    /** 手勢成功只代表 Android 接受輸入；看到目的頁後才推進派遣階段。 */
    boolean advanceForVerifiedScreen(
            ExpeditionScreenAnalyzer.Screen screen, long nowMillis) {
        Stage next = switch (stage) {
            case LIST_SEARCH -> screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                    ? Stage.DETAIL : null;
            case DETAIL -> screen == ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION
                    ? Stage.SELECTION : null;
            case SELECTION -> screen == ExpeditionScreenAnalyzer.Screen.RESULT
                    ? Stage.WAIT_RESULT : null;
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
        resetListScan();
        postReturnRevealPending = true;
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
