package com.pikminx.helper;

import android.graphics.Bitmap;
import android.os.Build;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 同時使用 APK 內建的 ML Kit 文字系統模型，將文字區塊轉成自動化流程可用的 token。
 * 離線模型涵蓋拉丁、中文、天城文、日文與韓文；未涵蓋文字系統不能假裝已讀懂其語意。
 */
final class OcrScanner implements AutoCloseable {
    enum TerminalState { PENDING, SUCCESS, FAILURE, TIMEOUT, CANCELLED }

    static final class GeometryException extends Exception {
        GeometryException(String message) {
            super(message);
        }

        GeometryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    interface FrameCallback {
        void onSuccess(OcrScan.Frame frame);
        void onFailure(Exception error);
    }

    static final class Transaction {
        private final OcrScan.TransactionId id;
        private final AtomicReference<TerminalState> state =
                new AtomicReference<>(TerminalState.PENDING);
        private volatile OcrRuntimeDiagnostics diagnostics;

        Transaction(OcrScan.TransactionId id) {
            if (id == null) {
                throw new IllegalArgumentException("Transaction identity is required");
            }
            this.id = id;
        }

        OcrScan.TransactionId id() { return id; }
        TerminalState state() { return state.get(); }

        void attachDiagnostics(OcrRuntimeDiagnostics diagnostics) {
            if (diagnostics != null) {
                this.diagnostics = diagnostics;
            }
        }

        OcrRuntimeDiagnostics diagnostics() { return diagnostics; }

        synchronized void onTerminal(Runnable listener) {
            if (listener == null) {
                throw new IllegalArgumentException("Terminal listener is required");
            }
            if (state.get() == TerminalState.PENDING) {
                if (terminalListener != null) {
                    throw new IllegalStateException("A terminal listener is already registered");
                }
                terminalListener = listener;
                return;
            }
            runTerminalListener(listener);
        }

        boolean tryFinish(TerminalState terminalState) {
            if (terminalState == null || terminalState == TerminalState.PENDING) {
                throw new IllegalArgumentException("A terminal state is required");
            }
            Runnable listener;
            synchronized (this) {
                if (!state.compareAndSet(TerminalState.PENDING, terminalState)) {
                    return false;
                }
                listener = terminalListener;
                terminalListener = null;
            }
            if (listener != null) {
                runTerminalListener(listener);
            }
            return true;
        }

        private Runnable terminalListener;

