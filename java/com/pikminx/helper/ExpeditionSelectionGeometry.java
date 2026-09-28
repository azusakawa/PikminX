package com.pikminx.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Derives the visible Pikmin selection items from one current frame.
 *
 * <p>This is deliberately independent of the postcard picker. OCR labels provide the
 * card order, while pixels immediately above each label provide the current item bounds.
 * No previous frame or device coordinate is retained.</p>
 */
final class ExpeditionSelectionGeometry {
    record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int centerX() { return (left + right) / 2; }
        int centerY() { return (top + bottom) / 2; }
    }

    record Item(String label, Bounds visualBounds, int row, int column) {
        int centerX() { return visualBounds.centerX(); }
        int centerY() { return visualBounds.centerY(); }
    }

    private record Label(String text, Bounds bounds) {
        int centerX() { return bounds.centerX(); }
        int centerY() { return bounds.centerY(); }
    }

    private ExpeditionSelectionGeometry() {}

    /** Each stroke stays within one observed row; empty slots are never interpolated. */
    static List<List<Item>> dragGroups(List<Item> items) {
        List<List<Item>> groups = new ArrayList<>();
        int index = 0;
        while (index < items.size() && groups.size() < 3) {
            int row = items.get(index).row();
            int maximum = groups.size() == 2 ? 2 : 5;
            List<Item> group = new ArrayList<>();
            while (index < items.size() && items.get(index).row() == row && group.size() < maximum) {
                Item item = items.get(index++);
                if (!group.isEmpty() && item.column() != group.get(group.size() - 1).column() + 1)
                    return List.of();
                group.add(item);
            }
            groups.add(List.copyOf(group));
            while (index < items.size() && items.get(index).row() == row) index++;
        }
        return List.copyOf(groups);
    }

    /** Physical Xiaomi evidence: low-capacity targets require current-item taps. */
    static List<List<Item>> selectionGroups(List<Item> items, int capacity) {
        if (capacity > 0 && capacity <= 2) {
            List<List<Item>> groups = new ArrayList<>();
            for (int i = 0; i < Math.min(capacity, items.size()); i++) {
                groups.add(List.of(items.get(i)));
            }
            return List.copyOf(groups);
        }
        return dragGroups(items);
    }

    static List<Item> detect(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        if (tokens == null || width < 1 || height < 1 || pixelAt == null) {
            return List.of();
        }
        List<Label> labels = mergeLabels(tokens, width, height);
        List<List<Label>> rows = groupRows(labels);
        List<Item> result = new ArrayList<>(12);
        for (int rowIndex = 0; rowIndex < rows.size() && result.size() < 12; rowIndex++) {
            List<Label> row = rows.get(rowIndex);
            if (row.size() > 5) return List.of();
            row.sort(Comparator.comparingInt(Label::centerX));
            for (int column = 0; column < Math.min(5, row.size()) && result.size() < 12; column++) {
                Label label = row.get(column);
                int cellLeft = column == 0 ? 0 : (row.get(column - 1).centerX() + label.centerX()) / 2;
                int cellRight = column + 1 == row.size() ? width
                        : (label.centerX() + row.get(column + 1).centerX()) / 2;
                Bounds visual = findVisualBounds(label, width, cellLeft, cellRight, pixelAt);
                if (visual == null) continue;
                result.add(new Item(label.text(), visual, rowIndex, column));
            }
        }
        return List.copyOf(result);
    }

    private static List<Label> mergeLabels(
            List<PetalMatcher.Token> tokens, int width, int height) {
        List<Label> labels = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            String text = PetalMatcher.normalize(token.text());
            if (!isCandidateLabel(text, token, width, height)) continue;
            labels.add(new Label(text, new Bounds(
                    Math.max(0, token.left()), Math.max(0, token.top()),
                    Math.min(width, token.right()), Math.min(height, token.bottom()))));
        }
        labels.sort(Comparator.comparingInt(Label::centerY).thenComparingInt(Label::centerX));
        List<Label> merged = new ArrayList<>();
        for (Label label : labels) {
            Label previous = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (previous != null && sameLabel(previous, label)) {
                Bounds a = previous.bounds(), b = label.bounds();
                merged.set(merged.size() - 1, new Label(
                        previous.text() + label.text(),
                        new Bounds(Math.min(a.left(), b.left()), Math.min(a.top(), b.top()),
                                Math.max(a.right(), b.right()), Math.max(a.bottom(), b.bottom()))));
            } else {
                merged.add(label);
            }
        }
        return merged;
    }

    private static boolean sameLabel(Label first, Label second) {
        Bounds a = first.bounds(), b = second.bounds();
        int verticalOverlap = Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top());
        int horizontalGap = Math.max(a.left(), b.left()) - Math.min(a.right(), b.right());
        return verticalOverlap >= Math.min(a.height(), b.height()) / 2
                && horizontalGap >= 0
                && horizontalGap <= Math.max(6, Math.min(a.height(), b.height()) / 2);
    }

    private static List<List<Label>> groupRows(List<Label> labels) {
        List<List<Label>> rows = new ArrayList<>();
        for (Label label : labels) {
            List<Label> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (row == null || Math.abs(row.get(0).centerY() - label.centerY())
                    > Math.max(12, row.get(0).bounds().height() * 3 / 2)) {
                row = new ArrayList<>();
                rows.add(row);
            }
            row.add(label);
        }
        return rows;
    }

    private static boolean isCandidateLabel(String text, PetalMatcher.Token token,
            int width, int height) {
        if (text.isEmpty() || !text.matches(".*\\p{IsHan}.*")
                || token.centerX() <= 0 || token.centerX() >= width
                || token.centerY() <= 0 || token.centerY() >= height) {
            return false;
        }
        return !text.contains("皮克敏") && !text.contains("可以") && !text.contains("最多")
                && !text.contains("選擇") && !text.contains("選取") && !text.contains("派遣")
                && !text.contains("自動") && !text.contains("排序") && !text.contains("友好度")
                && !text.contains("種類") && !text.contains("取消") && !text.contains("飾品")
                && !text.contains("搜尋") && !text.contains("搜索");
    }

    private static Bounds findVisualBounds(Label label, int width, int cellLeft, int cellRight,
            IntBinaryOperator pixelAt) {
        Bounds text = label.bounds();
        int radius = Math.max(text.width(), text.height() * 2);
        int left = Math.max(cellLeft, text.centerX() - radius);
        int right = Math.min(cellRight, text.centerX() + radius);
        int top = Math.max(0, text.top() - Math.max(text.height() * 8, width / 10));
        int bottom = Math.max(top + 1, text.top() - 2);
        int threshold = Math.max(3, (right - left) / 60);
        int bestArea = 0;
        Bounds best = null;
        int runStart = -1;
        int gap = 0;
        for (int y = top; y < bottom; y += 2) {
            int count = 0;
            for (int x = left; x < right; x += 2) {
                if (isItemPixel(pixelAt.applyAsInt(x, y))) count++;
            }
            if (count >= threshold) {
                if (runStart < 0) runStart = y;
                gap = 0;
            } else if (runStart >= 0 && ++gap > Math.max(2, text.height() / 2)) {
                Bounds candidate = componentBounds(runStart, y - gap * 2, left, right, pixelAt);
                if (candidate != null && candidate.width() * candidate.height() > bestArea) {
                    bestArea = candidate.width() * candidate.height();
                    best = candidate;
                }
                runStart = -1;
                gap = 0;
            }
        }
        if (runStart >= 0) {
            Bounds candidate = componentBounds(runStart, bottom, left, right, pixelAt);
            if (candidate != null && candidate.width() * candidate.height() > bestArea) best = candidate;
        }
        return best;
    }

    private static Bounds componentBounds(int top, int bottom, int left, int right,
            IntBinaryOperator pixelAt) {
        int minX = right, minY = bottom, maxX = left, maxY = top;
        for (int y = top; y < bottom; y += 2) for (int x = left; x < right; x += 2) {
            if (!isItemPixel(pixelAt.applyAsInt(x, y))) continue;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + 2); maxY = Math.max(maxY, y + 2);
        }
        return maxX <= minX || maxY <= minY ? null : new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean isItemPixel(int color) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        int minimum = Math.min(r, Math.min(g, b));
        int maximum = Math.max(r, Math.max(g, b));
        return maximum - minimum >= 28 || maximum < 170;
    }
}
