package com.pikminx.helper;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Finds the large returned fruit or seedling pot shown in the center of the squad screen. */
final class ReturnRewardDetector {
    private static final float MIN_REWARD_CONFIDENCE = 0.20f;

    enum SquadCloseup {
        SQUAD_CLOSEUP,
        NOT_SQUAD_CLOSEUP,
        UNKNOWN
    }

    record Region(int left, int top, int right, int bottom) {
        Region {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Region must have positive size");
            }
        }
    }

    record Target(int x, int y, int width, int height, float confidence) {
        boolean samePosition(Target other, int screenWidth, int screenHeight) {
            return other != null
                    && Math.abs(x - other.x) <= screenWidth * 0.05f
                    && Math.abs(y - other.y) <= screenHeight * 0.04f;
        }
    }

    private ReturnRewardDetector() {}

    static Target find(int width, int height, IntBinaryOperator pixelAt) {
        return find(width, height, pixelAt, null);
    }

    static Target find(
            int width,
            int height,
            IntBinaryOperator pixelAt,
            Region region) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        int step = Math.max(2, Math.round(width / 216f));
        int left = region == null
                ? Math.round(width * 0.18f) : clamp(region.left(), 0, width);
        int right = region == null
                ? Math.round(width * 0.82f) : clamp(region.right(), 0, width);
        int top = region == null
                ? Math.round(height * 0.52f) : clamp(region.top(), 0, height);
        int bottom = region == null
                ? Math.round(height * 0.70f) : clamp(region.bottom(), 0, height);
        if (right <= left || bottom <= top) {
            return null;
        }
        int gridWidth = Math.max(1, (right - left + step - 1) / step);
        int gridHeight = Math.max(1, (bottom - top + step - 1) / step);
        boolean[] foreground = new boolean[gridWidth * gridHeight];
        boolean[] visited = new boolean[foreground.length];
        for (int gy = 0; gy < gridHeight; gy++) {
            int y = Math.min(height - 1, top + gy * step);
            for (int gx = 0; gx < gridWidth; gx++) {
                int x = Math.min(width - 1, left + gx * step);
                foreground[gy * gridWidth + gx] = isRewardPixel(pixelAt.applyAsInt(x, y));
            }
        }

        Target best = null;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < foreground.length; index++) {
            if (!foreground[index] || visited[index]) {
                continue;
            }
            visited[index] = true;
            queue.add(index);
            int count = 0;
            int minX = gridWidth;
            int maxX = 0;
            int minY = gridHeight;
            int maxY = 0;
            long sumX = 0;
            long sumY = 0;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int gx = current % gridWidth;
                int gy = current / gridWidth;
                count++;
                minX = Math.min(minX, gx);
                maxX = Math.max(maxX, gx);
                minY = Math.min(minY, gy);
                maxY = Math.max(maxY, gy);
                sumX += gx;
                sumY += gy;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dy != 0) {
                            enqueue(gx + dx, gy + dy, gridWidth, gridHeight,
                                    foreground, visited, queue);
                        }
                    }
                }
            }

            int componentWidth = (maxX - minX + 1) * step;
            int componentHeight = (maxY - minY + 1) * step;
            float fill = count / (float) Math.max(
                    1, (maxX - minX + 1) * (maxY - minY + 1));
            float areaRatio = count * step * step / (float) Math.max(1, width * height);
            int centerX = left + Math.round((sumX / (float) count) * step);
            int centerY = Math.min(
                    top + Math.round((sumY / (float) count) * step),
                    top + minY * step + Math.round(componentHeight * 0.45f));
            boolean oversized = componentWidth > width * 0.56f;
            boolean boundedOversized = region != null
                    && oversized
                    && componentWidth <= width * 0.66f
                    && componentHeight >= height * 0.12f
                    && componentWidth <= componentHeight * 1.8f
                    && fill >= 0.50f
                    && fill <= 0.82f
                    && areaRatio >= 0.04f
                    && Math.abs(centerX - (left + right) / 2) <= (right - left) * 0.20f
                    && Math.abs(centerY - (top + bottom) / 2) <= (bottom - top) * 0.20f;
            if (componentWidth < width * 0.08f
                    || (oversized && !boundedOversized)
                    || componentHeight < height * 0.045f
                    || componentHeight > height * 0.26f
                    || (componentWidth > width * 0.38f
                            && componentHeight < height * 0.085f)
                    // Wide sparse foreground is the crowded squad, not one reward object.
                    || (componentWidth > width * 0.45f && fill < 0.28f)
                    || fill < 0.12f
                    || areaRatio < 0.003f
                    || (region == null
                            && (centerX < width * 0.27f
                                    || centerX > width * 0.85f
                                    || centerY < height * 0.43f
                                    || centerY > height * 0.67f))
                    || !hasFieldLikeBackground(
                            width,
                            height,
                            centerX,
                            centerY,
                            pixelAt,
                            boundedOversized ? 0.55f : 0.34f)
                    || looksLikeGift(width, height, centerX, centerY, pixelAt)) {
                continue;
            }
            float expectedCenterX = region == null ? width * 0.50f : (left + right) / 2f;
            float expectedCenterY = region == null ? height * 0.56f : (top + bottom) / 2f;
            float centerPenalty = Math.abs(centerX - expectedCenterX) / width
                    + Math.abs(centerY - expectedCenterY) / height;
            float confidence = areaRatio * 8f + fill * 0.35f - centerPenalty * 0.2f;
            if (confidence < (boundedOversized ? 0.45f : MIN_REWARD_CONFIDENCE)) {
                continue;
            }
            Target candidate = new Target(
                    centerX, centerY, componentWidth, componentHeight, confidence);
            if (best == null || candidate.confidence() > best.confidence()) {
                best = candidate;
            }
        }
        return best;
    }

    static boolean canUseSquadCloseupEvidence(Target detectedReward) {
        return detectedReward == null;
    }

    /** Uses scene scale and edge clipping, not Pikmin/decor colors, to detect the terminal closeup. */
    static SquadCloseup classifySquadCloseup(
            int width, int height, IntBinaryOperator pixelAt) {
        if (width <= 0 || height <= 0 || pixelAt == null) {
            return SquadCloseup.UNKNOWN;
        }
        float upperForeground = foregroundRatio(
                width, height, 0.05f, 0.20f, 0.95f, 0.40f, pixelAt);
        float middleForeground = foregroundRatio(
                width, height, 0.05f, 0.38f, 0.95f, 0.62f, pixelAt);
        float centerForeground = foregroundRatio(
                width, height, 0.15f, 0.32f, 0.85f, 0.62f, pixelAt);
        float leftEdgeForeground = foregroundRatio(
                width, height, 0f, 0.25f, 0.18f, 0.62f, pixelAt);
        float rightEdgeForeground = foregroundRatio(
                width, height, 0.82f, 0.25f, 1f, 0.62f, pixelAt);
        float edgeForeground = Math.max(leftEdgeForeground, rightEdgeForeground);
        if (edgeForeground >= 0.24f
                && Math.min(leftEdgeForeground, rightEdgeForeground) >= 0.16f) {
            return SquadCloseup.SQUAD_CLOSEUP;
        }
        if (upperForeground >= 0.24f
                && middleForeground >= 0.38f
                && (edgeForeground >= 0.18f || centerForeground >= 0.44f)) {
            return SquadCloseup.SQUAD_CLOSEUP;
        }
        if (upperForeground <= 0.18f
                || middleForeground <= 0.30f
                || (edgeForeground <= 0.12f && centerForeground <= 0.36f)) {
            return SquadCloseup.NOT_SQUAD_CLOSEUP;
        }
        return SquadCloseup.UNKNOWN;
    }

    /** 識別回程領取期間出現在畫面下半部的精華容量警告，不進行點擊。 */
    static boolean hasNectarCapacityWarning(
            List<PetalMatcher.Token> tokens, int width, int height) {
        if (tokens == null || width <= 0 || height <= 0) {
            return false;
        }
        boolean nectar = false;
        boolean capacity = false;
        boolean full = false;
        for (PetalMatcher.Token token : tokens) {
            if (token.centerX() < width * 0.10f
                    || token.centerX() > width * 0.90f
                    || token.centerY() < height * 0.35f
                    || token.centerY() > height * 0.85f) {
                continue;
            }
            String text = PetalMatcher.normalize(token.text());
            nectar |= text.contains("精華");
            capacity |= text.contains("攜帶") || text.contains("空間");
            full |= text.contains("滿");
        }
        return nectar && capacity && full;
    }

    private static void enqueue(
            int x,
            int y,
            int width,
            int height,
            boolean[] foreground,
            boolean[] visited,
            ArrayDeque<Integer> queue) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = y * width + x;
        if (foreground[index] && !visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static float foregroundRatio(
            int width,
            int height,
            float leftRatio,
            float topRatio,
            float rightRatio,
            float bottomRatio,
            IntBinaryOperator pixelAt) {
        int step = Math.max(2, Math.round(width / 216f));
        int left = clamp(Math.round(width * leftRatio), 0, width - 1);
        int right = clamp(Math.round(width * rightRatio), 0, width - 1);
        int top = clamp(Math.round(height * topRatio), 0, height - 1);
        int bottom = clamp(Math.round(height * bottomRatio), 0, height - 1);
        int foreground = 0;
        int samples = 0;
        for (int y = top; y <= bottom; y += step) {
            for (int x = left; x <= right; x += step) {
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                if (!(green >= red + 8 && green >= blue + 5 && green >= 60)) {
                    foreground++;
                }
                samples++;
            }
        }
        return samples == 0 ? 0f : foreground / (float) samples;
    }

    private static boolean isRewardPixel(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        boolean grass = green >= 62 && green >= red + 16 && green >= blue + 10;
        boolean greenFruit = green >= 90
                && red * 100 >= green * 55
                && red * 100 <= green * 92
                && blue * 100 <= green * 58;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        return greenFruit
                || (!grass && maximum >= 48 && (maximum - minimum >= 18 || minimum >= 105));
    }

    /** Returned fruit and pots sit in front of the field, unlike postcards held in the squad. */
    private static boolean hasFieldLikeBackground(
            int width,
            int height,
            int centerX,
            int centerY,
            IntBinaryOperator pixelAt,
            float minimumRatio) {
        int step = Math.max(2, Math.round(width / 216f));
        int left = clamp(centerX - Math.round(width * 0.26f), 0, width - 1);
        int right = clamp(centerX + Math.round(width * 0.26f), 0, width - 1);
        int top = clamp(centerY - Math.round(height * 0.16f), 0, height - 1);
        int bottom = clamp(centerY + Math.round(height * 0.16f), 0, height - 1);
        int coreHalfWidth = Math.round(width * 0.12f);
        int coreHalfHeight = Math.round(height * 0.10f);
        int fieldPixels = 0;
        int samples = 0;
        for (int y = top; y <= bottom; y += step) {
            for (int x = left; x <= right; x += step) {
                if (Math.abs(x - centerX) <= coreHalfWidth
                        && Math.abs(y - centerY) <= coreHalfHeight) {
                    continue;
                }
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                if (green >= red + 8 && green >= blue + 5 && green >= 60) {
                    fieldPixels++;
                }
                samples++;
            }
        }
        return samples >= 20 && fieldPixels / (float) samples >= minimumRatio;
    }

    /** Excludes the red-ribbon, neutral-box gift icon that must never be collected as fruit. */
    static boolean looksLikeGift(
            int width,
            int height,
            int centerX,
            int centerY,
            IntBinaryOperator pixelAt) {
        for (float offset : new float[]{-0.075f, -0.05f, -0.025f, 0f, 0.025f, 0.05f, 0.075f}) {
            int shiftedY = centerY + Math.round(height * offset);
            float topRed = ratio(width, height, centerX, shiftedY,
                    -0.075f, -0.10f, 0.075f, -0.02f, pixelAt, true);
            float totalRed = ratio(width, height, centerX, shiftedY,
                    -0.085f, -0.10f, 0.085f, 0.10f, pixelAt, true);
            float neutral = ratio(width, height, centerX, shiftedY,
                    -0.08f, -0.01f, 0.08f, 0.085f, pixelAt, false);
            if (topRed >= 0.11f && topRed <= 0.42f
                    && totalRed >= 0.085f && totalRed <= 0.27f
                    && neutral >= 0.22f) {
                return true;
            }
        }
        return false;
    }

    private static float ratio(
            int width,
            int height,
            int centerX,
            int centerY,
            float leftOffset,
            float topOffset,
            float rightOffset,
            float bottomOffset,
            IntBinaryOperator pixelAt,
            boolean redPixel) {
        int step = Math.max(1, width / 540);
        int left = clamp(Math.round(centerX + width * leftOffset), 0, width - 1);
        int right = clamp(Math.round(centerX + width * rightOffset), 0, width - 1);
        int top = clamp(Math.round(centerY + width * topOffset), 0, height - 1);
        int bottom = clamp(Math.round(centerY + width * bottomOffset), 0, height - 1);
        int matches = 0;
        int samples = 0;
        for (int y = top; y <= bottom; y += step) {
            for (int x = left; x <= right; x += step) {
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                boolean match = redPixel
                        ? red >= 145 && red - Math.max(green, blue) >= 30
                                && red >= green * 1.18f && red >= blue * 1.12f
                        : Math.min(red, Math.min(green, blue)) >= 120
                                && Math.max(red, Math.max(green, blue)) <= 246
                                && Math.max(red, Math.max(green, blue))
                                        - Math.min(red, Math.min(green, blue)) <= 45;
                if (match) {
                    matches++;
                }
                samples++;
            }
        }
        return samples == 0 ? 0f : matches / (float) samples;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
