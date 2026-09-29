package com.pikminx.helper;

import android.graphics.Bitmap;
import android.os.SystemClock;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Owns one OCR transaction after {@link CaptureCoordinator} has produced a bitmap and immutable
 * capture geometry.  It deliberately emits evidence only; workflow decisions and game actions
 * remain with its caller.
 */
final class OcrRuntime implements AutoCloseable {
    interface Scheduler {
        Object schedule(long delayMillis, Runnable action);
        void cancel(Object handle);
    }

    interface Engine {
        void scan(
                Bitmap bitmap,
                OcrScan.Profile profile,
                CaptureGeometry captureGeometry,
                OcrScan.Transform requestedTransform,
                OcrScanner.Transaction transaction,
                long admissionEpoch,
                BooleanSupplier callbackAdmission,
                Executor callbackExecutor,
                OcrScanner.FrameCallback callback,
                OcrRuntimeDiagnostics diagnostics);
    }

    interface Callback {
        void onSuccess(Transaction transaction, OcrScan.Frame frame);
        void onFailure(Transaction transaction, Failure failure);

        /** Invoked once when the runtime has detached its in-flight transaction. */
        default void onTerminal(Transaction transaction, OcrScanner.TerminalState state) { }
    }

    static final class Failure {
        private final Exception error;
        private final boolean engineFailure;

        Failure(Exception error, boolean engineFailure) {
            this.error = error == null
                    ? new IllegalStateException("OCR failed without an error") : error;
            this.engineFailure = engineFailure;
        }

        Exception error() { return error; }
        boolean engineFailure() { return engineFailure; }
    }

    /** Immutable request-time OCR context plus caller-owned workflow callbacks. */
    static final class Request {
        private final Bitmap bitmap;
        private final ScreenCoordinateTransform.ScreenshotRect region;
        private final OcrScan.Profile profile;
        private final CaptureGeometry captureGeometry;
        private final long runGeneration;
        private final long admissionEpoch;
        private final Runnable terminalCleanup;
        private final BooleanSupplier deliveryAllowed;
        private final Callback callback;

        Request(
                Bitmap bitmap,
                OcrScan.Profile profile,
                CaptureGeometry captureGeometry,
                long runGeneration,
                long admissionEpoch,
                boolean recycleSourceAtTerminal,
                BooleanSupplier deliveryAllowed,
                Callback callback) {
            this(
                    bitmap,
                    profile,
                    captureGeometry,
                    runGeneration,
                    admissionEpoch,
                    () -> {
                        if (recycleSourceAtTerminal
                                && bitmap != null
                                && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    },
                    deliveryAllowed,
                    callback);
        }

        Request(
                Bitmap bitmap,
                OcrScan.Profile profile,
                CaptureGeometry captureGeometry,
                long runGeneration,
                long admissionEpoch,
                Runnable terminalCleanup,
                BooleanSupplier deliveryAllowed,
                Callback callback) {
            this(bitmap, profile, captureGeometry, runGeneration, admissionEpoch,
                    terminalCleanup, deliveryAllowed, callback, null);
        }

        private Request(Bitmap bitmap, OcrScan.Profile profile, CaptureGeometry captureGeometry,
                long runGeneration, long admissionEpoch, Runnable terminalCleanup,
                BooleanSupplier deliveryAllowed, Callback callback,
                ScreenCoordinateTransform.ScreenshotRect region) {
            if (profile == null || runGeneration < 1L || admissionEpoch < 0L
                    || terminalCleanup == null || deliveryAllowed == null || callback == null) {
                throw new IllegalArgumentException("OCR request identity and callbacks are required");
            }
            this.bitmap = bitmap;
            this.region = region;
            this.profile = profile;
            this.captureGeometry = captureGeometry;
            this.runGeneration = runGeneration;
            this.admissionEpoch = admissionEpoch;
            this.terminalCleanup = terminalCleanup;
            this.deliveryAllowed = deliveryAllowed;
            this.callback = callback;
        }

        Request withRegion(ScreenCoordinateTransform.ScreenshotRect region) {
            return new Request(bitmap, profile, captureGeometry, runGeneration, admissionEpoch,
                    terminalCleanup, deliveryAllowed, callback, region);
        }
    }

    /** Gives post-OCR analyses independent, exactly-once source-resource holds. */
    static final class Transaction {
        private final OcrScanner.Transaction scannerTransaction;
        private final OcrScan.Profile profile;
        private final CaptureGeometry captureGeometry;
        private final long admissionEpoch;
        private final BooleanSupplier deliveryAllowed;
        private final Callback callback;
        private final DeferredCleanup sourceCleanup;
        private OcrScanner.TerminalState runtimeState = OcrScanner.TerminalState.PENDING;
        private Object watchdogHandle;
        private boolean postProcessingDeferred;
        private boolean postProcessingFinished;
        private String postProcessingOutcome;

