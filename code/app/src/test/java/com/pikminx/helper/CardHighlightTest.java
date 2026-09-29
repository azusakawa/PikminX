package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.IntBinaryOperator;

import org.junit.Test;

public final class CardHighlightTest {
    @Test
    public void slidingDarkControlMatchesReferenceAcrossScreenSizesAndBoundaries() {
        for (Scene scene : new Scene[] {
                centeredScene(432, 936),
                centeredScene(1280, 2772),
                boundaryScene(720, 1280, true),
                boundaryScene(720, 1280, false)
        }) {
            CountingReader referenceReader = new CountingReader(scene);
            CardHighlight.Point expected = referenceCloseButton(
                    scene.width, scene.height, referenceReader);

            CountingReader optimizedReader = new CountingReader(scene);
            CardHighlight.Point actual = CardHighlight.findPetalSearchCloseButton(
                    scene.width, scene.height, optimizedReader);

            assertTrue(scene.description + " should find the dark control", actual != null);
            assertEquals(scene.description, expected, actual);
            assertTrue(
                    scene.description + " should reduce repeated ROI reads optimized="
                            + optimizedReader.calls + " reference=" + referenceReader.calls,
                    optimizedReader.calls < referenceReader.calls);
        }
    }

    @Test
    public void negativeAndPartialDarkEvidenceRemainRejected() {
        Scene noDark = selectorOnlyScene(432, 936, 500);
        assertNull(CardHighlight.findPetalSearchCloseButton(
                noDark.width, noDark.height, new CountingReader(noDark)));

        int radius = Math.max(8, Math.round(noDark.width * 0.035f));
        int centerX = Math.round(noDark.width * 0.08f);
        Scene partial = noDark.withDarkRegion(
                centerX - 1,
                centerX + 1,
                430,
                430 + Math.max(1, radius / 2) - 1);
        assertNull(CardHighlight.findPetalSearchCloseButton(
                partial.width, partial.height, new CountingReader(partial)));
    }

    private static Scene centeredScene(int width, int height) {
        int selectorY = Math.round(height * 0.34f);
        int radius = Math.max(8, Math.round(width * 0.035f));
        int centerX = Math.round(width * 0.08f);
        return selectorOnlyScene(width, height, selectorY).withDarkRegion(
                centerX - radius,
                centerX + radius,
                selectorY - Math.round(height * 0.055f) - radius,
                selectorY - Math.round(height * 0.055f) + radius);
    }

    private static Scene boundaryScene(int width, int height, boolean first) {
        int selectorY = Math.round(height * 0.34f);
        int radius = Math.max(8, Math.round(width * 0.035f));
        int centerX = Math.round(width * 0.08f);
        int minimumGap = Math.max(8, Math.round(height * 0.025f));
        int maximumGap = Math.max(minimumGap + 1, Math.round(height * 0.09f));
        int startY = Math.max(radius, selectorY - maximumGap);
        int endY = Math.min(height - radius - 1, selectorY - minimumGap);
        int bestY = first ? startY : endY;
        return selectorOnlyScene(width, height, selectorY).withDarkRegion(
                centerX - radius,
                centerX + radius,
                bestY - radius,
                bestY + radius);
    }

    private static Scene selectorOnlyScene(int width, int height, int selectorY) {
        return new Scene(width, height, selectorY, 0, -1, 0, -1, "selector-only");
    }

    private static CardHighlight.Point referenceCloseButton(
            int width, int height, IntBinaryOperator pixelAt) {
        int selectorY = referenceSelectorY(width, height, pixelAt);
        if (selectorY < 0) {
            return null;
        }
        int minimumGap = Math.max(8, Math.round(height * 0.025f));
        int maximumGap = Math.max(minimumGap + 1, Math.round(height * 0.09f));
        CardHighlight.Point magnifier = referenceFindNeutralDarkControl(
                width,
                height,
                0.08f,
                selectorY - maximumGap,
                selectorY - minimumGap,
                pixelAt);
        return magnifier == null
                ? null
                : new CardHighlight.Point(Math.round(width * 0.91f), magnifier.y());
    }

    private static CardHighlight.Point referenceFindNeutralDarkControl(
            int width,
            int height,
            float xFraction,
            int requestedStartY,
            int requestedEndY,
            IntBinaryOperator pixelAt) {
        int centerX = Math.round(width * xFraction);
        int radius = Math.max(8, Math.round(width * 0.035f));
        int startY = Math.max(radius, requestedStartY);
        int endY = Math.min(height - radius - 1, requestedEndY);
        if (startY > endY) {
            return null;
        }
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
        return count == 0 ? null : new CardHighlight.Point(sumX / count, sumY / count);
    }

