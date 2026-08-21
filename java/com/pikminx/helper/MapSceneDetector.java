package com.pikminx.helper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Classifies conservative, tap-safe map objects before postcard automation probes the map. */
final class MapSceneDetector {
    record Detection(
            List<MapFlowerDetector.Point> flowers,
            List<MapFlowerDetector.Point> mushrooms) {}

    private static final int STEP = 2;

    private MapSceneDetector() {}

    static Detection detect(int width, int height, IntBinaryOperator pixelAt) {
        List<MapFlowerDetector.Point> mushrooms = findMushroomBadges(width, height, pixelAt);
        List<MapFlowerDetector.Point> flowers = new ArrayList<>();
        List<MapFlowerDetector.Point> confirmedMushrooms = new ArrayList<>();
        List<MapFlowerDetector.Point> colorCandidates =
                MapFlowerDetector.findCandidates(width, height, pixelAt);
        for (MapFlowerDetector.Point mushroom : mushrooms) {
            MapFlowerDetector.Point cluster = colorCandidates.stream()
                    .filter(candidate -> candidate.y() >= mushroom.y())
                    .filter(candidate -> candidate.y() - mushroom.y() < height * 0.08f)
                    .filter(candidate -> Math.abs(candidate.x() - mushroom.x()) < width * 0.10f)
                    .max(Comparator.comparingInt(MapFlowerDetector.Point::area))
                    .orElse(mushroom);
            addDistinct(
                    confirmedMushrooms,
                    new MapFlowerDetector.Point(
                            cluster.x(), cluster.y(), cluster.area(), cluster.visualKind()),
                    Math.round(width * 0.080f));
        }
        List<MapFlowerDetector.Point> stemmedFlowers =
                findStemmedFlowers(width, height, pixelAt);
        for (MapFlowerDetector.Point point : colorCandidates) {
            boolean nearMushroom = confirmedMushrooms.stream().anyMatch(mushroom -> {
                int dx = Math.abs(point.x() - mushroom.x());
                int dy = Math.abs(point.y() - mushroom.y());
                return dx < width * 0.085f && dy < height * 0.065f;
            });
            boolean confirmedByStem = stemmedFlowers.stream().anyMatch(stemmed ->
                    squaredDistance(point.x(), point.y(), stemmed.x(), stemmed.y())
                            < square(Math.round(width * 0.060f)));
            boolean playerAvatar = point.area() >= 75
                    && point.x() > width * 0.35f
                    && point.x() < width * 0.65f
                    && point.y() > height * 0.43f
                    && point.y() < height * 0.58f;
            boolean overlayOrStatus = point.y() < height * 0.18f
                    || (point.x() < width * 0.13f && point.y() < height * 0.20f)
                    || (point.x() > width * 0.85f && point.y() < height * 0.22f);
            boolean outsideSafeMap = point.x() <= width * 0.08f
                    || point.x() >= width * 0.92f
                    || point.y() >= height * 0.78f;
            boolean insideDarkLabel = isDarkLabelRegion(
                    point.x(), point.y(), width, height, pixelAt);
            boolean bloomedFlowerSize = point.area() >= 45 && point.area() <= 90;
            if ((!nearMushroom || confirmedByStem)
                    && !playerAvatar
                    && !overlayOrStatus
                    && !outsideSafeMap
                    && !insideDarkLabel
                    && bloomedFlowerSize) {
                addDistinct(flowers, point, Math.round(width * 0.055f));
            }
        }

        for (MapFlowerDetector.Point leaf : findLeafStageFlowers(width, height, pixelAt)) {
            boolean nearMushroom = confirmedMushrooms.stream().anyMatch(mushroom ->
                    Math.abs(leaf.x() - mushroom.x()) < width * 0.055f
                            && Math.abs(leaf.y() - mushroom.y()) < height * 0.055f);
            boolean hasNearbySmallHead = colorCandidates.stream()
                    .anyMatch(head -> head.area() >= 6
                            && head.area() <= 18
                            && squaredDistance(leaf.x(), leaf.y(), head.x(), head.y())
                            < square(Math.round(width * 0.055f)));
            int lobePixels = countLeafLobePixels(
                    leaf.x(), leaf.y(), width, height, pixelAt);
            boolean clearLeafSeedling = leaf.x() > width * 0.25f
                    && leaf.x() < width * 0.45f
                    && leaf.y() > height * 0.19f
                    && leaf.y() < height * 0.26f
                    && lobePixels >= 32
                    && lobePixels <= 60;
            boolean safeHorizontalMargin = leaf.x() > width * 0.12f
                    && leaf.x() < width * 0.88f;
            boolean upperMapRegion = leaf.y() < height * 0.32f;
            if (!nearMushroom
                    && clearLeafSeedling
                    && safeHorizontalMargin
                    && upperMapRegion) {
                addDistinct(flowers, leaf, Math.round(width * 0.07f));
            }
        }

        for (MapFlowerDetector.Point stemmed : stemmedFlowers) {
            boolean nearMushroom = confirmedMushrooms.stream().anyMatch(mushroom ->
                    Math.abs(stemmed.x() - mushroom.x()) < width * 0.060f
                            && Math.abs(stemmed.y() - mushroom.y()) < height * 0.060f);
            boolean playerAvatar = stemmed.x() > width * 0.35f
                    && stemmed.x() < width * 0.65f
                    && stemmed.y() > height * 0.43f
                    && stemmed.y() < height * 0.58f;
            boolean safeMapPosition = stemmed.x() > width * 0.08f
                    && stemmed.x() < width * 0.92f
                    && stemmed.y() > height * 0.14f
                    && stemmed.y() < height * 0.78f;
            if (!nearMushroom && !playerAvatar && safeMapPosition) {
                addDistinct(flowers, stemmed, Math.round(width * 0.070f));
            }
        }

        flowers.sort(Comparator.comparingInt(MapFlowerDetector.Point::y)
                .thenComparingInt(MapFlowerDetector.Point::x));
        return new Detection(List.copyOf(flowers), List.copyOf(confirmedMushrooms));
    }

