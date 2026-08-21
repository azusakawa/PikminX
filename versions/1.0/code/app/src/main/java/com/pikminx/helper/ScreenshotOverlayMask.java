package com.pikminx.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Removes known overlay rectangles from a screenshot copy without changing on-screen views. */
final class ScreenshotOverlayMask {
    record Region(int left, int top, int right, int bottom) {}

    interface PixelBuffer {
        int get(int x, int y);

        void fillRow(int y, int left, int right, int color);
    }

    private ScreenshotOverlayMask() {}

    static void erase(
            int width,
            int height,
            int[] pixels,
            List<Region> regions,
            int padding) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return;
        }
        erase(width, height, new PixelBuffer() {
            @Override
            public int get(int x, int y) {
                return pixels[y * width + x];
            }

            @Override
            public void fillRow(int y, int left, int right, int color) {
                Arrays.fill(pixels, y * width + left, y * width + right, color);
            }
        }, regions, padding);
    }

    static void erase(
            int width,
            int height,
            PixelBuffer pixels,
            List<Region> regions,
            int padding) {
        if (width <= 0
                || height <= 0
                || pixels == null
                || regions == null
                || regions.isEmpty()) {
            return;
        }

        List<Region> merged = new ArrayList<>();
        for (Region region : regions) {
            Region clipped = clip(region, width, height, Math.max(0, padding));
            if (clipped == null) {
                continue;
            }
            mergeInto(merged, clipped);
        }

        for (Region region : merged) {
            eraseRegion(width, pixels, region);
        }
    }

    private static Region clip(Region region, int width, int height, int padding) {
        if (region == null) {
            return null;
        }
        int left = Math.max(0, region.left() - padding);
        int top = Math.max(0, region.top() - padding);
        int right = Math.min(width, region.right() + padding);
        int bottom = Math.min(height, region.bottom() + padding);
        return left < right && top < bottom ? new Region(left, top, right, bottom) : null;
    }

    /** Joins touching icon/capsule rectangles so one overlay cannot be sampled into another. */
    private static void mergeInto(List<Region> regions, Region candidate) {
        for (int index = 0; index < regions.size(); index++) {
            Region existing = regions.get(index);
            if (!touches(existing, candidate)) {
                continue;
            }
            regions.remove(index);
            Region joined = new Region(
                    Math.min(existing.left(), candidate.left()),
                    Math.min(existing.top(), candidate.top()),
                    Math.max(existing.right(), candidate.right()),
                    Math.max(existing.bottom(), candidate.bottom()));
            mergeInto(regions, joined);
            return;
        }
        regions.add(candidate);
    }

    private static boolean touches(Region first, Region second) {
        return first.left() <= second.right()
                && second.left() <= first.right()
                && first.top() <= second.bottom()
                && second.top() <= first.bottom();
    }

    private static void eraseRegion(int width, PixelBuffer pixels, Region region) {
        int sourceX = region.left() > 0
                ? region.left() - 1
                : region.right() < width ? region.right() : -1;
        for (int y = region.top(); y < region.bottom(); y++) {
            int replacement = sourceX >= 0 ? pixels.get(sourceX, y) : 0xffbd8875;
            pixels.fillRow(y, region.left(), region.right(), replacement);
        }
    }
}
