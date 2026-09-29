package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.pikminx.helper.platform.settings.SettingsStore;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SettingsStoreTest {
    @Test
    public void defaultsUseTheEstablishedPreferenceContract() {
        SettingsStore settings = new SettingsStore(new InMemoryPreferences());

        assertEquals("pikminx_settings", SettingsStore.PREFERENCES_NAME);
        assertEquals(50, settings.threshold());
        assertEquals(6, settings.feedsPerSquad());
        assertEquals(0, settings.maxSquadSwitches());
        assertEquals(40, settings.nectarMinimumThreshold());
        assertEquals(1200, settings.feedPetalLimit());
        assertTrue(settings.overlayVisible());
        assertEquals(1, settings.postcardCollectionLimit());
        assertEquals(1, settings.postcardPikminCount());
        assertEquals(ExpeditionTargetMode.FRUIT_AND_POT, settings.expeditionTargetMode());
        assertEquals(DispatchSelectionMethod.AUTO, settings.dispatchSelectionMethod());
        assertEquals(DispatchPikminType.MIXED, settings.dispatchPikminType());
        assertTrue(settings.receiveReturnedPostcards());
        assertFalse(settings.continueReturnRewardOnNectarWarning());
        assertTrue(settings.postcardPetalPotNames().contains(settings.postcardPetalPotName()));
        assertFalse(settings.allowedFlowers().isEmpty());
    }

    @Test
    public void establishedKeysRoundTripAndConfirmedCountersCommitSynchronously() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        preferences.edit()
                .putInt("threshold", -7)
                .putInt("feeds_per_squad", 0)
                .putInt("max_squad_switches", 130)
                .putInt("nectar_minimum_threshold", -1)
                .putInt("feed_petal_limit", 1176)
                .putBoolean("overlay_visible", false)
                .putInt("postcard_collection_limit", 17)
                .putInt("postcard_pikmin_count", 0)
                .putInt("expedition_dispatch_count", -4)
                .putString("expedition_target_mode", "unknown")
                .putString("dispatch_selection_method", "unknown")
                .putString("dispatch_pikmin_type", "unknown")
                .putString("flowers", "  alpha\n beta, gamma ，delta  ")
                .putString("postcard_petal_color", "legacy")
                .putString("postcard_petal_pots", "legacy")
                .putString("reward_collection_mode", "legacy")
                .apply();

        SettingsStore settings = new SettingsStore(preferences);
        assertEquals(-7, settings.threshold());
        assertEquals(1, settings.feedsPerSquad());
        assertEquals(120, settings.maxSquadSwitches());
        assertEquals(0, settings.nectarMinimumThreshold());
        assertEquals(1150, settings.feedPetalLimit());
        assertFalse(settings.overlayVisible());
        assertEquals(15, settings.postcardCollectionLimit());
        assertEquals(1, settings.postcardPikminCount());
        assertEquals(ExpeditionTargetMode.FRUIT_AND_POT, settings.expeditionTargetMode());
        assertEquals(DispatchSelectionMethod.AUTO, settings.dispatchSelectionMethod());
        assertEquals(DispatchPikminType.MIXED, settings.dispatchPikminType());
        assertEquals(4, settings.allowedFlowers().size());

        String petalPot = settings.postcardPetalPotNames().get(0);
        settings.save(10_000, "  alpha\nbeta  ");
        settings.saveFeedSettings(0, 121, 1201, 1176);
        settings.savePostcardSettings(17, petalPot, 0);
        settings.saveExpeditionDispatchSettings(null, null, null);
        settings.saveReturnRewardSettings(false, true);

        SettingsStore reloaded = new SettingsStore(preferences);
        assertEquals(9999, reloaded.threshold());
        assertEquals("alpha\nbeta", reloaded.flowersText());
        assertEquals(1, reloaded.feedsPerSquad());
        assertEquals(120, reloaded.maxSquadSwitches());
        assertEquals(1200, reloaded.nectarMinimumThreshold());
        assertEquals(1150, reloaded.feedPetalLimit());
        assertEquals(15, reloaded.postcardCollectionLimit());
        assertEquals(petalPot, reloaded.postcardPetalPotName());
        assertEquals(1, reloaded.postcardPikminCount());
        assertFalse(reloaded.receiveReturnedPostcards());
        assertTrue(reloaded.continueReturnRewardOnNectarWarning());
        assertFalse(preferences.contains("postcard_petal_color"));
        assertFalse(preferences.contains("postcard_petal_pots"));
        assertFalse(preferences.contains("reward_collection_mode"));

        int postcardRemaining = reloaded.recordConfirmedPostcardReceipt();
        assertEquals(1, preferences.commitCount());
        assertEquals(postcardRemaining, new SettingsStore(preferences).postcardCollectionLimit());
        assertEquals(-4, preferences.getInt("expedition_dispatch_count", 0));
    }

    @Test
    public void expeditionRevisionChangesForConfigurationButNotProgressOrRepeatedSave() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        SettingsStore settings = new SettingsStore(preferences);
        assertEquals(0L, settings.expeditionSettingsRevision());
        settings.saveExpeditionDispatchSettings(ExpeditionTargetMode.FRUIT,
                DispatchSelectionMethod.DRAG_12, DispatchPikminType.RED);
        long runRevision = settings.expeditionSettingsRevision();
        assertEquals(1L, runRevision);
        settings.saveExpeditionDispatchSettings(ExpeditionTargetMode.FRUIT,
                DispatchSelectionMethod.DRAG_12, DispatchPikminType.RED);
        assertEquals(runRevision, settings.expeditionSettingsRevision());
        preferences.edit().putInt("expedition_dispatch_count", 2).apply();
        assertEquals(runRevision, settings.expeditionSettingsRevision());
        settings.saveExpeditionDispatchSettings(ExpeditionTargetMode.POT,
                DispatchSelectionMethod.DRAG_12, DispatchPikminType.RED);
        assertEquals(runRevision + 1L, settings.expeditionSettingsRevision());
        assertEquals(runRevision + 1L, new SettingsStore(preferences).expeditionSettingsRevision());
    }

    @Test
    public void anotherSettingsInstanceNotifiesAfterTheWholeConfigurationIsSaved() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        SettingsStore observer = new SettingsStore(preferences);
        SettingsStore editor = new SettingsStore(preferences);
        int[] changes = {0};
        observer.setExpeditionSettingsChangeListener(() -> {
            changes[0]++;
            assertEquals(ExpeditionTargetMode.POT, observer.expeditionTargetMode());
            assertEquals(DispatchSelectionMethod.DRAG_12, observer.dispatchSelectionMethod());
            assertEquals(DispatchPikminType.BLUE, observer.dispatchPikminType());
        });
        editor.saveExpeditionDispatchSettings(ExpeditionTargetMode.POT,
                DispatchSelectionMethod.DRAG_12, DispatchPikminType.BLUE);
        assertEquals(1, changes[0]);
        preferences.edit().putInt("expedition_dispatch_count", 3).apply();
        editor.setOverlayVisible(false);
        assertEquals(1, changes[0]);
        observer.setExpeditionSettingsChangeListener(null);
        editor.saveExpeditionDispatchSettings(ExpeditionTargetMode.FRUIT,
                DispatchSelectionMethod.AUTO, DispatchPikminType.MIXED);
        assertEquals(1, changes[0]);
    }

    private static final class InMemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private int commitCount;
        private final Set<OnSharedPreferenceChangeListener> listeners = new HashSet<>();

        int commitCount() {
            return commitCount;
        }

        @Override
        public Map<String, ?> getAll() {
            return Map.copyOf(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            if (!(value instanceof Set<?>)) {
                return defaultValues;
            }
            Set<String> result = new HashSet<>();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new InMemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.remove(listener);
        }

        private final class InMemoryEditor implements Editor {
            private final Map<String, Object> additions = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return put(key, values == null ? null : new HashSet<>(values));
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            @Override
            public Editor remove(String key) {
                additions.remove(key);
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                additions.clear();
                removals.clear();
                return this;
            }

            @Override
            public boolean commit() {
                applyChanges();
                commitCount++;
                return true;
            }

            @Override
            public void apply() {
                applyChanges();
            }

            private Editor put(String key, Object value) {
                removals.remove(key);
                additions.put(key, value);
                return this;
            }

            private void applyChanges() {
                Map<String, Object> before = new HashMap<>(values);
                if (clear) {
                    values.clear();
                }
                for (String key : removals) {
                    values.remove(key);
                }
                for (Map.Entry<String, Object> entry : additions.entrySet()) {
                    if (entry.getValue() == null) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                Set<String> keys = new HashSet<>(before.keySet());
                keys.addAll(values.keySet());
                for (String key : keys) {
                    if (!java.util.Objects.equals(before.get(key), values.get(key))) {
                        for (OnSharedPreferenceChangeListener listener : new HashSet<>(listeners)) {
                            listener.onSharedPreferenceChanged(InMemoryPreferences.this, key);
                        }
                    }
                }
            }
        }
    }
}
