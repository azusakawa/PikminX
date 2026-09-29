package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 驗證 OCR token 與花盆右下角剩餘數量的配對規則。 */
public final class PetalMatcherTest {
    @Test
    public void searchedBlueAnemoneAcceptsObservedBasketColorOcrAndKeepsLowCount() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("3", 369, 1891, 388, 1920),
                new PetalMatcher.Token("籃色銀蓮花", 136, 2027, 362, 2071));
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                tokens, "藍色銀蓮花", 0, 1280, 2772, 965);
        assertNotNull(selection);
        assertEquals("藍色銀蓮花", selection.name());
        assertEquals(3, selection.count());
        assertTrue(PlantingFlowPolicy.shouldSkipCandidate(selection.count(), 1200));
        assertNull(PetalMatcher.findSearchedFlower(
                tokens, "紅色銀蓮花", 0, 1280, 2772, 965));
        assertNull(PetalMatcher.findSearchedFlower(
                tokens, "藍色花瓣", 0, 1280, 2772, 965));
    }

    @Test
    public void correctedBlueColorStillRequiresCountInTheSameCard() {
        assertNull(PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("3", 800, 1891, 830, 1920),
                        new PetalMatcher.Token("籃色銀蓮花", 136, 2027, 362, 2071)),
                "藍色銀蓮花", 0, 1280, 2772, 965));
    }

    @Test
    public void plantingSearchSkipsScrollOnlyForFourBasicPots() {
        for (String flower : PetalCatalog.petals()) {
            boolean basic = List.of("白色花瓣", "黃色花瓣", "紅色花瓣", "藍色花瓣")
                    .contains(flower);
            assertEquals(flower, basic ? 0 : 3,
                    PetalMatcher.plantingSearchScrollCount(flower));
        }
        for (String basic : List.of("白色花瓣", "黃色花瓣", "紅色花瓣", "藍色花瓣")) {
            assertEquals(0, PetalMatcher.plantingSearchScrollCount(basic));
        }
        assertEquals(3, PetalMatcher.plantingSearchScrollCount(null));
        assertEquals(3, PetalMatcher.plantingSearchScrollCount("未知花瓣"));
    }

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1520;
    private static final List<PetalMatcher.Token> TOKENS = List.of(
            new PetalMatcher.Token("268", 410, 830, 462, 880),
            new PetalMatcher.Token("+10%", 330, 970, 390, 1010),
            new PetalMatcher.Token("4500", 250, 1015, 320, 1055),
            new PetalMatcher.Token("53", 430, 1015, 470, 1055),
            new PetalMatcher.Token("268", 175, 1260, 225, 1305),
            new PetalMatcher.Token("White Petals", 55, 1330, 240, 1380),
            new PetalMatcher.Token("251", 335, 1260, 385, 1305),
            new PetalMatcher.Token("Yellow Petals", 275, 1330, 455, 1380),
            new PetalMatcher.Token("0", 565, 1260, 600, 1305),
            new PetalMatcher.Token("Red Petals", 510, 1330, 680, 1380));
    private static final int CURRENT_WIDTH = 591;
    private static final int CURRENT_HEIGHT = 1280;
    private static final List<PetalMatcher.Token> CURRENT_ZH_TW_TOKENS = List.of(
            new PetalMatcher.Token("231", 334, 263, 376, 296),
            new PetalMatcher.Token("3900", 175, 329, 241, 357),
            new PetalMatcher.Token("38", 405, 329, 438, 357),
            new PetalMatcher.Token("92", 70, 660, 98, 690),
            new PetalMatcher.Token("1,200 +", 91, 791, 185, 831),
            new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902),
            new PetalMatcher.Token("78", 244, 660, 274, 690),
            new PetalMatcher.Token("231 +", 290, 791, 367, 831),
            new PetalMatcher.Token("黃色花瓣", 244, 862, 357, 902),
            new PetalMatcher.Token("1,055 +", 451, 791, 549, 831),
            new PetalMatcher.Token("紅色花瓣", 432, 862, 548, 902));

    @Test
    public void followsPriorityAndSkipsCurrentFlower() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                TOKENS,
                List.of("White Petals", "Yellow Petals", "Red Petals"),
                "White Petals",
                50,
                WIDTH,
                HEIGHT);
        assertEquals("Yellow Petals", selection.name());
        assertEquals(251, selection.count());
        assertTrue(selection.tapY() < selection.y());
    }

    @Test
    public void doesNotWrapBackToEarlierFlower() {
        assertNull(PetalMatcher.findFlower(
                TOKENS,
                List.of("White Petals", "Yellow Petals", "Red Petals"),
                "Yellow Petals",
                50,
                WIDTH,
                HEIGHT));
    }

    @Test
    public void rejectsCandidateBelowThreshold() {
        assertNull(PetalMatcher.findFlower(
                TOKENS,
                List.of("Red Petals"),
                "",
                50,
                WIDTH,
                HEIGHT));
    }

    @Test
    public void readsCommaCountBesidePlusButtonInCurrentZhTwCards() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                CURRENT_ZH_TW_TOKENS,
                List.of("白色花瓣", "紅色花瓣"),
                "",
                50,
                CURRENT_WIDTH,
                CURRENT_HEIGHT);
        assertEquals("白色花瓣", selection.name());
        assertEquals(1200, selection.count());
    }

    @Test
    public void readsCommonOcrThousandsSeparators() {
        for (String countText : List.of("1,200 +", "1.200 +", "1 200 +", "１，２００ ＋")) {
            PetalMatcher.Selection selection = PetalMatcher.findFlower(
                    List.of(
                            new PetalMatcher.Token(countText, 91, 791, 185, 831),
                            new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902)),
                    List.of("白色花瓣"),
                    "",
                    50,
                    CURRENT_WIDTH,
                    CURRENT_HEIGHT);
            assertEquals("OCR text: " + countText, 1200, selection.count());
        }
    }

    @Test
    public void doesNotMatchAFlowerNameContainedInsideALongerLabel() {
        assertNull(PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("321", 91, 791, 185, 831),
                        new PetalMatcher.Token("White Petals Premium", 69, 862, 210, 902)),
                "White Petals",
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void rejectsAFlowerNameSplitAcrossTwoOcrLinesLikePostcardMatching() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("321", 91, 791, 185, 831),
                        new PetalMatcher.Token("白色", 69, 850, 183, 880),
                        new PetalMatcher.Token("蝴蝶蘭", 69, 882, 183, 912)),
                "白色蝴蝶蘭",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNull(selection);
    }

    @Test
    public void appliesTheSameSingleTokenOcrCorrectionAsPostcardMatching() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("321", 91, 791, 185, 831),
                        new PetalMatcher.Token("紅色夭堂島", 69, 862, 183, 902)),
                "紅色天堂鳥",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals("紅色天堂鳥", selection.name());
        assertEquals(321, selection.count());
    }

    @Test
    public void doesNotBorrowACountFromAnAdjacentCardColumn() {
        assertNull(PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("999", 220, 791, 280, 831),
                        new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902)),
                "白色花瓣",
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void readsCountOnlyFromTheCardBandAboveItsFlowerName() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("686", 91, 650, 185, 690),
                        new PetalMatcher.Token("999 +", 91, 791, 185, 831),
                        new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902)),
                "白色花瓣",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals(999, selection.count());
    }

    @Test
    public void rejectsInventoryNumberWhenCardCountIsMissing() {
        assertNull(PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("686", 91, 650, 185, 690),
                        new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902)),
                "白色花瓣",
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void findsOnlyAnExplicitStartPlantingLabelAboveTheCardGrid() {
        PetalMatcher.Token start = PetalMatcher.findStartPlantingControl(
                List.of(
                        new PetalMatcher.Token("開始種花", 250, 250, 350, 300),
                        new PetalMatcher.Token("開始種花", 250, 900, 350, 950),
                        new PetalMatcher.Token("開始搜尋", 400, 250, 500, 300)),
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNotNull(start);
        assertEquals(275, start.centerY());
    }

    @Test
    public void requiresARecognizedFlowerCardBeforeMenuAutomation() {
        assertTrue(PetalMatcher.hasVisibleFlowerCard(
                CURRENT_ZH_TW_TOKENS,
                PetalCatalog.petals(),
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
        assertFalse(PetalMatcher.hasVisibleFlowerCard(
                List.of(new PetalMatcher.Token("開始種花", 250, 250, 350, 300)),
                PetalCatalog.petals(),
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void indexedFrameKeysPreserveFlowerMatchingResults() {
        PetalMatcher.TokenKeyIndex index = PetalMatcher.indexTokens(CURRENT_ZH_TW_TOKENS);
        PetalMatcher.Selection legacy = PetalMatcher.findFlower(
                CURRENT_ZH_TW_TOKENS,
                "白色花瓣",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);
        PetalMatcher.Selection indexed = PetalMatcher.findFlower(
                index,
                "白色花瓣",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertEquals(legacy, indexed);
        assertEquals(
                PetalMatcher.hasVisibleFlowerCard(
                        CURRENT_ZH_TW_TOKENS,
                        PetalCatalog.petals(),
                        CURRENT_WIDTH,
                        CURRENT_HEIGHT),
                PetalMatcher.hasVisibleFlowerCard(
                        index,
                        PetalCatalog.petals(),
                        CURRENT_WIDTH,
                        CURRENT_HEIGHT));
        assertEquals(
                PetalMatcher.findHighlightedFlower(
                        CURRENT_ZH_TW_TOKENS,
                        List.of("白色花瓣", "黃色花瓣", "紅色花瓣"),
                        CURRENT_WIDTH,
                        CURRENT_HEIGHT,
                        flower -> flower.name().equals("白色花瓣") ? 255 : 234),
                PetalMatcher.findHighlightedFlower(
                        index,
                        List.of("白色花瓣", "黃色花瓣", "紅色花瓣"),
                        CURRENT_WIDTH,
                        CURRENT_HEIGHT,
                        flower -> flower.name().equals("白色花瓣") ? 255 : 234));
    }

    @Test
    public void readsRemainingOnlyFromHighlightedCard() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("80", 334, 263, 376, 296),
                new PetalMatcher.Token("1,080 +", 91, 791, 185, 831),
                new PetalMatcher.Token("白色花瓣", 69, 862, 183, 902),
                new PetalMatcher.Token("17 +", 290, 791, 367, 831),
                new PetalMatcher.Token("黃色花瓣", 244, 862, 357, 902));

        PetalMatcher.Selection selected = PetalMatcher.findHighlightedFlower(
                tokens,
                List.of("白色花瓣", "黃色花瓣"),
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                flower -> flower.name().equals("白色花瓣") ? 255 : 234);

        assertEquals("白色花瓣", selected.name());
        assertEquals(1080, selected.count());
    }

    @Test
    public void readsPotRemainingInsteadOfNectarAtTopLeft() {
        PetalMatcher.Selection selection = PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("363", 70, 660, 110, 720),
                        new PetalMatcher.Token("43 +", 180, 800, 230, 862),
                        new PetalMatcher.Token("White Petals", 69, 862, 183, 902)),
                "White Petals",
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals(43, selection.count());
        assertNull(PetalMatcher.findFlower(
                List.of(
                        new PetalMatcher.Token("363", 70, 660, 110, 720),
                        new PetalMatcher.Token("White Petals", 69, 862, 183, 902)),
                "White Petals",
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void recognizesHighPriorityFlowerInUpperUnscrolledCardRow() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("1,200 +", 91, 438, 185, 478),
                new PetalMatcher.Token("白色花瓣", 69, 500, 183, 540));

        PetalMatcher.Selection first = PetalMatcher.findFlower(
                tokens,
                List.of("白色花瓣", "黃色花瓣"),
                "",
                50,
                CURRENT_WIDTH,
                CURRENT_HEIGHT);
        PetalMatcher.Selection highlighted = PetalMatcher.findHighlightedFlower(
                tokens,
                List.of("白色花瓣", "黃色花瓣"),
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                flower -> 255);

        assertNotNull(first);
        assertEquals("白色花瓣", first.name());
        assertEquals(1200, first.count());
        assertNotNull(highlighted);
        assertEquals("白色花瓣", highlighted.name());
    }

    @Test
    public void initialSelectionAlwaysUsesFirstConfiguredFlower() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("20 +", 91, 791, 185, 831),
                new PetalMatcher.Token("黃色勿忘草", 69, 862, 183, 902),
                new PetalMatcher.Token("900 +", 290, 791, 367, 831),
                new PetalMatcher.Token("白色百合", 244, 862, 357, 902));

        PetalMatcher.Selection selection = PetalMatcher.findInitialFlower(
                tokens,
                List.of("黃色勿忘草", "白色百合"),
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals("黃色勿忘草", selection.name());
        assertEquals(20, selection.count());
    }

    @Test
    public void searchedFlowerSelectsTheExactTargetAmongSpeciesResultsAndPinnedCurrentPot() {
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("1,040 +", 55, 791, 145, 831),
                        new PetalMatcher.Token("白色美人蕉", 45, 862, 155, 902),
                        new PetalMatcher.Token("693 +", 250, 791, 340, 831),
                        new PetalMatcher.Token("黃色美人蕉", 240, 862, 350, 902),
                        new PetalMatcher.Token("1,200 +", 440, 791, 530, 831),
                        new PetalMatcher.Token("紅色美人蕉", 430, 862, 540, 902),
                        new PetalMatcher.Token("601 +", 55, 1015, 145, 1055),
                        new PetalMatcher.Token("黃色花瓣", 45, 1090, 155, 1130)),
                "白色美人蕉",
                0,
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                0);

        assertNotNull(selection);
        assertEquals("白色美人蕉", selection.name());
        assertEquals(1040, selection.count());
    }

    @Test
    public void searchedFlowerAcceptsUpperFocusedRoiOn432By936Screen() {
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("572", 274, 382, 326, 414),
                        new PetalMatcher.Token("紅色雞冠花", 42, 432, 166, 464),
                        new PetalMatcher.Token("329", 76, 382, 128, 414),
                        new PetalMatcher.Token("黃色雞冠花", 238, 432, 362, 464)),
                "紅色雞冠花",
                0,
                432,
                936,
                0);

        assertNotNull(selection);
        assertEquals("紅色雞冠花", selection.name());
        assertEquals(329, selection.count());
    }

    @Test
    public void searchedFlowerUsesAreaBelowSearchBox() {
        List<PetalMatcher.Token> accepted = List.of(
                new PetalMatcher.Token("329", 70, 190, 120, 220),
                new PetalMatcher.Token("紅色雞冠花", 40, 230, 160, 262));
        List<PetalMatcher.Token> tooFarLeft = List.of(
                new PetalMatcher.Token("329", 5, 190, 45, 220),
                new PetalMatcher.Token("紅色雞冠花", 0, 230, 50, 262));
        List<PetalMatcher.Token> tooHigh = List.of(
                new PetalMatcher.Token("329", 70, 145, 120, 175),
                new PetalMatcher.Token("紅色雞冠花", 40, 185, 160, 215));
        List<PetalMatcher.Token> tooLow = List.of(
                new PetalMatcher.Token("329", 70, 775, 120, 805),
                new PetalMatcher.Token("紅色雞冠花", 40, 820, 160, 850));

        assertNotNull(PetalMatcher.findSearchedFlower(
                accepted, "紅色雞冠花", 0, 432, 936, 220));
        assertNotNull(PetalMatcher.findSearchedFlower(
                tooFarLeft, "紅色雞冠花", 0, 432, 936, 220));
        assertNull(PetalMatcher.findSearchedFlower(
                tooHigh, "紅色雞冠花", 0, 432, 936, 220));
        assertNotNull(PetalMatcher.findSearchedFlower(
                tooLow, "紅色雞冠花", 0, 432, 936, 220));
    }

    @Test
    public void searchedFlowerAcceptsLiveResultNearBottomOfFocusedRoi() {
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("1,103 +", 232, 1581, 396, 1620),
                        new PetalMatcher.Token("白色花瓣", 164, 1721, 340, 1764),
                        new PetalMatcher.Token("560", 328, 2243, 395, 2271),
                        new PetalMatcher.Token("白色百合", 163, 2376, 336, 2423)),
                "白色百合",
                0,
                1280,
                2772,
                960);

        assertNotNull(selection);
        assertEquals("白色百合", selection.name());
        assertEquals(560, selection.count());
    }

    @Test
    public void searchedFlowerAcceptsUniqueObservedOcrNameErrors() {
        PetalMatcher.Selection hibiscus = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("707", 296, 1889, 393, 1923),
                        new PetalMatcher.Token("紅色抉桑花", 136, 2028, 362, 2071)),
                "紅色扶桑花", 0, 1280, 2772, 950);
        PetalMatcher.Selection anniversaryRose = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("707", 720, 1891, 784, 1920),
                        new PetalMatcher.Token("黄色遇年紀念攻瑰", 459, 2027, 823, 2071)),
                "黃色週年紀念玫瑰", 0, 1280, 2772, 950);

        assertNotNull(hibiscus);
        assertEquals("紅色扶桑花", hibiscus.name());
        assertNotNull(anniversaryRose);
        assertEquals("黃色週年紀念玫瑰", anniversaryRose.name());
    }

    @Test
    public void searchedFlowerUsesTargetSegmentFromMergedHorizontalLabels() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("560", 328, 1891, 393, 1920),
                new PetalMatcher.Token("707", 720, 1891, 785, 1920),
                new PetalMatcher.Token(
                        "白色百合 |黄色過年紀念攻瑰", 163, 2027, 823, 2071));

        PetalMatcher.Selection whiteLily = PetalMatcher.findSearchedFlower(
                tokens, "白色百合", 0, 1280, 2772, 965);
        PetalMatcher.Selection anniversaryRose = PetalMatcher.findSearchedFlower(
                tokens, "黃色週年紀念玫瑰", 0, 1280, 2772, 965);

        assertNotNull(whiteLily);
        assertEquals(560, whiteLily.count());
        assertNotNull(anniversaryRose);
        assertEquals(707, anniversaryRose.count());
    }

    @Test
    public void indexedSearchResultDetectorPreservesLegacyOutputForExpandedLabels() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("560", 328, 1891, 393, 1920),
                new PetalMatcher.Token("707", 720, 1891, 785, 1920),
                new PetalMatcher.Token(
                        "白色百合 |黄色過年紀念攻瑰", 163, 2027, 823, 2071));
        String target = "黃色週年紀念玫瑰";
        String targetKey = PetalMatcher.flowerNameKey(target);
        PetalMatcher.TokenKeyIndex indexedTokens = PetalMatcher.searchableFlowerTokens(
                tokens, targetKey);

        PetalPotDetector.Match legacy = PetalPotDetector.find(
                indexedTokens.tokens(),
                target,
                0,
                1280,
                2772,
                965f / 2772f,
                1f,
                0.15f,
                0.085f,
                PetalMatcher::flowerNameKey);
        PetalPotDetector.Match indexed = PetalPotDetector.findWithKeys(
                indexedTokens.tokens(),
                indexedTokens.keys(),
                targetKey,
                0,
                1280,
                2772,
                965f / 2772f,
                1f,
                0.15f,
                0.085f);

        assertEquals(legacy, indexed);
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                tokens, target, 0, 1280, 2772, 965);
        assertNotNull(selection);
        assertEquals(target, selection.name());
        assertEquals(indexed.count(), selection.count());
        assertEquals(indexed.x(), selection.x());
        assertEquals(indexed.labelY(), selection.y());
        assertEquals(
                Math.max(0, indexed.labelTop() - Math.round(2772 * 0.075f)),
                selection.tapY());
    }

    @Test
    public void searchedFlowerParsesPlusSuffixAndSearchWhitespace() {
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("329 +", 76, 382, 132, 414),
                        new PetalMatcher.Token("紅色雞冠花", 42, 432, 166, 464)),
                "紅色 雞冠花",
                0,
                432,
                936,
                0);

        assertNotNull(selection);
        assertEquals("紅色雞冠花", selection.name());
        assertEquals(329, selection.count());
    }

    @Test
    public void basicPetalColorSearchIgnoresOtherWhitePotsAndPinnedCurrentPot() {
        PetalMatcher.Selection selection = PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("170 +", 55, 791, 145, 831),
                        new PetalMatcher.Token("白色花瓣", 45, 862, 155, 902),
                        new PetalMatcher.Token("1,040 +", 250, 791, 340, 831),
                        new PetalMatcher.Token("白色美人蕉", 240, 862, 350, 902),
                        new PetalMatcher.Token("602 +", 440, 791, 530, 831),
                        new PetalMatcher.Token("白色扶桑花", 430, 862, 540, 902),
                        new PetalMatcher.Token("632 +", 55, 1015, 145, 1055),
                        new PetalMatcher.Token("黃色花瓣", 45, 1090, 155, 1130)),
                "白色花瓣",
                0,
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                0);

        assertNotNull(selection);
        assertEquals("白色花瓣", selection.name());
        assertEquals(170, selection.count());
    }

    @Test
    public void searchedNextFlowerStillRequiresMoreThanTheSwitchThreshold() {
        assertNull(PetalMatcher.findSearchedFlower(
                List.of(
                        new PetalMatcher.Token("50 +", 91, 791, 185, 831),
                        new PetalMatcher.Token("黃色蝴蝶蘭", 69, 862, 183, 902)),
                "黃色蝴蝶蘭",
                51,
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                0));
    }

    @Test
    public void detectsWhenHighlightedPotNoLongerMatchesExpectedFlower() {
        PetalMatcher.Selection white = new PetalMatcher.Selection(
                "白色花瓣", 240, 120, 900, 820);
        PetalMatcher.Selection yellowForgetMeNot = new PetalMatcher.Selection(
                "黃色勿忘草", 1140, 120, 900, 820);

        assertTrue(PetalMatcher.needsSelectionCorrection("黃色勿忘草", white));
        assertFalse(PetalMatcher.needsSelectionCorrection(
                "黃色勿忘草", yellowForgetMeNot));
    }

    @Test
    public void selectedCardBackgroundHasClearContrast() {
        int selected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                122,
                884,
                (x, y) -> x < 210 ? 0xffffffff : 0xffeaf8f9);
        int unselected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                300,
                884,
                (x, y) -> x < 210 ? 0xffffffff : 0xffeaf8f9);

        assertTrue(selected >= unselected + 10);
    }

    @Test
    public void selectedCardIgnoresLabelShadowBelowItsBackground() {
        int centerY = 884;
        int selected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                122,
                centerY,
                (x, y) -> y < centerY ? 0xffffffff : 0xffcdcdcd);
        int unselected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                300,
                centerY,
                (x, y) -> 0xfff0fcf5);

        assertTrue(selected >= unselected + 10);
    }

    @Test
    public void unselectedCardDoesNotBorrowAdjacentSelectedBackground() {
        int selectedCenterX = 300;
        int selected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                selectedCenterX,
                884,
                (x, y) -> x >= 240 && x <= 360 ? 0xffffffff : 0xffeffbf7);
        int unselected = CardHighlight.score(
                CURRENT_WIDTH,
                CURRENT_HEIGHT,
                122,
                884,
                (x, y) -> x >= 240 && x <= 360 ? 0xffffffff : 0xffeffbf7);

        assertTrue(selected >= unselected + 10);
    }

    @Test
    public void selectedCardToleratesPotArtworkAndPlusButtonOcclusion() {
        int width = 576;
        int height = 1280;
        int centerX = 106;
        int centerY = 910;
        int selected = CardHighlight.score(
                width,
                height,
                centerX,
                centerY,
                (x, y) -> {
                    boolean potArtwork = x >= 68 && x <= 148 && y >= 800 && y <= 872;
                    boolean plusButton = x >= 145 && x <= 183 && y >= 820 && y <= 872;
                    if (potArtwork) {
                        return 0xff8d786d;
                    }
                    if (plusButton) {
                        return 0xffff7354;
                    }
                    return x >= 20 && x <= 195 && y >= 720 && y <= 955
                            ? 0xffffffff
                            : 0xffeffbf7;
                });
        int unselected = CardHighlight.score(
                width,
                height,
                centerX,
                centerY,
                (x, y) -> 0xffeffbf7);

        assertTrue(selected >= 245);
        assertTrue(selected >= unselected + 10);
    }

    @Test
    public void detectsWhitePlayTriangleSurroundedByGreenButton() {
        int width = 1280;
        int height = 2772;
        int centerX = 516;
        int centerY = 578;
        int xRadius = Math.round(width * 0.035f);
        int yRadius = Math.round(height * 0.017f);

        CardHighlight.Point button = CardHighlight.findStartButton(
                width,
                height,
                (x, y) -> Math.abs(x - centerX) <= 8 && Math.abs(y - centerY) <= 8
                        ? 0xffffffff
                        : ((Math.abs(x - centerX) >= xRadius - 8
                                        && Math.abs(x - centerX) <= xRadius + 8
                                        && Math.abs(y - centerY) <= 8)
                                || (Math.abs(x - centerX) <= 8
                                        && Math.abs(y - centerY) >= yRadius - 8
                                        && Math.abs(y - centerY) <= yRadius + 8)
                                ? 0xff45cc87
                                : 0xffffffff));

        assertNotNull(button);
        assertTrue(Math.abs(button.x() - centerX) <= 4);
        assertTrue(Math.abs(button.y() - centerY) <= 4);
    }

    @Test
    public void rejectsWhitePixelsWithoutGreenStartButton() {
        assertNull(CardHighlight.findStartButton(1280, 2772, (x, y) -> 0xffffffff));
    }

    @Test
    public void findsPetalSearchControlAtItsRenderedHeightInsteadOfAFixedY() {
        int width = 432;
        int height = 936;
        int collapsedX = Math.round(width * 0.91f);
        int expandedX = Math.round(width * 0.08f);

        for (int renderedY : List.of(299, 412)) {
            CardHighlight.Point button = CardHighlight.findPetalSearchButton(
                    width,
                    height,
                    (x, y) -> searchPanelPixel(
                            width, x, y, renderedY, collapsedX, renderedY));
            assertNotNull(button);
            assertTrue(Math.abs(button.y() - renderedY) <= 1);
        }
        int selectorY = 224;
        int magnifierY = 171;
        assertTrue(CardHighlight.isPetalSearchOpen(
                width,
                height,
                (x, y) -> searchPanelPixel(
                        width, x, y, selectorY, expandedX, magnifierY)));
        CardHighlight.Point close = CardHighlight.findPetalSearchCloseButton(
                width,
                height,
                (x, y) -> searchPanelPixel(
                        width, x, y, selectorY, expandedX, magnifierY));
        assertNotNull(close);
        assertEquals(collapsedX, close.x());
        assertTrue(Math.abs(close.y() - magnifierY) <= 1);
        assertFalse(CardHighlight.isPetalSearchOpen(
                width,
                height,
                (x, y) -> searchPanelPixel(
                        width, x, y, 171, expandedX, 229)));
        assertNull(CardHighlight.findPetalSearchButton(
                width, height, (x, y) -> 0xffffffff));
        assertNull(CardHighlight.findPetalSearchCloseButton(
                width, height, (x, y) -> 0xffffffff));
    }

    @Test
    public void analyzesPetalSearchControlsTogetherForOneFrame() {
        int width = 432;
        int height = 936;
        int collapsedX = Math.round(width * 0.91f);
        int selectorY = 360;

        CardHighlight.PetalSearchAnalysis analysis = CardHighlight.analyzePetalSearchControls(
                width,
                height,
                (x, y) -> searchPanelPixel(width, x, y, selectorY, collapsedX, selectorY));

        assertFalse(analysis.searchOpen());
        assertNotNull(analysis.searchButton());
        assertEquals(collapsedX, analysis.searchButton().x());
    }

    @Test
    public void reusesAComputedControlWithinTheSameFrameAnalysis() {
        int width = 432;
        int height = 936;
        int collapsedX = Math.round(width * 0.91f);
        AtomicInteger pixelReads = new AtomicInteger();

        CardHighlight.PetalSearchAnalysis analysis = CardHighlight.analyzePetalSearchControls(
                width,
                height,
                (x, y) -> {
                    pixelReads.incrementAndGet();
                    return searchPanelPixel(width, x, y, 360, collapsedX, 360);
                });

        assertNotNull(analysis.searchButton());
        int readsAfterFirstLookup = pixelReads.get();
        assertEquals(collapsedX, analysis.searchButton().x());
        assertEquals(readsAfterFirstLookup, pixelReads.get());
    }

    @Test
    public void findsPetalSearchControlAcrossTheSelectorRowInsteadOfAtFixedX() {
        int width = 432;
        int height = 936;
        int selectorY = 360;

        for (float xFraction : new float[] {0.82f, 0.88f, 0.94f}) {
            int renderedX = Math.round(width * xFraction);
            CardHighlight.Point button = CardHighlight.findPlantingPetalSearchButton(
                    width,
                    height,
                    (x, y) -> searchPanelPixel(
                            width, x, y, selectorY, renderedX, selectorY));

            assertNotNull(button);
            assertTrue(Math.abs(button.x() - renderedX) <= 1);
            assertTrue(Math.abs(button.y() - selectorY) <= 1);
        }
    }

    private static int searchPanelPixel(
            int width,
            int x,
            int y,
            int selectorY,
            int controlX,
            int controlY) {
        if (Math.abs(x - controlX) <= 5 && Math.abs(y - controlY) <= 5) {
            return 0xff626562;
        }
        if (Math.abs(y - selectorY) <= 5) {
            if (Math.abs(x - Math.round(width * 0.16f)) <= 6) {
                return 0xffffd400;
            }
            if (Math.abs(x - Math.round(width * 0.24f)) <= 6) {
                return 0xffff6168;
            }
            if (Math.abs(x - Math.round(width * 0.32f)) <= 6) {
                return 0xff3e8dcc;
            }
        }
        return 0xffffffff;
    }

    @Test
    public void startsAtFirstAllowedTargetWhenCurrentFlowerIsNotAllowed() {
        List<String> sequence = List.of("白色蝴蝶蘭", "紅色蝴蝶蘭");

        assertEquals("白色蝴蝶蘭", PetalMatcher.nextTarget(sequence, "白色九重葛"));
        assertEquals("紅色蝴蝶蘭", PetalMatcher.nextTarget(sequence, "白色蝴蝶蘭"));
        assertNull(PetalMatcher.nextTarget(sequence, "紅色蝴蝶蘭"));
    }

    @Test
    public void visibleOnlySwitchStartsAtFirstAllowedAndSkipsInsufficientCards() {
        PetalMatcher.Selection first = PetalMatcher.findFlower(
                CURRENT_ZH_TW_TOKENS,
                List.of("白色花瓣", "黃色花瓣", "紅色花瓣"),
                "白色九重葛",
                50,
                CURRENT_WIDTH,
                CURRENT_HEIGHT);
        PetalMatcher.Selection afterWhite = PetalMatcher.findFlower(
                CURRENT_ZH_TW_TOKENS,
                List.of("白色花瓣", "黃色花瓣", "紅色花瓣"),
                "白色花瓣",
                500,
                CURRENT_WIDTH,
                CURRENT_HEIGHT);

        assertEquals("白色花瓣", first.name());
        assertEquals("紅色花瓣", afterWhite.name());
    }

    @Test
    public void visibleOnlySwitchStopsWhenImmediateCardIsOffscreen() {
        assertNull(PetalMatcher.findFlower(
                CURRENT_ZH_TW_TOKENS,
                List.of("白色花瓣", "藍色花瓣", "紅色花瓣"),
                "白色花瓣",
                50,
                CURRENT_WIDTH,
                CURRENT_HEIGHT));
    }

    @Test
    public void screenSignatureIgnoresSmallOcrPositionNoise() {
        String first = PetalMatcher.screenSignature(List.of(
                new PetalMatcher.Token("White Petals", 70, 860, 180, 900),
                new PetalMatcher.Token("120", 90, 790, 180, 830)));
        String second = PetalMatcher.screenSignature(List.of(
                new PetalMatcher.Token("120", 95, 794, 185, 834),
                new PetalMatcher.Token("White Petals", 74, 864, 184, 904)));

        assertEquals(first, second);
    }

    @Test
    public void plantingPanelPullMovesUpTwentyPercentAtEveryResolution() {
        PetalMatcher.PanelPull phone = PetalMatcher.plantingPanelPull(432, 936);
        PetalMatcher.PanelPull tall = PetalMatcher.plantingPanelPull(1080, 2400);

        assertEquals(216, phone.x());
        assertEquals(562, phone.startY());
        assertEquals(187, phone.startY() - phone.endY());
        assertEquals(540, tall.x());
        assertEquals(1440, tall.startY());
        assertEquals(480, tall.startY() - tall.endY());
    }

    @Test
    public void plantingSearchUsesThreeFeedSizedUpwardScrolls() {
        PetalMatcher.SearchResultScroll phone =
                PetalMatcher.plantingSearchResultScroll(432, 936);
        PetalMatcher.SearchResultScroll tall =
                PetalMatcher.plantingSearchResultScroll(1080, 2400);

        assertEquals(3, PetalMatcher.PLANTING_SEARCH_SCROLL_COUNT);
        assertEquals(new PetalMatcher.SearchResultScroll(225, 842, 216, 505), phone);
        assertEquals(new PetalMatcher.SearchResultScroll(562, 2160, 540, 1296), tall);
    }
}
