package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 驗證使用者花朵輸入的去重與順序操作。 */
public final class PetalSelectionTest {
    @Test
    public void filtersLegacyValuesAndPreservesPriorityOrder() {
        PetalSelection selection = new PetalSelection(List.of(
                "黃色勿忘草", "白色花辦", "黃色勿忘草", "紅色花瓣"));

        assertEquals(List.of("白色花辦", "黃色勿忘草"), selection.ignored());
        assertEquals("黃色勿忘草\n紅色花瓣", selection.text());
        assertFalse(selection.available(1).contains("黃色勿忘草"));

        assertTrue(selection.add("白色花瓣"));
        assertFalse(selection.add("白色花瓣"));
        assertFalse(selection.add("特殊精華"));
        selection.move(2, -1);
        selection.remove(0);

        assertEquals("白色花瓣\n紅色花瓣", selection.text());
    }

    @Test
    public void exposesColorThenFlowerChoicesAndCanonicalizesOcrNames() {
        assertEquals("白色", PetalCatalog.categories().get(0).name());
        assertEquals("白色花瓣", PetalCatalog.categories().get(0).petals().get(0));
        assertEquals("紅色天堂鳥", PetalCatalog.canonicalName("紅色夭堂島"));
        assertEquals(2, PetalCatalog.categoryIndexOf("紅色天堂鳥"));
        assertEquals(-1, PetalCatalog.categoryIndexOf("未知花朵"));
    }
}