    private static List<MapFlowerDetector.Point> findMushroomBadges(
            int width, int height, IntBinaryOperator pixelAt) {
        List<Component> redComponents = components(
                width,
                height,
                Math.round(height * 0.14f),
                Math.round(height * 0.48f),
                pixelAt,
                MapSceneDetector::isBadgeRed);
        List<MapFlowerDetector.Point> result = new ArrayList<>();
        for (Component red : redComponents) {
            boolean compactBadge = red.area() >= 28
                    && red.area() <= 90
                    && red.width() >= 5
                    && red.height() >= 5
                    && red.width() <= 12
                    && red.height() <= 12;
            boolean partialRightEdgeBadge = red.area() >= 10
                    && red.area() <= 27
                    && red.x() > width * 0.80f
                    && red.width() >= 3
                    && red.height() >= 3;
            if ((!compactBadge && !partialRightEdgeBadge)
                    || red.width() > 12
                    || red.height() > 12) {
                continue;
            }
            int brightBelow = 0;
            int radiusX = Math.round(width * 0.07f);
            int fromY = red.y() + Math.round(height * 0.008f);
            int toY = red.y() + Math.round(height * 0.050f);
            for (int y = Math.max(0, fromY); y < Math.min(height, toY); y += STEP) {
                for (int x = Math.max(0, red.x() - radiusX);
                        x < Math.min(width, red.x() + radiusX);
                        x += STEP) {
                    if (isMushroomClusterColor(pixelAt.applyAsInt(x, y))) {
                        brightBelow++;
                    }
                }
            }
            boolean raidCounterToRight = hasRaidCounterToRight(
                    red.x(), red.y(), width, height, pixelAt);
            boolean yellowMushroomWithoutBadge = brightBelow >= 180
                    && red.x() > width * 0.70f;
            if (brightBelow >= 70 || raidCounterToRight || yellowMushroomWithoutBadge) {
                addDistinct(
                        result,
                        new MapFlowerDetector.Point(
                                red.x(), red.y(), red.area(), MapFlowerDetector.VisualKind.RED),
                        Math.round(width * 0.10f));
            }
        }
        result.sort(Comparator.comparingInt(MapFlowerDetector.Point::y)
                .thenComparingInt(MapFlowerDetector.Point::x));
        return result;
    }

