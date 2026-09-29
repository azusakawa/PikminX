package com.pikminx.helper.platform.config;

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
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 讀取 Cloudflare 公開設定；伺服器只提供 GET，APK 不含任何管理密鑰。 */
public final class RemoteConfigClient {
    static final String CONFIG_URL =
            "https://pikminx.twetq.com/v1/config";
    private static final String PREFS = "pikminx_remote_config";
    private static final String JSON_KEY = "json";
    private static final String SAVED_AT_KEY = "saved_at_millis";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int TIMEOUT_MILLIS = 5000;
    static final long CACHE_TTL_MILLIS = 5L * 60L * 1000L;
    private static final Object FETCH_LOCK = new Object();
    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static RemoteConfigStateModel.Snapshot state = RemoteConfigStateModel.initial();
    private static Status cachedStatus;
    private static boolean stateLoaded;
    private static long nextRequestId;
    private static long nextCallbackId;
    private static InFlight inFlight;

    public enum Feature {
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

    public interface Callback {
        void onResult(Status status);
    }

    public static long fetch(Context context, Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Remote config callback is required");
        }
        Context appContext = context.getApplicationContext();
        final long callbackId;
        final Status immediate;
        final long requestId;
        final boolean startFetch;
        synchronized (FETCH_LOCK) {
            ensureLoadedLocked(appContext);
            callbackId = ++nextCallbackId;
            if (isCacheFreshLocked(System.currentTimeMillis())) {
                immediate = cachedStatus;
                requestId = 0L;
                startFetch = false;
            } else {
                immediate = null;
                PendingCallback pending = new PendingCallback(callbackId, callback);
                if (inFlight != null) {
                    inFlight.callbacks.add(pending);
                    requestId = inFlight.requestId;
                    startFetch = false;
                } else {
                    requestId = ++nextRequestId;
                    state = RemoteConfigStateModel.beginFetch(
                            state, requestId, System.currentTimeMillis()).snapshot();
                    inFlight = new InFlight(requestId);
                    inFlight.callbacks.add(pending);
                    startFetch = true;
                }
            }
        }
        if (immediate != null) {
            postResult(new PendingCallback(callbackId, callback), immediate);
        } else if (startFetch) {
            FETCH_EXECUTOR.execute(() -> performFetch(appContext, requestId));
        }
        return callbackId;
    }

    public static Status cached(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (FETCH_LOCK) {
            ensureLoadedLocked(appContext);
            isCacheFreshLocked(System.currentTimeMillis());
            return cachedStatus;
        }
    }

    public static RemoteConfigStateModel.Snapshot state(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (FETCH_LOCK) {
            ensureLoadedLocked(appContext);
            isCacheFreshLocked(System.currentTimeMillis());
            return state;
        }
    }

    private static void ensureLoadedLocked(Context context) {
        if (stateLoaded) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = preferences.getString(JSON_KEY, null);
        long savedAtMillis = preferences.getLong(SAVED_AT_KEY, 0L);
        if (json == null) {
            cachedStatus = null;
            state = RemoteConfigStateModel.initial();
        } else {
            try {
                cachedStatus = parse(json);
                RemoteConfigStateModel.State cacheState =
                        RemoteConfigStateModel.isFresh(
                                RemoteConfigStateModel.restore(
                                        RemoteConfigStateModel.State.VALID,
                                        0L,
                                        cachedStatus.configVersion,
                                        savedAtMillis,
                                        ""),
                                System.currentTimeMillis(),
                                CACHE_TTL_MILLIS)
                                ? RemoteConfigStateModel.State.VALID
                                : RemoteConfigStateModel.State.STALE_USING_CACHE;
                state = RemoteConfigStateModel.restore(
                        cacheState, 0L, cachedStatus.configVersion, savedAtMillis, "");
            } catch (JSONException ignored) {
                cachedStatus = null;
                state = RemoteConfigStateModel.restore(
                        RemoteConfigStateModel.State.ERROR, 0L, -1, 0L,
                        "Invalid cached remote configuration");
            }
        }
        stateLoaded = true;
    }

    private static boolean isCacheFreshLocked(long nowMillis) {
        if (cachedStatus != null
                && state.state() == RemoteConfigStateModel.State.VALID
                && !RemoteConfigStateModel.isFresh(state, nowMillis, CACHE_TTL_MILLIS)) {
            state = RemoteConfigStateModel.restore(
                    RemoteConfigStateModel.State.STALE_USING_CACHE,
                    state.requestId(),
                    state.configVersion(),
                    state.fetchedAtMillis(),
                    state.error());
        }
        return cachedStatus != null
                && RemoteConfigStateModel.isFresh(state, nowMillis, CACHE_TTL_MILLIS);
    }

    private static void performFetch(Context context, long requestId) {
        String json = null;
        Status fetchedStatus = null;
        String failure = "";
        try {
            json = download();
            fetchedStatus = parse(json);
        } catch (Exception exception) {
            failure = failureMessage(exception);
        }

        Status result;
        List<PendingCallback> callbacks;
        synchronized (FETCH_LOCK) {
            ensureLoadedLocked(context);
            if (inFlight == null || inFlight.requestId != requestId) {
                return;
            }

            long completedAtMillis = System.currentTimeMillis();
            boolean accepted = fetchedStatus != null;
            if (accepted && cachedStatus != null
                    && fetchedStatus.configVersion < cachedStatus.configVersion) {
                accepted = false;
                failure = "Remote configuration is older than the cached version";
            }

            if (accepted) {
                save(context, json, completedAtMillis);
                cachedStatus = fetchedStatus;
                result = fetchedStatus;
                RemoteConfigStateModel.CompletionResult completion =
                        RemoteConfigStateModel.completeFetch(
                                state, requestId, true, fetchedStatus.configVersion,
                                completedAtMillis, "");
                if (completion.accepted()) {
                    state = completion.snapshot();
                }
            } else {
                result = cachedStatus;
                RemoteConfigStateModel.CompletionResult completion =
                        RemoteConfigStateModel.completeFetch(
                                state, requestId, false,
                                cachedStatus == null ? -1 : cachedStatus.configVersion,
                                completedAtMillis, failure);
                if (completion.accepted()) {
                    state = completion.snapshot();
                }
            }
            callbacks = new ArrayList<>(inFlight.callbacks);
            inFlight = null;
        }

        for (PendingCallback pending : callbacks) {
            postResult(pending, result);
        }
    }

