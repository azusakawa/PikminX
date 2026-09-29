package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.Test;

public final class ExpeditionScreenAnalyzerTest {
    @Test
    public void selectedTabAcceptsBorderNoiseButNotOtherWords() {
        assertTrue(ExpeditionScreenAnalyzer.isExploreTabLabel("|探險"));
        assertTrue(ExpeditionScreenAnalyzer.isExploreTabLabel("｜ 探险 ｜"));
        assertTrue(ExpeditionScreenAnalyzer.isExploreTabLabel("探險"));
        assertFalse(ExpeditionScreenAnalyzer.isExploreTabLabel("探險明信片"));
        assertFalse(ExpeditionScreenAnalyzer.isExploreTabLabel("不在探險"));
        assertFalse(ExpeditionScreenAnalyzer.isExploreTabLabel("探|險"));
        assertFalse(ExpeditionScreenAnalyzer.isExploreTabLabel("明信片"));
    }

    @Test
    public void recognizesPageSequenceFromPikminXOcr() {
        assertEquals(ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(List.of(
                        token("探險", 20, 40), token("花苗和水果", 30, 120),
                        token("發現日", 30, 180))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classify(List.of(
                        token("派皮克敏出去", 100, 620), token("探險吧！", 220, 620))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION,
                ExpeditionScreenAnalyzer.classify(List.of(
                        token("0/12", 80, 80), token("自動", 80, 700), token("GO", 220, 700))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.RESULT,
                ExpeditionScreenAnalyzer.classify(List.of(token("探險開始", 100, 500))));
    }

    @Test
    public void visualClassificationCanReuseAnAlreadyComputedBaseScreen() {
        assertEquals(
                ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classifyWithBaseScreen(
                        ExpeditionScreenAnalyzer.Screen.DETAIL,
                        List.of(token("無關文字", 20, 20)),
                        432,
                        936,
                        (x, y) -> 0xffffffff));
    }

    @Test
    public void fixedDetailMarkerOverridesMisleadingSelectionStatusWithoutBrightPixels() {
        int width = 432;
        int height = 936;

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classify(
                        List.of(
                                token("正在選擇皮克敏", 60, 100),
                                token("自動派遣進度：0/11", 60, 145),
                                token("派皮克敏出去", 90, 610),
                                token("探險吧！", 220, 610)),
                        width,
                        height,
                        (x, y) -> 0xffd7d7d7));
    }

    @Test
    public void actionButtonAloneDoesNotClassifyDetailPage() {
        assertEquals(
                ExpeditionScreenAnalyzer.Screen.UNKNOWN,
                ExpeditionScreenAnalyzer.classify(
                        List.of(token("前往探險", 120, 660)),
                        432,
                        936,
                        (x, y) -> 0xffd7d7d7));
    }

    @Test
    public void detailActionWithDetailMetadataClassifiesPromotionVariant() {
        assertEquals(
                ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classify(
                        List.of(
                                token("前往探險", 120, 660),
                                token("距離：578m", 60, 740)),
                        432,
                        936,
                        (x, y) -> 0xffd7d7d7));
    }

    @Test
    public void detailOutlineUsesObservedBoundsAcrossResolutionsAndPanelPositions() {
        for (int scale : new int[] {1, 2, 3}) {
            for (int panelTop : new int[] {350, 650}) {
                int width = 432 * scale, height = 936 * scale;
                int left = 110 * scale, top = panelTop * scale;
                int right = 290 * scale, bottom = (panelTop + 60) * scale;
                ExpeditionScreenAnalyzer.Point detected = ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                        ExpeditionScreenAnalyzer.Screen.DETAIL, List.of(), width, height,
                        (x, y) -> outlinedCapsule(x, y, left, top, right, bottom, 2 * scale));
                assertNotNull(detected);
                assertTrue(Math.abs(detected.x() - 200 * scale) <= 1);
                assertTrue(Math.abs(detected.y() - (panelTop + 30) * scale) <= 1);
            }
        }
        assertNotNull(ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, List.of(token("前往探險", 120, 370)),
                432, 936, (x, y) -> 0xffffffff));
    }

