package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure coverage for preserving the selected Mushroom sub-tab during projection updates. */
public final class MushroomUiStateTest {
    @Test
    public void tabSurvivesScanAndMapStateUpdates() {
        MushroomUiState state = MushroomUiState.initial()
                .withTab(MushroomUiState.Tab.MAP)
                .withScan(
                        MushroomUiState.ScanStatus.RESULTS,
                        "found",
                        4L,
                        java.util.List.of(new MushroomUiState.Result(
                                "一般灰色蘑菇", "小型", 0.56f, 717, 663)));

        assertEquals(MushroomUiState.Tab.MAP, state.tab());
        assertEquals(MushroomUiState.ScanStatus.RESULTS, state.scanStatus());
        assertEquals(1, state.results().size());
    }

    @Test
    public void stoppedProjectionReplacesPreviousScanMessage() {
        MushroomUiState invalid = MushroomUiState.initial().withScan(
                MushroomUiState.ScanStatus.INVALID_PAGE,
                "請先進入蘑菇／地圖畫面",
                7L,
                java.util.List.of());

        MushroomUiState stopped = invalid.withScan(
                MushroomUiState.ScanStatus.STOPPED,
                "",
                7L,
                java.util.List.of());

        assertEquals(MushroomUiState.ScanStatus.STOPPED, stopped.scanStatus());
        assertEquals("", stopped.scanMessage());
        assertTrue(stopped.results().isEmpty());
    }

    @Test
    public void resultsCanBeMarkedStaleWithoutChangingTheImmutableResults() {
        MushroomUiState state = MushroomUiState.initial().withScan(
                MushroomUiState.ScanStatus.RESULTS,
                "found",
                7L,
                java.util.List.of(new MushroomUiState.Result(
                        "一般灰色蘑菇", "小型", 0.56f, 717, 663)));

        MushroomUiState stale = state.withResultsStale(true);

        assertTrue(stale.resultsStale());
        assertEquals(state.results(), stale.results());
        assertTrue(stale.withScan(
                MushroomUiState.ScanStatus.CAPTURING, "scanning", 7L,
                java.util.List.of()).resultsStale());
        assertFalse(stale.withScan(
                MushroomUiState.ScanStatus.RESULTS, "found again", 8L,
                stale.results()).resultsStale());
    }

    @Test
    public void anyNonTerminalScanStateMarksExistingResultsAsHistorical() {
        MushroomUiState state = MushroomUiState.initial().withScan(
                MushroomUiState.ScanStatus.RESULTS,
                "found",
                7L,
                java.util.List.of(new MushroomUiState.Result(
                        "一般灰色蘑菇", "小型", 0.56f, 717, 663)));

        assertTrue(state.withScan(
                MushroomUiState.ScanStatus.ANALYZING, "scanning", 7L,
                java.util.List.of()).resultsStale());
    }
}
