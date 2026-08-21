package com.pikminx.helper;

/** 純函式形式的派遣剩餘次數規則，供持久化層與單元測試共用。 */
final class ExpeditionRemainingCount {
    private ExpeditionRemainingCount() {}

    static int afterConfirmedDispatch(int currentRemaining) {
        return Math.max(0, Math.min(99, currentRemaining) - 1);
    }
}
