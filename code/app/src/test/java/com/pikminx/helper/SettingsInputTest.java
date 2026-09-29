package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** 驗證主畫面與懸浮設定共用的輸入邊界。 */
public final class SettingsInputTest {
    @Test
    public void parsesValidOverlaySettings() {
        SettingsInput input = SettingsInput.parse("50", "白色花瓣\n紅色花瓣");
        assertEquals(50, input.threshold());
        assertEquals("白色花瓣\n紅色花瓣", input.flowers());
    }

    @Test
    public void rejectsInvalidOverlaySettings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettingsInput.parse("0", ""));
    }
}
