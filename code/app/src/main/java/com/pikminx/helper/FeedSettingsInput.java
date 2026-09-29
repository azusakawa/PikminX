package com.pikminx.helper;

/** 已驗證的花瓣生產設定。 */
record FeedSettingsInput(
        int feedsPerSquad,
        int maxSquadSwitches,
        int nectarMinimumThreshold,
        int petalLimit) {
    static FeedSettingsInput parse(
            String feedsPerSquadText,
            String maxSquadSwitchesText,
            String nectarMinimumThresholdText,
            String petalLimitText) {
        int petalLimit = boundedInteger(petalLimitText, 300, 1200, "花瓣上限");
        if (petalLimit % 50 != 0) {
            throw new IllegalArgumentException("花瓣上限必須以 50 為單位");
        }
        return new FeedSettingsInput(
                boundedInteger(feedsPerSquadText, 1, 10, "每隊餵食輪數"),
                boundedInteger(maxSquadSwitchesText, 0, 120, "最多換隊次數"),
                boundedInteger(nectarMinimumThresholdText, 0, 1200, "精華最低限制"),
                petalLimit);
    }

    int effectivePetalLimit() {
        return petalLimit - 50;
    }

    private static int boundedInteger(String text, int minimum, int maximum, String label) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= minimum && value <= maximum) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // 使用下方一致且可讀的輸入錯誤。
        }
        throw new IllegalArgumentException(
                label + "必須介於 " + minimum + " 到 " + maximum + " 之間");
    }
}
