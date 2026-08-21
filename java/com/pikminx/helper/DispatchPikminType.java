package com.pikminx.helper;

/** 派遣隊伍的顏色限制；MIXED 不操作顏色篩選。 */
enum DispatchPikminType {
    MIXED,
    RED,
    YELLOW,
    BLUE,
    PURPLE,
    WHITE,
    WINGED,
    ROCK,
    ICE;

    String label() {
        return switch (this) {
            case MIXED -> "混合";
            case RED -> "紅色";
            case YELLOW -> "黃色";
            case BLUE -> "藍色";
            case PURPLE -> "紫色";
            case WHITE -> "白色";
            case WINGED -> "羽翅";
            case ROCK -> "岩石";
            case ICE -> "冰凍";
        };
    }

    static DispatchPikminType fromStored(String value) {
        if (value == null) return MIXED;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return MIXED;
        }
    }
}
