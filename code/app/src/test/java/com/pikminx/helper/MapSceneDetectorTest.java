package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class MapSceneDetectorTest {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 936;

    @Test
    public void excludesThreeMushroomBadgesAndPlayerFromFlowerCandidates() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        drawFlower(pixels, 245, 340, 0xffb862e8);
        drawFlower(pixels, 335, 255, 0xffe63c52);
        drawFlower(pixels, 345, 330, 0xffe63c52);
        drawFlower(pixels, 140, 215, 0xffe5f06a);
        drawMushroom(pixels, 95, 180, 0xff40c5ef);
        drawMushroom(pixels, 45, 250, 0xffef84b7);
        drawMushroom(pixels, 385, 315, 0xfff2d84e);
        drawFlower(pixels, 215, 470, 0xffffffff);

        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertEquals(3, detection.mushrooms().size());
        assertTrue(detection.flowers().stream().noneMatch(point ->
                Math.abs(point.x() - 215) < 20 && Math.abs(point.y() - 470) < 20));
        assertTrue(detection.flowers().stream().noneMatch(point ->
                detection.mushrooms().stream().anyMatch(mushroom ->
                        Math.abs(point.x() - mushroom.x()) < 40
                                && Math.abs(point.y() - mushroom.y()) < 50)));
    }

    @Test
    public void classifiesConfirmedScreenshotCandidateGeometry() {
        List<MapSceneInventory.ObjectMarker> markers = List.of(
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.MUSHROOM, 95, 180),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.MUSHROOM, 45, 250),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.MUSHROOM, 385, 315),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.BIG_FLOWER, 140, 215),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.BIG_FLOWER, 335, 255),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.BIG_FLOWER, 345, 330),
                new MapSceneInventory.ObjectMarker(MapSceneInventory.Kind.BIG_FLOWER, 245, 340));

        assertEquals(4, MapSceneInventory.count(markers).bigFlowers());
        assertEquals(3, MapSceneInventory.count(markers).mushrooms());
    }

    @Test
    public void findsStemmedFlowerWhenPurpleHeadBlendsIntoBlueWater() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        for (int y = 250; y < 440; y++) {
            for (int x = 165; x < 325; x++) {
                pixels[y * WIDTH + x] = 0xff60c4ff;
            }
        }
        drawStemmedFlower(pixels, 245, 330);
        drawMushroom(pixels, 95, 180, 0xff40c5ef);
        drawMushroom(pixels, 45, 250, 0xffef84b7);
        drawMushroom(pixels, 385, 315, 0xfff2d84e);

        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertTrue(detection.flowers().stream().anyMatch(point ->
                Math.abs(point.x() - 245) < 18 && Math.abs(point.y() - 330) < 18));
        assertEquals(3, detection.mushrooms().size());
    }

    @Test
    public void findsWhiteReceiptReturnFlowerWithLowChromaStem() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        for (int y = 250; y < 440; y++) {
            for (int x = 165; x < 325; x++) {
                pixels[y * WIDTH + x] = 0xff60c4ff;
            }
        }
        drawWhiteReceiptReturnFlower(pixels, 245, 335);
        drawMushroom(pixels, 95, 180, 0xff40c5ef);
        drawMushroom(pixels, 385, 315, 0xff40c5ef);

        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertTrue(detection.flowers().stream().anyMatch(point ->
                Math.abs(point.x() - 245) < 18 && Math.abs(point.y() - 335) < 18));
    }

    @Test
    public void rejectsStemLikeGlyphInsideDarkFlowerNameBubble() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        for (int y = 180; y <= 235; y++) {
            for (int x = 315; x <= 390; x++) {
                pixels[y * WIDTH + x] = 0xff252525;
            }
        }
        for (int y = 190; y <= 205; y++) {
            for (int x = 333; x <= 353; x++) {
                pixels[y * WIDTH + x] = 0xffeee8f2;
            }
        }
        for (int y = 206; y <= 231; y++) {
            pixels[y * WIDTH + 343] = 0xff705b52;
        }

        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertTrue(detection.flowers().toString(), detection.flowers().stream().noneMatch(point ->
                Math.abs(point.x() - 343) < 25 && Math.abs(point.y() - 200) < 25));
    }

    @Test
    public void rejectsOversizedOverlayNoticeAsStemmedFlower() {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, 0xff45a85b);
        for (int y = 165; y <= 205; y++) {
            for (int x = 135; x <= 225; x++) {
                pixels[y * WIDTH + x] = 0xfff4edf0;
            }
        }
        for (int y = 206; y <= 250; y++) {
            pixels[y * WIDTH + 180] = 0xff705b52;
        }

        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                WIDTH, HEIGHT, (x, y) -> pixels[y * WIDTH + x]);

        assertTrue(detection.flowers().toString(), detection.flowers().stream().noneMatch(point ->
                Math.abs(point.x() - 180) < 30 && Math.abs(point.y() - 185) < 35));
    }

    private static void drawFlower(int[] pixels, int centerX, int centerY, int color) {
        for (int y = centerY - 9; y <= centerY + 9; y++) {
            for (int x = centerX - 9; x <= centerX + 9; x++) {
                if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                    pixels[y * WIDTH + x] = color;
                }
            }
        }
    }

    private static void drawMushroom(int[] pixels, int centerX, int badgeY, int color) {
        for (int y = badgeY - 8; y <= badgeY + 8; y++) {
            for (int x = centerX - 8; x <= centerX + 8; x++) {
                int dx = x - centerX;
                int dy = y - badgeY;
                if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && dx * dx + dy * dy <= 64) {
                    pixels[y * WIDTH + x] = 0xffef4e62;
                }
            }
        }
        for (int y = badgeY + 10; y < badgeY + 48; y++) {
            for (int x = centerX - 28; x < centerX + 28; x++) {
                if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                    pixels[y * WIDTH + x] = ((x + y) & 3) == 0 ? 0xffffffff : color;
                }
            }
        }
    }

    private static void drawStemmedFlower(int[] pixels, int centerX, int centerY) {
        for (int y = centerY - 14; y <= centerY + 10; y++) {
            for (int x = centerX - 16; x <= centerX + 16; x++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (dx * dx + dy * dy <= 16 * 16) {
                    pixels[y * WIDTH + x] = 0xffc690f0;
                }
            }
        }
        for (int y = centerY - 4; y <= centerY + 4; y++) {
            for (int x = centerX - 4; x <= centerX + 4; x++) {
                pixels[y * WIDTH + x] = 0xff8f4b51;
            }
        }
        for (int y = centerY + 9; y <= centerY + 45; y++) {
            for (int x = centerX - 2; x <= centerX + 1; x++) {
                pixels[y * WIDTH + x] = 0xff6f5d54;
            }
        }
    }

    private static void drawWhiteReceiptReturnFlower(
            int[] pixels, int centerX, int centerY) {
        for (int y = centerY - 12; y <= centerY + 9; y++) {
            for (int x = centerX - 15; x <= centerX + 15; x++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (dx * dx + dy * dy <= 15 * 15) {
                    pixels[y * WIDTH + x] = 0xfff2edf1;
                }
            }
        }
        for (int y = centerY - 4; y <= centerY + 4; y++) {
            for (int x = centerX - 4; x <= centerX + 4; x++) {
                pixels[y * WIDTH + x] = 0xffaa3857;
            }
        }
        for (int y = centerY + 9; y <= centerY + 45; y++) {
            int color = ((y - centerY) / 4) % 2 == 0 ? 0xff5f765d : 0xff72746d;
            for (int x = centerX - 2; x <= centerX + 1; x++) {
                pixels[y * WIDTH + x] = color;
            }
        }
    }
}
