package com.pikminx.helper;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TextNormalizerTest {
    @Test
    public void emptyEditorIsDifferentFromMissingEditor() {
        assertTrue(TextNormalizer.matchesEditor(true, null, ""));
        assertTrue(TextNormalizer.matchesEditor(true, "", ""));
        assertFalse(TextNormalizer.matchesEditor(false, null, ""));
        assertFalse(TextNormalizer.matchesEditor(true, "紅色", ""));
        assertFalse(TextNormalizer.matchesEditor(true, "?", ""));
        assertFalse(TextNormalizer.matchesEditor(true, " ", ""));
    }

    @Test
    public void nonemptyQueriesStillRequireMatchingText() {
        assertFalse(TextNormalizer.matchesEditor(true, null, "紅色"));
        assertFalse(TextNormalizer.matchesEditor(true, "黃色", "紅色"));
        assertTrue(TextNormalizer.matchesEditor(true, "紅 色", "紅色"));
        assertFalse(TextNormalizer.matchesEditor(true, null, null));
    }
}
