package com.pikminx.helper.platform.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

import com.pikminx.helper.BuildConfig;
import com.pikminx.helper.CaptureGeometry;
import com.pikminx.helper.OcrScan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Anonymous workflow summaries persisted until the existing transport accepts them. */
public final class UsageTelemetryClient {
    public static final String USAGE_URL = "https://pikminx.twetq.com/v1/usage";
    public static final int TELEMETRY_SCHEMA_VERSION = 9;
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024;
    private static final int MAX_UPLOAD_ATTEMPTS = 3;
    private static final int MAX_DIAGNOSTIC_EVENTS = 32;
    private static final String OUTBOX_PREFS = "pikminx_usage_outbox";
    private static final String OUTBOX_PREFIX = "batch:";
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    public enum Operation {
        PLANTING("planting"),
        FEED("feed"),
        POSTCARD("postcard"),
        DISPATCH("dispatch"),
        RETURN_REWARD("returnReward");

        private final String wireName;

        Operation(String wireName) {
            this.wireName = wireName;
        }
    }

    private UsageTelemetryClient() {}

    public static Session start(
            Context context,
            Operation operation,
            int requestedCount,
            int configVersion) {
        return new Session(
                context == null ? null : context.getApplicationContext(),
                operation,
                requestedCount,
                configVersion);
    }

    public static final class Session {
        private final Context context;
        private final String sessionId = UUID.randomUUID().toString();
        private final Operation operation;
        private final int requestedCount;
        private final int configVersion;
        private final long startedAtMillis = System.currentTimeMillis();
        private final long startedAtNanos = System.nanoTime();
        private int plantingCount;
        private String plantingFlower = "";
        private int plantingRemaining = -1;
        private int postcardCount;
        private int dispatchFruitCount;
        private int dispatchPotCount;
        private int returnRewardCount;
        private int feedNectarCount;
        private int feedPetalCount;
        private final List<DiagnosticEvent> diagnosticLog = new ArrayList<>();
        private int diagnosticSequence;
        private boolean finished;

        public Session(Operation operation, int requestedCount,
                int configVersion) {
            this(null, operation, requestedCount, configVersion);
        }

        private Session(Context context, Operation operation, int requestedCount,
                int configVersion) {
            this.context = context;
            this.operation = operation;
            this.requestedCount = Math.max(0, Math.min(99, requestedCount));
            this.configVersion = Math.max(0, configVersion);
        }

        public void recordPlantingPetalRemaining(String flower, int remaining) {
            if (operation != Operation.PLANTING
                    || flower == null
                    || remaining < 0) {
                return;
            }
            String observedFlower = flower.trim();
            if (observedFlower.isEmpty()) {
                return;
            }
            if (!observedFlower.equals(plantingFlower)
                    || plantingRemaining < 0
                    || remaining > plantingRemaining) {
                plantingFlower = observedFlower;
                plantingRemaining = remaining;
                return;
            }
            if (remaining < plantingRemaining) {
                plantingCount = (int) Math.min(
                        1_000_000_000L,
                        (long) plantingCount + plantingRemaining - remaining);
            }
            plantingRemaining = remaining;
        }

        public void recordPostcard() {
            postcardCount++;
        }

        public void recordDispatchFruit() {
            dispatchFruitCount++;
        }

        public void recordDispatchPot() {
            dispatchPotCount++;
        }

        public void recordReturnRewardSession() {
            returnRewardCount++;
        }

        public void recordFeedNectar(int consumed) {
            if (consumed > 0) {
                feedNectarCount = (int) Math.min(
                        1_000_000_000L, (long) feedNectarCount + consumed);
            }
        }

        public void recordFeedPetals(int collected) {
            if (collected > 0) {
                feedPetalCount = (int) Math.min(
                        1_000_000_000L, (long) feedPetalCount + collected);
            }
        }

        public synchronized void recordOcr(String source, String stage, OcrScan.Frame frame) {
            OcrScan.Transform transform = frame.transform();
            addDiagnostic(new OcrDiagnosticEvent(
                    ++diagnosticSequence, "ocr", safeLabel(source), safeLabel(stage),
                    frame.profile().name(), "success",
                    transform.sourceWidth(), transform.sourceHeight(),
                    transform.analysisWidth(), transform.analysisHeight(),
                    frame.tokens().size(), Math.max(0, frame.elapsedMillis()), "",
                    frame.captureGeometry(), transform));
        }

