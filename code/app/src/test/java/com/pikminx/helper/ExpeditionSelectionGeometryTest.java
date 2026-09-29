package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.Test;

/** Geometry regression only; synthetic pixels never establish gameplay acceptance. */
public final class ExpeditionSelectionGeometryTest {
    @Test
    public void aSixLabelRowCannotBeTruncatedIntoAnApparentlyValidFiveItemRow() {
        List<PetalMatcher.Token> labels = new ArrayList<>();
        for (int i = 0; i < 6; i++) labels.add(new PetalMatcher.Token("紅色", 20 + i * 60, 430,
                45 + i * 60, 450));
        assertTrue(ExpeditionSelectionGeometry.detect(labels, 400, 900,
                (x, y) -> y >= 330 && y < 410 ? 0xff30b040 : 0xffffffff).isEmpty());
    }

    @Test
    public void longLabelCannotPullItsVisualCenterIntoANeighboringItem() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("紫色長名稱", 40, 430, 160, 450),
                new PetalMatcher.Token("紅色", 190, 430, 250, 450));
        IntBinaryOperator pixels = (x, y) -> y >= 330 && y < 410
                && (x >= 80 && x < 120 || x >= 200 && x < 240) ? 0xff30b040 : 0xffffffff;
        var items = ExpeditionSelectionGeometry.detect(tokens, 400, 900, pixels);
        assertEquals(2, items.size());
        assertEquals(100, items.get(0).centerX());
        assertEquals(220, items.get(1).centerX());
    }

    @Test
    public void batchUsesOnlyActualItemsAndNeverCrossesRows() {
        List<ExpeditionSelectionGeometry.Item> items = new ArrayList<>();
        for (int i = 0; i < 15; i++) items.add(new ExpeditionSelectionGeometry.Item(
                "item" + i, new ExpeditionSelectionGeometry.Bounds(i * 10, 100, i * 10 + 8, 108), i / 5, i % 5));
        var groups = ExpeditionSelectionGeometry.dragGroups(items);
        assertEquals(List.of(5, 5, 2), groups.stream().map(List::size).toList());
        assertEquals(items.subList(0, 12), groups.stream().flatMap(List::stream).toList());
        assertEquals(List.of(5, 2), ExpeditionSelectionGeometry.dragGroups(items.subList(0, 7))
                .stream().map(List::size).toList());
        var lowCapacity = ExpeditionSelectionGeometry.selectionGroups(items, 2);
        assertEquals(List.of(1, 1), lowCapacity.stream().map(List::size).toList());
        assertEquals(items.subList(0, 2), lowCapacity.stream().flatMap(List::stream).toList());
        assertTrue(ExpeditionSelectionGeometry.dragGroups(List.of()).isEmpty());
        var sparse = ExpeditionSelectionGeometry.dragGroups(List.of(items.get(0), items.get(3), items.get(5)));
        assertTrue(sparse.isEmpty());
    }

    @Test
    public void derivesCurrentRowsAndColumnsFromLabelsAndPixels() {
        int width = 600, height = 1200;
        List<PetalMatcher.Token> tokens = new ArrayList<>();
        int[] centers = {70, 190, 310, 430, 550};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < centers.length; column++) {
                int labelTop = 330 + row * 260;
                int x = centers[column];
                tokens.add(new PetalMatcher.Token("紅色" + row + column,
                        x - 24, labelTop, x + 24, labelTop + 28));
            }
        }
        IntBinaryOperator pixels = (x, y) -> {
            for (int row = 0; row < 3; row++) for (int column = 0; column < 5; column++) {
                int cx = centers[column], labelTop = 330 + row * 260;
                if (x >= cx - 28 && x < cx + 28 && y >= labelTop - 100 && y < labelTop - 22) {
                    return 0xff2aa84a;
                }
            }
            return 0xffffffff;
        };

        List<ExpeditionSelectionGeometry.Item> items =
                ExpeditionSelectionGeometry.detect(tokens, width, height, pixels);
        assertEquals(12, items.size());
        assertEquals(0, items.get(0).row());
        assertEquals(4, items.get(4).column());
        assertEquals(1, items.get(5).row());
        assertEquals(2, items.get(11).row());
        assertTrue(items.get(0).centerY() < tokens.get(0).top());
        assertEquals(550, items.get(4).centerX());
    }

    @Test
    public void rejectsUiLabelsAndPixelsWithoutAVisibleItem() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("可以選擇最多12隻皮克敏", 20, 200, 300, 230),
                new PetalMatcher.Token("自動", 40, 280, 100, 310),
                new PetalMatcher.Token("紅色花苗", 80, 430, 180, 460));
        List<ExpeditionSelectionGeometry.Item> items =
                ExpeditionSelectionGeometry.detect(tokens, 600, 1200, (x, y) -> 0xffffffff);
        assertTrue(items.isEmpty());
    }

    @Test
    public void mergesSplitLabelTokensWithoutChangingCurrentOrder() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("紫色", 50, 430, 95, 460),
                new PetalMatcher.Token("山丘", 98, 430, 143, 460),
                new PetalMatcher.Token("紅色", 260, 430, 305, 460),
                new PetalMatcher.Token("花苗", 450, 430, 495, 460));
        IntBinaryOperator pixels = (x, y) -> x < 180 && y >= 330 && y < 408
                || x >= 240 && x < 330 && y >= 330 && y < 408
                || x >= 430 && y >= 330 && y < 408 ? 0xffe15b35 : 0xffffffff;
        List<ExpeditionSelectionGeometry.Item> items =
                ExpeditionSelectionGeometry.detect(tokens, 600, 900, pixels);
        assertEquals(3, items.size());
        assertEquals("紫色山丘", items.get(0).label());
        assertEquals(1, items.get(1).column());
    }
}
