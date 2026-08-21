package com.pikminx.helper;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 匿名上傳單次流程摘要；不把統計資料保存到裝置。 */
final class UsageTelemetryClient {
    static final String USAGE_URL = "https://pikminx.twetq.com/v1/usage";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final int MAX_UPLOAD_ATTEMPTS = 3;

    enum Operation {
        PLANTING("planting"),
        POSTCARD("postcard"),
        DISPATCH("dispatch"),
        RETURN_REWARD("returnReward");

        private final String wireName;

        Operation(String wireName) {
            this.wireName = wireName;
        }
    }

    private UsageTelemetryClient() {}

    static Session start(Context context, Operation operation, int requestedCount,
            int configVersion) {
        return new Session(operation, requestedCount,
                configVersion);
    }

    static final class Session {
        private final String sessionId = UUID.randomUUID().toString();
        private final Operation operation;
        private final int requestedCount;
        private final int configVersion;
        private final long startedAtMillis = System.currentTimeMillis();
        private int plantingCount;
        private int postcardCount;
        private int dispatchFruitCount;
        private int dispatchPotCount;
        private int returnRewardCount;
        private boolean finished;

        private Session(Operation operation, int requestedCount,
                int configVersion) {
            this.operation = operation;
            this.requestedCount = Math.max(0, Math.min(99, requestedCount));
            this.configVersion = Math.max(0, configVersion);
        }

        void recordPlanting() { plantingCount++; }
        void recordPostcard() { postcardCount++; }
        void recordDispatchFruit() { dispatchFruitCount++; }
        void recordDispatchPot() { dispatchPotCount++; }
        void recordReturnRewardSession() { returnRewardCount++; }

        void finish(String outcome) {
            if (finished) return;
            finished = true;
            Snapshot snapshot = new Snapshot(sessionId, operation, outcome, requestedCount,
                    System.currentTimeMillis() - startedAtMillis, configVersion, plantingCount,
                    postcardCount, dispatchFruitCount, dispatchPotCount, returnRewardCount);
            new Thread(() -> upload(snapshot), "pikminx-usage-upload").start();
        }
    }

    static final class Snapshot {
        private final String sessionId;
        private final Operation operation;
        private final String outcome;
        private final int requestedCount;
        private final long durationMillis;
        private final int configVersion;
        private final int plantingCount;
        private final int postcardCount;
        private final int dispatchFruitCount;
        private final int dispatchPotCount;
        private final int returnRewardCount;

        Snapshot(String sessionId, Operation operation, String outcome, int requestedCount,
                long durationMillis, int configVersion, int plantingCount, int postcardCount,
                int dispatchFruitCount, int dispatchPotCount, int returnRewardCount) {
            this.sessionId = sessionId;
            this.operation = operation;
            this.outcome = outcome;
            this.requestedCount = requestedCount;
            this.durationMillis = durationMillis;
            this.configVersion = configVersion;
            this.plantingCount = plantingCount;
            this.postcardCount = postcardCount;
            this.dispatchFruitCount = dispatchFruitCount;
            this.dispatchPotCount = dispatchPotCount;
            this.returnRewardCount = returnRewardCount;
        }

        int plantingCount() { return plantingCount; }
        int postcardCount() { return postcardCount; }
        int dispatchFruitCount() { return dispatchFruitCount; }
        int dispatchPotCount() { return dispatchPotCount; }
        int returnRewardCount() { return returnRewardCount; }
    }

    private static void upload(Snapshot snapshot) {
        try {
            byte[] payload = payload(snapshot).toString().getBytes(StandardCharsets.UTF_8);
            if (payload.length > MAX_PAYLOAD_BYTES) return;
            for (int attempt = 0; attempt < MAX_UPLOAD_ATTEMPTS; attempt++) {
                if (post(payload)) return;
                try {
                    Thread.sleep(250L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (JSONException | IOException ignored) {
            // Telemetry must never affect the automation flow.
        }
    }

    private static JSONObject payload(Snapshot snapshot) throws JSONException {
        JSONObject counts = new JSONObject().put("planting", snapshot.plantingCount)
                .put("postcard", snapshot.postcardCount)
                .put("dispatchFruit", snapshot.dispatchFruitCount)
                .put("dispatchPot", snapshot.dispatchPotCount)
                .put("returnReward", snapshot.returnRewardCount);
        return new JSONObject().put("schemaVersion", 1).put("sessionId", snapshot.sessionId)
                .put("operation", snapshot.operation.wireName).put("outcome", snapshot.outcome)
                .put("requestedCount", snapshot.requestedCount)
                .put("durationSeconds", Math.max(0, snapshot.durationMillis / 1000L))
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("appVersionName", BuildConfig.VERSION_NAME)
                .put("configVersion", snapshot.configVersion).put("counts", counts);
    }

    private static boolean post(byte[] payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(USAGE_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setFixedLengthStreamingMode(payload.length);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } finally {
            connection.disconnect();
        }
    }
}
