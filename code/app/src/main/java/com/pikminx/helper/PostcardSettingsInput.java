package com.pikminx.helper;

/** 驗證明信片分頁的剩餘收集次數、指定花盆與皮克敏數量。 */
record PostcardSettingsInput(
        int collectionLimit,
        String petalPotName,
        int pikminCount) {
    static PostcardSettingsInput parse(
            String limitText, String petalPotText, String pikminCountText) {
        final int limit;
        try {
            limit = Integer.parseInt(limitText.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("剩餘收集次數請輸入 0 到 15 的整數");
        }
        if (limit < 0 || limit > 15) {
            throw new IllegalArgumentException("剩餘收集次數必須介於 0 到 15");
        }
        String potName = petalPotText == null ? "" : petalPotText.trim();
        String canonicalPotName = PostcardPotCatalog.canonicalName(potName);
        if (canonicalPotName == null) {
            throw new IllegalArgumentException("請從內建花盆清單選擇名稱");
        }
        final int pikminCount;
        try {
            pikminCount = Integer.parseInt(pikminCountText.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("皮克敏數量請輸入 1 到 5 的整數");
        }
        if (pikminCount < 1 || pikminCount > 5) {
            throw new IllegalArgumentException("皮克敏數量必須介於 1 到 5");
        }
        return new PostcardSettingsInput(limit, canonicalPotName, pikminCount);
    }
}