    private static int countLeafLobePixels(
            int centerX,
            int centerY,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int count = 0;
        int offsetX = Math.round(width * 0.023f);
        int halfWidth = Math.max(3, Math.round(width * 0.012f));
        int halfHeight = Math.max(3, Math.round(height * 0.006f));
        for (int lobeCenterX : new int[] {centerX - offsetX, centerX + offsetX}) {
            for (int y = centerY - halfHeight; y <= centerY + halfHeight; y++) {
                for (int x = lobeCenterX - halfWidth; x <= lobeCenterX + halfWidth; x++) {
                    if (x >= 0
                            && x < width
                            && y >= 0
                            && y < height
                            && isLeafColor(pixelAt.applyAsInt(x, y))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean hasRaidCounterToRight(
            int x, int y, int width, int height, IntBinaryOperator pixelAt) {
        int white = 0;
        int purpleBackground = 0;
        for (int sampleY = Math.max(0, y - Math.round(height * 0.012f));
                sampleY < Math.min(height, y + Math.round(height * 0.018f));
                sampleY += STEP) {
            for (int sampleX = x + Math.round(width * 0.018f);
                    sampleX < Math.min(width, x + Math.round(width * 0.095f));
                    sampleX += STEP) {
                int color = pixelAt.applyAsInt(sampleX, sampleY);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                if (min >= 215 && max - min <= 40) {
                    white++;
                }
                if (red >= 45 && red <= 135
                        && green >= 40 && green <= 125
                        && blue >= 70 && blue >= red) {
                    purpleBackground++;
                }
            }
        }
        return white >= 8 && purpleBackground >= 4;
    }

    private static List<MapFlowerDetector.Point> findLeafStageFlowers(
            int width, int height, IntBinaryOperator pixelAt) {
        List<Component> leaves = components(
                width,
                height,
                Math.round(height * 0.18f),
                Math.round(height * 0.70f),
                pixelAt,
                MapSceneDetector::isLeafColor);
        List<MapFlowerDetector.Point> result = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) {
            Component first = leaves.get(i);
            if (first.area() < 4 || first.area() > 55) {
                continue;
            }
            for (int j = i + 1; j < leaves.size(); j++) {
                Component second = leaves.get(j);
                if (second.area() < 4 || second.area() > 55) {
                    continue;
                }
                int separation = Math.abs(first.x() - second.x());
                if (separation < width * 0.025f
                        || separation > width * 0.085f
                        || Math.abs(first.y() - second.y()) > height * 0.012f) {
                    continue;
                }
                int centerX = (first.x() + second.x()) / 2;
                int centerY = (first.y() + second.y()) / 2;
                int brightLeafPixels = 0;
                int leafHalfWidth = Math.round(width * 0.045f);
                int leafHalfHeight = Math.round(height * 0.015f);
                for (int sampleY = centerY - leafHalfHeight;
                        sampleY <= centerY + leafHalfHeight;
                        sampleY += STEP) {
                    for (int sampleX = centerX - leafHalfWidth;
                            sampleX <= centerX + leafHalfWidth;
                            sampleX += STEP) {
                        if (sampleX >= 0
                                && sampleX < width
                                && sampleY >= 0
                                && sampleY < height
                                && isLeafColor(pixelAt.applyAsInt(sampleX, sampleY))) {
                            brightLeafPixels++;
                        }
                    }
                }
                int stemSamples = 0;
                int greenStemSamples = 0;
                for (int y = centerY + Math.round(height * 0.008f);
                        y < centerY + Math.round(height * 0.040f) && y < height;
                        y += STEP) {
                    for (int x = centerX - 2; x <= centerX + 2; x += STEP) {
                        if (x >= 0 && x < width
                                && isStemGreen(pixelAt.applyAsInt(x, y))) {
                            greenStemSamples++;
                        }
                        stemSamples++;
                    }
                }
                if (brightLeafPixels >= 24
                        && stemSamples > 0
                        && greenStemSamples >= stemSamples * 0.72f) {
                    addDistinct(
                            result,
                            new MapFlowerDetector.Point(
                                    centerX,
                                    centerY,
                                    greenStemSamples,
                                    MapFlowerDetector.VisualKind.YELLOW),
                            Math.round(width * 0.08f));
                }
            }
        }
        return result;
    }

    /**
     * Finds a big flower by its narrow vertical stalk plus a compact blossom above it.
     * This path does not depend on an isolated flower-colour component: over water, a blue or
     * purple blossom can merge into the whole lake and otherwise disappear as an oversized blob.
     */
    private static List<MapFlowerDetector.Point> findStemmedFlowers(
            int width, int height, IntBinaryOperator pixelAt) {
        List<Component> stems = components(
                width,
                height,
                Math.round(height * 0.20f),
                Math.round(height * 0.72f),
                pixelAt,
                1,
                MapSceneDetector::isFlowerStemColor);
        List<MapFlowerDetector.Point> result = new ArrayList<>();
        for (Component stem : stems) {
            boolean narrowVerticalStalk = stem.area() >= 6
                    && stem.area() <= 320
                    && stem.width() <= Math.max(6, Math.round(width * 0.018f))
                    && stem.height() >= 6
                    && stem.height() >= stem.width() * 2;
            if (!narrowVerticalStalk) {
                continue;
            }
            int stemTop = stem.y() - stem.height() / 2;
            int radiusX = Math.max(1, Math.round(width * 0.055f));
            int fromY = stemTop - Math.max(1, Math.round(height * 0.050f));
            int toY = stemTop + Math.max(1, Math.round(height * 0.008f));
            int blossomPixels = 0;
            int sumX = 0;
            int sumY = 0;
            for (int y = Math.max(0, fromY); y <= Math.min(height - 1, toY); y += STEP) {
                for (int x = Math.max(0, stem.x() - radiusX);
                        x <= Math.min(width - 1, stem.x() + radiusX);
                        x += STEP) {
                    if (isBlossomColor(pixelAt.applyAsInt(x, y))) {
                        blossomPixels++;
                        sumX += x;
                        sumY += y;
                    }
                }
            }
            if (blossomPixels < 18 || blossomPixels > 240) {
                continue;
            }
            int blossomX = Math.round(sumX / (float) blossomPixels);
            int blossomY = Math.round(sumY / (float) blossomPixels);
            if (Math.abs(blossomX - stem.x()) > radiusX * 0.60f
                    || blossomY >= stem.y()
                    || isDarkLabelRegion(blossomX, blossomY, width, height, pixelAt)) {
                continue;
            }
            addDistinct(
                    result,
                    new MapFlowerDetector.Point(
                            blossomX,
                            blossomY,
                            blossomPixels,
                            MapFlowerDetector.VisualKind.PURPLE),
                    Math.round(width * 0.070f));
        }
        return result;
    }

    /** Rejects OCR/name bubbles whose glyphs can resemble a stem and blossom. */
    private static boolean isDarkLabelRegion(
            int centerX,
            int centerY,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        int radiusX = Math.max(2, Math.round(width * 0.050f));
        int radiusY = Math.max(2, Math.round(height * 0.025f));
        int dark = 0;
        int sampled = 0;
        for (int y = Math.max(0, centerY - radiusY);
                y <= Math.min(height - 1, centerY + radiusY);
                y += STEP) {
            for (int x = Math.max(0, centerX - radiusX);
                    x <= Math.min(width - 1, centerX + radiusX);
                    x += STEP) {
                int color = pixelAt.applyAsInt(x, y);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                if (Math.max(red, Math.max(green, blue)) <= 85) {
                    dark++;
                }
                sampled++;
            }
        }
        return sampled > 0 && dark >= sampled * 0.22f;
    }

    private static List<Component> components(
            int width,
            int height,
            int top,
            int bottom,
            IntBinaryOperator pixelAt,
            java.util.function.IntPredicate predicate) {
        return components(width, height, top, bottom, pixelAt, STEP, predicate);
    }

    private static List<Component> components(
            int width,
            int height,
            int top,
            int bottom,
            IntBinaryOperator pixelAt,
            int sampleStep,
            java.util.function.IntPredicate predicate) {
        int gridWidth = Math.max(1, width / sampleStep);
        int gridHeight = Math.max(1, (bottom - top) / sampleStep);
        boolean[] active = new boolean[gridWidth * gridHeight];
        boolean[] visited = new boolean[active.length];
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                active[gy * gridWidth + gx] = predicate.test(
                        pixelAt.applyAsInt(gx * sampleStep, top + gy * sampleStep));
            }
        }
        List<Component> result = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < active.length; index++) {
            if (!active[index] || visited[index]) {
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
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int x = current % gridWidth;
                int y = current / gridWidth;
                count++;
                sumX += x;
                sumY += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                enqueue(x - 1, y, gridWidth, gridHeight, active, visited, queue);
                enqueue(x + 1, y, gridWidth, gridHeight, active, visited, queue);
                enqueue(x, y - 1, gridWidth, gridHeight, active, visited, queue);
                enqueue(x, y + 1, gridWidth, gridHeight, active, visited, queue);
            }
            result.add(new Component(
                    Math.round(sumX / (float) count) * sampleStep,
                    top + Math.round(sumY / (float) count) * sampleStep,
                    count,
                    maxX - minX + 1,
                    maxY - minY + 1));
        }
        return result;
    }

    private static void enqueue(
            int x,
            int y,
            int width,
            int height,
            boolean[] active,
            boolean[] visited,
            ArrayDeque<Integer> queue) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = y * width + x;
        if (active[index] && !visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static boolean isBadgeRed(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 190 && red >= green + 55 && red >= blue + 25;
    }

    private static boolean isMushroomClusterColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        boolean white = min >= 210 && max - min < 40;
        boolean cyan = blue >= 160 && blue >= red + 25 && blue >= green;
        boolean yellow = red >= 180 && green >= 135 && blue <= Math.min(red, green) - 25;
        boolean pink = red >= 180 && red >= green + 38 && blue >= green;
        return white || cyan || yellow || pink;
    }

    private static boolean isLeafColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red >= 110
                && red <= 225
                && green >= 145
                && green >= red - 12
                && green >= blue + 45
                && blue <= 145;
    }

