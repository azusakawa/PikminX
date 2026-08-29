package com.pikminx.helper;

import java.util.List;

/** 自動種花在低於門檻並已穩定確認後的順位決策。 */
final class PlantingFlowPolicy {
    enum EntryAction {
        OPEN_MAP_ENTRY,
        BEGIN_POT_SEARCH,
        WAIT_FOR_SCREEN
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
            case PLANTING_MENU -> EntryAction.BEGIN_POT_SEARCH;
            case MAP_VISIBLE_NO_ENTRY, AMBIGUOUS, UNKNOWN -> EntryAction.WAIT_FOR_SCREEN;
        };
    }

    static StartAction startAction(boolean startVisible, boolean stopVisible) {
        if (stopVisible) {
            return StartAction.ALREADY_ACTIVE;
        }
        return startVisible ? StartAction.TAP_START : StartAction.WAIT_FOR_CONTROL;
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

    static boolean shouldRecordPlantedFlower(boolean newlyStarted, boolean startTapped) {
        return newlyStarted && startTapped;
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

    static LowCountDecision afterConfirmedLowCount(
            List<String> sequence, String currentFlower) {
        String nextFlower = PetalMatcher.nextTarget(sequence, currentFlower);
        return nextFlower == null
                ? new LowCountDecision(LowCountAction.STOP_PLANTING, null)
                : new LowCountDecision(LowCountAction.SEARCH_NEXT, nextFlower);
    }
}
