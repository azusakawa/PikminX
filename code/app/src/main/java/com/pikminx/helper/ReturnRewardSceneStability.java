package com.pikminx.helper;

import java.util.function.IntBinaryOperator;

/** Compares coarse luminance samples in the return-reward ROI across frames. */
final class ReturnRewardSceneStability {
    private static final int GRID_SIZE = 8;
    private static final int SAMPLE_COUNT = GRID_SIZE * GRID_SIZE;
    private static final float MAX_MEAN_LUMINANCE_DELTA = 12f;

    private final int[] previousLuminance = new int[SAMPLE_COUNT];
    private boolean hasPrevious;

    /** Returns false for the first observation; later frames are compared to it. */
    boolean observe(
            int width,
            int height,
            IntBinaryOperator pixelAt,
            ReturnRewardDetector.Region region) {
        if (!validInput(width, height, pixelAt, region)) {
            return false;
        }
        int sample = 0;
        long totalDelta = 0;
        for (int row = 0; row < GRID_SIZE; row++) {
            int y = coordinate(region.top(), region.bottom(), row);
            for (int column = 0; column < GRID_SIZE; column++) {
                int x = coordinate(region.left(), region.right(), column);
                int luminance = luminance(pixelAt.applyAsInt(x, y));
                if (hasPrevious) {
                    totalDelta += Math.abs(luminance - previousLuminance[sample]);
                }
                previousLuminance[sample++] = luminance;
            }
        }
        boolean stable = hasPrevious && totalDelta / (float) SAMPLE_COUNT
                < MAX_MEAN_LUMINANCE_DELTA;
        hasPrevious = true;
        return stable;
    }

    void reset() {
        hasPrevious = false;
    }

    private static boolean validInput(
            int width,
            int height,
            IntBinaryOperator pixelAt,
            ReturnRewardDetector.Region region) {
        return width > 0
                && height > 0
                && pixelAt != null
                && region != null
                && region.left() >= 0
                && region.top() >= 0
                && region.right() <= width
                && region.bottom() <= height;
    }

    private static int coordinate(int start, int end, int index) {
        return start + (int) ((long) (end - start - 1) * index / (GRID_SIZE - 1));
    }

    private static int luminance(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return (77 * red + 150 * green + 29 * blue + 128) >>> 8;
    }
}
