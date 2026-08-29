package com.pikminx.helper;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/** 以像素顏色與位置找出遊戲中的高亮花盆及開始按鈕。 */
final class CardHighlight {
    private static final double LEFT = 0.08;
    // 未滾動時第一列花盆可能位於畫面中段以上；不要用固定下半屏限制排除它。
    private static final double TOP = 0.22;
    private static final double RIGHT = 0.97;
    private static final double BOTTOM = 0.98;

    private CardHighlight() {}

    record Point(int x, int y) {}

    record Bounds(int left, int top, int right, int bottom) {}

    record MapEntryMatch(Point point, Point anchor, Bounds searchBounds, int score) {}

    record PlantingMenuControls(Point startControl, Point stopControl) {}

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

    /** 只以右下哨子為入口錨點，按畫面比例推算其正上方的種花入口。 */
    static MapEntryMatch findMapPlantingEntryAboveWhistle(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int left = Math.round(width * 0.76f);
        int top = Math.round(height * 0.78f);
        int right = Math.min(width - 1, Math.round(width * 0.98f));
        int bottom = Math.min(height - 1, Math.round(height * 0.97f));
        Bounds searchBounds = new Bounds(left, top, right, bottom);
        int regionWidth = right - left + 1;
        int regionHeight = bottom - top + 1;
        boolean[] visited = new boolean[regionWidth * regionHeight];
        int[] queue = new int[visited.length];
        Point bestAnchor = null;
        int bestScore = -1;
        int minimumBodyPixels = Math.max(50, width * height / 6000);
        for (int localY = 0; localY < regionHeight; localY++) {
            for (int localX = 0; localX < regionWidth; localX++) {
                int start = localY * regionWidth + localX;
                if (visited[start]) {
                    continue;
                }
                visited[start] = true;
                if (!isWhistleLight(pixelAt.applyAsInt(left + localX, top + localY))) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                int pixels = 0;
                int sumX = 0;
                int sumY = 0;
                int minX = localX;
                int maxX = localX;
                int minY = localY;
                int maxY = localY;
                queue[tail++] = start;
                while (head < tail) {
                    int current = queue[head++];
                    int x = current % regionWidth;
                    int y = current / regionWidth;
                    pixels++;
                    sumX += left + x;
                    sumY += top + y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    if (x > 0) {
                        tail = enqueueWhistleLight(
                                current - 1, left, top, regionWidth,
                                visited, queue, tail, pixelAt);
                    }
                    if (x + 1 < regionWidth) {
                        tail = enqueueWhistleLight(
                                current + 1, left, top, regionWidth,
                                visited, queue, tail, pixelAt);
                    }
                    if (y > 0) {
                        tail = enqueueWhistleLight(
                                current - regionWidth, left, top, regionWidth,
                                visited, queue, tail, pixelAt);
                    }
                    if (y + 1 < regionHeight) {
                        tail = enqueueWhistleLight(
                                current + regionWidth, left, top, regionWidth,
                                visited, queue, tail, pixelAt);
                    }
                }
                if (pixels < minimumBodyPixels) {
                    continue;
                }
                int padding = Math.max(4, width / 100);
                int cyanPixels = countWhistleCyan(
                        left + Math.max(0, minX - padding),
                        top + Math.max(0, minY - padding),
                        left + Math.min(regionWidth - 1, maxX + padding),
                        top + Math.min(regionHeight - 1, maxY + padding),
                        pixelAt);
                int score = pixels + cyanPixels * 2;
                if (cyanPixels >= Math.max(8, pixels / 30) && score > bestScore) {
                    bestAnchor = new Point(sumX / pixels, sumY / pixels);
                    bestScore = score;
                }
            }
        }
        if (bestAnchor == null) {
            return new MapEntryMatch(null, null, searchBounds, -1);
        }
        Point entry = new Point(
                bestAnchor.x(),
                Math.max(0, bestAnchor.y() - Math.round(height * 0.173f)));
        return new MapEntryMatch(entry, bestAnchor, searchBounds, bestScore);
    }

    private static int enqueueWhistleLight(
            int index,
            int left,
            int top,
            int regionWidth,
            boolean[] visited,
            int[] queue,
            int tail,
            IntBinaryOperator pixelAt) {
        if (visited[index]) {
            return tail;
        }
        visited[index] = true;
        int x = index % regionWidth;
        int y = index / regionWidth;
        if (isWhistleLight(pixelAt.applyAsInt(left + x, top + y))) {
            queue[tail++] = index;
        }
        return tail;
    }

