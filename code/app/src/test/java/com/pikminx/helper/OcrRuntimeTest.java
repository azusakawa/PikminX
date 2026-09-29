package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.junit.Test;

/** Focused transaction-lifecycle coverage for the CaptureCoordinator-to-OcrRuntime boundary. */
public final class OcrRuntimeTest {
    private static final String GAME = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds BOUNDS =
            new CaptureGeometry.Bounds(0, 0, 100, 100);

    @Test
    public void regionReachesTheExistingEngineWithTheOriginalCaptureIdentity() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);
        OcrRuntime.Transaction transaction = runtime.request(request(
                7L, 11L, 19L, cleanup::incrementAndGet, observer).withRegion(
                        new ScreenCoordinateTransform.ScreenshotRect(20, 30, 80, 50)));
        assertNotNull(transaction);
        assertNotNull(engine.requestedTransform);
        assertEquals(11L, engine.requestedTransform.captureSequence());
        assertEquals(20, engine.requestedTransform.cropLeft());
        assertEquals(30, engine.requestedTransform.cropTop());
        assertTrue(engine.call(0).geometry.isActionSafe(engine.requestedTransform));
        assertEquals(0, cleanup.get());
        OcrScan.Frame cropped = new OcrScan.Frame(transaction.id(), 19L, OcrScan.Profile.FULL_CHINESE,
                engine.requestedTransform, List.of(new PetalMatcher.Token("探險", 20, 30, 80, 50)),
                1L, null, engine.call(0).geometry, true);
        engine.call(0).succeed(cropped);
        engine.call(0).lateSuccess(cropped);
        assertEquals(1, observer.successes);
        assertTrue(observer.frame.canDriveAction(transaction.id()));
        assertSame(engine.requestedTransform, observer.frame.transform());
        assertEquals(1, cleanup.get());
        assertFalse(runtime.hasActiveTransaction());
    }

    @Test
    public void invalidRegionNeverStartsAnEngineAndCleansUpExactlyOnce() {
        FakeEngine engine = new FakeEngine();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, new FakeScheduler());
        assertNull(runtime.request(request(7L, 11L, 19L, cleanup::incrementAndGet, observer)
                .withRegion(new ScreenCoordinateTransform.ScreenshotRect(90, 20, 110, 50))));
        assertEquals(0, engine.calls.size());
        assertEquals(1, cleanup.get());
        assertEquals(1, observer.failures);
        assertFalse(observer.failure.engineFailure());
        assertFalse(runtime.hasActiveTransaction());
    }

    @Test
    public void successfulTransactionPreservesCaptureIdentityAndProfile() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        OcrRuntime.Transaction transaction = runtime.request(request(
                7L, 11L, 19L, cleanup::incrementAndGet, observer));
        assertNotNull(transaction);
        OcrScan.Frame frame = frame(engine.call(0));
        engine.call(0).succeed(frame);
        engine.call(0).lateSuccess(frame);

        assertEquals(1, observer.successes);
        assertEquals(0, observer.failures);
        assertSame(transaction, observer.transaction);
        assertEquals(7L, observer.frame.transactionId().runGeneration());
        assertEquals(11L, observer.frame.transactionId().captureSequence());
        assertEquals(19L, observer.frame.admissionEpoch());
        assertEquals(OcrScan.Profile.FULL_CHINESE, observer.frame.profile());
        assertEquals(engine.call(0).geometry, observer.frame.captureGeometry());
        assertEquals(1, cleanup.get());
        assertFalse(runtime.hasActiveTransaction());
    }

    @Test
    public void synchronousSetupFailureReleasesSourceOnceAndLeavesNoTransaction() {
        FakeEngine engine = new FakeEngine();
        engine.throwOnScan = true;
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        assertNull(runtime.request(request(3L, 4L, 5L, cleanup::incrementAndGet, observer)));

        assertEquals(0, observer.successes);
        assertEquals(1, observer.failures);
        assertTrue(observer.failure.engineFailure());
        assertEquals(1, cleanup.get());
        assertFalse(runtime.hasActiveTransaction());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void timeoutRejectsLateAndDuplicateCallbacksAndCleansOnce() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        runtime.request(request(2L, 3L, 17L, cleanup::incrementAndGet, observer));
        FakeEngine.Call call = engine.call(0);
        // OcrScanner records success before its main-executor callback is delivered.
        call.completeRecognition();
        scheduler.runNext();

        assertEquals(0, observer.successes);
        assertEquals(1, observer.failures);
        assertEquals(1, cleanup.get());
        assertFalse(runtime.hasActiveTransaction());

        call.lateSuccess(frame(call));
        call.lateSuccess(frame(call));
        assertEquals(0, observer.successes);
        assertEquals(1, observer.failures);
        assertEquals(1, cleanup.get());
    }

    @Test
    public void recognizerFailureDeliversEngineFailureAndReleasesSourceOnce() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        runtime.request(request(4L, 5L, 6L, cleanup::incrementAndGet, observer));
        IllegalStateException error = new IllegalStateException("recognizer failure");
        engine.call(0).fail(error);

        assertEquals(0, observer.successes);
        assertEquals(1, observer.failures);
        assertSame(error, observer.failure.error());
        assertTrue(observer.failure.engineFailure());
        assertEquals(1, cleanup.get());
        assertFalse(runtime.hasActiveTransaction());
    }

    @Test
    public void cancellationAllowsNewGenerationAndCannotResurrectOldResult() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger oldCleanup = new AtomicInteger();
        AtomicInteger newCleanup = new AtomicInteger();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        runtime.request(request(1L, 1L, 4L, oldCleanup::incrementAndGet, observer));
        FakeEngine.Call oldCall = engine.call(0);
        runtime.cancelActive();

        runtime.request(request(2L, 2L, 5L, newCleanup::incrementAndGet, observer));
        FakeEngine.Call newCall = engine.call(1);
        oldCall.lateSuccess(frame(oldCall));
        newCall.succeed(frame(newCall));

        assertEquals(1, observer.successes);
        assertEquals(2L, observer.frame.transactionId().runGeneration());
        assertEquals(0, observer.failures);
        assertEquals(1, oldCleanup.get());
        assertEquals(1, newCleanup.get());
    }

    @Test
    public void oneInFlightRuntimeRejectsBackpressureWithoutDisturbingActiveWork() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger firstCleanup = new AtomicInteger();
        AtomicInteger rejectedCleanup = new AtomicInteger();
        Observer first = new Observer();
        Observer rejected = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);

        OcrRuntime.Transaction active = runtime.request(request(
                1L, 1L, 1L, firstCleanup::incrementAndGet, first));
        assertNotNull(active);
        assertNull(runtime.request(request(
                1L, 2L, 1L, rejectedCleanup::incrementAndGet, rejected)));
        assertEquals(1, rejected.failures);
        assertEquals(1, rejectedCleanup.get());
        assertTrue(runtime.hasActiveTransaction());

        engine.call(0).succeed(frame(engine.call(0)));
        assertEquals(1, first.successes);
        assertEquals(1, firstCleanup.get());
    }

    @Test
    public void deferredAnalysisOwnershipDelaysFinalSourceCleanupExactlyOnce() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger cleanup = new AtomicInteger();
        OcrRuntime runtime = runtime(engine, scheduler);
        Observer observer = new Observer() {
            @Override
            public void onSuccess(OcrRuntime.Transaction transaction, OcrScan.Frame frame) {
                super.onSuccess(transaction, frame);
                assertTrue(transaction.deferPostProcessing());
                assertTrue(transaction.retainSourceForAnalysis());
            }
        };

        runtime.request(request(1L, 3L, 2L, cleanup::incrementAndGet, observer));
        engine.call(0).succeed(frame(engine.call(0)));
        assertEquals(0, cleanup.get());

        observer.transaction.releaseSourceForAnalysis();
        runtime.finishDeferredPostProcessing(observer.transaction, "ANALYSIS_COMPLETE");
        runtime.finishDeferredPostProcessing(observer.transaction, "ANALYSIS_COMPLETE");

        assertEquals(1, cleanup.get());
    }

    @Test
    public void captureCoordinatorContextReachesRuntimeWithoutIdentityRegeneration() {
        CapturePlatform capturePlatform = new CapturePlatform();
        List<CaptureCoordinator.Outcome<String, String>> outcomes = new ArrayList<>();
        CaptureCoordinator<String, String> captures = new CaptureCoordinator<>(
                capturePlatform, outcomes::add, 100L);
        CaptureCoordinator.Request<String> captureRequest = new CaptureCoordinator.Request<>(
                "capture", List.of(), 9L, CaptureGeometry.Mode.WINDOW, BOUNDS, BOUNDS,
                0, 4, 0L, 13L, CaptureCoordinator.Presentation.NONE, 0L);
        captures.enqueue(captureRequest);
        assertTrue(capturePlatform.success(captureRequest));
        CaptureCoordinator.Outcome<String, String> outcome = outcomes.get(0);

        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);
        runtime.request(request(
                outcome.request().generation(),
                outcome.geometry().captureSequence(),
                outcome.request().admissionEpoch(),
                () -> { },
                observer,
                outcome.geometry()));
        engine.call(0).succeed(frame(engine.call(0)));

        assertEquals(outcome.request().generation(), observer.frame.transactionId().runGeneration());
        assertEquals(outcome.geometry().captureSequence(),
                observer.frame.transactionId().captureSequence());
        assertEquals(outcome.request().admissionEpoch(), observer.frame.admissionEpoch());
        assertEquals(outcome.geometry(), observer.frame.captureGeometry());
    }

    @Test
    public void frameKeepsCaptureEpochAndActionGatewayRejectsLiveEpochChange() {
        FakeEngine engine = new FakeEngine();
        FakeScheduler scheduler = new FakeScheduler();
        Observer observer = new Observer();
        OcrRuntime runtime = runtime(engine, scheduler);
        runtime.request(request(6L, 8L, 21L, () -> { }, observer));
        engine.call(0).succeed(frame(engine.call(0)));

        OcrScan.Frame evidence = observer.frame;
        ActionAdmission.FrameContext frameContext = new ActionAdmission.FrameContext(
                evidence.transactionId().runGeneration(),
                evidence.captureGeometry().captureSequence(),
                evidence.transactionId().ocrRequestSequence(),
                evidence.captureGeometry().capturedAtUptimeMillis(),
                GAME,
                evidence.captureGeometry().targetWindowBoundsOnScreen(),
                evidence.captureGeometry().windowId(),
                evidence.admissionEpoch());
        ActionPlatform platform = new ActionPlatform(new ActionAdmission.CurrentState(
                true,
                evidence.transactionId().runGeneration(),
                evidence.captureGeometry().captureSequence(),
                evidence.transactionId().ocrRequestSequence(),
                evidence.captureGeometry().capturedAtUptimeMillis() + 1L,
                GAME,
                BOUNDS,
                4,
                22L));

        ActionGateway.Result result = new ActionGateway(platform, 3_000L).tap(frameContext, "tap");
        assertFalse(result.accepted());
        assertEquals(ActionAdmission.RejectionReason.ADMISSION_EPOCH, result.decision().reason());
        assertEquals(0, platform.gestures);
    }

    private static OcrRuntime runtime(FakeEngine engine, FakeScheduler scheduler) {
        Executor direct = Runnable::run;
        AtomicLong clock = new AtomicLong(1_000L);
        return new OcrRuntime(
                engine,
                scheduler,
                direct,
                6_000L,
                clock::get,
                new OcrRuntimeDiagnostics(),
                completion -> { });
    }

    private static OcrRuntime.Request request(
            long generation,
            long captureSequence,
            long admissionEpoch,
            Runnable cleanup,
            OcrRuntime.Callback callback) {
        return request(
                generation,
                captureSequence,
                admissionEpoch,
                cleanup,
                callback,
                geometry(captureSequence));
    }

    private static OcrRuntime.Request request(
            long generation,
            long captureSequence,
            long admissionEpoch,
            Runnable cleanup,
            OcrRuntime.Callback callback,
            CaptureGeometry geometry) {
        return new OcrRuntime.Request(
                null,
                OcrScan.Profile.FULL_CHINESE,
                geometry,
                generation,
                admissionEpoch,
                cleanup,
                () -> true,
                callback);
    }

    private static CaptureGeometry geometry(long captureSequence) {
        return new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW,
                100,
                100,
                BOUNDS,
                BOUNDS,
                0,
                4,
                captureSequence,
                900L);
    }

    private static OcrScan.Frame frame(FakeEngine.Call call) {
        return new OcrScan.Frame(
                call.transaction.id(),
                call.admissionEpoch,
                call.profile,
                OcrScan.Transform.create(call.profile, 100, 100, call.geometry),
                List.of(),
                1L,
                null,
                call.geometry,
                true);
    }

    private static class Observer implements OcrRuntime.Callback {
        int successes;
        int failures;
        OcrRuntime.Transaction transaction;
        OcrScan.Frame frame;
        OcrRuntime.Failure failure;

        @Override
        public void onSuccess(OcrRuntime.Transaction transaction, OcrScan.Frame frame) {
            successes++;
            this.transaction = transaction;
            this.frame = frame;
        }

        @Override
        public void onFailure(OcrRuntime.Transaction transaction, OcrRuntime.Failure failure) {
            failures++;
            this.transaction = transaction;
            this.failure = failure;
        }
    }

    private static final class FakeEngine implements OcrRuntime.Engine {
        final List<Call> calls = new ArrayList<>();
        boolean throwOnScan;
        OcrScan.Transform requestedTransform;

        @Override
        public void scan(
                Bitmap bitmap,
                OcrScan.Profile profile,
                CaptureGeometry captureGeometry,
                OcrScan.Transform requestedTransform,
                OcrScanner.Transaction transaction,
                long admissionEpoch,
                BooleanSupplier callbackAdmission,
                Executor callbackExecutor,
                OcrScanner.FrameCallback callback,
                OcrRuntimeDiagnostics diagnostics) {
            this.requestedTransform = requestedTransform;
            if (throwOnScan) {
                throw new IllegalStateException("synchronous setup failure");
            }
            calls.add(new Call(
                    profile, captureGeometry, transaction, admissionEpoch, callbackAdmission, callback));
        }

        Call call(int index) { return calls.get(index); }

        static final class Call {
            final OcrScan.Profile profile;
            final CaptureGeometry geometry;
            final OcrScanner.Transaction transaction;
            final long admissionEpoch;
            final BooleanSupplier callbackAdmission;
            final OcrScanner.FrameCallback callback;

            Call(
                    OcrScan.Profile profile,
                    CaptureGeometry geometry,
                    OcrScanner.Transaction transaction,
                    long admissionEpoch,
                    BooleanSupplier callbackAdmission,
                    OcrScanner.FrameCallback callback) {
                this.profile = profile;
                this.geometry = geometry;
                this.transaction = transaction;
                this.admissionEpoch = admissionEpoch;
                this.callbackAdmission = callbackAdmission;
                this.callback = callback;
            }

            void completeRecognition() {
                assertTrue(callbackAdmission.getAsBoolean());
                assertTrue(transaction.tryFinish(OcrScanner.TerminalState.SUCCESS));
            }

            void succeed(OcrScan.Frame frame) {
                completeRecognition();
                callback.onSuccess(frame);
            }

            void fail(Exception error) {
                assertTrue(callbackAdmission.getAsBoolean());
                assertTrue(transaction.tryFinish(OcrScanner.TerminalState.FAILURE));
                callback.onFailure(error);
            }

            void lateSuccess(OcrScan.Frame frame) {
                callback.onSuccess(frame);
            }
        }
    }

    private static final class FakeScheduler implements OcrRuntime.Scheduler {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public Object schedule(long delayMillis, Runnable action) {
            tasks.add(action);
            return action;
        }

        @Override
        public void cancel(Object handle) {
            tasks.remove(handle);
        }

        void runNext() {
            tasks.remove(0).run();
        }

        int pendingCount() { return tasks.size(); }
    }

    private static final class CapturePlatform
            implements CaptureCoordinator.Platform<String, String> {
        private CaptureCoordinator.Callback<String> callback;

        @Override
        public Object prepareOverlay(CaptureCoordinator.Request<String> request) {
            return null;
        }

        @Override
        public void restoreOverlay(CaptureCoordinator.Request<String> request, Object token) { }

        @Override
        public void dispatch(
                CaptureCoordinator.Request<String> request,
                CaptureCoordinator.Callback<String> callback) {
            this.callback = callback;
        }

        @Override
        public Object schedule(long delayMillis, Runnable timeout) { return timeout; }

        @Override
        public void cancel(Object handle) { }

        boolean success(CaptureCoordinator.Request<String> request) {
            return callback.success(
                    1L,
                    request.generation(),
                    request.admissionEpoch(),
                    new CaptureCoordinator.Frame<>("capture", 100, 100, 900L));
        }
    }

    private static final class ActionPlatform implements ActionGateway.Platform {
        private final ActionAdmission.CurrentState state;
        int gestures;

        ActionPlatform(ActionAdmission.CurrentState state) { this.state = state; }

        @Override public ActionAdmission.CurrentState currentState() { return state; }
        @Override public boolean performNodeAction(Object node, int action, Object argument) {
            return true;
        }
        @Override public boolean dispatchGesture(Object gesture) {
            gestures++;
            return true;
        }
        @Override public boolean performGlobalAction(int action) { return true; }
    }
}