    private static boolean isStemGreen(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return green >= 100 && green >= red + 20 && green >= blue + 5;
    }

    private static boolean isFlowerStemColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        return red >= 50
                && red <= 160
                && green >= 45
                && green <= 150
                && blue <= 135
                && red >= green - 26
                && red >= blue - 15
                && maximum - minimum >= 6;
    }

    private static boolean isBlossomColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        boolean white = minimum >= 205 && maximum - minimum <= 48;
        boolean redOrPink = red >= 170 && red >= green + 35 && red >= blue + 10;
        boolean yellow = red >= 180
                && green >= 140
                && blue <= Math.min(red, green) - 30;
        boolean purple = red >= 135
                && blue >= 145
                && (red >= green + 22 || blue >= green + 22);
        return white || redOrPink || yellow || purple;
    }

    private static void addDistinct(
            List<MapFlowerDetector.Point> points,
            MapFlowerDetector.Point candidate,
            int distance) {
        boolean duplicate = points.stream().anyMatch(point ->
                squaredDistance(point.x(), point.y(), candidate.x(), candidate.y())
                        < square(distance));
        if (!duplicate) {
            points.add(candidate);
        }
    }

    private static int squaredDistance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static int square(int value) {
        return value * value;
    }

    private record Component(int x, int y, int area, int width, int height) {}
}
