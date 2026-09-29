package com.pikminx.helper;

import java.util.List;
import java.util.function.IntBinaryOperator;

/** Immutable, bitmap-backed planting analysis performed away from the service state machine. */
final class H10aPlantingAnalysis {
    interface StageListener {
        void onStage(String stage);
    }

    /** Selects only the detector evidence required by the current planting sub-state. */
    enum SearchWork {
        NONE,
        NO_SEARCH_ANALYSIS,
        CLOSE_ONLY,
        PLANTING_AND_CLOSE,
        RESULT,
        PLANTING_AND_CLOSE_AND_SCREEN,
        /** Compatibility mode for callers that still use the former boolean constructor. */
        ALL
    }

    record Input(
            List<PetalMatcher.Token> tokens,
            List<String> allowedFlowers,
            int width,
            int height,
            String currentFlower,
            String targetFlower,
            int minimumCount,
            SearchWork searchWork,
            boolean selectionFromSearch,
            int targetSelectionX,
            int targetSelectionY) {
        Input {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
            allowedFlowers = allowedFlowers == null ? List.of() : List.copyOf(allowedFlowers);
            currentFlower = currentFlower == null ? "" : currentFlower;
            targetFlower = targetFlower == null ? "" : targetFlower;
            searchWork = searchWork == null ? SearchWork.NONE : searchWork;
        }

        Input(
                List<PetalMatcher.Token> tokens,
                List<String> allowedFlowers,
                int width,
                int height,
                String currentFlower,
                String targetFlower,
                int minimumCount,
                boolean searchStep,
                boolean selectionFromSearch,
                int targetSelectionX,
                int targetSelectionY) {
            this(
                    tokens,
                    allowedFlowers,
                    width,
                    height,
                    currentFlower,
                    targetFlower,
                    minimumCount,
                    searchStep ? SearchWork.ALL : SearchWork.NONE,
                    selectionFromSearch,
                    targetSelectionX,
                    targetSelectionY);
        }

        boolean searchStep() {
            return searchWork != SearchWork.NONE;
        }
    }

    record SearchControls(
            CardHighlight.Point searchButton,
            CardHighlight.Point plantingSearchButton,
            CardHighlight.Point closeButton,
            int searchResultsTop) {
        boolean searchOpen() {
            return closeButton != null;
        }
    }

    record Timings(
            long mapDetectorMillis,
            long pixelDetectorMillis,
            long imageSamplingMillis,
            long stateClassificationMillis) {}

    record Result(
            PlantingScreenAnalyzer.Detection plantingScreen,
            PetalMatcher.Token ocrStartControl,
            PetalMatcher.Selection highlighted,
            boolean visibleFlowerCard,
            PetalMatcher.Selection visibleCurrent,
            SearchControls searchControls,
            PetalMatcher.Selection searchedFlower,
            boolean targetSelectionHighlighted,
            Timings timings) {}

    private H10aPlantingAnalysis() {}

    static Result analyze(Input input, IntBinaryOperator pixelAt) {
        return analyze(input, pixelAt, stage -> { });
    }

