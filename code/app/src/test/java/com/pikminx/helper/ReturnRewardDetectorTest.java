package com.pikminx.helper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public final class ReturnRewardDetectorTest {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 936;
    private static final int GRASS = rgb(76, 190, 83);

    @Test
    public void findsLargeReturnedFruitInCentralSafeRegion() {
        int[] pixels = field();
        ellipse(pixels, 216, 525, 58, 72, rgb(211, 68, 52));
        ellipse(pixels, 204, 502, 26, 28, rgb(246, 182, 95));

        ReturnRewardDetector.Target target = ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertNotNull(target);
        assertTrue(Math.abs(target.x() - 216) < 30);
        assertTrue(Math.abs(target.y() - 525) < 35);
    }

    @Test
    public void findsWideReturnedObjectDuringPickupAnimation() {
        int[] pixels = field();
        ellipse(pixels, 216, 525, 92, 50, rgb(228, 154, 48));

        assertNotNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void findsFragmentedReturnedObjectDuringPickupAnimation() {
        int[] pixels = field();
        ellipse(pixels, 216, 525, 87, 78, rgb(228, 154, 48));
        ellipse(pixels, 216, 525, 68, 59, GRASS);

        assertNotNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void findsReturnedFruitNearRightEdgeOfCollectionArea() {
        int[] pixels = field();
        ellipse(pixels, 340, 555, 38, 60, rgb(220, 150, 45));

        ReturnRewardDetector.Target target = ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertNotNull(target);
        assertTrue(target.x() > 300);
    }

    @Test
    public void searchesOnlyInsideUserSelectedRoi() {
        int[] pixels = field();
        ellipse(pixels, 216, 525, 42, 55, rgb(211, 68, 52));
        ellipse(pixels, 340, 555, 55, 70, rgb(228, 154, 48));

        ReturnRewardDetector.Target target = ReturnRewardDetector.find(
                WIDTH,
                HEIGHT,
                (x, y) -> pixels[y * WIDTH + x],
                new ReturnRewardDetector.Region(150, 450, 280, 620));

        assertNotNull(target);
        assertTrue(Math.abs(target.x() - 216) < 30);
    }

    @Test
    public void doesNotReturnRewardOutsideUserSelectedRoi() {
        int[] pixels = field();
        ellipse(pixels, 340, 555, 55, 70, rgb(228, 154, 48));

        assertNull(ReturnRewardDetector.find(
                WIDTH,
                HEIGHT,
                (x, y) -> pixels[y * WIDTH + x],
                new ReturnRewardDetector.Region(120, 450, 280, 620)));
    }

    @Test
    public void findsUserAnchoredRewardOutsideLegacyCenterRegion() {
        int[] pixels = field();
        ellipse(pixels, 216, 330, 42, 55, rgb(211, 68, 52));

        assertNotNull(ReturnRewardDetector.find(
                WIDTH,
                HEIGHT,
                (x, y) -> pixels[y * WIDTH + x],
                new ReturnRewardDetector.Region(140, 250, 292, 410)));
    }

    @Test
    public void ignoresGrassAndSmallFlowersWhenNothingCanBeCollected() {
        int[] pixels = field();
        for (int y = 430; y < 650; y += 55) {
            ellipse(pixels, 120 + y % 170, y, 7, 5, rgb(205, 45, 48));
        }

        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void ignoresWeakSparseCrowdCandidate() {
        int[] pixels = field();
        int color = rgb(120, 50, 180);
        for (int inset = 0; inset < 12; inset++) {
            rectangle(pixels, 136 + inset, 480 + inset, 296 - inset, 480 + inset, color);
            rectangle(pixels, 136 + inset, 600 - inset, 296 - inset, 600 - inset, color);
            rectangle(pixels, 136 + inset, 480 + inset, 136 + inset, 600 - inset, color);
            rectangle(pixels, 296 - inset, 480 + inset, 296 - inset, 600 - inset, color);
        }

        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void ignoresWideSparseCrowdCandidate() {
        int[] pixels = field();
        int color = rgb(120, 50, 180);
        for (int left : new int[] {110, 160, 210, 260}) {
            rectangle(pixels, left, 500, left + 10, 620, color);
        }
        rectangle(pixels, 110, 555, 320, 561, color);

        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void ignoresLargeObjectsOutsideTheCentralCollectionArea() {
        int[] pixels = field();
        ellipse(pixels, 380, 560, 48, 70, rgb(245, 230, 205));

        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void prefersGreenFruitAndIgnoresFriendPostcard() {
        int[] fruitAndPostcard = field();
        ellipse(fruitAndPostcard, 216, 510, 45, 55, rgb(157, 220, 85));
        rectangle(fruitAndPostcard, 125, 560, 307, 620, rgb(235, 230, 220));

        ReturnRewardDetector.Target fruit = ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> fruitAndPostcard[y * WIDTH + x]);

        assertNotNull(fruit);
        assertTrue(Math.abs(fruit.x() - 216) < 30);
        assertTrue(Math.abs(fruit.y() - 510) < 40);

        int[] postcardOnly = field();
        rectangle(postcardOnly, 125, 560, 307, 620, rgb(235, 230, 220));
        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> postcardOnly[y * WIDTH + x]));
    }

    @Test
    public void ignoresPostcardWhenNoFieldSurroundsIt() {
        int[] pixels = field();
        rectangle(pixels, 95, 390, 337, 670, rgb(40, 40, 40));
        rectangle(pixels, 166, 465, 266, 595, rgb(235, 230, 220));

        assertNull(ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void prefersLowerRewardOverUpperFriendPostcard() {
        int[] pixels = field();
        rectangle(pixels, 170, 360, 350, 510, rgb(235, 230, 220));
        rectangle(pixels, 245, 360, 265, 510, rgb(190, 35, 40));
        ellipse(pixels, 216, 560, 32, 40, rgb(205, 55, 45));

        ReturnRewardDetector.Target target = ReturnRewardDetector.find(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertNotNull(target);
        assertTrue(Math.abs(target.x() - 216) < 30);
        assertTrue(Math.abs(target.y() - 560) < 35);
    }

    @Test
    public void recognizesNectarCapacityWarningNearConfiguredHintRegion() {
        assertTrue(ReturnRewardDetector.hasNectarCapacityWarning(List.of(
                token("精華", 180, 620),
                token("攜帶空間快要滿了", 180, 660)), WIDTH, HEIGHT));
    }

    @Test
    public void ignoresCapacityWordsOutsideWarningRegion() {
        assertFalse(ReturnRewardDetector.hasNectarCapacityWarning(List.of(
                token("精華攜帶空間快要滿了", 180, 180)), WIDTH, HEIGHT));
    }

    @Test
    public void ignoresUnrelatedFullMessage() {
        assertFalse(ReturnRewardDetector.hasNectarCapacityWarning(List.of(
                token("精華", 180, 620),
                token("已經滿了", 180, 660)), WIDTH, HEIGHT));
    }

    @Test
    public void recognizesBroadClippedSquadCloseupWithoutUsingObjectColors() {
        int[] pixels = field();
        rectangle(pixels, 0, 190, 95, 600, rgb(90, 70, 160));
        rectangle(pixels, 100, 200, 330, 390, rgb(210, 190, 110));
        rectangle(pixels, 90, 360, 370, 610, rgb(90, 150, 210));

        assertEquals(ReturnRewardDetector.SquadCloseup.SQUAD_CLOSEUP,
                ReturnRewardDetector.classifySquadCloseup(
                        WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void recognizesSparseCloseupWhenSquadIsClippedOnBothEdges() {
        int[] pixels = field();
        rectangle(pixels, 0, 240, 62, 430, rgb(170, 80, 130));
        rectangle(pixels, 368, 420, 431, 610, rgb(220, 200, 130));

        assertEquals(ReturnRewardDetector.SquadCloseup.SQUAD_CLOSEUP,
                ReturnRewardDetector.classifySquadCloseup(
                        WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    @Test
    public void isolatedReturnedFruitIsNotSquadCloseup() {
        int[] pixels = field();
        ellipse(pixels, 216, 525, 58, 72, rgb(211, 68, 52));
        ellipse(pixels, 204, 502, 26, 28, rgb(246, 182, 95));

        assertEquals(ReturnRewardDetector.SquadCloseup.NOT_SQUAD_CLOSEUP,
                ReturnRewardDetector.classifySquadCloseup(
                        WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]));
    }

    private static int[] field() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, GRASS);
        return pixels;
    }

    private static void ellipse(
            int[] pixels, int centerX, int centerY, int radiusX, int radiusY, int color) {
        for (int y = Math.max(0, centerY - radiusY); y <= Math.min(HEIGHT - 1, centerY + radiusY); y++) {
            for (int x = Math.max(0, centerX - radiusX); x <= Math.min(WIDTH - 1, centerX + radiusX); x++) {
                float dx = (x - centerX) / (float) radiusX;
                float dy = (y - centerY) / (float) radiusY;
                if (dx * dx + dy * dy <= 1f) {
                    pixels[y * WIDTH + x] = color;
                }
            }
        }
    }

    private static void rectangle(
            int[] pixels, int left, int top, int right, int bottom, int color) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                pixels[y * WIDTH + x] = color;
            }
        }
    }

    private static int rgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    private static PetalMatcher.Token token(String text, int centerX, int centerY) {
        return new PetalMatcher.Token(
                text, centerX - 40, centerY - 15, centerX + 40, centerY + 15);
    }
}