        private static void runTerminalListener(Runnable listener) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Resource cleanup must never alter transaction state delivery.
            }
        }
    }

    static final class TransactionRegistry {
        private long nextRequestSequence;
        private Transaction active;

        synchronized Transaction begin(long runGeneration, long captureSequence) {
            if (active != null) {
                throw new IllegalStateException("An OCR transaction is already active");
            }
            active = new Transaction(new OcrScan.TransactionId(
                    runGeneration, captureSequence, ++nextRequestSequence));
            return active;
        }

        synchronized boolean isActive(Transaction transaction) {
            return active == transaction;
        }

        synchronized boolean acceptsCallback(Transaction transaction) {
            return transaction != null
                    && active == transaction
                    && transaction.state() == TerminalState.PENDING;
        }

        synchronized boolean clear(Transaction transaction) {
            if (active != transaction) {
                return false;
            }
            active = null;
            return true;
        }

        synchronized Transaction active() { return active; }
    }

    static final class TaskResourceLease {
        private final AtomicInteger pendingTasks;
        private final AtomicBoolean releaseRequested = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();
        private final Runnable release;

        TaskResourceLease(int taskCount, Runnable release) {
            if (taskCount < 1 || release == null) {
                throw new IllegalArgumentException("Task count and release action are required");
            }
            pendingTasks = new AtomicInteger(taskCount);
            this.release = release;
        }

        boolean taskComplete() {
            int remaining = pendingTasks.decrementAndGet();
            if (remaining < 0) {
                throw new IllegalStateException("Too many task completions");
            }
            releaseIfReady(remaining);
            return remaining == 0;
        }

        void releaseAfterTasks() {
            releaseRequested.set(true);
            releaseIfReady(pendingTasks.get());
        }

        boolean released() { return released.get(); }

        private void releaseIfReady(int remaining) {
            if (remaining == 0
                    && releaseRequested.get()
                    && released.compareAndSet(false, true)) {
                release.run();
            }
        }
    }

    private enum Script {
        LATIN,
        CHINESE,
        DEVANAGARI,
        JAPANESE,
        KOREAN
    }

    private record RecognizerEntry(Script script, TextRecognizer recognizer) {}
    private record ScoredToken(PetalMatcher.Token token, int score) {}

    private static final AtomicInteger OCR_THREAD_SEQUENCE = new AtomicInteger();
    static final int OCR_CALLBACK_QUEUE_CAPACITY = 8;
    static final int OCR_CALLBACK_WORKER_COUNT = 1;
    private static final ExecutorService OCR_EXECUTOR = new ThreadPoolExecutor(
            OCR_CALLBACK_WORKER_COUNT,
            OCR_CALLBACK_WORKER_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(OCR_CALLBACK_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(
                        runnable, "pikminx-ocr-" + OCR_THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final List<RecognizerEntry> recognizers = List.of(
            new RecognizerEntry(Script.LATIN, TextRecognition.getClient(
                    new TextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.CHINESE, TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.DEVANAGARI, TextRecognition.getClient(
                    new DevanagariTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.JAPANESE, TextRecognition.getClient(
                    new JapaneseTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.KOREAN, TextRecognition.getClient(
                    new KoreanTextRecognizerOptions.Builder().build())));

    static List<String> supportedScriptNames() {
        return List.of("Latin", "Chinese", "Devanagari", "Japanese", "Korean");
    }

    static List<String> selectedScriptNames(OcrScan.Profile profile) {
        return profile.scriptMode() == OcrScan.ScriptMode.CHINESE
                ? List.of("Chinese") : supportedScriptNames();
    }

    static boolean canDeliverCompleteFrame(
            int selectedRecognizerCount, int completedRecognizerCount, Exception failure) {
        return selectedRecognizerCount > 0
                && completedRecognizerCount == selectedRecognizerCount
                && failure == null;
    }

    static boolean canProcessCallback(
            Transaction transaction, BooleanSupplier callbackAdmission) {
        if (transaction == null || callbackAdmission == null
                || transaction.state() != TerminalState.PENDING) {
            return false;
        }
        try {
            return callbackAdmission.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 依流程 profile 統一裁切、縮放、辨識及還原來源座標。 */
    void scan(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Transaction transaction,
            Executor callbackExecutor,
            FrameCallback callback) {
        scan(
                bitmap,
                profile,
                captureGeometry,
                transaction,
                () -> transaction != null && transaction.state() == TerminalState.PENDING,
                callbackExecutor,
                callback,
                null);
    }

    /** Scans with a cheap caller-owned guard checked before each result is parsed. */
    void scan(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Transaction transaction,
            BooleanSupplier callbackAdmission,
            Executor callbackExecutor,
            FrameCallback callback) {
        scan(
                bitmap,
                profile,
                captureGeometry,
                transaction,
                callbackAdmission,
                callbackExecutor,
                callback,
                null);
    }

    /** Scans with bounded runtime timeline instrumentation; the instrumentation is optional. */
    void scan(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Transaction transaction,
            BooleanSupplier callbackAdmission,
            Executor callbackExecutor,
            FrameCallback callback,
            OcrRuntimeDiagnostics diagnostics) {
        scan(
                bitmap,
                profile,
                captureGeometry,
                transaction,
                0L,
                callbackAdmission,
                callbackExecutor,
                callback,
                diagnostics);
    }

    /** Scans while retaining the caller's action-admission epoch on the delivered frame. */
    void scan(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Transaction transaction,
            long admissionEpoch,
            BooleanSupplier callbackAdmission,
            Executor callbackExecutor,
            FrameCallback callback,
            OcrRuntimeDiagnostics diagnostics) {
        scan(bitmap, profile, captureGeometry, null, transaction, admissionEpoch,
                callbackAdmission, callbackExecutor, callback, diagnostics);
    }

    void scan(Bitmap bitmap, OcrScan.Profile profile, CaptureGeometry captureGeometry,
            OcrScan.Transform requestedTransform, Transaction transaction, long admissionEpoch,
            BooleanSupplier callbackAdmission, Executor callbackExecutor, FrameCallback callback,
            OcrRuntimeDiagnostics diagnostics) {
        if (diagnostics != null && transaction != null) {
            transaction.attachDiagnostics(diagnostics);
        }
        OcrRuntimeDiagnostics runtimeDiagnostics = diagnostics == null || transaction == null
                ? transaction == null ? null : transaction.diagnostics()
                : diagnostics;
        if (runtimeDiagnostics != null && transaction != null) {
            runtimeDiagnostics.markQueueEnter(transaction.id());
        }
        if (captureGeometry == null
                || transaction == null
                || transaction.id().captureSequence() != captureGeometry.captureSequence()
                || !captureGeometry.matchesBitmap(bitmap.getWidth(), bitmap.getHeight())) {
            finishFailure(
                    transaction,
                    callbackExecutor,
                    callback,
                    new GeometryException("OCR transaction geometry is inconsistent"));
            return;
        }
        OcrScan.Transform transform = requestedTransform == null ? OcrScan.Transform.create(
                profile, bitmap.getWidth(), bitmap.getHeight(), captureGeometry) : requestedTransform;
        if (requestedTransform != null && (!captureGeometry.isActionSafe(transform)
                || transform.cropLeft() < 0 || transform.cropTop() < 0
                || transform.cropWidth() <= 0 || transform.cropHeight() <= 0
                || transform.cropLeft() + transform.cropWidth() > bitmap.getWidth()
                || transform.cropTop() + transform.cropHeight() > bitmap.getHeight())) {
            finishFailure(transaction, callbackExecutor, callback,
                    new GeometryException("OCR ROI does not belong to this capture"));
            return;
        }
        Bitmap analysis;
        try {
            analysis = analysisBitmap(bitmap, transform);
        } catch (RuntimeException error) {
            if (BuildConfig.GEOMETRY_VALIDATION) {
                recordGeometryFailure(profile, transform, captureGeometry, error);
            }
            finishFailure(
                    transaction,
                    callbackExecutor,
                    callback,
                    new GeometryException("Unable to prepare OCR geometry", error));
            return;
        }
        InputImage image;
        try {
            image = InputImage.fromBitmap(analysis, 0);
        } catch (RuntimeException error) {
            analysis.recycle();
            if (BuildConfig.GEOMETRY_VALIDATION) {
                recordGeometryFailure(profile, transform, captureGeometry, error);
            }
            finishFailure(transaction, callbackExecutor, callback, error);
            return;
        }
        List<ScoredToken> recognized = Collections.synchronizedList(new ArrayList<>());
        RecognizerEntry chineseRecognizer = recognizers.stream()
                .filter(entry -> entry.script() == Script.CHINESE)
                .findFirst()
                .orElseThrow();
        List<RecognizerEntry> selectedRecognizers = profile.scriptMode()
                == OcrScan.ScriptMode.CHINESE
                ? List.of(chineseRecognizer)
                : recognizers;
        AtomicInteger completedTasks = new AtomicInteger();
        AtomicReference<Exception> firstFailure = new AtomicReference<>();
        TaskResourceLease resources = new TaskResourceLease(
                selectedRecognizers.size(), analysis::recycle);
        transaction.onTerminal(() -> {
            if (transaction.state() != TerminalState.SUCCESS) {
                // ML Kit Task has no public cancel operation. Hold the analysis bitmap until
                // every late callback has completed, while allowing stale callbacks to return
                // before touching the OCR result.
                resources.releaseAfterTasks();
            }
        });
        long startedAt = android.os.SystemClock.elapsedRealtime();
        if (runtimeDiagnostics != null) {
            runtimeDiagnostics.markWorkerStart(transaction.id());
        }
        for (RecognizerEntry entry : selectedRecognizers) {
            String recognizerType = entry.script().name();
            if (runtimeDiagnostics != null) {
                runtimeDiagnostics.markRecognizerStart(transaction.id(), recognizerType);
            }
            try {
                Executor recognizerCallbackExecutor = runtimeDiagnostics == null
                        ? OCR_EXECUTOR
                        : runtimeDiagnostics.executorForRecognizer(
                                transaction.id(), recognizerType, OCR_EXECUTOR);
                entry.recognizer().process(image).addOnCompleteListener(
                        recognizerCallbackExecutor, task -> {
                    if (runtimeDiagnostics != null) {
                        runtimeDiagnostics.markRecognizerEnd(transaction.id(), recognizerType);
                    }
                    if (!canProcessCallback(transaction, callbackAdmission)) {
                        if (runtimeDiagnostics != null) {
                            runtimeDiagnostics.markStaleCallback();
                        }
                        completedTasks.incrementAndGet();
                        if (resources.taskComplete()) {
                            if (runtimeDiagnostics != null) {
                                runtimeDiagnostics.markAllRecognizersComplete(transaction.id());
                            }
                            resources.releaseAfterTasks();
                        }
                        return;
                    }
                    Exception taskFailure = null;
                    try {
                        if (task.isSuccessful()) {
                            if (canProcessCallback(transaction, callbackAdmission)) {
                                if (runtimeDiagnostics != null) {
                                    runtimeDiagnostics.markTokenParsingStart(
                                            transaction.id(), recognizerType);
                                }
                                try {
                                    recognized.addAll(tokens(task.getResult(), entry.script()));
                                } finally {
                                    if (runtimeDiagnostics != null) {
                                        runtimeDiagnostics.markTokenParsingEnd(
                                                transaction.id(), recognizerType);
                                    }
                                }
                            } else if (runtimeDiagnostics != null) {
                                runtimeDiagnostics.markStaleCallback();
                            }
                        } else {
                            taskFailure = task.getException();
                            if (taskFailure == null) {
                                taskFailure = new IllegalStateException("ML Kit OCR task failed");
                            }
                        }
                    } catch (RuntimeException error) {
                        taskFailure = error;
                    }
                    if (taskFailure != null && firstFailure.compareAndSet(null, taskFailure)) {
                        if (BuildConfig.GEOMETRY_VALIDATION) {
                            recordGeometryFailure(
                                    profile, transform, captureGeometry, taskFailure);
                        }
                        finishFailure(transaction, callbackExecutor, callback, taskFailure);
                        resources.releaseAfterTasks();
                    }
                    int completedCount = completedTasks.incrementAndGet();
                    if (!resources.taskComplete()) {
                        return;
                    }
                    if (runtimeDiagnostics != null) {
                        runtimeDiagnostics.markAllRecognizersComplete(transaction.id());
                    }
                    // Keep the scanner-owned bitmap alive until the success callback has
                    // finished consuming Frame.pixelAtSource().  The callback may run
                    // detectors against the frame after all recognizer tasks complete.
                    // deliverSuccess() owns the final release for the successful path;
                    // failure/stale paths release here or through the terminal listener.
                    Exception failure = firstFailure.get();
                    boolean callbackStillAdmitted = canProcessCallback(
                            transaction, callbackAdmission);
                    if (!canDeliverCompleteFrame(
                                    selectedRecognizers.size(), completedCount, failure)
                            || !callbackStillAdmitted) {
                        if (!callbackStillAdmitted && failure == null && runtimeDiagnostics != null) {
                            runtimeDiagnostics.markStaleCallback();
                        }
                        resources.releaseAfterTasks();
                        return;
                    }
                    try {
                        if (!canProcessCallback(transaction, callbackAdmission)) {
                            if (runtimeDiagnostics != null) {
                                runtimeDiagnostics.markStaleCallback();
                            }
                            resources.releaseAfterTasks();
                            return;
                        }
                        List<PetalMatcher.Token> merged =
                                transform.toSourceTokens(merge(recognized));
                        OcrScan.Frame frame = new OcrScan.Frame(
                                transaction.id(),
                                admissionEpoch,
                                profile,
                                transform,
                                merged,
                                android.os.SystemClock.elapsedRealtime() - startedAt,
                                analysis::getPixel,
                                captureGeometry,
                                true);
                        if (!transaction.tryFinish(TerminalState.SUCCESS)) {
                            resources.releaseAfterTasks();
                            return;
                        }
                        if (BuildConfig.GEOMETRY_VALIDATION) {
                            recordGeometrySuccess(frame);
                        }
                        deliverSuccess(
                                callbackExecutor, callback, frame, resources, transaction);
                    } catch (RuntimeException error) {
                        finishFailure(transaction, callbackExecutor, callback, error);
                        resources.releaseAfterTasks();
                    }
                });
            } catch (RuntimeException error) {
                if (runtimeDiagnostics != null) {
                    runtimeDiagnostics.markRecognizerEnd(transaction.id(), recognizerType);
                }
                if (firstFailure.compareAndSet(null, error)) {
                    finishFailure(transaction, callbackExecutor, callback, error);
                    resources.releaseAfterTasks();
                }
                completedTasks.incrementAndGet();
                if (resources.taskComplete()) {
                    if (runtimeDiagnostics != null) {
                        runtimeDiagnostics.markAllRecognizersComplete(transaction.id());
                    }
                    resources.releaseAfterTasks();
                }
            }
        }
    }

    private void finishFailure(
            Transaction transaction,
            Executor callbackExecutor,
            FrameCallback callback,
            Exception error) {
        if (transaction == null || !transaction.tryFinish(TerminalState.FAILURE)) {
            return;
        }
        OcrRuntimeDiagnostics diagnostics = transaction.diagnostics();
        if (diagnostics != null) {
            diagnostics.markMainExecutorEnqueue(transaction.id());
        }
        try {
            callbackExecutor.execute(() -> {
                if (diagnostics != null) {
                    diagnostics.markMainExecutorStart(transaction.id());
                }
                callback.onFailure(error);
            });
        } catch (RuntimeException ignored) {
            // The service watchdog owns recovery if callback delivery is unavailable.
        }
    }

    private void deliverSuccess(
            Executor callbackExecutor,
            FrameCallback callback,
            OcrScan.Frame frame,
            TaskResourceLease resources,
            Transaction transaction) {
        OcrRuntimeDiagnostics diagnostics = transaction == null
                ? null : transaction.diagnostics();
        if (diagnostics != null) {
            diagnostics.markMainExecutorEnqueue(transaction.id());
        }
        try {
            callbackExecutor.execute(() -> {
                if (diagnostics != null) {
                    diagnostics.markMainExecutorStart(transaction.id());
                }
                try {
                    callback.onSuccess(frame);
                } finally {
                    resources.releaseAfterTasks();
                }
            });
        } catch (RuntimeException ignored) {
            resources.releaseAfterTasks();
        }
    }

    private void recordGeometrySuccess(OcrScan.Frame frame) {
        if (!BuildConfig.GEOMETRY_VALIDATION) {
            return;
        }
        try {
            GeometryValidation.log(GeometryValidation.success(Build.VERSION.SDK_INT, frame));
        } catch (RuntimeException ignored) {
            // Developer diagnostics must never alter OCR behavior.
        }
    }

    private void recordGeometryFailure(
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry captureGeometry,
            Exception error) {
        if (!BuildConfig.GEOMETRY_VALIDATION) {
            return;
        }
        try {
            GeometryValidation.log(GeometryValidation.failure(
                    Build.VERSION.SDK_INT, profile, transform, captureGeometry, error));
        } catch (RuntimeException ignored) {
            // Developer diagnostics must never alter OCR behavior.
        }
    }

    private Bitmap analysisBitmap(Bitmap source, OcrScan.Transform transform) {
        if (transform.usesSourceBitmap()) {
            Bitmap.Config config = source.getConfig() == null
                    ? Bitmap.Config.ARGB_8888 : source.getConfig();
            Bitmap copy = source.copy(config, false);
            if (copy == null) {
                throw new IllegalStateException("Unable to create scanner-owned bitmap");
            }
            return copy;
        }
        Bitmap crop = null;
        try {
            crop = Bitmap.createBitmap(
                    source,
                    transform.cropLeft(),
                    transform.cropTop(),
                    transform.cropWidth(),
                    transform.cropHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(
                    crop, transform.analysisWidth(), transform.analysisHeight(), true);
            if (scaled != crop) {
                crop.recycle();
            }
            return scaled;
        } catch (RuntimeException error) {
            if (crop != null && crop != source && !crop.isRecycled()) {
                crop.recycle();
            }
            throw error;
        }
    }

    /** 將每個模型的文字與框座標保留為可依文字系統選優的 token。 */
    private List<ScoredToken> tokens(Text text, Script script) {
        List<ScoredToken> result = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                android.graphics.Rect box = line.getBoundingBox();
                if (box != null && !line.getText().isBlank()) {
                    PetalMatcher.Token token = new PetalMatcher.Token(
                            line.getText(), box.left, box.top, box.right, box.bottom);
                    result.add(new ScoredToken(token, score(script, line.getText())));
                }
            }
        }
        return result;
    }

    /** 合併同一行的多模型結果；日文假名、韓文與天城文優先使用對應模型。 */
    private List<PetalMatcher.Token> merge(List<ScoredToken> recognized) {
        List<ScoredToken> ordered = new ArrayList<>(recognized);
        ordered.sort(Comparator
                .comparingInt((ScoredToken value) -> value.token().top())
                .thenComparingInt(value -> value.token().left())
                .thenComparing(Comparator.comparingInt(ScoredToken::score).reversed()));
        List<ScoredToken> merged = new ArrayList<>();
        for (ScoredToken candidate : ordered) {
            int duplicateIndex = duplicateIndex(merged, candidate.token());
            if (duplicateIndex < 0) {
                merged.add(candidate);
            } else if (candidate.score() > merged.get(duplicateIndex).score()) {
                merged.set(duplicateIndex, candidate);
            }
        }
        merged.sort(Comparator
                .comparingInt((ScoredToken value) -> value.token().top())
                .thenComparingInt(value -> value.token().left()));
        List<PetalMatcher.Token> result = new ArrayList<>(merged.size());
        for (ScoredToken value : merged) {
            result.add(value.token());
        }
        return result;
    }

    private int duplicateIndex(List<ScoredToken> values, PetalMatcher.Token candidate) {
        for (int index = 0; index < values.size(); index++) {
            PetalMatcher.Token existing = values.get(index).token();
            int overlapLeft = Math.max(existing.left(), candidate.left());
            int overlapTop = Math.max(existing.top(), candidate.top());
            int overlapRight = Math.min(existing.right(), candidate.right());
            int overlapBottom = Math.min(existing.bottom(), candidate.bottom());
            int overlap = Math.max(0, overlapRight - overlapLeft)
                    * Math.max(0, overlapBottom - overlapTop);
            int existingArea = Math.max(1, existing.right() - existing.left())
                    * Math.max(1, existing.bottom() - existing.top());
            int candidateArea = Math.max(1, candidate.right() - candidate.left())
                    * Math.max(1, candidate.bottom() - candidate.top());
            int union = existingArea + candidateArea - overlap;
            if (overlap * 100 >= union * 72) {
                return index;
            }
        }
        return -1;
    }

    private int score(Script script, String text) {
        int letters = 0;
        boolean han = false;
        boolean kana = false;
        boolean hangul = false;
        boolean devanagari = false;
        boolean latin = false;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                letters++;
            }
            Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(codePoint);
            han |= unicodeScript == Character.UnicodeScript.HAN;
            kana |= unicodeScript == Character.UnicodeScript.HIRAGANA
                    || unicodeScript == Character.UnicodeScript.KATAKANA;
            hangul |= unicodeScript == Character.UnicodeScript.HANGUL;
            devanagari |= unicodeScript == Character.UnicodeScript.DEVANAGARI;
            latin |= unicodeScript == Character.UnicodeScript.LATIN;
        }
        int scriptMatch = switch (script) {
            case LATIN -> latin ? 200 : 0;
            case CHINESE -> han && !kana ? 180 : 0;
            case DEVANAGARI -> devanagari ? 220 : 0;
            case JAPANESE -> kana ? 220 : han ? 140 : 0;
            case KOREAN -> hangul ? 220 : 0;
        };
        return scriptMatch + Math.min(letters, 40);
    }

    /** 釋放 ML Kit recognizer，避免無障礙服務重啟時累積資源。 */
    @Override
    public void close() {
        for (RecognizerEntry entry : recognizers) {
            entry.recognizer().close();
        }
    }
}
