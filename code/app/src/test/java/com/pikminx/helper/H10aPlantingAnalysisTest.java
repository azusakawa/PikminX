package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public final class H10aPlantingAnalysisTest {
    @Test
    public void inputSnapshotsMutableEvidenceBeforeWorkerRuns() {
        List<PetalMatcher.Token> tokens = new ArrayList<>();
        tokens.add(new PetalMatcher.Token("花", 1, 2, 20, 30));
        List<String> flowers = new ArrayList<>();
        flowers.add("紅色花瓣");

        H10aPlantingAnalysis.Input input = new H10aPlantingAnalysis.Input(
                tokens, flowers, 100, 100, "目前", "目標", 2,
                false, false, 0, 0);
        tokens.clear();
        flowers.clear();

        assertEquals(1, input.tokens().size());
        assertEquals(List.of("紅色花瓣"), input.allowedFlowers());
    }

    @Test
    public void workerResultContainsOnlyImmutableAnalysisAndFiniteTimings() {
        H10aPlantingAnalysis.Input input = new H10aPlantingAnalysis.Input(
                List.of(), List.of("紅色花瓣"), 100, 100, "", "", 0,
                false, false, 0, 0);

        H10aPlantingAnalysis.Result result = H10aPlantingAnalysis.analyze(
                input, (x, y) -> 0xFFFFFFFF);

        assertNotNull(result);
        assertNotNull(result.plantingScreen());
        assertTrue(result.timings().mapDetectorMillis() >= 0L);
        assertTrue(result.timings().pixelDetectorMillis() >= 0L);
        assertTrue(result.timings().imageSamplingMillis() >= 0L);
        assertTrue(result.timings().stateClassificationMillis() >= 0L);
    }

    @Test
    public void resultSearchWorkSkipsUnusedPlantingAndGenericSearchStages() {
        List<String> stages = new ArrayList<>();
        H10aPlantingAnalysis.Input input = new H10aPlantingAnalysis.Input(
                List.of(), List.of("紅色花瓣"), 100, 100, "", "紅色花瓣", 0,
                H10aPlantingAnalysis.SearchWork.RESULT,
                false, 0, 0);

        H10aPlantingAnalysis.Result result = H10aPlantingAnalysis.analyze(
                input, (x, y) -> 0xFFFFFFFF, stages::add);

        assertEquals(List.of("controls", "search-result"), stages);
        assertNull(result.plantingScreen());
        assertNotNull(result.searchControls());
        assertNull(result.searchControls().searchButton());
        assertNull(result.searchControls().plantingSearchButton());
    }

    @Test
    public void searchStateWithoutRequiredEvidenceDoesNoDetectorWork() {
        List<String> stages = new ArrayList<>();
        H10aPlantingAnalysis.Input input = new H10aPlantingAnalysis.Input(
                List.of(), List.of(), 100, 100, "", "", 0,
                H10aPlantingAnalysis.SearchWork.NO_SEARCH_ANALYSIS,
                false, 0, 0);

        H10aPlantingAnalysis.Result result = H10aPlantingAnalysis.analyze(
                input, (x, y) -> 0xFFFFFFFF, stages::add);

        assertEquals(List.of(), stages);
        assertNull(result.plantingScreen());
        assertNull(result.searchControls());
        assertNull(result.searchedFlower());
    }
}
