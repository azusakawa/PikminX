package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class FeedSettingsInputTest {
    @Test
    public void acceptsConfiguredBoundaries() {
        assertEquals(new FeedSettingsInput(1, 0, 0, 300),
                FeedSettingsInput.parse("1", "0", "0", "300"));
        assertEquals(new FeedSettingsInput(10, 120, 1200, 1200),
                FeedSettingsInput.parse("10", "120", "1200", "1200"));
        assertEquals(250, FeedSettingsInput.parse("6", "0", "40", "300")
                .effectivePetalLimit());
        assertEquals(1150, FeedSettingsInput.parse("6", "0", "40", "1200")
                .effectivePetalLimit());
    }

    @Test
    public void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("0", "0", "40", "300"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "-1", "40", "300"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "121", "40", "300"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "0", "1201", "300"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "0", "40", "250"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "0", "40", "1250"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedSettingsInput.parse("6", "0", "40", "325"));
    }
}
