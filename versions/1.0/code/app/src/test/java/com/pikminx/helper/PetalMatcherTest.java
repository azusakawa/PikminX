package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 驗證 OCR token 與花盆右下角剩餘數量的配對規則。 */
public final class PetalMatcherTest {
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
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals("白色美人蕉", selection.name());
        assertEquals(1040, selection.count());
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
                CURRENT_HEIGHT);

        assertNotNull(selection);
        assertEquals("白色花瓣", selection.name());
        assertEquals(170, selection.count());
    }

    @Test
    public void focusedSearchCanCompleteAfterTwoStableFrames() {
        assertFalse(PetalMatcher.hasStableSearchResult(1));
        assertTrue(PetalMatcher.hasStableSearchResult(2));
        assertTrue(PetalMatcher.hasStableSearchResult(3));
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
                CURRENT_HEIGHT));
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
                    (x, y) -> Math.abs(x - collapsedX) <= 5
                                    && Math.abs(y - renderedY) <= 5
                            ? 0xff626562
                            : 0xffffffff);
            assertNotNull(button);
            assertTrue(Math.abs(button.y() - renderedY) <= 1);
        }
        assertTrue(CardHighlight.isPetalSearchOpen(
                width,
                height,
                (x, y) -> Math.abs(x - expandedX) <= 5 && Math.abs(y - 412) <= 5
                        ? 0xff626562
                        : 0xffffffff));
        assertEquals(
                new CardHighlight.Point(collapsedX, 412),
                CardHighlight.findPetalSearchCloseButton(
                        width,
                        height,
                        (x, y) -> Math.abs(x - expandedX) <= 5
                                        && Math.abs(y - 412) <= 5
                                ? 0xff626562
                                : 0xffffffff));
        assertNull(CardHighlight.findPetalSearchButton(
                width, height, (x, y) -> 0xffffffff));
        assertNull(CardHighlight.findPetalSearchCloseButton(
                width, height, (x, y) -> 0xffffffff));
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
}
