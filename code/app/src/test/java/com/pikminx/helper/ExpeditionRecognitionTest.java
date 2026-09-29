package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Xiaomi evidence-derived admission regression; no gameplay acceptance is implied. */
public final class ExpeditionRecognitionTest {
    @Test
    public void admitsOnlyConservativeObservedFruitAndSeedlingRanges() {
        ExpeditionRecognition.Scores apple = new ExpeditionRecognition.Scores(
                "FRUIT", "large_green_apple.png", 0.8539, "small_lime.png", 0.7311);
        ExpeditionRecognition.Scores weakSeedling = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_blue.png", 0.5092, "seedling_black.png", 0.3498);
        assertEquals(ExpeditionRecognition.Classification.FRUIT,
                ExpeditionRecognition.visual(apple, weakSeedling));

        ExpeditionRecognition.Scores huge = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_huge.png", 0.8186, "seedling_white.png", 0.6928);
        ExpeditionRecognition.Scores weakFruit = new ExpeditionRecognition.Scores(
                "FRUIT", "giant_melon.png", 0.6167, "large_lemon.png", 0.5722);
        assertEquals(ExpeditionRecognition.Classification.SEEDLING,
                ExpeditionRecognition.visual(weakFruit, huge));
        ExpeditionRecognition.Scores red = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_red.png", 0.9022306403, "seedling_yellow.png", 0.7516140988);
        assertEquals(ExpeditionRecognition.Classification.SEEDLING,
                ExpeditionRecognition.visual(weakFruit, red));
        ExpeditionRecognition.Scores white = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_white.png", 0.802, "seedling_pink.png", 0.672);
        assertEquals(ExpeditionRecognition.Classification.SEEDLING,
                ExpeditionRecognition.visual(weakFruit, white));
        ExpeditionRecognition.Scores blue = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_blue.png", 0.862, "seedling_purple.png", 0.745);
        assertEquals(ExpeditionRecognition.Classification.SEEDLING,
                ExpeditionRecognition.visual(weakFruit, blue));
        ExpeditionRecognition.Scores black = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_black.png", 0.803, "seedling_red.png", 0.518);
        assertEquals(ExpeditionRecognition.Classification.SEEDLING,
                ExpeditionRecognition.visual(weakFruit, black));
        ExpeditionRecognition.Scores appleLikeGreyPot = new ExpeditionRecognition.Scores(
                "FRUIT", "large_green_apple.png", 0.862, "small_lime.png", 0.713);
        ExpeditionRecognition.Scores greyPot = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_black.png", 0.753, "seedling_red.png", 0.509);
        assertEquals(ExpeditionRecognition.Classification.AMBIGUOUS,
                ExpeditionRecognition.visual(appleLikeGreyPot, greyPot));
        assertTrue(ExpeditionRecognition.requiresTitleOcr(
                ExpeditionRecognition.Classification.FRUIT, appleLikeGreyPot));
        assertTrue(ExpeditionRecognition.requiresTitleOcr(
                ExpeditionRecognition.Classification.SEEDLING, appleLikeGreyPot));
    }

    @Test
    public void greenAppleTitleOcrSurvivesCrossBankVisualClassification() {
        ExpeditionRecognition.Scores fruit = new ExpeditionRecognition.Scores(
                "FRUIT", "large_green_apple.png", 0.82, "small_lime.png", 0.71);

        assertTrue(ExpeditionRecognition.requiresTitleOcr(
                ExpeditionRecognition.Classification.SEEDLING, fruit));
        assertTrue(ExpeditionRecognition.requiresTitleOcr(
                ExpeditionRecognition.Classification.UNKNOWN, fruit));
    }

    @Test
    public void rejectsObservedIceAndActiveCardRanges() {
        ExpeditionRecognition.Scores ice = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_blue.png", 0.8115774405, "seedling_huge.png", 0.6068518481);
        ExpeditionRecognition.Scores neutralFruit = new ExpeditionRecognition.Scores(
                "FRUIT", "small_blueberry.png", 0.4588, "large_plum.png", 0.4525);
        assertEquals(ExpeditionRecognition.Classification.UNKNOWN,
                ExpeditionRecognition.visual(neutralFruit, ice));

        ExpeditionRecognition.Scores activeCard = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_blue.png", 0.7980, "seedling_purple.png", 0.5332);
        assertEquals(ExpeditionRecognition.Classification.UNKNOWN,
                ExpeditionRecognition.visual(neutralFruit, activeCard));
    }

    @Test
    public void keepsCrossBankNearTieAmbiguous() {
        ExpeditionRecognition.Scores fruit = new ExpeditionRecognition.Scores(
                "FRUIT", "large_green_apple.png", 0.86, "small_lime.png", 0.80);
        ExpeditionRecognition.Scores seedling = new ExpeditionRecognition.Scores(
                "SEEDLING", "seedling_huge.png", 0.85, "seedling_white.png", 0.72);
        assertEquals(ExpeditionRecognition.Classification.AMBIGUOUS,
                ExpeditionRecognition.visual(fruit, seedling));
    }
}
