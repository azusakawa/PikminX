package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public final class PostcardPotCatalogTest {
    @Test
    public void canonicalizesOnlyBuiltInSingleLineOcrNamesAndCategorizesThem() {
        assertEquals("紅色天堂鳥", PostcardPotCatalog.canonicalName("紅色夭堂鳥"));
        assertEquals("紅色天堂鳥", PostcardPotCatalog.canonicalName("紅色天堂島"));
        assertEquals("黃色天堂鳥", PostcardPotCatalog.canonicalName("黄色夭堂鸟"));
        assertEquals(PostcardPotCatalog.Color.RED,
                PostcardPotCatalog.colorOf("紅色天堂鳥"));
        assertNull(PostcardPotCatalog.canonicalName("天堂鳥"));
        assertNull(PostcardPotCatalog.canonicalName("紅色\n天堂鳥"));
        assertNull(PostcardPotCatalog.canonicalName("白色玫瑰"));
    }

    @Test
    public void exposesTheExactBuiltInColorListsWithoutOcrMerge() {
        assertEquals(4, PostcardPotCatalog.categories().size());
        assertEquals(46, PostcardPotCatalog.namesForColor(PostcardPotCatalog.Color.WHITE).size());
        assertEquals(37, PostcardPotCatalog.namesForColor(PostcardPotCatalog.Color.YELLOW).size());
        assertEquals(40, PostcardPotCatalog.namesForColor(PostcardPotCatalog.Color.RED).size());
        assertEquals(28, PostcardPotCatalog.namesForColor(PostcardPotCatalog.Color.BLUE).size());
        assertEquals(151, PostcardPotCatalog.allNames().size());
        assertEquals(List.of("白色百合", "白色彼岸花"),
                PostcardPotCatalog.namesForColor(PostcardPotCatalog.Color.WHITE).subList(0, 2));
        assertEquals("藍色矮牽牛", PostcardPotCatalog.namesForColor(
                PostcardPotCatalog.Color.BLUE).get(27));
    }
}
