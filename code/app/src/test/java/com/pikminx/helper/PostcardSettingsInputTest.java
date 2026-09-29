package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class PostcardSettingsInputTest {
    @Test
    public void containsOnlyRemainingCountPotNameAndPikminCount() {
        assertEquals(3, PostcardSettingsInput.class.getRecordComponents().length);
    }

    @Test
    public void acceptsBothCollectionLimits() {
        assertEquals(1, PostcardSettingsInput.parse("1", "黃色花瓣", "1").collectionLimit());
        PostcardSettingsInput maximum = PostcardSettingsInput.parse("15", " 白色百合 ", "5");
        assertEquals(15, maximum.collectionLimit());
        assertEquals("白色百合", maximum.petalPotName());
        assertEquals(5, maximum.pikminCount());
    }

    @Test
    public void acceptsZeroRemainingCollectionsForSaving() {
        PostcardSettingsInput input = PostcardSettingsInput.parse(
                "0", "紅色天堂鳥", "3");

        assertEquals(0, input.collectionLimit());
        assertEquals("紅色天堂鳥", input.petalPotName());
        assertEquals(3, input.pikminCount());
    }

    @Test
    public void rejectsOutsideOrNonNumericLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("-1", "黃色花瓣", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("16", "黃色花瓣", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("", "黃色花瓣", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("十五", "黃色花瓣", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("1", "  ", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("1", "白色玫瑰", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("1", "黃色花瓣", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> PostcardSettingsInput.parse("1", "黃色花瓣", "6"));
    }
}
