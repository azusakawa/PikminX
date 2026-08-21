package com.pikminx.helper;

import java.util.function.IntBinaryOperator;

/** Finds a teal outlined detail-page action without reading its language. */
final class FlowerDetailActionDetector {
    record Target(int x, int y, int width, int height, float confidence) {}

    private FlowerDetailActionDetector() {}

    static Target find(int width, int height, IntBinaryOperator pixelAt) {
        if (width <= 0 || height <= 0 || !hasLightDetailSheet(width, height, pixelAt)) {
            return null;
        }

        // The search window follows the detail sheet rather than a device-specific
        // pixel position. The returned click is the measured control center.
        int left = Math.round(width * 0.12f);
        int right = Math.round(width * 0.88f);
        int top = Math.round(height * 0.64f);
        int bottom = Math.round(height * 0.76f);
        int step = Math.max(1, Math.round(width / 432f));
        int sampled = 0;
        int teal = 0;
        int minX = right;
        int maxX = left;
        int minY = bottom;
        int maxY = top;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                sampled++;
                if (!isTealAccent(pixelAt.applyAsInt(x, y))) {
                    continue;
                }
                teal++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (sampled == 0 || teal * 1000 < sampled * 8) {
            return null;
        }

        int controlWidth = maxX - minX + step;
        int controlHeight = maxY - minY + step;
        float aspect = controlWidth / (float) Math.max(1, controlHeight);
        // Game controls scale with the available content width. Screen height,
        // however, changes substantially between 16:9 and 21:9 phones, so a
        // height-relative limit rejects the same button on taller displays.
        if (controlWidth < width * 0.28f
                || controlWidth > width * 0.75f
                || controlHeight < width * 0.055f
                || controlHeight > width * 0.34f
                || aspect < 2.0f
                || aspect > 9.5f) {
            return null;
        }
        float confidence = teal / (float) sampled;
        return new Target(
                (minX + maxX + step) / 2,
                (minY + maxY + step) / 2,
                controlWidth,
                controlHeight,
                confidence);
    }

    private static boolean hasLightDetailSheet(
            int width, int height, IntBinaryOperator pixelAt) {
        int left = Math.round(width * 0.06f);
        int right = Math.round(width * 0.94f);
        int top = Math.round(height * 0.58f);
        int bottom = Math.round(height * 0.92f);
        int step = Math.max(4, width / 72);
        int sampled = 0;
        int light = 0;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                sampled++;
                if (red >= 230 && green >= 230 && blue >= 230) {
                    light++;
                }
            }
        }
        return sampled > 0 && light * 100 >= sampled * 42;
    }

    private static boolean isTealAccent(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return red <= 110
                && green >= 105
                && blue >= 70
                && green - red >= 40
                && blue - red >= 20
                && Math.abs(green - blue) <= 90;
    }
}
