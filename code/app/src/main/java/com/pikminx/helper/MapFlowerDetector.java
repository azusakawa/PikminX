package com.pikminx.helper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * 地圖花朵圖示本身沒有文字，因此先以本機像素找出彩色花冠候選；候選是否有效、
 * 花名及後續頁面仍全部交由 OCR 確認。
 */
final class MapFlowerDetector {
    enum VisualKind {
        WHITE,
        RED,
        YELLOW,
        BLUE,
        PURPLE,
        MIXED
    }

    record Point(int x, int y, int area, VisualKind visualKind) {}

    private static final int SAMPLE_STEP = 2;

    private MapFlowerDetector() {}

    static List<Point> findCandidates(
            int width, int height, IntBinaryOperator pixelAt) {
        int left = Math.round(width * 0.05f);
        int right = Math.round(width * 0.90f);
        int top = Math.round(height * 0.14f);
        int bottom = Math.round(height * 0.86f);
        int gridWidth = Math.max(1, (right - left) / SAMPLE_STEP);
        int gridHeight = Math.max(1, (bottom - top) / SAMPLE_STEP);
        boolean[] flowerPixel = new boolean[gridWidth * gridHeight];
        boolean[] visited = new boolean[flowerPixel.length];

        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                int x = left + gx * SAMPLE_STEP;
                int y = top + gy * SAMPLE_STEP;
                flowerPixel[gy * gridWidth + gx] = isFlowerColor(pixelAt.applyAsInt(x, y));
            }
        }

        List<Component> components = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < flowerPixel.length; index++) {
            if (!flowerPixel[index] || visited[index]) {
                continue;
            }
            visited[index] = true;
            queue.add(index);
            int count = 0;
            int sumX = 0;
            int sumY = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = 0;
            int whiteCount = 0;
            int redCount = 0;
            int yellowCount = 0;
            int blueCount = 0;
            int purpleCount = 0;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int gx = current % gridWidth;
                int gy = current / gridWidth;
                count++;
                sumX += gx;
                sumY += gy;
                minX = Math.min(minX, gx);
                maxX = Math.max(maxX, gx);
                minY = Math.min(minY, gy);
                maxY = Math.max(maxY, gy);
                VisualKind visualKind = visualKind(pixelAt.applyAsInt(
                        left + gx * SAMPLE_STEP, top + gy * SAMPLE_STEP));
                switch (visualKind) {
                    case WHITE -> whiteCount++;
                    case RED -> redCount++;
                    case YELLOW -> yellowCount++;
                    case BLUE -> blueCount++;
                    case PURPLE -> purpleCount++;
                    default -> { }
                }
                enqueue(gx - 1, gy, gridWidth, gridHeight, flowerPixel, visited, queue);
                enqueue(gx + 1, gy, gridWidth, gridHeight, flowerPixel, visited, queue);
                enqueue(gx, gy - 1, gridWidth, gridHeight, flowerPixel, visited, queue);
                enqueue(gx, gy + 1, gridWidth, gridHeight, flowerPixel, visited, queue);
            }

            int componentWidth = maxX - minX + 1;
            int componentHeight = maxY - minY + 1;
            float fill = count / (float) (componentWidth * componentHeight);
            float aspect = componentWidth / (float) componentHeight;
            if (count >= 6
                    && count <= 260
                    && componentWidth >= 3
                    && componentHeight >= 3
                    && componentWidth <= 35
                    && componentHeight <= 35
                    && fill >= 0.16f
                    && aspect >= 0.35f
                    && aspect <= 2.8f) {
                components.add(new Component(
                        left + Math.round(sumX / (float) count) * SAMPLE_STEP,
                        top + Math.round(sumY / (float) count) * SAMPLE_STEP,
                        count,
                        dominantKind(
                                whiteCount, redCount, yellowCount, blueCount, purpleCount)));
            }
        }

        components.sort(Comparator
                .comparingInt(Component::area).reversed()
                .thenComparingInt(component ->
                        Math.abs(component.x() - width / 2)
                                + Math.abs(component.y() - height / 2)));
        List<Point> result = new ArrayList<>();
        for (Component component : components) {
            boolean duplicate = result.stream().anyMatch(point ->
                    Math.abs(point.x() - component.x()) < 24
                            && Math.abs(point.y() - component.y()) < 24);
            if (!duplicate) {
                result.add(new Point(
                        component.x(),
                        component.y(),
                        component.area(),
                        component.visualKind()));
            }
            if (result.size() == 30) {
                break;
            }
        }
        return result;
    }

    private static void enqueue(
            int x,
            int y,
            int width,
            int height,
            boolean[] flowerPixel,
            boolean[] visited,
            ArrayDeque<Integer> queue) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = y * width + x;
        if (flowerPixel[index] && !visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static boolean isFlowerColor(int color) {
        return visualKind(color) != VisualKind.MIXED;
    }

    private static VisualKind visualKind(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int delta = max - min;
        boolean white = max >= 225 && delta <= 28;
        boolean redOrPink = red >= 175 && red >= green + 42 && red >= blue + 18;
        boolean yellow = red >= 185 && green >= 145 && blue <= Math.min(red, green) - 38;
        boolean blueFlower = blue >= 170 && blue >= red + 35 && blue >= green + 10;
        boolean purple = red >= 135 && blue >= 145 && green + 30 < Math.max(red, blue);
        if (white) {
            return VisualKind.WHITE;
        }
        if (redOrPink) {
            return VisualKind.RED;
        }
        if (yellow) {
            return VisualKind.YELLOW;
        }
        if (blueFlower) {
            return VisualKind.BLUE;
        }
        if (purple) {
            return VisualKind.PURPLE;
        }
        return VisualKind.MIXED;
    }

    private static VisualKind dominantKind(
            int white, int red, int yellow, int blue, int purple) {
        int maximum = Math.max(white, Math.max(red, Math.max(yellow, Math.max(blue, purple))));
        if (maximum == white) {
            return VisualKind.WHITE;
        }
        if (maximum == red) {
            return VisualKind.RED;
        }
        if (maximum == yellow) {
            return VisualKind.YELLOW;
        }
        if (maximum == blue) {
            return VisualKind.BLUE;
        }
        return VisualKind.PURPLE;
    }

    private record Component(int x, int y, int area, VisualKind visualKind) {}
}