        Transaction(
                OcrScanner.Transaction scannerTransaction,
                OcrScan.Profile profile,
                CaptureGeometry captureGeometry,
                long admissionEpoch,
                BooleanSupplier deliveryAllowed,
                Callback callback,
                DeferredCleanup sourceCleanup) {
            this.scannerTransaction = scannerTransaction;
            this.profile = profile;
            this.captureGeometry = captureGeometry;
            this.admissionEpoch = admissionEpoch;
            this.deliveryAllowed = deliveryAllowed;
            this.callback = callback;
            this.sourceCleanup = sourceCleanup;
        }

        OcrScan.TransactionId id() { return scannerTransaction.id(); }
        OcrScan.Profile profile() { return profile; }
        CaptureGeometry captureGeometry() { return captureGeometry; }
        long admissionEpoch() { return admissionEpoch; }
        OcrScanner.Transaction scannerTransaction() { return scannerTransaction; }

        synchronized boolean tryFinishRuntime(OcrScanner.TerminalState state) {
            if (state == null || state == OcrScanner.TerminalState.PENDING
                    || runtimeState != OcrScanner.TerminalState.PENDING) {
                return false;
            }
            runtimeState = state;
            return true;
        }

        synchronized boolean isRuntimePending() {
            return runtimeState == OcrScanner.TerminalState.PENDING;
        }

        synchronized boolean deferPostProcessing() {
            if (postProcessingFinished) {
                return false;
            }
            postProcessingDeferred = true;
            return true;
        }

        synchronized boolean cancelPostProcessingDeferral() {
            if (postProcessingFinished) {
                return false;
            }
            postProcessingDeferred = false;
            return true;
        }

        synchronized boolean tryFinishPostProcessing() {
            if (postProcessingFinished || postProcessingDeferred) {
                return false;
            }
            postProcessingFinished = true;
            return true;
        }

        synchronized void setPostProcessingOutcome(String outcome) {
            if (!postProcessingFinished && outcome != null && !outcome.isEmpty()) {
                postProcessingOutcome = outcome;
            }
        }

        synchronized String resolvePostProcessingOutcome(String fallback) {
            return postProcessingOutcome == null ? fallback : postProcessingOutcome;
        }

        boolean retainSourceForAnalysis() { return sourceCleanup.tryRetain(); }
        void releaseSourceForAnalysis() { sourceCleanup.release(); }
        void requestSourceCleanup() { sourceCleanup.requestCleanup(); }
    }

    private final Engine engine;
    private OcrScanner scanner;
    private final Scheduler scheduler;
    private final Executor callbackExecutor;
    private final long callbackTimeoutMillis;
    private final LongSupplier uptimeMillis;
    private final OcrRuntimeDiagnostics diagnostics;
    private final Consumer<OcrRuntimeDiagnostics.Completion> completionListener;
    private final OcrScanner.TransactionRegistry transactions =
            new OcrScanner.TransactionRegistry();
    private Transaction active;

    OcrRuntime(
            OcrScanner scanner,
            Scheduler scheduler,
            Executor callbackExecutor,
            long callbackTimeoutMillis,
            Consumer<OcrRuntimeDiagnostics.Completion> completionListener) {
        this(
                (bitmap, profile, geometry, requestedTransform, transaction, admissionEpoch, callbackAdmission,
                        executor, callback, diagnostics) -> scanner.scan(
                                bitmap,
                                profile,
                                geometry,
                                requestedTransform,
                                transaction,
                                admissionEpoch,
                                callbackAdmission,
                                executor,
                                callback,
                                diagnostics),
                scheduler,
                callbackExecutor,
                callbackTimeoutMillis,
                SystemClock::uptimeMillis,
                new OcrRuntimeDiagnostics(),
                completionListener);
        this.scanner = scanner;
    }

    OcrRuntime(
            Engine engine,
            Scheduler scheduler,
            Executor callbackExecutor,
            long callbackTimeoutMillis,
            LongSupplier uptimeMillis,
            OcrRuntimeDiagnostics diagnostics,
            Consumer<OcrRuntimeDiagnostics.Completion> completionListener) {
        if (engine == null || scheduler == null || callbackExecutor == null
                || callbackTimeoutMillis < 1L || uptimeMillis == null || diagnostics == null) {
            throw new IllegalArgumentException("OCR runtime dependencies are required");
        }
        this.engine = engine;
        this.scanner = null;
        this.scheduler = scheduler;
        this.callbackExecutor = callbackExecutor;
        this.callbackTimeoutMillis = callbackTimeoutMillis;
        this.uptimeMillis = uptimeMillis;
        this.diagnostics = diagnostics;
        this.completionListener = completionListener == null ? completion -> { } : completionListener;
    }