    private static int referenceSelectorY(
            int width, int height, IntBinaryOperator pixelAt) {
        int minimumColorPixels = Math.max(5, width / 100);
        int startY = Math.round(height * 0.10f);
        int endY = Math.round(height * 0.55f);
        int runStart = -1;
        int bestStart = -1;
        int bestEnd = -1;
        for (int y = startY; y <= endY; y++) {
            boolean matches = referenceColorCenterAt(
                    y, width, 0.11f, 0.21f, minimumColorPixels,
                    PixelKind.YELLOW, pixelAt) >= 0
                    && referenceColorCenterAt(
                            y, width, 0.19f, 0.29f, minimumColorPixels,
                            PixelKind.RED, pixelAt) >= 0
                    && referenceColorCenterAt(
                            y, width, 0.27f, 0.38f, minimumColorPixels,
                            PixelKind.BLUE, pixelAt) >= 0;
            if (matches) {
                if (runStart < 0) {
                    runStart = y;
                }
                continue;
            }
            if (runStart >= 0 && y - 1 - runStart > bestEnd - bestStart) {
                bestStart = runStart;
                bestEnd = y - 1;
            }
            runStart = -1;
        }
        if (runStart >= 0 && endY - runStart > bestEnd - bestStart) {
            bestStart = runStart;
            bestEnd = endY;
        }
        return bestStart < 0 ? -1 : (bestStart + bestEnd) / 2;
    }

    private static int referenceColorCenterAt(
            int y,
            int width,
            float leftFraction,
            float rightFraction,
            int minimumPixels,
            PixelKind kind,
            IntBinaryOperator pixelAt) {
        int sumX = 0;
        int count = 0;
        int left = Math.round(width * leftFraction);
        int right = Math.round(width * rightFraction);
        for (int x = left; x <= right; x++) {
            if (kind.matches(pixelAt.applyAsInt(x, y))) {
                sumX += x;
                count++;
            }
        }
        return count >= minimumPixels ? sumX / count : -1;
    }

    private static boolean isNeutralDark(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return Math.max(red, Math.max(green, blue)) < 180
                && Math.max(red, Math.max(green, blue))
                        - Math.min(red, Math.min(green, blue)) <= 35;
    }

    private enum PixelKind {
        YELLOW {
            @Override
            boolean matches(int color) {
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                return red >= 210 && green >= 165 && blue <= 120 && red >= blue + 90;
            }
        },
        RED {
            @Override
            boolean matches(int color) {
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                return red >= 220 && green <= 145 && blue <= 170 && red >= green + 70;
            }
        },
        BLUE {
            @Override
            boolean matches(int color) {
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                return blue >= 150 && blue >= red + 25 && blue >= green + 10;
            }
        };

        abstract boolean matches(int color);
    }

    private static final class CountingReader implements IntBinaryOperator {
        private final Scene scene;
        private long calls;

        private CountingReader(Scene scene) {
            this.scene = scene;
        }

        @Override
        public int applyAsInt(int x, int y) {
            calls++;
            return scene.pixel(x, y);
        }
    }

    private static final class Scene {
        private final int width;
        private final int height;
        private final int selectorY;
        private final int darkLeft;
        private final int darkRight;
        private final int darkTop;
        private final int darkBottom;
        private final String description;

        private Scene(
                int width,
                int height,
                int selectorY,
                int darkLeft,
                int darkRight,
                int darkTop,
                int darkBottom,
                String description) {
            this.width = width;
            this.height = height;
            this.selectorY = selectorY;
            this.darkLeft = darkLeft;
            this.darkRight = darkRight;
            this.darkTop = darkTop;
            this.darkBottom = darkBottom;
            this.description = description;
        }

        private Scene withDarkRegion(int left, int right, int top, int bottom) {
            return new Scene(
                    width,
                    height,
                    selectorY,
                    left,
                    right,
                    top,
                    bottom,
                    "size=" + width + "x" + height + " dark=" + left + "," + top);
        }

        private int pixel(int x, int y) {
            if (Math.abs(y - selectorY) <= 1) {
                if (inRange(x, 0.11f, 0.21f)) {
                    return 0xffffc000;
                }
                if (inRange(x, 0.19f, 0.29f)) {
                    return 0xffff5960;
                }
                if (inRange(x, 0.27f, 0.38f)) {
                    return 0xff3788cf;
                }
            }
            if (x >= darkLeft && x <= darkRight && y >= darkTop && y <= darkBottom) {
                return 0xff555555;
            }
            return 0xff4d9f68;
        }

        private boolean inRange(int x, float leftFraction, float rightFraction) {
            return x >= Math.round(width * leftFraction)
                    && x <= Math.round(width * rightFraction);
        }
    }
}
