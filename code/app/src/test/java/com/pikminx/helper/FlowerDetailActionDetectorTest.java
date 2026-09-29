package com.pikminx.helper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class FlowerDetailActionDetectorTest {
    /** Covers common portrait aspect ratios without tying the control to one pixel size. */
    @Test
    public void findsUsePetalsControlAcrossPortraitResolutions() {
        int[][] resolutions = {
                {432, 768},   // 16:9
                {720, 1440},  // 18:9
                {1080, 2340}, // 19.5:9
                {550, 1280},  // Xperia 1 II recording
                {1440, 3200}  // 20:9 at high density
        };

        for (int[] resolution : resolutions) {
            int width = resolution[0];
            int height = resolution[1];
            int[] pixels = detailPage(width, height);

            FlowerDetailActionDetector.Target target = FlowerDetailActionDetector.find(
                    width,
                    height,
                    (x, y) -> pixels[y * width + x]);

            assertNotNull(width + "x" + height, target);
            assertTrue(width + "x" + height, Math.abs(target.x() - width / 2) <= width * 0.02f);
            assertTrue(width + "x" + height, Math.abs(target.y() - height * 0.70f) <= height * 0.02f);
        }
    }

    /** A light sheet alone must not be treated as an actionable button. */
    @Test
    public void rejectsDetailSheetWithoutTealControl() {
        int width = 550;
        int height = 1280;
        int[] pixels = detailPage(width, height);
        int top = Math.round(height * 0.63f);
        int bottom = Math.round(height * 0.77f);
        Arrays.fill(pixels, top * width, bottom * width, 0xffffffff);

        FlowerDetailActionDetector.Target target = FlowerDetailActionDetector.find(
                width,
                height,
                (x, y) -> pixels[y * width + x]);

        assertTrue(target == null);
    }

    /** Dispatch uses the narrower outlined "go explore" control shown in the real capture. */
    @Test
    public void findsNarrowDispatchActionFromRealScreenRatio() {
        int width = 432;
        int height = 936;
        int[] pixels = detailPage(width, height, 0.34f, 0.055f, 0.747f);

        FlowerDetailActionDetector.Target target = FlowerDetailActionDetector.find(
                width,
                height,
                (x, y) -> pixels[y * width + x]);

        assertNotNull(target);
        assertTrue(Math.abs(target.x() - width / 2) <= width * 0.02f);
        assertTrue(Math.abs(target.y() - height * 0.747f) <= height * 0.02f);
    }

    private static int[] detailPage(int width, int height) {
        return detailPage(width, height, 0.60f, 0.11f, 0.70f);
    }

    private static int[] detailPage(
            int width,
            int height,
            float controlWidthFraction,
            float controlHeightFraction,
            float controlCenterYFraction) {
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xff4b6b55);
        fillRect(
                pixels,
                width,
                Math.round(width * 0.06f),
                Math.round(height * 0.58f),
                Math.round(width * 0.94f),
                Math.round(height * 0.92f),
                0xfffafafa);

        int controlWidth = Math.round(width * controlWidthFraction);
        int controlHeight = Math.round(height * controlHeightFraction);
        int centerX = width / 2;
        int centerY = Math.round(height * controlCenterYFraction);
        fillRect(
                pixels,
                width,
                centerX - controlWidth / 2,
                centerY - controlHeight / 2,
                centerX + controlWidth / 2,
                centerY + controlHeight / 2,
                0xff209b91);
        return pixels;
    }

    private static void fillRect(
            int[] pixels,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int color) {
        int height = pixels.length / width;
        for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(width, right); x++) {
                pixels[y * width + x] = color;
            }
        }
    }
}
