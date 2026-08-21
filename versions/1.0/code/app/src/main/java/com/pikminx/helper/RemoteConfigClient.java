package com.pikminx.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 讀取 Cloudflare 公開設定；伺服器只提供 GET，APK 不含任何管理密鑰。 */
final class RemoteConfigClient {
    static final String CONFIG_URL =
            "https://pikminx.twetq.com/v1/config";
    private static final String PREFS = "pikminx_remote_config";
    private static final String JSON_KEY = "json";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int TIMEOUT_MILLIS = 5000;

    enum Feature {
        PLANTING("planting"),
        POSTCARD("postcard"),
        DISPATCH("dispatch"),
        RETURN_REWARD("returnReward"),
        OVERLAY("overlay");

        private final String key;

        Feature(String key) {
            this.key = key;
        }
    }

    private RemoteConfigClient() {}

    interface Callback {
        void onResult(Status status);
    }

    static void fetch(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            Status status = null;
            try {
                String json = download();
                status = parse(json);
                Status cached = cached(appContext);
                if (cached == null || status.configVersion >= cached.configVersion) {
                    save(appContext, json);
                } else {
                    status = cached;
                }
            } catch (IOException | JSONException ignored) {
                status = cached(appContext);
            }

            Status result = status;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }, "pikminx-remote-config").start();
    }

    static Status cached(Context context) {
        String json = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(JSON_KEY, null);
        if (json == null) {
            return null;
        }
        try {
            return parse(json);
        } catch (JSONException ignored) {
            return null;
        }
    }

    static Status parse(String json) throws JSONException {
        JSONObject value = new JSONObject(json);
        int schemaVersion = value.getInt("schemaVersion");
        int configVersion = value.getInt("configVersion");
        String mode = value.getString("mode");
        String message = value.optString("message", "").trim();
        int minimumVersionCode = value.optInt("minimumVersionCode", 1);
        int latestVersionCode = value.optInt("latestVersionCode", 0);
        String latestVersionName = value.optString("latestVersionName", "").trim();
        boolean forceUpdate = value.optBoolean("forceUpdate", false);
        String downloadUrl = value.optString("downloadUrl", "").trim();
        String updatedAt = value.optString("updatedAt", "").trim();
        JSONObject features = null;
        if (value.has("features")) {
            Object rawFeatures = value.get("features");
            if (!(rawFeatures instanceof JSONObject)) {
                throw new JSONException("Invalid feature configuration");
            }
            features = (JSONObject) rawFeatures;
        }
        boolean planting = feature(features, Feature.PLANTING, true);
        boolean postcard = feature(features, Feature.POSTCARD, true);
        boolean dispatch = feature(features, Feature.DISPATCH, true);
        boolean returnReward = feature(features, Feature.RETURN_REWARD, true);
        boolean overlay = feature(features, Feature.OVERLAY, true);

        if (schemaVersion != 1 || configVersion < 1 || configVersion > 1_000_000_000
                || (!"enabled".equals(mode) && !"maintenance".equals(mode)
                && !"disabled".equals(mode))
                || message.length() > 256
                || minimumVersionCode < 1 || minimumVersionCode > 1_000_000_000
                || latestVersionCode < 0 || latestVersionCode > 1_000_000_000
                || latestVersionName.length() > 32 || updatedAt.length() > 64
                || !isHttpsUrl(downloadUrl)) {
            throw new JSONException("Invalid remote configuration");
        }

        return new Status(configVersion, mode, message, minimumVersionCode,
                latestVersionCode, latestVersionName, forceUpdate, downloadUrl, updatedAt,
                planting, postcard, dispatch, returnReward, overlay);
    }

    private static boolean feature(JSONObject features, Feature feature, boolean fallback)
            throws JSONException {
        if (features == null || !features.has(feature.key)) {
            return fallback;
        }
        Object value = features.get(feature.key);
        if (!(value instanceof Boolean)) {
            throw new JSONException("Invalid feature flag: " + feature.key);
        }
        return (Boolean) value;
    }

    private static String download() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Remote configuration HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("Remote configuration is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isHttpsUrl(String value) {
        if (value.isEmpty()) {
            return true;
        }
        try {
            URL url = new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && url.getHost() != null
                    && !url.getHost().isEmpty()
                    && url.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void save(Context context, String json) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(JSON_KEY, json).apply();
    }

    static final class Status {
        private final int configVersion;
        private final String mode;
        private final String message;
        private final int minimumVersionCode;
        private final int latestVersionCode;
        private final String latestVersionName;
        private final boolean forceUpdate;
        private final String downloadUrl;
        private final String updatedAt;
        private final boolean plantingEnabled;
        private final boolean postcardEnabled;
        private final boolean dispatchEnabled;
        private final boolean returnRewardEnabled;
        private final boolean overlayEnabled;

        Status(int configVersion, String mode, String message, int minimumVersionCode,
                String latestVersionName, boolean forceUpdate, String downloadUrl,
                String updatedAt) {
            this(configVersion, mode, message, minimumVersionCode, 0, latestVersionName,
                    forceUpdate, downloadUrl, updatedAt, true, true, true, true, true);
        }

        Status(int configVersion, String mode, String message, int minimumVersionCode,
                int latestVersionCode, String latestVersionName, boolean forceUpdate,
                String downloadUrl, String updatedAt, boolean plantingEnabled,
                boolean postcardEnabled, boolean dispatchEnabled, boolean returnRewardEnabled,
                boolean overlayEnabled) {
            this.configVersion = configVersion;
            this.mode = mode;
            this.message = message;
            this.minimumVersionCode = minimumVersionCode;
            this.latestVersionCode = latestVersionCode;
            this.latestVersionName = latestVersionName;
            this.forceUpdate = forceUpdate;
            this.downloadUrl = downloadUrl;
            this.updatedAt = updatedAt;
            this.plantingEnabled = plantingEnabled;
            this.postcardEnabled = postcardEnabled;
            this.dispatchEnabled = dispatchEnabled;
            this.returnRewardEnabled = returnRewardEnabled;
            this.overlayEnabled = overlayEnabled;
        }

        boolean blocksAutomation(int currentVersionCode) {
            return blocksAutomation(currentVersionCode, null);
        }

        boolean blocksAutomation(int currentVersionCode, Feature feature) {
            return !"enabled".equals(mode)
                    || (forceUpdate && currentVersionCode < minimumVersionCode)
                    || (feature != null && !featureEnabled(feature));
        }

        boolean featureEnabled(Feature feature) {
            return switch (feature) {
                case PLANTING -> plantingEnabled;
                case POSTCARD -> postcardEnabled;
                case DISPATCH -> dispatchEnabled;
                case RETURN_REWARD -> returnRewardEnabled;
                case OVERLAY -> overlayEnabled;
            };
        }

        boolean updateAvailable(int currentVersionCode) {
            return latestVersionCode > currentVersionCode
                    || (forceUpdate && currentVersionCode < minimumVersionCode);
        }

        boolean forceUpdate() {
            return forceUpdate;
        }

        String message() {
            return message.isEmpty() ? "遠端服務目前不可用" : message;
        }

        int configVersion() {
            return configVersion;
        }

        int latestVersionCode() {
            return latestVersionCode;
        }

        String latestVersionName() {
            return latestVersionName;
        }

        String downloadUrl() {
            return downloadUrl;
        }

        String updatedAt() {
            return updatedAt;
        }
    }
}