    @Test
    public void verifiedDetailDoesNotTapWithoutOcrOrAUniqueObservedButton() {
        int width = 432, height = 936;
        assertNull(ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL,
                List.of(token("派皮克敏出去", 90, 610)), width, height, (x, y) -> 0xffffffff));
        IntBinaryOperator twoButtons = (x, y) -> {
            int first = outlinedCapsule(x, y, 110, 570, 290, 630, 2);
            return first != 0xffffffff ? first : outlinedCapsule(x, y, 110, 700, 290, 760, 2);
        };
        assertNull(ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.DETAIL, List.of(), width, height, twoButtons));
        assertNull(ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                ExpeditionScreenAnalyzer.Screen.UNKNOWN, List.of(), width, height,
                (x, y) -> outlinedCapsule(x, y, 110, 650, 290, 710, 2)));
    }

    private static int outlinedCapsule(int x, int y, int left, int top, int right, int bottom, int stroke) {
        double radius = (bottom - top) / 2.0;
        double centerY = (top + bottom) / 2.0;
        double nearestX = Math.max(left + radius, Math.min(right - radius, x));
        double distance = Math.hypot(x - nearestX, y - centerY);
        return distance <= radius && distance >= radius - stroke ? 0xff00ac90 : 0xffffffff;
    }

    @Test
    public void selectionControlsOverrideAFalseDetailButtonPixelMatch() {
        int width = 432;
        int height = 936;

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION,
                ExpeditionScreenAnalyzer.classify(
                        List.of(
                                token("0/12", 60, 100),
                                token("自動", 70, 360),
                                token("篩選", 170, 360),
                                token("排序", 270, 360)),
                        width,
                        height,
                        (x, y) -> y >= height * 0.58f
                                && y < height * 0.92f
                                ? (x >= width * 0.33f && x < width * 0.67f
                                        && y >= height * 0.72f && y < height * 0.775f
                                                ? 0xff209b91
                                                : 0xfffafafa)
                                : 0xff4b6b55));
    }

    @Test
    public void scatteredExploreCardAccentsDoNotBecomeADetailButton() {
        int width = 432;
        int height = 936;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xff4b6b55);
        fillRect(pixels, width, 0, Math.round(height * 0.58f),
                width, Math.round(height * 0.92f), 0xfffafafa);
        fillRect(pixels, width, 90, 610, 104, 626, 0xff209b91);
        fillRect(pixels, width, 205, 642, 219, 658, 0xff209b91);
        fillRect(pixels, width, 320, 675, 334, 691, 0xff209b91);

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(
                        List.of(token("探險", 220, 420), token("花苗和水果", 20, 500)),
                        width,
                        height,
                        (x, y) -> pixels[y * width + x]));
    }

    @Test
    public void detailActionUsesOnlyLowerOcrButtonAndSupportsSplitTokens() {
        int width = 432;
        int height = 936;

        ExpeditionScreenAnalyzer.Point statusOnly = ExpeditionScreenAnalyzer.findDetailAction(
                List.of(token("正在點擊前往探險", 80, 140)),
                width,
                height,
                (x, y) -> y >= height * 0.58f && y < height * 0.92f
                        ? (x >= width * 0.33f && x < width * 0.67f
                                && y >= height * 0.72f && y < height * 0.775f
                                        ? 0xff209b91 : 0xfffafafa)
                        : 0xff4b6b55);

        assertNull(statusOnly);

        ExpeditionScreenAnalyzer.Point action = ExpeditionScreenAnalyzer.findDetailAction(
                List.of(
                        new PetalMatcher.Token("前往", 120, 660, 190, 702),
                        new PetalMatcher.Token("探險", 195, 660, 265, 702)),
                width,
                height,
                (x, y) -> 0xffd7d7d7);

        assertNotNull(action);
        assertEquals(192, action.x());
        assertEquals(681, action.y());
    }

    @Test
    public void detailActionAllowsOneOcrSubstitutionOnlyWithAdventureAnchorInButtonRegion() {
        int width = 432;
        int height = 936;

        assertNotNull(ExpeditionScreenAnalyzer.findDetailAction(
                List.of(token("前住探險", 120, 660)),
                width,
                height,
                (x, y) -> 0xffd7d7d7));
        assertNull(ExpeditionScreenAnalyzer.findDetailAction(
                List.of(token("前往探查", 120, 660)),
                width,
                height,
                (x, y) -> 0xffd7d7d7));
        assertNull(ExpeditionScreenAnalyzer.findDetailAction(
                List.of(token("前住探險", 80, 140)),
                width,
                height,
                (x, y) -> 0xffd7d7d7));
        assertNull(ExpeditionScreenAnalyzer.findDetailAction(
                List.of(token("派皮克敏出去探險吧", 120, 660)),
                width,
                height,
                (x, y) -> 0xffd7d7d7));
    }

    @Test
    public void detailActionDiagnosticExplainsTextAndRegionRejections() {
        int width = 432;
        int height = 936;

        String outsideRegion = ExpeditionScreenAnalyzer.detailActionDiagnostic(
                List.of(token("正在點擊前往探險", 80, 140)), width, height);
        String splitAccepted = ExpeditionScreenAnalyzer.detailActionDiagnostic(
                List.of(
                        new PetalMatcher.Token("前往", 120, 660, 190, 702),
                        new PetalMatcher.Token("探險", 195, 660, 265, 702)),
                width,
                height);
        String splitTooFarApart = ExpeditionScreenAnalyzer.detailActionDiagnostic(
                List.of(
                        new PetalMatcher.Token("前往", 60, 660, 130, 702),
                        new PetalMatcher.Token("探險", 260, 660, 330, 702)),
                width,
                height);

        assertTrue(outsideRegion.contains("reason=direct_outside_region"));
        assertTrue(splitAccepted.contains("reason=pair_accepted"));
        assertTrue(splitTooFarApart.contains("reason=pair_horizontal_gap"));
        assertTrue(ExpeditionScreenAnalyzer.detailActionDiagnostic(
                List.of(token("前住探險", 120, 660)), width, height)
                .contains("match=fuzzy_3_of_4"));
    }

    @Test
    public void returnedExpeditionDialogRequiresTitleEvenWithoutCollectButton() {
        assertTrue(ExpeditionScreenAnalyzer.isReturnedExpeditionDialog(List.of(
                token("皮克敏從探險回來了！", 45, 390),
                token("領取", 156, 650))));
        assertTrue(ExpeditionScreenAnalyzer.isReturnedExpeditionDialog(List.of(
                token("皮克敏從探險回來了！", 45, 390))));
        assertTrue(ExpeditionScreenAnalyzer.isReturnedExpeditionDialog(List.of(
                token("皮克敏从探险回来了！", 45, 390))));
        assertFalse(ExpeditionScreenAnalyzer.isReturnedExpeditionDialog(List.of(
                token("領取", 156, 650), token("完成！", 45, 390))));
    }

    @Test
    public void visibleItemsIdentifyExploreListWhenOverlayCoversTheExploreTab() {
        List<PetalMatcher.Token> captured = List.of(
                token("明信片", 351, 129),
                token("節品一覽", 296, 861),
                token("黃色花苗", 50, 194),
                token("藍色花苗", 50, 365),
                token("粉紅色花苗", 178, 194),
                token("白色花苗", 323, 706));

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(captured));
    }

    @Test
    public void fruitModeExcludesSeedlingsAndActiveExpeditions() {
        List<PetalMatcher.Token> tokens = List.of(
                token("大花苗", 100, 300),
                token("蘋果", 100, 500),
                token("Tottori", 100, 550),
                token("草莓", 100, 700),
                token("鳥取砂丘G-2", 100, 750),
                token("查看皮克敏", 120, 710));

        ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200);

        assertNotNull(target);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.FRUIT, target.kind());
        assertEquals("蘋果", target.label());
    }

    @Test
    public void targetModeIsEnforced() {
        List<PetalMatcher.Token> tokens = List.of(
                token("大花苗", 100, 350),
                token("檸檬", 450, 500), token("Tottori", 450, 550));

        ExpeditionScreenAnalyzer.Target pot = ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.POT, 1080, 1200);
        ExpeditionScreenAnalyzer.Target fruit = ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200);

        assertNotNull(pot);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.POT, pot.kind());
        assertNotNull(fruit);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.FRUIT, fruit.kind());
        assertNull(ExpeditionScreenAnalyzer.findTarget(
                List.of(token("大花苗", 100, 350)), ExpeditionTargetMode.FRUIT, 1080, 1200));
    }

    @Test
    public void futureFruitNameIsAcceptedFromItsCardLocation() {
        List<PetalMatcher.Token> tokens = List.of(
                token("奇異果", 450, 500), token("Tottori", 450, 550));

        ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200);

        assertNotNull(target);
        assertEquals("奇異果", target.label());
    }

    @Test
    public void seedlingAndGiftSuffixesAreNeverFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("冰藍花苗", 100, 500), token("Tottori", 100, 550),
                token("紅色禮品", 450, 500), token("Kobe", 450, 550),
                token("灰色礼品", 800, 500), token("Tottori", 800, 550));

        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200));
    }

    @Test
    public void locationAndDurationTextAreNotFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("鳥取砂丘E-2", 450, 500), token("21小時", 450, 550));

        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200));
    }

    @Test
    public void mushroomNameWithLocationIsNeverFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("一般霓蘑菇", 100, 300), token("Ruhraue", 100, 350));

        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200));
    }

    @Test
    public void cardsAboveFlowerFruitSectionAreNeverFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("一般霓魔姑", 100, 300), token("Ruhraue", 100, 350),
                token("花苗和水果", 100, 450),
                token("紅色花苗", 100, 550), token("Tottori", 100, 600));

        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1080, 1200));
    }

    @Test
    public void giftLocationAndCloseButtonAreNotFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("福壽里", 184, 2350), token("飾品一覽", 991, 2573),
                token("X", 107, 2579));

        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 1280, 2772));
    }

    @Test
    public void sameVisualIdentitySurvivesBoundsAndResolutionJitter() {
        ExpeditionScreenAnalyzer.Target first = new ExpeditionScreenAnalyzer.Target(
                ExpeditionScreenAnalyzer.ItemKind.FRUIT, "large_peach.png", 500, 600);
        ExpeditionScreenAnalyzer.Target second = new ExpeditionScreenAnalyzer.Target(
                ExpeditionScreenAnalyzer.ItemKind.FRUIT, "large_peach.png", 520, 620);

        assertEquals(first.confirmationKey(1080, 2400), second.confirmationKey(1080, 2400));

        ExpeditionScreenAnalyzer.Target scaled = new ExpeditionScreenAnalyzer.Target(
                ExpeditionScreenAnalyzer.ItemKind.FRUIT, "large_peach.png", 1000, 1200);
        assertEquals(first.confirmationKey(1080, 2400), scaled.confirmationKey(2160, 4800));
        ExpeditionScreenAnalyzer.Target different = new ExpeditionScreenAnalyzer.Target(
                ExpeditionScreenAnalyzer.ItemKind.FRUIT, "large_apple.png", 500, 600);
        assertFalse(first.confirmationKey(1080, 2400).equals(different.confirmationKey(1080, 2400)));
    }

    @Test
    public void recognizesAllProvidedPotStylesAndRejectsLeafyFruit() {
        int width = 1080;
        int height = 1200;
        int centerX = 540;
        int labelTop = 600;
        int[] bodyColors = {
                0xffff9fbd, 0xffc89b36, 0xffe4d6b4,
                0xff56c9df, 0xff3265c9, 0xff7542a7,
                0xffd8b72e, 0xff555555, 0xffeeeeee
        };

        for (int bodyColor : bodyColors) {
            int[] pixels = itemPage(width, height, centerX, labelTop, bodyColor, true);
            assertTrue(ExpeditionScreenAnalyzer.looksLikePotStyle(
                    width, height, centerX, labelTop, (x, y) -> pixels[y * width + x]));
        }

        int[] fruit = itemPage(width, height, centerX, labelTop, 0xffd93f38, false);
        assertFalse(ExpeditionScreenAnalyzer.looksLikePotStyle(
                width, height, centerX, labelTop, (x, y) -> fruit[y * width + x]));

        List<PetalMatcher.Token> peachLabel = List.of(
                token("桃子", centerX - 60, labelTop),
                token("Tottori", centerX - 60, labelTop + 50));
        int[] pot = itemPage(width, height, centerX, labelTop, 0xffff9fbd, true);
        assertNull(ExpeditionScreenAnalyzer.findTarget(
                peachLabel, ExpeditionTargetMode.FRUIT, width, height,
                (x, y) -> pot[y * width + x]));
        assertNotNull(ExpeditionScreenAnalyzer.findTarget(
                peachLabel, ExpeditionTargetMode.POT, width, height,
                (x, y) -> pot[y * width + x]));
        assertNull(ExpeditionScreenAnalyzer.findTarget(
                peachLabel, ExpeditionTargetMode.POT, width, height,
                (x, y) -> fruit[y * width + x]));
        assertNotNull(ExpeditionScreenAnalyzer.findTarget(
                peachLabel, ExpeditionTargetMode.FRUIT, width, height,
                (x, y) -> fruit[y * width + x]));
    }

    @Test
    public void detectsExpandedExplorePanelAndMushroomListStart() {
        List<PetalMatcher.Token> collapsed = List.of(
                token("探險", 200, 650), token("紅色花苗", 100, 750));
        List<PetalMatcher.Token> expanded = List.of(
                token("探險", 200, 120), token("蘑菇", 20, 180),
                token("今天還剩下 1 次", 20, 220));

        assertFalse(ExpeditionScreenAnalyzer.isExplorePanelExpanded(collapsed, 1200));
        assertTrue(ExpeditionScreenAnalyzer.isExplorePanelExpanded(expanded, 1200));
        assertTrue(ExpeditionScreenAnalyzer.isExploreListStart(expanded));
        ExpeditionScreenAnalyzer.Point anchor =
                ExpeditionScreenAnalyzer.findExploreTabAnchor(
                        List.of(token("探險", 600, 650)), 1080, 1200);
        assertNotNull(anchor);
        assertEquals(660, anchor.x());
        assertEquals(671, anchor.y());
    }

    @Test
    public void detectsOnlyAStateSpecificGreenResultCloseCross() {
        int width = 432, height = 936;
        int centerX = 52, centerY = 850, radius = 30;
        IntBinaryOperator pixels = (x, y) -> {
            int dx = x - centerX, dy = y - centerY;
            if (dx * dx + dy * dy <= radius * radius) {
                if ((Math.abs(dx - dy) <= 4 || Math.abs(dx + dy) <= 4)
                        && Math.abs(dx) <= 14 && Math.abs(dy) <= 14) return 0xfff5fff8;
                return 0xff219653;
            }
            return 0xff4aae52; // Real post-GO flower-field background is also green.
        };
        ExpeditionScreenAnalyzer.Point close =
                ExpeditionScreenAnalyzer.findResultClose(width, height, pixels);
        assertNotNull(close);
        assertTrue(Math.abs(close.x() - centerX) <= 4);
        assertTrue(Math.abs(close.y() - centerY) <= 4);
        assertNull(ExpeditionScreenAnalyzer.findResultClose(width, height,
                (x, y) -> 0xff4aae52));
    }

    @Test
    public void fullSelectionRequiresEqualCounter() {
        assertTrue(ExpeditionScreenAnalyzer.hasFullSelection(
                List.of(token("12/12", 100, 100))));
    }

    @Test
    public void onlyOcrSelectionRequiresAFullCounter() {
        assertFalse(DispatchSelectionMethod.AUTO.requiresFullSelection());
        assertTrue(DispatchSelectionMethod.DRAG_12.requiresFullSelection());
    }

    @Test
    public void findsPikminSearchFromPixelsWithoutAutoOcr() {
        ExpeditionScreenAnalyzer.Point search =
                ExpeditionScreenAnalyzer.findPikminSearchButton(
                        432, 936, searchIconPixels(432, 382));

        assertNotNull(search);
        assertEquals(39, search.x());
        assertEquals(382, search.y());
    }

    @Test
    public void searchControlFollowsItsObservedHorizontalPosition() {
        for (int scale : new int[] {1, 2}) {
            IntBinaryOperator pixels = searchIconPixels(432 * scale, 382 * scale);
            ExpeditionScreenAnalyzer.Point shifted = ExpeditionScreenAnalyzer.findPikminSearchButton(
                    432 * scale, 936 * scale, (x, y) -> pixels.applyAsInt(x - 10 * scale, y));
            assertNotNull(shifted);
            assertEquals(49 * scale, shifted.x());
            assertEquals(382 * scale, shifted.y());
        }
    }

    @Test
    public void pikminAutoUsesExactOcrTextCenterAcrossLayouts() {
        assertEquals(new ExpeditionScreenAnalyzer.Point(104, 403),
                ExpeditionScreenAnalyzer.findPikminAutoButton(
                        List.of(token("自動", 44, 382)), 432, 936));
        assertEquals(new ExpeditionScreenAnalyzer.Point(259, 1001),
                ExpeditionScreenAnalyzer.findPikminAutoButton(
                        List.of(token("自动", 199, 980)), 1080, 2400));
        assertNull(ExpeditionScreenAnalyzer.findPikminAutoButton(
                List.of(token("自動選擇", 44, 382)), 432, 936));
        assertNull(ExpeditionScreenAnalyzer.findPikminAutoButton(
                List.of(token("自動", 320, 382)), 432, 936));
    }

    @Test
    public void pixelSearchAnchorConfirmsSelectionWhenAutoOcrIsMissing() {
        List<PetalMatcher.Token> counterOnly = List.of(token("0/10", 180, 310));

        assertEquals(ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION,
                ExpeditionScreenAnalyzer.classify(
                        counterOnly, 432, 936, searchIconPixels(432, 382)));
        assertEquals(ExpeditionScreenAnalyzer.Screen.UNKNOWN,
                ExpeditionScreenAnalyzer.classify(
                        counterOnly, 432, 936, (x, y) -> 0xffffffff));
    }

    @Test
    public void scrolledExploreListStillFindsFutureFruit() {
        List<PetalMatcher.Token> tokens = List.of(
                token("探險", 240, 390),
                token("桃子：天堂鳥", 150, 480),
                token("鳥取砂丘 H-2", 150, 530),
                token("19小時", 150, 580));

        assertEquals(ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(
                        tokens, 432, 936, (x, y) -> 0xffffffff));
        ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT, 432, 936, (x, y) -> 0xffffffff);
        assertNotNull(target);
        assertEquals("桃子：天堂鳥", target.label());
    }

    @Test
    public void returnListRecognizesTheObservedXiaomiExploreTabOcrSubstitution() {
        int width = 1280;
        int height = 2772;
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("速險", 781, 1229, 874, 1280),
                new PetalMatcher.Token("蘋果", 192, 1492, 279, 1539),
                new PetalMatcher.Token("022 小時", 934, 1637, 1153, 1699));

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(
                        tokens, width, height, (x, y) -> 0xffffffff));
        assertTrue(ExpeditionScreenAnalyzer.hasExploreNavigationAnchor(
                tokens, width, height));
        assertEquals(new ExpeditionScreenAnalyzer.Point(827, 1254),
                ExpeditionScreenAnalyzer.findExploreTabAnchor(tokens, width, height));
    }

    @Test
    public void exploreTabOcrSubstitutionWithoutListEvidenceStaysUnknown() {
        List<PetalMatcher.Token> tokens = List.of(token("速險", 781, 1229));

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.UNKNOWN,
                ExpeditionScreenAnalyzer.classify(
                        tokens, 1280, 2772, (x, y) -> 0xffffffff));
    }

    private static IntBinaryOperator searchIconPixels(int width, int centerY) {
        int centerX = Math.round(width * 0.09f);
        return (x, y) -> Math.abs(x - centerX) <= 7 && Math.abs(y - centerY) <= 7
                ? 0xff5f6368 : 0xffffffff;
    }

    @Test
    public void visibleCardsProveExploreListButNotExpandedPanel() {
        List<PetalMatcher.Token> tokens = List.of(
                token("探險", 240, 390),
                token("完成！", 20, 560),
                token("領取！", 20, 610));

        assertEquals(ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(
                        tokens, 432, 936, (x, y) -> 0xffffffff));
        assertFalse(ExpeditionScreenAnalyzer.isExplorePanelExpanded(tokens, 432, 936));
        assertNull(ExpeditionScreenAnalyzer.findTarget(
                tokens, ExpeditionTargetMode.FRUIT_AND_POT, 432, 936));
    }

    @Test
    public void floatingOverlayTextIsNotAnExploreNavigationAnchor() {
        assertFalse(ExpeditionScreenAnalyzer.hasExploreNavigationAnchor(
                List.of(token("只在探險清單辨識水果", 180, 80)), 432, 936));
    }

    @Test
    public void dispatchSearchUsesTheSelectedPikminTypeName() {
        assertEquals(
                List.of("混合", "紅色", "黃色", "藍色", "紫色", "白色", "羽翅", "岩石", "冰凍"),
                java.util.Arrays.stream(DispatchPikminType.values())
                        .map(DispatchPikminType::label)
                        .toList());
    }

    @Test
    public void greenAppleGuardUsesStableTitleSuffixAndRejectsSeedlings() {
        ExpeditionScreenAnalyzer.TitleEvidence apple =
                ExpeditionScreenAnalyzer.greenAppleTitleEvidence(List.of(
                        token("責蘋果", 100, 500), token("和安里", 100, 550)));
        assertNotNull(apple);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.FRUIT, apple.kind());
        assertEquals("責蘋果", apple.label());

        ExpeditionScreenAnalyzer.TitleEvidence seedling =
                ExpeditionScreenAnalyzer.greenAppleTitleEvidence(List.of(
                        token("灰色花苗", 100, 500), token("Tottori", 100, 550)));
        assertNotNull(seedling);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.POT, seedling.kind());
        assertNull(ExpeditionScreenAnalyzer.greenAppleTitleEvidence(List.of(
                token("和安里", 100, 550))));
        assertNull(ExpeditionScreenAnalyzer.greenAppleTitleEvidence(List.of(
                token("蘋果花苗", 100, 500))));
    }

    @Test
    public void greenAppleEvidenceIgnoresGenericFruitPotSectionHeading() {
        ExpeditionScreenAnalyzer.TitleEvidence apple =
                ExpeditionScreenAnalyzer.greenAppleTitleEvidence(List.of(
                        token("青蘋果：洋桔梗", 100, 500),
                        token("花苗和水果", 100, 450)));

        assertNotNull(apple);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.FRUIT, apple.kind());
    }

    private static PetalMatcher.Token token(String text, int x, int y) {
        return new PetalMatcher.Token(text, x, y, x + 120, y + 42);
    }

    private static int[] itemPage(
            int width, int height, int centerX, int labelTop, int bodyColor, boolean pot) {
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xffffffff);
        fillRect(pixels, width, centerX - 12, labelTop - 190,
                centerX + 12, labelTop - 105, 0xff54a936);
        fillRect(pixels, width, centerX - 50, labelTop - 185,
                centerX + 12, labelTop - 145, 0xff62b946);
        if (pot) {
            fillRect(pixels, width, centerX - 78, labelTop - 125,
                    centerX + 78, labelTop - 82, 0xff81512d);
            fillRect(pixels, width, centerX - 88, labelTop - 90,
                    centerX + 88, labelTop - 18, bodyColor);
        } else {
            fillRect(pixels, width, centerX - 92, labelTop - 138,
                    centerX + 92, labelTop - 18, bodyColor);
        }
        return pixels;
    }

    private static void fillRect(
            int[] pixels, int width, int left, int top, int right, int bottom, int color) {
        int height = pixels.length / width;
        for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(width, right); x++) {
                pixels[y * width + x] = color;
            }
        }
    }
}
