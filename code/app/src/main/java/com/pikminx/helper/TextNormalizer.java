package com.pikminx.helper;

import java.text.Normalizer;
import java.util.Locale;

final class TextNormalizer {
    private TextNormalizer() {}

    static boolean matchesEditor(boolean editorPresent, CharSequence actual, String expected) {
        // Android may expose an empty editor with null text; absence of the node
        // itself must never count as a verified empty search.
        if (!editorPresent || expected == null) return false;
        if (expected.isEmpty()) return actual == null || actual.length() == 0;
        return normalizeForMatch(actual == null ? null : actual.toString())
                .equals(normalizeForMatch(expected));
    }

    static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
}
