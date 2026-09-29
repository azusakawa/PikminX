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

    /** Dispatch-only stricter variant: teal marks must belong to one control. */
    static Target findConnected(int width, int height, IntBinaryOperator pixelAt) {
        if (width <= 0 || height <= 0 || !hasLightDetailSheet(width, height, pixelAt)) {
            return null;
        }

        int left = Math.round(width * 0.12f);
        int right = Math.round(width * 0.88f);
        int top = Math.round(height * 0.64f);
        int bottom = Math.round(height * 0.80f);
        int step = Math.max(1, Math.round(width / 432f));
        int columns = Math.max(0, (right - left + step - 1) / step);
        int rows = Math.max(0, (bottom - top + step - 1) / step);
        boolean[] teal = new boolean[columns * rows];
        int tealCount = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                teal[index] = isTealAccent(pixelAt.applyAsInt(
                        left + column * step, top + row * step));
                if (teal[index]) {
                    tealCount++;
                }
            }
        }
        if (teal.length == 0 || tealCount * 1000 < teal.length * 5) {
            return null;
        }

        boolean[] visited = new boolean[teal.length];
        int[] queue = new int[teal.length];
        Target best = null;
        int bestPixels = 0;
        for (int start = 0; start < teal.length; start++) {
            if (!teal[start] || visited[start]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int minColumn = columns;
            int maxColumn = 0;
            int minRow = rows;
            int maxRow = 0;
            int pixels = 0;
            while (head < tail) {
                int index = queue[head++];
                int row = index / columns;
                int column = index % columns;
                pixels++;
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                    for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                        int nextRow = row + rowOffset;
                        int nextColumn = column + columnOffset;
                        if ((rowOffset == 0 && columnOffset == 0)
                                || nextRow < 0 || nextRow >= rows
                                || nextColumn < 0 || nextColumn >= columns) {
                            continue;
                        }
                        int next = nextRow * columns + nextColumn;
                        if (teal[next] && !visited[next]) {
                            visited[next] = true;
                            queue[tail++] = next;
                        }
                    }
                }
            }

            int controlWidth = (maxColumn - minColumn + 1) * step;
            int controlHeight = (maxRow - minRow + 1) * step;
            float aspect = controlWidth / (float) Math.max(1, controlHeight);
            if (controlWidth < width * 0.28f
                    || controlWidth > width * 0.75f
                    || controlHeight < width * 0.055f
                    || controlHeight > width * 0.34f
                    || aspect < 2.0f
                    || aspect > 9.5f
                    || pixels <= bestPixels) {
                continue;
            }
            bestPixels = pixels;
            int minX = left + minColumn * step;
            int maxX = left + maxColumn * step;
            int minY = top + minRow * step;
            int maxY = top + maxRow * step;
            best = new Target(
                    (minX + maxX + step) / 2,
                    (minY + maxY + step) / 2,
                    controlWidth,
                    controlHeight,
                    pixels / (float) teal.length);
        }
        return best != null
                ? best
                : findFragmentedOutline(width, left, top, step, columns, rows, teal);
    }

    /** Thin outlined controls can split into separate top/bottom strokes after capture scaling. */
    private static Target findFragmentedOutline(
            int width,
            int left,
            int top,
            int step,
            int columns,
            int rows,
            boolean[] teal) {
        int[] runStart = new int[rows];
        int[] runEnd = new int[rows];
        java.util.Arrays.fill(runStart, -1);
        java.util.Arrays.fill(runEnd, -1);
        int minimumRun = Math.max(1, Math.round(width * 0.16f / step));
        for (int row = 0; row < rows; row++) {
            int bestStart = -1;
            int bestEnd = -1;
            int currentStart = -1;
            for (int column = 0; column <= columns; column++) {
                boolean marked = column < columns && teal[row * columns + column];
                if (marked && currentStart < 0) {
                    currentStart = column;
                }
                if ((!marked || column == columns) && currentStart >= 0) {
                    if (column - currentStart > bestEnd - bestStart) {
                        bestStart = currentStart;
                        bestEnd = column - 1;
                    }
                    currentStart = -1;
                }
            }
            if (bestStart >= 0 && bestEnd - bestStart + 1 >= minimumRun) {
                runStart[row] = bestStart;
                runEnd[row] = bestEnd;
            }
        }

        Target best = null;
        int bestArea = 0;
        for (int upper = 0; upper < rows; upper++) {
            if (runStart[upper] < 0) {
                continue;
            }
            for (int lower = upper + 1; lower < rows; lower++) {
                if (runStart[lower] < 0) {
                    continue;
                }
                int controlHeight = (lower - upper + 1) * step;
                if (controlHeight < width * 0.055f || controlHeight > width * 0.34f) {
                    continue;
                }
                float upperCenter = (runStart[upper] + runEnd[upper]) / 2f;
                float lowerCenter = (runStart[lower] + runEnd[lower]) / 2f;
                if (Math.abs(upperCenter - lowerCenter) * step > width * 0.05f) {
                    continue;
                }

                int minColumn = columns;
                int maxColumn = -1;
                int pixels = 0;
                for (int row = upper; row <= lower; row++) {
                    for (int column = 0; column < columns; column++) {
                        if (!teal[row * columns + column]) {
                            continue;
                        }
                        pixels++;
                        minColumn = Math.min(minColumn, column);
                        maxColumn = Math.max(maxColumn, column);
                    }
                }
                int controlWidth = (maxColumn - minColumn + 1) * step;
                float aspect = controlWidth / (float) Math.max(1, controlHeight);
                int centerX = left + (minColumn + maxColumn + 1) * step / 2;
                int area = controlWidth * controlHeight;
                if (controlWidth < width * 0.28f
                        || controlWidth > width * 0.75f
                        || aspect < 2.0f
                        || aspect > 9.5f
                        || centerX < width * 0.30f
                        || centerX > width * 0.70f
                        || area <= bestArea) {
                    continue;
                }
                bestArea = area;
                best = new Target(
                        centerX,
                        top + (upper + lower + 1) * step / 2,
                        controlWidth,
                        controlHeight,
                        pixels / (float) teal.length);
            }
        }
        return best;
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
