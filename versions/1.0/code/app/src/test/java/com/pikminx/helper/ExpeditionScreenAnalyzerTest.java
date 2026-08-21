package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ExpeditionScreenAnalyzerTest {
    @Test
    public void recognizesAutoCoolPageSequenceFromPikminXOcr() {
        assertEquals(ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST,
                ExpeditionScreenAnalyzer.classify(List.of(
                        token("探險", 20, 40), token("花苗和水果", 30, 120),
                        token("發現日", 30, 180))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classify(List.of(token("前往探險", 100, 700))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION,
                ExpeditionScreenAnalyzer.classify(List.of(
                        token("0/12", 80, 80), token("自動", 80, 700), token("GO", 220, 700))));
        assertEquals(ExpeditionScreenAnalyzer.Screen.RESULT,
                ExpeditionScreenAnalyzer.classify(List.of(token("探險開始", 100, 500))));
    }

    @Test
    public void visibleGoExploreButtonOverridesStaleListOcr() {
        int width = 432;
        int height = 936;

        assertEquals(
                ExpeditionScreenAnalyzer.Screen.DETAIL,
                ExpeditionScreenAnalyzer.classify(
                        List.of(token("探險", 180, 120), token("花苗和水果", 80, 180)),
                        width,
                        height,
                        (x, y) -> y >= height * 0.58f
                                && (y < height * 0.92f)
                                ? (x >= width * 0.33f && x < width * 0.67f
                                        && y >= height * 0.72f && y < height * 0.775f
                                                ? 0xff209b91
                                                : 0xfffafafa)
                                : 0xff4b6b55));
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
                token("草莓", 100, 700),
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
                token("大花苗", 100, 350), token("檸檬", 100, 500));

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

        List<PetalMatcher.Token> peachLabel = List.of(token("桃子", centerX - 60, labelTop));
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
    public void focusedOcrRecoversAVisibleSeedlingMissedByFullScreenOcr() {
        int width = 432;
        int height = 936;
        int cropTop = 168;
        assertNull(ExpeditionScreenAnalyzer.findTarget(
                List.of(token("探險", 240, 120), token("完成！", 170, 470)),
                ExpeditionTargetMode.FRUIT_AND_POT,
                width,
                height));

        ExpeditionScreenAnalyzer.Target target =
                ExpeditionScreenAnalyzer.findFocusedTarget(
                        List.of(token("粉紅色花苗", 40, (550 - cropTop) * 2)),
                        ExpeditionTargetMode.FRUIT_AND_POT,
                        width,
                        height,
                        cropTop,
                        2);

        assertNotNull(target);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.POT, target.kind());
        assertEquals("粉紅色花苗", target.label());
        assertTrue(Math.abs(target.y() - 550) < 30);
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
    public void resultCloseAnchorMatchesTheRealBottomLeftButton() {
        ExpeditionScreenAnalyzer.Point close =
                ExpeditionScreenAnalyzer.resultCloseAnchor(432, 936);

        assertEquals(42, close.x());
        assertEquals(878, close.y());
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
    public void findsIconOnlyPikminSearchBesideAutoControl() {
        ExpeditionScreenAnalyzer.Point search =
                ExpeditionScreenAnalyzer.findPikminSearchButton(
                        List.of(token("自動", 70, 360)), 432, 936);

        assertNotNull(search);
        assertEquals(38, search.x());
        assertEquals(381, search.y());
    }

    @Test
    public void dispatchSearchUsesTheSelectedPikminTypeName() {
        assertEquals(
                List.of("混合", "紅色", "黃色", "藍色", "紫色", "白色", "羽翅", "岩石", "冰凍"),
                java.util.Arrays.stream(DispatchPikminType.values())
                        .map(DispatchPikminType::label)
                        .toList());
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
