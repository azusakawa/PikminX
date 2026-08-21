package com.pikminx.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** 以 SharedPreferences 保存跨啟動仍需保留的使用者設定。 */
final class SettingsStore {
    private static final String PREFS = "pikminx_settings";
    private static final String DEFAULT_FLOWERS = "白色花瓣\n黃色花瓣\n紅色花瓣\n藍色花瓣";
    private static final String DEFAULT_POSTCARD_PETAL_POT = "黃色花瓣";
    private final SharedPreferences preferences;

    /** 使用應用程式自己的偏好檔建立儲存層。 */
    SettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 取得花盆剩餘量低於此值時的切換門檻。 */
    int threshold() {
        return preferences.getInt("threshold", 50);
    }

    /** 取得兩次 OCR 掃描之間的秒數。 */
    /** 取得上次使用者選擇的懸浮窗顯示狀態。 */
    boolean overlayVisible() {
        return preferences.getBoolean("overlay_visible", true);
    }

    /** 取得尚未完成的明信片收集次數；成功接收後會持久化遞減至 0。 */
    int postcardCollectionLimit() {
        return Math.max(0, Math.min(15,
                preferences.getInt("postcard_collection_limit", 1)));
    }

    /** 取得明信片流程指定使用的花盆名稱。 */
    String postcardPetalPotName() {
        String stored = preferences.getString(
                "postcard_petal_pot_name", DEFAULT_POSTCARD_PETAL_POT);
        String canonical = PostcardPotCatalog.canonicalName(stored);
        return canonical == null ? DEFAULT_POSTCARD_PETAL_POT : canonical;
    }

    /** 取得 APK 內建花盆目錄；不從 OCR 或使用者輸入建立選項。 */
    List<String> postcardPetalPotNames() {
        return PostcardPotCatalog.allNames();
    }

    /** 取得每次取回明信片要派出的皮克敏數量。 */
    int postcardPikminCount() {
        return Math.max(1, Math.min(5,
                preferences.getInt("postcard_pikmin_count", 1)));
    }

    /** 取得自動派遣次數；首次在新裝置預設只測試 1 次。 */
    int expeditionDispatchCount() {
        return Math.max(0, Math.min(99, preferences.getInt("expedition_dispatch_count", 1)));
    }

    ExpeditionTargetMode expeditionTargetMode() {
        return ExpeditionTargetMode.fromStored(preferences.getString(
                "expedition_target_mode", ExpeditionTargetMode.FRUIT_AND_POT.name()));
    }

    DispatchSelectionMethod dispatchSelectionMethod() {
        return DispatchSelectionMethod.fromStored(preferences.getString(
                "dispatch_selection_method", DispatchSelectionMethod.AUTO.name()));
    }

    DispatchPikminType dispatchPikminType() {
        return DispatchPikminType.fromStored(preferences.getString(
                "dispatch_pikmin_type", DispatchPikminType.MIXED.name()));
    }

    /** 回程物品帶出明信片時，預設領取；刪除必須由使用者明確選取。 */
    boolean receiveReturnedPostcards() {
        return preferences.getBoolean("return_reward_receive_postcard", true);
    }

    /** 儲存懸浮窗顯示狀態。 */
    void setOverlayVisible(boolean visible) {
        preferences.edit().putBoolean("overlay_visible", visible).apply();
    }

    /** 取得原始花朵輸入文字，以便回填編輯框。 */
    String flowersText() {
        return preferences.getString("flowers", DEFAULT_FLOWERS);
    }

    /** 將換行、半形逗號與全形逗號拆成自動化順序。 */
    List<String> allowedFlowers() {
        List<String> result = new ArrayList<>();
        for (String value : flowersText().split("[\\n,，]+")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 以安全上下限保存門檻、間隔與花朵順序。 */
    void save(int threshold, String flowers) {
        preferences.edit()
                .putInt("threshold", Math.max(1, Math.min(threshold, 9999)))
                .putString("flowers", flowers.trim())
                .apply();
    }

    /** 保存明信片分頁的剩餘收集次數、花盆名稱與皮克敏數量。 */
    void savePostcardSettings(
            int limit,
            String petalPotName,
            int pikminCount) {
        preferences.edit()
                .putInt("postcard_collection_limit", Math.max(0, Math.min(15, limit)))
                .putString("postcard_petal_pot_name", petalPotName.trim())
                .putInt("postcard_pikmin_count", Math.max(1, Math.min(5, pikminCount)))
                .remove("postcard_petal_color")
                .remove("postcard_petal_pots")
                .apply();
    }

    /** 保存真正的探險派遣設定；不保存任何畫面座標。 */
    void saveExpeditionDispatchSettings(
            int count,
            ExpeditionTargetMode targetMode,
            DispatchSelectionMethod selectionMethod,
            DispatchPikminType pikminType) {
        preferences.edit()
                .putInt("expedition_dispatch_count", Math.max(1, Math.min(99, count)))
                .putString("expedition_target_mode", (targetMode == null
                        ? ExpeditionTargetMode.FRUIT_AND_POT : targetMode).name())
                .putString("dispatch_selection_method", (selectionMethod == null
                        ? DispatchSelectionMethod.AUTO : selectionMethod).name())
                .putString("dispatch_pikmin_type", (pikminType == null
                        ? DispatchPikminType.MIXED : pikminType).name())
                .remove("reward_collection_mode")
                .apply();
    }

    void saveReturnRewardSettings(boolean receivePostcard) {
        preferences.edit()
                .putBoolean("return_reward_receive_postcard", receivePostcard)
                .apply();
    }

    /**
     * 只有 OCR 已確認離開接收頁後才同步扣除一次。
     * 使用 commit 是為了在方法回傳前確保重開應用程式仍能讀到新值。
     */
    @SuppressLint("ApplySharedPref")
    int recordConfirmedPostcardReceipt() {
        int remaining = PostcardRemainingCount.afterConfirmedReceipt(
                postcardCollectionLimit());
        return preferences.edit()
                .putInt("postcard_collection_limit", remaining)
                .commit()
                ? remaining
                : -1;
    }

    /** 確認完整回到派遣清單後扣除一次，並同步寫入以免程序重啟後重複執行。 */
    @SuppressLint("ApplySharedPref")
    int recordConfirmedExpeditionDispatch() {
        int remaining = ExpeditionRemainingCount.afterConfirmedDispatch(
                expeditionDispatchCount());
        return preferences.edit()
                .putInt("expedition_dispatch_count", remaining)
                .commit()
                ? remaining
                : -1;
    }
}