    static Result analyze(
            Input input, IntBinaryOperator pixelAt, StageListener stageListener) {
        if (input == null || pixelAt == null) {
            throw new IllegalArgumentException("Analysis input and pixel reader are required");
        }
        StageListener listener = stageListener == null ? stage -> { } : stageListener;
        long imageStartedAt = System.nanoTime();
        boolean fullLegacySearch = input.searchWork() == SearchWork.ALL;
        boolean normalWork = !input.searchStep();
        boolean needsPlantingScreen = normalWork
                || fullLegacySearch
                || input.searchWork() == SearchWork.PLANTING_AND_CLOSE_AND_SCREEN;
        boolean needsStateClassification = normalWork || fullLegacySearch;
        PetalMatcher.TokenKeyIndex tokenIndex = null;
        PlantingScreenAnalyzer.Detection plantingScreen = null;
        PetalMatcher.Token ocrStartControl = null;
        boolean visibleFlowerCard = false;
        long mapDetectorMillis = 0L;
        long stateClassificationMillis = 0L;

        if (normalWork || fullLegacySearch) {
            listener.onStage("token-keys");
            tokenIndex = PetalMatcher.indexTokens(input.tokens());
        }
        if (needsPlantingScreen) {
            listener.onStage("map");
            long mapStartedAt = System.nanoTime();
            plantingScreen = PlantingScreenAnalyzer.analyze(
                    input.tokens(),
                    input.allowedFlowers(),
                    input.width(),
                    input.height(),
                    pixelAt);
            mapDetectorMillis = elapsedMillis(mapStartedAt);
        }

        PetalMatcher.Selection highlighted = null;
        PetalMatcher.Selection visibleCurrent = null;
        if (needsStateClassification) {
            listener.onStage("state");
            long stateStartedAt = System.nanoTime();
            ocrStartControl = PetalMatcher.findStartPlantingControl(
                    input.tokens(), input.width(), input.height());
            visibleFlowerCard = PetalMatcher.hasVisibleFlowerCard(
                    tokenIndex, PetalCatalog.petals(), input.width(), input.height());
            stateClassificationMillis = elapsedMillis(stateStartedAt);
        }

        if (normalWork) {
            listener.onStage("highlight");
            long pixelStartedAt = System.nanoTime();
            highlighted = PetalMatcher.findHighlightedFlower(
                    tokenIndex,
                    PetalCatalog.petals(),
                    input.width(),
                    input.height(),
                    flower -> CardHighlight.score(
                            input.width(),
                            input.height(),
                            flower.x(),
                            flower.y(),
                            pixelAt));
            if (highlighted == null && !input.currentFlower().isEmpty()) {
                visibleCurrent = PetalMatcher.findFlower(
                        tokenIndex,
                        input.currentFlower(),
                        input.width(),
                        input.height());
            }
            boolean targetSelectionHighlighted = targetSelectionHighlighted(input, pixelAt);
            // Keep the sub-stage visible to diagnostics without retaining a bitmap.
            long pixelDetectorMillis = elapsedMillis(pixelStartedAt);
            long imageSamplingMillis = elapsedMillis(imageStartedAt);
            return new Result(
                    plantingScreen,
                    ocrStartControl,
                    highlighted,
                    visibleFlowerCard,
                    visibleCurrent,
                    null,
                    null,
                    targetSelectionHighlighted,
                    new Timings(
                            mapDetectorMillis,
                            pixelDetectorMillis,
                            imageSamplingMillis,
                    stateClassificationMillis));
        }

        if (input.searchWork() == SearchWork.NO_SEARCH_ANALYSIS) {
            return new Result(
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    false,
                    new Timings(
                            0L,
                            0L,
                            elapsedMillis(imageStartedAt),
                            0L));
        }

        listener.onStage("controls");
        long controlsStartedAt = System.nanoTime();
        CardHighlight.PetalSearchAnalysis controls = CardHighlight.analyzePetalSearchControls(
                input.width(), input.height(), pixelAt);
        CardHighlight.Point searchButton = null;
        CardHighlight.Point plantingSearchButton = null;
        CardHighlight.Point closeButton = null;
        if (fullLegacySearch) {
            searchButton = controls.searchButton();
            plantingSearchButton = controls.plantingSearchButton();
            closeButton = controls.closeButton();
        } else if (input.searchWork() == SearchWork.PLANTING_AND_CLOSE_AND_SCREEN) {
            plantingSearchButton = controls.plantingSearchButton();
            closeButton = controls.closeButton();
        } else if (input.searchWork() == SearchWork.PLANTING_AND_CLOSE) {
            // An already-open search panel is sufficient evidence to skip the button scan.
            closeButton = controls.closeButton();
            if (closeButton == null) {
                plantingSearchButton = controls.plantingSearchButton();
            }
        } else if (input.searchWork() == SearchWork.CLOSE_ONLY
                || input.searchWork() == SearchWork.RESULT) {
            closeButton = controls.closeButton();
        }
        int searchResultsTop = closeButton == null
                ? -1
                : Math.min(
                        input.height() - 1,
                        closeButton.y() + Math.round(input.height() * 0.03f));
        PetalMatcher.Selection searchedFlower = null;
        if (fullLegacySearch || input.searchWork() == SearchWork.RESULT) {
            listener.onStage("search-result");
            searchedFlower = searchResultsTop < 0
                    ? null
                    : PetalMatcher.findSearchedFlower(
                            input.tokens(),
                            input.targetFlower(),
                            input.minimumCount(),
                            input.width(),
                            input.height(),
                            searchResultsTop);
        }
        long pixelDetectorMillis = elapsedMillis(controlsStartedAt);
        long imageSamplingMillis = elapsedMillis(imageStartedAt);
        return new Result(
                plantingScreen,
                ocrStartControl,
                highlighted,
                visibleFlowerCard,
                visibleCurrent,
                new SearchControls(
                        searchButton,
                        plantingSearchButton,
                        closeButton,
                        searchResultsTop),
                searchedFlower,
                false,
                new Timings(
                                mapDetectorMillis,
                                pixelDetectorMillis,
                                imageSamplingMillis,
                                stateClassificationMillis));
    }

    private static boolean targetSelectionHighlighted(Input input, IntBinaryOperator pixelAt) {
        return input.selectionFromSearch()
                && input.targetSelectionX() > 0
                && input.targetSelectionY() > 0
                && CardHighlight.score(
                        input.width(),
                        input.height(),
                        input.targetSelectionX(),
                        input.targetSelectionY(),
                        pixelAt) >= 245;
    }

    private static long elapsedMillis(long startedAtNanos) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
        return Math.max(0L, elapsedNanos / 1_000_000L);
    }
}
