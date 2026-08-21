package com.pikminx.helper;

/** 進入皮克敏選擇頁後的選取方法。 */
enum DispatchSelectionMethod {
    AUTO,
    DRAG_12;

    boolean requiresFullSelection() {
        return this == DRAG_12;
    }

    static DispatchSelectionMethod fromStored(String value) {
        if (value == null) {
            return AUTO;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AUTO;
        }
    }
}
