package com.pikminx.helper;

/** 已完成驗證的設定值，避免 UI 將不合法輸入傳給自動化服務。 */
record SettingsInput(int threshold, String flowers) {
    /** 驗證門檻、掃描間隔與花朵清單並建立設定值。 */
    static SettingsInput parse(String thresholdText, String flowersText) {
        int threshold = boundedInteger(thresholdText, 1, 1200, "換花門檻");
        String flowers = flowersText.trim();
        if (flowers.isEmpty()) {
            throw new IllegalArgumentException("至少選擇一種允許的花瓣");
        }
        return new SettingsInput(threshold, flowers);
    }

    /** 解析指定範圍內的整數，統一回報使用者可理解的錯誤。 */
    private static int boundedInteger(String text, int minimum, int maximum, String label) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= minimum && value <= maximum) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The shared error below is clearer than exposing a parser detail.
        }
        throw new IllegalArgumentException(
                label + "必須介於 " + minimum + " 到 " + maximum + " 之間");
    }
}