        public synchronized void recordOcrFailure(
                String source, String stage, OcrScan.Profile profile,
                CaptureGeometry captureGeometry, Exception error) {
            OcrScan.Transform transform = OcrScan.Transform.create(
                    profile,
                    captureGeometry.bitmapWidth(),
                    captureGeometry.bitmapHeight(),
                    captureGeometry);
            addDiagnostic(new OcrDiagnosticEvent(
                    ++diagnosticSequence, "ocr", safeLabel(source), safeLabel(stage),
                    profile.name(), "failure",
                    transform.sourceWidth(), transform.sourceHeight(),
                    transform.analysisWidth(), transform.analysisHeight(),
                    0, 0,
                    error == null ? "Exception" : safeLabel(error.getClass().getSimpleName()),
                    captureGeometry, transform));
        }

        public synchronized void recordFeedSearchGesture(
                CaptureGeometry captureGeometry,
                int bitmapX,
                int bitmapY,
                int screenX,
                int screenY,
                String mapping,
                int attempt) {
            addDiagnostic(new GestureDiagnosticEvent(
                    ++diagnosticSequence,
                    "gesture",
                    "feed-search",
                    "OPENING_NECTAR_SEARCH",
                    "attempt",
                    Math.max(1, attempt),
                    captureGeometry,
                    bitmapX,
                    bitmapY,
                    screenX,
                    screenY,
                    "display-bitmap".equals(mapping) ? mapping : "scaled"));
        }

        public synchronized void recordFeedNectarSelection(
                String outcome,
                String reason,
                int scan,
                int candidateFrames,
                int missingFrames,
                boolean nectarCountFound,
                boolean petalCountFound) {
            addDiagnostic(new FeedNectarSelectionDiagnosticEvent(
                    ++diagnosticSequence,
                    "feed-nectar-selection",
                    "nectar-selection",
                    "SELECTING_NECTAR",
                    safeLabel(outcome),
                    safeLabel(reason),
                    Math.max(1, Math.min(99, scan)),
                    Math.max(0, Math.min(2, candidateFrames)),
                    Math.max(0, Math.min(8, missingFrames)),
                    nectarCountFound,
                    petalCountFound));
        }

        public synchronized void recordDispatchRecognition(
                String bank, String bestTemplate, double bestScore,
                String secondTemplate, double secondScore, double margin,
                int left, int top, int width, int height, long frameId,
                String visual, String classification, String metric) {
            addDiagnostic(new DispatchRecognitionDiagnosticEvent(++diagnosticSequence,
                    "dispatch-recognition", bank, bestTemplate, bestScore, secondTemplate,
                    secondScore, margin, left, top, width, height, frameId, visual, classification, metric));
        }

        public synchronized void recordDispatchDetail(
                String outcome,
                String reason,
                String match,
                int attempt,
                boolean transitionPending,
                int bitmapWidth,
                int bitmapHeight,
                int bitmapX,
                int bitmapY) {
            int safeWidth = Math.max(1, Math.min(10_000, bitmapWidth));
            int safeHeight = Math.max(1, Math.min(10_000, bitmapHeight));
            boolean validPoint = bitmapX >= 0 && bitmapX < safeWidth
                    && bitmapY >= 0 && bitmapY < safeHeight;
            addDiagnostic(new DispatchDetailDiagnosticEvent(
                    ++diagnosticSequence,
                    "dispatch-detail",
                    "detail-action",
                    "DETAIL",
                    outcome,
                    reason,
                    match,
                    Math.max(0, Math.min(2, attempt)),
                    transitionPending,
                    Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L),
                    safeWidth,
                    safeHeight,
                    validPoint ? bitmapX : -1,
                    validPoint ? bitmapY : -1,
                    "stage-timeout".equals(outcome) ? safeLabel(reason) : ""));
        }

