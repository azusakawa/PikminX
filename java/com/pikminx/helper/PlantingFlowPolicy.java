package com.pikminx.helper;

import java.util.List;

/** 自動種花在低於門檻並已穩定確認後的順位決策。 */
final class PlantingFlowPolicy {
    enum EntryAction {
        OPEN_MAP_ENTRY,
        CONFIRM_PLANTING_MENU,
        STOP_WRONG_SCREEN
    }

    enum StartAction {
        TAP_START,
        ALREADY_ACTIVE,
        WAIT_FOR_CONTROL
    }

    enum LowCountAction {
        SEARCH_NEXT,
        STOP_PLANTING
    }

    record LowCountDecision(LowCountAction action, String nextFlower) {}

    private PlantingFlowPolicy() {}

    static EntryAction entryAction(PlantingScreenAnalyzer.Screen screen) {
        return switch (screen) {
            case MAP_WITH_ENTRY -> EntryAction.OPEN_MAP_ENTRY;
            case PLANTING_MENU -> EntryAction.CONFIRM_PLANTING_MENU;
            case HOME, MAP_VISIBLE_NO_ENTRY, AMBIGUOUS, UNKNOWN ->
                    EntryAction.STOP_WRONG_SCREEN;
        };
    }

    static StartAction startAction(boolean startVisible, boolean stopVisible) {
        if (stopVisible) {
            return StartAction.ALREADY_ACTIVE;
        }
        return startVisible ? StartAction.TAP_START : StartAction.WAIT_FOR_CONTROL;
    }

    static boolean shouldWaitForStartAfterSelection(
            boolean startVisible, boolean stopVisible) {
        return startAction(startVisible, stopVisible) != StartAction.ALREADY_ACTIVE;
    }

    static boolean hasActiveMapEvidence(
            PlantingScreenAnalyzer.Screen screen,
            boolean startVisible,
            boolean startedNotice,
            boolean plantingStatsHeader,
            boolean boostVisible) {
        return screen == PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY
                && !startVisible
                && (startedNotice || (plantingStatsHeader && boostVisible));
    }

    static boolean shouldReturnToMenuAfterConfirmedStart(
            PlantingScreenAnalyzer.Screen screen) {
        return screen != PlantingScreenAnalyzer.Screen.PLANTING_MENU;
    }

    /** 高亮辨識優先；高亮不明確時才採用目前花名的精確 OCR 配對。 */
    static PetalMatcher.Selection monitoringSelection(
            PetalMatcher.Selection highlighted,
            PetalMatcher.Selection visibleCurrent) {
        return highlighted != null ? highlighted : visibleCurrent;
    }

    static boolean shouldUseFocusedMonitorOcr(int consecutiveMisses) {
        return consecutiveMisses >= 2;
    }

    static boolean shouldSkipCandidate(int remaining, int threshold) {
        return remaining >= 0 && SwitchGuard.isBelowThreshold(remaining, threshold);
    }

    /** 搜尋結果已穩定確認時，點擊成功後不再重複執行選取 OCR。 */
    static boolean requiresSelectionOcrAfterTap(boolean searchedSelection) {
        return !searchedSelection;
    }

    static LowCountDecision afterConfirmedLowCount(
            List<String> sequence, String currentFlower) {
        String nextFlower = PetalMatcher.nextTarget(sequence, currentFlower);
        return nextFlower == null
                ? new LowCountDecision(LowCountAction.STOP_PLANTING, null)
                : new LowCountDecision(LowCountAction.SEARCH_NEXT, nextFlower);
    }
}
