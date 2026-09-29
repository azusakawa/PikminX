package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class PlantingFlowPolicyTest {
    @Test
    public void candidateIsSkippedBeforeSelectionOnlyBelowThreshold() {
        assertTrue(PlantingFlowPolicy.shouldSkipCandidate(0, 10));
        assertTrue(PlantingFlowPolicy.shouldSkipCandidate(9, 10));
        assertFalse(PlantingFlowPolicy.shouldSkipCandidate(10, 10));
        assertFalse(PlantingFlowPolicy.shouldSkipCandidate(100, 10));
        assertFalse(PlantingFlowPolicy.shouldSkipCandidate(-1, 10));
    }

    @Test
    public void searchedPotTapSkipsSelectionOcrForInitialAndActivePlanting() {
        assertFalse(PlantingFlowPolicy.requiresSelectionOcrAfterTap(true));
        assertTrue(PlantingFlowPolicy.requiresSelectionOcrAfterTap(false));
    }

    @Test
    public void skippingSecondAdvancesToThirdWithoutChangingCurrentFlower() {
        String current = "白色花瓣";
        var sequence = List.of(current, "黃色花瓣", "紅色花瓣");
        assertEquals("紅色花瓣",
                PlantingFlowPolicy.afterConfirmedLowCount(sequence, "黃色花瓣").nextFlower());
        assertEquals(PlantingFlowPolicy.LowCountAction.STOP_PLANTING,
                PlantingFlowPolicy.afterConfirmedLowCount(sequence, "紅色花瓣").action());
    }
    @Test
    public void opensTheMapEntryBeforeSearchingForPots() {
        assertEquals(
                PlantingFlowPolicy.EntryAction.OPEN_MAP_ENTRY,
                PlantingFlowPolicy.entryAction(
                        PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY));
    }

    @Test
    public void confirmsThePlantingMenuBeforeSearchingForPots() {
        assertEquals(
                PlantingFlowPolicy.EntryAction.CONFIRM_PLANTING_MENU,
                PlantingFlowPolicy.entryAction(
                        PlantingScreenAnalyzer.Screen.PLANTING_MENU));
    }

    @Test
    public void homeStopsWithoutUsingTheMapEntryOrPotSearchPath() {
        assertEquals(
                PlantingFlowPolicy.EntryAction.STOP_WRONG_SCREEN,
                PlantingFlowPolicy.entryAction(PlantingScreenAnalyzer.Screen.HOME));
    }

    @Test
    public void screenWithoutAWhistleOrPlantingMenuStopsWithoutAGesture() {
        for (PlantingScreenAnalyzer.Screen screen : List.of(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                PlantingScreenAnalyzer.Screen.AMBIGUOUS,
                PlantingScreenAnalyzer.Screen.UNKNOWN)) {
            assertEquals(
                    PlantingFlowPolicy.EntryAction.STOP_WRONG_SCREEN,
                    PlantingFlowPolicy.entryAction(screen));
        }
    }

    @Test
    public void startsOnlyWhenPlayIsPresentAndTreatsStopAsAlreadyActive() {
        assertEquals(
                PlantingFlowPolicy.StartAction.TAP_START,
                PlantingFlowPolicy.startAction(true, false));
        assertEquals(
                PlantingFlowPolicy.StartAction.ALREADY_ACTIVE,
                PlantingFlowPolicy.startAction(false, true));
        assertEquals(
                PlantingFlowPolicy.StartAction.WAIT_FOR_CONTROL,
                PlantingFlowPolicy.startAction(false, false));
    }

    @Test
    public void activePlantingSkipsWaitingStartAfterInitialSelection() {
        assertFalse(PlantingFlowPolicy.shouldWaitForStartAfterSelection(false, true));
        assertTrue(PlantingFlowPolicy.shouldWaitForStartAfterSelection(true, false));
        assertTrue(PlantingFlowPolicy.shouldWaitForStartAfterSelection(false, false));
    }

    @Test
    public void acceptsTheExplicitStartedNoticeOnThePlantingMap() {
        assertTrue(PlantingFlowPolicy.hasActiveMapEvidence(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                false,
                true,
                false,
                false));
    }

    @Test
    public void acceptsOnePlantingStatsHeaderAndBoostOnThePlantingMap() {
        assertTrue(PlantingFlowPolicy.hasActiveMapEvidence(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                false,
                false,
                true,
                true));
        assertFalse(PlantingFlowPolicy.hasActiveMapEvidence(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                false,
                false,
                true,
                false));
    }

    @Test
    public void doesNotTreatTheMenuOrStartControlAsActiveMapEvidence() {
        assertFalse(PlantingFlowPolicy.hasActiveMapEvidence(
                PlantingScreenAnalyzer.Screen.PLANTING_MENU,
                false,
                true,
                true,
                true));
        assertFalse(PlantingFlowPolicy.hasActiveMapEvidence(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                true,
                true,
                true,
                true));
    }

    @Test
    public void returnsToTheMenuAfterAConfirmedMapStart() {
        assertTrue(PlantingFlowPolicy.shouldReturnToMenuAfterConfirmedStart(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY));
        assertFalse(PlantingFlowPolicy.shouldReturnToMenuAfterConfirmedStart(
                PlantingScreenAnalyzer.Screen.PLANTING_MENU));
    }

    @Test
    public void monitoringUsesExactCurrentFlowerWhenHighlightIsUnavailable() {
        PetalMatcher.Selection current = new PetalMatcher.Selection(
                "紅色花瓣", 541, 480, 880, 800);
        PetalMatcher.Selection highlighted = new PetalMatcher.Selection(
                "黃色花瓣", 365, 280, 880, 800);

        assertEquals(current, PlantingFlowPolicy.monitoringSelection(null, current));
        assertEquals(highlighted,
                PlantingFlowPolicy.monitoringSelection(highlighted, current));
        assertNull(PlantingFlowPolicy.monitoringSelection(null, null));
    }

    @Test
    public void focusedMonitoringOcrStartsAfterTwoConsecutiveMisses() {
        assertFalse(PlantingFlowPolicy.shouldUseFocusedMonitorOcr(1));
        assertTrue(PlantingFlowPolicy.shouldUseFocusedMonitorOcr(2));
    }

    @Test
    public void searchesTheNextConfiguredFlowerAfterConfirmedLowCount() {
        PlantingFlowPolicy.LowCountDecision decision =
                PlantingFlowPolicy.afterConfirmedLowCount(
                        List.of("白色花瓣", "黃色花瓣"), "白色花瓣");

        assertEquals(PlantingFlowPolicy.LowCountAction.SEARCH_NEXT, decision.action());
        assertEquals("黃色花瓣", decision.nextFlower());
    }

    @Test
    public void stopsPlantingAfterTheLastConfiguredFlowerReachesTheLimit() {
        PlantingFlowPolicy.LowCountDecision decision =
                PlantingFlowPolicy.afterConfirmedLowCount(
                        List.of("白色花瓣", "黃色花瓣"), "黃色花瓣");

        assertEquals(PlantingFlowPolicy.LowCountAction.STOP_PLANTING, decision.action());
        assertNull(decision.nextFlower());
    }
}