        public synchronized void recordDispatchSelection(
                String outcome,
                String reason,
                int attempt,
                int selectedCount,
                boolean goVisible,
                String layout,
                int bitmapWidth,
                int bitmapHeight,
                int bitmapX,
                int bitmapY) {
            addDiagnostic(new DispatchSelectionDiagnosticEvent(
                    ++diagnosticSequence,
                    "dispatch-selection",
                    "auto-selection",
                    "SELECTION",
                    outcome,
                    reason,
                    Math.max(0, Math.min(3, attempt)),
                    Math.max(-1, Math.min(12, selectedCount)),
                    goVisible,
                    "search-expanded".equals(layout) ? layout : "default",
                    bitmapWidth,
                    bitmapHeight,
                    bitmapX,
                    bitmapY));
        }

        private void addDiagnostic(DiagnosticEvent event) {
            if (diagnosticLog.size() == MAX_DIAGNOSTIC_EVENTS) {
                int removable = 0;
                for (int index = 0; index < diagnosticLog.size(); index++) {
                    if ("ocr".equals(diagnosticLog.get(index).type())) {
                        removable = index;
                        break;
                    }
                }
                diagnosticLog.remove(removable);
            }
            diagnosticLog.add(event);
        }

        public void finish(String outcome) {
            finish(outcome, snapshot ->
                    UPLOAD_EXECUTOR.execute(() -> queueAndUpload(context, snapshot)));
        }

        /** Allows a host-local dispatcher to observe one terminal snapshot without networking. */
        public synchronized void finish(String outcome, Consumer<Snapshot> dispatcher) {
            if (finished) {
                return;
            }
            finished = true;
            Snapshot snapshot = snapshot(
                    outcome,
                    System.currentTimeMillis() - startedAtMillis);
            if (dispatcher != null) {
                dispatcher.accept(snapshot);
            }
        }

