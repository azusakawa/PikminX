package com.pikminx.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Pure current-frame proposals. This class owns neither capture, OCR, templates nor gestures. */
final class ExpeditionVision {
    record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int centerX() { return (left + right) / 2; }
        int centerY() { return (top + bottom) / 2; }
        ScreenCoordinateTransform.ScreenshotRect roi() {
            return new ScreenCoordinateTransform.ScreenshotRect(left, top, right, bottom);
        }
    }
    record Icon(Bounds bounds, Bounds cardText, long signature, boolean cardContext) {}
    record Scrollbar(int trackTop, int trackBottom, int thumbTop, int thumbBottom) {
        boolean atBottom() { return thumbBottom >= trackBottom - 2; }
        boolean atTop() { return thumbTop <= trackTop + 2; }
    }
    record Surface(Bounds selectedTab, Bounds handle, Bounds content, List<Icon> icons, Scrollbar scrollbar) {
        Surface { icons = List.copyOf(icons); }
    }
    private record Component(Bounds bounds, int area) {}

    static Surface detect(int width, int height, IntBinaryOperator pixelAt) {
        if (width < 32 || height <= width || pixelAt == null) return null;
        int step = Math.max(1, width / 426);
        Bounds selected = null;
        for (Component component : components(new Bounds(0, 0, width, height),
                step, pixelAt, true)) {
            Bounds b = component.bounds();
            if (b.width() < width * .10 || b.width() > width * .28
                    || b.height() < height * .02 || b.height() > height * .06
                    || b.width() < b.height() * 2 || b.width() > b.height() * 5
                    || component.area() < b.width() * b.height() * .50
                    || !whiteNavigationBackground(width, b, step, pixelAt)) continue;
            if (selected != null) return null;
            selected = b;
        }
        if (selected == null) return null;
        Bounds handle = findHandle(width, selected, step, pixelAt);
        // These limits derive from the current tab/control size, not a stored screen position.
        int contentTop = selected.bottom() + selected.height() / 2;
        int contentBottom = height - selected.height() * 5 / 2;
        if (contentBottom - contentTop < selected.height() * 2)
            return new Surface(selected, handle, new Bounds(0, height - 1, width, height), List.of(), null);
        Bounds content = new Bounds(0, contentTop, width, contentBottom);
        List<Bounds> activeCards = activeCardFrames(width, content, step, pixelAt);
        List<Icon> icons = new ArrayList<>();
        for (Component component : components(content, step, pixelAt, false)) {
            Bounds b = component.bounds();
            if (b.top() <= content.top() + step || b.bottom() >= content.bottom() - step
                    || b.left() <= step || b.right() >= width - step
                    || b.width() < width * .045 || b.height() < width * .065
                    || b.width() > width * .28 || b.height() > width * .28
                    || b.width() < b.height() * .4 || b.width() > b.height() * 1.8
                    || component.area() < (double) width * width * .002) continue;
            if (activeCards.stream().anyMatch(card -> b.left() >= card.left()
                    && b.right() <= card.right() && b.top() >= card.top()
                    && b.bottom() <= card.bottom())) continue;
            Bounds text = new Bounds(Math.max(0, b.left() - b.width() / 2),
                    Math.max(content.top(), b.top() - b.height() / 2),
                    Math.min(width, b.right() + b.width() / 2),
                    Math.min(content.bottom(), b.bottom() + b.height() * 3 / 2));
            icons.add(new Icon(b, text, signature(b, pixelAt), hasCardTextRows(b, text, step, pixelAt)));
        }
        icons.sort(Comparator.comparingInt((Icon icon) -> icon.bounds().top())
                .thenComparingInt(icon -> icon.bounds().left()));
        return new Surface(selected, handle, content, icons, scrollbar(width, content, pixelAt));
    }

    /** Current pale-pink outlines enclose already-dispatched expeditions, including their fruit. */
    private static List<Bounds> activeCardFrames(int width, Bounds content, int step,
            IntBinaryOperator pixelAt) {
        List<Bounds> frames = new ArrayList<>();
        for (Component component : components(content, step,
                (x, y) -> activeCardPink(pixelAt.applyAsInt(x, y)) ? 0xff000000 : 0xffffffff, false)) {
            Bounds b = component.bounds();
            if (b.width() < width / 5 || b.width() > width / 2
                    || b.height() < width / 5 || b.height() > width / 2
                    || component.area() > b.width() * b.height() / 5) continue;
            int supportedEdges = 0;
            for (int edge = 0; edge < 4; edge++) {
                int hits = 0;
                for (int sample = 2; sample <= 8; sample++) {
                    int x = edge < 2 ? b.left() + b.width() * sample / 10
                            : edge == 2 ? b.left() : b.right() - step;
                    int y = edge >= 2 ? b.top() + b.height() * sample / 10
                            : edge == 0 ? b.top() : b.bottom() - step;
                    boolean found = false;
                    for (int offset = -step; offset <= step; offset++) {
                        int px = x + (edge >= 2 ? offset : 0);
                        int py = y + (edge < 2 ? offset : 0);
                        if (px >= 0 && px < width && py >= content.top() && py < content.bottom()
                                && activeCardPink(pixelAt.applyAsInt(px, py))) found = true;
                    }
                    if (found) hits++;
                }
                if (hits >= 5) supportedEdges++;
            }
            if (supportedEdges == 4) frames.add(b);
        }
        return frames;
    }

    private static boolean activeCardPink(int color) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        return r >= 240 && g >= 180 && b >= 180 && r - g >= 8 && Math.abs(g - b) <= 25;
    }

    /** Detects the current track and its darker thumb; no stored screen position is returned. */
    private static Scrollbar scrollbar(int width, Bounds content, IntBinaryOperator pixelAt) {
        int bestX = -1, bestCount = 0;
        for (int x = width - Math.max(4, width / 32); x < width; x++) {
            int count = 0;
            for (int y = content.top(); y < content.bottom(); y++)
                if (scrollbarBlue(pixelAt.applyAsInt(x, y))) count++;
            if (count > bestCount) { bestCount = count; bestX = x; }
        }
        if (bestX < 0 || bestCount < content.height() / 3) return null;
        int top = content.bottom(), bottom = -1, minRed = 255, maxRed = 0;
        for (int y = content.top(); y < content.bottom(); y++) {
            int c = pixelAt.applyAsInt(bestX, y);
            if (!scrollbarBlue(c)) continue;
            top = Math.min(top, y); bottom = y;
            int red = (c >>> 16) & 255;
            minRed = Math.min(minRed, red); maxRed = Math.max(maxRed, red);
        }
        if (bottom < top || bestCount < (bottom - top + 1) * .95 || maxRed - minRed < 20) return null;
        int threshold = (minRed + maxRed) / 2, thumbTop = bottom, thumbBottom = -1, dark = 0;
        for (int y = top; y <= bottom; y++) {
            int c = pixelAt.applyAsInt(bestX, y);
            if (scrollbarBlue(c) && ((c >>> 16) & 255) <= threshold) {
                thumbTop = Math.min(thumbTop, y); thumbBottom = y; dark++;
            }
        }
        if (dark < 8 || dark < (thumbBottom - thumbTop + 1) * .90) return null;
        return new Scrollbar(top, bottom, thumbTop, thumbBottom);
    }

    private static boolean scrollbarBlue(int c) {
        int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
        return r >= 140 && g >= 140 && b >= r + 8 && b >= g + 8;
    }

    private static Bounds findHandle(int width, Bounds tab, int step, IntBinaryOperator pixelAt) {
        Bounds region = new Bounds(width / 4, Math.max(0, tab.top() - tab.height() * 2), width * 3 / 4, tab.top());
        if (region.height() <= 0) return null;
        Bounds found = null;
        for (Component component : components(region, step, (x, y) -> {
            int c = pixelAt.applyAsInt(x, y);
            int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
            int lo = Math.min(r, Math.min(g, b)), hi = Math.max(r, Math.max(g, b));
            return lo >= 180 && hi <= 245 && hi - lo <= 12 ? 0xff000000 : 0xffffffff;
        }, false)) {
            Bounds b = component.bounds();
            if (b.width() < width * .08 || b.width() > width * .25
                    || b.height() < step || b.height() > tab.height() / 3
                    || b.width() < b.height() * 6
                    || component.area() < b.width() * b.height() * .85) continue;
            if (found != null) return null;
            found = b;
        }
        return found;
    }

    /** Do not admit an underlying list through a dimmed game-modal backdrop. */
    private static boolean whiteNavigationBackground(int width, Bounds tab, int step, IntBinaryOperator pixelAt) {
        int white = 0, samples = 0;
        for (int y = tab.top(); y < tab.bottom(); y += step) for (int x = 0; x < width; x += step) {
            int c = pixelAt.applyAsInt(x, y);
            if (((c >>> 16) & 255) > 230 && ((c >>> 8) & 255) > 230 && (c & 255) > 230) white++;
            samples++;
        }
        return white >= samples * .60;
    }

    /** Available cards have title/location rows below the icon, unlike toolbar counters/photos. */
    private static boolean hasCardTextRows(Bounds icon, Bounds text, int step, IntBinaryOperator pixelAt) {
        int rows = 0, run = 0, gap = 0;
        for (int y = icon.bottom(); y < text.bottom(); y += step) {
            int dark = 0, colored = 0, white = 0, samples = 0;
            for (int x = text.left(); x < text.right(); x += step) {
                int color = pixelAt.applyAsInt(x, y);
                int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
                int lo = Math.min(r, Math.min(g, b)), hi = Math.max(r, Math.max(g, b));
                samples++;
                if (lo > 230) white++;
                if (hi - lo > 45) colored++;
                if (hi < 180 && hi - lo < 40) dark++;
            }
            boolean textRow = dark >= Math.max(3, samples / 40)
                    && white >= samples * .65 && colored <= samples * .10;
            if (textRow) {
                run += gap + 1; gap = 0;
            } else if (run > 0 && ++gap > 1) {
                // One sampled row may cross a dense glyph stroke instead of its white background.
                if (run * step >= Math.max(6, text.width() / 35)) rows++;
                run = 0; gap = 0;
            }
        }
        if (run * step >= Math.max(6, text.width() / 35)) rows++;
        return rows >= 2;
    }

    /** Stable structure ignores animated colored sprites but retains text, cards and scrollbar. */
    static long viewportSignature(Surface surface, IntBinaryOperator pixelAt) {
        Bounds b = surface.content();
        long hash = 0xcbf29ce484222325L;
        for (int blockY = 0; blockY < 32; blockY++) for (int blockX = 0; blockX < 16; blockX++) {
            int dark = 0, pink = 0, scroll = 0;
            for (int sy = blockY * 8; sy < (blockY + 1) * 8; sy++) {
                for (int sx = blockX * 8; sx < (blockX + 1) * 8; sx++) {
                    int c = pixelAt.applyAsInt(b.left() + (2 * sx + 1) * b.width() / 256,
                            b.top() + (2 * sy + 1) * b.height() / 512);
                    int r = (c >>> 16) & 255, g = (c >>> 8) & 255, blue = c & 255;
                    int low = Math.min(r, Math.min(g, blue)), high = Math.max(r, Math.max(g, blue));
                    if (high < 180 && high - low < 40) dark++;
                    else if (activeCardPink(c)) pink++;
                    else if (scrollbarBlue(c)) scroll++;
                }
            }
            hash = (hash ^ Math.min(15, dark / 6)) * 0x100000001b3L;
            hash = (hash ^ Math.min(15, pink / 6)) * 0x100000001b3L;
            hash = (hash ^ Math.min(15, scroll / 6)) * 0x100000001b3L;
        }
        return hash;
    }

    /** Shape/color signature describes this proposal; it is not a persistent game-object ID. */
    private static long signature(Bounds b, IntBinaryOperator pixelAt) {
        long hash = 0xcbf29ce484222325L;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int color = pixelAt.applyAsInt(b.left() + (2 * x + 1) * b.width() / 16,
                    b.top() + (2 * y + 1) * b.height() / 16);
            int quantized = ((color >>> 21) & 7) << 6 | ((color >>> 13) & 7) << 3 | ((color >>> 5) & 7);
            hash = (hash ^ quantized) * 0x100000001b3L;
        }
        return hash;
    }

    private static List<Component> components(Bounds roi, int step, IntBinaryOperator pixelAt, boolean teal) {
        int columns = (roi.width() + step - 1) / step, rows = (roi.height() + step - 1) / step;
        byte[] mask = new byte[columns * rows];
        int[] queue = new int[mask.length];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            int color = pixelAt.applyAsInt(roi.left() + x * step, roi.top() + y * step);
            int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
            if (teal ? g >= r + 40 && b >= r + 30 && g >= b : Math.min(r, Math.min(g, b)) < 220)
                mask[y * columns + x] = 1;
        }
        List<Component> result = new ArrayList<>();
        for (int seed = 0; seed < mask.length; seed++) {
            if (mask[seed] != 1) continue;
            int head = 0, tail = 1; queue[0] = seed; mask[seed] = 2;
            int minX = seed % columns, maxX = minX, minY = seed / columns, maxY = minY;
            while (head < tail) {
                int index = queue[head++], x = index % columns, y = index / columns;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= columns || ny < 0 || ny >= rows) continue;
                    int next = ny * columns + nx;
                    if (mask[next] == 1) { mask[next] = 2; queue[tail++] = next; }
                }
            }
            if (tail >= 12) result.add(new Component(new Bounds(
                    roi.left() + minX * step, roi.top() + minY * step,
                    Math.min(roi.right(), roi.left() + (maxX + 1) * step),
                    Math.min(roi.bottom(), roi.top() + (maxY + 1) * step)), tail * step * step));
        }
        return result;
    }

    private ExpeditionVision() {}
}
