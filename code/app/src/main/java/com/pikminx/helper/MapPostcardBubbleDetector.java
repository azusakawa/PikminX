package com.pikminx.helper;

import java.util.ArrayDeque;
import java.util.function.IntBinaryOperator;

/**
 * Finds the dark rounded information bubble that the game keeps above the
 * flower used for the most recently received postcard.
 *
 * <p>The detector intentionally ignores every glyph inside the bubble. Its
 * geometry, dark neutral background and bright foreground are stable across
 * languages, while the location text is not.</p>
 */
final class MapPostcardBubbleDetector {
    record Target(int x, int y, int width, int height, float confidence) {}

    private MapPostcardBubbleDetector() {}

    static Target find(int width, int height, IntBinaryOperator pixelAt) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        int step = Math.max(2, Math.round(width / 216f));
        int left = Math.round(width * 0.04f);
        int right = Math.round(width * 0.96f);
        int top = Math.round(height * 0.14f);
        int bottom = Math.round(height * 0.78f);
        int gridWidth = Math.max(1, (right - left + step - 1) / step);
        int gridHeight = Math.max(1, (bottom - top + step - 1) / step);
        boolean[] dark = new boolean[gridWidth * gridHeight];
        boolean[] visited = new boolean[dark.length];
        for (int gy = 0; gy < gridHeight; gy++) {
            int y = Math.min(height - 1, top + gy * step);
            for (int gx = 0; gx < gridWidth; gx++) {
                int x = Math.min(width - 1, left + gx * step);
                dark[gy * gridWidth + gx] = isDarkNeutral(pixelAt.applyAsInt(x, y));
            }
        }

        Target best = null;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < dark.length; index++) {
            if (!dark[index] || visited[index]) {
                continue;
            }
            visited[index] = true;
            queue.add(index);
            int count = 0;
            int minX = gridWidth;
            int maxX = 0;
            int minY = gridHeight;
            int maxY = 0;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int gx = current % gridWidth;
                int gy = current / gridWidth;
                count++;
                minX = Math.min(minX, gx);
                maxX = Math.max(maxX, gx);
                minY = Math.min(minY, gy);
                maxY = Math.max(maxY, gy);
                enqueue(gx - 1, gy, gridWidth, gridHeight, dark, visited, queue);
                enqueue(gx + 1, gy, gridWidth, gridHeight, dark, visited, queue);
                enqueue(gx, gy - 1, gridWidth, gridHeight, dark, visited, queue);
                enqueue(gx, gy + 1, gridWidth, gridHeight, dark, visited, queue);
            }

            int componentWidth = (maxX - minX + 1) * step;
            int componentHeight = (maxY - minY + 1) * step;
            float fill = count / (float) Math.max(
                    1, (maxX - minX + 1) * (maxY - minY + 1));
            float darkAreaRatio = count * step * step / (float) Math.max(1, width * height);
            // Location text can make the bubble short, tall, narrow or wide.
            // Judge one contiguous filled area instead of its aspect ratio.
            if (componentWidth > width * 0.65f
                    || componentHeight < height * 0.025f
                    || componentHeight > height * 0.12f
                    || fill < 0.42f
                    || darkAreaRatio < 0.0045f
                    || darkAreaRatio > 0.045f) {
                continue;
            }

            int pixelLeft = left + minX * step;
            int pixelTop = top + minY * step;
            int pixelRight = Math.min(width, left + (maxX + 1) * step);
            int pixelBottom = Math.min(height, top + (maxY + 1) * step);
            ForegroundStats foreground = brightForegroundStats(
                    pixelLeft, pixelTop, pixelRight, pixelBottom, step, pixelAt);
            if (foreground.ratio() < 0.012f
                    || foreground.widthCoverage() < 0.35f
                    || foreground.heightCoverage() < 0.25f) {
                continue;
            }
            int centerX = (pixelLeft + pixelRight) / 2;
            int centerY = (pixelTop + pixelBottom) / 2;
            float centerPenalty = Math.abs(centerX - width / 2f) / width
                    + Math.abs(centerY - height * 0.43f) / height;
            float confidence = fill
                    + Math.min(0.18f, foreground.ratio() * 2f)
                    - centerPenalty * 0.10f;
            Target candidate = new Target(
                    centerX, centerY, pixelRight - pixelLeft, pixelBottom - pixelTop, confidence);
            if (best == null || candidate.confidence() > best.confidence()) {
                best = candidate;
            }
        }
        return best;
    }

    private static void enqueue(
            int x,
            int y,
            int width,
            int height,
            boolean[] dark,
            boolean[] visited,
            ArrayDeque<Integer> queue) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = y * width + x;
        if (dark[index] && !visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static boolean isDarkNeutral(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        int chroma = Math.max(red, Math.max(green, blue))
                - Math.min(red, Math.min(green, blue));
        return luminance <= 118 && chroma <= 55;
    }

    private record ForegroundStats(float ratio, float widthCoverage, float heightCoverage) {}

    private static ForegroundStats brightForegroundStats(
            int left,
            int top,
            int right,
            int bottom,
            int step,
            IntBinaryOperator pixelAt) {
        int sampled = 0;
        int bright = 0;
        int minBrightX = right;
        int maxBrightX = left;
        int minBrightY = bottom;
        int maxBrightY = top;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                sampled++;
                if (red >= 205 && green >= 205 && blue >= 205) {
                    bright++;
                    minBrightX = Math.min(minBrightX, x);
                    maxBrightX = Math.max(maxBrightX, x);
                    minBrightY = Math.min(minBrightY, y);
                    maxBrightY = Math.max(maxBrightY, y);
                }
            }
        }
        if (sampled == 0 || bright == 0) {
            return new ForegroundStats(0f, 0f, 0f);
        }
        float widthCoverage = (maxBrightX - minBrightX + step)
                / (float) Math.max(1, right - left);
        float heightCoverage = (maxBrightY - minBrightY + step)
                / (float) Math.max(1, bottom - top);
        return new ForegroundStats(bright / (float) sampled, widthCoverage, heightCoverage);
    }
}