        public synchronized Snapshot snapshot(String outcome, long durationMillis) {
            return new Snapshot(
                    sessionId,
                    operation,
                    outcome,
                    requestedCount,
                    durationMillis,
                    configVersion,
                    plantingCount,
                    postcardCount,
                    dispatchFruitCount,
                    dispatchPotCount,
                    returnRewardCount,
                    feedNectarCount,
                    feedPetalCount,
                    diagnosticLog);
        }
    }

    private interface DiagnosticEvent {
        int sequence();
        String type();
    }

    private record OcrDiagnosticEvent(
            int sequence, String type, String source, String stage, String profile, String outcome,
            int sourceWidth, int sourceHeight, int analysisWidth, int analysisHeight,
            int tokenCount, long elapsedMillis, String error, CaptureGeometry captureGeometry,
            OcrScan.Transform transform) implements DiagnosticEvent {}

    private record GestureDiagnosticEvent(
            int sequence, String type, String source, String stage, String outcome, int attempt,
            CaptureGeometry captureGeometry,
            int bitmapX, int bitmapY, int screenX, int screenY,
            String mapping) implements DiagnosticEvent {}

    private record FeedNectarSelectionDiagnosticEvent(
            int sequence,
            String type,
            String source,
            String stage,
            String outcome,
            String reason,
            int scan,
            int candidateFrames,
            int missingFrames,
            boolean nectarCountFound,
            boolean petalCountFound) implements DiagnosticEvent {}

    private record DispatchRecognitionDiagnosticEvent(
            int sequence, String type, String bank, String bestTemplate, double bestScore,
            String secondTemplate, double secondScore, double margin,
            int left, int top, int width, int height, long frameId,
            String visual, String classification, String metric) implements DiagnosticEvent {}

    private record DispatchDetailDiagnosticEvent(
            int sequence, String type, String source, String stage, String outcome,
            String reason, String match, int attempt,
            boolean transitionPending, long eventElapsedMillis,
            int bitmapWidth, int bitmapHeight, int bitmapX, int bitmapY,
            String stopReason) implements DiagnosticEvent {}

    private record DispatchSelectionDiagnosticEvent(
            int sequence, String type, String source, String stage, String outcome,
            String reason, int attempt, int selectedCount, boolean goVisible, String layout,
            int bitmapWidth, int bitmapHeight, int bitmapX, int bitmapY)
            implements DiagnosticEvent {}

    public static final class Snapshot {
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
        private final int feedNectarCount;
        private final int feedPetalCount;
        private final List<DiagnosticEvent> diagnosticLog;

        public Snapshot(String sessionId, Operation operation, String outcome, int requestedCount,
                long durationMillis, int configVersion, int plantingCount, int postcardCount,
                int dispatchFruitCount, int dispatchPotCount, int returnRewardCount,
                int feedNectarCount) {
            this(sessionId, operation, outcome, requestedCount, durationMillis, configVersion,
                    plantingCount, postcardCount, dispatchFruitCount, dispatchPotCount,
                    returnRewardCount, feedNectarCount, 0, List.of());
        }

        private Snapshot(String sessionId, Operation operation, String outcome, int requestedCount,
                long durationMillis, int configVersion, int plantingCount, int postcardCount,
                int dispatchFruitCount, int dispatchPotCount, int returnRewardCount,
                int feedNectarCount, int feedPetalCount,
                List<DiagnosticEvent> diagnosticLog) {
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
            this.feedNectarCount = feedNectarCount;
            this.feedPetalCount = feedPetalCount;
            this.diagnosticLog = List.copyOf(diagnosticLog);
        }

        public int plantingCount() {
            return plantingCount;
        }

        /** Immutable identifier reused when a persisted batch is retried. */
        public String batchId() {
            return sessionId;
        }

        public int postcardCount() {
            return postcardCount;
        }

        public int dispatchFruitCount() {
            return dispatchFruitCount;
        }

        public int dispatchPotCount() {
            return dispatchPotCount;
        }

        public int returnRewardCount() {
            return returnRewardCount;
        }

        public int feedNectarCount() {
            return feedNectarCount;
        }

        public int feedPetalCount() {
            return feedPetalCount;
        }

        public int diagnosticEventCount() {
            return diagnosticLog.size();
        }

        public int firstDiagnosticSequence() {
            return diagnosticLog.isEmpty() ? 0 : diagnosticLog.get(0).sequence();
        }

        public boolean hasDiagnosticType(String type) {
            return diagnosticLog.stream().anyMatch(event -> event.type().equals(type));
        }

        public String lastDiagnosticType() {
            return diagnosticLog.isEmpty() ? "" : diagnosticLog.get(diagnosticLog.size() - 1).type();
        }

        public int lastGestureScreenX() {
            DiagnosticEvent event = diagnosticLog.isEmpty()
                    ? null : diagnosticLog.get(diagnosticLog.size() - 1);
            return event instanceof GestureDiagnosticEvent gesture ? gesture.screenX() : -1;
        }

        public int lastGestureScreenY() {
            DiagnosticEvent event = diagnosticLog.isEmpty()
                    ? null : diagnosticLog.get(diagnosticLog.size() - 1);
            return event instanceof GestureDiagnosticEvent gesture ? gesture.screenY() : -1;
        }

        public String lastGestureMapping() {
            DiagnosticEvent event = diagnosticLog.isEmpty()
                    ? null : diagnosticLog.get(diagnosticLog.size() - 1);
            return event instanceof GestureDiagnosticEvent gesture ? gesture.mapping() : "";
        }

        public String lastDispatchDetailOutcome() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail ? detail.outcome() : "";
        }

        public String lastDispatchDetailReason() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail ? detail.reason() : "";
        }

        public String lastDispatchDetailMatch() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail ? detail.match() : "";
        }

        public int lastDispatchDetailAttempt() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail ? detail.attempt() : -1;
        }

        public boolean lastDispatchDetailTransitionPending() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail
                    && detail.transitionPending();
        }

        public int lastDispatchDetailBitmapX() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail
                    ? detail.bitmapX() : -1;
        }

        public int lastDispatchDetailBitmapY() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail
                    ? detail.bitmapY() : -1;
        }

        public String lastDispatchDetailStopReason() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchDetailDiagnosticEvent detail
                    ? detail.stopReason() : "";
        }

        public String lastDispatchSelectionOutcome() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchSelectionDiagnosticEvent selection
                    ? selection.outcome() : "";
        }

        public String lastDispatchSelectionReason() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchSelectionDiagnosticEvent selection
                    ? selection.reason() : "";
        }

        public int lastDispatchSelectionBitmapX() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchSelectionDiagnosticEvent selection
                    ? selection.bitmapX() : -1;
        }

        public int lastDispatchSelectionBitmapY() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof DispatchSelectionDiagnosticEvent selection
                    ? selection.bitmapY() : -1;
        }

        public String lastFeedNectarSelectionOutcome() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof FeedNectarSelectionDiagnosticEvent selection
                    ? selection.outcome() : "";
        }

        public String lastFeedNectarSelectionReason() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof FeedNectarSelectionDiagnosticEvent selection
                    ? selection.reason() : "";
        }

        public int lastFeedNectarSelectionScan() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof FeedNectarSelectionDiagnosticEvent selection
                    ? selection.scan() : -1;
        }

        public boolean lastFeedNectarSelectionNectarCountFound() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof FeedNectarSelectionDiagnosticEvent selection
                    && selection.nectarCountFound();
        }

        public boolean lastFeedNectarSelectionPetalCountFound() {
            DiagnosticEvent event = lastDiagnosticEvent();
            return event instanceof FeedNectarSelectionDiagnosticEvent selection
                    && selection.petalCountFound();
        }

        private DiagnosticEvent lastDiagnosticEvent() {
            return diagnosticLog.isEmpty() ? null : diagnosticLog.get(diagnosticLog.size() - 1);
        }
    }

    private static void queueAndUpload(Context context, Snapshot snapshot) {
        try {
            String json = payload(snapshot).toString();
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            if (payload.length > MAX_PAYLOAD_BYTES) return;
            if (context == null) {
                return;
            }
            SharedPreferences preferences = context.getSharedPreferences(
                    OUTBOX_PREFS, Context.MODE_PRIVATE);
            // This runs off the gameplay thread. Never transmit before durable storage succeeds.
            if (!preferences.edit().putString(OUTBOX_PREFIX + snapshot.batchId(), json).commit()) return;
            uploadPending(preferences, UsageTelemetryClient::uploadPayload);
        } catch (JSONException | RuntimeException ignored) {
            // Telemetry must never affect the automation flow.
        }
    }

    interface BatchSender { boolean send(byte[] payload) throws IOException; }

    static void uploadPending(SharedPreferences preferences, BatchSender sender) {
        Map<String, ?> values = preferences.getAll();
        List<String> keys = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key.startsWith(OUTBOX_PREFIX)) keys.add(key);
        }
        keys.sort(String::compareTo);
        for (String key : keys) {
            Object value = values.get(key);
            if (!(value instanceof String json)) continue;
            try {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_PAYLOAD_BYTES || !sender.send(bytes)) continue;
                if (!preferences.edit().remove(key).commit()) return;
            } catch (IOException ignored) {
                return;
            }
        }
    }

    static int pendingBatchCount(Context context) {
        if (context == null) return 0;
        int count = 0;
        for (String key : context.getSharedPreferences(OUTBOX_PREFS, Context.MODE_PRIVATE)
                .getAll().keySet()) {
            if (key.startsWith(OUTBOX_PREFIX)) count++;
        }
        return count;
    }

    private static boolean uploadPayload(byte[] payload) throws IOException {
        for (int attempt = 0; attempt < MAX_UPLOAD_ATTEMPTS; attempt++) {
            if (post(payload)) return true;
            try {
                Thread.sleep(250L << attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static Map<String, Integer> counts(Snapshot snapshot) {
        return Map.of(
                "planting", snapshot.plantingCount,
                "postcard", snapshot.postcardCount,
                "dispatchFruit", snapshot.dispatchFruitCount,
                "dispatchPot", snapshot.dispatchPotCount,
                "returnReward", snapshot.returnRewardCount,
                "feedNectar", snapshot.feedNectarCount,
                "feedPetals", snapshot.feedPetalCount);
    }

    public static JSONObject payload(Snapshot snapshot) throws JSONException {
        JSONObject counts = new JSONObject(UsageTelemetryClient.counts(snapshot));
        JSONArray diagnosticLog = new JSONArray();
        for (DiagnosticEvent event : snapshot.diagnosticLog) {
            if (event instanceof OcrDiagnosticEvent ocr) {
                diagnosticLog.put(withCaptureGeometry(new JSONObject()
                                .put("sequence", ocr.sequence())
                                .put("type", ocr.type())
                                .put("source", ocr.source())
                                .put("stage", ocr.stage())
                                .put("profile", ocr.profile())
                                .put("outcome", ocr.outcome())
                                .put("sourceWidth", ocr.sourceWidth())
                                .put("sourceHeight", ocr.sourceHeight())
                                .put("analysisWidth", ocr.analysisWidth())
                                .put("analysisHeight", ocr.analysisHeight())
                                .put("tokenCount", ocr.tokenCount())
                                .put("elapsedMillis", ocr.elapsedMillis())
                                .put("error", ocr.error()), ocr.captureGeometry())
                        .put("roiBasis", ocr.transform().roiBasis().name())
                        .put("resolvedBasisLeft", ocr.transform().basisLeft())
                        .put("resolvedBasisTop", ocr.transform().basisTop())
                        .put("resolvedBasisRight", ocr.transform().basisRight())
                        .put("resolvedBasisBottom", ocr.transform().basisBottom())
                        .put("resolvedRoiLeft", ocr.transform().cropLeft())
                        .put("resolvedRoiTop", ocr.transform().cropTop())
                        .put("resolvedRoiRight", ocr.transform().cropLeft()
                                + ocr.transform().cropWidth())
                        .put("resolvedRoiBottom", ocr.transform().cropTop()
                                + ocr.transform().cropHeight())
                        .put("roiFallback", ocr.transform().fallbackToScreenshot())
                        .put("roiFallbackReason", ocr.transform().fallbackReason()));
            } else if (event instanceof GestureDiagnosticEvent gesture) {
                diagnosticLog.put(withCaptureGeometry(new JSONObject()
                        .put("sequence", gesture.sequence())
                        .put("type", gesture.type())
                        .put("source", gesture.source())
                        .put("stage", gesture.stage())
                        .put("outcome", gesture.outcome())
                        .put("attempt", gesture.attempt())
                        .put("bitmapWidth", gesture.captureGeometry().bitmapWidth())
                        .put("bitmapHeight", gesture.captureGeometry().bitmapHeight())
                        .put("bitmapX", gesture.bitmapX())
                        .put("bitmapY", gesture.bitmapY())
                        .put("screenX", gesture.screenX())
                        .put("screenY", gesture.screenY())
                        .put("mapping", gesture.mapping()), gesture.captureGeometry()));
            } else if (event instanceof FeedNectarSelectionDiagnosticEvent selection) {
                diagnosticLog.put(new JSONObject()
                        .put("sequence", selection.sequence())
                        .put("type", selection.type())
                        .put("source", selection.source())
                        .put("stage", selection.stage())
                        .put("outcome", selection.outcome())
                        .put("reason", selection.reason())
                        .put("scan", selection.scan())
                        .put("candidateFrames", selection.candidateFrames())
                        .put("missingFrames", selection.missingFrames())
                        .put("nectarCountFound", selection.nectarCountFound())
                        .put("petalCountFound", selection.petalCountFound()));
            } else if (event instanceof DispatchRecognitionDiagnosticEvent recognition) {
                diagnosticLog.put(new JSONObject()
                        .put("sequence", recognition.sequence()).put("type", recognition.type())
                        .put("bank", recognition.bank()).put("bestTemplate", recognition.bestTemplate())
                        .put("bestScore", Double.isFinite(recognition.bestScore()) ? recognition.bestScore() : JSONObject.NULL)
                        .put("secondTemplate", recognition.secondTemplate())
                        .put("secondScore", Double.isFinite(recognition.secondScore()) ? recognition.secondScore() : JSONObject.NULL)
                        .put("margin", Double.isFinite(recognition.margin()) ? recognition.margin() : JSONObject.NULL)
                        .put("candidateLeft", recognition.left()).put("candidateTop", recognition.top())
                        .put("candidateWidth", recognition.width()).put("candidateHeight", recognition.height())
                        .put("frameId", recognition.frameId()).put("visualClassification", recognition.visual())
                        .put("finalClassification", recognition.classification()).put("metric", recognition.metric())
                        .put("calibration", "DEVICE_CALIBRATION_REQUIRED"));
            } else if (event instanceof DispatchDetailDiagnosticEvent detail) {
                diagnosticLog.put(new JSONObject()
                        .put("sequence", detail.sequence())
                        .put("type", detail.type())
                        .put("source", detail.source())
                        .put("stage", detail.stage())
                        .put("outcome", detail.outcome())
                        .put("reason", detail.reason())
                        .put("match", detail.match())
                        .put("attempt", detail.attempt())
                        .put("transitionPending", detail.transitionPending())
                        .put("eventElapsedMillis", detail.eventElapsedMillis())
                        .put("bitmapWidth", detail.bitmapWidth())
                        .put("bitmapHeight", detail.bitmapHeight())
                        .put("bitmapX", detail.bitmapX())
                        .put("bitmapY", detail.bitmapY())
                        .put("stopReason", detail.stopReason()));
            } else if (event instanceof DispatchSelectionDiagnosticEvent selection) {
                diagnosticLog.put(new JSONObject()
                        .put("sequence", selection.sequence())
                        .put("type", selection.type())
                        .put("source", selection.source())
                        .put("stage", selection.stage())
                        .put("outcome", selection.outcome())
                        .put("reason", selection.reason())
                        .put("attempt", selection.attempt())
                        .put("selectedCount", selection.selectedCount())
                        .put("goVisible", selection.goVisible())
                        .put("layout", selection.layout())
                        .put("bitmapWidth", selection.bitmapWidth())
                        .put("bitmapHeight", selection.bitmapHeight())
                        .put("bitmapX", selection.bitmapX())
                        .put("bitmapY", selection.bitmapY()));
            }
        }
        return new JSONObject()
                .put("schemaVersion", TELEMETRY_SCHEMA_VERSION)
                .put("batchId", snapshot.batchId())
                .put("statisticsSchemaVersion", TELEMETRY_SCHEMA_VERSION)
                .put("sessionId", snapshot.sessionId)
                .put("operation", snapshot.operation.wireName)
                .put("outcome", snapshot.outcome)
                .put("requestedCount", snapshot.requestedCount)
                .put("durationSeconds", Math.max(0, snapshot.durationMillis / 1000L))
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("appVersionName", BuildConfig.VERSION_NAME)
                .put("configVersion", snapshot.configVersion)
                .put("counts", counts)
                .put("diagnosticLog", diagnosticLog);
    }

    private static JSONObject withCaptureGeometry(
            JSONObject json, CaptureGeometry geometry) throws JSONException {
        CaptureGeometry.Bounds target = geometry.targetWindowBoundsOnScreen();
        return json
                .put("captureMode", geometry.mode().name())
                .put("expectedLeft", geometry.expectedSourceBoundsOnScreen().left())
                .put("expectedTop", geometry.expectedSourceBoundsOnScreen().top())
                .put("expectedRight", geometry.expectedSourceBoundsOnScreen().right())
                .put("expectedBottom", geometry.expectedSourceBoundsOnScreen().bottom())
                .put("targetWindowLeft", target == null ? -1 : target.left())
                .put("targetWindowTop", target == null ? -1 : target.top())
                .put("targetWindowRight", target == null ? -1 : target.right())
                .put("targetWindowBottom", target == null ? -1 : target.bottom())
                .put("displayId", geometry.displayId())
                .put("captureSequence", geometry.captureSequence())
                .put("capturedAtUptimeMillis", geometry.capturedAtUptimeMillis())
                .put("scaleX", geometry.scaleX())
                .put("scaleY", geometry.scaleY());
    }

    private static String safeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return sanitized.substring(0, Math.min(64, sanitized.length()));
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
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int responseCode = connection.getResponseCode();
            return acceptedResponse(responseCode);
        } finally {
            connection.disconnect();
        }
    }

    static boolean acceptedResponse(int responseCode) {
        // A bare conflict is not proof that this batch was accepted previously.
        return responseCode >= 200 && responseCode < 300;
    }
}
