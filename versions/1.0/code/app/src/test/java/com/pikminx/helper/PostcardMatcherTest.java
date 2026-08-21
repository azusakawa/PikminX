package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 以七張附圖的繁中 OCR 錨點與相對位置驗證明信片流程。 */
public final class PostcardMatcherTest {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 936;

    @Test
    public void recognizesMapAndFlowerName() {
        List<PetalMatcher.Token> tokens = List.of(
                token("紅色花朵 鳥取砂丘 K-14", 145, 420, 263, 462),
                token("鳥取砂丘 K-14", 145, 462, 263, 490));

        assertEquals(PostcardMatcher.Page.MAP,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
        assertEquals("紅色花朵",
                PostcardMatcher.findMapFlowerName(tokens, WIDTH, HEIGHT).text());
    }

    @Test
    public void recognizesDetailAndWarningButtons() {
        List<PetalMatcher.Token> detail = List.of(
                token("紅色花朵", 175, 565, 260, 610),
                token("使用花瓣就能獲得明信片！", 84, 640, 348, 690));
        List<PetalMatcher.Token> warning = List.of(
                token("注意！", 180, 390, 252, 430),
                token("接受並繼續", 225, 518, 365, 570));

        assertEquals(PostcardMatcher.Page.FLOWER_DETAIL,
                PostcardMatcher.detectPage(detail, WIDTH, HEIGHT));
        assertNotNull(PostcardMatcher.findUsePetals(detail));
        assertEquals(PostcardMatcher.Page.WARNING,
                PostcardMatcher.detectPage(warning, WIDTH, HEIGHT));
        assertNotNull(PostcardMatcher.findAcceptContinue(warning));
    }

    @Test
    public void petalListSignatureIgnoresChangingContentOutsideTheScrollableList() {
        List<PetalMatcher.Token> firstFrame = List.of(
                token("21:14", 20, 25, 75, 55),
                token("收集明信片：辨識中", 65, 255, 250, 285),
                token("紅色鼠尾草", 45, 590, 135, 625),
                token("藍色鼠尾草", 175, 590, 265, 625),
                token("黃色鵝觀鷗金香", 300, 590, 420, 625),
                token("紅色鵝觀鷗金香", 45, 790, 165, 825));
        List<PetalMatcher.Token> secondFrame = List.of(
                token("21:15", 20, 25, 75, 55),
                token("等待花瓣列表停止滾動", 65, 255, 270, 285),
                token("紅色鼠尾草", 48, 592, 138, 627),
                token("藍色鼠尾草", 178, 592, 268, 627),
                token("黃色鵝觀鷗金香", 303, 592, 423, 627),
                token("紅色鵝觀鷗金香", 48, 792, 168, 827));

        assertEquals(
                PostcardMatcher.petalListSignature(firstFrame, WIDTH, HEIGHT),
                PostcardMatcher.petalListSignature(secondFrame, WIDTH, HEIGHT));
    }

    @Test
    public void petalListSignatureChangesWhenDifferentPotsBecomeVisible() {
        List<PetalMatcher.Token> upperRows = List.of(
                token("白色花瓣", 45, 590, 135, 625),
                token("黃色花瓣", 175, 590, 265, 625));
        List<PetalMatcher.Token> lowerRows = List.of(
                token("紅色鼠尾草", 45, 590, 135, 625),
                token("藍色鼠尾草", 175, 590, 265, 625));

        assertFalse(PostcardMatcher.petalListSignature(upperRows, WIDTH, HEIGHT)
                .equals(PostcardMatcher.petalListSignature(lowerRows, WIDTH, HEIGHT)));
    }

    @Test
    public void findsPostcardNameFromTopLocationAndIgnoresFlowerSpecies() {
        List<PetalMatcher.Token> tokens = List.of(
                token("6號小鳥風車", 142, 86, 288, 132),
                token("藍色扶桑花", 142, 585, 286, 625),
                token("使用花瓣就能獲得明信片！", 86, 668, 344, 718));

        PostcardMatcher.Target postcardName =
                PostcardMatcher.findDetailPostcardName(tokens, WIDTH, HEIGHT);
        assertNotNull(postcardName);
        assertEquals("6號小鳥風車", postcardName.text());
    }

    @Test
    public void acceptsKanaOnlyPostcardLocationText() {
        List<PetalMatcher.Token> tokens = List.of(
                token("ウォールアート", 142, 86, 288, 132),
                token("紅色牽牛花", 142, 585, 286, 625),
                token("使用花瓣就能獲得明信片！", 86, 668, 344, 718));

        PostcardMatcher.Target postcardName =
                PostcardMatcher.findDetailPostcardName(tokens, WIDTH, HEIGHT);
        assertNotNull(postcardName);
        assertEquals("ウォールアート", postcardName.text());
    }

    @Test
    public void joinsSplitTopPostcardName() {
        List<PetalMatcher.Token> tokens = List.of(
                token("6號小鳥", 150, 76, 282, 108),
                token("風車", 184, 108, 248, 140),
                token("藍色扶桑花", 142, 585, 286, 625),
                token("使用花瓣就能獲得明信片！", 86, 668, 344, 718));

        PostcardMatcher.Target postcardName =
                PostcardMatcher.findDetailPostcardName(tokens, WIDTH, HEIGHT);
        assertNotNull(postcardName);
        assertEquals("6號小鳥風車", postcardName.text());
    }

    @Test
    public void extractsMapPostcardLocationRatherThanFlowerSpecies() {
        List<PetalMatcher.Token> tokens = List.of(
                token("紅色花朵 鳥取砂丘 K-14", 145, 420, 285, 462));

        PostcardMatcher.Target postcardName =
                PostcardMatcher.findMapPostcardName(tokens, WIDTH, HEIGHT);
        assertNotNull(postcardName);
        assertEquals("鳥取砂丘 K-14", postcardName.text());
    }

    @Test
    public void recognizesRealMapBubbleWhenSpeciesDoesNotContainFlowerWord() {
        List<PetalMatcher.Token> tokens = List.of(
                token("藍色扶桑花", 194, 276, 291, 296),
                token("6號小鳥風車", 194, 296, 291, 318));

        assertEquals(PostcardMatcher.Page.MAP,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
        PostcardMatcher.Target postcardName =
                PostcardMatcher.findMapPostcardName(tokens, WIDTH, HEIGHT);
        assertNotNull(postcardName);
        assertEquals("6號小鳥風車", postcardName.text());
    }

    @Test
    public void prefersCoherentJapaneseLocationOverNearbyMixedScriptOcrNoise() {
        List<PetalMatcher.Token> tokens = List.of(
                token("紅色牽牛花", 194, 276, 291, 296),
                token("E #おmW畄 1日H!", 194, 296, 291, 318),
                token("機関車のウォールアート", 146, 296, 282, 318));

        PostcardMatcher.Target postcardName =
                PostcardMatcher.findMapPostcardName(tokens, WIDTH, HEIGHT);

        assertNotNull(postcardName);
        assertEquals("機関車のウォールアート", postcardName.text());
    }

    @Test
    public void requiresSpeciesAndLocationBeforeAcceptingFlowerBubble() {
        List<PetalMatcher.Token> valid = List.of(
                token("藍色扶桑花", 194, 276, 291, 296),
                token("6號小鳥風車", 194, 296, 291, 318));
        List<PetalMatcher.Token> mushroomOrNoise = List.of(
                token("5", 370, 286, 397, 310));

        assertTrue(PostcardMatcher.hasConfirmedMapFlowerBubble(valid, WIDTH, HEIGHT));
        assertFalse(PostcardMatcher.hasConfirmedMapFlowerBubble(
                mushroomOrNoise, WIDTH, HEIGHT));
    }

    @Test
    public void acceptsReceiptReturnBubbleWhenOcrMergesSpeciesAndLocation() {
        List<PetalMatcher.Token> mergedBubble = List.of(
                token("藍色扶桑花 6號小鳥風車", 194, 276, 291, 318));

        PostcardMatcher.Target postcard =
                PostcardMatcher.findMapPostcardName(mergedBubble, WIDTH, HEIGHT);

        assertNotNull(postcard);
        assertEquals("6號小鳥風車", postcard.text());
        assertTrue(PostcardMatcher.hasConfirmedMapFlowerBubble(
                mergedBubble, WIDTH, HEIGHT));
    }

    @Test
    public void choosesFirstVisiblePotWithAtLeastEightyPetals() {
        List<PetalMatcher.Token> tokens = List.of(
                token("選擇要使用的花瓣。", 120, 330, 320, 370),
                token("0", 87, 470, 110, 500),
                token("白色花瓣", 52, 615, 145, 650),
                token("1,175 +", 190, 470, 245, 500),
                token("黃色花瓣", 174, 615, 266, 650),
                token("下一步", 330, 850, 410, 900));

        PostcardMatcher.PetalPot pot = PostcardMatcher.findAvailablePetalPot(
                tokens, "黃色花瓣", 80, WIDTH, HEIGHT);
        assertEquals(PostcardMatcher.Page.PETAL_SELECTION,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
        assertNotNull(pot);
        assertEquals("黃色花瓣", pot.name());
        assertEquals(1175, pot.count());
        assertTrue(pot.y() < 615);
        assertNull(PostcardMatcher.findAvailablePetalPot(
                tokens, "紅色花瓣", 80, WIDTH, HEIGHT));
    }

    @Test
    public void selectsOnlyVisibleSearchResultWithoutMatchingItsOcrName() {
        List<PetalMatcher.Token> tokens = List.of(
                token("970", 158, 1550, 226, 1578),
                token("1,200", 295, 1841, 395, 1878),
                token("黄色養花", 141, 1978, 360, 2031));

        PostcardMatcher.PetalPot pot = PostcardMatcher.findSingleVisiblePetalPot(
                tokens, "黃色曇花", 80, 1280, 2816);

        assertNotNull(pot);
        assertEquals("黃色曇花", pot.name());
        assertEquals(1200, pot.count());
    }

    @Test
    public void rejectsAmbiguousSearchResultsInsteadOfClickingTheFirstPot() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,200", 90, 620, 145, 655),
                token("任意辨識甲", 50, 700, 160, 740),
                token("900", 285, 620, 335, 655),
                token("任意辨識乙", 250, 700, 370, 740));

        assertNull(PostcardMatcher.findSingleVisiblePetalPot(
                tokens, "黃色曇花", 80, WIDTH, HEIGHT));
    }

    @Test
    public void rejectsTargetPotWhenOcrSplitsItsNameAcrossTheSameRow() {
        List<PetalMatcher.Token> tokens = List.of(
                token("選擇要使用的花瓣。", 120, 330, 320, 370),
                token("960", 340, 660, 385, 695),
                token("紅色", 270, 730, 310, 765),
                token("天堂鳥", 314, 730, 375, 765));

        PostcardMatcher.PetalPot pot = PostcardMatcher.findAvailablePetalPot(
                tokens, "紅色天堂鳥", 80, WIDTH, HEIGHT);

        assertNull(pot);
    }

    @Test
    public void selectsOnlyThePotWhoseNameMatchesTheConfiguredField() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,128", 70, 650, 125, 685),
                token("白色天堂鳥", 40, 730, 135, 765),
                token("1,200", 195, 650, 250, 685),
                token("黃色天堂鳥", 165, 730, 270, 765),
                token("880", 330, 650, 380, 685),
                token("紅色天堂鳥", 295, 730, 405, 765));

