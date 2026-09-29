package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public final class PlantingScreenAnalyzerTest {
    @Test
    public void whistleEntryTakesPriorityOverHomeChrome() {
        int width = 432;
        int height = 936;
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("飾品一覽", 120, 70),
                        token("好友", 260, 70),
                        token("通知", 380, 70),
                        token("步數", 220, 300)),
                PetalCatalog.petals(),
                width,
                height,
                whistlePixels(
                        Math.round(width * 0.86f),
                        Math.round(height * 0.91f),
                        width,
                        height));

        assertEquals(PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY, detection.screen());
        assertNotNull(detection.entryEvidence().whistleAnchor());
        assertNotNull(detection.mapEntry());
    }

    @Test
    public void homeWithoutAWhistleRemainsDistinctFromTheMap() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("飾品一覽", 120, 70),
                        token("好友", 260, 70),
                        token("通知", 380, 70),
                        token("步數", 220, 300)),
                PetalCatalog.petals(),
                432,
                936,
                (x, y) -> 0xff4d9f68);

        assertEquals(PlantingScreenAnalyzer.Screen.HOME, detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void whistleIsTheOnlyMapEntryAnchorAcrossScreenSizes() {
        for (int[] size : new int[][] {{462, 1000}, {432, 936}, {1280, 2772}}) {
            int width = size[0];
            int height = size[1];
            int whistleX = Math.round(width * 0.86f);
            int whistleY = Math.round(height * 0.91f);
            PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                    List.of(),
                    PetalCatalog.petals(),
                    width,
                    height,
                    whistlePixels(whistleX, whistleY, width, height));

            assertEquals(PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY, detection.screen());
            assertNotNull(detection.mapEntry());
            assertEquals(whistleX, detection.mapEntry().x(), 5);
            assertEquals(
                    whistleY - Math.round(height * 0.173f),
                    detection.mapEntry().y(),
                    5);
            assertEquals(
                    PlantingScreenAnalyzer.EntrySource.WHISTLE_RELATIVE,
                    detection.entryEvidence().source());
            assertNotNull(detection.entryEvidence().whistleAnchor());
            assertNotNull(detection.entryEvidence().whistleSearchBounds());
        }
    }

    @Test
    public void lowerWhistleWinsOverLargerCyanControlAboveIt() {
        int width = 432;
        int height = 936;
        int whistleX = Math.round(width * 0.86f);
        int whistleY = Math.round(height * 0.91f);
        int upperControlY = Math.round(height * 0.82f);
        java.util.function.IntBinaryOperator whistle =
                whistlePixels(whistleX, whistleY, width, height);
        java.util.function.IntBinaryOperator upperControl =
                oversizedWhistleLikePixels(whistleX, upperControlY, width, height);

        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(),
                PetalCatalog.petals(),
                width,
                height,
                (x, y) -> {
                    int upper = upperControl.applyAsInt(x, y);
                    return upper != 0xff4d9f68 ? upper : whistle.applyAsInt(x, y);
                });

        assertEquals(PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY, detection.screen());
        assertNotNull(detection.entryEvidence().whistleAnchor());
        assertEquals(whistleY, detection.entryEvidence().whistleAnchor().y(), 5);
        assertEquals(
                whistleY - Math.round(height * 0.173f),
                detection.mapEntry().y(),
                5);
    }

    @Test
    public void expectedWhistleWinsOverALowerRightDistractor() {
        int width = 1280;
        int height = 2772;
        int whistleX = Math.round(width * 0.86f);
        int whistleY = Math.round(height * 0.90f);
        java.util.function.IntBinaryOperator whistle =
                whistlePixels(whistleX, whistleY, width, height);
        java.util.function.IntBinaryOperator distractor =
                whistlePixels(
                        Math.round(width * 0.945f),
                        Math.round(height * 0.955f),
                        width,
                        height);

        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 220, 300)),
                PetalCatalog.petals(),
                width,
                height,
                (x, y) -> {
                    int expected = whistle.applyAsInt(x, y);
                    return expected != 0xff4d9f68
                            ? expected
                            : distractor.applyAsInt(x, y);
                });

        assertEquals(PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY, detection.screen());
        assertNotNull(detection.entryEvidence().whistleAnchor());
        assertEquals(whistleX, detection.entryEvidence().whistleAnchor().x(), 5);
        assertEquals(whistleY, detection.entryEvidence().whistleAnchor().y(), 5);
    }

    @Test
    public void lightObjectWithoutCyanDoesNotBecomeAWhistle() {
        int width = 432;
        int height = 936;
        int centerX = Math.round(width * 0.86f);
        int centerY = Math.round(height * 0.91f);
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 180, 140)),
                PetalCatalog.petals(),
                width,
                height,
                (x, y) -> Math.abs(x - centerX) <= 28 && Math.abs(y - centerY) <= 14
                        ? 0xfff2f4f2
                        : 0xff4d9f68);

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void ocrToolbarDoesNotCreateAnEntryWithoutAWhistle() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("步數", 270, 250),
                        token("606", 475, 735),
                        token("Boost", 455, 816)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68);

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void flowerIconDoesNotCreateAnEntryWithoutAWhistle() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 270, 250)),
                PetalCatalog.petals(),
                638,
                1000,
                mapPlantingEntryPixels(486, 736, 638, 1000));

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void rejectsABlueMapControlEvenWhenItContainsAWarmBadge() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 270, 250)),
                PetalCatalog.petals(),
                638,
                1000,
                blueMapControlPixels(486, 760, 638, 1000));

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void mapAnchorsAloneDoNotCreateABlindEntryTap() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 270, 250)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68);

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void warmMapStepNumberDoesNotBecomeAPlantingStopControl() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("步數", 270, 300),
                        token("加速", 180, 260),
                        token("606", 475, 735),
                        token("Boost", 455, 816)),
                PetalCatalog.petals(),
                638,
                1000,
                mapWithWarmStepPixels(486, 736, 273, 270, 638, 1000));

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.mapEntry());
        assertNull(detection.startControl());
        assertNull(detection.stopControl());
    }

    @Test
    public void assistantOverlayTextIsNotPlantingMenuEvidence() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("自動種花・辨識中 開始種花", 140, 90)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
    }

    @Test
    public void bottomDualControlsAndOcrDoNotReplaceTheWhistleAnchor() {
        for (int[] size : new int[][] {{462, 1000}, {638, 1000}, {720, 1280}}) {
            int width = size[0];
            int height = size[1];
            int bottomControlX = Math.round(width * 0.55f);
            int bottomControlY = Math.round(height * 0.936f);
            PetalMatcher.Token count = relativeToken("884", width, height, 0.79f, 0.756f);
            PetalMatcher.Token boost = relativeToken("Boost", width, height, 0.76f, 0.837f);
            PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                    List.of(
                            token("種植的花朵總數", 100, 145),
                            token("已達到獲得上限", 350, 145),
                            token("加速", 150, 230),
                            count,
                            boost),
                    PetalCatalog.petals(),
                    width,
                    height,
                    activePlantingReturnPixels(
                            bottomControlX, bottomControlY, width, height));

            assertEquals(
                    PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                    detection.screen());
            assertNull(detection.mapEntry());
        }
    }

    @Test
    public void potCardDataAloneDoesNotConfirmTheMenuOrMapEntry() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("約9900朵花的步行距離", 170, 250),
                        token("白色花瓣", 420, 690),
                        token("602", 460, 620)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xffffffff);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void requiresAMapAnchorBeforeTappingANumberInTheEntryRegion() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("606", 475, 735)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
        assertNull(detection.mapEntry());
    }

    @Test
    public void plantingHeaderPairAloneDoesNotConfirmTheMenu() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(
                        token("種植的花朵總數", 100, 145),
                        token("已達到獲得上限", 350, 145)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xffffffff);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
    }

    @Test
    public void accessibilityStopNodeDoesNotBypassTheColorRowAnchor() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68,
                false,
                true);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
    }

    @Test
    public void weakControlTextAloneDoesNotConfirmThePlantingMenu() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("加速", 180, 260)),
                PetalCatalog.petals(),
                638,
                1000,
                (x, y) -> 0xff4d9f68);

        assertEquals(PlantingScreenAnalyzer.Screen.UNKNOWN, detection.screen());
    }

    @Test
    public void findsOrangeStopControlAtExpandedAndCollapsedPanelHeights() {
        for (int centerY : List.of(210, 575)) {
            PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                    List.of(token("大家一起", 390, centerY)),
                    PetalCatalog.petals(),
                    638,
                    1000,
                    withPlantingColorRow(
                            orangeStopPixels(273, centerY, 638, 1000),
                            638,
                            1000,
                            centerY + 100));

            assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
            assertNotNull(detection.stopControl());
            assertEquals(centerY, detection.stopControl().y(), 5);
            assertNull(detection.startControl());
        }
    }

    @Test
    public void greenPlayControlIsFoundAtExpandedAndCollapsedPanelHeights() {
        for (int centerY : List.of(210, 575)) {
            PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                    List.of(token("大家一起", 390, centerY)),
                    PetalCatalog.petals(),
                    638,
                    1000,
                    withPlantingColorRow(
                            greenStartPixels(273, centerY, 638, 1000),
                            638,
                            1000,
                            centerY + 100));

            assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
            assertNotNull(detection.startControl());
            assertEquals(centerY, detection.startControl().y(), 5);
            assertNull(detection.stopControl());
        }
    }

    @Test
    public void collapsedGreenPlayControlConfirmsMenuWhenOcrIsUnavailable() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(),
                PetalCatalog.petals(),
                638,
                1000,
                withPlantingColorRow(
                        greenStartPixels(273, 575, 638, 1000),
                        638,
                        1000,
                        675));

        assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
        assertNotNull(detection.startControl());
    }

    @Test
    public void disabledPlayControlStillConfirmsMenuFromTheFixedColorRow() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(),
                PetalCatalog.petals(),
                638,
                1000,
                withPlantingColorRow((x, y) -> 0xffffffff, 638, 1000, 690));

        assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
        assertNull(detection.startControl());
        assertNull(detection.stopControl());
    }

    @Test
    public void fixedControlRowConfirmsTheMenuRegardlessOfPanelStateOrPersonalCounts() {
        for (int background : new int[] {0xffffffff, 0xfffe5d63}) {
            PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                    List.of(
                            token("步數", 220, 160),
                            token("95", 330, 730),
                            token("1600", 150, 800),
                            token("15", 420, 800)),
                    PetalCatalog.petals(),
                    591,
                    1280,
                    withPlantingColorRow(
                            greenStartPixelsOnBackground(
                                    237, 725, 591, 1280, background),
                            591,
                            1280,
                            853));

            assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
            assertNotNull(detection.startControl());
            assertNull(detection.mapEntry());
        }
    }

    @Test
    public void confirmedPlantingMenuOverridesAWhistleLikePotDistractor() {
        int width = 638;
        int height = 1000;
        java.util.function.IntBinaryOperator menu = withPlantingColorRow(
                greenStartPixels(273, 575, width, height),
                width,
                height,
                690);
        java.util.function.IntBinaryOperator distractor = whistlePixels(
                Math.round(width * 0.86f),
                Math.round(height * 0.90f),
                width,
                height);

        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("大家一起", 390, 575)),
                PetalCatalog.petals(),
                width,
                height,
                (x, y) -> {
                    int distractorPixel = distractor.applyAsInt(x, y);
                    return distractorPixel != 0xff4d9f68
                            ? distractorPixel
                            : menu.applyAsInt(x, y);
                });

        assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
        assertNotNull(detection.startControl());
        assertNull(detection.mapEntry());
    }

    @Test
    public void greenMapObjectWithoutAPanelDoesNotConfirmThePlantingMenu() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 220, 160)),
                PetalCatalog.petals(),
                591,
                1280,
                greenStartPixelsOnBackground(237, 725, 591, 1280, 0xff4d9f68));

        assertEquals(
                PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY,
                detection.screen());
        assertNull(detection.startControl());
    }

    @Test
    public void colorRowAnchorSkipsUpperFlowerFieldStartLikeDistractor() {
        PlantingScreenAnalyzer.Detection detection = PlantingScreenAnalyzer.analyze(
                List.of(token("步數", 220, 160)),
                PetalCatalog.petals(),
                638,
                1000,
                plantingMenuWithUpperGreenDistractor(638, 1000));

        assertEquals(PlantingScreenAnalyzer.Screen.PLANTING_MENU, detection.screen());
        assertNotNull(detection.startControl());
        assertEquals(575, detection.startControl().y(), 8);
    }

    private static PetalMatcher.Token token(String text, int left, int top) {
        return new PetalMatcher.Token(text, left, top, left + 60, top + 42);
    }

    private static PetalMatcher.Token relativeToken(
            String text, int width, int height, float centerX, float centerY) {
        int tokenWidth = Math.max(24, Math.round(width * 0.09f));
        int tokenHeight = Math.max(18, Math.round(height * 0.04f));
        int x = Math.round(width * centerX);
        int y = Math.round(height * centerY);
        return new PetalMatcher.Token(
                text,
                x - tokenWidth / 2,
                y - tokenHeight / 2,
                x + tokenWidth / 2,
                y + tokenHeight / 2);
    }

    private static java.util.function.IntBinaryOperator orangeStopPixels(
            int centerX, int centerY, int width, int height) {
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        return (x, y) -> {
            if (Math.abs(x - centerX) <= 7 && Math.abs(y - centerY) <= 7) {
                return 0xffffffff;
            }
            boolean warmRing = (Math.abs(Math.abs(x - centerX) - xRadius) <= 7
                            && Math.abs(y - centerY) <= 7)
                    || (Math.abs(x - centerX) <= 7
                            && Math.abs(Math.abs(y - centerY) - yRadius) <= 7);
            return warmRing ? 0xfff9af3c : 0xffff7e87;
        };
    }

    private static java.util.function.IntBinaryOperator whistlePixels(
            int centerX, int centerY, int width, int height) {
        int bodyX = Math.max(14, Math.round(width * 0.055f));
        int bodyY = Math.max(10, Math.round(height * 0.014f));
        int tipX = Math.max(5, Math.round(width * 0.014f));
        int tipY = Math.max(8, Math.round(height * 0.012f));
        return (x, y) -> {
            int dx = Math.abs(x - centerX);
            int dy = Math.abs(y - centerY);
            if (dx <= bodyX && dy <= bodyY) {
                return 0xfff2f4f2;
            }
            if (Math.abs(x - (centerX + bodyX)) <= tipX && dy <= tipY) {
                return 0xff66c8cf;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator oversizedWhistleLikePixels(
            int centerX, int centerY, int width, int height) {
        int bodyX = Math.max(18, Math.round(width * 0.075f));
        int bodyY = Math.max(14, Math.round(height * 0.020f));
        int cyanX = Math.max(8, Math.round(width * 0.025f));
        int cyanY = Math.max(10, Math.round(height * 0.016f));
        return (x, y) -> {
            if (Math.abs(x - centerX) <= bodyX && Math.abs(y - centerY) <= bodyY) {
                return 0xfff2f4f2;
            }
            if (Math.abs(x - (centerX + bodyX)) <= cyanX
                    && Math.abs(y - centerY) <= cyanY) {
                return 0xff66c8cf;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator mapPlantingEntryPixels(
            int centerX, int centerY, int width, int height) {
        int outerX = Math.max(12, Math.round(width * 0.04f));
        int outerY = Math.max(14, Math.round(height * 0.028f));
        int innerX = outerX / 2;
        int innerY = outerY / 2;
        return (x, y) -> {
            int dx = Math.abs(x - centerX);
            int dy = Math.abs(y - centerY);
            if (dx <= 4 && dy <= 4) {
                return 0xffd5b64b;
            }
            if (dx <= innerX && dy <= innerY) {
                return 0xffeeeeea;
            }
            if (dx <= outerX && dy <= outerY) {
                return 0xff3f8d61;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator mapWithWarmStepPixels(
            int entryX,
            int entryY,
            int stepX,
            int stepY,
            int width,
            int height) {
        java.util.function.IntBinaryOperator mapEntry =
                mapPlantingEntryCountBadgePixels(entryX, entryY, width, height);
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        return (x, y) -> {
            if (Math.abs(x - stepX) <= 7 && Math.abs(y - stepY) <= 7) {
                return 0xffffffff;
            }
            boolean warmRing = (Math.abs(Math.abs(x - stepX) - xRadius) <= 7
                            && Math.abs(y - stepY) <= 7)
                    || (Math.abs(x - stepX) <= 7
                            && Math.abs(Math.abs(y - stepY) - yRadius) <= 7);
            return warmRing ? 0xfff9af3c : mapEntry.applyAsInt(x, y);
        };
    }

    private static java.util.function.IntBinaryOperator activePlantingReturnPixels(
            int centerX, int centerY, int width, int height) {
        int xOffset = Math.max(10, Math.round(width * 0.025f));
        int yOffset = Math.max(12, Math.round(height * 0.016f));
        return (x, y) -> {
            if (Math.abs(x - centerX) <= 5 && Math.abs(y - centerY) <= 5) {
                return 0xfffadf8d;
            }
            if (Math.abs(x - centerX) <= 5
                    && Math.abs(y - (centerY - yOffset)) <= 5) {
                return 0xfff5fbf7;
            }
            if ((Math.abs(x - centerX) <= 5
                            && Math.abs(y - (centerY + yOffset)) <= 5)
                    || (Math.abs(x - (centerX + xOffset)) <= 5
                            && Math.abs(y - centerY) <= 5)) {
                return 0xff4f9661;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator mapPlantingEntryCountBadgePixels(
            int centerX, int centerY, int width, int height) {
        int outerX = Math.max(12, Math.round(width * 0.04f));
        int outerY = Math.max(14, Math.round(height * 0.028f));
        int innerX = outerX / 2;
        int innerY = outerY / 2;
        return (x, y) -> {
            int dx = Math.abs(x - centerX);
            int dy = Math.abs(y - centerY);
            if (dx <= innerX && dy <= innerY) {
                return 0xffe8b946;
            }
            if (dx <= outerX && dy <= outerY) {
                boolean visiblePetal = dx <= innerX + 6 || dy <= innerY + 6;
                return visiblePetal ? 0xffeeeeea : 0xff3f8d61;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator blueMapControlPixels(
            int centerX, int centerY, int width, int height) {
        int outerX = Math.max(12, Math.round(width * 0.04f));
        int outerY = Math.max(14, Math.round(height * 0.028f));
        int innerX = outerX / 2;
        int innerY = outerY / 2;
        return (x, y) -> {
            int dx = Math.abs(x - centerX);
            int dy = Math.abs(y - centerY);
            if (dx <= innerX && dy <= innerY) {
                return dx <= innerX / 3 ? 0xffe8b946 : 0xff48b9d7;
            }
            if (dx <= outerX && dy <= outerY) {
                return 0xff3f8d61;
            }
            return 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator greenStartPixels(
            int centerX, int centerY, int width, int height) {
        return greenStartPixelsOnBackground(
                centerX, centerY, width, height, 0xffffffff);
    }

    private static java.util.function.IntBinaryOperator greenStartPixelsOnBackground(
            int centerX,
            int centerY,
            int width,
            int height,
            int background) {
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        return (x, y) -> {
            if (Math.abs(x - centerX) <= 7 && Math.abs(y - centerY) <= 7) {
                return 0xffffffff;
            }
            boolean greenRing = (Math.abs(Math.abs(x - centerX) - xRadius) <= 7
                            && Math.abs(y - centerY) <= 7)
                    || (Math.abs(x - centerX) <= 7
                            && Math.abs(Math.abs(y - centerY) - yRadius) <= 7);
            return greenRing ? 0xff45cc87 : background;
        };
    }

    private static java.util.function.IntBinaryOperator plantingMenuWithUpperGreenDistractor(
            int width, int height) {
        int controlX = 273;
        int distractorY = 330;
        int controlY = 575;
        int panelTop = 500;
        int colorY = 690;
        int dotRadius = Math.max(7, Math.round(width * 0.018f));
        int[] dotX = {
                Math.round(width * 0.08f),
                Math.round(width * 0.16f),
                Math.round(width * 0.24f),
                Math.round(width * 0.33f)
        };
        int[] dotColor = {0xffeef5f8, 0xffffd800, 0xffff5360, 0xff3788cf};
        return (x, y) -> {
            for (int index = 0; index < dotX.length; index++) {
                int dx = x - dotX[index];
                int dy = y - colorY;
                if (dx * dx + dy * dy <= dotRadius * dotRadius) {
                    return dotColor[index];
                }
            }
            Integer actual = greenStartControlPixel(
                    x, y, controlX, controlY, width, height);
            if (actual != null) {
                return actual;
            }
            Integer distractor = greenStartControlPixel(
                    x, y, controlX, distractorY, width, height);
            if (distractor != null) {
                return distractor;
            }
            return y >= panelTop ? 0xffffffff : 0xff4d9f68;
        };
    }

    private static java.util.function.IntBinaryOperator withPlantingColorRow(
            java.util.function.IntBinaryOperator base,
            int width,
            int height,
            int colorY) {
        int dotRadius = Math.max(7, Math.round(width * 0.018f));
        int stripHalfHeight = Math.max(12, Math.round(height * 0.012f));
        int[] dotX = {
                Math.round(width * 0.08f),
                Math.round(width * 0.16f),
                Math.round(width * 0.24f),
                Math.round(width * 0.33f)
        };
        int[] dotColor = {0xffeef5f8, 0xffffd800, 0xffff5360, 0xff3788cf};
        return (x, y) -> {
            for (int index = 0; index < dotX.length; index++) {
                int dx = x - dotX[index];
                int dy = y - colorY;
                if (dx * dx + dy * dy <= dotRadius * dotRadius) {
                    return dotColor[index];
                }
            }
            if (Math.abs(y - colorY) <= stripHalfHeight) {
                return 0xffffffff;
            }
            return base.applyAsInt(x, y);
        };
    }

    private static Integer greenStartControlPixel(
            int x, int y, int centerX, int centerY, int width, int height) {
        if (Math.abs(x - centerX) <= 7 && Math.abs(y - centerY) <= 7) {
            return 0xffffffff;
        }
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        boolean greenRing = (Math.abs(Math.abs(x - centerX) - xRadius) <= 7
                        && Math.abs(y - centerY) <= 7)
                || (Math.abs(x - centerX) <= 7
                        && Math.abs(Math.abs(y - centerY) - yRadius) <= 7);
        return greenRing ? 0xff45cc87 : null;
    }
}
