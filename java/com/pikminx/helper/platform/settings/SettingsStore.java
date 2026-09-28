package com.pikminx.helper.platform.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.pikminx.helper.DispatchPikminType;
import com.pikminx.helper.DispatchSelectionMethod;
import com.pikminx.helper.ExpeditionTargetMode;
import com.pikminx.helper.PostcardPotCatalog;
import com.pikminx.helper.PostcardRemainingCount;

import java.util.ArrayList;
import java.util.List;

/** SharedPreferences-backed application settings owner. */
public final class SettingsStore {
    public static final String PREFERENCES_NAME = "pikminx_settings";
    private static final String DEFAULT_FLOWERS = "白色花瓣\n黃色花瓣\n紅色花瓣\n藍色花瓣";
    private static final String DEFAULT_POSTCARD_PETAL_POT = "黃色花瓣";
    private static final String EXPEDITION_SETTINGS_REVISION = "expedition_settings_revision";
    private final SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener expeditionSettingsListener;

    public SettingsStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    /** Constructor for deterministic in-memory preference tests. */
    public SettingsStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public int threshold() { return preferences.getInt("threshold", 50); }

    public int feedsPerSquad() {
        return Math.max(1, Math.min(10, preferences.getInt("feeds_per_squad", 6)));
    }

    public int maxSquadSwitches() {
        return Math.max(0, Math.min(120, preferences.getInt("max_squad_switches", 0)));
    }

    public int nectarMinimumThreshold() {
        return Math.max(0, Math.min(1200,
                preferences.getInt("nectar_minimum_threshold", 40)));
    }

    public int feedPetalLimit() {
        int value = Math.max(300, Math.min(1200,
                preferences.getInt("feed_petal_limit", 1200)));
        return 300 + ((value - 300) / 50) * 50;
    }

    public boolean overlayVisible() { return preferences.getBoolean("overlay_visible", true); }

    public int postcardCollectionLimit() {
        return Math.max(0, Math.min(15,
                preferences.getInt("postcard_collection_limit", 1)));
    }

    public String postcardPetalPotName() {
        String stored = preferences.getString(
                "postcard_petal_pot_name", DEFAULT_POSTCARD_PETAL_POT);
        String canonical = PostcardPotCatalog.canonicalName(stored);
        return canonical == null ? DEFAULT_POSTCARD_PETAL_POT : canonical;
    }

    public List<String> postcardPetalPotNames() { return PostcardPotCatalog.allNames(); }

    public int postcardPikminCount() {
        return Math.max(1, Math.min(5,
                preferences.getInt("postcard_pikmin_count", 1)));
    }

    public ExpeditionTargetMode expeditionTargetMode() {
        return ExpeditionTargetMode.fromStored(preferences.getString(
                "expedition_target_mode", ExpeditionTargetMode.FRUIT_AND_POT.name()));
    }

    public DispatchSelectionMethod dispatchSelectionMethod() {
        return DispatchSelectionMethod.fromStored(preferences.getString(
                "dispatch_selection_method", DispatchSelectionMethod.AUTO.name()));
    }

    public DispatchPikminType dispatchPikminType() {
        return DispatchPikminType.fromStored(preferences.getString(
                "dispatch_pikmin_type", DispatchPikminType.MIXED.name()));
    }

    /** Revision changes only for user configuration, never confirmed gameplay counters. */
    public long expeditionSettingsRevision() {
        try {
            return preferences.getLong(EXPEDITION_SETTINGS_REVISION, 0L);
        } catch (ClassCastException invalidLegacyValue) {
            return 0L;
        }
    }

    /** The service retains this observer; other SettingsStore instances share preferences. */
    public void setExpeditionSettingsChangeListener(Runnable changed) {
        if (expeditionSettingsListener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(expeditionSettingsListener);
            expeditionSettingsListener = null;
        }
        if (changed != null) {
            expeditionSettingsListener = (store, key) -> {
                if (key == null || EXPEDITION_SETTINGS_REVISION.equals(key)) changed.run();
            };
            preferences.registerOnSharedPreferenceChangeListener(expeditionSettingsListener);
        }
    }