    OcrRuntimeDiagnostics diagnostics() { return diagnostics; }

    synchronized boolean hasActiveTransaction() { return active != null; }

    /** Starts one bounded OCR transaction, or reports a failure without disturbing an active one. */
    Transaction request(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("OCR request is required");
        }
        DeferredCleanup sourceCleanup = new DeferredCleanup(request.terminalCleanup);
        if (request.captureGeometry == null) {
            sourceCleanup.requestCleanup();
            deliverRejected(request, new Failure(
                    new IllegalArgumentException("Capture geometry is required"), false));
            return null;
        }

        OcrScan.Transform requestedTransform;
        try {
            if (request.profile == OcrScan.Profile.TARGETED_CHINESE && request.region == null)
                throw new IllegalArgumentException("Targeted OCR requires an explicit current-frame ROI");
            requestedTransform = request.region == null ? null
                    : OcrScan.Transform.forRegion(request.profile, request.captureGeometry, request.region);
        } catch (RuntimeException invalidRegion) {
            sourceCleanup.requestCleanup();
            deliverRejected(request, new Failure(invalidRegion, false));
            return null;
        }
        OcrScanner.Transaction scannerTransaction = null;
        Transaction created = null;
        try {
            synchronized (this) {
                scannerTransaction = transactions.begin(
                        request.runGeneration, request.captureGeometry.captureSequence());
                diagnostics.begin(
                        scannerTransaction.id(),
                        request.profile.name(),
                        request.captureGeometry.capturedAtUptimeMillis(),
                        uptimeMillis.getAsLong());
                created = new Transaction(
                        scannerTransaction,
                        request.profile,
                        request.captureGeometry,
                        request.admissionEpoch,
                        request.deliveryAllowed,
                        request.callback,
                        sourceCleanup);
                active = created;
            }
            Transaction expected = created;
            created.watchdogHandle = scheduler.schedule(
                    callbackTimeoutMillis, () -> timedOut(expected));
            engine.scan(
                    request.bitmap,
                    request.profile,
                    request.captureGeometry,
                    requestedTransform,
                    scannerTransaction,
                    request.admissionEpoch,
                    () -> acceptsCallback(expected),
                    callbackExecutor,
                    new OcrScanner.FrameCallback() {
                        @Override
                        public void onSuccess(OcrScan.Frame frame) {
                            completeSuccess(expected, frame);
                        }

                        @Override
                        public void onFailure(Exception error) {
                            completeFailure(
                                    expected,
                                    new Failure(
                                            error,
                                            !(error instanceof OcrScanner.GeometryException)));
                        }
                    },
                    diagnostics);
            return created;
        } catch (RuntimeException error) {
            if (created != null && isActive(created)) {
                completeFailure(created, new Failure(error, true));
                return null;
            }
            if (scannerTransaction != null
                    && scannerTransaction.state() == OcrScanner.TerminalState.PENDING) {
                scannerTransaction.tryFinish(OcrScanner.TerminalState.FAILURE);
                transactions.clear(scannerTransaction);
            }
            sourceCleanup.requestCleanup();
            deliverRejected(request, new Failure(error, true));
            return null;
        }
    }

    /** Cancels the active transaction without routing a workflow failure. */
    void cancelActive() {
        Transaction expected;
        synchronized (this) {
            expected = active;
        }
        if (expected == null) {
            return;
        }
        if (!expected.tryFinishRuntime(OcrScanner.TerminalState.CANCELLED)) {
            return;
        }
        expected.scannerTransaction.tryFinish(OcrScanner.TerminalState.CANCELLED);
        Transaction cancelled = clearActive(expected);
        if (cancelled == null) {
            return;
        }
        notifyTerminal(cancelled, OcrScanner.TerminalState.CANCELLED);
        finishPostProcessing(cancelled, "CANCELLED");
        // A callback already queued by ML Kit may still read the scanner-owned pixel source.
        scheduler.schedule(callbackTimeoutMillis, cancelled::requestSourceCleanup);
    }

    void finishDeferredPostProcessing(Transaction transaction, String outcome) {
        if (transaction == null) {
            return;
        }
        transaction.cancelPostProcessingDeferral();
        finishPostProcessing(transaction, outcome);
    }

