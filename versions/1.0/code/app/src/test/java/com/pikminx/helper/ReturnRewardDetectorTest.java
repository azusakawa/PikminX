package com.pikminx.helper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void ignoresGrassAndSmallFlowersWhenNothingCanBeCollected() {
        int[] pixels = field();
        for (int y = 430; y < 650; y += 55) {
            ellipse(pixels, 120 + y % 170, y, 7, 5, rgb(205, 45, 48));
        }

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
}
