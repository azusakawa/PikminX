package com.pikminx.helper;

/** 自動派遣要處理的探險清單項目。 */
enum ExpeditionTargetMode {
    FRUIT,
    POT,
    FRUIT_AND_POT;

    boolean accepts(ExpeditionScreenAnalyzer.ItemKind kind) {
        return switch (this) {
            case FRUIT -> kind == ExpeditionScreenAnalyzer.ItemKind.FRUIT;
            case POT -> kind == ExpeditionScreenAnalyzer.ItemKind.POT;
            case FRUIT_AND_POT -> kind == ExpeditionScreenAnalyzer.ItemKind.FRUIT
                    || kind == ExpeditionScreenAnalyzer.ItemKind.POT;
        };
    }

    static ExpeditionTargetMode fromStored(String value) {
        if (value == null) {
            return FRUIT_AND_POT;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return FRUIT_AND_POT;
        }
    }
}
