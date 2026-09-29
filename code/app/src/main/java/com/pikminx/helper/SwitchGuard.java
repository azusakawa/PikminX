package com.pikminx.helper;

/** 防止 OCR 短暫誤判造成重複切花或過於頻繁切換。 */
final class SwitchGuard {
    static final int REQUIRED_CONFIRMATIONS = 2;
    private static final long COOLDOWN_MILLIS = 30_000;
    private int confirmations;
    private long lastSwitchAt = -1;
    private String pendingFlower = "";

    /** 以門檻、連續確認次數與冷卻時間判斷是否允許切換。 */
    boolean shouldSwitch(Integer remaining, int threshold, long nowMillis) {
        if (cooldownRemainingMillis(nowMillis) > 0
                || remaining == null
                || !isBelowThreshold(remaining, threshold)) {
            confirmations = 0;
            return false;
        }
        confirmations++;
        if (confirmations < REQUIRED_CONFIRMATIONS) {
            return false;
        }
        confirmations = 0;
        return true;
    }

    /** 設定語意是「低於門檻才切換」，等於門檻仍可繼續使用。 */
    static boolean isBelowThreshold(int remaining, int threshold) {
        return remaining < threshold;
    }

    /** 記錄已完成切換並啟動冷卻。 */
    void markSwitched(long nowMillis) {
        confirmations = 0;
        lastSwitchAt = nowMillis;
    }

    /** 記錄等待無障礙點擊確認的花朵。 */
    void requestSwitch(String flower) {
        pendingFlower = flower;
    }

    /** 取得待確認的花朵名稱。 */
    String pendingFlower() {
        return pendingFlower;
    }

    /** 只有高亮名稱一致時才接受點擊結果。 */
    boolean confirmSwitch(String highlightedFlower, long nowMillis) {
        if (pendingFlower.isEmpty() || !pendingFlower.equals(highlightedFlower)) {
            return false;
        }
        pendingFlower = "";
        markSwitched(nowMillis);
        return true;
    }

    /** 取消尚未確認的切換。 */
    void cancelSwitch() {
        pendingFlower = "";
    }

    /** 重設流程狀態，通常在暫停或服務重啟時呼叫。 */
    void reset() {
        confirmations = 0;
        lastSwitchAt = -1;
        pendingFlower = "";
    }

    /** 回傳目前連續低數量確認次數。 */
    int confirmations() {
        return confirmations;
    }

    /** 計算尚未結束的切換冷卻時間。 */
    long cooldownRemainingMillis(long nowMillis) {
        if (lastSwitchAt < 0) {
            return 0;
        }
        return Math.max(0, COOLDOWN_MILLIS - (nowMillis - lastSwitchAt));
    }
}
