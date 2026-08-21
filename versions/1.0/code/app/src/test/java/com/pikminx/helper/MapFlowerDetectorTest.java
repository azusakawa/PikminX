package com.pikminx.helper;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class MapFlowerDetectorTest {
    @Test
    public void findsCompactRedFlowerAndRejectsLargeBlueBackground() {
        int width = 432;
        int height = 936;
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        for (int y = 390; y < 414; y++) {
            for (int x = 194; x < 218; x++) {
                pixels[y * width + x] = 0xffe63c52;
            }
        }
        for (int y = 140; y < 300; y++) {
            for (int x = 20; x < 170; x++) {
                pixels[y * width + x] = 0xff29aee8;
            }
        }

        List<MapFlowerDetector.Point> candidates = MapFlowerDetector.findCandidates(
                width, height, (x, y) -> pixels[y * width + x]);

        assertTrue(candidates.stream().anyMatch(point ->
                Math.abs(point.x() - 206) <= 4 && Math.abs(point.y() - 402) <= 4));
        assertTrue(candidates.stream().anyMatch(point ->
                point.visualKind() == MapFlowerDetector.VisualKind.RED));
    }
}
