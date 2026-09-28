package com.pikminx.helper;

/**
 * Future-facing Mushroom domain entry points.  The release deliberately keeps only the contract;
 * no production caller invokes these stubs while Mushroom is frozen.
 */
final class MushroomFeatureContract {
    private MushroomFeatureContract() {}

    static void startMushroomScan() {
        // TODO：啟動蘑菇掃描流程。
        // 未來應確認目前 Pikmin Bloom 位於可進行蘑菇辨識的有效畫面，
        // 取得最新遊戲畫面並辨識可見蘑菇，將有效結果交給蘑菇工作流程，
        // 並拒絕過期、失焦或已取消的結果。
    }

    static void startMushroomPatrol() {
        // TODO：啟動蘑菇巡航功能。
        // 未來應依使用者設定的單點、路徑或範圍執行巡航，管理定位、Pause、Resume、Stop
        // 與工作階段生命週期，並確保巡航停止後不會由舊 callback 重新啟動。
    }
}