        PostcardMatcher.PetalPot pot = PostcardMatcher.findAvailablePetalPot(
                tokens, "紅色天堂鳥", 80, WIDTH, HEIGHT);

        assertNotNull(pot);
        assertEquals("紅色天堂鳥", pot.name());
        assertEquals(880, pot.count());
    }

    @Test
    public void picksFavoriteFromSortSheetNotTheToolbar() {
        List<PetalMatcher.Token> tokens = List.of(
                token("選擇皮克敏出去取回明信片。(0/5)", 65, 330, 370, 370),
                token("喜愛▼", 155, 385, 230, 430),
                token("排序：", 180, 690, 250, 730),
                token("喜愛", 180, 780, 250, 820),
                token("友好度", 180, 850, 260, 880),
                token("飾品", 180, 890, 250, 920));

        assertTrue(PostcardMatcher.isSortMenuVisible(tokens, HEIGHT));
        assertTrue(PostcardMatcher.findFavoriteMenuItem(tokens, HEIGHT).y() > 700);
        assertTrue(PostcardMatcher.findSortControl(tokens, HEIGHT).y() < 500);
    }

    @Test
    public void opensSortMenuFromEveryLegalCurrentSortValue() {
        for (String value : List.of("自動", "發現日", "種類", "友好度", "飾品")) {
            assertNotNull(PostcardMatcher.findSortControl(
                    List.of(token(value + "▼", 155, 385, 230, 430)), HEIGHT));
        }
    }

    @Test
    public void recognizesPikminPageFromOpenSortMenuWithoutCounter() {
        List<PetalMatcher.Token> tokens = List.of(
                token("排序：", 150, 700, 260, 750),
                token("喜愛▼", 160, 790, 250, 830),
                token("友好度", 160, 850, 250, 890),
                token("飾品", 160, 900, 250, 940));

        assertEquals(PostcardMatcher.Page.PIKMIN_SELECTION,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
    }

    @Test
    public void selectsFirstFavoritePikminThenGo() {
        List<PetalMatcher.Token> tokens = List.of(
                token("選擇皮克敏出去取回明信片。(1/5)", 65, 330, 370, 370),
                token("羽翅廚師...", 12, 520, 98, 560),
                token("黃色飛機...", 112, 520, 198, 560),
                token("GO", 330, 810, 420, 900));

        assertEquals(PostcardMatcher.Page.PIKMIN_SELECTION,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
        assertEquals(1, PostcardMatcher.selectedPikminCount(tokens));
        assertTrue(PostcardMatcher.findFirstPikmin(tokens, WIDTH, HEIGHT).x() < 100);
        assertEquals(2, PostcardMatcher.findPikminCandidates(tokens, WIDTH, HEIGHT).size());
        assertNotNull(PostcardMatcher.findGo(tokens));
    }

    @Test
    public void ordersPikminCandidatesLeftToRightThenTopToBottom() {
        List<PostcardMatcher.Target> candidates = PostcardMatcher.findPikminCandidates(
                List.of(
                        token("第二隻", 112, 520, 198, 560),
                        token("第三隻", 12, 660, 98, 700),
                        token("第一隻", 12, 518, 98, 558)),
                WIDTH,
                HEIGHT);

        assertEquals(List.of("第一隻", "第二隻", "第三隻"),
                candidates.stream().map(PostcardMatcher.Target::text).toList());
    }

    @Test
    public void selectionGridContainsTwelveResolutionIndependentSlots() {
        List<PostcardMatcher.Target> phone =
                PostcardMatcher.findPikminSelectionSlots(432, 936);
        List<PostcardMatcher.Target> tallPhone =
                PostcardMatcher.findPikminSelectionSlots(1080, 2400);

        assertEquals(12, phone.size());
        assertEquals(new PostcardMatcher.Target("pikmin-slot-1", 56, 482), phone.get(0));
        assertEquals(new PostcardMatcher.Target("pikmin-slot-6", 56, 618), phone.get(5));
        assertEquals(new PostcardMatcher.Target("pikmin-slot-12", 138, 753), phone.get(11));
        assertEquals(new PostcardMatcher.Target("pikmin-slot-1", 140, 1236), tallPhone.get(0));
        assertEquals(new PostcardMatcher.Target("pikmin-slot-12", 346, 1932), tallPhone.get(11));
    }

    @Test
    public void recognizesPikminPageWhenOcrSplitsTheTitle() {
        List<PetalMatcher.Token> tokens = List.of(
                token("選擇皮克敏出去", 65, 330, 270, 370),
                token("(0/5)", 300, 330, 370, 370),
                token("喜愛▼", 155, 385, 230, 430),
                token("羽翅廚師...", 12, 520, 98, 560));

        assertEquals(PostcardMatcher.Page.PIKMIN_SELECTION,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
    }

    @Test
    public void recognizesReceivedPostcardAndReceiveButton() {
        List<PetalMatcher.Token> tokens = List.of(
                token("持有的明信片：101/550", 70, 515, 330, 560),
                token("接收", 165, 665, 267, 725));

        assertEquals(PostcardMatcher.Page.POSTCARD_RECEIVED,
                PostcardMatcher.detectPage(tokens, WIDTH, HEIGHT));
        assertNotNull(PostcardMatcher.findReceive(tokens));
    }

    @Test
    public void findsTraditionalAndSimplifiedDiscardButtons() {
        List<PetalMatcher.Token> traditional = List.of(
                token("持有的明信片：399/400", 70, 515, 330, 560),
                token("捨棄", 75, 665, 175, 725),
                token("接收", 260, 665, 360, 725));
        List<PetalMatcher.Token> simplified = List.of(
                token("持有的明信片：399/400", 70, 515, 330, 560),
                token("舍棄", 75, 665, 175, 725),
                token("接收", 260, 665, 360, 725));

        assertNotNull(PostcardMatcher.findDiscard(traditional));
        assertNotNull(PostcardMatcher.findDiscard(simplified));
    }

    @Test
    public void mirrorsReceiveButtonWhenDiscardTextIsMissing() {
        List<PetalMatcher.Token> tokens = List.of(
                token("持有的明信片：236/550", 70, 515, 330, 560),
                token("接收", 235, 670, 335, 725));

        assertEquals(new PostcardMatcher.Target("捨棄", 147, 697),
                PostcardMatcher.findDiscard(tokens, WIDTH, HEIGHT));
    }

    private static PetalMatcher.Token token(
            String text, int left, int top, int right, int bottom) {
        return new PetalMatcher.Token(text, left, top, right, bottom);
    }
}
