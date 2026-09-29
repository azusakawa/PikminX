package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class ScreenshotOverlayMaskTest {
    @Test
    public void erasesAdjacentOverlayViewsAcrossDisplaySizes() {
        int[][] resolutions = {
                {432, 768},
                {550, 1280},
                {1080, 2340},
                {1440, 3200}
        };
        for (int[] resolution : resolutions) {
            verifyMask(resolution[0], resolution[1]);
        }
    }

    @Test
    public void usesTheAvailableSideForAnEdgeAlignedOverlay() {
        int width = 120;
        int height = 80;
        int[] pixels = background(width, height);
        fill(pixels, width, 0, 12, 30, 45, 0xffffffff);

        ScreenshotOverlayMask.erase(
                width,
                height,
                pixels,
                List.of(new ScreenshotOverlayMask.Region(0, 12, 30, 45)),
                0);

        for (int y = 12; y < 45; y++) {
            assertEquals(pixels[y * width + 30], pixels[y * width]);
        }
    }

    private static void verifyMask(int width, int height) {
        int[] pixels = background(width, height);
        int iconLeft = Math.round(width * 0.04f);
        int iconTop = Math.round(height * 0.08f);
        int iconSize = Math.max(24, Math.round(width * 0.08f));
        int noticeRight = Math.min(width, iconLeft + Math.round(width * 0.48f));
        int noticeBottom = iconTop + Math.max(iconSize, Math.round(height * 0.06f));
        int sampleX = Math.max(0, iconLeft - 5);
        fill(pixels, width, iconLeft, iconTop, iconLeft + iconSize, noticeBottom, 0xff131313);
        fill(pixels, width, iconLeft + iconSize, iconTop, noticeRight, noticeBottom, 0xffffffff);

        ScreenshotOverlayMask.erase(
                width,
                height,
                pixels,
                List.of(
                        new ScreenshotOverlayMask.Region(
                                iconLeft, iconTop, iconLeft + iconSize, noticeBottom),
                        new ScreenshotOverlayMask.Region(
                                iconLeft + iconSize, iconTop, noticeRight, noticeBottom)),
                4);

        for (int y = Math.max(0, iconTop - 4); y < Math.min(height, noticeBottom + 4); y++) {
            int expected = pixels[y * width + sampleX];
            for (int x = Math.max(0, iconLeft - 4); x < Math.min(width, noticeRight + 4); x++) {
                assertEquals(width + "x" + height, expected, pixels[y * width + x]);
            }
        }
    }

    private static int[] background(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int color = 0xff000000 | ((y * 37) & 0x00ffffff);
            Arrays.fill(pixels, y * width, (y + 1) * width, color);
        }
        return pixels;
    }

    private static void fill(
            int[] pixels,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int color) {
        for (int y = top; y < bottom; y++) {
            Arrays.fill(pixels, y * width + left, y * width + right, color);
        }
    }
}
