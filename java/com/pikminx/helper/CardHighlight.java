package com.pikminx.helper;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;

/** 以像素顏色與位置找出遊戲中的高亮花盆及開始按鈕。 */
final class CardHighlight {
    private static final double LEFT = 0.08;
    // 未滾動時第一列花盆可能位於畫面中段以上；不要用固定下半屏限制排除它。
    private static final double TOP = 0.22;
    private static final double RIGHT = 0.97;
    private static final double BOTTOM = 0.98;

    private CardHighlight() {}

    record Point(int x, int y) {}

    /** 從右側欄位找出收合狀態的花盆搜尋按鈕，不假設固定垂直座標。 */
    static Point findPetalSearchButton(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        return findNeutralDarkControl(width, height, 0.91f, pixelAt);
    }

    /** 搜尋欄展開後，放大鏡會移到欄位左側。 */
    static boolean isPetalSearchOpen(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        return findPetalSearchCloseButton(width, height, pixelAt) != null;
    }

    /** 以左側放大鏡的實際高度定位同列右側 X，不使用 Unity 回報的假輸入框邊界。 */
    static Point findPetalSearchCloseButton(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        Point magnifier = findNeutralDarkControl(width, height, 0.08f, pixelAt);
        return magnifier == null
                ? null
                : new Point(Math.round(width * 0.91f), magnifier.y());
    }

    /** 尋找畫面下方可能的開始種花按鈕中心點。 */
    static Point findStartButton(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int xStart = Math.round(width * 0.34f);
        int xEnd = Math.round(width * 0.47f);
        int yStart = Math.round(height * 0.17f);
        int yEnd = Math.round(height * 0.25f);
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        int step = Math.max(4, width / 160);
        for (int y = yStart; y <= yEnd; y += step) {
            for (int x = xStart; x <= xEnd; x += step) {
                if (isLight(pixelAt.applyAsInt(x, y))
                        && isGreen(pixelAt.applyAsInt(x - xRadius, y))
                        && isGreen(pixelAt.applyAsInt(x + xRadius, y))
                        && isGreen(pixelAt.applyAsInt(x, y - yRadius))
                        && isGreen(pixelAt.applyAsInt(x, y + yRadius))) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    /** 判斷座標是否位於畫面有效範圍內。 */
    static boolean contains(int width, int height, int x, int y) {
        return x >= width * LEFT
                && x <= width * RIGHT
                && y >= height * TOP
                && y <= height * BOTTOM;
    }

    /** 以卡片左右背景的多點中位亮度判斷高亮，避免花盆圖或「＋」按鈕遮住單一取樣點。 */
    static int score(
            int width,
            int height,
            int centerX,
            int centerY,
            IntBinaryOperator pixelAt) {
        int[] xOffsets = {
                Math.max(8, Math.round(width * 0.06f)),
                Math.max(10, Math.round(width * 0.09f))
        };
        int[] yOffsets = {
                Math.max(8, Math.round(height * 0.025f)),
                Math.max(10, Math.round(height * 0.040f)),
                Math.max(12, Math.round(height * 0.055f))
        };
        int[] brightness = new int[xOffsets.length * yOffsets.length * 2];
        int index = 0;
        for (int yOffset : yOffsets) {
            int y = Math.max(0, centerY - yOffset);
            for (int xOffset : xOffsets) {
                brightness[index++] = minimumChannel(pixelAt.applyAsInt(
                        Math.max(0, centerX - xOffset), y));
                brightness[index++] = minimumChannel(pixelAt.applyAsInt(
                        Math.min(width - 1, centerX + xOffset), y));
            }
        }
        Arrays.sort(brightness);
        return brightness[brightness.length / 2];
    }

    /** 取得 RGB 三個通道中的最低亮度。 */
    private static int minimumChannel(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return Math.min(red, Math.min(green, blue));
    }

    /** 判斷像素是否接近白色花盆背景。 */
    private static boolean isLight(int color) {
        return minimumChannel(color) >= 235;
    }

    /** 判斷像素是否符合綠色開始按鈕的色相特徵。 */
    private static boolean isGreen(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return green >= 150 && green >= red + 35 && green >= blue + 25;
    }

    /** 只掃描固定欄位並取深灰圖示的像素重心，避開不同活動版面造成的 Y 位移。 */
    private static Point findNeutralDarkControl(
            int width,
            int height,
            float xFraction,
            IntBinaryOperator pixelAt) {
        int centerX = Math.round(width * xFraction);
        int radius = Math.max(8, Math.round(width * 0.035f));
        int startY = Math.round(height * 0.20f);
        int endY = Math.round(height * 0.52f);
        int bestY = -1;
        int bestCount = 0;
        for (int y = startY; y <= endY; y++) {
            int count = 0;
            for (int sampleY = y - radius; sampleY <= y + radius; sampleY++) {
                for (int sampleX = centerX - radius; sampleX <= centerX + radius; sampleX++) {
                    if (isNeutralDark(pixelAt.applyAsInt(sampleX, sampleY))) {
                        count++;
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestY = y;
            }
        }
        if (bestCount < radius * 2) {
            return null;
        }
        int sumX = 0;
        int sumY = 0;
        int count = 0;
        for (int y = bestY - radius; y <= bestY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (isNeutralDark(pixelAt.applyAsInt(x, y))) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        return count == 0 ? null : new Point(sumX / count, sumY / count);
    }

    private static boolean isNeutralDark(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return Math.max(red, Math.max(green, blue)) < 180
                && Math.max(red, Math.max(green, blue))
                        - Math.min(red, Math.min(green, blue)) <= 35;
    }
}
