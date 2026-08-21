package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 驗證花瓣目錄的分類、去重與公開數量。 */
public final class PetalCatalogTest {
    @Test
    public void formatsSharedCatalogNamesForThePlantingSearchField() {
        assertEquals("蝴蝶蘭", PetalCatalog.searchQuery("黃色蝴蝶蘭"));
        assertEquals("白色", PetalCatalog.searchQuery("白色花瓣"));
        assertEquals("", PetalCatalog.searchQuery("向日葵"));
        assertEquals("", PetalCatalog.searchQuery("不存在的花"));
    }

    @Test
    public void usesPostcardPotCatalogAsTheSingleNameSource() {
        List<String> petals = PetalCatalog.categories().stream()
                .flatMap(category -> category.petals().stream())
                .toList();
        Set<String> unique = new HashSet<>(petals);

        assertEquals(petals.size(), unique.size());
        assertEquals(new HashSet<>(PostcardPotCatalog.allNames()), unique);
        assertEquals(151, PetalCatalog.size());
        assertEquals(PostcardPotCatalog.allNames(), PetalCatalog.petals());
        assertTrue(PetalCatalog.contains("白色花瓣"));
        assertTrue(PetalCatalog.contains("白色九重葛"));
        assertTrue(PetalCatalog.contains("白色蝴蝶蘭"));
        assertTrue(PetalCatalog.contains("黃色勿忘草"));
        assertTrue(PetalCatalog.contains("白色美人蕉"));
        assertTrue(PetalCatalog.contains("黃色美人蕉"));
        assertTrue(PetalCatalog.contains("紅色美人蕉"));
        assertFalse(PetalCatalog.contains("藍色美人蕉"));
        assertTrue(PetalCatalog.contains("白色櫻花"));
        assertFalse(PetalCatalog.contains("櫻花"));
        assertFalse(PetalCatalog.contains("特殊精華"));
        assertFalse(PetalCatalog.contains("白色花辦"));
    }
}
