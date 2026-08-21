package com.pikminx.helper;

/** 純函式形式的明信片剩餘次數規則，讓持久化層與測試共用同一邏輯。 */
final class PostcardRemainingCount {
    private PostcardRemainingCount() {}

    static int afterConfirmedReceipt(int currentRemaining) {
        return Math.max(0, Math.min(15, currentRemaining) - 1);
    }
}