    private static void postResult(PendingCallback pending, Status result) {
        mainHandler().post(() -> pending.callback.onResult(result));
    }

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static Handler mainHandler() {
        return new Handler(Looper.getMainLooper());
    }

    private static final class InFlight {
        final long requestId;
        final List<PendingCallback> callbacks = new ArrayList<>();

        InFlight(long requestId) {
            this.requestId = requestId;
        }
    }

    private static final class PendingCallback {
        final long callbackId;
        final Callback callback;

        PendingCallback(long callbackId, Callback callback) {
            this.callbackId = callbackId;
            this.callback = callback;
        }
    }

    public static Status parse(String json) throws JSONException {
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
        String sha256 = value.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
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
                || !isHttpsUrl(downloadUrl)
                || downloadUrl.isEmpty() != sha256.isEmpty()
                || (!sha256.isEmpty() && !isValidSha256(sha256))) {
            throw new JSONException("Invalid remote configuration");
        }

        return new Status(configVersion, mode, message, minimumVersionCode,
                latestVersionCode, latestVersionName, forceUpdate, downloadUrl, sha256, updatedAt,
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

    static boolean isHttpsUrl(String value) {
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

    public static boolean isValidSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void save(Context context, String json, long savedAtMillis) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(JSON_KEY, json)
                .putLong(SAVED_AT_KEY, savedAtMillis)
                .apply();
    }

    public static final class Status {
        private final int configVersion;
        private final String mode;
        private final String message;
        private final int minimumVersionCode;
        private final int latestVersionCode;
        private final String latestVersionName;
        private final boolean forceUpdate;
        private final String downloadUrl;
        private final String sha256;
        private final String updatedAt;
        private final boolean plantingEnabled;
        private final boolean postcardEnabled;
        private final boolean dispatchEnabled;
        private final boolean returnRewardEnabled;
        private final boolean overlayEnabled;

        public Status(int configVersion, String mode, String message, int minimumVersionCode,
                String latestVersionName, boolean forceUpdate, String downloadUrl,
                String updatedAt) {
            this(configVersion, mode, message, minimumVersionCode, 0, latestVersionName,
                    forceUpdate, downloadUrl, "", updatedAt, true, true, true, true, true);
        }

        public Status(int configVersion, String mode, String message, int minimumVersionCode,
                int latestVersionCode, String latestVersionName, boolean forceUpdate,
                String downloadUrl, String updatedAt, boolean plantingEnabled,
                boolean postcardEnabled, boolean dispatchEnabled, boolean returnRewardEnabled,
                boolean overlayEnabled) {
            this(configVersion, mode, message, minimumVersionCode, latestVersionCode,
                    latestVersionName, forceUpdate, downloadUrl, "", updatedAt, plantingEnabled,
                    postcardEnabled, dispatchEnabled, returnRewardEnabled, overlayEnabled);
        }

        public Status(int configVersion, String mode, String message, int minimumVersionCode,
                int latestVersionCode, String latestVersionName, boolean forceUpdate,
                String downloadUrl, String sha256, String updatedAt, boolean plantingEnabled,
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
            this.sha256 = sha256;
            this.updatedAt = updatedAt;
            this.plantingEnabled = plantingEnabled;
            this.postcardEnabled = postcardEnabled;
            this.dispatchEnabled = dispatchEnabled;
            this.returnRewardEnabled = returnRewardEnabled;
            this.overlayEnabled = overlayEnabled;
        }

        public boolean blocksAutomation(int currentVersionCode) {
            return blocksAutomation(currentVersionCode, null);
        }

        public boolean blocksAutomation(int currentVersionCode, Feature feature) {
            return !"enabled".equals(mode)
                    || (forceUpdate && currentVersionCode < minimumVersionCode)
                    || (feature != null && !featureEnabled(feature));
        }

        public boolean featureEnabled(Feature feature) {
            return switch (feature) {
                case PLANTING -> plantingEnabled;
                case POSTCARD -> postcardEnabled;
                case DISPATCH -> dispatchEnabled;
                case RETURN_REWARD -> returnRewardEnabled;
                case OVERLAY -> overlayEnabled;
            };
        }

        public boolean updateAvailable(int currentVersionCode) {
            return latestVersionCode > currentVersionCode
                    || (forceUpdate && currentVersionCode < minimumVersionCode);
        }

        public boolean hasInstallableUpdate(int currentVersionCode) {
            return updateAvailable(currentVersionCode)
                    && !downloadUrl.isEmpty()
                    && isValidSha256(sha256);
        }

        public boolean forceUpdate() {
            return forceUpdate;
        }

        public String message() {
            return message.isEmpty() ? "遠端服務目前不可用" : message;
        }

        public int configVersion() {
            return configVersion;
        }

        public int latestVersionCode() {
            return latestVersionCode;
        }

        public String latestVersionName() {
            return latestVersionName;
        }

        public String downloadUrl() {
            return downloadUrl;
        }

        public String sha256() {
            return sha256;
        }

        public String updatedAt() {
            return updatedAt;
        }

    }
}