    public boolean receiveReturnedPostcards() {
        return preferences.getBoolean("return_reward_receive_postcard", true);
    }

    public boolean continueReturnRewardOnNectarWarning() {
        return preferences.getBoolean("return_reward_continue_nectar_warning", false);
    }

    public void setOverlayVisible(boolean visible) {
        preferences.edit().putBoolean("overlay_visible", visible).apply();
    }

    public String flowersText() { return preferences.getString("flowers", DEFAULT_FLOWERS); }

    public List<String> allowedFlowers() {
        List<String> result = new ArrayList<>();
        for (String value : flowersText().split("[\\n,，]+")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public void save(int threshold, String flowers) {
        preferences.edit()
                .putInt("threshold", Math.max(1, Math.min(threshold, 9999)))
                .putString("flowers", flowers.trim())
                .apply();
    }

    public void saveFeedSettings(int feedsPerSquad, int maxSquadSwitches,
            int nectarMinimumThreshold, int petalLimit) {
        preferences.edit()
                .putInt("feeds_per_squad", Math.max(1, Math.min(10, feedsPerSquad)))
                .putInt("max_squad_switches", Math.max(0, Math.min(120, maxSquadSwitches)))
                .putInt("nectar_minimum_threshold", Math.max(0, Math.min(1200, nectarMinimumThreshold)))
                .putInt("feed_petal_limit", 300 + ((Math.max(300, Math.min(1200, petalLimit)) - 300) / 50) * 50)
                .apply();
    }

    public void savePostcardSettings(int limit, String petalPotName, int pikminCount) {
        preferences.edit()
                .putInt("postcard_collection_limit", Math.max(0, Math.min(15, limit)))
                .putString("postcard_petal_pot_name", petalPotName.trim())
                .putInt("postcard_pikmin_count", Math.max(1, Math.min(5, pikminCount)))
                .remove("postcard_petal_color")
                .remove("postcard_petal_pots")
                .apply();
    }

    public void saveExpeditionDispatchSettings(ExpeditionTargetMode targetMode,
            DispatchSelectionMethod selectionMethod, DispatchPikminType pikminType) {
        ExpeditionTargetMode savedTarget = targetMode == null
                ? ExpeditionTargetMode.FRUIT_AND_POT : targetMode;
        DispatchSelectionMethod savedMethod = selectionMethod == null
                ? DispatchSelectionMethod.AUTO : selectionMethod;
        DispatchPikminType savedType = pikminType == null ? DispatchPikminType.MIXED : pikminType;
        boolean changed = savedTarget != expeditionTargetMode()
                || savedMethod != dispatchSelectionMethod()
                || savedType != dispatchPikminType();
        SharedPreferences.Editor editor = preferences.edit()
                .putString("expedition_target_mode", savedTarget.name())
                .putString("dispatch_selection_method", savedMethod.name())
                .putString("dispatch_pikmin_type", savedType.name())
                .remove("reward_collection_mode");
        if (changed) editor.putLong(EXPEDITION_SETTINGS_REVISION, expeditionSettingsRevision() + 1L);
        editor.apply();
    }

    public void saveReturnRewardSettings(boolean receivePostcard, boolean continueOnNectarWarning) {
        preferences.edit()
                .putBoolean("return_reward_receive_postcard", receivePostcard)
                .putBoolean("return_reward_continue_nectar_warning", continueOnNectarWarning)
                .apply();
    }

    @SuppressLint("ApplySharedPref")
    public int recordConfirmedPostcardReceipt() {
        int remaining = PostcardRemainingCount.afterConfirmedReceipt(postcardCollectionLimit());
        return preferences.edit().putInt("postcard_collection_limit", remaining).commit()
                ? remaining : -1;
    }

}