    private static int countWhistleCyan(
            int left,
            int top,
            int right,
            int bottom,
            IntBinaryOperator pixelAt) {
        int count = 0;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (isWhistleCyan(pixelAt.applyAsInt(x, y))) {
                    count++;
                }
            }
        }
        return count;
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

    /** 尋找展開或收合種花面板中的開始按鈕中心點。 */
    static Point findStartButton(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int xStart = Math.round(width * 0.34f);
        int xEnd = Math.round(width * 0.47f);
        int yStart = Math.round(height * 0.17f);
        int yEnd = Math.round(height * 0.64f);
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

    /** 尋找種花面板中「白色方塊／橘黃底」的停止控制項。 */
    static Point findStopButton(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int xStart = Math.round(width * 0.34f);
        int xEnd = Math.round(width * 0.47f);
        // 面板展開時按鈕位於畫面上方，收合時會下移到畫面中段。
        int yStart = Math.round(height * 0.16f);
        int yEnd = Math.round(height * 0.64f);
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        int step = Math.max(4, width / 160);
        for (int y = yStart; y <= yEnd; y += step) {
            for (int x = xStart; x <= xEnd; x += step) {
                if (isLight(pixelAt.applyAsInt(x, y))
                        && isWarmStop(pixelAt.applyAsInt(x - xRadius, y))
                        && isWarmStop(pixelAt.applyAsInt(x + xRadius, y))
                        && isWarmStop(pixelAt.applyAsInt(x, y - yRadius))
                        && isWarmStop(pixelAt.applyAsInt(x, y + yRadius))) {
                    return refineLightCenter(
                            x, y, width, height, xRadius, yRadius, pixelAt);
                }
            }
        }
        return null;
    }

    /** 以花瓣顏色列為唯一錨點，只在其相對上方尋找開始／停止鈕。 */
    static PlantingMenuControls findPlantingMenuControls(
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int minimumColorPixels = Math.max(5, width / 100);
        int yStart = Math.round(height * 0.20f);
        int yEnd = Math.round(height * 0.80f);
        for (int y = yStart; y <= yEnd; y++) {
            int yellowX = colorCenterAt(
                    y, width, 0.11f, 0.21f, minimumColorPixels,
                    CardHighlight::isPetalYellow, pixelAt);
            int redX = colorCenterAt(
                    y, width, 0.19f, 0.29f, minimumColorPixels,
                    CardHighlight::isPetalRed, pixelAt);
            int blueX = colorCenterAt(
                    y, width, 0.27f, 0.38f, minimumColorPixels,
                    CardHighlight::isPetalBlue, pixelAt);
            if (yellowX < 0 || redX < 0 || blueX < 0) {
                continue;
            }
            int firstSpacing = redX - yellowX;
            int secondSpacing = blueX - redX;
            if (firstSpacing < width * 0.05f
                    || firstSpacing > width * 0.12f
                    || secondSpacing < width * 0.05f
                    || secondSpacing > width * 0.12f
                    || Math.abs(firstSpacing - secondSpacing) > width * 0.035f
                    || !isLightSelectorStrip(width, height, y, pixelAt)) {
                continue;
            }

            Bounds controlSearch = new Bounds(
                    Math.max(0, blueX + Math.round(width * 0.02f)),
                    Math.max(0, y - Math.round(height * 0.16f)),
                    Math.min(width - 1, blueX + Math.round(width * 0.16f)),
                    Math.max(0, y - Math.round(height * 0.04f)));
            Point start = findStartButton(width, height, controlSearch, pixelAt);
            Point stop = findStopButton(width, height, controlSearch, pixelAt);
            if (isPlantingPanelControlRow(width, height, start, pixelAt)) {
                return new PlantingMenuControls(start, null);
            }
            if (isPlantingPanelControlRow(width, height, stop, pixelAt)) {
                return new PlantingMenuControls(null, stop);
            }
        }
        return null;
    }

    private static Point findStartButton(
            int width,
            int height,
            Bounds bounds,
            IntBinaryOperator pixelAt) {
        return findControl(width, height, bounds, CardHighlight::isGreen, pixelAt);
    }

    private static Point findStopButton(
            int width,
            int height,
            Bounds bounds,
            IntBinaryOperator pixelAt) {
        return findControl(width, height, bounds, CardHighlight::isWarmStop, pixelAt);
    }

    private static Point findControl(
            int width,
            int height,
            Bounds bounds,
            IntPredicate ringColor,
            IntBinaryOperator pixelAt) {
        int xRadius = Math.max(12, Math.round(width * 0.035f));
        int yRadius = Math.max(12, Math.round(height * 0.017f));
        int step = Math.max(4, width / 160);
        for (int y = bounds.top(); y <= bounds.bottom(); y += step) {
            for (int x = bounds.left(); x <= bounds.right(); x += step) {
                if (x - xRadius < 0 || x + xRadius >= width
                        || y - yRadius < 0 || y + yRadius >= height) {
                    continue;
                }
                if (isLight(pixelAt.applyAsInt(x, y))
                        && ringColor.test(pixelAt.applyAsInt(x - xRadius, y))
                        && ringColor.test(pixelAt.applyAsInt(x + xRadius, y))
                        && ringColor.test(pixelAt.applyAsInt(x, y - yRadius))
                        && ringColor.test(pixelAt.applyAsInt(x, y + yRadius))) {
                    return refineLightCenter(
                            x, y, width, height, xRadius, yRadius, pixelAt);
                }
            }
        }
        return null;
    }

    private static int colorCenterAt(
            int y,
            int width,
            float leftFraction,
            float rightFraction,
            int minimumPixels,
            IntPredicate colorPredicate,
            IntBinaryOperator pixelAt) {
        int sumX = 0;
        int count = 0;
        int left = Math.round(width * leftFraction);
        int right = Math.round(width * rightFraction);
        for (int x = left; x <= right; x++) {
            if (colorPredicate.test(pixelAt.applyAsInt(x, y))) {
                sumX += x;
                count++;
            }
        }
        return count >= minimumPixels ? sumX / count : -1;
    }

    private static boolean isLightSelectorStrip(
            int width,
            int height,
            int y,
            IntBinaryOperator pixelAt) {
        for (float xFraction : new float[] {0.46f, 0.58f, 0.70f, 0.82f}) {
            if (panelSurfaceAt(
                    Math.round(width * xFraction), y, width, height, pixelAt) != 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * 確認中央開始／停止鈕位於種花面板的固定控制列。
     * 白色是尚未種花面板，紅色是已種花面板；數量與文字不參與判斷。
     */
    static boolean isPlantingPanelControlRow(
            int width,
            int height,
            Point control,
            IntBinaryOperator pixelAt) {
        if (control == null) {
            return false;
        }
        int expectedSurface = 0;
        for (float xFraction : new float[] {0.03f, 0.29f, 0.71f, 0.97f}) {
            int surface = panelSurfaceAt(
                    Math.round(width * xFraction), control.y(), width, height, pixelAt);
            if (surface == 0 || (expectedSurface != 0 && surface != expectedSurface)) {
                return false;
            }
            expectedSurface = surface;
        }
        return true;
    }

    private static int panelSurfaceAt(
            int centerX,
            int centerY,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int xRadius = Math.max(2, Math.round(width * 0.006f));
        int yRadius = Math.max(2, Math.round(height * 0.004f));
        int light = 0;
        int active = 0;
        int total = 0;
        for (int y = Math.max(0, centerY - yRadius);
                y <= Math.min(height - 1, centerY + yRadius);
                y++) {
            for (int x = Math.max(0, centerX - xRadius);
                    x <= Math.min(width - 1, centerX + xRadius);
                    x++) {
                int color = pixelAt.applyAsInt(x, y);
                if (isPlantingPanelLight(color)) {
                    light++;
                }
                if (isPlantingPanelActive(color)) {
                    active++;
                }
                total++;
            }
        }
        int required = total * 3 / 4;
        if (light >= required) {
            return 1;
        }
        return active >= required ? 2 : 0;
    }

    /** 將粗略命中點收旂到白色方塊的實際重心。 */
    private static Point refineLightCenter(
            int centerX,
            int centerY,
            int width,
            int height,
            int xRadius,
            int yRadius,
            IntBinaryOperator pixelAt) {
        int halfWidth = Math.max(4, xRadius / 2);
        int halfHeight = Math.max(4, yRadius / 2);
        int sumX = 0;
        int sumY = 0;
        int count = 0;
        for (int y = Math.max(0, centerY - halfHeight);
                y <= Math.min(height - 1, centerY + halfHeight);
                y++) {
            for (int x = Math.max(0, centerX - halfWidth);
                    x <= Math.min(width - 1, centerX + halfWidth);
                    x++) {
                if (isLight(pixelAt.applyAsInt(x, y))) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        return count == 0 ? new Point(centerX, centerY) : new Point(sumX / count, sumY / count);
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

    private static boolean isPlantingPanelLight(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        return minimum >= 220 && maximum - minimum <= 45;
    }

    private static boolean isPlantingPanelActive(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 220
                && green <= 190
                && blue <= 190
                && red >= green + 45
                && red >= blue + 35;
    }

    private static boolean isWhistleLight(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return Math.min(red, Math.min(green, blue)) >= 185
                && Math.max(red, Math.max(green, blue))
                        - Math.min(red, Math.min(green, blue)) <= 45;
    }

    private static boolean isWhistleCyan(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return green >= 130
                && blue >= 130
                && green >= red + 20
                && blue >= red + 20;
    }

    /** 判斷像素是否符合綠色開始按鈕的色相特徵。 */
    private static boolean isGreen(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return green >= 150 && green >= red + 35 && green >= blue + 25;
    }

    /** 排除粉紅面板本身，只接受停止鈕的橘黃暖色環。 */
    private static boolean isWarmStop(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 220
                && green >= 130
                && green <= 225
                && blue <= 160
                && red >= green + 30
                && green >= blue + 35;
    }

    private static boolean isPetalYellow(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 210 && green >= 165 && blue <= 120 && red >= blue + 90;
    }

    private static boolean isPetalRed(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 220 && green <= 145 && blue <= 170 && red >= green + 70;
    }

    private static boolean isPetalBlue(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return blue >= 150 && blue >= red + 25 && blue >= green + 10;
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