    private boolean acceptsCallback(Transaction expected) {
        return expected != null
                && expected.isRuntimePending()
                && transactions.acceptsCallback(expected.scannerTransaction)
                && isActive(expected);
    }

    private boolean isActive(Transaction expected) {
        synchronized (this) {
            return active == expected;
        }
    }

    private Transaction clearActive(Transaction expected) {
        synchronized (this) {
            if (expected == null
                    || active != expected
                    || !transactions.clear(expected.scannerTransaction)) {
                return null;
            }
            active = null;
        }
        if (expected.watchdogHandle != null) {
            scheduler.cancel(expected.watchdogHandle);
            expected.watchdogHandle = null;
        }
        return expected;
    }

    private void completeSuccess(Transaction expected, OcrScan.Frame frame) {
        if (expected == null
                || !expected.tryFinishRuntime(OcrScanner.TerminalState.SUCCESS)) {
            return;
        }
        expected.scannerTransaction.tryFinish(OcrScanner.TerminalState.SUCCESS);
        Transaction completed = clearActive(expected);
        if (completed == null) {
            return;
        }
        diagnostics.markOcrCompletion(completed.id());
        notifyTerminal(completed, OcrScanner.TerminalState.SUCCESS);
        String outcome = "SUCCESS";
        try {
            if (!deliveryAllowed(completed)) {
                outcome = "STALE_GENERATION";
                return;
            }
            if (frame == null) {
                outcome = "NULL_FRAME";
                return;
            }
            completed.callback.onSuccess(completed, frame);
        } catch (RuntimeException error) {
            outcome = "SUCCESS_CALLBACK_FAILURE";
            if (deliveryAllowed(completed)) {
                completed.callback.onFailure(completed, new Failure(error, false));
            }
        } finally {
            finishPostProcessing(completed, outcome);
        }
    }

    private void completeFailure(Transaction expected, Failure failure) {
        if (expected == null
                || !expected.tryFinishRuntime(OcrScanner.TerminalState.FAILURE)) {
            return;
        }
        expected.scannerTransaction.tryFinish(OcrScanner.TerminalState.FAILURE);
        deliverFailure(expected, failure, OcrScanner.TerminalState.FAILURE);
    }

    private void deliverFailure(
            Transaction expected, Failure failure, OcrScanner.TerminalState state) {
        Transaction completed = clearActive(expected);
        if (completed == null) {
            return;
        }
        notifyTerminal(completed, state);
        String outcome = state == OcrScanner.TerminalState.TIMEOUT ? "TIMEOUT" : "FAILURE";
        try {
            if (!deliveryAllowed(completed)) {
                outcome = "STALE_GENERATION";
                return;
            }
            completed.callback.onFailure(completed, failure);
        } finally {
            finishPostProcessing(completed, outcome);
        }
    }

    private void timedOut(Transaction expected) {
        if (!isActive(expected)
                || !expected.tryFinishRuntime(OcrScanner.TerminalState.TIMEOUT)) {
            return;
        }
        expected.scannerTransaction.tryFinish(OcrScanner.TerminalState.TIMEOUT);
        diagnostics.markTimeout(expected.id());
        deliverFailure(
                expected,
                new Failure(new IllegalStateException("OCR callback timed out"), true),
                OcrScanner.TerminalState.TIMEOUT);
    }

    private void finishPostProcessing(Transaction transaction, String outcome) {
        if (transaction == null || !transaction.tryFinishPostProcessing()) {
            return;
        }
        OcrRuntimeDiagnostics.Completion completion = diagnostics.finish(
                transaction.id(), transaction.resolvePostProcessingOutcome(outcome),
                uptimeMillis.getAsLong());
        try {
            completionListener.accept(completion);
        } finally {
            transaction.requestSourceCleanup();
        }
    }

    private void deliverRejected(Request request, Failure failure) {
        if (request == null || !deliveryAllowed(request.deliveryAllowed)) {
            return;
        }
        request.callback.onFailure(null, failure);
    }

    private boolean deliveryAllowed(Transaction transaction) {
        return transaction != null && deliveryAllowed(transaction.deliveryAllowed);
    }

    private static boolean deliveryAllowed(BooleanSupplier allowed) {
        try {
            return allowed != null && allowed.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void notifyTerminal(Transaction transaction, OcrScanner.TerminalState state) {
        try {
            transaction.callback.onTerminal(transaction, state);
        } catch (RuntimeException ignored) {
            // Terminal cleanup must not be interrupted by workflow bookkeeping.
        }
    }

    @Override
    public void close() {
        cancelActive();
        if (scanner != null) {
            scanner.close();
        }
    }
}
