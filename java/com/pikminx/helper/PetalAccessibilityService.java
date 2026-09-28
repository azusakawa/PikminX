package com.pikminx.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.pikminx.helper.platform.config.RemoteConfigClient;
import com.pikminx.helper.platform.diagnostics.AdmissionDiagnostics;
import com.pikminx.helper.platform.diagnostics.UsageTelemetryClient;
import com.pikminx.helper.platform.diagnostics.WorkflowDiagnostics;
import com.pikminx.helper.platform.settings.SettingsStore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 以無障礙服務讀取遊戲畫面，依使用者輸入的花朵順序執行點擊。
 *
 * <p>使用者先在遊戲中開啟種花選單；服務會以搜尋欄篩選設定中的目標花盆，
 * 確認搜尋文字、完整目標名稱與右下角數量後點擊，不會滾動花盆清單。</p>
 */
public final class PetalAccessibilityService extends AccessibilityService {
    private record GatewayGesture(
            GestureDescription gesture,
            AccessibilityService.GestureResultCallback callback) {}

    private record CurrentGameWindow(
            String packageName,
            CaptureGeometry.Bounds bounds,
            int windowId) {}

    private record PendingCaptureOnlyAction(
            long generation,
            String profile,
            Runnable action,
            Runnable failed) {}

    private enum GestureDispatchResult {
        SENT,
        SYSTEM_REJECTED,
        STALE
    }

    private static final String TAG = "PikminX";
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final int MAX_ACTION_ATTEMPTS = 3;
    private static final long SCAN_INTERVAL_MILLIS = 3000L;
    private static final long POSTCARD_MIN_SCAN_DELAY_MILLIS = 1200L;
    private static final long POSTCARD_VERIFY_DELAY_MILLIS = 1800L;
    private static final long POSTCARD_RECEIPT_RETURN_VERIFY_DELAY_MILLIS =
            PostcardTiming.receiptReturnDelayMillis(POSTCARD_VERIFY_DELAY_MILLIS);
    private static final long POSTCARD_RECEIPT_EXIT_DELAY_MILLIS =
            PostcardTiming.receiptReturnDelayMillis(900L);
    private static final long GAME_ACTION_TAP_DURATION_MILLIS = 180L;
    private static final long AUTOMATION_START_DELAY_MILLIS = 180L;
    private static final long POSTCARD_FAST_SCAN_DELAY_MILLIS = 450L;
    private static final long DISPATCH_SCAN_DELAY_MILLIS = 850L;
    private static final long DISPATCH_AFTER_TAP_DELAY_MILLIS = 1600L;
    private static final long DISPATCH_AUTO_VERIFY_DELAY_MILLIS = 450L;
    private static final long DISPATCH_PIKMIN_TAP_DELAY_MILLIS = 250L;
    private static final long DISPATCH_AFTER_SCROLL_DELAY_MILLIS = 250L;
    private static final long SCREENSHOT_CALLBACK_TIMEOUT_MILLIS = 6000L;
    private static final long OCR_CALLBACK_TIMEOUT_MILLIS = 6000L;
    private static final long H10A_SLOW_ANALYSIS_LOG_DELAY_MILLIS = 1000L;
    // OCR and screenshot processing may take time, but an actionable frame must remain recent.
    private static final long MAX_ACTIONABLE_FRAME_AGE_MILLIS = 3000L;
    private static final long RETURN_REWARD_SCAN_DELAY_MILLIS = 1000L;
    private static final long RETURN_REWARD_AFTER_TAP_DELAY_MILLIS = 1000L;
    private static final long RETURN_REWARD_FRAME_HIDE_DELAY_MILLIS = 50L;
    private static final long RETURN_REWARD_SETTLE_MILLIS = 1500L;
    private static final long RETURN_REWARD_PERSISTENT_TARGET_REARM_MILLIS = 3000L;
    private static final long RETURN_REWARD_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    private static final int RETURN_REWARD_REQUIRED_WARNING_FRAMES = 2;
    private static final long FEED_OCR_RETRY_MILLIS = 500L;
    private static final long FEED_SQUAD_SETTLE_MILLIS = 1000L;
    private static final long FEED_NO_EFFECT_TIMEOUT_MILLIS = 10_000L;
    private static final long FEED_DRAG_MILLIS = 700L;
    private static final long FEED_HOLD_SAMPLE_MILLIS = 1000L;
    private static final long FEED_HOLD_MIN_MILLIS = 2000L;
    private static final long FEED_HOLD_MAX_MILLIS = 10_000L;
    private static final long FEED_COLLECT_SCAN_MILLIS = 250L;
    private static final long FEED_SPIRAL_HANDOFF_MILLIS = 100L;
    private static final long FEED_SPIRAL_HARVEST_MILLIS = 6000L;
    private static final long FEED_HARVEST_RECEIPT_WINDOW_MILLIS = 2500L;
    private static final long FEED_HARVEST_RECEIPT_QUIET_MILLIS = 500L;
    private static final long FEED_HARVEST_RECEIPT_MAX_WINDOW_MILLIS = 5000L;
    private static final long FEED_ZOOM_MILLIS = 650L;
    private static final long FEED_ZOOM_SETTLE_MILLIS = 1000L;
    private static final long FEED_NECTAR_SELECT_TAP_MILLIS = 20L;
    private static final long FEED_DETAIL_CLOSE_SETTLE_MILLIS = 800L;
    private static final long FEED_WHISTLE_TAP_GAP_MILLIS = 100L;
    private static final int FEED_MAX_GESTURES_PER_ROUND = 4;
    private static final int FEED_MAX_TARGET_MISSING_FRAMES = 8;
    private static final int FEED_REQUIRED_SEARCH_FRAMES = 2;
    private static final int FEED_REQUIRED_NECTAR_TARGET_FRAMES = 2;
    private static final int FEED_REQUIRED_PANEL_CLOSED_FRAMES = 2;
    private static final int FEED_PANEL_CLOSE_RETRY_FRAMES = 3;
    private static final int FEED_MAX_NECTAR_TAP_ATTEMPTS = 2;
    private static final int FEED_WHISTLE_TAP_COUNT = 3;
    private static final int FEED_HOLD_REQUIRED_STABLE_READS = 3;
    private static final int FEED_SPIRAL_SEGMENTS = 160;
    private static final int FEED_REQUIRED_BLOOM_TARGET_FRAMES = 2;
    private static final int FEED_MAX_BLOOM_TARGET_MISSING_FRAMES = 8;
    private static final int FEED_DETAIL_RETURN_FRAMES = 6;
    // 搜尋框、鍵盤與 Unity 清單都有轉場動畫；每個搜尋步驟先等一秒。
    private static final long POSTCARD_PETAL_STEP_DELAY_MILLIS = 1000L;
    // 懸浮 ICON 與使用者指定尺寸一致；背景透明，避免額外黑框。
    private static WeakReference<PetalAccessibilityService> connectedService =
            new WeakReference<>(null);

    private enum AutomationStep {
        CHECKING_PLANTING_ENTRY,
        WAITING_INITIAL_PLANTING_MENU,
        MONITORING,
        REVEALING_SEARCH_PANEL,
        OPENING_SEARCH,
        CLEARING_SEARCH,
        ENTERING_SEARCH,
        CLOSING_SEARCH_KEYBOARD,
        SELECTING_SEARCH_RESULT,
        VERIFYING_SELECTION,
        CLOSING_SEARCH_AFTER_SELECTION,
        WAITING_START,
        VERIFYING_START,
        WAITING_MENU_AFTER_START,
        WAITING_STOP,
        VERIFYING_STOP
    }

    private enum AutomationMode {
        NONE,
        PLANTING,
        FEED,
        POSTCARD,
        DISPATCH,
        RETURN_REWARD
    }

    private enum FeedStep {
        WAITING_GAME_READY,
        OPENING_NECTAR,
        OPENING_NECTAR_SEARCH,
        CLEARING_NECTAR_SEARCH,
        ENTERING_NECTAR_SEARCH,
        CONFIRMING_NECTAR_SEARCH,
        CLOSING_NECTAR_KEYBOARD,
        SELECTING_NECTAR,
        WAITING_NECTAR_PANEL_CLOSE,
        ZOOMING_OUT,
        READING_NECTAR_COUNT,
        READING_CONSUMED_COUNT,
        FEEDING,
        COLLECTING,
        SWITCHING
    }

    private enum FeedSearchResetPhase {
        NONE,
        CLOSING
    }

    private enum FeedCollectPhase {
        LOCATING_BLOOM,
        SPIRALING,
        RECEIVING_SPIRAL_RECEIPTS
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTask = this::requestScan;
    private final List<Runnable> pendingAutomationStartTasks = new ArrayList<>();
    private AutomationMode pendingAutomationStartMode = AutomationMode.NONE;
    private OverlayHost overlayHost;
    private WindowManager returnRewardWindowManager;
    private SettingsStore settings;
    private OcrRuntime ocrRuntime;
    private final FrameAnalysisExecutor frameAnalysisExecutor = new FrameAnalysisExecutor();
    private H10aAnalysisJob activeH10aAnalysisJob;
    private NectarTemplateMatcher nectarTemplateMatcher;
    private ExpeditionTemplateMatcher expeditionTemplateMatcher;
    private final ExpeditionRecognition.Consensus expeditionRecognitionConsensus =
            new ExpeditionRecognition.Consensus();
    private int consecutiveOcrEngineFailures;
    private final SwitchGuard switchGuard = new SwitchGuard();
    private final PostcardAutomation postcardAutomation = new PostcardAutomation();
    private final PostcardReturnGuard postcardReturnGuard = new PostcardReturnGuard();
    private boolean running;
    private boolean busy;
    private AutomationMode automationMode = AutomationMode.NONE;
    private int postcardUnknownFrames;
    private int postcardMissingControlFrames;
    private int postcardReceiptWaitFrames;
    private int postcardBackAttempts;
    private boolean postcardReturnFlowerTapped;
    private final ObservationStability postcardPotStability =
            new ObservationStability(2, 2, 0.08f, 0.07f);
    private int postcardPetalSearchMissingFrames;
    private int postcardPetalInputAttempts;
    private final SearchKeyboardGuard postcardSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private final ObservationStability plantingPotStability =
            new ObservationStability(2, 2, 0.08f, 0.07f);
    private final ObservationStability plantingEntryStability =
            new ObservationStability(2, 1, 0.025f, 0.025f);
    private final ObservationStability plantingMenuStability =
            new ObservationStability(2, 0, 0.01f, 0.01f);
    private final ObservationStability plantingActiveStability =
            new ObservationStability(2, 1, 0.01f, 0.01f);
    private final ObservationStability feedReadyStability =
            new ObservationStability(2, 1, 0f, 0f);
    private int plantingSearchMissingFrames;
    private int plantingMonitorMissingFrames;
    private int plantingSearchInputAttempts;
    private final SearchKeyboardGuard plantingSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private int plantingSearchMinimumCount;
    private final PlantingSearchCloseGuard plantingSearchCloseGuard =
            new PlantingSearchCloseGuard(12);
    private boolean plantingSkippedLast;
    private final java.util.Set<String> plantingSkippedFlowers = new java.util.HashSet<>();
    private int postcardPikminCountConfirmations;
    private int postcardLastPikminCount = -1;
    private ExpeditionDispatchSession expeditionDispatchSession;
    private ExpeditionScreenAnalyzer.ItemKind dispatchCurrentItemKind;
    private ExpeditionTargetMode expeditionTargetMode = ExpeditionTargetMode.FRUIT_AND_POT;
    private DispatchSelectionMethod dispatchSelectionMethod = DispatchSelectionMethod.AUTO;
    private DispatchPikminType dispatchPikminType = DispatchPikminType.MIXED;
    private boolean dispatchColorSelected;
    private boolean dispatchPikminSelected;
    private boolean dispatchSearchOpened;
    private boolean dispatchSearchTextConfirmed;
    private int dispatchSearchOpenAttempts;
    private int dispatchSearchInputAttempts;
    private int dispatchSearchResultMissingFrames;
    private final SearchKeyboardGuard dispatchSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private int dispatchSelectionTargetCount;
    private int dispatchSelectionBeforeCount = -1;
    private boolean dispatchSelectionGesturePending;
    private boolean dispatchReturnRevealPending;
    private int dispatchAutoTapAttempts;
    private int dispatchAutoResultMissingFrames;
    private int dispatchAutoAnchorMissingFrames;
    private int dispatchUnknownFrames;
    private final ReturnRewardScanGuard returnRewardScanGuard = new ReturnRewardScanGuard();
    private final ReturnRewardRoi returnRewardRoi = new ReturnRewardRoi();
    private final Runnable returnRewardAnchorGuardTask = this::guardReturnRewardAnchor;
    private View returnRewardAnchorOverlay;
    private View returnRewardRoiOverlay;
    private boolean returnRewardRoiHiddenForCapture;
    private long returnRewardStartedAt;
    private long returnRewardLastTapAt;
    private boolean returnRewardReceivePostcard = true;
    private boolean returnRewardContinueOnNectarWarning;
    private int returnRewardNectarWarningFrames;
    private boolean returnRewardNectarWarningActive;
    private PostcardMatcher.Target returnRewardPostcardTarget;
    private int returnRewardPostcardConfirmations;
    private int returnRewardPostcardAttempts;
    private boolean returnRewardWaitingPostcardExit;
    private String currentFlower = "";
    private AutomationStep automationStep = AutomationStep.MONITORING;
    private String targetFlower = "";
    private int targetCount;
    private int actionAttempts;
    private int stopMissingConfirmations;
    private int plantingTransitionFrames;
    private boolean initialPlantingMenuConfirmed;
    private boolean startAfterSelection;
    private boolean selectionFromSearch;
    private int targetSelectionX;
    private int targetSelectionY;
    private CaptureCoordinator<Void, Bitmap> captureCoordinator;
    private ActionGateway actionGateway;
    private PendingCaptureOnlyAction pendingCaptureOnlyAction;
    private final AdmissionDiagnostics admissionDiagnostics = new AdmissionDiagnostics();
    private final WorkflowDiagnostics workflowDiagnostics = new WorkflowDiagnostics();
    private final ThreadLocal<CaptureGeometry> handlingCaptureGeometry = new ThreadLocal<>();
    private final ThreadLocal<ActionAdmission.FrameContext> handlingActionContext =
            new ThreadLocal<>();
    private long runGeneration;
    private record DispatchVisualCandidate(ExpeditionVision.Icon icon,
            ExpeditionRecognition.Scores fruit, ExpeditionRecognition.Scores seedling,
            ExpeditionRecognition.Classification classification) {}
    private java.util.concurrent.atomic.AtomicBoolean dispatchVisionCancellation;
    private final java.util.Set<String> dispatchInspectedVisual = new java.util.HashSet<>();
    private long dispatchSettingsRevision;
    private long pendingDispatchSettingsRevision;
    private long latestActionableOcrRequestSequence;
    private long actionAdmissionEpoch;
    private String recentPackage = "";
    private long recentPackageAt;
    private String overlayServiceLifecycle = "CREATED";
    private long remoteConfigRequestToken;
    private UsageTelemetryClient.Session usageSession;
    private FeedSettingsInput feedSettings = new FeedSettingsInput(6, 0, 40, 1200);
    private FeedStep feedStep = FeedStep.WAITING_GAME_READY;
    private List<String> feedFlowerSequence = List.of();
    private int feedFlowerSequenceIndex;
    private String feedTargetFlower = "";
    private int feedRound;
    private int feedAttemptCount;
    private int feedSquadSwitchCount;
    private int feedTargetMissingFrames;
    private int feedReadyMissingFrames;
    private int feedNectarOpenAttempts;
    private boolean feedRequireNectarSearch;
    private int feedSearchMissingFrames;
    private int feedSearchActionAttempts;
    private int feedSearchInputAttempts;
    private int feedSearchOpenConfirmationFrames;
    private int feedSearchTextConfirmationFrames;
    private FeedSearchResetPhase feedSearchResetPhase = FeedSearchResetPhase.NONE;
    private final SearchKeyboardGuard feedSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private FeedScreenAnalyzer.NectarSelection feedNectarCandidate;
    private FeedScreenAnalyzer.NectarSelection feedSelectedNectar;
    private boolean feedNectarFreshFrameRequired;
    private int feedNectarCandidateFrames;
    private int feedNectarTapAttempts;
    private int feedPanelCloseWaitFrames;
    private int feedPanelClosedConfirmationFrames;
    private int feedDetailCloseAttempts;
    private int feedNectarBeforeRound = -1;
    private boolean feedCollectAfterCount;
    private long feedNoEffectStartedAt;
    private boolean feedZoomReady;
    private int feedCollectedPetals;
    private boolean feedCollectReturningFromDetail;
    private boolean feedCollectReturningFromShare;
    private int feedCollectReturnFrames;
    private FeedCollectPhase feedCollectPhase = FeedCollectPhase.LOCATING_BLOOM;
    private boolean feedCollectionOnlyRecovery;
    private FeedScreenAnalyzer.VisualSignature feedBloomBaseline;
    private FeedScreenAnalyzer.BloomTarget feedBloomCandidate;
    private int feedBloomCandidateFrames;
    private int feedBloomMissingFrames;
    private long feedHarvestReceiptWindowStartedAt;
    private long feedHarvestLastReceiptAt;
    private int feedSpiralGestureGain;
    private Integer feedHarvestPreviousReceiptGain;
    private boolean feedHarvestReceiptVisible;
    private GestureDescription.StrokeDescription feedHoldStroke;
    private float feedHoldX;
    private float feedHoldY;
    private float feedHoldDirection;
    private long feedHoldStartedAt;
    private Integer feedHoldLastCount;
    private int feedHoldStableReads;
    private boolean feedHoldReleasing;
    private Runnable feedHoldCompleted;
    private Runnable feedHoldFailed;
    private ActionAdmission.FrameContext feedHoldActionContext;
    private ActionAdmission.FrameContext feedHoldOriginContext;
    private final FeedHoldLifecycle feedHoldLifecycle = new FeedHoldLifecycle();

    private interface OcrFrameConsumer {
        void accept(OcrRuntime.Transaction transaction, OcrScan.Frame frame);
    }

    private interface OcrFailureConsumer {
        void accept(Exception error);
    }

    /** Holds both worker and main-result ownership until the pure analysis handoff is complete. */
    private final class H10aAnalysisJob {
        final OcrRuntime.Transaction transaction;
        final OcrFailureConsumer failure;
        final OcrScan.Frame frame;
        final Bitmap bitmap;
        final H10aPlantingAnalysis.Input input;
        final ActionAdmission.FrameContext actionContext;
        final String mode;
        final String step;
        private boolean cancelled;
        private boolean started;
        private volatile String stage = "queued";
        private volatile boolean finished;
        private boolean workerHoldReleased;
        private boolean mainHoldReleased;
        private final Runnable slowDiagnostic;

        H10aAnalysisJob(
                OcrRuntime.Transaction transaction,
                OcrFailureConsumer failure,
                OcrScan.Frame frame,
                Bitmap bitmap,
                H10aPlantingAnalysis.Input input,
                ActionAdmission.FrameContext actionContext,
                String mode,
                String step) {
            boolean workerRetained = transaction != null
                    && transaction.retainSourceForAnalysis();
            boolean mainRetained = workerRetained
                    && transaction.retainSourceForAnalysis();
            if (transaction == null
                    || failure == null
                    || frame == null
                    || bitmap == null
                    || input == null
                    || actionContext == null
                    || mode == null
                    || step == null
                    || !workerRetained
                    || !mainRetained) {
                if (transaction != null && mainRetained) {
                    transaction.releaseSourceForAnalysis();
                }
                if (transaction != null && workerRetained) {
                    transaction.releaseSourceForAnalysis();
                }
                throw new IllegalStateException("Unable to retain analysis bitmap");
            }
            this.transaction = transaction;
            this.failure = failure;
            this.frame = frame;
            this.bitmap = bitmap;
            this.input = input;
            this.actionContext = actionContext;
            this.mode = mode;
            this.step = step;
            slowDiagnostic = () -> {
                synchronized (this) {
                    if (!started || finished || cancelled) {
                        return;
                    }
                    Log.w(TAG, "H10A_ANALYSIS_SLOW tx="
                            + this.transaction.id()
                            + " stage=" + stage
                            + " size=" + this.input.width() + "x" + this.input.height());
                }
            };
        }

        synchronized boolean begin() {
            if (cancelled) {
                releaseWorkerHoldLocked();
                return false;
            }
            started = true;
            stage = "running";
            handler.postDelayed(slowDiagnostic, H10A_SLOW_ANALYSIS_LOG_DELAY_MILLIS);
            return true;
        }

        synchronized boolean isCancelled() {
            return cancelled;
        }

        synchronized void cancel() {
            cancelled = true;
            handler.removeCallbacks(slowDiagnostic);
            if (!started) {
                releaseWorkerHoldLocked();
            }
            releaseMainHoldLocked();
        }

        void run() {
            if (!begin()) {
                return;
            }
            long startedAtNanos = System.nanoTime();
            H10aPlantingAnalysis.Result result = null;
            RuntimeException failure = null;
            try {
                if (isCancelled() || bitmap.isRecycled()) {
                    failure = new IllegalStateException("Planting analysis bitmap is unavailable");
                } else {
                    stage = "snapshot";
                    long snapshotStartedAtNanos = System.nanoTime();
                    int[] pixels = snapshotBitmapPixels(bitmap);
                    long snapshotMillis = elapsedMillis(snapshotStartedAtNanos);
                    int pixelWidth = bitmap.getWidth();
                    stage = "detectors";
                    result = H10aPlantingAnalysis.analyze(
                            input,
                            (x, y) -> pixels[y * pixelWidth + x],
                            this::setAnalysisStage);
                    recordH10aAnalysisTimings(
                            result,
                            elapsedMillis(startedAtNanos),
                            false,
                            snapshotMillis);
                }
                stage = "delivery";
                H10aPlantingAnalysis.Result delivered = result;
                RuntimeException deliveredFailure = failure;
                postH10aAnalysis(this, delivered, deliveredFailure);
            } catch (RuntimeException error) {
                postH10aAnalysis(this, null, error);
            } finally {
                synchronized (this) {
                    finished = true;
                }
                handler.removeCallbacks(slowDiagnostic);
                releaseWorkerHold();
            }
        }

        void setAnalysisStage(String nextStage) {
            if (nextStage != null && !nextStage.isEmpty()) {
                stage = nextStage;
            }
        }

        void releaseWorkerHold() {
            synchronized (this) {
                releaseWorkerHoldLocked();
            }
        }

        private void releaseWorkerHoldLocked() {
            if (workerHoldReleased) {
                return;
            }
            workerHoldReleased = true;
            transaction.releaseSourceForAnalysis();
        }

        void releaseMainHold() {
            synchronized (this) {
                releaseMainHoldLocked();
            }
        }

        private void releaseMainHoldLocked() {
            if (mainHoldReleased) {
                return;
            }
            mainHoldReleased = true;
            transaction.releaseSourceForAnalysis();
        }
    }

    private boolean postH10aAnalysis(
            H10aAnalysisJob job,
            H10aPlantingAnalysis.Result result,
            RuntimeException error) {
        try {
            if (handler.post(() -> deliverH10aAnalysis(job, result, error))) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // The service may be tearing down its main looper.
        }
        job.releaseMainHold();
        ocrRuntime.finishDeferredPostProcessing(
                job.transaction,
                error == null ? "ANALYSIS_DELIVERY_FAILED" : "ANALYSIS_FAILED");
        return false;
    }

    private H10aPlantingAnalysis.Input plantingAnalysisInput(
            OcrScan.Frame frame, Bitmap bitmap) {
        return new H10aPlantingAnalysis.Input(
                frame.tokens(),
                settings.allowedFlowers(),
                bitmap.getWidth(),
                bitmap.getHeight(),
                currentFlower,
                targetFlower,
                plantingSearchMinimumCount,
                plantingSearchWork(),
                selectionFromSearch,
                targetSelectionX,
                targetSelectionY);
    }

    /** Limits planting analysis to evidence consumed by the current search sub-state. */
    private H10aPlantingAnalysis.SearchWork plantingSearchWork() {
        if (automationMode != AutomationMode.PLANTING || !isPlantingSearchStep()) {
            return H10aPlantingAnalysis.SearchWork.NONE;
        }
        return switch (automationStep) {
            case REVEALING_SEARCH_PANEL, ENTERING_SEARCH ->
                    H10aPlantingAnalysis.SearchWork.NO_SEARCH_ANALYSIS;
            case OPENING_SEARCH -> H10aPlantingAnalysis.SearchWork.PLANTING_AND_CLOSE;
            case CLEARING_SEARCH, CLOSING_SEARCH_KEYBOARD ->
                    H10aPlantingAnalysis.SearchWork.CLOSE_ONLY;
            case SELECTING_SEARCH_RESULT -> H10aPlantingAnalysis.SearchWork.RESULT;
            case CLOSING_SEARCH_AFTER_SELECTION ->
                    H10aPlantingAnalysis.SearchWork.PLANTING_AND_CLOSE_AND_SCREEN;
            default -> H10aPlantingAnalysis.SearchWork.NONE;
        };
    }

    /** Takes one immutable worker-owned pixel snapshot instead of calling Bitmap.getPixel per sample. */
    private int[] snapshotBitmapPixels(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalStateException("Planting analysis bitmap is unavailable");
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long pixelCount = (long) width * height;
        if (width <= 0 || height <= 0 || pixelCount > Integer.MAX_VALUE) {
            throw new IllegalStateException("Planting analysis bitmap is too large");
        }
        int[] pixels = new int[(int) pixelCount];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    /** Submits only the immutable planting detector portion of handleTokens. */
    private boolean dispatchH10aPlantingAnalysis(
            OcrRuntime.Transaction transaction,
            OcrScan.Frame frame,
            Bitmap bitmap,
            OcrFailureConsumer failure) {
        if (transaction == null
                || frame == null
                || bitmap == null
                || bitmap.isRecycled()
                || settings == null) {
            return false;
        }
        H10aPlantingAnalysis.Input input = plantingAnalysisInput(frame, bitmap);
        ActionAdmission.FrameContext actionContext = actionContextForFrame(frame);
        if (!transaction.deferPostProcessing()) {
            return false;
        }
        H10aAnalysisJob job;
        try {
            job = new H10aAnalysisJob(
                    transaction,
                    failure,
                    frame,
                    bitmap,
                    input,
                    actionContext,
                    AutomationMode.PLANTING.name(),
                    automationStep.name());
        } catch (RuntimeException error) {
            transaction.cancelPostProcessingDeferral();
            Log.w(TAG, "H10A analysis bitmap retention failed", error);
            return false;
        }
        activeH10aAnalysisJob = job;
        busy = true;
        workflowDiagnostics.recordCount(
                WorkflowDiagnostics.Counter.FRAME_ANALYSIS_SUBMISSIONS);
        workflowDiagnostics.recordAnalysisQueueState(
                frameAnalysisExecutor.depth(), frameAnalysisExecutor.highWater());
        if (frameAnalysisExecutor.submit(job::run)) {
            workflowDiagnostics.recordAnalysisQueueState(
                    frameAnalysisExecutor.depth(), frameAnalysisExecutor.highWater());
            return true;
        }
        workflowDiagnostics.recordCount(
                WorkflowDiagnostics.Counter.FRAME_ANALYSIS_REJECTED_SUBMISSIONS);
        activeH10aAnalysisJob = null;
        job.cancel();
        transaction.cancelPostProcessingDeferral();
        busy = false;
        workflowDiagnostics.recordAnalysisQueueState(
                frameAnalysisExecutor.depth(), frameAnalysisExecutor.highWater());
        return false;
    }

    private void deliverH10aAnalysis(
            H10aAnalysisJob job,
            H10aPlantingAnalysis.Result result,
            RuntimeException error) {
        if (activeH10aAnalysisJob != job) {
            job.releaseMainHold();
            return;
        }
        activeH10aAnalysisJob = null;
        busy = false;
        workflowDiagnostics.recordAnalysisQueueState(
                frameAnalysisExecutor.depth(), frameAnalysisExecutor.highWater());
        String outcome = "ANALYSIS_COMPLETE";
        try {
            if (job.isCancelled()) {
                outcome = "ANALYSIS_CANCELLED";
                return;
            }
            if (error != null || result == null) {
                outcome = "ANALYSIS_FAILED";
                if (isActiveRun(job.transaction.id().runGeneration())) {
                    job.failure.accept(error == null
                            ? new IllegalStateException("Planting analysis returned no result")
                            : error);
                }
                return;
            }
            long actionDecisionStartedAtUptimeMillis =
                    android.os.SystemClock.uptimeMillis();
            ActionAdmission.CurrentState current = currentActionAdmissionState();
            H10aAnalysisAdmission.Decision admission = H10aAnalysisAdmission.evaluate(
                    new H10aAnalysisAdmission.Context(
                            job.actionContext,
                            job.mode,
                            job.step),
                    new H10aAnalysisAdmission.Current(
                            current,
                            automationMode.name(),
                            automationStep.name()),
                    MAX_ACTIONABLE_FRAME_AGE_MILLIS);
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.ACTION_DECISION,
                    android.os.SystemClock.uptimeMillis()
                            - actionDecisionStartedAtUptimeMillis);
            boolean frameSafe = job.frame.canDriveAction(job.transaction.id());
            ActionAdmission.Decision actionDecision = !frameSafe
                    ? new ActionAdmission.Decision(
                            false, ActionAdmission.RejectionReason.FRAME_NOT_ACTION_SAFE)
                    : admission.reason()
                    == H10aAnalysisAdmission.RejectionReason.ACTION_ADMISSION
                    ? new ActionAdmission.Decision(false, admission.actionReason())
                    : new ActionAdmission.Decision(
                            admission.allowed(),
                            admission.allowed()
                                    ? ActionAdmission.RejectionReason.NONE
                                    : ActionAdmission.RejectionReason.INVALID_CONTEXT);
            recordActionAdmissionDiagnostic(job.actionContext, actionDecision);
            if (!admission.allowed() || !frameSafe) {
                outcome = "ANALYSIS_ADMISSION_REJECTED";
                workflowDiagnostics.recordCount(
                        WorkflowDiagnostics.Counter.FRAME_ANALYSIS_STALE_RESULTS);
                Log.i(TAG, "H10A_ANALYSIS_DROP reason=" + admission.reason()
                        + " actionReason=" + admission.actionReason()
                        + " tx=" + job.transaction.id());
                dropStaleAction();
                return;
            }
            workflowDiagnostics.recordCount(
                    WorkflowDiagnostics.Counter.FRAME_ANALYSIS_COMPLETIONS);
            runWithActionContext(
                    job.transaction,
                    job.frame,
                    () -> handlePlantingTokens(job.frame, job.bitmap, result));
        } catch (RuntimeException analysisFailure) {
            outcome = "ANALYSIS_CALLBACK_FAILURE";
            Log.e(TAG, "H10A planting analysis callback failed", analysisFailure);
            if (isActiveRun(job.transaction.id().runGeneration())) {
                job.failure.accept(analysisFailure);
            }
        } finally {
            job.releaseMainHold();
            ocrRuntime.diagnostics().markHandleTokensEnd(job.transaction.id());
            ocrRuntime.finishDeferredPostProcessing(job.transaction, outcome);
        }
    }

    private void cancelH10aAnalysis() {
        H10aAnalysisJob job = activeH10aAnalysisJob;
        if (job == null) {
            return;
        }
        activeH10aAnalysisJob = null;
        busy = false;
        job.cancel();
        workflowDiagnostics.recordCount(
                WorkflowDiagnostics.Counter.FRAME_ANALYSIS_STALE_RESULTS);
        ocrRuntime.diagnostics().markHandleTokensEnd(job.transaction.id());
        ocrRuntime.finishDeferredPostProcessing(job.transaction, "ANALYSIS_CANCELLED");
        workflowDiagnostics.recordAnalysisQueueState(
                frameAnalysisExecutor.depth(), frameAnalysisExecutor.highWater());
    }

    private void recordH10aAnalysisTimings(
            H10aPlantingAnalysis.Result result,
            long totalMillis,
            boolean mainThread,
            long snapshotMillis) {
        if (result == null || result.timings() == null) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.FRAME_ANALYSIS,
                totalMillis,
                threadName,
                mainThread);
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.MAP_DETECTOR,
                result.timings().mapDetectorMillis(),
                threadName,
                mainThread);
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.PIXEL_DETECTOR,
                result.timings().pixelDetectorMillis(),
                threadName,
                mainThread);
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.IMAGE_SAMPLING,
                saturatingAdd(
                        Math.max(0L, snapshotMillis),
                        result.timings().imageSamplingMillis()),
                threadName,
                mainThread);
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.STATE_CLASSIFICATION,
                result.timings().stateClassificationMillis(),
                threadName,
                mainThread);
    }

    private static long saturatingAdd(long first, long second) {
        if (first < 0L || second < 0L || Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    /** 服務啟動後初始化 OCR、偏好設定與可拖曳懸浮窗。 */
    private OverlayHost createOverlayHost() {
        return new OverlayHost(
                this,
                handler,
                settings,
                new OverlayHost.WorkflowCallbacks() {
                    @Override
                    public void startPlanting() {
                        startAutomation();
                    }

                    @Override
                    public void scheduleFeedStart(FeedSettingsInput input) {
                        postDelayedAutomationStart(
                                AutomationMode.FEED, () -> startFeedAutomation(input));
                    }

                    @Override
                    public void startPostcard(
                            int collectionLimit, String petalPotName, int pikminCount) {
                        startPostcardAutomation(collectionLimit, petalPotName, pikminCount);
                    }

                    @Override
                    public void scheduleDispatchStart(
                            ExpeditionTargetMode targetMode,
                            DispatchSelectionMethod selectionMethod,
                            DispatchPikminType pikminType) {
                        postDelayedAutomationStart(
                                AutomationMode.DISPATCH,
                                () -> startExpeditionDispatch(
                                        targetMode, selectionMethod, pikminType));
                    }

                    @Override
                    public void scheduleReturnRewardStart(
                            boolean receivePostcards, boolean continueOnNectarWarning) {
                        postDelayedAutomationStart(
                                AutomationMode.RETURN_REWARD,
                                () -> startReturnRewardCollection(
                                        receivePostcards, continueOnNectarWarning));
                    }

                    @Override
                    public boolean isAnyWorkflowActive() {
                        return PetalAccessibilityService.this.isAnyWorkflowActive();
                    }

                    @Override
                    public void requestGlobalStop(String reason) {
                        PetalAccessibilityService.this.requestGlobalStop(reason);
                    }

                },
                this::isOverlayFeatureEnabled,
                this::overlayDiagnosticContext);
    }

    private boolean isOverlayFeatureEnabled() {
        RemoteConfigClient.Status remoteConfig = RemoteConfigClient.cached(this);
        return remoteConfig == null
                || remoteConfig.featureEnabled(RemoteConfigClient.Feature.OVERLAY);
    }

    private String overlayDiagnosticContext() {
        String foregroundPackage = "<none>";
        try {
            CurrentGameWindow window = currentGameWindow();
            if (window != null) {
                foregroundPackage = window.packageName();
            }
        } catch (RuntimeException ignored) {
            foregroundPackage = "<unavailable>";
        }
        String lastObservedPackage = recentPackage.isBlank() ? "<none>" : recentPackage;
        return "foregroundPackage=" + foregroundPackage
                + " recentPackage=" + lastObservedPackage
                + " lifecycle=" + overlayServiceLifecycle;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        overlayServiceLifecycle = "CONNECTED";
        connectedService = new WeakReference<>(this);
        if (settings != null) settings.setExpeditionSettingsChangeListener(null);
        settings = new SettingsStore(this);
        settings.setExpeditionSettingsChangeListener(() -> {
            long revision = settings.expeditionSettingsRevision();
            if (automationMode == AutomationMode.DISPATCH && revision != dispatchSettingsRevision
                    || pendingAutomationStartMode == AutomationMode.DISPATCH
                            && revision != pendingDispatchSettingsRevision) {
                requestGlobalStop("expedition-settings-changed");
            }
        });
        returnRewardWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ocrRuntime = new OcrRuntime(
                new OcrScanner(),
                new OcrRuntime.Scheduler() {
                    @Override
                    public Object schedule(long delayMillis, Runnable action) {
                        handler.postDelayed(action, delayMillis);
                        return action;
                    }

                    @Override
                    public void cancel(Object handle) {
                        if (handle instanceof Runnable action) {
                            handler.removeCallbacks(action);
                        }
                    }
                },
                getMainExecutor(),
                OCR_CALLBACK_TIMEOUT_MILLIS,
                this::logOcrTimelineIfNeeded);
        nectarTemplateMatcher = new NectarTemplateMatcher(this);
        expeditionTemplateMatcher = new ExpeditionTemplateMatcher(this);
        final long requestToken = ++remoteConfigRequestToken;
        RemoteConfigClient.fetch(this, remoteConfig -> {
            if (requestToken != remoteConfigRequestToken || connectedService.get() != this) {
                return;
            }
            if (remoteConfig == null) {
                return;
            }
            if (!remoteConfig.featureEnabled(RemoteConfigClient.Feature.OVERLAY)
                    && overlayHost != null) {
                applyOverlayVisibility(false);
            }
            if (running && remoteConfig.blocksAutomation(
                    BuildConfig.VERSION_CODE, featureFor(automationMode))) {
                pause(remoteConfig.message());
            }
        });
    }

    /** 記錄最近活動套件，讓掃描流程能判斷遊戲是否仍在前景。 */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        if (packageName != null) {
            recentPackage = packageName.toString();
            recentPackageAt = android.os.SystemClock.elapsedRealtime();
        }
    }

    /** 系統中斷服務時立即停止排程，避免背景點擊。 */
    @Override
    public void onInterrupt() {
        overlayServiceLifecycle = "INTERRUPTED";
        pause(getString(R.string.status_service_interrupted));
    }

    /** 服務銷毀時釋放 OCR、懸浮窗與弱引用。 */
    @Override
    public void onDestroy() {
        overlayServiceLifecycle = "DESTROYING";
        if (settings != null) settings.setExpeditionSettingsChangeListener(null);
        remoteConfigRequestToken++;
        pause(getString(R.string.status_service_closed));
        if (connectedService.get() == this) {
            connectedService.clear();
        }
        if (ocrRuntime != null) {
            ocrRuntime.close();
        }
        if (captureCoordinator != null) {
            captureCoordinator.stop();
        }
        frameAnalysisExecutor.close();
        if (overlayHost != null) {
            overlayHost.detach();
            overlayHost = null;
        }
        safeRemoveOverlayView(returnRewardAnchorOverlay, "return-reward-anchor");
        safeRemoveOverlayView(returnRewardRoiOverlay, "return-reward-roi");
        super.onDestroy();
    }

    /** 提供 Activity 查詢目前懸浮窗是否可見。 */
    static boolean isOverlayVisible() {
        PetalAccessibilityService service = connectedService.get();
        return service != null
                && service.overlayHost != null
                && service.overlayHost.isIconVisible();
    }

    /** Returns whether Android has connected the enabled accessibility service instance. */
    static boolean isConnected() {
        return connectedService.get() != null;
    }

    /** 提供 Activity 切換懸浮窗，服務未連線時回傳 false。 */
    static boolean setOverlayVisible(boolean visible) {
        PetalAccessibilityService service = connectedService.get();
        if (service == null) {
            return false;
        }
        // Retry creation when the service is valid but its first overlay add did not complete.
        if ((service.overlayHost == null || !service.overlayHost.isAttached())
                && !service.showOverlay()) {
            return false;
        }
        service.applyOverlayVisibility(visible);
        return true;
    }

    /** 套用懸浮窗呈現狀態；隱藏與顯示不改變工作流。 */
    private void applyOverlayVisibility(boolean visible) {
        boolean presentationVisible = visible && isOverlayFeatureEnabled();
        if (overlayHost != null) {
            overlayHost.setOverlayVisible(presentationVisible);
        }
        settings.setOverlayVisible(presentationVisible);
    }

    private void renderWorkflowStarted() {
        if (overlayHost != null) {
            overlayHost.renderWorkflow(new OverlayHost.WorkflowProjection(
                    overlayWorkflowFor(automationMode),
                    true,
                    true,
                    false));
            overlayHost.renderPrimaryAction();
            overlayHost.showIconForActiveWorkflow();
        }
    }

    private void renderWorkflowStopped() {
        if (overlayHost != null) {
            overlayHost.renderWorkflow(new OverlayHost.WorkflowProjection(
                    OverlayHost.Workflow.NONE, false, false, false));
            overlayHost.renderPrimaryAction();
            overlayHost.showIconForActiveWorkflow();
        }
    }

    private OverlayHost.Workflow overlayWorkflowFor(AutomationMode mode) {
        return switch (mode) {
            case PLANTING -> OverlayHost.Workflow.PLANTING;
            case FEED -> OverlayHost.Workflow.FEED;
            case POSTCARD -> OverlayHost.Workflow.POSTCARD;
            case DISPATCH -> OverlayHost.Workflow.DISPATCH;
            case RETURN_REWARD -> OverlayHost.Workflow.RETURN_REWARD;
            case NONE -> OverlayHost.Workflow.NONE;
        };
    }

    private void showOverlayNotice(String message) {
        if (overlayHost != null) {
            overlayHost.showNotice(message);
        }
    }

    private RemoteConfigClient.Feature featureFor(AutomationMode mode) {
        return switch (mode) {
            case PLANTING -> RemoteConfigClient.Feature.PLANTING;
            case FEED -> null;
            case POSTCARD -> RemoteConfigClient.Feature.POSTCARD;
            case DISPATCH -> RemoteConfigClient.Feature.DISPATCH;
            case RETURN_REWARD -> RemoteConfigClient.Feature.RETURN_REWARD;
            case NONE -> null;
        };
    }

    private int currentRemoteConfigVersion() {
        RemoteConfigClient.Status remoteConfig = RemoteConfigClient.cached(this);
        return remoteConfig == null ? 0 : remoteConfig.configVersion();
    }

    private boolean remoteConfigBlocksAutomation(RemoteConfigClient.Feature feature) {
        RemoteConfigClient.Status remoteConfig = RemoteConfigClient.cached(this);
        if (remoteConfig == null || !remoteConfig.blocksAutomation(BuildConfig.VERSION_CODE, feature)) {
            return false;
        }
        setStatus(remoteConfig.message());
        return true;
    }

    private boolean remoteConfigBlocksAutomation() {
        return remoteConfigBlocksAutomation(featureFor(automationMode));
    }

    /** 重設流程狀態並開始週期性截圖。 */
    private void startAutomation() {
        if (!admitWorkflowStart(AutomationMode.PLANTING)) {
            return;
        }
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.PLANTING)) {
            return;
        }
        if (settings.allowedFlowers().isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        runGeneration++;
        admissionDiagnostics.reset();
        workflowDiagnostics.reset();
        captureCoordinator().resetStats();
        actionGateway = null;
        ocrRuntime.diagnostics().reset(android.os.SystemClock.uptimeMillis());
        latestActionableOcrRequestSequence = 0L;
        running = true;
        automationMode = AutomationMode.PLANTING;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.PLANTING, 1, currentRemoteConfigVersion());
        busy = false;
        switchGuard.reset();
        resetPlantingSearch();
        resetPlantingNavigation();
        currentFlower = "";
        automationStep = AutomationStep.CHECKING_PLANTING_ENTRY;
        targetFlower = "";
        targetCount = 0;
        actionAttempts = 0;
        initialPlantingMenuConfirmed = false;
        startAfterSelection = false;
        selectionFromSearch = false;
        targetSelectionX = 0;
        targetSelectionY = 0;
        renderWorkflowStarted();
        setStatus(getString(R.string.status_planting_checking_entry));
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.overlay_planting_checking),
                getString(R.string.overlay_ocr_detail));
        schedule(200);
    }

    /** Service-owned single-workflow admission; the disabled Mushroom identity is not an owner. */
    private EnumSet<AutomationMode> activeWorkflowOwners() {
        EnumSet<AutomationMode> owners = EnumSet.noneOf(AutomationMode.class);
        if (running || automationMode != AutomationMode.NONE) {
            owners.add(automationMode);
        }
        if (pendingAutomationStartMode != AutomationMode.NONE) {
            owners.add(pendingAutomationStartMode);
        }
        return owners;
    }

    private boolean isAnyWorkflowActive() {
        return !activeWorkflowOwners().isEmpty();
    }

    private String whichWorkflowIsActive() {
        EnumSet<AutomationMode> owners = activeWorkflowOwners();
        return owners.isEmpty() ? AutomationMode.NONE.name() : owners.toString();
    }

    private boolean admitWorkflowStart(AutomationMode requestedMode) {
        EnumSet<AutomationMode> activeOwners = activeWorkflowOwners();
        if (activeOwners.isEmpty()) {
            return true;
        }
        if (activeOwners.size() > 1) {
            requestGlobalStop("multi-active-start-" + requestedMode);
        } else {
            String message = getString(R.string.status_workflow_start_blocked);
            Log.i(TAG, "WORKFLOW_START_REJECTED requested=" + requestedMode
                    + " active=" + whichWorkflowIsActive());
            setStatus(message);
            showOverlayNotice(message);
        }
        return false;
    }

    private void requestGlobalStop(String reason) {
        EnumSet<AutomationMode> activeOwners = activeWorkflowOwners();
        if (activeOwners.isEmpty()) {
            return;
        }
        String safeReason = reason == null || reason.isBlank() ? "unspecified" : reason;
        Log.i(TAG, "GLOBAL_WORKFLOW_STOP active=" + activeOwners + " reason=" + safeReason);
        pause(getString(R.string.status_paused));
        if (!isAnyWorkflowActive()) {
            Log.i(TAG, "GLOBAL_WORKFLOW_STOP_COMPLETE reason=" + safeReason);
        } else {
            Log.e(TAG, "GLOBAL_WORKFLOW_STOP_INCOMPLETE active="
                    + whichWorkflowIsActive() + " reason=" + safeReason);
        }
    }

    /** 啟動花瓣生產；先處理目前隊伍，三擊哨子只計入後續換隊次數。 */
    private void startFeedAutomation(FeedSettingsInput input) {
        if (!admitWorkflowStart(AutomationMode.FEED)) {
            return;
        }
        if (remoteConfigBlocksAutomation(null)) {
            return;
        }
        List<String> sequence = List.copyOf(settings.allowedFlowers());
        if (sequence.isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        runGeneration++;
        admissionDiagnostics.reset();
        workflowDiagnostics.reset();
        captureCoordinator().resetStats();
        actionGateway = null;
        ocrRuntime.diagnostics().reset(android.os.SystemClock.uptimeMillis());
        latestActionableOcrRequestSequence = 0L;
        running = true;
        busy = false;
        automationMode = AutomationMode.FEED;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.FEED, input.feedsPerSquad(),
                currentRemoteConfigVersion());
        feedSettings = input;
        feedStep = FeedStep.WAITING_GAME_READY;
        feedFlowerSequence = sequence;
        feedFlowerSequenceIndex = 0;
        feedTargetFlower = feedFlowerSequence.get(feedFlowerSequenceIndex);
        feedRequireNectarSearch = true;
        feedRound = 0;
        feedAttemptCount = 0;
        feedSquadSwitchCount = 0;
        feedTargetMissingFrames = 0;
        feedReadyMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchMissingFrames = 0;
        feedReadyStability.reset();
        feedSearchActionAttempts = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.NONE;
        feedSearchKeyboardGuard.reset();
        feedNectarCandidate = null;
        feedSelectedNectar = null;
        feedNectarFreshFrameRequired = false;
        feedNectarCandidateFrames = 0;
        feedNectarTapAttempts = 0;
        feedPanelCloseWaitFrames = 0;
        feedPanelClosedConfirmationFrames = 0;
        feedDetailCloseAttempts = 0;
        feedNectarBeforeRound = -1;
        feedCollectAfterCount = false;
        feedNoEffectStartedAt = 0L;
        feedZoomReady = false;
        feedCollectedPetals = 0;
        feedCollectReturningFromDetail = false;
        feedCollectReturningFromShare = false;
        feedCollectReturnFrames = 0;
        feedCollectionOnlyRecovery = false;
        resetFeedSpiralState();
        resetFeedHoldState();
        renderWorkflowStarted();
        setStatus(getString(R.string.status_feed_started));
        setRunStatus(
                AutomationMode.FEED,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_feed_started),
                getString(R.string.overlay_feed_progress, 0, input.feedsPerSquad(), 0,
                        input.maxSquadSwitches()));
        schedule(200L);
    }

    /** 啟動獨立的明信片 OCR 狀態機，與種花流程互斥。 */
    private void startPostcardAutomation(
            int collectionLimit,
            String petalPotName,
            int pikminCount) {
        if (!admitWorkflowStart(AutomationMode.POSTCARD)) {
            return;
        }
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.POSTCARD)) {
            return;
        }
        runGeneration++;
        admissionDiagnostics.reset();
        workflowDiagnostics.reset();
        captureCoordinator().resetStats();
        actionGateway = null;
        ocrRuntime.diagnostics().reset(android.os.SystemClock.uptimeMillis());
        latestActionableOcrRequestSequence = 0L;
        running = true;
        busy = false;
        automationMode = AutomationMode.POSTCARD;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.POSTCARD, collectionLimit,
                currentRemoteConfigVersion());
        postcardAutomation.start(collectionLimit, petalPotName, pikminCount);
        postcardReturnGuard.reset();
        postcardUnknownFrames = 0;
        postcardMissingControlFrames = 0;
        postcardReceiptWaitFrames = 0;
        postcardBackAttempts = 0;
        postcardReturnFlowerTapped = false;
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        postcardPikminCountConfirmations = 0;
        postcardLastPikminCount = -1;
        renderWorkflowStarted();
        setPostcardStatus(getString(
                R.string.status_postcard_progress,
                postcardAutomation.completedCount(),
                postcardAutomation.collectionLimit()));
        schedule(200);
    }

    /** 啟動  的派遣頁面順序，但所有 OCR、像素與前景判斷都由 PikminX 執行。 */
    private void startExpeditionDispatch(
            ExpeditionTargetMode targetMode,
            DispatchSelectionMethod selectionMethod,
            DispatchPikminType pikminType) {
        if (!admitWorkflowStart(AutomationMode.DISPATCH)) {
            return;
        }
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.DISPATCH)) {
            return;
        }
        if (activeGameBoundsStrict() == null) {
            showOverlayNotice(getString(R.string.status_reward_wrong_page));
            return;
        }
        runGeneration++;
        admissionDiagnostics.reset();
        workflowDiagnostics.reset();
        captureCoordinator().resetStats();
        actionGateway = null;
        ocrRuntime.diagnostics().reset(android.os.SystemClock.uptimeMillis());
        latestActionableOcrRequestSequence = 0L;
        running = true;
        busy = false;
        automationMode = AutomationMode.DISPATCH;
        dispatchSettingsRevision = settings.expeditionSettingsRevision();
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.DISPATCH, 0, currentRemoteConfigVersion());
        expeditionTargetMode = targetMode == null
                ? ExpeditionTargetMode.FRUIT_AND_POT : targetMode;
        dispatchSelectionMethod = selectionMethod == null
                ? DispatchSelectionMethod.AUTO : selectionMethod;
        dispatchPikminType = pikminType == null ? DispatchPikminType.MIXED : pikminType;
        if (dispatchVisionCancellation != null) dispatchVisionCancellation.set(true);
        dispatchVisionCancellation = null;
        dispatchInspectedVisual.clear();
        if (expeditionTemplateMatcher != null) expeditionTemplateMatcher.clearObservationCache();
        expeditionRecognitionConsensus.reset();
        dispatchCurrentItemKind = null;
        expeditionDispatchSession = new ExpeditionDispatchSession(android.os.SystemClock.elapsedRealtime());
        dispatchColorSelected = false;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchResultMissingFrames = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchSelectionTargetCount = 0;
        dispatchSelectionBeforeCount = -1;
        dispatchSelectionGesturePending = false;
        dispatchReturnRevealPending = false;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoAnchorMissingFrames = 0;
        dispatchUnknownFrames = 0;
        renderWorkflowStarted();
        setStatus(getString(R.string.status_reward_started));
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_reward_started),
                getString(R.string.status_reward_scanning));
        schedule(DISPATCH_SCAN_DELAY_MILLIS);
    }

    /** 啟動獨立的回程收取循環；不增加或扣除現有派遣次數。 */
    private void startReturnRewardCollection(
            boolean receivePostcard, boolean continueOnNectarWarning) {
        if (!admitWorkflowStart(AutomationMode.RETURN_REWARD)) {
            return;
        }
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.RETURN_REWARD)) {
            return;
        }
        Rect gameBounds = activeGameBoundsStrict();
        if (gameBounds == null) {
            showOverlayNotice(getString(R.string.status_return_reward_left_game));
            return;
        }
        runGeneration++;
        admissionDiagnostics.reset();
        workflowDiagnostics.reset();
        captureCoordinator().resetStats();
        actionGateway = null;
        ocrRuntime.diagnostics().reset(android.os.SystemClock.uptimeMillis());
        latestActionableOcrRequestSequence = 0L;
        running = true;
        busy = false;
        automationMode = AutomationMode.RETURN_REWARD;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.RETURN_REWARD, 1,
                currentRemoteConfigVersion());
        returnRewardScanGuard.reset();
        clearReturnRewardRoiOverlays();
        returnRewardRoi.reset();
        returnRewardStartedAt = 0L;
        returnRewardLastTapAt = 0L;
        returnRewardReceivePostcard = receivePostcard;
        returnRewardContinueOnNectarWarning = continueOnNectarWarning;
        returnRewardNectarWarningFrames = 0;
        returnRewardNectarWarningActive = false;
        resetReturnRewardPostcard();
        renderWorkflowStarted();
        setStatus(getString(R.string.status_return_reward_select_roi));
        setRunStatus(
                AutomationMode.RETURN_REWARD,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_return_reward_select_roi),
                getString(R.string.overlay_return_reward_safety));
        showReturnRewardAnchorOverlay(captureBounds(gameBounds));
    }

    /** Blocks reward scanning until the user's first post-start tap defines the ROI. */
    private void showReturnRewardAnchorOverlay(CaptureGeometry.Bounds gameBounds) {
        if (returnRewardWindowManager == null) {
            stopWithError(getString(R.string.status_return_reward_roi_failed));
            return;
        }
        View capture = new View(this);
        capture.setBackgroundColor(Color.TRANSPARENT);
        capture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        capture.setOnTouchListener(new ReturnRewardAnchorTouchListener());
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                gameBounds.width(),
                gameBounds.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = gameBounds.left();
        params.y = gameBounds.top();
        if (!safeAddOverlayView(capture, params, "return-reward-anchor")) {
            stopWithError(getString(R.string.status_return_reward_roi_failed));
            return;
        }
        returnRewardAnchorOverlay = capture;
        handler.postDelayed(returnRewardAnchorGuardTask, 500L);
    }

    private void guardReturnRewardAnchor() {
        if (!running
                || automationMode != AutomationMode.RETURN_REWARD
                || returnRewardRoi.isArmed()) {
            return;
        }
        if (activeGameBoundsStrict() == null || !isGameForeground()) {
            stopWithError(getString(R.string.status_return_reward_left_game));
            return;
        }
        handler.postDelayed(returnRewardAnchorGuardTask, 500L);
    }

    private void armReturnRewardRoi(int screenX, int screenY) {
        if (!running
                || automationMode != AutomationMode.RETURN_REWARD
                || returnRewardRoi.isArmed()) {
            return;
        }
        Rect activeBounds = activeGameBoundsStrict();
        if (activeBounds == null
                || !returnRewardRoi.armFromScreenTap(
                        screenX, screenY, captureBounds(activeBounds))) {
            return;
        }
        handler.removeCallbacks(returnRewardAnchorGuardTask);
        safeRemoveOverlayView(returnRewardAnchorOverlay, "return-reward-anchor");
        returnRewardAnchorOverlay = null;
        showReturnRewardRoiOverlay();
        if (returnRewardRoiOverlay == null) {
            return;
        }
        returnRewardStartedAt = android.os.SystemClock.elapsedRealtime();
        returnRewardLastTapAt = 0L;
        returnRewardScanGuard.reset();
        setStatus(getString(R.string.status_return_reward_roi_selected));
        setRunStatus(
                AutomationMode.RETURN_REWARD,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_return_reward_roi_selected),
                getString(R.string.overlay_return_reward_safety));
        schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
    }

    private void showReturnRewardRoiOverlay() {
        CaptureGeometry.Bounds bounds = returnRewardRoi.screenBounds();
        if (bounds == null || returnRewardWindowManager == null) {
            stopWithError(getString(R.string.status_return_reward_roi_failed));
            return;
        }
        ReturnRewardRoiView frame = new ReturnRewardRoiView();
        frame.setContentDescription(getString(R.string.status_return_reward_roi_selected));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bounds.left();
        params.y = bounds.top();
        if (!safeAddOverlayView(frame, params, "return-reward-roi")) {
            stopWithError(getString(R.string.status_return_reward_roi_failed));
            return;
        }
        returnRewardRoiOverlay = frame;
    }

    private void hideReturnRewardRoiForCapture() {
        if (returnRewardRoiOverlay != null
                && returnRewardRoiOverlay.getVisibility() == View.VISIBLE) {
            returnRewardRoiOverlay.setVisibility(View.INVISIBLE);
            returnRewardRoiHiddenForCapture = true;
        }
    }

    private void restoreReturnRewardRoiAfterCapture() {
        if (!returnRewardRoiHiddenForCapture) {
            return;
        }
        returnRewardRoiHiddenForCapture = false;
        if (running
                && automationMode == AutomationMode.RETURN_REWARD
                && returnRewardRoiOverlay != null) {
            returnRewardRoiOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void clearReturnRewardRoiOverlays() {
        handler.removeCallbacks(returnRewardAnchorGuardTask);
        safeRemoveOverlayView(returnRewardAnchorOverlay, "return-reward-anchor");
        safeRemoveOverlayView(returnRewardRoiOverlay, "return-reward-roi");
        returnRewardAnchorOverlay = null;
        returnRewardRoiOverlay = null;
        returnRewardRoiHiddenForCapture = false;
    }

    /** Requests a new capture context between long gesture stages without another OCR pass. */
    private void requestFreshCaptureOnly(
            String profile, Runnable action, Runnable failed) {
        if (!running) {
            if (failed != null) {
                failed.run();
            }
            return;
        }
        if (pendingCaptureOnlyAction != null) {
            return;
        }
        pendingCaptureOnlyAction = new PendingCaptureOnlyAction(
                runGeneration, profile == null ? "CAPTURE_ONLY" : profile, action, failed);
        busy = false;
        schedule(0L);
    }

    /** 排程回呼：只在遊戲前景且沒有其他掃描時擷取畫面。 */
    private void requestScan() {
        if (activeWorkflowOwners().size() > 1) {
            requestGlobalStop("multi-active-scan-callback");
            return;
        }
        if (!running || busy) {
            return;
        }
        if (automationMode == AutomationMode.RETURN_REWARD && !returnRewardRoi.isArmed()) {
            return;
        }
        Rect strictBounds = activeGameBoundsStrict();
        if ((automationMode == AutomationMode.FEED
                || automationMode == AutomationMode.DISPATCH
                || automationMode == AutomationMode.RETURN_REWARD)
                && strictBounds == null) {
            if (automationMode == AutomationMode.FEED
                    && feedStep == FeedStep.WAITING_GAME_READY
                    && ++feedReadyMissingFrames < FEED_MAX_TARGET_MISSING_FRAMES) {
                statusFeed(getString(
                        R.string.status_feed_waiting_ready,
                        feedReadyMissingFrames,
                        FEED_MAX_TARGET_MISSING_FRAMES));
                schedule(FEED_OCR_RETRY_MILLIS);
                return;
            }
            int message = automationMode == AutomationMode.RETURN_REWARD
                    ? R.string.status_return_reward_left_game
                    : automationMode == AutomationMode.FEED
                            ? R.string.status_feed_wrong_page
                            : R.string.status_reward_left_game;
            stopWithError(getString(message));
            return;
        }
        if (!isGameForeground()) {
            setStatus(getString(R.string.status_waiting_game));
            setRunStatus(
                    automationMode,
                    OverlayRunStatus.Kind.IDLE,
                    getString(R.string.status_waiting_game),
                    getString(R.string.overlay_waiting_game_detail));
            schedule(1500);
            return;
        }
        busy = true;
        if (automationMode == AutomationMode.RETURN_REWARD) {
            takeGameScreenshot(runGeneration);
            return;
        }
        takeGameScreenshot(runGeneration);
    }

    /** Enqueues one immutable capture transaction with request-time identity. */
    private void takeGameScreenshot(long generation) {
        List<ScreenshotOverlayMask.Region> overlayRegions = List.of();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        workflowDiagnostics.recordCount(WorkflowDiagnostics.Counter.SCREENSHOT_REQUESTS);
        Rect gameBounds = null;
        int gameWindowId = -1;
        if (root != null && GAME_PACKAGE.contentEquals(root.getPackageName())) {
            gameBounds = new Rect();
            root.getBoundsInScreen(gameBounds);
            if (gameBounds.isEmpty()) {
                gameBounds.set(
                        0,
                        0,
                        getResources().getDisplayMetrics().widthPixels,
                        getResources().getDisplayMetrics().heightPixels);
            }
            gameWindowId = root.getWindowId();
        }
        boolean windowCapture = android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && gameBounds != null
                && gameWindowId >= 0;
        CaptureCoordinator.Presentation presentation = automationMode == AutomationMode.RETURN_REWARD
                        ? CaptureCoordinator.Presentation.RETURN_REWARD
                        : CaptureCoordinator.Presentation.NONE;
        CaptureCoordinator.Request<Void> request = new CaptureCoordinator.Request<>(
                null,
                windowCapture ? List.of() : overlayRegions,
                generation,
                windowCapture ? CaptureGeometry.Mode.WINDOW : CaptureGeometry.Mode.DISPLAY,
                windowCapture
                        ? captureBounds(gameBounds)
                        : new CaptureGeometry.Bounds(
                                0,
                                0,
                                getResources().getDisplayMetrics().widthPixels,
                                getResources().getDisplayMetrics().heightPixels),
                gameBounds == null ? null : captureBounds(gameBounds),
                Display.DEFAULT_DISPLAY,
                gameWindowId,
                0L,
                actionAdmissionEpoch,
                presentation,
                presentation == CaptureCoordinator.Presentation.RETURN_REWARD
                        ? RETURN_REWARD_FRAME_HIDE_DELAY_MILLIS : 0L);
        CaptureCoordinator<Void, Bitmap> coordinator = captureCoordinator();
        coordinator.enqueue(request);
        CaptureCoordinator.Snapshot snapshot = coordinator.snapshot();
        ocrRuntime.diagnostics().recordScreenshot(
                android.os.SystemClock.uptimeMillis(), snapshot.depth(), snapshot.highWater());
    }

    private CaptureCoordinator<Void, Bitmap> captureCoordinator() {
        if (captureCoordinator != null && !captureCoordinator.isStopped()) {
            return captureCoordinator;
        }
        captureCoordinator = new CaptureCoordinator<>(
                new CaptureCoordinator.Platform<>() {
                    @Override
                    public Object prepareOverlay(CaptureCoordinator.Request<Void> request) {
                        return switch (request.presentation()) {
                            case RETURN_REWARD -> {
                                hideReturnRewardRoiForCapture();
                                yield CaptureCoordinator.Presentation.RETURN_REWARD;
                            }
                            case NONE -> CaptureCoordinator.Presentation.NONE;
                        };
                    }

                    @Override
                    public void restoreOverlay(
                            CaptureCoordinator.Request<Void> request, Object token) {
                        switch (request.presentation()) {
                            case RETURN_REWARD -> restoreReturnRewardRoiAfterCapture();
                            case NONE -> { }
                        }
                    }

                    @Override
                    public void dispatch(
                            CaptureCoordinator.Request<Void> request,
                            CaptureCoordinator.Callback<Bitmap> callback) {
                        TakeScreenshotCallback platformCallback = new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult result) {
                                long callbackStartedAtUptimeMillis =
                                        android.os.SystemClock.uptimeMillis();
                                Bitmap bitmap = null;
                                try {
                                    long bitmapCopyStartedAtUptimeMillis =
                                            android.os.SystemClock.uptimeMillis();
                                    try {
                                        bitmap = copyBitmap(result);
                                    } catch (RuntimeException error) {
                                        callback.failure(CaptureCoordinator.FailureKind.COPY, -1);
                                        return;
                                    } finally {
                                        workflowDiagnostics.recordDuration(
                                                WorkflowDiagnostics.Metric.BITMAP_COPY,
                                                android.os.SystemClock.uptimeMillis()
                                                        - bitmapCopyStartedAtUptimeMillis);
                                    }
                                    if (bitmap == null) {
                                        callback.failure(CaptureCoordinator.FailureKind.COPY, -1);
                                        return;
                                    }
                                    boolean accepted = callback.success(
                                            request.captureSequence(),
                                            request.generation(),
                                            request.admissionEpoch(),
                                            new CaptureCoordinator.Frame<>(
                                                    bitmap,
                                                    bitmap.getWidth(),
                                                    bitmap.getHeight(),
                                                    result.getTimestamp()));
                                    if (!accepted) {
                                        bitmap.recycle();
                                    }
                                } finally {
                                    workflowDiagnostics.recordDuration(
                                            WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK,
                                            android.os.SystemClock.uptimeMillis()
                                                    - callbackStartedAtUptimeMillis);
                                }
                            }

                            @Override
                            public void onFailure(int errorCode) {
                                long callbackStartedAtUptimeMillis =
                                        android.os.SystemClock.uptimeMillis();
                                try {
                                    callback.failure(
                                            isTransientScreenshotFailure(errorCode)
                                                    ? CaptureCoordinator.FailureKind.TRANSIENT
                                                    : CaptureCoordinator.FailureKind.PERMANENT,
                                            errorCode);
                                } finally {
                                    workflowDiagnostics.recordDuration(
                                            WorkflowDiagnostics.Metric.SCREENSHOT_CALLBACK,
                                            android.os.SystemClock.uptimeMillis()
                                                    - callbackStartedAtUptimeMillis);
                                }
                            }
                        };
                        if (android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                                && request.mode() == CaptureGeometry.Mode.WINDOW) {
                            takeScreenshotOfWindow(
                                    request.windowId(), getMainExecutor(), platformCallback);
                        } else {
                            takeScreenshot(request.displayId(), getMainExecutor(), platformCallback);
                        }
                    }

                    @Override
                    public Object schedule(long delayMillis, Runnable action) {
                        handler.postDelayed(action, delayMillis);
                        return action;
                    }

                    @Override
                    public void cancel(Object handle) {
                        if (handle instanceof Runnable action) {
                            handler.removeCallbacks(action);
                        }
                    }
                },
                new CaptureCoordinator.Listener<>() {
                    @Override
                    public void completed(CaptureCoordinator.Outcome<Void, Bitmap> outcome) {
                        CaptureCoordinator.Snapshot snapshot = captureCoordinator.snapshot();
                        ocrRuntime.diagnostics().recordScreenshotQueueState(
                                snapshot.depth(), snapshot.highWater());
                        handleCaptureOutcome(outcome);
                    }

                    @Override
                    public void retrying(
                            CaptureCoordinator.Request<Void> request,
                            CaptureCoordinator.FailureKind failure,
                            int errorCode,
                            long delayMillis) {
                        handleCaptureRetry(request, failure, errorCode, delayMillis);
                    }
                },
                SCREENSHOT_CALLBACK_TIMEOUT_MILLIS);
        return captureCoordinator;
    }

    private void clearScreenshotPipeline() {
        if (captureCoordinator != null) {
            captureCoordinator.clear();
            CaptureCoordinator.Snapshot snapshot = captureCoordinator.snapshot();
            ocrRuntime.diagnostics().recordScreenshotQueueState(
                    snapshot.depth(), snapshot.highWater());
        }
        restoreReturnRewardRoiAfterCapture();
    }

    private void handleCaptureOutcome(CaptureCoordinator.Outcome<Void, Bitmap> outcome) {
        if (outcome == null || outcome.request() == null) {
            return;
        }
        CaptureCoordinator.Request<Void> request = outcome.request();
        if (outcome.successful()) {
            Bitmap bitmap = outcome.frame().value();
            if (!isActiveRun(request.generation())) {
                bitmap.recycle();
                return;
            }
            handleCapturedBitmap(request, outcome.geometry(), bitmap);
            return;
        }
        if (outcome.failure() == CaptureCoordinator.FailureKind.CANCELLED
                || outcome.retried()
                || !isActiveRun(request.generation())) {
            return;
        }
        String message = outcome.failure() == CaptureCoordinator.FailureKind.COPY
                ? getString(R.string.status_copy_failed)
                : getString(R.string.status_capture_failed, outcome.errorCode());
        screenshotFailed(message, request.generation());
    }

    private void handleCaptureRetry(
            CaptureCoordinator.Request<Void> request,
            CaptureCoordinator.FailureKind failure,
            int errorCode,
            long delayMillis) {
        if (!isActiveRun(request.generation())) {
            return;
        }
        busy = false;
        setStatus(failure == CaptureCoordinator.FailureKind.COPY
                ? getString(R.string.status_copy_failed)
                : getString(R.string.status_capture_failed, errorCode));
        schedule(delayMillis);
    }

    /** Keeps the existing OCR/workflow handoff as the R2 compatibility adapter. */
    private void handleCapturedBitmap(
            CaptureCoordinator.Request<Void> request,
            CaptureGeometry captureGeometry,
            Bitmap bitmap) {
        long generation = request.generation();
        maskOverlayRegions(bitmap, request.overlayRegions());
        PendingCaptureOnlyAction pending = pendingCaptureOnlyAction;
        if (pending != null) {
            pendingCaptureOnlyAction = null;
            if (pending.generation() != generation) {
                bitmap.recycle();
                return;
            }
            busy = false;
            try {
                ActionAdmission.FrameContext context = actionContextForCapture(
                        generation, captureGeometry, request.admissionEpoch());
                ActionAdmission.Decision decision = evaluateActionAdmission(context);
                AdmissionDiagnostics.Event diagnostic = admissionDiagnostics.recordCaptureOnly(
                        pending.profile(),
                        context,
                        android.os.SystemClock.uptimeMillis(),
                        decision);
                workflowDiagnostics.recordAdmission(decision);
                workflowDiagnostics.recordSinceCapture(
                        WorkflowDiagnostics.Metric.CAPTURE_TO_ADMISSION,
                        context,
                        android.os.SystemClock.uptimeMillis());
                logAdmissionDiagnosticIfNeeded(diagnostic, decision);
                if (!decision.allowed()) {
                    dropStaleAction();
                    if (pending.failed() != null) {
                        pending.failed().run();
                    }
                } else {
                    runWithActionContext(context, captureGeometry, pending.action());
                }
            } catch (RuntimeException error) {
                Log.e(TAG, "Capture-only action failed", error);
                if (pending.failed() != null && isActiveRun(generation)) {
                    pending.failed().run();
                }
            } finally {
                bitmap.recycle();
            }
            return;
        }
        // Keyboard-only routes remain outside OCR ownership but retain the request-time epoch.
        if (automationMode == AutomationMode.POSTCARD
                && postcardAutomation.step() == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
            busy = false;
            try {
                runWithActionContext(
                        actionContextForCapture(
                                generation, captureGeometry, request.admissionEpoch()),
                        captureGeometry,
                        () -> closePostcardKeyboard(bitmap));
            } finally {
                bitmap.recycle();
            }
            return;
        }
        if (automationMode == AutomationMode.PLANTING
                && automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
            busy = false;
            try {
                runWithActionContext(
                        actionContextForCapture(
                                generation, captureGeometry, request.admissionEpoch()),
                        captureGeometry,
                        () -> closePlantingSearchKeyboard(bitmap));
            } finally {
                bitmap.recycle();
            }
            return;
        }
        if (automationMode == AutomationMode.FEED
                && feedStep == FeedStep.CLOSING_NECTAR_KEYBOARD) {
            busy = false;
            try {
                runWithActionContext(
                        actionContextForCapture(
                                generation, captureGeometry, request.admissionEpoch()),
                        captureGeometry,
                        () -> closeFeedSearchKeyboard(bitmap));
            } finally {
                bitmap.recycle();
            }
            return;
        }
        if (automationMode == AutomationMode.DISPATCH && expeditionDispatchSession != null
                && expeditionDispatchSession.stage() == ExpeditionDispatchSession.Stage.LIST_SEARCH
                && !expeditionDispatchSession.transitionPending()) {
            scanDispatchVisualCandidates(bitmap, captureGeometry, generation, request.admissionEpoch());
            return;
        }
        OcrScan.Profile profile = ocrProfileForCurrentStep();
        OcrFailureConsumer fullFailure = error -> {
            recordOcrDiagnosticFailure("full", profile, captureGeometry, error);
            scanFailed(getString(R.string.status_ocr_failed), generation);
        };
        startOcrTransaction(
                bitmap,
                profile,
                captureGeometry,
                request.admissionEpoch(),
                "full",
                true,
                (ocrTransaction, frame) -> {
                    recordOcrDiagnostic("full", frame);
                    // Focused OCR copies this bitmap synchronously before this callback returns.
                    runWithActionContext(
                            ocrTransaction,
                            frame,
                            () -> handleTokens(ocrTransaction, frame, bitmap, fullFailure));
                },
                fullFailure);
    }

    private OcrScan.Profile ocrProfileForCurrentStep() {
        if (automationMode == AutomationMode.FEED) {
            return OcrScan.Profile.FULL_CHINESE;
        }
        if (automationMode == AutomationMode.RETURN_REWARD) {
            return OcrScan.Profile.FULL_CHINESE;
        }
        if (automationMode == AutomationMode.DISPATCH) {
            return OcrScan.Profile.FULL_CHINESE;
        }
        if (automationMode == AutomationMode.PLANTING) {
            boolean chineseOnly = automationStep == AutomationStep.MONITORING
                    || automationStep == AutomationStep.REVEALING_SEARCH_PANEL
                    || automationStep == AutomationStep.CHECKING_PLANTING_ENTRY
                    || automationStep == AutomationStep.WAITING_INITIAL_PLANTING_MENU
                    || automationStep == AutomationStep.WAITING_MENU_AFTER_START
                    || automationStep == AutomationStep.OPENING_SEARCH
                    || automationStep == AutomationStep.CLEARING_SEARCH
                    || automationStep == AutomationStep.ENTERING_SEARCH
                    || automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD
                    || automationStep == AutomationStep.SELECTING_SEARCH_RESULT
                    || automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION
                    || automationStep == AutomationStep.WAITING_START
                    || automationStep == AutomationStep.VERIFYING_START;
            if (automationStep == AutomationStep.SELECTING_SEARCH_RESULT) {
                return OcrScan.Profile.PLANTING_SEARCH_RESULTS;
            }
            return chineseOnly
                    ? OcrScan.Profile.FULL_CHINESE
                    : OcrScan.Profile.FULL_MULTILINGUAL;
        }
        if (automationMode != AutomationMode.POSTCARD) {
            return OcrScan.Profile.FULL_MULTILINGUAL;
        }
        boolean chineseOnly = switch (postcardAutomation.step()) {
            case OPEN_PETAL_SEARCH,
                    ENTER_PETAL_SEARCH,
                    CLOSE_PETAL_KEYBOARD,
                    SELECT_PETAL,
                    TAP_NEXT,
                    GO,
                    RECEIVE,
                    WAIT_RECEIPT_EXIT -> true;
            default -> false;
        };
        return chineseOnly
                ? OcrScan.Profile.FULL_CHINESE
                : OcrScan.Profile.FULL_MULTILINGUAL;
    }

    private void recordOcrDiagnostic(String source, OcrScan.Frame frame) {
        if (usageSession != null) {
            usageSession.recordOcr(source, ocrDiagnosticStage(), frame);
        }
    }

    private void recordOcrDiagnosticFailure(
            String source,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Exception error) {
        if (usageSession != null) {
            usageSession.recordOcrFailure(
                    source, ocrDiagnosticStage(), profile,
                    captureGeometry, error);
        }
        if (automationMode == AutomationMode.PLANTING) {
            String detail = error == null
                    ? "unknown"
                    : error.getClass().getSimpleName() + ":"
                            + String.valueOf(error.getMessage())
                                    .replace('\n', ' ').replace('\r', ' ');
            Log.i(TAG, "PLANTING_OCR event=engine-failure"
                    + " stage=" + automationStep.name()
                    + " source=" + source
                    + " profile=" + profile.name()
                    + " error=" + detail);
        }
        if (isPlantingEntryStep()) {
            logPlantingEntryFailure(profile, error);
        }
    }

    private void recordOcrAdmissionDiagnostic(
            OcrScan.Frame frame,
            ActionAdmission.FrameContext context,
            long ocrCompletedAtUptimeMillis,
            long admissionAtUptimeMillis,
            ActionAdmission.Decision decision) {
        AdmissionDiagnostics.Event diagnostic = admissionDiagnostics.recordOcr(
                frame.profile().name(),
                context,
                ocrCompletedAtUptimeMillis,
                admissionAtUptimeMillis,
                decision);
        workflowDiagnostics.recordAdmission(decision);
        workflowDiagnostics.recordSinceCapture(
                WorkflowDiagnostics.Metric.CAPTURE_TO_OCR,
                context,
                ocrCompletedAtUptimeMillis);
        workflowDiagnostics.recordSinceCapture(
                WorkflowDiagnostics.Metric.CAPTURE_TO_ADMISSION,
                context,
                admissionAtUptimeMillis);
        if (frame != null) {
            ocrRuntime.diagnostics().markAdmission(frame.transactionId());
        }
        logAdmissionDiagnosticIfNeeded(diagnostic, decision);
    }

    private void recordActionAdmissionDiagnostic(
            ActionAdmission.FrameContext context,
            ActionAdmission.Decision decision) {
        AdmissionDiagnostics.Event diagnostic = admissionDiagnostics.recordActionCheck(
                ocrDiagnosticStage(),
                context,
                android.os.SystemClock.uptimeMillis(),
                decision);
        workflowDiagnostics.recordAdmission(decision);
        workflowDiagnostics.recordSinceCapture(
                WorkflowDiagnostics.Metric.CAPTURE_TO_ADMISSION,
                context,
                android.os.SystemClock.uptimeMillis());
        logAdmissionDiagnosticIfNeeded(diagnostic, decision);
    }

    private void logAdmissionDiagnosticIfNeeded(
            AdmissionDiagnostics.Event diagnostic,
            ActionAdmission.Decision decision) {
        if (diagnostic == null || !diagnostic.shouldLog()) {
            return;
        }
        Log.i(TAG, "ACTION_ADMISSION reason="
                + (decision == null ? ActionAdmission.RejectionReason.INVALID_CONTEXT
                        : decision.reason())
                + " " + admissionDiagnostics.summary());
    }

    private void logOcrTimelineIfNeeded(OcrRuntimeDiagnostics.Completion completion) {
        if (completion == null || !completion.shouldLogTimeline()) {
            return;
        }
        Log.i(TAG, "OCR_TIMELINE totalMs=" + completion.totalLatencyMillis()
                + " " + completion.compactTimeline());
    }

    private String ocrDiagnosticStage() {
        return switch (automationMode) {
            case PLANTING -> automationStep.name();
            case FEED -> feedStep.name();
            case POSTCARD -> postcardAutomation.step().name();
            case DISPATCH -> expeditionDispatchSession == null
                    ? "START" : expeditionDispatchSession.stage().name();
            case RETURN_REWARD -> "RETURN_REWARD";
            case NONE -> "NONE";
        };
    }


    private static CaptureGeometry.Bounds captureBounds(Rect bounds) {
        return new CaptureGeometry.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    private ActionAdmission.FrameContext actionContextForFrame(OcrScan.Frame frame) {
        return actionContextForFrame(frame, frame.admissionEpoch());
    }

    private ActionAdmission.FrameContext actionContextForFrame(
            OcrScan.Frame frame, long admissionEpoch) {
        CaptureGeometry geometry = frame.captureGeometry();
        OcrScan.TransactionId transaction = frame.transactionId();
        return new ActionAdmission.FrameContext(
                transaction.runGeneration(),
                geometry.captureSequence(),
                transaction.ocrRequestSequence(),
                geometry.capturedAtUptimeMillis(),
                GAME_PACKAGE,
                geometry.targetWindowBoundsOnScreen(),
                geometry.windowId(),
                admissionEpoch);
    }

    private ActionAdmission.FrameContext actionContextForCapture(
            long generation, CaptureGeometry geometry) {
        return actionContextForCapture(generation, geometry, actionAdmissionEpoch);
    }

    private ActionAdmission.FrameContext actionContextForCapture(
            long generation, CaptureGeometry geometry, long admissionEpoch) {
        return new ActionAdmission.FrameContext(
                generation,
                geometry.captureSequence(),
                0L,
                geometry.capturedAtUptimeMillis(),
                GAME_PACKAGE,
                geometry.targetWindowBoundsOnScreen(),
                geometry.windowId(),
                admissionEpoch);
    }

    private void runWithActionContext(OcrScan.Frame frame, Runnable action) {
        ActionAdmission.FrameContext context = actionContextForFrame(frame);
        runWithActionContext(context, frame.captureGeometry(), action);
    }

    private void runWithActionContext(
            OcrRuntime.Transaction transaction, OcrScan.Frame frame, Runnable action) {
        ActionAdmission.FrameContext context = actionContextForFrame(frame);
        runWithActionContext(context, frame.captureGeometry(), action);
    }

    private void runWithActionContext(
            ActionAdmission.FrameContext context,
            CaptureGeometry geometry,
            Runnable action) {
        ActionAdmission.FrameContext previous = handlingActionContext.get();
        handlingActionContext.set(context);
        try {
            runWithCaptureGeometry(geometry, action);
        } finally {
            if (previous == null) {
                handlingActionContext.remove();
            } else {
                handlingActionContext.set(previous);
            }
        }
    }

    private void runWithCaptureGeometry(CaptureGeometry geometry, Runnable action) {
        CaptureGeometry previous = handlingCaptureGeometry.get();
        handlingCaptureGeometry.set(geometry);
        try {
            action.run();
        } finally {
            if (previous == null) {
                handlingCaptureGeometry.remove();
            } else {
                handlingCaptureGeometry.set(previous);
            }
        }
    }

    private CaptureGeometry currentCaptureGeometry() {
        CaptureGeometry geometry = handlingCaptureGeometry.get();
        if (geometry == null) {
            throw new IllegalStateException("Screenshot-derived gesture is missing capture geometry");
        }
        return geometry;
    }

    private ActionAdmission.FrameContext currentActionContext() {
        ActionAdmission.FrameContext context = handlingActionContext.get();
        if (context == null) {
            throw new IllegalStateException("Screenshot-derived gesture is missing action context");
        }
        return context;
    }

    private ActionAdmission.CurrentState currentActionAdmissionState() {
        CurrentGameWindow window = currentGameWindow();
        return new ActionAdmission.CurrentState(
                isActiveRun(runGeneration),
                runGeneration,
                captureCoordinator == null ? 0L : captureCoordinator.latestCaptureSequence(),
                latestActionableOcrRequestSequence,
                android.os.SystemClock.uptimeMillis(),
                window == null ? null : window.packageName(),
                window == null ? null : window.bounds(),
                window == null ? -1 : window.windowId(),
                actionAdmissionEpoch);
    }

    private ActionGateway actionGateway() {
        if (actionGateway != null) {
            return actionGateway;
        }
        actionGateway = new ActionGateway(new ActionGateway.Platform() {
            @Override
            public ActionAdmission.CurrentState currentState() {
                return currentActionAdmissionState();
            }

            @Override
            public boolean performNodeAction(Object node, int action, Object argument) {
                if (!(node instanceof AccessibilityNodeInfo accessibilityNode)) {
                    return false;
                }
                if (action == ActionGateway.ACTION_SET_TEXT && argument instanceof String text) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    return accessibilityNode.performAction(action, arguments);
                }
                return accessibilityNode.performAction(action);
            }

            @Override
            public boolean dispatchGesture(Object gesture) {
                if (!(gesture instanceof GatewayGesture request)) {
                    return false;
                }
                return PetalAccessibilityService.this.dispatchGesture(
                        request.gesture(), request.callback(), handler);
            }

            @Override
            public boolean performGlobalAction(int action) {
                return PetalAccessibilityService.this.performGlobalAction(action);
            }
        }, MAX_ACTIONABLE_FRAME_AGE_MILLIS,
                (kind, context, decision) -> recordActionAdmissionDiagnostic(context, decision));
        return actionGateway;
    }

    private ActionAdmission.Decision evaluateActionAdmission(
            ActionAdmission.FrameContext context) {
        return actionGateway().admit(context);
    }

    private boolean isActionAdmitted(ActionAdmission.FrameContext context) {
        ActionAdmission.Decision admission = evaluateActionAdmission(context);
        recordActionAdmissionDiagnostic(context, admission);
        return admission.allowed();
    }

    /** Reads only immutable values from the current window; no node/window is retained. */
    private CurrentGameWindow currentGameWindow() {
        long traversalStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            CurrentGameWindow activeWindow = snapshotGameWindow(root, -1);
            if (activeWindow != null) {
                return activeWindow;
            }
            return currentGameWindowFromInteractiveWindows();
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.ACCESSIBILITY_TRAVERSAL,
                    android.os.SystemClock.uptimeMillis() - traversalStartedAtUptimeMillis);
        }
    }

    private CurrentGameWindow snapshotGameWindow(
            AccessibilityNodeInfo root, int windowId) {
        if (root == null) {
            return null;
        }
        CharSequence packageName = root.getPackageName();
        if (packageName == null || !GAME_PACKAGE.contentEquals(packageName)) {
            return null;
        }
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            bounds.set(
                    0,
                    0,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
        }
        if (bounds.isEmpty()) {
            return null;
        }
        return new CurrentGameWindow(
                packageName.toString(),
                captureBounds(bounds),
                windowId >= 0 ? windowId : root.getWindowId());
    }

    /**
     * The app's accessibility overlay can be the active root while the game remains the
     * foreground application.  Inspect only the top visible application window in that case;
     * never use recent package history to infer foreground state.
     */
    private CurrentGameWindow currentGameWindowFromInteractiveWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        CurrentGameWindow topApplication = null;
        int topLayer = Integer.MIN_VALUE;
        boolean topApplicationKnown = false;
        for (AccessibilityWindowInfo window : windows) {
            if (window == null
                    || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue;
            }
            int layer = window.getLayer();
            if (topApplicationKnown && layer <= topLayer) {
                continue;
            }
            topApplicationKnown = true;
            topLayer = layer;
            AccessibilityNodeInfo root = window.getRoot();
            topApplication = snapshotGameWindow(root, window.getId());
        }
        return topApplication;
    }

    /** Drops an action whose frame no longer matches the current game state and requests a rescan. */
    private void dropStaleAction() {
        if (running) {
            actionAdmissionEpoch++;
        }
        busy = false;
        if (!running) {
            return;
        }
        setStatus(getString(R.string.status_waiting_game));
        schedule(1500L);
    }

    /** Captures physical screen bounds before the asynchronous screenshot starts. */
    private List<ScreenshotOverlayMask.Region> captureVisibleOverlayRegions() {
        List<ScreenshotOverlayMask.Region> regions = new ArrayList<>();
        if (overlayHost != null) {
            overlayHost.addVisibleRegions(regions);
        }
        return List.copyOf(regions);
    }

    /** Sanitizes only the OCR bitmap; the actual accessibility overlays remain untouched. */
    private static void maskOverlayRegions(
            Bitmap bitmap, List<ScreenshotOverlayMask.Region> overlayRegions) {
        if (overlayRegions == null || overlayRegions.isEmpty()) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] rowBuffer = new int[width];
        ScreenshotOverlayMask.erase(
                width,
                height,
                new ScreenshotOverlayMask.PixelBuffer() {
                    @Override
                    public int get(int x, int y) {
                        return bitmap.getPixel(x, y);
                    }

                    @Override
                    public void fillRow(int y, int left, int right, int color) {
                        int length = right - left;
                        Arrays.fill(rowBuffer, 0, length, color);
                        bitmap.setPixels(rowBuffer, 0, length, left, y, length, 1);
                    }
                },
                overlayRegions,
                Math.max(4, Math.round(width * 0.01f)));
    }

    /** 將硬體 buffer 複製成可供 OCR 讀取的 ARGB bitmap。 */
    private Bitmap copyBitmap(ScreenshotResult result) {
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            return hardwareBitmap == null
                    ? null
                    : hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true);
        } finally {
            buffer.close();
        }
    }

    /** 執行一次 OCR 結果狀態機；種花的 bitmap 純分析先移至受限 worker。 */
    private void handleTokens(
            OcrRuntime.Transaction transaction,
            OcrScan.Frame frame,
            Bitmap bitmap,
            OcrFailureConsumer failure) {
        OcrScan.TransactionId transactionId = frame == null ? null : frame.transactionId();
        if (transactionId != null) {
            ocrRuntime.diagnostics().markHandleTokensStart(transactionId);
        }
        if (automationMode == AutomationMode.PLANTING) {
            if (dispatchH10aPlantingAnalysis(transaction, frame, bitmap, failure)) {
                return;
            }
            try {
                long analysisStartedAtNanos = System.nanoTime();
                long snapshotStartedAtNanos = System.nanoTime();
                int[] pixels = snapshotBitmapPixels(bitmap);
                long snapshotMillis = elapsedMillis(snapshotStartedAtNanos);
                int pixelWidth = bitmap.getWidth();
                H10aPlantingAnalysis.Result analysis = H10aPlantingAnalysis.analyze(
                        plantingAnalysisInput(frame, bitmap),
                        (x, y) -> pixels[y * pixelWidth + x]);
                recordH10aAnalysisTimings(
                        analysis,
                        elapsedMillis(analysisStartedAtNanos),
                        true,
                        snapshotMillis);
                handlePlantingTokens(frame, bitmap, analysis);
            } finally {
                if (transactionId != null) {
                    ocrRuntime.diagnostics().markHandleTokensEnd(transactionId);
                }
            }
            return;
        }
        try {
            handleTokensOnMain(frame, bitmap);
        } finally {
            if (transactionId != null) {
                ocrRuntime.diagnostics().markHandleTokensEnd(transactionId);
            }
        }
    }

    /** Keeps all workflow state mutation and non-planting handlers on the main thread. */
    private void handleTokensOnMain(OcrScan.Frame frame, Bitmap bitmap) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        if (automationMode == AutomationMode.FEED) {
            handleFeedTokens(frame, bitmap);
            return;
        }
        if (automationMode == AutomationMode.RETURN_REWARD) {
            handleReturnRewardTokens(tokens, bitmap);
            return;
        }
        if (automationMode == AutomationMode.DISPATCH) {
            handleExpeditionDispatch(frame, bitmap);
            return;
        }
        if (automationMode == AutomationMode.POSTCARD) {
            handlePostcardTokens(tokens, bitmap);
        }
    }

    /** Applies an immutable planting detector result to the main-thread state machine. */
    private void handlePlantingTokens(
            OcrScan.Frame frame,
            Bitmap bitmap,
            H10aPlantingAnalysis.Result analysis) {
        long stateStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            handlePlantingTokensOnMain(frame, bitmap, analysis);
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.WORKFLOW_STATE_MUTATION,
                    android.os.SystemClock.uptimeMillis() - stateStartedAtUptimeMillis);
        }
    }

    private void handlePlantingTokensOnMain(
            OcrScan.Frame frame,
            Bitmap bitmap,
            H10aPlantingAnalysis.Result analysis) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        List<String> sequence = settings.allowedFlowers();
        if (analysis == null) {
            handlePlantingMonitorMiss(bitmap);
            return;
        }
        if (isPlantingSearchStep()) {
            handlePlantingFlowerSearch(tokens, bitmap, frame, analysis);
            return;
        }
        PlantingScreenAnalyzer.Detection plantingScreen = analysis.plantingScreen();
        if (isPlantingEntryStep()) {
            logPlantingEntryFrame(frame, plantingScreen);
        }
        PlantingControlEvidence plantingControls = collectPlantingControlEvidence(
                analysis.ocrStartControl(), plantingScreen);

        switch (automationStep) {
            case CHECKING_PLANTING_ENTRY -> {
                handleInitialPlantingEntry(plantingControls);
                return;
            }
            case WAITING_INITIAL_PLANTING_MENU -> {
                verifyPlantingMenuOpened(plantingControls, false);
                return;
            }
            case WAITING_MENU_AFTER_START -> {
                verifyPlantingMenuOpened(plantingControls, true);
                return;
            }
            case WAITING_START -> {
                startPlanting(plantingControls);
                return;
            }
            case VERIFYING_START -> {
                verifyPlantingStarted(tokens, width, height, plantingControls);
                return;
            }
            case WAITING_STOP -> {
                stopPlanting(plantingControls);
                return;
            }
            case VERIFYING_STOP -> {
                verifyPlantingStopped(plantingControls);
                return;
            }
            case VERIFYING_SELECTION, MONITORING -> {
                // Only these states need the highlighted flower calculation below.
            }
            case REVEALING_SEARCH_PANEL,
                    OPENING_SEARCH,
                    CLEARING_SEARCH,
                    ENTERING_SEARCH,
                    CLOSING_SEARCH_KEYBOARD,
                    SELECTING_SEARCH_RESULT,
                    CLOSING_SEARCH_AFTER_SELECTION -> {
                handlePlantingFlowerSearch(tokens, bitmap, frame, analysis);
                return;
            }
        }

        PetalMatcher.Selection highlighted = analysis.highlighted();
        if (automationStep == AutomationStep.VERIFYING_SELECTION) {
            verifyFlowerSelection(highlighted, analysis.targetSelectionHighlighted());
            return;
        }

        boolean plantingCanStart = plantingControls.startVisible();
        String firstFlower = sequence.get(0);

        // 每次開始都先搜尋第一順位；搜尋結果已連續確認名稱與數量，不再重讀全畫面。
        if (currentFlower.isEmpty()) {
            returnToInitialPlantingEntry();
            return;
        }

        if (!analysis.visibleFlowerCard()) {
            logPlantingMonitorMiss("card-match-missing", frame);
            setStatus(getString(R.string.status_selected_not_visible));
            handlePlantingMonitorMiss(bitmap);
            return;
        }

        if (plantingCanStart) {
            if (highlighted != null && firstFlower.equals(highlighted.name())) {
                currentFlower = highlighted.name();
                if (usageSession != null) {
                    usageSession.recordPlantingPetalRemaining(
                            highlighted.name(), highlighted.count());
                }
                showPlantingStatus(highlighted.name(), highlighted.count());
                automationStep = AutomationStep.WAITING_START;
                actionAttempts = 0;
                startPlanting(plantingControls);
                return;
            }
            beginPlantingFlowerSearch(firstFlower, 0, true);
            return;
        }

        PetalMatcher.Selection monitored = PlantingFlowPolicy.monitoringSelection(
                highlighted, analysis.visibleCurrent());
        if (monitored == null) {
            logPlantingMonitorMiss("current-match-missing", frame);
            setStatus(getString(R.string.status_selected_not_visible));
            setPlantingNoticeText(
                    getString(R.string.overlay_planting_unreadable, currentFlower), false);
            handlePlantingMonitorMiss(bitmap);
            return;
        }

        handlePlantingMonitorSelection(monitored);
    }

    private void logPlantingMonitorMiss(String event, OcrScan.Frame frame) {
        Log.i(TAG, "PLANTING_OCR event=" + event
                + " currentFlower=" + currentFlower
                + " profile=" + frame.profile().name()
                + " tokenCount=" + frame.tokens().size()
                + " elapsedMs=" + frame.elapsedMillis());
    }

    /** 將高亮與精確花名備援讀值送進同一套門檻、冷卻及換花流程。 */
    private void handlePlantingMonitorSelection(PetalMatcher.Selection selection) {
        plantingMonitorMissingFrames = 0;
        if (PetalMatcher.needsSelectionCorrection(currentFlower, selection)) {
            // 手動切換到其他花盆時，以搜尋欄重新篩出設定中的目前目標。
            beginPlantingFlowerSearch(currentFlower, 0, false);
            return;
        }
        int remaining = selection.count();
        if (usageSession != null) {
            usageSession.recordPlantingPetalRemaining(selection.name(), remaining);
        }
        showPlantingStatus(selection.name(), remaining);
        long now = android.os.SystemClock.elapsedRealtime();
        long cooldown = switchGuard.cooldownRemainingMillis(now);
        int threshold = settings.threshold();
        boolean readyToSwitch = switchGuard.shouldSwitch(remaining, threshold, now);
        if (readyToSwitch) {
            targetCount = remaining;
            logPlantingSwitch("low-count-confirmed", false);
        }

        if (cooldown > 0) {
            setStatus(getString(
                    R.string.status_switch_cooldown, (cooldown + 999) / 1000));
            scheduleNext();
            return;
        }
        if (!SwitchGuard.isBelowThreshold(remaining, threshold)) {
            setStatus(currentFlower.isEmpty()
                    ? getString(R.string.status_remaining, remaining, threshold)
                    : getString(R.string.status_current_remaining, currentFlower, remaining));
            scheduleNext();
            return;
        }
        if (!readyToSwitch) {
            setStatus(getString(
                    R.string.status_confirming_low,
                    remaining,
                    switchGuard.confirmations(),
                    SwitchGuard.REQUIRED_CONFIRMATIONS));
            scheduleNext();
            return;
        }

        PlantingFlowPolicy.LowCountDecision lowCountDecision =
                PlantingFlowPolicy.afterConfirmedLowCount(
                        settings.allowedFlowers(), currentFlower);
        if (lowCountDecision.action()
                == PlantingFlowPolicy.LowCountAction.STOP_PLANTING) {
            beginFinalPlantingStop();
            return;
        }

        // 下一順位花盆一律透過搜尋欄取得，避免清單長度與解析度改變搜尋結果。
        beginPlantingFlowerSearch(lowCountDecision.nextFlower(), 0, false);
    }

    /** 完整畫面連續讀不到目前花盆時，才以相對裁切的 PETAL_LIST 再讀一次。 */
    private void handlePlantingMonitorMiss(Bitmap bitmap) {
        if (!PlantingFlowPolicy.shouldUseFocusedMonitorOcr(
                ++plantingMonitorMissingFrames)) {
            scheduleNext();
            return;
        }
        plantingMonitorMissingFrames = 0;
        scanFocusedPlantingMonitorRegion(bitmap);
    }

    /** 花瓣生產：選精華、讀數量、餵食、確認發光、採花及三擊換隊。 */
    private void handleFeedTokens(OcrScan.Frame frame, Bitmap bitmap) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        switch (feedStep) {
            case WAITING_GAME_READY -> {
                if (!resumeFeedFromExistingPetalReady(tokens, bitmap)) {
                    waitForFeedGameReady(tokens, bitmap);
                }
            }
            case OPENING_NECTAR -> {
                boolean detailOpen = FeedScreenAnalyzer.isPikminDetailOpen(tokens);
                boolean searchControlVisible = CardHighlight.isPetalSearchOpen(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
                boolean searchInputActive = hasFocusedGameEditableText();
                boolean visualOpen = FeedScreenAnalyzer.isNectarPanelVisualOpen(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
                FeedScreenAnalyzer.NectarSearchAnalysis listAnalysis =
                        FeedScreenAnalyzer.analyzeVisibleNectarList(
                                tokens,
                                feedTargetFlower,
                                bitmap.getWidth(),
                                bitmap.getHeight(),
                                bitmap::getPixel);
                List<FeedScreenAnalyzer.NectarSelection> visibleCandidates =
                        !searchInputActive && "visible-target-missing".equals(listAnalysis.reason())
                                ? FeedScreenAnalyzer.findVisibleNectarCandidates(
                                tokens,
                                feedTargetFlower,
                                bitmap.getWidth(),
                                bitmap.getHeight())
                                : List.of();
                boolean listConfirmed = !searchInputActive
                        && (listAnalysis.selection() != null || !visibleCandidates.isEmpty());
                boolean panelOpen = !detailOpen && visualOpen;
                if (!panelOpen
                        && (detailOpen || searchInputActive
                                || FeedScreenAnalyzer.currentNectarCount(
                                tokens,
                                bitmap.getWidth(),
                                bitmap.getHeight()) == null)) {
                    feedReadyStability.reset();
                    feedNectarOpenAttempts = 0;
                    feedStep = FeedStep.WAITING_GAME_READY;
                    actionAdmissionEpoch++;
                    Log.i(TAG, "[FEED-PANEL] event=entry-evidence-invalid"
                            + " detailOpen=" + detailOpen
                            + " searchInputActive=" + searchInputActive
                            + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                if (!panelOpen && feedNectarOpenAttempts == 1) {
                    Log.i(TAG, "[FEED-PANEL] event=guard-rejected"
                            + " detailOpen=" + detailOpen
                            + " searchControlVisible=" + searchControlVisible
                            + " searchInputActive=" + searchInputActive
                            + " visualOpen=" + visualOpen
                            + " listReason=" + listAnalysis.reason()
                            + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                }
                if (panelOpen) {
                    ObservationStability.Result stability = feedReadyStability.observe(
                            listConfirmed ? "nectar-list" : "nectar-panel",
                            bitmap.getWidth() / 2,
                            bitmap.getHeight() / 5,
                            bitmap.getWidth(),
                            bitmap.getHeight());
                    statusFeed(getString(R.string.status_feed_confirming_panel_open));
                    if (stability == ObservationStability.Result.STABLE) {
                        feedReadyStability.reset();
                        feedNectarOpenAttempts = 0;
                        if (!listConfirmed) {
                            actionAdmissionEpoch++;
                            Log.i(TAG, "FEED_NECTAR_LIST event=not-confirmed"
                                    + " target=" + FeedScreenAnalyzer.nectarDisplayName(
                                            feedTargetFlower)
                                    + " listReason=" + listAnalysis.reason()
                                    + " candidateCount=" + visibleCandidates.size()
                                    + " searchControlVisible=" + searchControlVisible);
                            schedule(FEED_OCR_RETRY_MILLIS);
                            return;
                        }
                        FeedScreenAnalyzer.NectarSelection selection = listAnalysis.selection();
                        Log.i(TAG, "FEED_NECTAR_LIST event=confirmed"
                                + " target=" + FeedScreenAnalyzer.nectarDisplayName(
                                        feedTargetFlower)
                                + " candidate=" + (selection == null
                                        ? "template-pending"
                                        : selection.x() + "," + selection.tapY())
                                + " candidateCount=" + visibleCandidates.size()
                                + " searchControlVisible=" + searchControlVisible);
                        if (!feedRequireNectarSearch) {
                            feedStep = FeedStep.SELECTING_NECTAR;
                        } else {
                            Log.i(TAG, "FEED_NECTAR_SELECTION event=search-required"
                                    + " target=" + FeedScreenAnalyzer.nectarDisplayName(
                                            feedTargetFlower)
                                    + " visibleCandidates=" + visibleCandidates.size());
                            feedStep = FeedStep.OPENING_NECTAR_SEARCH;
                        }
                        schedule(0L);
                    } else {
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                    return;
                }
                feedReadyStability.reset();
                if (feedNectarOpenAttempts >= MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_feed_open_failed));
                    return;
                }
                feedNectarOpenAttempts++;
                statusFeed(getString(R.string.status_feed_opening_nectar));
                ActionAdmission.FrameContext entryContext = currentActionContext();
                CaptureGeometry entryGeometry = currentCaptureGeometry();
                Log.i(TAG, "[FEED-PANEL] event=entry-tap-request"
                        + " x=" + Math.round(bitmap.getWidth() * 0.50f)
                        + " y=" + Math.round(bitmap.getHeight() * 0.873f)
                        + " generation=" + entryContext.runGeneration()
                        + " capture=" + entryContext.captureSequence()
                        + " ocr=" + entryContext.ocrRequestSequence()
                        + " epoch=" + entryContext.admissionEpoch()
                        + " package=" + entryContext.packageName()
                        + " windowId=" + entryContext.windowId()
                        + " bounds=" + entryContext.windowBounds()
                        + " geometryMode=" + entryGeometry.mode()
                        + " targetBounds=" + entryGeometry.targetWindowBoundsOnScreen());
                dispatchTap(
                        Math.round(bitmap.getWidth() * 0.50f),
                        Math.round(bitmap.getHeight() * 0.873f),
                        90L,
                        () -> schedule(700L),
                        () -> stopWithError(getString(R.string.status_feed_open_failed)));
            }
            case OPENING_NECTAR_SEARCH -> openFeedNectarSearch(tokens, bitmap);
            case CLEARING_NECTAR_SEARCH -> clearFeedNectarSearch(bitmap);
            case ENTERING_NECTAR_SEARCH -> enterFeedNectarSearch();
            case CONFIRMING_NECTAR_SEARCH -> confirmFeedNectarSearch(bitmap);
            case CLOSING_NECTAR_KEYBOARD -> closeFeedSearchKeyboard(bitmap);
            case SELECTING_NECTAR -> {
                if (FeedScreenAnalyzer.isPikminDetailOpen(tokens)) {
                    if (closeFeedPikminDetailIfOpen(tokens)) {
                        return;
                    }
                    statusFeed(getString(R.string.status_feed_waiting_panel_close));
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                if (hasFocusedGameEditableText()) {
                    int missingFrames = ++feedSearchMissingFrames;
                    boolean focusCleared = missingFrames < FEED_MAX_TARGET_MISSING_FRAMES
                            && clearFocusedGameEditableText();
                    Log.i(TAG, "FEED_NECTAR_SELECTION event=editable-focus-block"
                            + " clearAccepted=" + focusCleared
                            + " missingFrames=" + missingFrames);
                    if (missingFrames >= FEED_MAX_TARGET_MISSING_FRAMES || !focusCleared) {
                        stopWithError(getString(R.string.status_feed_keyboard_failed));
                        return;
                    }
                    statusFeed(getString(R.string.status_feed_closing_keyboard));
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                feedSearchMissingFrames = 0;
                String query = FeedScreenAnalyzer.nectarSearchQuery(feedTargetFlower);
                FeedScreenAnalyzer.NectarSearchAnalysis listAnalysis =
                        FeedScreenAnalyzer.analyzeVisibleNectarList(
                                tokens,
                                feedTargetFlower,
                                bitmap.getWidth(),
                                bitmap.getHeight(),
                                bitmap::getPixel);
                List<FeedScreenAnalyzer.NectarSelection> visibleCandidates =
                        "visible-target-missing".equals(listAnalysis.reason())
                                ? FeedScreenAnalyzer.findVisibleNectarCandidates(
                                tokens,
                                feedTargetFlower,
                                bitmap.getWidth(),
                                bitmap.getHeight())
                                : List.of();
                FeedScreenAnalyzer.NectarSelection templateSelection = visibleCandidates.isEmpty()
                        ? null
                        : findVisibleTemplateNectarCandidate(frame, bitmap, visibleCandidates);
                boolean listConfirmed = listAnalysis.selection() != null || templateSelection != null;
                FeedScreenAnalyzer.NectarSearchAnalysis nectarAnalysis;
                if (listConfirmed) {
                    FeedScreenAnalyzer.NectarSelection selection = listAnalysis.selection() == null
                            ? templateSelection : listAnalysis.selection();
                    nectarAnalysis = new FeedScreenAnalyzer.NectarSearchAnalysis(
                            selection,
                            listAnalysis.selection() == null
                                    ? "visible-template-matched" : listAnalysis.reason(),
                            true,
                            true);
                } else {
                    if (!CardHighlight.isPetalSearchOpen(
                            bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)) {
                        feedSearchOpenConfirmationFrames = 0;
                        feedSearchTextConfirmationFrames = 0;
                        feedNectarCandidate = null;
                        feedNectarCandidateFrames = 0;
                        feedStep = FeedStep.OPENING_NECTAR_SEARCH;
                        schedule(FEED_OCR_RETRY_MILLIS);
                        return;
                    }
                    if (!gameEditableTextMatches(query)) {
                        feedNectarCandidate = null;
                        feedNectarCandidateFrames = 0;
                        feedStep = FeedStep.ENTERING_NECTAR_SEARCH;
                        schedule(FEED_OCR_RETRY_MILLIS);
                        return;
                    }
                    String canonicalTarget = PetalCatalog.canonicalName(feedTargetFlower);
                    boolean basicNectar = canonicalTarget != null
                            && canonicalTarget.endsWith("花瓣");
                    nectarAnalysis = basicNectar
                            ? new FeedScreenAnalyzer.NectarSearchAnalysis(
                                    null, "basic-card-geometry-required", false, false)
                            : FeedScreenAnalyzer.analyzeSearchedNectar(
                                    tokens,
                                    feedTargetFlower,
                                    bitmap.getWidth(),
                                    bitmap.getHeight());
                }
                FeedScreenAnalyzer.NectarSelection selection = nectarAnalysis.selection();
                if (selection == null) {
                    if (feedTargetMissingFrames == 0) {
                        Log.i(TAG, "FEED_NECTAR_ANALYSIS reason=" + nectarAnalysis.reason()
                                + " nectarCountFound=" + nectarAnalysis.nectarCountFound()
                                + " petalCountFound=" + nectarAnalysis.petalCountFound()
                                + " candidates=" + FeedScreenAnalyzer.searchedNectarNumberLocations(
                                        tokens, bitmap.getWidth(), bitmap.getHeight()));
                    }
                    nectarTemplateMatcher.clearCache();
                    feedNectarCandidate = null;
                    feedNectarCandidateFrames = 0;
                    feedTargetMissingFrames++;
                    recordFeedNectarSelectionDiagnostic(
                            "missing",
                            nectarAnalysis.reason(),
                            feedTargetMissingFrames,
                            0,
                            feedTargetMissingFrames,
                            nectarAnalysis.nectarCountFound(),
                            nectarAnalysis.petalCountFound());
                    statusFeed(getString(
                            R.string.status_feed_searching_nectar,
                            FeedScreenAnalyzer.nectarDisplayName(feedTargetFlower),
                            feedTargetMissingFrames,
                            FEED_MAX_TARGET_MISSING_FRAMES));
                    if (feedTargetMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                        stopWithError(getString(
                                R.string.status_feed_nectar_missing,
                                FeedScreenAnalyzer.nectarDisplayName(feedTargetFlower)));
                    } else {
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                    return;
                }
                NectarTemplateMatcher.Evidence visualEvidence =
                        matchFeedNectarTemplate(frame, bitmap, selection);
                if (visualEvidence.status() == NectarTemplateMatcher.Status.CONFLICT) {
                    nectarTemplateMatcher.clearCache();
                    feedNectarCandidate = null;
                    feedNectarCandidateFrames = 0;
                    feedTargetMissingFrames++;
                    statusFeed(getString(
                            R.string.status_feed_searching_nectar,
                            selection.name(),
                            feedTargetMissingFrames,
                            FEED_MAX_TARGET_MISSING_FRAMES));
                    if (feedTargetMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                        stopWithError(getString(
                                R.string.status_feed_nectar_missing, selection.name()));
                    } else {
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                    return;
                }
                if (listConfirmed && visualEvidence.status() != NectarTemplateMatcher.Status.MATCHED) {
                    nectarTemplateMatcher.clearCache();
                    feedNectarCandidate = null;
                    feedNectarCandidateFrames = 0;
                    feedTargetMissingFrames++;
                    String reason = "visible-template-"
                            + visualEvidence.status().name().toLowerCase(java.util.Locale.ROOT);
                    recordFeedNectarSelectionDiagnostic(
                            "missing",
                            reason,
                            feedTargetMissingFrames,
                            0,
                            feedTargetMissingFrames,
                            true,
                            true);
                    statusFeed(getString(
                            R.string.status_feed_searching_nectar,
                            selection.name(),
                            feedTargetMissingFrames,
                            FEED_MAX_TARGET_MISSING_FRAMES));
                    if (feedTargetMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                        stopWithError(getString(
                                R.string.status_feed_nectar_missing, selection.name()));
                    } else {
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                    return;
                }
                int requiredTargetFrames = NectarTemplateMatcher.requiredStableFrames(
                        visualEvidence.status(), FEED_REQUIRED_NECTAR_TARGET_FRAMES);
                String stabilityReason = FeedScreenAnalyzer.nectarSelectionStabilityReason(
                        feedNectarCandidate,
                        selection,
                        bitmap.getWidth(),
                        bitmap.getHeight());
                Log.i(TAG, "FEED_NECTAR_SELECTION event=candidate"
                        + " target=" + selection.name()
                        + " nectarCount=" + selection.count()
                        + " petalCount=" + selection.petalCount()
                        + " x=" + selection.x()
                        + " y=" + selection.tapY()
                        + " template=" + visualEvidence.status()
                        + " listConfirmed=" + listConfirmed
                        + " stability=" + stabilityReason
                        + " candidateFrames=" + feedNectarCandidateFrames
                        + " requiredFrames=" + requiredTargetFrames);
                if (!"stable".equals(stabilityReason)) {
                    nectarTemplateMatcher.clearCache();
                    feedNectarCandidate = selection;
                    feedNectarCandidateFrames = 1;
                    feedTargetMissingFrames++;
                    recordFeedNectarSelectionDiagnostic(
                            "confirming",
                            stabilityReason,
                            feedTargetMissingFrames,
                            feedNectarCandidateFrames,
                            feedTargetMissingFrames,
                            true,
                            true);
                    statusFeed(getString(
                            R.string.status_feed_confirming_nectar_target,
                            selection.name(),
                            feedNectarCandidateFrames,
                            requiredTargetFrames));
                    if (feedTargetMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                        stopWithError(getString(
                                R.string.status_feed_nectar_missing, selection.name()));
                    } else {
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                    return;
                }
                feedNectarCandidate = selection;
                feedNectarCandidateFrames++;
                recordFeedNectarSelectionDiagnostic(
                        "confirmed",
                        "stable",
                        Math.max(1, feedTargetMissingFrames + 1),
                        feedNectarCandidateFrames,
                        feedTargetMissingFrames,
                        true,
                        true);
                if (feedNectarCandidateFrames < requiredTargetFrames) {
                    statusFeed(getString(
                            R.string.status_feed_confirming_nectar_target,
                            selection.name(),
                            feedNectarCandidateFrames,
                            requiredTargetFrames));
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                if (!feedNectarFreshFrameRequired) {
                    feedNectarFreshFrameRequired = true;
                    feedNectarCandidate = null;
                    feedNectarCandidateFrames = 0;
                    Log.i(TAG, "FEED_NECTAR_SELECTION event=fresh-frame-request"
                            + " target=" + selection.name()
                            + " requiredFrames=" + requiredTargetFrames);
                    requestFreshCaptureOnly(
                            "CAPTURE_ONLY_FEED_NECTAR_SELECTION",
                            () -> {
                                Log.i(TAG, "FEED_NECTAR_SELECTION event=fresh-frame-ready"
                                        + " target=" + selection.name());
                                schedule(0L);
                            },
                            () -> stopWithError(
                                    getString(R.string.status_feed_select_failed)));
                    return;
                }
                selectFeedNectar(selection);
            }
            case WAITING_NECTAR_PANEL_CLOSE -> waitForFeedNectarPanelClose(tokens, bitmap);
            case READING_NECTAR_COUNT -> {
                if (!feedZoomReady) {
                    zoomOutBeforeFeedRound();
                    return;
                }
                Integer remaining = FeedScreenAnalyzer.currentNectarCount(
                        tokens, bitmap.getWidth(), bitmap.getHeight());
                if (remaining == null) {
                    statusFeed(getString(R.string.status_feed_count_retry));
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                if (feedAttemptCount > 0) {
                    Integer consumed = FeedScreenAnalyzer.consumedNectar(
                            feedNectarBeforeRound, remaining);
                    if (consumed == null) {
                        statusFeed(getString(R.string.status_feed_count_retry));
                        schedule(FEED_OCR_RETRY_MILLIS);
                        return;
                    }
                    if (usageSession != null) {
                        usageSession.recordFeedNectar(consumed);
                    }
                    feedNectarBeforeRound = remaining;
                }
                if (FeedScreenAnalyzer.shouldAdvanceNectar(
                        remaining, feedSettings.nectarMinimumThreshold())) {
                    if (advanceFeedNectar()) {
                        schedule(200L);
                    }
                    return;
                }
                if (feedAttemptCount == 0) {
                    feedNectarBeforeRound = remaining;
                }
                feedBloomBaseline = FeedScreenAnalyzer.capture(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
                feedZoomReady = false;
                beginFeedGesture();
            }
            case READING_CONSUMED_COUNT -> {
                if (closeFeedPikminDetailIfOpen(tokens)) {
                    return;
                }
                Integer remaining = FeedScreenAnalyzer.currentNectarCount(
                        tokens, bitmap.getWidth(), bitmap.getHeight());
                FeedScreenAnalyzer.FeedRoundStatistics statistics = remaining == null ? null
                        : FeedScreenAnalyzer.feedRoundStatistics(
                                feedNectarBeforeRound, remaining, feedCollectedPetals);
                if (statistics == null) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (feedNoEffectStartedAt == 0L) {
                        feedNoEffectStartedAt = now;
                    }
                    if (now - feedNoEffectStartedAt >= FEED_NO_EFFECT_TIMEOUT_MILLIS) {
                        logFeedSpiral("round-statistics",
                                "nectar=unreadable petals=" + feedCollectedPetals);
                        if (feedCollectAfterCount) {
                            completeFeedPetalCollection();
                        } else {
                            switchFeedSquad(true);
                        }
                        return;
                    }
                    statusFeed(getString(R.string.status_feed_count_retry));
                    schedule(FEED_OCR_RETRY_MILLIS);
                    return;
                }
                if (usageSession != null) {
                    usageSession.recordFeedNectar(statistics.nectarConsumed());
                }
                logFeedSpiral("round-statistics",
                        "nectar=" + statistics.nectarConsumed()
                                + " petals=" + statistics.petalsObserved());
                feedNectarBeforeRound = remaining;
                if (feedCollectAfterCount) {
                    completeFeedPetalCollection();
                } else {
                    switchFeedSquad(true);
                }
            }
            case COLLECTING -> handleFeedPetalCollection(tokens, bitmap);
            case FEEDING -> observeFeedHold(tokens, bitmap);
            case ZOOMING_OUT, SWITCHING -> {
                // 手勢完成回呼負責推進；不在中途截圖重入。
            }
        }
    }

    private FeedScreenAnalyzer.NectarSelection findVisibleTemplateNectarCandidate(
            OcrScan.Frame frame,
            Bitmap bitmap,
            List<FeedScreenAnalyzer.NectarSelection> candidates) {
        // The filtered basic-color list puts the canonical card first. Keep the
        // template gate, but do not let red-looking sibling cards veto it.
        FeedScreenAnalyzer.NectarSelection first = candidates.get(0);
        return matchFeedNectarTemplate(frame, bitmap, first).status()
                == NectarTemplateMatcher.Status.MATCHED ? first : null;
    }

    private NectarTemplateMatcher.Evidence matchFeedNectarTemplate(
            OcrScan.Frame frame,
            Bitmap bitmap,
            FeedScreenAnalyzer.NectarSelection selection) {
        NectarTemplateMatcher.MatchContext matchContext =
                new NectarTemplateMatcher.MatchContext(
                        frame.transactionId().runGeneration(),
                        frame.captureGeometry().captureSequence(),
                        android.os.SystemClock.uptimeMillis(),
                        feedStep.name(),
                        selection.name() + "#" + selection.count() + "#" + selection.petalCount(),
                        frame.captureGeometry().targetWindowBoundsOnScreen(),
                        frame.captureGeometry().windowId());
        NectarTemplateMatcher.MatchStats matchStatsBefore = nectarTemplateMatcher.stats();
        long templateStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        NectarTemplateMatcher.Evidence evidence = nectarTemplateMatcher.match(
                bitmap,
                feedTargetFlower,
                selection.x(),
                selection.tapY(),
                matchContext);
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.TEMPLATE_DETECTOR,
                android.os.SystemClock.uptimeMillis() - templateStartedAtUptimeMillis);
        NectarTemplateMatcher.MatchStats matchStats = nectarTemplateMatcher.stats();
        workflowDiagnostics.recordCount(
                WorkflowDiagnostics.Counter.NECTAR_FULL_MATCHES,
                matchStats.fullMatchExecutions() - matchStatsBefore.fullMatchExecutions());
        workflowDiagnostics.recordCount(
                WorkflowDiagnostics.Counter.NECTAR_CACHE_HITS,
                matchStats.cacheHits() - matchStatsBefore.cacheHits());
        Log.i(TAG, "FEED_NECTAR_TEMPLATE status=" + evidence.status()
                + " expectedScore=" + evidence.expectedScore()
                + " bestScore=" + evidence.bestScore()
                + " fullMatches=" + matchStats.fullMatchExecutions()
                + " cacheHits=" + matchStats.cacheHits());
        return evidence;
    }

    private boolean resumeFeedFromExistingPetalReady(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (FeedScreenAnalyzer.isPikminDetailOpen(tokens)
                || FeedScreenAnalyzer.isNectarPanelOpen(tokens, width, height, bitmap::getPixel)) {
            feedBloomCandidate = null;
            feedBloomCandidateFrames = 0;
            return false;
        }
        Integer nectarCount = FeedScreenAnalyzer.currentNectarCount(tokens, width, height);
        FeedScreenAnalyzer.BloomTarget target = FeedScreenAnalyzer.findExistingBloomTarget(
                feedTargetFlower, width, height, bitmap::getPixel);
        if (nectarCount == null || target == null) {
            feedBloomCandidate = null;
            feedBloomCandidateFrames = 0;
            return false;
        }
        feedReadyMissingFrames = 0;
        if (FeedScreenAnalyzer.isSameBloomTarget(
                feedBloomCandidate, target, width, height)) {
            feedBloomCandidateFrames++;
        } else {
            feedBloomCandidateFrames = 1;
        }
        feedBloomCandidate = target;
        logFeedSpiral("petal-ready-candidate",
                "target=" + feedTargetFlower
                        + " nectar=" + nectarCount
                        + " x=" + target.x()
                        + " y=" + target.y()
                        + " frames=" + feedBloomCandidateFrames);
        if (!FeedScreenAnalyzer.hasStableBloom(
                feedBloomCandidateFrames, FEED_REQUIRED_BLOOM_TARGET_FRAMES)) {
            statusFeed(getString(
                    R.string.status_feed_collect_target_confirming,
                    feedBloomCandidateFrames,
                    FEED_REQUIRED_BLOOM_TARGET_FRAMES));
            schedule(FEED_COLLECT_SCAN_MILLIS);
            return true;
        }
        feedReadyStability.reset();
        beginRecoveredFeedPetalCollection(target);
        return true;
    }

    private void waitForFeedGameReady(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        boolean panelOpen = FeedScreenAnalyzer.isNectarPanelOpen(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        Integer count = FeedScreenAnalyzer.currentNectarCount(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        String key = panelOpen ? "nectar-panel" : count == null ? "" : "nectar:" + count;
        if (key.isEmpty()) {
            feedReadyStability.miss();
            if (++feedReadyMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                stopWithError(getString(R.string.status_feed_wrong_page));
                return;
            }
            statusFeed(getString(
                    R.string.status_feed_waiting_ready,
                    feedReadyMissingFrames,
                    FEED_MAX_TARGET_MISSING_FRAMES));
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        feedReadyMissingFrames = 0;
        ObservationStability.Result stability = feedReadyStability.observe(
                key,
                bitmap.getWidth() / 2,
                bitmap.getHeight() * 9 / 10,
                bitmap.getWidth(),
                bitmap.getHeight());
        statusFeed(getString(R.string.status_feed_confirming_ready));
        if (stability == ObservationStability.Result.STABLE) {
            feedReadyStability.reset();
            feedStep = FeedStep.OPENING_NECTAR;
            schedule(0L);
        } else {
            schedule(FEED_OCR_RETRY_MILLIS);
        }
    }

    private boolean hasConfirmedFeedNectarList(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (hasFocusedGameEditableText()) {
            return false;
        }
        FeedScreenAnalyzer.NectarSearchAnalysis analysis =
                FeedScreenAnalyzer.analyzeVisibleNectarList(
                        tokens,
                        feedTargetFlower,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        bitmap::getPixel);
        if (analysis.selection() != null) {
            return true;
        }
        return !FeedScreenAnalyzer.findVisibleNectarCandidates(
                tokens,
                feedTargetFlower,
                bitmap.getWidth(),
                bitmap.getHeight()).isEmpty();
    }

    private void openFeedNectarSearch(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CardHighlight.PetalSearchAnalysis searchAnalysis = CardHighlight.analyzePetalSearchControls(
                width, height, bitmap::getPixel);
        CardHighlight.Point search = searchAnalysis.searchButton();
        if (search != null) {
            if (!hasConfirmedFeedNectarList(tokens, bitmap)) {
                actionAdmissionEpoch++;
                feedStep = FeedStep.OPENING_NECTAR;
                feedSearchOpenConfirmationFrames = 0;
                Log.i(TAG, "[FEED-PANEL] event=search-guard-rejected"
                        + " reason=nectar-list-not-confirmed"
                        + " bitmap=" + width + "x" + height);
                schedule(FEED_OCR_RETRY_MILLIS);
                return;
            }
            feedSearchMissingFrames = 0;
            feedSearchOpenConfirmationFrames = 0;
            if (feedSearchActionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_feed_search_open_failed));
                return;
            }
            feedSearchActionAttempts++;
            statusFeed(getString(R.string.status_feed_opening_search));
            CaptureGeometry captureGeometry = currentCaptureGeometry();
            ScreenCoordinateTransform.Point screenSearch =
                    ScreenCoordinateTransform.feedSearchPointToScreen(
                            search.x(), search.y(), captureGeometry);
            String mapping = ScreenCoordinateTransform.feedSearchUsesDisplayBitmap(captureGeometry)
                    ? "display-bitmap" : "scaled";
            if (usageSession != null) {
                usageSession.recordFeedSearchGesture(
                        captureGeometry,
                        search.x(),
                        search.y(),
                        screenSearch.x(),
                        screenSearch.y(),
                        mapping,
                        feedSearchActionAttempts);
            }
            Log.i(TAG, "[FEED-SEARCH] event=gesture-request"
                    + " attempt=" + feedSearchActionAttempts
                    + " bitmap=" + search.x() + "," + search.y()
                    + " screen=" + screenSearch.x() + "," + screenSearch.y()
                    + " capture=" + captureGeometry.bitmapWidth() + "x"
                            + captureGeometry.bitmapHeight()
                    + " expectedBounds=" + captureGeometry.expectedSourceBoundsOnScreen()
                    + " targetBounds=" + captureGeometry.targetWindowBoundsOnScreen()
                    + " scale=" + captureGeometry.scaleX() + "," + captureGeometry.scaleY()
                    + " mapping=" + mapping);
            dispatchScreenTap(
                    screenSearch.x(),
                    screenSearch.y(),
                    GAME_ACTION_TAP_DURATION_MILLIS,
                    () -> schedule(700L),
                    () -> stopWithError(getString(R.string.status_feed_search_open_failed)));
            return;
        }
        boolean searchOpen = searchAnalysis.searchOpen();
        feedSearchOpenConfirmationFrames = searchOpen
                ? feedSearchOpenConfirmationFrames + 1 : 0;
        if (FeedScreenAnalyzer.hasStableSearchEvidence(
                searchOpen,
                true,
                feedSearchOpenConfirmationFrames,
                FEED_REQUIRED_SEARCH_FRAMES)) {
            feedSearchActionAttempts = 0;
            feedSearchMissingFrames = 0;
            feedSearchOpenConfirmationFrames = 0;
            feedStep = feedSearchResetPhase == FeedSearchResetPhase.CLOSING
                    ? FeedStep.CLEARING_NECTAR_SEARCH
                    : FeedStep.ENTERING_NECTAR_SEARCH;
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        if (!searchOpen) {
            if (++feedSearchMissingFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                stopWithError(getString(R.string.status_feed_search_open_failed));
                return;
            }
        } else {
            feedSearchMissingFrames = 0;
        }
        statusFeed(getString(R.string.status_feed_confirming_search));
        schedule(FEED_OCR_RETRY_MILLIS);
    }

    /** 切換下一項精華前點擊一次 X，再交由開啟流程確認收合或直接輸入。 */
    private void clearFeedNectarSearch(Bitmap bitmap) {
        CardHighlight.PetalSearchAnalysis searchAnalysis = CardHighlight.analyzePetalSearchControls(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        if (!searchAnalysis.searchOpen()) {
            feedSearchActionAttempts = 0;
            feedSearchMissingFrames = 0;
            feedSearchResetPhase = FeedSearchResetPhase.NONE;
            feedStep = FeedStep.OPENING_NECTAR_SEARCH;
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        CardHighlight.Point clear = searchAnalysis.closeButton();
        if (clear == null) {
            stopWithError(getString(R.string.status_feed_search_input_failed));
            return;
        }
        feedSearchActionAttempts = 1;
        feedSearchMissingFrames = 0;
        statusFeed(getString(R.string.status_feed_confirming_search));
        dispatchTap(
                clear.x(),
                clear.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> {
                    feedSearchActionAttempts = 0;
                    feedSearchMissingFrames = 0;
                    feedSearchResetPhase = FeedSearchResetPhase.NONE;
                    feedStep = FeedStep.OPENING_NECTAR_SEARCH;
                    schedule(700L);
                },
                () -> stopWithError(getString(R.string.status_feed_search_input_failed)));
    }

    private void enterFeedNectarSearch() {
        String query = FeedScreenAnalyzer.nectarSearchQuery(feedTargetFlower);
        if (query.isBlank()) {
            stopWithError(getString(R.string.status_feed_search_invalid));
            return;
        }
        if (feedSearchInputAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_feed_search_input_failed));
            return;
        }
        feedSearchInputAttempts++;
        AccessibilityNodeInfo searchInput = findGameNode(node ->
                node.isEditable() && node.isEnabled());
        if (searchInput == null
                || (!searchInput.isFocused()
                        && !focusGameEditableText(searchInput))
                || !setEditableText(searchInput, query)) {
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        feedSearchTextConfirmationFrames = 0;
        feedStep = FeedStep.CONFIRMING_NECTAR_SEARCH;
        statusFeed(getString(R.string.status_feed_confirming_search));
        schedule(FEED_OCR_RETRY_MILLIS);
    }

    private void confirmFeedNectarSearch(Bitmap bitmap) {
        String query = FeedScreenAnalyzer.nectarSearchQuery(feedTargetFlower);
        boolean searchOpen = CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        boolean queryMatches = gameEditableTextMatches(query);
        feedSearchTextConfirmationFrames = searchOpen && queryMatches
                ? feedSearchTextConfirmationFrames + 1 : 0;
        if (FeedScreenAnalyzer.hasStableSearchEvidence(
                searchOpen,
                queryMatches,
                feedSearchTextConfirmationFrames,
                FEED_REQUIRED_SEARCH_FRAMES)) {
            feedSearchInputAttempts = 0;
            feedSearchTextConfirmationFrames = 0;
            feedSearchKeyboardGuard.reset();
            feedStep = FeedStep.CLOSING_NECTAR_KEYBOARD;
            statusFeed(getString(R.string.status_feed_closing_keyboard));
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        statusFeed(getString(R.string.status_feed_confirming_search));
        if (!searchOpen) {
            feedSearchOpenConfirmationFrames = 0;
            feedStep = FeedStep.OPENING_NECTAR_SEARCH;
        } else if (!queryMatches) {
            feedStep = FeedStep.ENTERING_NECTAR_SEARCH;
        }
        schedule(FEED_OCR_RETRY_MILLIS);
    }

    private void closeFeedSearchKeyboard(Bitmap bitmap) {
        String query = FeedScreenAnalyzer.nectarSearchQuery(feedTargetFlower);
        boolean searchPageConfirmed = CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)
                && gameEditableTextMatches(query);
        SearchKeyboardGuard.Action action = observeSearchKeyboard(feedSearchKeyboardGuard, query, searchPageConfirmed);
        if (action == SearchKeyboardGuard.Action.COMPLETE) {
            feedSearchKeyboardGuard.reset();
            if (hasFocusedGameEditableText()) {
                if (!clearFocusedGameEditableText()) {
                    stopWithError(getString(R.string.status_feed_keyboard_failed));
                    return;
                }
                feedSearchMissingFrames = 0;
                feedStep = FeedStep.SELECTING_NECTAR;
                statusFeed(getString(R.string.status_feed_closing_keyboard));
                schedule(FEED_OCR_RETRY_MILLIS);
                return;
            }
            feedSearchMissingFrames = 0;
            feedStep = FeedStep.SELECTING_NECTAR;
            schedule(700L);
            return;
        }
        if (action == SearchKeyboardGuard.Action.SEND_BACK) {
            if (!sendSearchKeyboardBack(feedSearchKeyboardGuard, query, searchPageConfirmed)) {
                stopWithError(getString(R.string.status_feed_keyboard_failed));
                return;
            }
            schedule(600L);
            return;
        }
        if (action == SearchKeyboardGuard.Action.WAIT) {
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        stopWithError(getString(R.string.status_feed_keyboard_failed));
    }

    private void waitForFeedNectarPanelClose(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        boolean detailOpen = FeedScreenAnalyzer.isPikminDetailOpen(tokens);
        if ((detailOpen || feedDetailCloseAttempts > 0)
                && closeFeedPikminDetailIfOpen(tokens)) {
            return;
        }
        boolean panelVisible = FeedScreenAnalyzer.isNectarPanelOpen(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        Integer nectarCount = FeedScreenAnalyzer.currentNectarCount(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        Integer expectedNectarCount = feedSelectedNectar == null
                ? null : feedSelectedNectar.count();
        if (FeedScreenAnalyzer.isSelectedNectarConfirmed(
                panelVisible, detailOpen, expectedNectarCount, nectarCount)) {
            feedPanelCloseWaitFrames = 0;
            feedPanelClosedConfirmationFrames++;
            statusFeed(getString(
                    R.string.status_feed_confirming_panel_closed,
                    feedPanelClosedConfirmationFrames,
                    FEED_REQUIRED_PANEL_CLOSED_FRAMES));
            if (feedPanelClosedConfirmationFrames < FEED_REQUIRED_PANEL_CLOSED_FRAMES) {
                schedule(FEED_OCR_RETRY_MILLIS);
                return;
            }
            feedNectarCandidate = null;
            feedSelectedNectar = null;
            feedNectarCandidateFrames = 0;
            feedNectarTapAttempts = 0;
            feedPanelClosedConfirmationFrames = 0;
            feedZoomReady = false;
            zoomOutBeforeFeedRound();
            return;
        }
        feedPanelClosedConfirmationFrames = 0;
        feedPanelCloseWaitFrames++;
        if (!panelVisible) {
            if (feedPanelCloseWaitFrames == 1) {
                Log.i(TAG, "FEED_NECTAR_SELECTION event=confirmation-pending"
                        + " target=" + (feedSelectedNectar == null
                                ? "<none>" : feedSelectedNectar.name())
                        + " expectedCount=" + expectedNectarCount
                        + " observedCount=" + nectarCount);
            }
            statusFeed(getString(R.string.status_feed_count_retry));
            if (feedPanelCloseWaitFrames >= FEED_MAX_TARGET_MISSING_FRAMES) {
                stopWithError(getString(R.string.status_feed_feed_view_failed));
            } else {
                schedule(FEED_OCR_RETRY_MILLIS);
            }
            return;
        }
        statusFeed(getString(R.string.status_feed_waiting_panel_close));
        if (feedPanelCloseWaitFrames < FEED_PANEL_CLOSE_RETRY_FRAMES) {
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        feedPanelCloseWaitFrames = 0;
        if (feedNectarTapAttempts >= FEED_MAX_NECTAR_TAP_ATTEMPTS) {
            stopWithError(getString(R.string.status_feed_panel_close_failed));
            return;
        }
        feedNectarCandidate = null;
        feedNectarCandidateFrames = 0;
        feedTargetMissingFrames = 0;
        feedStep = FeedStep.SELECTING_NECTAR;
        statusFeed(getString(
                R.string.status_feed_retrying_nectar_tap,
                feedNectarTapAttempts + 1,
                FEED_MAX_NECTAR_TAP_ATTEMPTS));
        schedule(FEED_OCR_RETRY_MILLIS);
    }

    /** 遊戲自動收合精華面板後，以雙指向中心內縮再開始餵食。 */
    private void zoomOutBeforeFeedRound() {
        zoomOutFeedView(() -> {
            feedStep = FeedStep.READING_NECTAR_COUNT;
            schedule(0L);
        });
    }

    private void zoomOutFeedView(Runnable completed) {
        ActionAdmission.FrameContext actionContext = currentActionContext();
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            stopWithError(getString(R.string.status_feed_zoom_failed));
            return;
        }
        FeedScreenAnalyzer.ZoomOutPinch pinch = FeedScreenAnalyzer.zoomOutPinch(
                bounds.width(), bounds.height());
        Path left = new Path();
        left.moveTo(bounds.left + pinch.leftStartX(), bounds.top + pinch.y());
        left.lineTo(bounds.left + pinch.leftEndX(), bounds.top + pinch.y());
        Path right = new Path();
        right.moveTo(bounds.left + pinch.rightStartX(), bounds.top + pinch.y());
        right.lineTo(bounds.left + pinch.rightEndX(), bounds.top + pinch.y());
        feedStep = FeedStep.ZOOMING_OUT;
        statusFeed(getString(R.string.status_feed_zooming));
        long generation = actionContext.runGeneration();
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        left, 0L, FEED_ZOOM_MILLIS))
                .addStroke(new GestureDescription.StrokeDescription(
                        right, 0L, FEED_ZOOM_MILLIS))
                .build();
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                gesture, actionContext, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    handler.postDelayed(() -> {
                        if (!isActiveRun(generation)) {
                            return;
                        }
                        requestFreshCaptureOnly(
                                "CAPTURE_ONLY_FEED_ZOOM",
                                () -> {
                                    feedZoomReady = true;
                                    completed.run();
                                },
                                () -> stopWithError(
                                        getString(R.string.status_feed_zoom_failed)));
                    }, FEED_ZOOM_SETTLE_MILLIS);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    stopWithError(getString(R.string.status_feed_zoom_failed));
                }
            }
        }, () -> stopWithError(getString(R.string.status_feed_zoom_failed)));
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED && isActiveRun(generation)) {
            stopWithError(getString(R.string.status_feed_zoom_failed));
        }
    }

    private void selectFeedNectar(FeedScreenAnalyzer.NectarSelection selection) {
        feedTargetMissingFrames = 0;
        feedNectarCandidate = null;
        feedNectarFreshFrameRequired = false;
        feedNectarCandidateFrames = 0;
        int effectiveLimit = feedSettings.effectivePetalLimit();
        Log.i(TAG, "FEED_NECTAR_SELECTION event=eligibility"
                + " target=" + selection.name()
                + " nectarCount=" + selection.count()
                + " petalCount=" + selection.petalCount()
                + " nectarMinimum=" + feedSettings.nectarMinimumThreshold()
                + " effectivePetalLimit=" + effectiveLimit);
        if (FeedScreenAnalyzer.hasReachedPetalLimit(
                selection.petalCount(), effectiveLimit)) {
            Log.i(TAG, "FEED_NECTAR_SELECTION event=skip reason=petal-limit");
            statusFeed(getString(
                    R.string.status_feed_petal_limit_reached,
                    selection.petalCount(),
                    effectiveLimit));
            if (advanceFeedNectar()) {
                schedule(200L);
            }
            return;
        }
        if (FeedScreenAnalyzer.shouldAdvanceNectar(
                selection.count(), feedSettings.nectarMinimumThreshold())) {
            Log.i(TAG, "FEED_NECTAR_SELECTION event=skip reason=nectar-minimum");
            if (advanceFeedNectar()) {
                schedule(FEED_OCR_RETRY_MILLIS);
            }
            return;
        }
        Log.i(TAG, "FEED_NECTAR_SELECTION event=tap"
                + " x=" + selection.x()
                + " y=" + selection.tapY()
                + " tapAttempt=" + (feedNectarTapAttempts + 1));
        statusFeed(getString(
                R.string.status_feed_selecting_nectar,
                selection.name(),
                selection.count(),
                selection.petalCount()));
        feedRequireNectarSearch = false;
        feedSelectedNectar = selection;
        feedNectarTapAttempts++;
        feedPanelCloseWaitFrames = 0;
        feedPanelClosedConfirmationFrames = 0;
        feedStep = FeedStep.WAITING_NECTAR_PANEL_CLOSE;
        dispatchTap(
                selection.x(),
                selection.tapY(),
                FEED_NECTAR_SELECT_TAP_MILLIS,
                () -> {
                    statusFeed(getString(R.string.status_feed_waiting_panel_close));
                    schedule(650L);
                },
                () -> stopWithError(getString(R.string.status_feed_select_failed)));
    }

    /** 依啟動時的序列快照前進一項；尾端完成，不循環。 */
    private boolean advanceFeedNectar() {
        if (feedFlowerSequenceIndex + 1 >= feedFlowerSequence.size()) {
            finishWithSuccess(getString(R.string.status_feed_no_more_nectar));
            return false;
        }
        String previous = feedTargetFlower;
        feedFlowerSequenceIndex++;
        String next = feedFlowerSequence.get(feedFlowerSequenceIndex);
        Log.i(TAG, "FEED_NECTAR_ADVANCE reason=threshold-or-petal-limit"
                + " from=" + FeedScreenAnalyzer.nectarDisplayName(previous)
                + " to=" + FeedScreenAnalyzer.nectarDisplayName(next)
                + " index=" + feedFlowerSequenceIndex
                + " sequenceSize=" + feedFlowerSequence.size());
        feedTargetFlower = next;
        feedRequireNectarSearch = true;
        feedTargetMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchActionAttempts = 0;
        feedSearchMissingFrames = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.CLOSING;
        feedNectarCandidate = null;
        feedSelectedNectar = null;
        feedNectarFreshFrameRequired = false;
        feedNectarCandidateFrames = 0;
        feedNectarTapAttempts = 0;
        feedPanelCloseWaitFrames = 0;
        feedPanelClosedConfirmationFrames = 0;
        feedNectarBeforeRound = -1;
        feedZoomReady = false;
        feedStep = FeedStep.OPENING_NECTAR;
        statusFeed(getString(R.string.status_feed_next_nectar, next));
        return true;
    }

    private void beginFeedGesture() {
        feedAttemptCount++;
        feedStep = FeedStep.FEEDING;
        statusFeed(getString(
                R.string.status_feed_feeding,
                feedRound + 1,
                feedSettings.feedsPerSquad(),
                feedAttemptCount,
                FEED_MAX_GESTURES_PER_ROUND));
        dispatchFeedHoldGesture(
                () -> {
                    feedNoEffectStartedAt = 0L;
                    long generation = runGeneration;
                    handler.postDelayed(() -> {
                        if (isActiveRun(generation)) {
                            startFeedSpiralHarvest();
                        }
                    }, FEED_SPIRAL_HANDOFF_MILLIS);
                },
                () -> {
                    if (feedAttemptCount >= FEED_MAX_GESTURES_PER_ROUND) {
                        readFeedConsumedNectar(false);
                    } else {
                        feedStep = FeedStep.READING_NECTAR_COUNT;
                        schedule(FEED_OCR_RETRY_MILLIS);
                    }
                });
    }

    private void readFeedConsumedNectar(boolean collectAfterCount) {
        feedCollectAfterCount = collectAfterCount;
        feedNoEffectStartedAt = 0L;
        feedStep = FeedStep.READING_CONSUMED_COUNT;
        statusFeed(getString(R.string.status_feed_counting_consumed));
        schedule(FEED_OCR_RETRY_MILLIS);
    }

    private void beginRecoveredFeedPetalCollection(
            FeedScreenAnalyzer.BloomTarget target) {
        feedStep = FeedStep.COLLECTING;
        feedCollectionOnlyRecovery = true;
        feedCollectedPetals = 0;
        feedCollectAfterCount = false;
        feedNoEffectStartedAt = 0L;
        feedCollectReturningFromDetail = false;
        feedCollectReturningFromShare = false;
        feedCollectReturnFrames = 0;
        prepareFeedSpiralHarvest();
        feedBloomCandidate = target;
        feedBloomCandidateFrames = FEED_REQUIRED_BLOOM_TARGET_FRAMES;
        resetFeedHoldState();
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            stopWithError(getString(R.string.status_feed_left_game));
            return;
        }
        ScreenCoordinateTransform.Point screenTarget = ScreenCoordinateTransform.toScreen(
                target.x(), target.y(), currentCaptureGeometry());
        FeedScreenAnalyzer.BloomTarget localTarget = new FeedScreenAnalyzer.BloomTarget(
                screenTarget.x() - bounds.left,
                screenTarget.y() - bounds.top,
                target.score());
        logFeedSpiral("petal-ready-recovery",
                "target=" + feedTargetFlower
                        + " x=" + target.x()
                        + " y=" + target.y()
                        + " screenX=" + screenTarget.x()
                        + " screenY=" + screenTarget.y());
        dispatchFeedSpiralHarvest(bounds, localTarget);
    }

    private boolean closeFeedPikminDetailIfOpen(List<PetalMatcher.Token> tokens) {
        if (!FeedScreenAnalyzer.isPikminDetailOpen(tokens)) {
            feedDetailCloseAttempts = 0;
            return false;
        }
        if (feedDetailCloseAttempts >= FEED_DETAIL_RETURN_FRAMES) {
            stopWithError(getString(R.string.status_feed_detail_close_failed));
            return true;
        }
        if (feedDetailCloseAttempts == 0) {
            feedDetailCloseAttempts = 1;
            statusFeed(getString(R.string.status_feed_closing_detail));
            if (!performGameGlobalAction(GLOBAL_ACTION_BACK)) {
                stopWithError(getString(R.string.status_feed_detail_close_failed));
            } else {
                schedule(FEED_DETAIL_CLOSE_SETTLE_MILLIS);
            }
        } else {
            feedDetailCloseAttempts++;
            statusFeed(getString(R.string.status_feed_detail_return_checking));
            schedule(FEED_OCR_RETRY_MILLIS);
        }
        return true;
    }

    private void startFeedSpiralHarvest() {
        feedStep = FeedStep.COLLECTING;
        statusFeed(getString(R.string.status_feed_collecting));
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            stopWithError(getString(R.string.status_feed_left_game));
            return;
        }
        feedCollectedPetals = 0;
        feedCollectReturningFromDetail = false;
        feedCollectReturningFromShare = false;
        feedCollectReturnFrames = 0;
        prepareFeedSpiralHarvest();
        resetFeedHoldState();
        schedule(0L);
    }

    private void resetFeedSpiralState() {
        feedBloomBaseline = null;
        prepareFeedSpiralHarvest();
    }

    private void prepareFeedSpiralHarvest() {
        feedCollectPhase = FeedCollectPhase.LOCATING_BLOOM;
        feedBloomCandidate = null;
        feedBloomCandidateFrames = 0;
        feedBloomMissingFrames = 0;
        feedHarvestReceiptWindowStartedAt = 0L;
        feedHarvestLastReceiptAt = 0L;
        feedSpiralGestureGain = 0;
        feedHarvestPreviousReceiptGain = null;
        feedHarvestReceiptVisible = false;
    }

    private void handleFeedPetalCollection(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (handleFeedCollectionOverlay(tokens, bitmap)) {
            return;
        }
        if (feedCollectPhase == FeedCollectPhase.LOCATING_BLOOM) {
            locateFeedSpiralStart(bitmap);
            return;
        }
        Integer receiptGain = FeedScreenAnalyzer.petalReceiptGain(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        receiveFeedSpiralReceipts(
                receiptGain, android.os.SystemClock.elapsedRealtime());
    }

    private void locateFeedSpiralStart(Bitmap bitmap) {
        List<FeedScreenAnalyzer.BloomTarget> targets = FeedScreenAnalyzer.findBloomTargets(
                feedBloomBaseline,
                bitmap.getWidth(),
                bitmap.getHeight(),
                bitmap::getPixel);
        FeedScreenAnalyzer.BloomTarget target =
                FeedScreenAnalyzer.nearestBloomTargetToCenter(
                        targets, bitmap.getWidth(), bitmap.getHeight());
        if (target == null) {
            feedBloomCandidate = null;
            feedBloomCandidateFrames = 0;
            feedBloomMissingFrames++;
            logFeedSpiral("target-missing",
                    "frame=" + feedBloomMissingFrames
                            + " max=" + FEED_MAX_BLOOM_TARGET_MISSING_FRAMES);
            if (feedBloomMissingFrames >= FEED_MAX_BLOOM_TARGET_MISSING_FRAMES) {
                stopWithError(getString(R.string.status_feed_collect_target_missing));
            } else {
                schedule(FEED_COLLECT_SCAN_MILLIS);
            }
            return;
        }
        feedBloomMissingFrames = 0;
        if (FeedScreenAnalyzer.isSameBloomTarget(
                feedBloomCandidate,
                target,
                bitmap.getWidth(),
                bitmap.getHeight())) {
            feedBloomCandidateFrames++;
        } else {
            feedBloomCandidateFrames = 1;
        }
        feedBloomCandidate = target;
        statusFeed(getString(
                R.string.status_feed_collect_target_confirming,
                feedBloomCandidateFrames,
                FEED_REQUIRED_BLOOM_TARGET_FRAMES));
        logFeedSpiral("target-candidate",
                "x=" + target.x() + " y=" + target.y()
                        + " score=" + target.score()
                        + " frames=" + feedBloomCandidateFrames);
        if (!FeedScreenAnalyzer.hasStableBloom(
                feedBloomCandidateFrames, FEED_REQUIRED_BLOOM_TARGET_FRAMES)) {
            schedule(FEED_COLLECT_SCAN_MILLIS);
            return;
        }
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            stopWithError(getString(R.string.status_feed_left_game));
            return;
        }
        ScreenCoordinateTransform.Point screenTarget = ScreenCoordinateTransform.toScreen(
                feedBloomCandidate.x(), feedBloomCandidate.y(), currentCaptureGeometry());
        FeedScreenAnalyzer.BloomTarget localTarget = new FeedScreenAnalyzer.BloomTarget(
                screenTarget.x() - bounds.left,
                screenTarget.y() - bounds.top,
                feedBloomCandidate.score());
        dispatchFeedSpiralHarvest(bounds, localTarget);
    }

    private void dispatchFeedSpiralHarvest(
            Rect bounds, FeedScreenAnalyzer.BloomTarget startTarget) {
        List<FeedScreenAnalyzer.SpiralPoint> points =
                FeedScreenAnalyzer.ellipticalSpiralFromTarget(
                        bounds.width(),
                        bounds.height(),
                        FEED_SPIRAL_SEGMENTS,
                        startTarget);
        if (points.isEmpty()) {
            stopWithError(getString(R.string.status_feed_collect_spiral_failed));
            return;
        }
        Path path = new Path();
        FeedScreenAnalyzer.SpiralPoint first = points.get(0);
        path.moveTo(bounds.left + first.x(), bounds.top + first.y());
        for (int index = 1; index < points.size(); index++) {
            FeedScreenAnalyzer.SpiralPoint point = points.get(index);
            path.lineTo(bounds.left + point.x(), bounds.top + point.y());
        }
        feedCollectPhase = FeedCollectPhase.SPIRALING;
        feedHarvestReceiptWindowStartedAt = android.os.SystemClock.elapsedRealtime();
        statusFeed(getString(R.string.status_feed_collect_spiral_swiping));
        logFeedSpiral("gesture-request",
                "bounds=" + bounds + " startX=" + first.x() + " startY=" + first.y()
                        + " points=" + points.size());
        dispatchPath(
                path,
                FEED_SPIRAL_HARVEST_MILLIS,
                () -> {
                    feedCollectPhase = FeedCollectPhase.RECEIVING_SPIRAL_RECEIPTS;
                    feedHarvestReceiptWindowStartedAt =
                            android.os.SystemClock.elapsedRealtime();
                    logFeedSpiral("gesture-completed",
                            "gestureGain=" + feedSpiralGestureGain);
                    schedule(FEED_COLLECT_SCAN_MILLIS);
                },
                () -> stopWithError(getString(R.string.status_feed_collect_spiral_failed)));
        schedule(FEED_COLLECT_SCAN_MILLIS);
    }

    private void receiveFeedSpiralReceipts(Integer receiptGain, long now) {
        int newGain = FeedScreenAnalyzer.newPetalReceiptGain(
                receiptGain, feedHarvestPreviousReceiptGain, feedHarvestReceiptVisible);
        if (receiptGain == null) {
            feedHarvestReceiptVisible = false;
        } else {
            feedHarvestPreviousReceiptGain = receiptGain;
            feedHarvestReceiptVisible = true;
        }
        if (newGain > 0) {
            feedSpiralGestureGain += newGain;
            feedHarvestLastReceiptAt = now;
            recordFeedPetalGain(newGain);
            logFeedSpiral("receipt",
                    "gain=" + newGain + " totalGain=" + feedCollectedPetals);
        }
        if (feedCollectPhase == FeedCollectPhase.SPIRALING) {
            schedule(FEED_COLLECT_SCAN_MILLIS);
            return;
        }
        long elapsed = now - feedHarvestReceiptWindowStartedAt;
        boolean quiet = feedHarvestLastReceiptAt == 0L
                || now - feedHarvestLastReceiptAt >= FEED_HARVEST_RECEIPT_QUIET_MILLIS;
        if (elapsed < FEED_HARVEST_RECEIPT_MAX_WINDOW_MILLIS
                && (elapsed < FEED_HARVEST_RECEIPT_WINDOW_MILLIS || !quiet)) {
            statusFeed(getString(
                    R.string.status_feed_collect_spiral_receiving,
                    feedSpiralGestureGain,
                    feedCollectedPetals));
            schedule(FEED_COLLECT_SCAN_MILLIS);
            return;
        }
        if (feedCollectionOnlyRecovery) {
            completeFeedPetalCollection();
        } else {
            readFeedConsumedNectar(true);
        }
    }

    private void recordFeedPetalGain(int gain) {
        feedCollectedPetals += gain;
        if (usageSession != null) {
            usageSession.recordFeedPetals(gain);
        }
        statusFeed(getString(
                R.string.status_feed_collect_receipt,
                gain,
                feedCollectedPetals));
    }


    /** 分享預覽與皮克敏詳情是兩層畫面，每次只返回一層並確認。 */
    private boolean handleFeedCollectionOverlay(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        boolean shareOpen = FeedScreenAnalyzer.isSharePreviewOpen(tokens);
        boolean detailOpen = FeedScreenAnalyzer.isPikminDetailOpen(tokens);
        if (feedCollectReturningFromShare) {
            if (!shareOpen) {
                feedCollectReturningFromShare = false;
                feedCollectReturnFrames = 0;
                schedule(FEED_COLLECT_SCAN_MILLIS);
            } else if (++feedCollectReturnFrames >= FEED_DETAIL_RETURN_FRAMES) {
                stopWithError(getString(R.string.status_feed_share_close_failed));
            } else {
                statusFeed(getString(R.string.status_feed_share_return_checking));
                schedule(FEED_OCR_RETRY_MILLIS);
            }
            return true;
        }
        if (shareOpen) {
            feedCollectReturningFromShare = true;
            feedCollectReturnFrames = 0;
            statusFeed(getString(R.string.status_feed_closing_share));
            if (!performGameGlobalAction(GLOBAL_ACTION_BACK)) {
                stopWithError(getString(R.string.status_feed_share_close_failed));
            } else {
                schedule(FEED_DETAIL_CLOSE_SETTLE_MILLIS);
            }
            return true;
        }
        if (feedCollectReturningFromDetail) {
            Integer nectar = detailOpen ? null : FeedScreenAnalyzer.currentNectarCount(
                    tokens, bitmap.getWidth(), bitmap.getHeight());
            if (nectar != null) {
                feedCollectReturningFromDetail = false;
                feedCollectReturnFrames = 0;
                schedule(FEED_COLLECT_SCAN_MILLIS);
                return true;
            }
            if (++feedCollectReturnFrames >= FEED_DETAIL_RETURN_FRAMES) {
                stopWithError(getString(R.string.status_feed_detail_close_failed));
            } else {
                statusFeed(getString(R.string.status_feed_detail_return_checking));
                schedule(FEED_OCR_RETRY_MILLIS);
            }
            return true;
        }
        if (!detailOpen) {
            return false;
        }
        feedCollectReturningFromDetail = true;
        feedCollectReturnFrames = 0;
        logFeedSpiral("detail-return",
                "phase=" + feedCollectPhase);
        statusFeed(getString(R.string.status_feed_closing_detail));
        if (!performGameGlobalAction(GLOBAL_ACTION_BACK)) {
            stopWithError(getString(R.string.status_feed_detail_close_failed));
        } else {
            schedule(FEED_DETAIL_CLOSE_SETTLE_MILLIS);
        }
        return true;
    }

    private void logFeedSpiral(String event, String details) {
        Log.i(TAG, "FEED_SPIRAL event=" + event + " " + details);
    }

    private void completeFeedPetalCollection() {
        if (feedCollectedPetals <= 0) {
            stopWithError(getString(R.string.status_feed_collect_spiral_failed));
            return;
        }
        feedRound++;
        feedAttemptCount = 0;
        feedNectarBeforeRound = -1;
        feedCollectAfterCount = false;
        feedCollectionOnlyRecovery = false;
        feedNoEffectStartedAt = 0L;
        feedDetailCloseAttempts = 0;
        feedZoomReady = false;
        feedCollectedPetals = 0;
        feedCollectReturningFromDetail = false;
        feedCollectReturningFromShare = false;
        feedCollectReturnFrames = 0;
        resetFeedSpiralState();
        if (feedRound >= feedSettings.feedsPerSquad()) {
            switchFeedSquad(false);
        } else {
            prepareFeedSearchForCurrentTarget();
            statusFeed(getString(
                    R.string.status_feed_round_complete,
                    feedRound,
                    feedSettings.feedsPerSquad()));
            schedule(FEED_OCR_RETRY_MILLIS);
        }
    }

    private void switchFeedSquad(boolean failedRound) {
        if (feedSquadSwitchCount >= feedSettings.maxSquadSwitches()) {
            if (failedRound) {
                stopWithError(getString(R.string.status_feed_no_effect_stopped));
            } else {
                finishWithSuccess(getString(
                        R.string.status_feed_completed,
                        feedSquadSwitchCount,
                        feedSettings.maxSquadSwitches()));
            }
            return;
        }
        feedStep = FeedStep.SWITCHING;
        feedZoomReady = false;
        statusFeed(getString(
                R.string.status_feed_switching,
                feedSquadSwitchCount + 1,
                feedSettings.maxSquadSwitches()));
        tapFeedWhistle(1, currentActionContext());
    }

    private void tapFeedWhistle(
            int tapNumber, ActionAdmission.FrameContext actionContext) {
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            stopWithError(getString(R.string.status_feed_left_game));
            return;
        }
        dispatchScreenTap(
                Math.round(bounds.left + bounds.width() * 0.87f),
                Math.round(bounds.top + bounds.height() * 0.915f),
                90L,
                actionContext,
                () -> {
                    if (tapNumber < FEED_WHISTLE_TAP_COUNT) {
                        handler.postDelayed(
                                () -> tapFeedWhistle(tapNumber + 1, actionContext),
                                FEED_WHISTLE_TAP_GAP_MILLIS);
                        return;
                    }
                    feedSquadSwitchCount++;
                    feedRound = 0;
                    feedAttemptCount = 0;
                    feedNectarBeforeRound = -1;
                    feedCollectAfterCount = false;
                    feedNoEffectStartedAt = 0L;
                    feedDetailCloseAttempts = 0;
                    feedZoomReady = false;
                    prepareFeedSearchForCurrentTarget();
                    handler.postDelayed(() -> schedule(0L), FEED_SQUAD_SETTLE_MILLIS);
                },
                () -> stopWithError(getString(R.string.status_feed_whistle_failed)));
    }

    private void prepareFeedSearchForCurrentTarget() {
        nectarTemplateMatcher.clearCache();
        feedRequireNectarSearch = true;
        feedTargetMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchActionAttempts = 0;
        feedSearchMissingFrames = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.CLOSING;
        feedNectarCandidate = null;
        feedSelectedNectar = null;
        feedNectarFreshFrameRequired = false;
        feedNectarCandidateFrames = 0;
        feedNectarTapAttempts = 0;
        feedPanelCloseWaitFrames = 0;
        feedPanelClosedConfirmationFrames = 0;
        feedNectarBeforeRound = -1;
        feedZoomReady = false;
        feedStep = FeedStep.OPENING_NECTAR;
    }

    /** 將精華向上拖曳後分段持續按住；每段完成後依精華 OCR 回執決定是否放開。 */
    private void dispatchFeedHoldGesture(Runnable completed, Runnable failed) {
        if (!feedHoldLifecycle.isIdle()) {
            failed.run();
            return;
        }
        ActionAdmission.FrameContext actionContext = currentActionContext();
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            failed.run();
            return;
        }
        float startX = bounds.left + bounds.width() * 0.52f;
        float startY = bounds.top + bounds.height() * 0.90f;
        float endX = bounds.left + bounds.width() * 0.50f;
        float endY = bounds.top + bounds.height() * 0.54f;
        Path drag = new Path();
        drag.moveTo(startX, startY);
        drag.lineTo(endX, endY);
        GestureDescription.StrokeDescription dragStroke =
                new GestureDescription.StrokeDescription(drag, 0L, FEED_DRAG_MILLIS, true);
        resetFeedHoldState();
        feedHoldLifecycle.beginStart();
        feedHoldX = endX;
        feedHoldY = endY;
        feedHoldLastCount = feedNectarBeforeRound >= 0 ? feedNectarBeforeRound : null;
        feedHoldCompleted = completed;
        feedHoldFailed = failed;
        feedHoldActionContext = actionContext;
        feedHoldOriginContext = actionContext;
        long generation = actionContext.runGeneration();
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(dragStroke)
                .build();
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                ActionGateway.Kind.CONTINUED_GESTURE,
                gesture, actionContext, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (!isActiveRun(generation)) {
                    return;
                }
                if (!feedHoldLifecycle.markContinuedStrokeActive()) {
                    return;
                }
                feedHoldStroke = dragStroke;
                feedHoldStartedAt = android.os.SystemClock.elapsedRealtime();
                requestFreshCaptureOnly(
                        "CAPTURE_ONLY_FEED_HOLD_STAGE",
                        () -> {
                            if (isActiveRun(generation)
                                    && feedHoldLifecycle.canContinue()
                                    && !feedHoldReleasing) {
                                dispatchNextFeedHoldSlice(currentActionContext());
                            }
                        },
                        () -> stopAfterFeedHoldCleanupFailure(generation));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    finishFeedHoldGesture(false);
                }
            }
        }, () -> finishFeedHoldGesture(false));
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED) {
            finishFeedHoldGesture(false);
        }
    }

    private void dispatchNextFeedHoldSlice(ActionAdmission.FrameContext actionContext) {
        if (feedHoldStroke == null
                || !feedHoldLifecycle.canContinue()
                || feedHoldReleasing) {
            return;
        }
        if (actionContext == null) {
            handleFeedHoldContinuationFailure();
            return;
        }
        if (!FeedHoldLifecycle.sameWindowForCleanup(feedHoldOriginContext, actionContext)) {
            finishFeedHoldStateWithoutWorkflow();
            stopWithError(getString(R.string.status_feed_select_failed));
            return;
        }
        feedHoldActionContext = actionContext;
        long elapsed = android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt;
        if (elapsed >= FEED_HOLD_MAX_MILLIS) {
            releaseFeedHoldGesture("timeout", actionContext);
            return;
        }
        long duration = Math.min(FEED_HOLD_SAMPLE_MILLIS, FEED_HOLD_MAX_MILLIS - elapsed);
        float nextX = feedHoldX + feedHoldDirection;
        Path hold = feedHoldPath(nextX);
        GestureDescription.StrokeDescription nextStroke = feedHoldStroke.continueStroke(
                hold, 0L, Math.max(1L, duration), true);
        long generation = actionContext.runGeneration();
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                ActionGateway.Kind.CONTINUED_GESTURE,
                new GestureDescription.Builder().addStroke(nextStroke).build(),
                actionContext,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (!isActiveRun(generation) || feedHoldReleasing) {
                            return;
                        }
                        feedHoldStroke = nextStroke;
                        feedHoldX = nextX;
                        feedHoldDirection = -feedHoldDirection;
                        schedule(0L);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            handleFeedHoldContinuationFailure();
                        }
                    }
                },
                this::handleFeedHoldContinuationFailure);
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED) {
            handleFeedHoldContinuationFailure();
        }
    }

    private void observeFeedHold(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (feedHoldStroke == null || !feedHoldLifecycle.canContinue() || feedHoldReleasing) {
            return;
        }
        ActionAdmission.FrameContext actionContext = currentActionContext();
        feedHoldActionContext = actionContext;
        long elapsed = android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt;
        Integer current = FeedScreenAnalyzer.currentNectarCount(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        FeedScreenAnalyzer.NectarHoldProgress progress = FeedScreenAnalyzer.observeNectarHold(
                feedHoldLastCount,
                feedHoldStableReads,
                current,
                elapsed,
                FEED_HOLD_MIN_MILLIS,
                FEED_HOLD_MAX_MILLIS,
                FEED_HOLD_REQUIRED_STABLE_READS);
        feedHoldLastCount = progress.count();
        feedHoldStableReads = progress.stableReads();
        logFeedSpiral("hold-observation",
                "elapsedMs=" + elapsed
                        + " count=" + current
                        + " stableReads=" + feedHoldStableReads);
        if (progress.shouldRelease()) {
            releaseFeedHoldGesture(
                    elapsed >= FEED_HOLD_MAX_MILLIS ? "timeout" : "stable",
                    actionContext);
            return;
        }
        if (current == null) {
            statusFeed(getString(R.string.status_feed_count_retry));
        }
        dispatchNextFeedHoldSlice(actionContext);
    }

    private void releaseFeedHoldGesture(String reason) {
        releaseFeedHoldGesture(reason, feedHoldActionContext);
    }

    private void releaseFeedHoldGesture(
            String reason, ActionAdmission.FrameContext actionContext) {
        if (feedHoldStroke == null || !feedHoldLifecycle.canContinue() || feedHoldReleasing) {
            return;
        }
        if (actionContext == null) {
            handleFeedHoldContinuationFailure();
            return;
        }
        if (!FeedHoldLifecycle.sameWindowForCleanup(feedHoldOriginContext, actionContext)) {
            finishFeedHoldStateWithoutWorkflow();
            stopWithError(getString(R.string.status_feed_select_failed));
            return;
        }
        ActionAdmission.Decision admission = evaluateActionAdmission(actionContext);
        recordActionAdmissionDiagnostic(actionContext, admission);
        if (!admission.allowed()) {
            handleFeedHoldContinuationFailure();
            return;
        }
        feedHoldActionContext = actionContext;
        feedHoldReleasing = true;
        logFeedSpiral("hold-release",
                "reason=" + reason
                        + " elapsedMs="
                        + (android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt)
                        + " count=" + feedHoldLastCount
                        + " stableReads=" + feedHoldStableReads);
        GestureDispatchResult dispatchResult = dispatchFeedHoldTerminalCleanup(
                actionContext,
                () -> finishFeedHoldGesture(true),
                () -> stopAfterFeedHoldCleanupFailure(actionContext.runGeneration()));
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED) {
            stopAfterFeedHoldCleanupFailure(actionContext.runGeneration());
        }
    }

    /** A rejected continuation gets one fresh window context before terminal cleanup. */
    private void handleFeedHoldContinuationFailure() {
        if (!feedHoldLifecycle.beginAbort()) {
            return;
        }
        feedHoldReleasing = true;
        busy = false;
        long generation = runGeneration;
        requestFreshCaptureOnly(
                "CAPTURE_ONLY_FEED_HOLD_CLEANUP",
                () -> {
                    if (!isActiveRun(generation) || !feedHoldLifecycle.isCleanupPending()) {
                        return;
                    }
                    ActionAdmission.FrameContext freshContext = currentActionContext();
                    if (!FeedHoldLifecycle.sameWindowForCleanup(
                            feedHoldOriginContext, freshContext)) {
                        finishFeedHoldStateWithoutWorkflow();
                        stopWithError(getString(R.string.status_feed_select_failed));
                        return;
                    }
                    dispatchFeedHoldTerminalCleanup(
                            freshContext,
                            () -> finishFeedHoldGesture(false),
                            () -> stopAfterFeedHoldCleanupFailure(generation));
                },
                () -> stopAfterFeedHoldCleanupFailure(generation));
    }

    private void stopAfterFeedHoldCleanupFailure(long generation) {
        if (!isActiveRun(generation)) {
            finishFeedHoldStateWithoutWorkflow();
            return;
        }
        finishFeedHoldStateWithoutWorkflow();
        stopWithError(getString(R.string.status_feed_select_failed));
    }

    private GestureDispatchResult dispatchFeedHoldTerminalCleanup(
            ActionAdmission.FrameContext actionContext,
            Runnable completed,
            Runnable failed) {
        if (actionContext == null
                || feedHoldStroke == null
                || !FeedHoldLifecycle.sameWindowForCleanup(
                        feedHoldOriginContext, actionContext)
                || !feedHoldLifecycle.beginTerminalCleanup()) {
            if (failed != null) {
                failed.run();
            }
            return GestureDispatchResult.STALE;
        }
        float releaseX = feedHoldX + feedHoldDirection;
        GestureDescription.StrokeDescription releaseStroke = feedHoldStroke.continueStroke(
                feedHoldPath(releaseX), 0L, 1L, false);
        long generation = actionContext.runGeneration();
        return dispatchGestureSafely(
                ActionGateway.Kind.CONTINUED_GESTURE,
                new GestureDescription.Builder().addStroke(releaseStroke).build(),
                actionContext,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            completed.run();
                        } else {
                            finishFeedHoldStateWithoutWorkflow();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            failed.run();
                        } else {
                            finishFeedHoldStateWithoutWorkflow();
                        }
                    }
                },
                failed);
    }

    private Path feedHoldPath(float endX) {
        Path path = new Path();
        path.moveTo(feedHoldX, feedHoldY);
        path.lineTo(endX, feedHoldY);
        return path;
    }

    private void finishFeedHoldGesture(boolean succeeded) {
        boolean resolved = feedHoldLifecycle.isStarting()
                ? feedHoldLifecycle.abortWithoutTerminal()
                : feedHoldLifecycle.resolveTerminalCleanup();
        if (!resolved) {
            return;
        }
        Runnable callback = succeeded ? feedHoldCompleted : feedHoldFailed;
        resetFeedHoldState();
        if (callback != null) {
            callback.run();
        }
    }

    /** Stops an active continued stroke before lifecycle state is reset. */
    private void abortFeedHoldForLifecycle() {
        if (feedHoldLifecycle.isIdle()) {
            return;
        }
        if (!feedHoldLifecycle.isContinuedStrokeActive()) {
            finishFeedHoldStateWithoutWorkflow();
            return;
        }
        ActionAdmission.FrameContext context = feedHoldActionContext;
        ActionAdmission.Decision admission = context == null
                ? new ActionAdmission.Decision(
                        false, ActionAdmission.RejectionReason.INVALID_CONTEXT)
                : evaluateActionAdmission(context);
        if (context != null) {
            recordActionAdmissionDiagnostic(context, admission);
        }
        if (context != null
                && admission.allowed()
                && FeedHoldLifecycle.sameWindowForCleanup(feedHoldOriginContext, context)) {
            GestureDispatchResult result = dispatchFeedHoldTerminalCleanup(
                    context,
                    this::finishFeedHoldStateWithoutWorkflow,
                    this::finishFeedHoldStateWithoutWorkflow);
            // Pause invalidates callbacks immediately. A successfully submitted terminal
            // stroke is therefore resolved locally here; a rejected one is idempotently
            // resolved by the failure callback above.
            if (result != GestureDispatchResult.SENT
                    || !feedHoldLifecycle.isIdle()) {
                finishFeedHoldStateWithoutWorkflow();
            }
            return;
        }
        // There is no public AccessibilityService cancelStroke API. Never synthesize a
        // terminal gesture for an unknown window; invalidate local continuation ownership.
        finishFeedHoldStateWithoutWorkflow();
    }

    /** Clears a hold only after its terminal dispatch or local abort is resolved. */
    private void finishFeedHoldStateWithoutWorkflow() {
        if (!feedHoldLifecycle.isIdle()) {
            feedHoldLifecycle.abortWithoutTerminal();
        }
        feedHoldCompleted = null;
        feedHoldFailed = null;
        resetFeedHoldState();
    }

    private void resetFeedHoldState() {
        if (!feedHoldLifecycle.isIdle()) {
            return;
        }
        feedHoldStroke = null;
        feedHoldActionContext = null;
        feedHoldOriginContext = null;
        feedHoldX = 0f;
        feedHoldY = 0f;
        feedHoldDirection = 1f;
        feedHoldStartedAt = 0L;
        feedHoldLastCount = null;
        feedHoldStableReads = 0;
        feedHoldReleasing = false;
        feedHoldCompleted = null;
        feedHoldFailed = null;
    }

    private void statusFeed(String message) {
        setStatus(message);
        setRunStatus(
                AutomationMode.FEED,
                OverlayRunStatus.Kind.RECOGNIZING,
                message,
                getString(
                        R.string.overlay_feed_progress,
                        feedRound,
                        feedSettings.feedsPerSquad(),
                        feedSquadSwitchCount,
                        feedSettings.maxSquadSwitches()));
    }

    /**  派遣頁面順序：清單 → 詳細頁 → 選皮 → GO → 結果 → 清單。 */
    private void handleExpeditionDispatch(OcrScan.Frame frame, Bitmap bitmap) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        if (expeditionDispatchSession == null || activeGameBoundsStrict() == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (ExpeditionScreenAnalyzer.isReturnedExpeditionDialog(tokens)) {
            // Return Reward owns collection; Expedition must not consume its dialog.
            stopWithError(getString(R.string.status_reward_return_reward_required));
            return;
        }
        ExpeditionScreenAnalyzer.Screen ocrScreen = ExpeditionScreenAnalyzer.classify(tokens);
        ExpeditionScreenAnalyzer.Screen screen = ExpeditionScreenAnalyzer.classifyWithBaseScreen(
                ocrScreen, tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        ExpeditionDispatchSession.Stage previousStage = expeditionDispatchSession.stage();
        int previousDetailTapAttempts = previousStage == ExpeditionDispatchSession.Stage.DETAIL
                ? expeditionDispatchSession.detailTapAttempts() : 0;
        if ((previousStage == ExpeditionDispatchSession.Stage.WAIT_RESULT
                        || previousStage == ExpeditionDispatchSession.Stage.VERIFY_RETURN)
                && ocrScreen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            // The existing VERIFY_RETURN confirmation requires this strong OCR result twice.
            screen = ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST;
        }
        expeditionDispatchSession.advanceForVerifiedScreen(screen, now);
        if (previousStage == ExpeditionDispatchSession.Stage.SELECTION
                && (screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN
                        || screen == ExpeditionScreenAnalyzer.Screen.RESULT)
                && ExpeditionScreenAnalyzer.findResultClose(bitmap) != null) {
            if (expeditionDispatchSession.observePostGo(
                    true, frame.captureGeometry().captureSequence(), now)
                    && usageSession != null) {
                // Count once at confirmed dispatch, even if closing/returning later fails.
                if (dispatchCurrentItemKind == ExpeditionScreenAnalyzer.ItemKind.FRUIT) {
                    usageSession.recordDispatchFruit();
                } else if (dispatchCurrentItemKind == ExpeditionScreenAnalyzer.ItemKind.POT) {
                    usageSession.recordDispatchPot();
                }
            }
        }
        ExpeditionDispatchSession.Stage stage = expeditionDispatchSession.stage();
        if (previousStage == ExpeditionDispatchSession.Stage.DETAIL
                && stage == ExpeditionDispatchSession.Stage.SELECTION) {
            recordDispatchDetailDiagnostic(
                    "selection-confirmed", "destination-confirmed", "unknown",
                    previousDetailTapAttempts, false,
                    bitmap.getWidth(), bitmap.getHeight(), null);
        }
        if (previousStage != stage) {
            Log.i(TAG, "DISPATCH_STAGE from=" + previousStage
                    + " to=" + stage + " screen=" + screen + " ocrScreen=" + ocrScreen);
        }
        boolean recoverableExploreList = stage == ExpeditionDispatchSession.Stage.LIST_SEARCH
                && screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN
                && ExpeditionScreenAnalyzer.hasExploreNavigationAnchor(
                        tokens, bitmap.getWidth(), bitmap.getHeight());
        if (screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN && !recoverableExploreList) {
            dispatchUnknownFrames++;
        } else {
            dispatchUnknownFrames = 0;
        }
        if (screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN) {
            logDispatchUnknownFrame(tokens, stage, ocrScreen, recoverableExploreList);
        }
        if (dispatchUnknownFrames >= 8
                && stage != ExpeditionDispatchSession.Stage.WAIT_RESULT
                && !expeditionDispatchSession.transitionPending()) {
            stopWithError(getString(R.string.status_reward_unknown_page));
            return;
        }

        switch (stage) {
            case LIST_SEARCH -> handleDispatchList(frame, bitmap, screen, now);
            case DETAIL -> handleDispatchDetail(tokens, bitmap, screen, now);
            case SELECTION -> handleDispatchSelection(tokens, bitmap, screen, now);
            case WAIT_RESULT -> handleDispatchResult(tokens, bitmap, screen, now);
            case VERIFY_RETURN -> handleDispatchReturn(tokens, screen, now);
        }
    }

    private void logDispatchUnknownFrame(
            List<PetalMatcher.Token> tokens,
            ExpeditionDispatchSession.Stage stage,
            ExpeditionScreenAnalyzer.Screen ocrScreen,
            boolean recoverableExploreList) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        for (PetalMatcher.Token token : tokens) {
            if (summary.length() >= 1500) {
                break;
            }
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append(String.valueOf(token.text()).replace('\n', ' ').replace('\r', ' '))
                    .append('@').append(token.left()).append(',').append(token.top())
                    .append(',').append(token.right()).append(',').append(token.bottom());
        }
        Log.d(TAG, "DISPATCH_SCREEN_UNKNOWN stage=" + stage
                + " ocrScreen=" + ocrScreen
                + " recoverableExploreList=" + recoverableExploreList
                + " unknownFrames=" + dispatchUnknownFrames
                + " tokens=" + summary);
    }

    private void handleDispatchList(OcrScan.Frame frame, Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen, long now) {
        // Non-pending list captures enter the visual path before OCR in handleCapturedBitmap.
        if (screen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            ExpeditionDispatchSession.CandidateRetry retry = expeditionDispatchSession.candidateStillOnList(now);
            if (retry != ExpeditionDispatchSession.CandidateRetry.WAIT) {
                waitForDispatchFrame(getString(R.string.status_reward_scanning));
                return;
            }
        }
        handleDispatchConfirmation(expeditionDispatchSession.confirm("", now));
        waitForDispatchFrame(getString(R.string.status_reward_opening_detail));
    }

    private void scanDispatchVisualCandidates(Bitmap bitmap, CaptureGeometry geometry,
            long generation, long epoch) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        boolean normalizing = expeditionDispatchSession.normalizing();
        int[] pixels;
        try {
            pixels = snapshotBitmapPixels(bitmap);
        } catch (RuntimeException failure) {
            busy = false;
            stopWithError(getString(R.string.status_copy_failed));
            return;
        } finally {
            bitmap.recycle();
        }
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        dispatchVisionCancellation = cancelled;
        boolean submitted = frameAnalysisExecutor.submit(() -> {
            ExpeditionVision.Surface surface;
            List<DispatchVisualCandidate> candidates = new ArrayList<>();
            try {
                if (cancelled.get()) return;
                surface = ExpeditionVision.detect(width, height, (x, y) -> pixels[y * width + x]);
                if (surface != null && !normalizing) for (ExpeditionVision.Icon icon : surface.icons()) {
                    if (cancelled.get()) return;
                    if (!icon.cardContext()) continue;
                    ExpeditionRecognition.Scores[] scores = expeditionTemplateMatcher.match(
                            icon.bounds(), (x, y) -> pixels[y * width + x]);
                    candidates.add(new DispatchVisualCandidate(icon, scores[0], scores[1],
                            ExpeditionRecognition.visual(scores[0], scores[1])));
                }
            } catch (RuntimeException failure) {
                handler.post(() -> {
                    if (!cancelled.get() && isActiveRun(generation)) {
                        busy = false;
                        stopWithError(getString(R.string.status_reward_unknown_page));
                    }
                });
                return;
            }
            ExpeditionVision.Surface detected = surface;
            handler.post(() -> {
                if (cancelled.get() || !isActiveRun(generation)) return;
                if (dispatchVisionCancellation == cancelled) dispatchVisionCancellation = null;
                Log.i(TAG, "EXPEDITION_SCORE_CACHE " + expeditionTemplateMatcher.observationStats());
                if (detected == null) {
                    retryDispatchVisualFrame();
                    return;
                }
                for (DispatchVisualCandidate candidate : candidates) {
                    recordDispatchRecognition(candidate.fruit(), candidate.icon(), geometry.captureSequence(), generation,
                            candidate.classification(), candidate.classification());
                    recordDispatchRecognition(candidate.seedling(), candidate.icon(), geometry.captureSequence(), generation,
                            candidate.classification(), candidate.classification());
                }
                Bitmap source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                ExpeditionVision.Bounds tab = detected.selectedTab();
                ScreenCoordinateTransform.ScreenshotRect header = new ScreenCoordinateTransform.ScreenshotRect(
                        0, tab.top(), width, Math.min(height, detected.content().top() + tab.height() * 3));
                startOcrTransaction(source, OcrScan.Profile.TARGETED_CHINESE, geometry, epoch,
                        "dispatch-header", true, (transaction, frame) -> runWithActionContext(transaction, frame, () -> {
                            recordOcrDiagnostic("dispatch-header", frame);
                            boolean expeditionTab = frame.tokens().stream().anyMatch(token ->
                                    ExpeditionScreenAnalyzer.isExploreTabLabel(token.text())
                                    && token.centerX() >= tab.left() && token.centerX() < tab.right()
                                    && token.centerY() >= tab.top() && token.centerY() < tab.bottom());
                            if (!expeditionTab) {
                                Log.i(TAG, "EXPEDITION_HEADER_MISSING frame=" + geometry.captureSequence()
                                        + " tab=" + tab + " tokens=" + frame.tokens().stream()
                                        .filter(token -> token.centerY() >= tab.top() && token.centerY() < tab.bottom())
                                        .map(token -> ExpeditionScreenAnalyzer.normalize(token.text())
                                                + "@" + token.centerX() + "," + token.centerY())
                                        .collect(java.util.stream.Collectors.joining("|")));
                                retryDispatchVisualFrame(); return;
                            }
                            dispatchUnknownFrames = 0;
                            long now = android.os.SystemClock.elapsedRealtime();
                            long signature = ExpeditionVision.viewportSignature(detected, (x, y) -> pixels[y * width + x]);
                            boolean expanded = ExpeditionScreenAnalyzer.isExplorePanelExpanded(
                                    frame.tokens(), width, height);
                            if (dispatchReturnRevealPending) {
                                scrollDispatchList(detected, signature, false, true);
                                return;
                            }
                            long priorEpoch = expeditionDispatchSession.viewportEpoch();
                            ExpeditionDispatchSession.ListDecision decision = expeditionDispatchSession.observeList(
                                    geometry.captureSequence(), signature,
                                    expanded,
                                    detected.scrollbar() != null,
                                    detected.scrollbar() != null && detected.scrollbar().atTop(),
                                    detected.scrollbar() != null && detected.scrollbar().atBottom(),
                                    candidates.stream().anyMatch(candidate -> dispatchCandidateActionable(candidate, width, height)), now);
                            Log.i(TAG, "EXPEDITION_SWEEP decision=" + decision + " frame=" + geometry.captureSequence()
                                    + " normalizing=" + expeditionDispatchSession.normalizing()
                                    + " signature=" + signature + " epoch=" + expeditionDispatchSession.viewportEpoch()
                                    + " scrollbar=" + detected.scrollbar());
                            if (priorEpoch != expeditionDispatchSession.viewportEpoch()) {
                                dispatchInspectedVisual.clear(); expeditionRecognitionConsensus.reset();
                            }
                            switch (decision) {
                                case EXPAND -> revealDispatchExplorePanel(detected);
                                case SCROLL_UP -> scrollDispatchList(detected, signature, true);
                                case WAIT -> waitForDispatchFrame(getString(R.string.status_reward_scanning));
                                case COMPLETE -> finishDispatchSweep();
                                case ABORT -> stopWithError(getString(R.string.status_reward_stage_timeout));
                                case SCAN -> {
                                    if (normalizing) {
                                        waitForDispatchFrame(getString(R.string.status_reward_scanning));
                                    } else {
                                        selectDispatchVisualCandidate(candidates, detected, pixels, width, height,
                                                geometry, generation, epoch, signature, now);
                                    }
                                }
                            }
                        }), error -> retryDispatchVisualFrame(), header);
            });
        });
        if (!submitted) {
            cancelled.set(true);
            dispatchVisionCancellation = null;
            retryDispatchVisualFrame();
        }
    }

    private void retryDispatchVisualFrame() {
        busy = false;
        if (!running || automationMode != AutomationMode.DISPATCH) return;
        if (++dispatchUnknownFrames >= 8) {
            stopWithError(getString(R.string.status_reward_unknown_page));
        } else {
            waitForDispatchFrame(getString(R.string.status_reward_confirming));
        }
    }

    private boolean dispatchCandidateActionable(DispatchVisualCandidate candidate, int width, int height) {
        ExpeditionScreenAnalyzer.ItemKind kind = switch (candidate.classification()) {
            case FRUIT -> ExpeditionScreenAnalyzer.ItemKind.FRUIT;
            case SEEDLING -> ExpeditionScreenAnalyzer.ItemKind.POT;
            default -> null;
        };
        if (kind == null || !expeditionTargetMode.accepts(kind)) return false;
        String identity = kind == ExpeditionScreenAnalyzer.ItemKind.FRUIT
                ? candidate.fruit().bestTemplate() : candidate.seedling().bestTemplate();
        ExpeditionVision.Bounds bounds = candidate.icon().bounds();
        return !expeditionDispatchSession.isQuarantined(new ExpeditionScreenAnalyzer.Target(
                kind, identity, bounds.centerX(), bounds.centerY()).confirmationKey(width, height));
    }

    private String dispatchVisualKey(DispatchVisualCandidate candidate, int width, int height) {
        ExpeditionVision.Bounds b = candidate.icon().bounds();
        return candidate.fruit().bestTemplate() + ":" + candidate.seedling().bestTemplate()
                + ":" + b.centerX() * 20 / width + ":" + b.centerY() * 20 / height;
    }

    private void selectDispatchVisualCandidate(List<DispatchVisualCandidate> candidates,
            ExpeditionVision.Surface surface, int[] pixels, int width, int height,
            CaptureGeometry geometry, long generation, long epoch, long signature, long now) {
        // Visual identity is established before policy. A known target has priority over OCR escalation.
        for (DispatchVisualCandidate candidate : candidates) {
            ExpeditionScreenAnalyzer.ItemKind kind = switch (candidate.classification()) {
                case FRUIT -> ExpeditionScreenAnalyzer.ItemKind.FRUIT;
                case SEEDLING -> ExpeditionScreenAnalyzer.ItemKind.POT;
                default -> null;
            };
            if (kind == null) continue;
            if (ExpeditionRecognition.requiresTitleOcr(
                    candidate.classification(), candidate.fruit())) continue;
            if (!expeditionTargetMode.accepts(kind)) {
                dispatchInspectedVisual.add(dispatchVisualKey(candidate, width, height));
                continue;
            }
            ExpeditionVision.Bounds bounds = candidate.icon().bounds();
            String identity = kind == ExpeditionScreenAnalyzer.ItemKind.FRUIT
                    ? candidate.fruit().bestTemplate() : candidate.seedling().bestTemplate();
            ExpeditionScreenAnalyzer.Target target = new ExpeditionScreenAnalyzer.Target(kind, identity,
                    bounds.centerX(), bounds.centerY());
            if (expeditionDispatchSession.isQuarantined(target.confirmationKey(width, height))) {
                dispatchInspectedVisual.add(dispatchVisualKey(candidate, width, height));
                continue;
            }
            handleDispatchListTarget(target, width, height, now);
            return;
        }
        for (DispatchVisualCandidate candidate : candidates) {
            String key = dispatchVisualKey(candidate, width, height);
            if (dispatchInspectedVisual.contains(key)) continue;
            // Rank changes cannot restart the five-capture uncertainty budget.
            // Consensus still keeps each candidate/text identity separate.
            Bitmap source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            startOcrTransaction(source, OcrScan.Profile.TARGETED_CHINESE, geometry, epoch,
                    "dispatch-title", true, (transaction, frame) -> runWithActionContext(transaction, frame, () -> {
                        recordOcrDiagnostic("dispatch-title", frame);
                        boolean gift = frame.tokens().stream().anyMatch(token -> {
                            String text = ExpeditionScreenAnalyzer.normalize(token.text());
                            return token.centerY() >= candidate.icon().bounds().bottom()
                                    && (text.endsWith("禮品") || text.endsWith("礼品"));
                        });
                        if (gift) {
                            dispatchInspectedVisual.add(key); expeditionRecognitionConsensus.reset();
                            expeditionDispatchSession.recordProgress(android.os.SystemClock.elapsedRealtime());
                            waitForDispatchFrame(getString(R.string.status_reward_scanning));
                            return;
                        }
                        // Secondary semantics are tied to this icon and this capture. They do not
                        // bypass the uncalibrated visual gate or reinterpret an unknown Seedling.
                        boolean greenAppleGuard = ExpeditionRecognition.requiresTitleOcr(
                                candidate.classification(), candidate.fruit());
                        ExpeditionScreenAnalyzer.TitleEvidence titleEvidence = greenAppleGuard
                                ? ExpeditionScreenAnalyzer.greenAppleTitleEvidence(frame.tokens()) : null;
                        ExpeditionVision.Bounds candidateBounds = candidate.icon().bounds();
                        ExpeditionScreenAnalyzer.Target ocr = greenAppleGuard
                                ? titleEvidence == null ? null : new ExpeditionScreenAnalyzer.Target(
                                        titleEvidence.kind(), titleEvidence.label(),
                                        candidateBounds.centerX(), candidateBounds.centerY())
                                : ExpeditionScreenAnalyzer.findTarget(frame.tokens(),
                                        ExpeditionTargetMode.FRUIT_AND_POT, width, height,
                                        (x, y) -> pixels[y * width + x]);
                        ExpeditionRecognition.Classification meaning = ocr == null ? ExpeditionRecognition.Classification.UNKNOWN
                                : ocr.kind() == ExpeditionScreenAnalyzer.ItemKind.FRUIT
                                        ? ExpeditionRecognition.Classification.FRUIT : ExpeditionRecognition.Classification.SEEDLING;
                        ExpeditionRecognition.Classification agreement = expeditionRecognitionConsensus.observe(
                                generation, geometry.captureSequence(), key + ":" + (ocr == null ? "" : ExpeditionScreenAnalyzer.normalize(ocr.label())), meaning);
                        boolean resolvable = candidate.classification()
                                == ExpeditionRecognition.Classification.AMBIGUOUS
                                || greenAppleGuard;
                        String ocrLabel = ocr == null ? ""
                                : ExpeditionScreenAnalyzer.normalize(ocr.label());
                        Log.i(TAG, "EXPEDITION_TITLE_OCR key=" + key
                                + " visual=" + candidate.classification()
                                + " ocrKind=" + (ocr == null ? "NONE" : ocr.kind())
                                + " label=" + ocrLabel
                                + " meaning=" + meaning
                                + " agreement=" + agreement
                                + " observations=" + expeditionRecognitionConsensus.observations()
                                + " resolvable=" + resolvable
                                + " policyAccepts=" + (ocr != null
                                        && expeditionTargetMode.accepts(ocr.kind())));
                        if (resolvable
                                && agreement == meaning
                                && meaning != ExpeditionRecognition.Classification.UNKNOWN
                                && ocr != null && expeditionTargetMode.accepts(ocr.kind())) {
                            ExpeditionVision.Bounds bounds = candidate.icon().bounds();
                            String identity = "OCR:" + ExpeditionScreenAnalyzer.normalize(ocr.label());
                            handleDispatchListTarget(new ExpeditionScreenAnalyzer.Target(
                                    ocr.kind(), identity, bounds.centerX(), bounds.centerY()),
                                    width, height, android.os.SystemClock.elapsedRealtime());
                            return;
                        }
                        if (agreement == meaning && meaning != ExpeditionRecognition.Classification.UNKNOWN
                                || expeditionRecognitionConsensus.observations() >= 5) {
                            dispatchInspectedVisual.add(key);
                            expeditionRecognitionConsensus.reset();
                            expeditionDispatchSession.recordSkippedCandidate(android.os.SystemClock.elapsedRealtime());
                        }
                        waitForDispatchFrame(getString(R.string.status_reward_confirming));
                    }), error -> {
                        // This weak candidate remains unknown. OCR failure never turns it into a target.
                        dispatchInspectedVisual.add(key);
                        expeditionRecognitionConsensus.reset();
                        expeditionDispatchSession.recordSkippedCandidate(android.os.SystemClock.elapsedRealtime());
                        waitForDispatchFrame(getString(R.string.status_reward_confirming));
                    }, candidate.icon().cardText().roi());
            return;
        }
        scrollDispatchList(surface, signature, false);
    }

    private void recordDispatchRecognition(ExpeditionRecognition.Scores scores,
            ExpeditionVision.Icon candidate, long frameId, long generation,
            ExpeditionRecognition.Classification visual, ExpeditionRecognition.Classification result) {
        ExpeditionVision.Bounds bounds = candidate.bounds();
        Log.i(TAG, "EXPEDITION_RECOGNITION bank=" + scores.bank()
                + " bestTemplate=" + scores.bestTemplate() + " bestScore=" + scores.bestScore()
                + " secondTemplate=" + scores.secondTemplate() + " secondScore=" + scores.secondScore()
                + " margin=" + scores.margin() + " candidateSize=" + bounds.width() + "x" + bounds.height()
                + " candidateOrigin=" + bounds.left() + "," + bounds.top() + " frameId=" + frameId
                + " runGeneration=" + generation + " visualClassification=" + visual + " finalClassification=" + result
                + " candidateSource=PIXEL_COMPONENT metric=" + ExpeditionRecognition.METRIC
                + " calibration=" + ExpeditionRecognition.CALIBRATION);
        if (usageSession != null) usageSession.recordDispatchRecognition(scores.bank(), scores.bestTemplate(), scores.bestScore(),
                scores.secondTemplate(), scores.secondScore(), scores.margin(), bounds.left(), bounds.top(),
                bounds.width(), bounds.height(), frameId, visual.name(), result.name(), ExpeditionRecognition.METRIC);
    }

    private void finishDispatchSweep() {
        ExpeditionDispatchSession.Outcome outcome = expeditionDispatchSession.outcome();
        if (!expeditionDispatchSession.complete()) return;
        int completed = expeditionDispatchSession.completedCount();
        int skipped = expeditionDispatchSession.skippedCount();
        Log.i(TAG, "EXPEDITION_OUTCOME outcome=" + outcome + " dispatched=" + completed + " skipped=" + skipped);
        finishUsageSession(outcome.name());
        finishWithSuccess(outcome == ExpeditionDispatchSession.Outcome.COMPLETE_WITH_SKIPS
                ? getString(R.string.status_reward_complete_with_skips, completed)
                : getString(R.string.status_reward_complete, completed));
    }

    private void handleDispatchListTarget(
            ExpeditionScreenAnalyzer.Target target, int width, int height, long now) {
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                target.confirmationKey(width, height), now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_confirming));
            return;
        }
        expeditionRecognitionConsensus.reset();
        dispatchCurrentItemKind = target.kind();
        if (!expeditionDispatchSession.beginCandidateSelection(target.confirmationKey(width, height), now)) {
            waitForDispatchFrame(getString(R.string.status_reward_scanning));
            return;
        }
        dispatchActionTap(
                new ExpeditionScreenAnalyzer.Point(target.x(), target.y()),
                getString(R.string.status_reward_opening_detail),
                () -> {});
    }

    private void handleDispatchDetail(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ExpeditionScreenAnalyzer.Point action =
                ExpeditionScreenAnalyzer.findDetailActionForVerifiedScreen(
                        screen, tokens, width, height, bitmap::getPixel);
        if (expeditionDispatchSession.transitionPending()) {
            if (expeditionDispatchSession.shouldRetryDetailTap(screen, action != null, now)) {
                ExpeditionDispatchSession.Confirmation retryConfirmation =
                        expeditionDispatchSession.confirmDetailAction(
                                dispatchDetailConfirmationKey("RETRY", action), now);
                if (retryConfirmation == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
                    recordDispatchDetailDiagnostic(
                            "stage-timeout", "attempt-limit", "accepted",
                            expeditionDispatchSession.detailTapAttempts(), true,
                            width, height, action);
                }
                if (!handleDispatchConfirmation(retryConfirmation)) {
                    if (retryConfirmation != ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
                        recordDispatchDetailDiagnostic(
                                "confirmation-waiting", "ocr-match", "accepted",
                                expeditionDispatchSession.detailTapAttempts(), true,
                                width, height, action);
                        waitForDispatchFrame(
                                getString(R.string.status_reward_go_explore_retrying));
                    }
                    return;
                }
                dispatchDetailActionTap(action, width, height, now, true);
                return;
            }
            ExpeditionDispatchSession.Confirmation timeout =
                    expeditionDispatchSession.confirm("", now);
            if (timeout == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
                String reason = screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                        ? action == null ? "ocr-no-match" : "attempt-limit"
                        : "destination-missing";
                recordDispatchDetailDiagnostic(
                        "stage-timeout", reason,
                        action == null ? "none" : "accepted",
                        expeditionDispatchSession.detailTapAttempts(), true,
                        width, height, action);
            }
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_go_explore_waiting));
            }
            return;
        }
        if (screen != ExpeditionScreenAnalyzer.Screen.DETAIL || action == null) {
            ExpeditionDispatchSession.Confirmation timeout = expeditionDispatchSession.confirm("", now);
            if (timeout == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
                recordDispatchDetailDiagnostic(
                        "stage-timeout",
                        screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                                ? "ocr-no-match" : "destination-missing",
                        action == null ? "none" : "unknown",
                        expeditionDispatchSession.detailTapAttempts(), false,
                        width, height, action);
            }
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_go_explore));
            }
            return;
        }
        ExpeditionDispatchSession.Confirmation actionConfirmation =
                expeditionDispatchSession.confirmDetailAction(
                        dispatchDetailConfirmationKey("INITIAL", action), now);
        if (actionConfirmation == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
            recordDispatchDetailDiagnostic(
                    "stage-timeout", "attempt-limit", "accepted",
                    expeditionDispatchSession.detailTapAttempts(), false,
                    width, height, action);
        }
        if (!handleDispatchConfirmation(actionConfirmation)) {
            if (actionConfirmation != ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
                recordDispatchDetailDiagnostic(
                        "confirmation-waiting", "ocr-match", "accepted",
                        expeditionDispatchSession.detailTapAttempts(), false,
                        width, height, action);
                waitForDispatchFrame(getString(R.string.status_reward_confirming));
            }
            return;
        }
        dispatchDetailActionTap(action, width, height, now, false);
    }

    private String dispatchDetailConfirmationKey(
            String phase, ExpeditionScreenAnalyzer.Point action) {
        return "DETAIL_ACTION:" + phase + ":" + action.x() / 24 + ":" + action.y() / 24;
    }

    private void dispatchDetailActionTap(
            ExpeditionScreenAnalyzer.Point action,
            int bitmapWidth,
            int bitmapHeight,
            long now,
            boolean retry) {
        if (!expeditionDispatchSession.beginDetailTapTransition(now)) {
            recordDispatchDetailDiagnostic(
                    "stage-timeout", "attempt-limit", "accepted",
                    expeditionDispatchSession.detailTapAttempts(),
                    expeditionDispatchSession.transitionPending(),
                    bitmapWidth, bitmapHeight, action);
            stopWithError(getString(R.string.status_reward_stage_timeout));
            return;
        }
        recordDispatchDetailDiagnostic(
                "tap-requested", "ocr-match", "accepted",
                expeditionDispatchSession.detailTapAttempts(), true,
                bitmapWidth, bitmapHeight, action);
        dispatchActionTap(
                action,
                getString(retry
                        ? R.string.status_reward_go_explore_retrying
                        : R.string.status_reward_go_explore),
                DISPATCH_AFTER_TAP_DELAY_MILLIS,
                () -> {
                    recordDispatchDetailDiagnostic(
                            "tap-completed", "gesture", "accepted",
                            expeditionDispatchSession.detailTapAttempts(), true,
                            bitmapWidth, bitmapHeight, action);
                    setStatus(getString(R.string.status_reward_go_explore_waiting));
                    setRunStatus(
                            AutomationMode.DISPATCH,
                            OverlayRunStatus.Kind.RECOGNIZING,
                            getString(R.string.status_reward_go_explore_waiting),
                            getString(R.string.overlay_reward_safety_items));
                },
                () -> {
                    recordDispatchDetailDiagnostic(
                            "tap-failed", "gesture", "accepted",
                            expeditionDispatchSession.detailTapAttempts(), true,
                            bitmapWidth, bitmapHeight, action);
                    stopWithError(getString(R.string.status_reward_gesture_failed));
                });
    }

    private void handleDispatchSelection(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        if (screen != ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION) {
            ExpeditionDispatchSession.Confirmation timeout = expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            }
            return;
        }
        if (expeditionDispatchSession.transitionPending()) {
            ExpeditionDispatchSession.Confirmation timeout =
                    expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_tapping_go));
            }
            return;
        }

        if (!dispatchColorSelected) {
            handleDispatchPikminFilter(tokens, bitmap, now);
            return;
        }

        if (!dispatchPikminSelected) {
            if (dispatchSelectionMethod == DispatchSelectionMethod.AUTO) {
                String autoLayout = dispatchSearchOpened ? "search-expanded" : "default";
                ExpeditionScreenAnalyzer.Point automatic =
                        ExpeditionScreenAnalyzer.findPikminAutoButton(
                                tokens, bitmap.getWidth(), bitmap.getHeight());
                ExpeditionScreenAnalyzer.Point visibleGo =
                        ExpeditionScreenAnalyzer.findPikminGoButton(
                                tokens, bitmap.getWidth(), bitmap.getHeight());
                int selectedCount = ExpeditionScreenAnalyzer.selectedPikminCount(tokens);
                if (selectedCount > 0 && visibleGo != null) {
                    ExpeditionDispatchSession.Confirmation selectedConfirmation =
                            expeditionDispatchSession.confirm("AUTO_SELECTED", now);
                    if (!handleDispatchConfirmation(selectedConfirmation)) {
                        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                        return;
                    }
                    if (automatic != null) {
                        recordDispatchSelectionDiagnostic(
                                "selection-confirmed",
                                selectedCount > 0 && visibleGo != null
                                        ? "selected-count-and-go"
                                        : selectedCount > 0 ? "selected-count" : "go-visible",
                                dispatchAutoTapAttempts,
                                selectedCount,
                                visibleGo != null,
                                autoLayout,
                                bitmap,
                                automatic);
                    }
                    dispatchPikminSelected = true;
                    dispatchAutoTapAttempts = 0;
                    dispatchAutoResultMissingFrames = 0;
                    dispatchAutoAnchorMissingFrames = 0;
                    expeditionDispatchSession.recordProgress(now);
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                if (dispatchAutoTapAttempts > 0 && dispatchAutoResultMissingFrames < 2) {
                    dispatchAutoResultMissingFrames++;
                    if (dispatchAutoResultMissingFrames == 2 && automatic != null) {
                        recordDispatchSelectionDiagnostic(
                                "result-missing", "result-not-observed",
                                dispatchAutoTapAttempts, selectedCount, false,
                                autoLayout, bitmap, automatic);
                    }
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                if (dispatchAutoTapAttempts >= MAX_ACTION_ATTEMPTS) {
                    if (automatic != null) {
                        recordDispatchSelectionDiagnostic(
                                "attempt-limit", "attempt-limit",
                                dispatchAutoTapAttempts, selectedCount, false,
                                autoLayout, bitmap, automatic);
                    }
                    stopWithError(getString(R.string.status_reward_selection_missing));
                    return;
                }
                if (automatic == null) {
                    dispatchAutoAnchorMissingFrames++;
                    if (dispatchAutoAnchorMissingFrames >= MAX_ACTION_ATTEMPTS) {
                        stopWithError(getString(R.string.status_reward_selection_missing));
                    } else {
                        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    }
                    return;
                }
                dispatchAutoAnchorMissingFrames = 0;
                ExpeditionDispatchSession.Confirmation autoConfirmation =
                        expeditionDispatchSession.confirm(
                                "AUTO_SELECTION_SCREEN:" + autoLayout,
                                now);
                if (!handleDispatchConfirmation(autoConfirmation)) {
                    recordDispatchSelectionDiagnostic(
                            "confirmation-waiting", "two-frame-selection",
                            dispatchAutoTapAttempts, selectedCount, false,
                            autoLayout, bitmap, automatic);
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                int autoAttempt = ++dispatchAutoTapAttempts;
                dispatchAutoResultMissingFrames = 0;
                expeditionDispatchSession.recordProgress(now);
                recordDispatchSelectionDiagnostic(
                        "tap-requested", "relative-center",
                        autoAttempt, selectedCount, false,
                        autoLayout, bitmap, automatic);
                dispatchActionTap(
                        automatic,
                        getString(R.string.status_reward_selecting_pikmin),
                        DISPATCH_AUTO_VERIFY_DELAY_MILLIS,
                        () -> recordDispatchSelectionDiagnostic(
                                "tap-completed", "gesture",
                                autoAttempt, selectedCount, false,
                                autoLayout, bitmap, automatic),
                        () -> {
                            recordDispatchSelectionDiagnostic(
                                    "tap-failed", "gesture",
                                    autoAttempt, selectedCount, false,
                                    autoLayout, bitmap, automatic);
                            stopWithError(getString(R.string.status_reward_gesture_failed));
                        });
            } else {
                selectDispatchPikminFromGrid(tokens, bitmap, now);
            }
            return;
        }

        ExpeditionScreenAnalyzer.Point go = ExpeditionScreenAnalyzer.findPikminGoButton(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        boolean selectionReady = !dispatchSelectionMethod.requiresFullSelection()
                || (dispatchSelectionTargetCount > 0
                        && ExpeditionScreenAnalyzer.selectedPikminCount(tokens)
                                >= dispatchSelectionTargetCount);
        if (go == null || !selectionReady) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                "GO:" + go.x() / 24 + ":" + go.y() / 24, now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_tapping_go));
            return;
        }
        expeditionDispatchSession.beginTransition(now);
        long goCapture = currentCaptureGeometry().captureSequence();
        dispatchActionTap(
                go,
                getString(R.string.status_reward_tapping_go),
                () -> expeditionDispatchSession.recordGoGestureCompleted(goCapture));
    }

    private void handleDispatchResult(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        if (expeditionDispatchSession.transitionPending()) {
            ExpeditionDispatchSession.Confirmation timeout =
                    expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_closing_result));
            }
            return;
        }
        ExpeditionScreenAnalyzer.Point close = ExpeditionScreenAnalyzer.findResultClose(bitmap);
        if (close == null) {
            ExpeditionDispatchSession.Confirmation timeout = expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_waiting_result));
            }
            return;
        }
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                "CLOSE", now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_waiting_result));
            return;
        }
        expeditionDispatchSession.beginTransition(now);
        Log.i(TAG, "EXPEDITION_RESULT_CLOSE action=TAP x=" + close.x() + " y=" + close.y());
        dispatchActionTap(
                close,
                getString(R.string.status_reward_closing_result),
                () -> {});
    }

    private void handleDispatchReturn(
            List<PetalMatcher.Token> tokens,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        if (screen != ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            ExpeditionDispatchSession.Confirmation timeout = expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_returning));
            }
            return;
        }
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                "RETURN:EXPLORE_LIST", now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_returning));
            return;
        }
        if (!expeditionDispatchSession.recordReturnedToList(now)) {
            stopWithError(getString(R.string.status_reward_return_state_invalid));
            return;
        }
        dispatchCurrentItemKind = null;
        if (expeditionTemplateMatcher != null) expeditionTemplateMatcher.clearObservationCache();
        int completed = expeditionDispatchSession.completedCount();
        dispatchColorSelected = false;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchResultMissingFrames = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchSelectionTargetCount = 0;
        dispatchSelectionBeforeCount = -1;
        dispatchSelectionGesturePending = false;
        dispatchReturnRevealPending = true;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoAnchorMissingFrames = 0;
        waitForDispatchFrame(getString(R.string.status_reward_progress, completed));
    }

    /** 回傳 false 表示尚未可執行；逾時時方法會自行停止流程。 */
    private boolean handleDispatchConfirmation(ExpeditionDispatchSession.Confirmation confirmation) {
        if (confirmation == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
            stopWithError(getString(R.string.status_reward_stage_timeout));
            return false;
        }
        return confirmation == ExpeditionDispatchSession.Confirmation.READY;
    }

    private void waitForDispatchFrame(String message) {
        if (!running || automationMode != AutomationMode.DISPATCH) {
            return;
        }
        setStatus(message);
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.RECOGNIZING,
                message,
                getString(R.string.overlay_reward_safety_items));
        schedule(DISPATCH_SCAN_DELAY_MILLIS);
    }

    private void recordDispatchDetailDiagnostic(
            String outcome,
            String reason,
            String match,
            int attempt,
            boolean transitionPending,
            int bitmapWidth,
            int bitmapHeight,
            ExpeditionScreenAnalyzer.Point action) {
        if (usageSession != null) {
            usageSession.recordDispatchDetail(
                    outcome, reason, match, attempt, transitionPending,
                    bitmapWidth, bitmapHeight,
                    action == null ? -1 : action.x(),
                    action == null ? -1 : action.y());
        }
    }

    private void recordFeedNectarSelectionDiagnostic(
            String outcome,
            String reason,
            int scan,
            int candidateFrames,
            int missingFrames,
            boolean nectarCountFound,
            boolean petalCountFound) {
        if (usageSession != null) {
            usageSession.recordFeedNectarSelection(
                    outcome,
                    reason,
                    scan,
                    candidateFrames,
                    missingFrames,
                    nectarCountFound,
                    petalCountFound);
        }
    }

    private void recordDispatchSelectionDiagnostic(
            String outcome,
            String reason,
            int attempt,
            int selectedCount,
            boolean goVisible,
            String layout,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Point point) {
        if (usageSession != null) {
            usageSession.recordDispatchSelection(
                    outcome, reason, attempt, selectedCount, goVisible, layout,
                    bitmap.getWidth(), bitmap.getHeight(), point.x(), point.y());
        }
    }

    private void dispatchActionTap(
            ExpeditionScreenAnalyzer.Point point,
            String message,
            Runnable advance) {
        dispatchActionTap(point, message, DISPATCH_AFTER_TAP_DELAY_MILLIS, advance);
    }

    private void dispatchActionTap(
            ExpeditionScreenAnalyzer.Point point,
            String message,
            long nextScanDelayMillis,
            Runnable advance) {
        dispatchActionTap(
                point,
                message,
                nextScanDelayMillis,
                advance,
                () -> stopWithError(getString(R.string.status_reward_gesture_failed)));
    }

    private void dispatchActionTap(
            ExpeditionScreenAnalyzer.Point point,
            String message,
            long nextScanDelayMillis,
            Runnable advance,
            Runnable failure) {
        setStatus(message);
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.SEARCHING,
                message,
                getString(R.string.status_reward_progress,
                        expeditionDispatchSession.completedCount()));
        busy = true;
        dispatchTap(
                point.x(),
                point.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> {
                    advance.run();
                    busy = false;
                    schedule(nextScanDelayMillis);
                },
                () -> {
                    busy = false;
                    failure.run();
                });
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(
            ExpeditionScreenAnalyzer.Point point,
            Bitmap bitmap) {
        return screenPointFromBitmap(point, currentCaptureGeometry());
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(
            ExpeditionScreenAnalyzer.Point point,
            CaptureGeometry captureGeometry) {
        long startedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            ScreenCoordinateTransform.Point mapped = actionGateway().toScreen(
                    point.x(),
                    point.y(),
                    captureGeometry);
            return new ExpeditionScreenAnalyzer.Point(mapped.x(), mapped.y());
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.COORDINATE_CONVERSION,
                    android.os.SystemClock.uptimeMillis() - startedAtUptimeMillis);
        }
    }

    private void handleDispatchPikminFilter(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            long now) {
        // MIXED still clears a filter left by a previous target. The game owns the
        // filtering semantics; PikminX only verifies the focused editor and refresh.
        String label = dispatchPikminType == DispatchPikminType.MIXED
                ? "" : dispatchPikminType.label();
        boolean focusedEditor = hasFocusedGameEditableText();
        boolean focusedMatches = focusedGameEditableTextMatches(label);
        Log.i(TAG, "EXPEDITION_FILTER type=" + dispatchPikminType
                + " labelEmpty=" + label.isEmpty()
                + " searchOpened=" + dispatchSearchOpened
                + " focusedEditor=" + focusedEditor
                + " focusedMatches=" + focusedMatches
                + " inputAttempts=" + dispatchSearchInputAttempts);
        if (!dispatchSearchOpened) {
            if (focusedEditor) {
                dispatchSearchOpened = true;
                dispatchSearchOpenAttempts = 0;
                expeditionDispatchSession.recordProgress(now);
            } else {
                if (dispatchSearchOpenAttempts >= MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_reward_search_missing));
                    return;
                }
                ExpeditionScreenAnalyzer.Point search =
                        ExpeditionScreenAnalyzer.findPikminSearchButton(
                                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
                if (search == null) {
                    waitForDispatchFrame(getString(R.string.status_reward_search_missing));
                    return;
                }
                ExpeditionDispatchSession.Confirmation confirmation =
                        expeditionDispatchSession.confirm(
                                "PIKMIN_SEARCH_CONTROL",
                                now);
                if (!handleDispatchConfirmation(confirmation)) {
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                dispatchSearchOpenAttempts++;
                dispatchActionTap(
                        search,
                        getString(R.string.status_reward_selecting_pikmin),
                        () -> {});
                return;
            }
        }

        if (!dispatchSearchTextConfirmed) {
            if (!focusedMatches) {
                dispatchSearchInputAttempts++;
                boolean accepted = setFocusedGameEditableText(label);
                if (dispatchSearchInputAttempts >= MAX_ACTION_ATTEMPTS && !accepted) {
                    stopWithError(getString(R.string.status_reward_search_input_failed));
                    return;
                }
                if (dispatchSearchInputAttempts > MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_reward_search_input_failed));
                    return;
                }
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
            ExpeditionDispatchSession.Confirmation confirmation =
                    expeditionDispatchSession.confirm(
                            "PIKMIN_FILTER:" + (label.isEmpty() ? "MIXED_CLEAR" : label), now);
            if (!handleDispatchConfirmation(confirmation)) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
            dispatchSearchTextConfirmed = true;
            dispatchSearchInputAttempts = 0;
            expeditionDispatchSession.recordProgress(now);
        }

        // The observed empty-search Back closes both keyboard and search field.
        // This is valid only after this run verified the actual editor was empty.
        boolean clearedMixedSearch = label.isEmpty() && dispatchSearchTextConfirmed
                && !isInputMethodWindowVisible();
        boolean searchPageConfirmed = dispatchSearchOpened
                && dispatchSearchTextConfirmed
                && (gameEditableTextMatches(label) || clearedMixedSearch);
        SearchKeyboardGuard.Action keyboardAction = observeSearchKeyboard(dispatchSearchKeyboardGuard, label, searchPageConfirmed);
        if (keyboardAction == SearchKeyboardGuard.Action.SEND_BACK) {
            if (!sendSearchKeyboardBack(dispatchSearchKeyboardGuard, label, searchPageConfirmed)) {
                stopWithError(getString(R.string.status_reward_keyboard_failed));
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        if (keyboardAction == SearchKeyboardGuard.Action.WAIT) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        if (keyboardAction != SearchKeyboardGuard.Action.COMPLETE) {
            stopWithError(getString(R.string.status_reward_keyboard_failed));
            return;
        }
        dispatchSearchKeyboardGuard.reset();
        if (!clearedMixedSearch && !gameEditableTextMatches(label)) {
            dispatchSearchOpened = false;
            dispatchSearchTextConfirmed = false;
            dispatchSearchKeyboardGuard.reset();
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        List<ExpeditionSelectionGeometry.Item> refreshedItems = ExpeditionSelectionGeometry.detect(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        if (refreshedItems.isEmpty()) {
            if (++dispatchSearchResultMissingFrames >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_reward_selection_missing));
            } else {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            }
            return;
        }
        dispatchSearchResultMissingFrames = 0;
        dispatchColorSelected = true;
        expeditionDispatchSession.recordProgress(now);
        dispatchSearchKeyboardGuard.reset();
        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
    }

    /** Uses current card pixels and labels, then dispatches bounded 5+5+2 gestures. */
    private void selectDispatchPikminFromGrid(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            long now) {
        int selected = ExpeditionScreenAnalyzer.selectedPikminCount(tokens);
        if (dispatchSelectionGesturePending) {
            // Reacquire only after the complete batch. Never rebuild/toggle rows after
            // a partial result, since the selected state of individual items is unknown.
            int limit = ExpeditionScreenAnalyzer.pikminSelectionLimit(tokens);
            int expected = limit > 0 ? Math.min(dispatchSelectionTargetCount, limit)
                    : dispatchSelectionTargetCount;
            if (expected > 0 && selected >= expected && selected > dispatchSelectionBeforeCount) {
                if (!handleDispatchConfirmation(expeditionDispatchSession.confirm("DRAG12_SELECTED", now))) {
                    if (running) waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                dispatchSelectionTargetCount = expected;
                dispatchPikminSelected = true;
                expeditionDispatchSession.recordProgress(now);
            } else if (!handleDispatchConfirmation(expeditionDispatchSession.confirm("", now))) {
                if (!running) return;
            }
            if (running) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            }
            return;
        }
        List<ExpeditionSelectionGeometry.Item> detectedItems = ExpeditionSelectionGeometry.detect(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        int capacity = ExpeditionScreenAnalyzer.pikminSelectionLimit(tokens);
        List<List<ExpeditionSelectionGeometry.Item>> groups =
                ExpeditionSelectionGeometry.selectionGroups(detectedItems, capacity);
        Log.i(TAG, "EXPEDITION_DRAG12 items=" + detectedItems.size()
                + " layout=" + detectedItems.stream()
                        .map(item -> item.row() + ":" + item.column()
                                + "@" + item.centerX() + "," + item.centerY())
                        .collect(java.util.stream.Collectors.joining(","))
                + " groups=" + groups.stream().map(group -> Integer.toString(group.size()))
                        .collect(java.util.stream.Collectors.joining(","))
                + " selected=" + selected + " capacity=" + capacity);
        if (groups.isEmpty()) {
            stopWithError(getString(R.string.status_reward_selection_missing));
            return;
        }
        StringBuilder key = new StringBuilder("DRAG12");
        List<GestureDescription> gesturePlans = new ArrayList<>();
        int targetCount = 0;
        for (List<ExpeditionSelectionGeometry.Item> group : groups) {
            GestureDescription.Builder gesture = new GestureDescription.Builder();
            long tapStart = 200L;
            for (int index = 0; index < group.size(); index++) {
                ExpeditionSelectionGeometry.Item item = group.get(index);
                ExpeditionScreenAnalyzer.Point point = screenPointFromBitmap(
                        new ExpeditionScreenAnalyzer.Point(item.centerX(), item.centerY()), currentCaptureGeometry());
                Path path = new Path(); path.moveTo(point.x(), point.y());
                gesture.addStroke(new GestureDescription.StrokeDescription(path, tapStart, 80L));
                tapStart += 120L;
                key.append(':').append(item.row()).append(',').append(item.column());
            }
            gesturePlans.add(gesture.build());
            targetCount += group.size();
        }
        ExpeditionDispatchSession.Confirmation confirmation =
                expeditionDispatchSession.confirm(key.toString(), now, 2);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        dispatchSelectionBeforeCount = selected;
        dispatchSelectionTargetCount = targetCount;
        dispatchSelectionGesturePending = true;
        setStatus(getString(R.string.status_reward_selecting_pikmin));
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.SEARCHING,
                getString(R.string.status_reward_selecting_pikmin),
                getString(R.string.status_reward_progress, expeditionDispatchSession.completedCount()));
        busy = true;
        dispatchGesturePlans(gesturePlans, 0, currentActionContext(),
                () -> {
                    busy = false;
                    schedule(DISPATCH_PIKMIN_TAP_DELAY_MILLIS);
                },
                () -> {
                    busy = false;
                    stopWithError(getString(R.string.status_reward_gesture_failed));
                });
    }

    /** Submits bounded item taps without any perception between the 5+5+2 batches. */
    private void dispatchGesturePlans(List<GestureDescription> plans, int index,
            ActionAdmission.FrameContext actionContext,
            Runnable onCompleted, Runnable onFailed) {
        if (index >= plans.size()) { onCompleted.run(); return; }
        GestureDispatchResult result = dispatchGesturePlan(
                plans.get(index), actionContext,
                () -> dispatchGesturePlans(
                        plans, index + 1, actionContext, onCompleted, onFailed),
                onFailed);
        if (result == GestureDispatchResult.STALE && running) onFailed.run();
    }
    
    /** Uses the current detected tab and window geometry to expand the panel. */
    private void revealDispatchExplorePanel(ExpeditionVision.Surface surface) {
        ExpeditionVision.Bounds tab = surface.selectedTab(), handle = surface.handle();
        if (handle == null) {
            stopWithError(getString(R.string.status_reward_unknown_page)); return;
        }
        ExpeditionScreenAnalyzer.Point start = screenPointFromBitmap(
                new ExpeditionScreenAnalyzer.Point(handle.centerX(), handle.centerY()), currentCaptureGeometry());
        ExpeditionScreenAnalyzer.Point end = screenPointFromBitmap(new ExpeditionScreenAnalyzer.Point(
                handle.centerX(), tab.height() * 2), currentCaptureGeometry());
        Path path = new Path(); path.moveTo(start.x(), start.y()); path.lineTo(end.x(), end.y());
        busy = true;
        dispatchPath(path, 500L, () -> {
            dispatchInspectedVisual.clear(); expeditionRecognitionConsensus.reset();
            if (expeditionTemplateMatcher != null) expeditionTemplateMatcher.clearObservationCache();
            busy = false; schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
        }, () -> {
            busy = false; stopWithError(getString(R.string.status_reward_gesture_failed));
        });
    }

    private void scrollDispatchList(ExpeditionVision.Surface surface, long signature, boolean towardTop) {
        scrollDispatchList(surface, signature, towardTop, false);
    }

    private void scrollDispatchList(ExpeditionVision.Surface surface, long signature,
            boolean towardTop, boolean returnReveal) {
        ExpeditionVision.Bounds content = surface.content();
        int upper = content.top() + content.height() / 4;
        int lower = content.bottom() - content.height() / 4;
        if (returnReveal) {
            // User-observed return behavior: one bounded finger-up drag from
            // approximately 60% to 40% of the current full-screen capture.
            int screenHeight = currentCaptureGeometry().bitmapHeight();
            lower = screenHeight * 3 / 5;
            upper = screenHeight * 2 / 5;
        } else if (towardTop && surface.scrollbar() == null) {
            // A long pull at the top can collapse the Expedition sheet. Without a
            // scrollbar, use the old short probe so repeated viewport evidence can
            // establish the boundary without changing the sheet state.
            upper = content.centerY();
            lower = Math.min(content.bottom() - surface.selectedTab().height(),
                    upper + surface.selectedTab().height() * 2);
        }
        ExpeditionScreenAnalyzer.Point start = screenPointFromBitmap(new ExpeditionScreenAnalyzer.Point(
                content.centerX(), towardTop ? upper : lower), currentCaptureGeometry());
        ExpeditionScreenAnalyzer.Point end = screenPointFromBitmap(new ExpeditionScreenAnalyzer.Point(
                content.centerX(), towardTop ? lower : upper), currentCaptureGeometry());
        Path path = new Path(); path.moveTo(start.x(), start.y()); path.lineTo(end.x(), end.y());
        busy = true;
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.SEARCHING,
                getString(R.string.status_reward_scrolling), getString(R.string.status_reward_scanning));
        dispatchPath(path, 720L, () -> {
            boolean atBottom = surface.scrollbar() != null && surface.scrollbar().atBottom();
            if (returnReveal) {
                dispatchReturnRevealPending = false;
                expeditionDispatchSession.recordReturnRevealScroll(android.os.SystemClock.elapsedRealtime());
                Log.i(TAG, "EXPEDITION_RETURN_REVEAL direction=UP range=60to40 signature=" + signature
                        + " scrollbar=" + surface.scrollbar());
            } else {
                expeditionDispatchSession.recordListScroll(signature, atBottom);
            }
            if (expeditionTemplateMatcher != null) expeditionTemplateMatcher.clearObservationCache();
            if (!returnReveal) {
                Log.i(TAG, "EXPEDITION_SCROLL direction=" + (towardTop ? "TOP" : "DOWN")
                        + " signature=" + signature + " scrollbar=" + surface.scrollbar());
            }
            dispatchInspectedVisual.clear(); expeditionRecognitionConsensus.reset();
            busy = false; schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
        }, () -> {
            busy = false; stopWithError(getString(R.string.status_reward_gesture_failed));
        });
    }

    /** 啟動時先確認地圖種花入口或種花面板，未確認前不發送滑動。 */
    private void handleInitialPlantingEntry(
            PlantingControlEvidence controls) {
        PlantingScreenAnalyzer.Detection detection = controls.detection();
        PlantingFlowPolicy.EntryAction action =
                PlantingFlowPolicy.entryAction(detection.screen());
        if (action == PlantingFlowPolicy.EntryAction.CONFIRM_PLANTING_MENU) {
            if (!hasStablePlantingMenu()) {
                setPlantingNoticeText(
                        getString(R.string.status_planting_checking_entry), false);
                schedule(500);
                return;
            }
            plantingEntryStability.reset();
            plantingMenuStability.reset();
            plantingTransitionFrames = 0;
            actionAttempts = 0;
            initialPlantingMenuConfirmed = true;
            setPlantingNoticeText(
                    getString(R.string.status_planting_menu_ready), false);
            beginInitialPlantingFlowerSearch(controls);
            return;
        }
        if (action == PlantingFlowPolicy.EntryAction.STOP_WRONG_SCREEN) {
            plantingEntryStability.miss();
            plantingMenuStability.miss();
            plantingTransitionFrames = 0;
            stopWithError(getString(R.string.status_planting_wrong_page));
            return;
        }
        PlantingScreenAnalyzer.Point entry = plantingEntryControl(detection);
        if (entry == null) {
            plantingEntryStability.miss();
            plantingMenuStability.miss();
            boolean confirmedMap = detection.screen()
                    == PlantingScreenAnalyzer.Screen.MAP_VISIBLE_NO_ENTRY
                    || detection.screen() == PlantingScreenAnalyzer.Screen.AMBIGUOUS;
            if (confirmedMap) {
                plantingTransitionFrames = 0;
            }
            setPlantingNoticeText(getString(confirmedMap || ++plantingTransitionFrames < 6
                    ? R.string.status_planting_checking_entry
                    : R.string.status_planting_switch_to_menu), false);
            scheduleNext();
            return;
        }

        plantingMenuStability.miss();
        ObservationStability.Result stability = observePlantingEntry(entry);
        if (stability != ObservationStability.Result.STABLE) {
            setPlantingNoticeText(
                    getString(R.string.status_planting_checking_entry), false);
            schedule(700);
            return;
        }
        plantingEntryStability.reset();
        plantingTransitionFrames = 0;
        actionAttempts = 0;
        automationStep = AutomationStep.WAITING_INITIAL_PLANTING_MENU;
        initialPlantingMenuConfirmed = false;
        setPlantingNoticeText(
                getString(R.string.status_planting_opening_menu), false);
        dispatchPlantingEntryTap(
                entry,
                "initial source=" + detection.entryEvidence().source(),
                () -> schedule(700),
                () -> stopWithError(getString(R.string.status_planting_menu_unconfirmed)));
    }

    /** 點擊地圖入口後要求真正的種花面板證據，不以手勢完成當作轉場成功。 */
    private void verifyPlantingMenuOpened(
            PlantingControlEvidence controls, boolean afterStart) {
        PlantingScreenAnalyzer.Detection detection = controls.detection();
        PlantingFlowPolicy.EntryAction entryAction =
                PlantingFlowPolicy.entryAction(detection.screen());
        if (entryAction == PlantingFlowPolicy.EntryAction.CONFIRM_PLANTING_MENU) {
            if (!hasStablePlantingMenu()) {
                setStatus(getString(afterStart
                        ? R.string.status_planting_reentering
                        : R.string.status_planting_opening_menu));
                schedule(500);
                return;
            }
            plantingEntryStability.reset();
            plantingMenuStability.reset();
            plantingTransitionFrames = 0;
            actionAttempts = 0;
            if (afterStart) {
                resumePlantingAfterMenuReturn();
            } else {
                initialPlantingMenuConfirmed = true;
                setPlantingNoticeText(
                        getString(R.string.status_planting_menu_ready), false);
                beginInitialPlantingFlowerSearch(controls);
            }
            return;
        }
        PlantingScreenAnalyzer.Point returnControl = plantingEntryControl(detection);
        if (returnControl != null) {
            plantingMenuStability.miss();
            if (observePlantingEntry(returnControl)
                    != ObservationStability.Result.STABLE) {
                setStatus(getString(afterStart
                        ? R.string.status_planting_reentering
                        : R.string.status_planting_opening_menu));
                schedule(500);
                return;
            }
            plantingEntryStability.reset();
            if (++actionAttempts > MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(afterStart
                        ? R.string.status_planting_reentry_failed
                        : R.string.status_planting_menu_unconfirmed));
                return;
            }
            setStatus(getString(afterStart
                    ? R.string.status_planting_reentering
                    : R.string.status_planting_opening_menu));
            dispatchPlantingEntryTap(
                    returnControl,
                    (afterStart ? "after-start-retry" : "initial-retry")
                            + " source=" + detection.entryEvidence().source(),
                    () -> schedule(700),
                    () -> stopWithError(getString(afterStart
                            ? R.string.status_planting_reentry_failed
                            : R.string.status_planting_menu_unconfirmed)));
            return;
        }
        plantingEntryStability.miss();
        plantingMenuStability.miss();
        if (++plantingTransitionFrames >= 6) {
            stopWithError(getString(afterStart
                    ? R.string.status_planting_reentry_failed
                    : R.string.status_planting_menu_unconfirmed));
            return;
        }
        setStatus(getString(afterStart
                ? R.string.status_planting_reentering
                : R.string.status_planting_opening_menu));
        schedule(700);
    }

    private boolean hasStablePlantingMenu() {
        CaptureGeometry geometry = currentCaptureGeometry();
        ObservationStability.Result stability = plantingMenuStability.observe(
                "planting-menu",
                geometry.bitmapWidth() / 2,
                geometry.bitmapHeight() / 2,
                geometry.bitmapWidth(),
                geometry.bitmapHeight());
        return stability == ObservationStability.Result.STABLE;
    }

    private ObservationStability.Result observePlantingEntry(
            PlantingScreenAnalyzer.Point entry) {
        CaptureGeometry geometry = currentCaptureGeometry();
        ObservationStability.Result stability = plantingEntryStability.observe(
                "planting-map-entry",
                entry.x(),
                entry.y(),
                geometry.bitmapWidth(),
                geometry.bitmapHeight());
        return stability;
    }

    /** 所有地圖狀態都只使用右下哨子相對定位出的種花入口。 */
    private PlantingScreenAnalyzer.Point plantingEntryControl(
            PlantingScreenAnalyzer.Detection detection) {
        return switch (PlantingFlowPolicy.entryAction(detection.screen())) {
            case OPEN_MAP_ENTRY -> detection.mapEntry();
            case CONFIRM_PLANTING_MENU, STOP_WRONG_SCREEN -> null;
        };
    }

    private boolean isPlantingEntryStep() {
        return automationMode == AutomationMode.PLANTING
                && (automationStep == AutomationStep.CHECKING_PLANTING_ENTRY
                        || automationStep == AutomationStep.WAITING_INITIAL_PLANTING_MENU
                        || automationStep == AutomationStep.WAITING_MENU_AFTER_START);
    }

    private void logPlantingEntryFrame(
            OcrScan.Frame frame, PlantingScreenAnalyzer.Detection detection) {
        PlantingScreenAnalyzer.Point entry = detection.mapEntry();
        PlantingScreenAnalyzer.Point anchor = detection.entryEvidence().whistleAnchor();
        Log.i(TAG, "PLANTING_ENTRY stage=" + automationStep.name()
                + " screen=" + detection.screen().name()
                + " entrySource=" + detection.entryEvidence().source().name()
                + " entry=" + (entry == null
                        ? "none"
                        : "[" + entry.x() + "," + entry.y() + "]")
                + " anchor=" + (anchor == null
                        ? "none"
                        : "[" + anchor.x() + "," + anchor.y() + "]")
                + " anchorScore=" + detection.entryEvidence().whistleScore()
                + " tokenCount=" + frame.tokens().size()
                + " profile=" + frame.profile().name()
                + " failureReason=none");
    }

    private void logPlantingEntryFailure(OcrScan.Profile profile, Exception error) {
        String message = error == null || error.getMessage() == null
                ? "unknown"
                : error.getMessage().replace('\n', ' ').replace('\r', ' ');
        Log.i(TAG, "PLANTING_ENTRY stage=" + automationStep.name()
                + " screen=UNAVAILABLE entrySource=NONE entry=none"
                + " anchor=none anchorScore=-1 tokenCount=0"
                + " profile=" + profile.name()
                + " failureReason=" + (error == null
                        ? "unknown"
                        : error.getClass().getSimpleName() + ":" + message));
    }

    private void beginInitialPlantingFlowerSearch(PlantingControlEvidence controls) {
        if (!initialPlantingMenuConfirmed) {
            returnToInitialPlantingEntry();
            return;
        }
        boolean waitForStart = PlantingFlowPolicy.shouldWaitForStartAfterSelection(
                controls.startVisible(), controls.stopVisible());
        beginPlantingFlowerSearch(settings.allowedFlowers().get(0), 0, waitForStart);
        if (!waitForStart) {
            logPlantingSwitch("already-active-skip-waiting-start", true);
        }
    }

    private void returnToInitialPlantingEntry() {
        initialPlantingMenuConfirmed = false;
        resetPlantingNavigation();
        automationStep = AutomationStep.CHECKING_PLANTING_ENTRY;
        setPlantingNoticeText(
                getString(R.string.status_planting_checking_entry), false);
        scheduleNext();
    }

    /** 判斷目前是否正在執行自動種花的搜尋框子流程。 */
    private boolean isPlantingSearchStep() {
        return automationStep == AutomationStep.REVEALING_SEARCH_PANEL
                || automationStep == AutomationStep.OPENING_SEARCH
                || automationStep == AutomationStep.CLEARING_SEARCH
                || automationStep == AutomationStep.ENTERING_SEARCH
                || automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD
                || automationStep == AutomationStep.SELECTING_SEARCH_RESULT
                || automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
    }

    /** 設定搜尋目標並從開啟搜尋框開始，完全取代舊的清單滑動查找。 */
    private void beginPlantingFlowerSearch(
            String flower, int minimumCount, boolean startAfter) {
        String query = PetalCatalog.searchQuery(flower);
        if (query.isBlank()) {
            stopWithError(getString(R.string.status_flower_search_invalid_name));
            return;
        }
        if (!isPlantingSearchStep()) {
            plantingSkippedFlowers.clear();
        }
        targetFlower = PetalCatalog.canonicalName(flower);
        targetCount = -1;
        plantingSkippedLast = false;
        resetPlantingSearch();
        plantingSearchMinimumCount = Math.max(0, minimumCount);
        startAfterSelection = startAfter;
        selectionFromSearch = false;
        actionAttempts = 0;
        automationStep = AutomationStep.REVEALING_SEARCH_PANEL;
        setStatus(getString(R.string.status_searching_flower, targetFlower));
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.SEARCHING,
                getString(R.string.overlay_planting_searching, targetFlower),
                query);
        logPlantingSwitch("search-begin", false);
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /** 依搜尋子狀態開啟欄位、輸入、關閉鍵盤及辨識完整目標花盆。 */
    private void handlePlantingFlowerSearch(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            OcrScan.Frame frame,
            H10aPlantingAnalysis.Result analysis) {
        H10aPlantingAnalysis.SearchControls searchControls =
                analysis == null ? null : analysis.searchControls();
        boolean searchOpen = searchControls != null && searchControls.searchOpen();
        logPlantingSwitch("search-frame", searchOpen);
        if (automationStep == AutomationStep.REVEALING_SEARCH_PANEL) {
            revealPlantingSearchPanel();
            return;
        }
        if (automationStep == AutomationStep.OPENING_SEARCH) {
            openPlantingFlowerSearch(searchControls);
            return;
        }
        if (automationStep == AutomationStep.CLEARING_SEARCH) {
            clearPlantingSearchForNextFlower(searchControls);
            return;
        }
        if (automationStep == AutomationStep.ENTERING_SEARCH) {
            enterPlantingFlowerSearch();
            return;
        }
        if (automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
            closePlantingSearchKeyboard(searchControls);
            return;
        }
        if (automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION) {
            boolean closedMenu = analysis != null
                    && analysis.plantingScreen() != null
                    && analysis.plantingScreen().screen()
                            == PlantingScreenAnalyzer.Screen.PLANTING_MENU
                    && searchControls != null
                    && searchControls.plantingSearchButton() != null;
            closePlantingSearchAfterSelection(searchControls, closedMenu);
            return;
        }

        String query = PetalCatalog.searchQuery(targetFlower);
        if (!gameEditableTextMatches(query)) {
            automationStep = AutomationStep.ENTERING_SEARCH;
            setStatus(getString(R.string.status_flower_search_confirming_text));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }

        PetalMatcher.Selection pot = analysis == null ? null : analysis.searchedFlower();
        if (pot == null) {
            handlePlantingSearchMiss();
            return;
        }
        confirmPlantingSearchResult(pot, bitmap.getWidth(), bitmap.getHeight());
    }

    /** 以展開搜尋框的實際中心推算底緣，花盆名稱只在其下方判斷。 */
    private int plantingSearchResultsTop(Bitmap bitmap) {
        return plantingSearchResultsTop(
                bitmap,
                CardHighlight.analyzePetalSearchControls(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel));
    }

    private int plantingSearchResultsTop(
            Bitmap bitmap, CardHighlight.PetalSearchAnalysis searchAnalysis) {
        CardHighlight.Point search = searchAnalysis.closeButton();
        return search == null
                ? -1
                : Math.min(
                        bitmap.getHeight() - 1,
                        search.y() + Math.round(bitmap.getHeight() * 0.03f));
    }

    private int plantingSearchResultsTop(
            H10aPlantingAnalysis.SearchControls searchControls) {
        return searchControls == null ? -1 : searchControls.searchResultsTop();
    }

    /** 依目前遊戲視窗尺寸上拉 20%，再沿用既有花盆名稱搜尋流程。 */
    private void revealPlantingSearchPanel() {
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            stopWithError(getString(R.string.status_flower_panel_reveal_failed));
            return;
        }
        PetalMatcher.PanelPull pull = PetalMatcher.plantingPanelPull(
                bounds.width(), bounds.height());
        Path path = new Path();
        path.moveTo(bounds.left + pull.x(), bounds.top + pull.startY());
        path.lineTo(bounds.left + pull.x(), bounds.top + pull.endY());
        busy = true;
        setStatus(getString(R.string.status_flower_panel_revealing));
        dispatchPath(path, 500L, () -> {
            busy = false;
            automationStep = AutomationStep.OPENING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
        }, () -> {
            busy = false;
            stopWithError(getString(R.string.status_flower_panel_reveal_failed));
        });
    }

    /** 以實際像素位置開啟搜尋欄；活動橫幅會改變圖示高度，不能使用固定 Y。 */
    private void openPlantingFlowerSearch(
            H10aPlantingAnalysis.SearchControls searchControls) {
        if (searchControls != null && searchControls.searchOpen()) {
            actionAttempts = 0;
            automationStep = AutomationStep.ENTERING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point search = searchControls == null
                ? null : searchControls.plantingSearchButton();
        if (search == null || ++actionAttempts > MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_flower_search_open_failed));
            return;
        }
        setStatus(getString(R.string.status_flower_search_opening));
        dispatchTap(
                search.x(),
                search.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> schedule(700),
                () -> {
                    automationStep = AutomationStep.OPENING_SEARCH;
                    scheduleNext();
                });
    }

    /** 將第一順位或下一順位花名寫入遊戲的可編輯搜尋欄。 */
    private void enterPlantingFlowerSearch() {
        String query = PetalCatalog.searchQuery(targetFlower);
        if (query.isBlank()) {
            stopWithError(getString(R.string.status_flower_search_invalid_name));
            return;
        }
        if (!setGameEditableText(query)) {
            plantingSearchInputAttempts++;
            if (plantingSearchInputAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_flower_search_input_failed));
            } else {
                automationStep = AutomationStep.ENTERING_SEARCH;
                setStatus(getString(R.string.status_flower_search_retrying_input));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            }
            return;
        }
        plantingSearchInputAttempts = 0;
        plantingSearchKeyboardGuard.reset();
        automationStep = AutomationStep.CLOSING_SEARCH_KEYBOARD;
        setStatus(getString(R.string.status_flower_search_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /** 以輸入法視窗狀態關閉鍵盤，避免依賴 Sony、三星或 Gboard 的按鍵位置。 */
    private void closePlantingSearchKeyboard(Bitmap bitmap) {
        closePlantingSearchKeyboard(
                bitmap,
                CardHighlight.analyzePetalSearchControls(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel));
    }

    private void closePlantingSearchKeyboard(
            Bitmap bitmap, CardHighlight.PetalSearchAnalysis searchAnalysis) {
        String query = PetalCatalog.searchQuery(targetFlower);
        boolean searchPageConfirmed = searchAnalysis.searchOpen()
                && gameEditableTextMatches(query);
        SearchKeyboardGuard.Action action = observeSearchKeyboard(plantingSearchKeyboardGuard, query, searchPageConfirmed);
        if (action == SearchKeyboardGuard.Action.COMPLETE) {
            plantingSearchKeyboardGuard.reset();
            scrollPlantingSearchResults(0);
            return;
        }
        if (action == SearchKeyboardGuard.Action.SEND_BACK) {
            if (!sendSearchKeyboardBack(plantingSearchKeyboardGuard, query, searchPageConfirmed)) {
                stopWithError(getString(R.string.status_flower_search_keyboard_failed));
                return;
            }
            setStatus(getString(R.string.status_flower_search_closing_keyboard));
            schedule(600);
            return;
        }
        if (action == SearchKeyboardGuard.Action.WAIT) {
            setStatus(getString(R.string.status_flower_search_waiting_keyboard));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        stopWithError(getString(R.string.status_flower_search_keyboard_failed));
    }

    private void closePlantingSearchKeyboard(
            H10aPlantingAnalysis.SearchControls searchControls) {
        String query = PetalCatalog.searchQuery(targetFlower);
        boolean searchPageConfirmed = searchControls != null
                && searchControls.searchOpen()
                && gameEditableTextMatches(query);
        SearchKeyboardGuard.Action action = observeSearchKeyboard(
                plantingSearchKeyboardGuard, query, searchPageConfirmed);
        if (action == SearchKeyboardGuard.Action.COMPLETE) {
            plantingSearchKeyboardGuard.reset();
            scrollPlantingSearchResults(0);
            return;
        }
        if (action == SearchKeyboardGuard.Action.SEND_BACK) {
            if (!sendSearchKeyboardBack(
                    plantingSearchKeyboardGuard, query, searchPageConfirmed)) {
                stopWithError(getString(R.string.status_flower_search_keyboard_failed));
                return;
            }
            setStatus(getString(R.string.status_flower_search_closing_keyboard));
            schedule(600);
            return;
        }
        if (action == SearchKeyboardGuard.Action.WAIT) {
            setStatus(getString(R.string.status_flower_search_waiting_keyboard));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        stopWithError(getString(R.string.status_flower_search_keyboard_failed));
    }

    /** 搜尋文字確認後，基礎花盆直接判斷，其餘上滑三次再進入花盆 OCR。 */
    private void scrollPlantingSearchResults(int completedScrolls) {
        scrollPlantingSearchResults(completedScrolls, currentActionContext());
    }

    private void scrollPlantingSearchResults(
            int completedScrolls, ActionAdmission.FrameContext actionContext) {
        if (completedScrolls >= PetalMatcher.plantingSearchScrollCount(targetFlower)) {
            busy = false;
            automationStep = AutomationStep.SELECTING_SEARCH_RESULT;
            setStatus(getString(R.string.status_flower_search_keyboard_closed));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            busy = false;
            stopWithError(getString(R.string.status_flower_search_scroll_failed));
            return;
        }
        PetalMatcher.SearchResultScroll scroll = PetalMatcher.plantingSearchResultScroll(
                bounds.width(), bounds.height());
        Path path = new Path();
        path.moveTo(bounds.left + scroll.startX(), bounds.top + scroll.startY());
        path.lineTo(bounds.left + scroll.endX(), bounds.top + scroll.endY());
        busy = true;
        setStatus(getString(
                R.string.status_flower_search_scrolling_results,
                completedScrolls + 1,
                PetalMatcher.PLANTING_SEARCH_SCROLL_COUNT));
        long generation = actionContext.runGeneration();
        dispatchPath(
                path,
                FEED_DRAG_MILLIS,
                actionContext,
                () -> {
                    busy = false;
                    requestFreshCaptureOnly(
                            "CAPTURE_ONLY_PLANTING_SCROLL",
                            () -> scrollPlantingSearchResults(completedScrolls + 1),
                            () -> {
                                if (isActiveRun(generation)) {
                                    stopWithError(getString(
                                            R.string.status_flower_search_scroll_failed));
                                }
                            });
                },
                () -> {
                    busy = false;
                    stopWithError(getString(R.string.status_flower_search_scroll_failed));
                });
    }

    /**
     * 全畫面 OCR 不穩定時，沿用明信片流程裁切花盆清單並放大兩倍重讀。
     * 回呼中的 token 會換算回原始螢幕座標，確保不同解析度仍點擊同一位置。
     */
    private void scanFocusedPlantingPetalRegion(Bitmap bitmap) {
        scanFocusedPlantingPetalRegion(bitmap, false);
    }

    private void scanFocusedPlantingMonitorRegion(Bitmap bitmap) {
        scanFocusedPlantingPetalRegion(bitmap, true);
    }

    private void scanFocusedPlantingPetalRegion(Bitmap bitmap, boolean monitorRead) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int searchResultsTop = monitorRead ? -1 : plantingSearchResultsTop(bitmap);
        OcrScan.Profile profile = monitorRead
                ? OcrScan.Profile.DISPATCH_LIST
                : OcrScan.Profile.PLANTING_SEARCH_RESULTS;
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        ActionAdmission.FrameContext sourceContext = currentActionContext();
        long generation = sourceContext.runGeneration();
        long admissionEpoch = sourceContext.admissionEpoch();
        String scanTarget = monitorRead ? currentFlower : targetFlower;
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_flower_search_focused_ocr),
                scanTarget);
        startOcrTransaction(
                bitmap,
                profile,
                captureGeometry,
                admissionEpoch,
                "planting-focused",
                false,
                (ocrTransaction, frame) -> {
                runWithActionContext(ocrTransaction, frame, () -> {
                if (!isActiveRun(generation)) {
                    return;
                }
                recordOcrDiagnostic("planting-focused", frame);
                if (monitorRead) {
                    if (automationStep != AutomationStep.MONITORING
                            || !scanTarget.equals(currentFlower)) {
                        schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                        return;
                    }
                    PetalMatcher.Selection current = PetalMatcher.findFlower(
                            frame.tokens(), scanTarget, width, height);
                    if (current == null) {
                        setPlantingNoticeText(
                                getString(R.string.overlay_planting_unreadable, scanTarget), false);
                        scheduleNext();
                    } else {
                        handlePlantingMonitorSelection(current);
                    }
                    return;
                }
                if (automationStep != AutomationStep.SELECTING_SEARCH_RESULT) {
                    schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                    return;
                }
                PetalMatcher.Selection pot = searchResultsTop < 0
                        ? null
                        : PetalMatcher.findSearchedFlower(
                                frame.tokens(),
                                targetFlower,
                                plantingSearchMinimumCount,
                                width,
                                height,
                                searchResultsTop);
                if (pot == null) {
                    handlePlantingSearchMiss();
                } else {
                    confirmPlantingSearchResult(pot, width, height);
                }
                });
                },
                error -> {
                if (isActiveRun(generation)) {
                    recordOcrDiagnosticFailure(
                            "planting-focused", profile, captureGeometry, error);
                    if (monitorRead) {
                        setPlantingNoticeText(
                                getString(R.string.overlay_planting_unreadable, scanTarget), false);
                        scheduleNext();
                    } else {
                        handlePlantingSearchMiss();
                    }
                }
                });
    }

    /** 限制搜尋結果等待次數，避免搜尋不到時無限循環。 */
    private void handlePlantingSearchMiss() {
        plantingSearchMissingFrames++;
        plantingPotStability.miss();
        if (plantingSearchMissingFrames >= 6) {
            stopWithError(getString(
                    R.string.status_flower_search_result_missing,
                    PetalCatalog.searchQuery(targetFlower)));
            return;
        }
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_flower_search_waiting_result),
                PetalCatalog.searchQuery(targetFlower));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /** 要求同一個完整目標結果連續出現兩幀，再交給花盆點擊後確認。 */
    private void confirmPlantingSearchResult(
            PetalMatcher.Selection pot, int width, int height) {
        ObservationStability.Result stability = plantingPotStability.observe(
                pot.name() + ":" + pot.count(), pot.x(), pot.y(), width, height);
        plantingSearchMissingFrames = 0;
        if (stability != ObservationStability.Result.STABLE) {
            setRunStatus(
                    AutomationMode.PLANTING,
                    OverlayRunStatus.Kind.RECOGNIZING,
                    getString(
                            R.string.status_flower_search_confirming_result,
                            pot.name(),
                            pot.count(),
                            plantingPotStability.confirmations(),
                            2),
                    getString(R.string.overlay_ocr_detail));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        targetCount = pot.count();
        logPlantingSwitch("candidate-confirmed", true);
        if (PlantingFlowPolicy.shouldSkipCandidate(targetCount, settings.threshold())) {
            if (!plantingSkippedFlowers.add(targetFlower)) {
                stopWithError(getString(R.string.status_flower_search_result_missing,
                        PetalCatalog.searchQuery(targetFlower)));
                return;
            }
            PlantingFlowPolicy.LowCountDecision next =
                    PlantingFlowPolicy.afterConfirmedLowCount(settings.allowedFlowers(), targetFlower);
            plantingSkippedLast = next.action() == PlantingFlowPolicy.LowCountAction.STOP_PLANTING;
            String message = getString(R.string.status_planting_skipped_flower,
                    targetFlower, targetCount, settings.threshold());
            setStatus(message);
            setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.SEARCHING, message, "");
            logPlantingSwitch("skip-low-count", true);
            if (next.nextFlower() != null) {
                targetFlower = next.nextFlower();
                targetCount = -1;
                resetPlantingSearch();
                automationStep = AutomationStep.CLEARING_SEARCH;
                logPlantingSwitch("search-clear-before-next", true);
                schedule(700);
                return;
            }
            plantingSearchCloseGuard.reset();
            automationStep = AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
            schedule(700);
            return;
        }
        boolean shouldStart = startAfterSelection;
        resetPlantingSearch();
        tapFlower(pot, shouldStart, true);
    }

    /** 低於門檻時先以搜尋框 X 清空結果，再重新開啟完整搜尋流程。 */
    private void clearPlantingSearchForNextFlower(
            H10aPlantingAnalysis.SearchControls searchControls) {
        if (searchControls == null || !searchControls.searchOpen()) {
            automationStep = AutomationStep.OPENING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point clear = searchControls.closeButton();
        if (clear == null) {
            stopWithError(getString(R.string.status_flower_search_input_failed));
            return;
        }
        dispatchTap(
                clear.x(),
                clear.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> {
                    automationStep = AutomationStep.OPENING_SEARCH;
                    logPlantingSwitch("search-cleared-next", false);
                    schedule(700);
                },
                () -> stopWithError(getString(R.string.status_flower_search_input_failed)));
    }

    /** 清除自動種花搜尋流程的暫存，不修改目前設定中的目標花名。 */
    private void resetPlantingSearch() {
        plantingPotStability.reset();
        plantingSearchMissingFrames = 0;
        plantingSearchInputAttempts = 0;
        plantingSearchKeyboardGuard.reset();
        plantingSearchMinimumCount = 0;
        plantingSearchCloseGuard.reset();
    }

    private void resetPlantingNavigation() {
        plantingEntryStability.reset();
        plantingMenuStability.reset();
        plantingActiveStability.reset();
        plantingTransitionFrames = 0;
        plantingMonitorMissingFrames = 0;
        stopMissingConfirmations = 0;
    }

    /** 點擊已確認花盆；搜尋選取直接接回開始或監控流程，不再重複 OCR。 */
    private void tapFlower(
            PetalMatcher.Selection selection, boolean startAfter, boolean searchedSelection) {
        targetFlower = selection.name();
        targetCount = selection.count();
        startAfterSelection = startAfter;
        selectionFromSearch = searchedSelection;
        targetSelectionX = selection.x();
        targetSelectionY = selection.y();
        actionAttempts = 0;
        automationStep = AutomationStep.VERIFYING_SELECTION;
        switchGuard.requestSwitch(selection.name());
        logPlantingSwitch("selection-tap", searchedSelection);
        setStatus(getString(R.string.status_confirming_selection, selection.name()));
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.overlay_planting_switching, selection.name()),
                getString(R.string.overlay_ocr_detail));
        dispatchTap(
                selection.x(),
                selection.tapY(),
                80,
                () -> {
                    if (PlantingFlowPolicy.requiresSelectionOcrAfterTap(searchedSelection)) {
                        schedule(500);
                        return;
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (!switchGuard.confirmSwitch(selection.name(), now)) {
                        stopWithError(getString(
                                R.string.status_selection_unconfirmed, selection.name()));
                        return;
                    }
                    currentFlower = selection.name();
                    if (usageSession != null) {
                        usageSession.recordPlantingPetalRemaining(
                                currentFlower, targetCount);
                    }
                    showPlantingStatus(currentFlower, targetCount);
                    logPlantingSwitch("selection-accepted-after-tap", true);
                    selectionFromSearch = false;
                    targetSelectionX = 0;
                    targetSelectionY = 0;
                    plantingSearchCloseGuard.reset();
                    automationStep = AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
                    setStatus(getString(R.string.status_flower_search_closing_after_selection));
                    schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                },
                () -> {
                    switchGuard.cancelSwitch();
                    automationStep = AutomationStep.MONITORING;
                    scheduleNext();
                });
    }

    /** 驗證點擊後的高亮；搜尋結果可依同一卡片背景確認，不再依賴 OCR 花名。 */
    private void verifyFlowerSelection(
            PetalMatcher.Selection highlighted, boolean searchedCardHighlighted) {
        boolean exactNameConfirmed = highlighted != null
                && targetFlower.equals(highlighted.name());
        String confirmedName = exactNameConfirmed ? highlighted.name() : targetFlower;
        if ((exactNameConfirmed || searchedCardHighlighted)
                && switchGuard.confirmSwitch(
                        confirmedName, android.os.SystemClock.elapsedRealtime())) {
            int confirmedCount = exactNameConfirmed ? highlighted.count() : targetCount;
            currentFlower = confirmedName;
            targetCount = confirmedCount;
            if (usageSession != null) {
                usageSession.recordPlantingPetalRemaining(confirmedName, confirmedCount);
            }
            showPlantingStatus(confirmedName, confirmedCount);
            logPlantingSwitch("selection-confirmed", selectionFromSearch);
            actionAttempts = 0;
            boolean shouldCloseSearch = selectionFromSearch;
            selectionFromSearch = false;
            targetSelectionX = 0;
            targetSelectionY = 0;
            if (shouldCloseSearch) {
                plantingSearchCloseGuard.reset();
                automationStep = AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
                setStatus(getString(R.string.status_flower_search_closing_after_selection));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                return;
            }
            continueAfterConfirmedFlowerSelection();
            return;
        }
        if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
            switchGuard.cancelSwitch();
            stopWithError(getString(R.string.status_selection_unconfirmed, targetFlower));
        } else {
            schedule(500);
        }
    }

    /** 點擊搜尋欄右側 X，並等待搜尋欄與輸入法視窗都消失後才繼續。 */
    private void closePlantingSearchAfterSelection(
            H10aPlantingAnalysis.SearchControls searchControls,
            boolean closedMenu) {
        boolean foreground = activeGameBoundsStrict() != null;
        boolean imeVisible = isInputMethodWindowVisible();
        CardHighlight.Point close = searchControls == null
                ? null : searchControls.closeButton();
        PlantingSearchCloseGuard.Action action = plantingSearchCloseGuard.observe(
                foreground, imeVisible, close != null, closedMenu);
        logPlantingSwitch("close-search-" + action, close != null);
        switch (action) {
            case TAP_CLOSE -> dispatchTap(close.x(), close.y(), GAME_ACTION_TAP_DURATION_MILLIS,
                    () -> schedule(600),
                    () -> stopWithError(getString(R.string.status_flower_search_close_failed)));
            case WAIT -> schedule(600);
            case FAIL -> stopWithError(getString(R.string.status_flower_search_close_failed));
            case COMPLETE -> {
                plantingSearchCloseGuard.reset();
                if (plantingSkippedLast) {
                    plantingSkippedLast = false;
                    beginFinalPlantingStop();
                } else {
                    continueAfterConfirmedFlowerSelection();
                }
            }
        }
    }

    /** 搜尋介面清理完成後，接回既有的開始種花或監控流程。 */
    private void continueAfterConfirmedFlowerSelection() {
        if (SwitchGuard.isBelowThreshold(targetCount, settings.threshold())) {
            PlantingFlowPolicy.LowCountDecision lowCountDecision =
                    PlantingFlowPolicy.afterConfirmedLowCount(
                            settings.allowedFlowers(), currentFlower);
            if (lowCountDecision.action()
                    == PlantingFlowPolicy.LowCountAction.STOP_PLANTING) {
                beginFinalPlantingStop();
                return;
            }
            beginPlantingFlowerSearch(
                    lowCountDecision.nextFlower(), 0, startAfterSelection);
            return;
        }
        if (startAfterSelection) {
            automationStep = AutomationStep.WAITING_START;
            setStatus(getString(R.string.status_starting_planting, currentFlower));
            schedule(300);
            return;
        }
        automationStep = AutomationStep.MONITORING;
        targetFlower = "";
        String switchedMessage = getString(
                R.string.status_switched, currentFlower, targetCount);
        setStatus(switchedMessage);
        scheduleNext();
    }

    /** 尋找並點擊遊戲的「開始種花」控制項。 */
    private void startPlanting(PlantingControlEvidence controls) {
        PetalMatcher.Token control = controls.ocrStartControl();
        PlantingScreenAnalyzer.Point visualControl = controls.visualStartControl();
        PlantingFlowPolicy.StartAction startAction = PlantingFlowPolicy.startAction(
                controls.startVisible(),
                controls.stopVisible());
        if (startAction == PlantingFlowPolicy.StartAction.ALREADY_ACTIVE) {
            markPlantingActive(false);
            return;
        }
        if (startAction == PlantingFlowPolicy.StartAction.WAIT_FOR_CONTROL) {
            if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_start_control_unavailable));
            } else {
                schedule(500);
            }
            return;
        }

        prepareStartVerification();
        if (controls.accessibilityStartVisible() && clickGameNode(node -> nodeLabelEquals(
                node, "開始種花", "start planting"))) {
            schedule(700);
            return;
        }
        if (control != null) {
            dispatchTap(
                    control.centerX(),
                    control.centerY(),
                    80,
                    () -> schedule(700),
                    () -> stopWithError(getString(R.string.status_start_tap_failed)));
            return;
        }
        if (visualControl != null) {
            dispatchTap(
                    visualControl.x(),
                    visualControl.y(),
                    80,
                    () -> schedule(700),
                    () -> stopWithError(getString(R.string.status_start_tap_failed)));
            return;
        }
        stopWithError(getString(R.string.status_start_control_unavailable));
    }

    private void prepareStartVerification() {
        automationStep = AutomationStep.VERIFYING_START;
        actionAttempts = 0;
        resetPlantingNavigation();
    }

    /** 開始鍵點擊後確認種花面板、地圖入口或地圖上的啟動證據。 */
    private void verifyPlantingStarted(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            PlantingControlEvidence controls) {
        PlantingScreenAnalyzer.Detection detection = controls.detection();
        boolean startVisible = controls.startVisible();
        if (startVisible) {
            plantingActiveStability.reset();
            if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_start_unconfirmed));
            } else {
                schedule(700);
            }
            return;
        }

        PlantingScreenAnalyzer.Point entry = plantingEntryControl(detection);
        if (entry != null) {
            ObservationStability.Result stability = observePlantingEntry(entry);
            if (stability != ObservationStability.Result.STABLE) {
                setStatus(getString(R.string.status_planting_reentering));
                schedule(500);
                return;
            }
            plantingEntryStability.reset();
            plantingTransitionFrames = 0;
            actionAttempts = 0;
            automationStep = AutomationStep.WAITING_MENU_AFTER_START;
            setStatus(getString(R.string.status_planting_reentering));
            dispatchPlantingEntryTap(
                    entry,
                    "after-start-return source=" + detection.entryEvidence().source(),
                    () -> schedule(700),
                    () -> stopWithError(getString(R.string.status_planting_reentry_failed)));
            return;
        }

        boolean startedNotice = containsPlantingToken(tokens, "種花開始");
        boolean plantingStatsHeader = containsPlantingToken(tokens, "種植的花朵總數")
                || containsPlantingToken(tokens, "已達到獲得上限");
        boolean boostVisible = containsPlantingToken(tokens, "Boost");
        boolean activeMapEvidence = PlantingFlowPolicy.hasActiveMapEvidence(
                detection.screen(),
                startVisible,
                startedNotice,
                plantingStatsHeader,
                boostVisible);
        if (startedNotice) {
            completePlantingStartConfirmation(detection);
            return;
        }
        if (activeMapEvidence) {
            ObservationStability.Result stability = plantingActiveStability.observe(
                    "planting-active-map",
                    width / 2,
                    height / 2,
                    width,
                    height);
            if (stability == ObservationStability.Result.STABLE) {
                completePlantingStartConfirmation(detection);
            } else {
                schedule(700);
            }
            return;
        }

        plantingEntryStability.miss();
        if (detection.screen() == PlantingScreenAnalyzer.Screen.PLANTING_MENU
                && controls.accessibilityStopVisible()) {
            completePlantingStartConfirmation(detection);
            return;
        }
        if (detection.screen() == PlantingScreenAnalyzer.Screen.PLANTING_MENU
                && detection.stopControl() != null) {
            ObservationStability.Result stability = plantingActiveStability.observe(
                    "planting-menu-stop",
                    detection.stopControl().x(),
                    detection.stopControl().y(),
                    width,
                    height);
            if (stability == ObservationStability.Result.STABLE) {
                completePlantingStartConfirmation(detection);
            } else {
                schedule(700);
            }
            return;
        }
        plantingActiveStability.miss();
        if (++plantingTransitionFrames >= 6) {
            stopWithError(getString(R.string.status_start_unconfirmed));
        } else {
            setStatus(getString(R.string.status_planting_reentering));
            schedule(700);
        }
    }

    private void completePlantingStartConfirmation(
            PlantingScreenAnalyzer.Detection detection) {
        plantingActiveStability.reset();
        if (!PlantingFlowPolicy.shouldReturnToMenuAfterConfirmedStart(detection.screen())) {
            markPlantingActive(true);
            return;
        }
        plantingEntryStability.reset();
        plantingMenuStability.reset();
        plantingTransitionFrames = 0;
        actionAttempts = 0;
        automationStep = AutomationStep.WAITING_MENU_AFTER_START;
        setStatus(getString(R.string.status_planting_reentering));
        schedule(500);
    }

    /** 完成開始或確認原本已在種花，再進入低數量監控。 */
    private void markPlantingActive(boolean newlyStarted) {
        automationStep = AutomationStep.MONITORING;
        targetFlower = "";
        startAfterSelection = false;
        actionAttempts = 0;
        setStatus(getString(newlyStarted
                ? R.string.status_planting_started
                : R.string.status_planting_already_active, currentFlower));
        resetPlantingNavigation();
        scheduleNext();
    }

    /** 返回種花面板後，再搜尋一次當前花盆，讓數量卡回到可監控位置。 */
    private void resumePlantingAfterMenuReturn() {
        startAfterSelection = false;
        actionAttempts = 0;
        resetPlantingNavigation();
        if (currentFlower.isEmpty()) {
            returnToInitialPlantingEntry();
            return;
        }
        beginPlantingFlowerSearch(currentFlower, 0, false);
    }

    /** 最後順位低於門檻後，轉入遊戲內停止鍵流程。 */
    private void beginFinalPlantingStop() {
        automationStep = AutomationStep.WAITING_STOP;
        actionAttempts = 0;
        stopMissingConfirmations = 0;
        plantingTransitionFrames = 0;
        plantingEntryStability.reset();
        setStatus(getString(R.string.status_stopping_planting, currentFlower));
        schedule(300);
    }

    /** 只在停止鍵或其無障礙節點已確認時發送點擊。 */
    private void stopPlanting(PlantingControlEvidence controls) {
        PlantingScreenAnalyzer.Detection detection = controls.detection();
        if (!controls.stopVisible()) {
            if (controls.startVisible()) {
                finishWithSuccess(getString(R.string.status_planting_stopped, currentFlower));
                return;
            }
            if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_stop_control_unavailable));
            } else {
                setStatus(getString(R.string.status_stopping_planting, currentFlower));
                schedule(500);
            }
            return;
        }

        automationStep = AutomationStep.VERIFYING_STOP;
        actionAttempts = 0;
        stopMissingConfirmations = 0;
        plantingTransitionFrames = 0;
        if (controls.accessibilityStopVisible() && clickGameNode(node -> nodeLabelEquals(
                node, "停止種花", "stop planting"))) {
            schedule(700);
            return;
        }
        PlantingScreenAnalyzer.Point stop = controls.visualStopControl();
        if (stop == null) {
            stopWithError(getString(R.string.status_stop_control_unavailable));
            return;
        }
        dispatchTap(
                stop.x(),
                stop.y(),
                80,
                () -> schedule(700),
                () -> stopWithError(getString(R.string.status_stop_tap_failed)));
    }

    /** 停止點擊後要求播放鍵或地圖入口連續出現，不以停止鍵短暫消失為成功。 */
    private void verifyPlantingStopped(PlantingControlEvidence controls) {
        PlantingScreenAnalyzer.Detection detection = controls.detection();
        if (controls.stopVisible()) {
            stopMissingConfirmations = 0;
            if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_stop_unconfirmed));
            } else {
                schedule(700);
            }
            return;
        }
        boolean stoppedEvidence = controls.startVisible()
                || detection.screen() == PlantingScreenAnalyzer.Screen.MAP_WITH_ENTRY;
        if (stoppedEvidence) {
            if (++stopMissingConfirmations >= 2) {
                finishWithSuccess(getString(
                        R.string.status_planting_stopped, currentFlower));
            } else {
                schedule(500);
            }
            return;
        }
        stopMissingConfirmations = 0;
        if (++plantingTransitionFrames >= 6) {
            stopWithError(getString(R.string.status_stop_unconfirmed));
        } else {
            schedule(700);
        }
    }

    private PlantingControlEvidence collectPlantingControlEvidence(
            PetalMatcher.Token ocrStartControl,
            PlantingScreenAnalyzer.Detection detection) {
        return new PlantingControlEvidence(
                detection,
                ocrStartControl,
                hasStartPlantingNode(),
                hasStopPlantingNode());
    }

    private static boolean containsPlantingToken(
            List<PetalMatcher.Token> tokens, String expected) {
        String normalizedExpected = PetalMatcher.normalize(expected);
        for (PetalMatcher.Token token : tokens) {
            if (PetalMatcher.normalize(token.text()).contains(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStartPlantingNode() {
        return findGameNode(node -> nodeLabelEquals(
                node, "開始種花", "start planting")) != null;
    }

    private boolean hasStopPlantingNode() {
        return findGameNode(node -> nodeLabelEquals(
                node, "停止種花", "stop planting")) != null;
    }

    /** 點擊符合條件的遊戲無障礙節點或其可點擊父節點。 */
    private boolean clickGameNode(Predicate<AccessibilityNodeInfo> predicate) {
        ActionAdmission.FrameContext actionContext = currentActionContext();
        AccessibilityNodeInfo node = findGameNode(predicate);
        while (node != null) {
            if (node.isClickable()) {
                ActionGateway.Result result = actionGateway().nodeClick(
                        actionContext,
                        node,
                        candidate -> candidate instanceof AccessibilityNodeInfo current
                                && current.isClickable()
                                && current.getPackageName() != null
                                && GAME_PACKAGE.contentEquals(current.getPackageName())
                                && (actionContext.windowId() < 0
                                        || current.getWindowId() == actionContext.windowId()));
                if (!result.decision().allowed()) {
                    handleStaleAction(() -> { });
                    return false;
                }
                return result.dispatched();
            }
            node = node.getParent();
        }
        return false;
    }

    /** 從目前遊戲視窗根節點開始搜尋符合條件的節點。 */
    private AccessibilityNodeInfo findGameNode(Predicate<AccessibilityNodeInfo> predicate) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !GAME_PACKAGE.contentEquals(root.getPackageName())) {
            return null;
        }
        return findNode(root, predicate);
    }

    /** 深度優先走訪無障礙節點樹，找到第一個符合條件的節點。 */
    private AccessibilityNodeInfo findNode(
            AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> predicate) {
        if (predicate.test(node)) {
            return node;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo result = findNode(child, predicate);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /** 比對節點文字或 content description，處理無障礙控制項名稱差異。 */
    private boolean nodeLabelEquals(AccessibilityNodeInfo node, String... values) {
        String text = PetalMatcher.normalize(
                node.getText() == null ? "" : node.getText().toString());
        String description = PetalMatcher.normalize(
                node.getContentDescription() == null
                        ? ""
                        : node.getContentDescription().toString());
        for (String value : values) {
            String expected = PetalMatcher.normalize(value);
            if (text.equals(expected) || description.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /** 合併節點文字、描述與提示，供比對使用。 */
    private String nodeLabel(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        CharSequence hint = node.getHintText();
        String textValue = text == null ? "" : text.toString();
        String descriptionValue = description == null ? "" : description.toString();
        String hintValue = hint == null ? "" : hint.toString();
        return PetalMatcher.normalize(textValue + " " + descriptionValue + " " + hintValue);
    }

    /** 依附圖中的 OCR 錨點驅動明信片流程，每次動作都等待下一張畫面確認。 */
    private void handleReturnRewardTarget(
            ReturnRewardDetector.Target target, int width, int height) {
        if (returnRewardTimedOut()) {
            stopWithError(getString(R.string.status_return_reward_timeout));
            return;
        }
        if (activeGameBoundsStrict() == null) {
            stopWithError(getString(R.string.status_return_reward_left_game));
            return;
        }
        if (returnRewardWaitingPostcardExit) {
            resetReturnRewardPostcard();
        }
        long now = android.os.SystemClock.elapsedRealtime();
        long sinceTap = now - returnRewardLastTapAt;
        if (returnRewardLastTapAt > 0 && sinceTap < RETURN_REWARD_SETTLE_MILLIS) {
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SETTLE_MILLIS - sinceTap);
            return;
        }
        returnRewardLastTapAt = now;
        setReturnRewardStatus(getString(R.string.status_return_reward_tapping));
        dispatchTap(
                target.x(),
                target.y(),
                70L,
                () -> schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS),
                () -> stopWithError(getString(R.string.status_return_reward_gesture_failed)));
    }

    private void handleReturnRewardTokens(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        long detectorStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (returnRewardTimedOut()) {
                stopWithError(getString(R.string.status_return_reward_timeout));
                return;
            }
        PostcardMatcher.Page postcardPage = PostcardMatcher.detectPage(tokens, width, height);
        boolean postcardVisible = postcardPage == PostcardMatcher.Page.POSTCARD_RECEIVED;
        if (postcardVisible
                && returnRewardScanGuard.observe(postcardPage, null, width, height)
                        == ReturnRewardScanGuard.Decision.POSTCARD) {
            PostcardMatcher.Target target = returnRewardReceivePostcard
                    ? PostcardMatcher.findReceive(tokens)
                    : PostcardMatcher.findDiscard(tokens, width, height);
            if (target == null) {
                returnRewardPostcardTarget = null;
                returnRewardPostcardConfirmations = 0;
                schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                return;
            }
            if (returnRewardPostcardTarget != null
                    && Math.abs(target.x() - returnRewardPostcardTarget.x()) <= width * 0.04f
                    && Math.abs(target.y() - returnRewardPostcardTarget.y()) <= height * 0.025f) {
                returnRewardPostcardConfirmations++;
            } else {
                returnRewardPostcardTarget = target;
                returnRewardPostcardConfirmations = 1;
            }
            if (returnRewardPostcardConfirmations < 2) {
                setReturnRewardStatus(getString(returnRewardReceivePostcard
                        ? R.string.status_return_reward_postcard_receive
                        : R.string.status_return_reward_postcard_discard));
                schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                return;
            }
            if (returnRewardPostcardAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_return_reward_postcard_missing));
                return;
            }
            returnRewardPostcardAttempts++;
            returnRewardPostcardTarget = null;
            returnRewardPostcardConfirmations = 0;
            returnRewardWaitingPostcardExit = true;
            returnRewardLastTapAt = android.os.SystemClock.elapsedRealtime();
            setReturnRewardStatus(getString(returnRewardReceivePostcard
                    ? R.string.status_return_reward_postcard_receive
                    : R.string.status_return_reward_postcard_discard));
            dispatchTap(
                    target.x(),
                    target.y(),
                    85L,
                    () -> schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS),
                    () -> {
                        if (returnRewardPostcardAttempts >= MAX_ACTION_ATTEMPTS) {
                            stopWithError(getString(R.string.status_return_reward_postcard_missing));
                        } else {
                            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                        }
                    });
            return;
        }

        ReturnRewardDetector.Region rewardRegion =
                returnRewardRoi.detectorRegion(currentCaptureGeometry());
        if (rewardRegion == null) {
            stopWithError(getString(R.string.status_return_reward_roi_failed));
            return;
        }
        ReturnRewardDetector.Target detectedRewardTarget = ReturnRewardDetector.find(
                width, height, bitmap::getPixel, rewardRegion);
        boolean pikminDetailOpen = FeedScreenAnalyzer.isPikminDetailOpen(tokens);
        ReturnRewardDetector.SquadCloseup squadCloseup =
                ReturnRewardDetector.classifySquadCloseup(
                        width, height, bitmap::getPixel);
        boolean squadCloseupConfirmed = pikminDetailOpen
                || squadCloseup == ReturnRewardDetector.SquadCloseup.SQUAD_CLOSEUP;
        boolean squadCloseupUnknown = !pikminDetailOpen
                && squadCloseup == ReturnRewardDetector.SquadCloseup.UNKNOWN;
        ReturnRewardDetector.Target rewardTarget = squadCloseupConfirmed
                || squadCloseupUnknown
                ? null : detectedRewardTarget;

        boolean nectarWarningVisible = ReturnRewardDetector.hasNectarCapacityWarning(
                tokens, width, height);
        if (nectarWarningVisible) {
            returnRewardNectarWarningFrames++;
        } else {
            returnRewardNectarWarningFrames = 0;
            returnRewardNectarWarningActive = false;
        }
        boolean nectarWarningConfirmed = returnRewardNectarWarningFrames
                >= RETURN_REWARD_REQUIRED_WARNING_FRAMES;
        if (nectarWarningConfirmed && !returnRewardNectarWarningActive) {
            returnRewardNectarWarningActive = true;
            if (!returnRewardContinueOnNectarWarning) {
                stopWithError(getString(
                        R.string.status_return_reward_nectar_warning_stopped));
                return;
            }
            setReturnRewardStatus(getString(
                    R.string.status_return_reward_nectar_warning_continuing));
        }
        if (nectarWarningVisible && rewardTarget == null) {
            setReturnRewardStatus(getString(nectarWarningConfirmed
                    ? R.string.status_return_reward_nectar_warning_continuing
                    : R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
            return;
        }

        if (returnRewardWaitingPostcardExit) {
            resetReturnRewardPostcard();
            returnRewardLastTapAt = android.os.SystemClock.elapsedRealtime();
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
            return;
        }
        returnRewardPostcardTarget = null;
        returnRewardPostcardConfirmations = 0;
        long sinceTap = android.os.SystemClock.elapsedRealtime() - returnRewardLastTapAt;
        if (returnRewardLastTapAt > 0 && sinceTap < RETURN_REWARD_SETTLE_MILLIS) {
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SETTLE_MILLIS - sinceTap);
            return;
        }
        boolean persistentTargetRearmEligible = returnRewardLastTapAt > 0
                && sinceTap >= RETURN_REWARD_PERSISTENT_TARGET_REARM_MILLIS;
        PostcardMatcher.Page guardPage = squadCloseupUnknown
                ? PostcardMatcher.Page.UNKNOWN : postcardPage;
        ReturnRewardScanGuard.Decision decision = returnRewardScanGuard.observe(
                guardPage,
                rewardTarget,
                width,
                height,
                persistentTargetRearmEligible,
                squadCloseupConfirmed);
        if (decision == ReturnRewardScanGuard.Decision.TARGET_CONFIRMED) {
            handleReturnRewardTarget(rewardTarget, width, height);
            return;
        }
        if (decision == ReturnRewardScanGuard.Decision.SQUAD_COMPLETE) {
            finishWithSuccess(getString(R.string.status_return_reward_complete));
            return;
        }
        if (decision == ReturnRewardScanGuard.Decision.COMPLETE) {
            finishWithSuccess(getString(R.string.status_return_reward_complete));
            return;
        }
        setReturnRewardStatus(getString(rewardTarget == null
                ? R.string.status_return_reward_waiting
                : R.string.status_return_reward_confirming));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.MAP_DETECTOR,
                    android.os.SystemClock.uptimeMillis() - detectorStartedAtUptimeMillis);
        }
    }

    private boolean returnRewardTimedOut() {
        return returnRewardStartedAt > 0
                && android.os.SystemClock.elapsedRealtime() - returnRewardStartedAt
                        >= RETURN_REWARD_TIMEOUT_MILLIS;
    }

    private void setReturnRewardStatus(String message) {
        setStatus(message);
        setRunStatus(
                AutomationMode.RETURN_REWARD,
                OverlayRunStatus.Kind.RECOGNIZING,
                message,
                getString(R.string.overlay_return_reward_safety));
    }

    private void resetReturnRewardPostcard() {
        returnRewardPostcardTarget = null;
        returnRewardPostcardConfirmations = 0;
        returnRewardPostcardAttempts = 0;
        returnRewardWaitingPostcardExit = false;
    }

    private void handlePostcardTokens(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        PostcardAutomation.Step step = postcardAutomation.step();
        boolean receiveTapped = postcardAutomation.receiveTapped();
        PostcardMatcher.Page page = PostcardMatcher.detectPage(tokens, width, height);
        boolean flowerNavigationStep = isFlowerNavigationStep(step);
        FlowerDetailActionDetector.Target flowerDetailAction = flowerNavigationStep
                ? FlowerDetailActionDetector.find(width, height, bitmap::getPixel)
                : null;
        MapPostcardBubbleDetector.Target previousFlowerBubble =
                PostcardBubbleDetectionPolicy.shouldDetect(page, step, receiveTapped)
                        ? MapPostcardBubbleDetector.find(width, height, bitmap::getPixel)
                        : null;
        if (page == PostcardMatcher.Page.UNKNOWN
                && previousFlowerBubble != null
                && (flowerNavigationStep || step == PostcardAutomation.Step.WAIT_RECEIPT_EXIT)) {
            page = PostcardMatcher.Page.MAP;
        }
        if (postcardAutomation.step() == PostcardAutomation.Step.USE_PETALS
                && page != PostcardMatcher.Page.WARNING
                && looksLikeWarningDialog(bitmap)) {
            page = PostcardMatcher.Page.WARNING;
        }
        if ((page == PostcardMatcher.Page.UNKNOWN || page == PostcardMatcher.Page.MAP)
                && flowerNavigationStep
                && flowerDetailAction != null) {
            page = PostcardMatcher.Page.FLOWER_DETAIL;
        }
        // The Pikmin page keeps the same flower background and white bottom
        // sheet as the detail page. Never let the visual detail fallback run
        // after the petal flow has begun, otherwise its fallback tap lands on
        // an arbitrary Pikmin card during the page transition.
        if (page == PostcardMatcher.Page.FLOWER_DETAIL && !flowerNavigationStep) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        boolean mapAllowed = flowerNavigationStep || step == PostcardAutomation.Step.WAIT_RECEIPT_EXIT;
        if (page == PostcardMatcher.Page.MAP && !mapAllowed) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        if (PostcardPageRecovery.shouldRetryStableFrame(page, postcardAutomation.step())) {
            postcardUnknownFrames = 0;
            int waitingMessage = isPetalSearchStep(postcardAutomation.step())
                    ? R.string.status_postcard_waiting_search_result
                    : R.string.status_postcard_waiting_page;
            setPostcardStatus(
                    OverlayRunStatus.Kind.RECOGNIZING,
                    getString(waitingMessage),
                    getString(R.string.overlay_ocr_detail));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }

        if (receiveTapped
                && page != PostcardMatcher.Page.POSTCARD_RECEIVED) {
            if (postcardAutomation.step() == PostcardAutomation.Step.WAIT_RECEIPT_EXIT
                    && previousFlowerBubble == null
                    && !postcardReturnFlowerTapped) {
                PostcardMatcher.Target flower = findPostcardReturnFlower(bitmap, page);
                if (flower != null) {
                    postcardReturnFlowerTapped = true;
                    tapPostcardTarget(
                            flower,
                            PostcardAutomation.Step.WAIT_RECEIPT_EXIT,
                            getString(R.string.status_postcard_revealing_previous_bubble),
                            POSTCARD_RECEIPT_RETURN_VERIFY_DELAY_MILLIS);
                    return;
                }
            }
            PostcardReturnGuard.Decision returnDecision =
                    postcardReturnGuard.observe(
                            true, previousFlowerBubble != null);
            if (returnDecision == PostcardReturnGuard.Decision.WAIT) {
                setPostcardStatus(getString(R.string.status_postcard_checking_returned_bubble));
                schedule(POSTCARD_RECEIPT_RETURN_VERIFY_DELAY_MILLIS);
                return;
            }
            if (returnDecision == PostcardReturnGuard.Decision.FAILED) {
                stopWithError(getString(R.string.status_postcard_returned_bubble_missing));
                return;
            }
            if (!confirmPostcardReceiptExit()) {
                return;
            }
            openPreviousPostcardBubble(previousFlowerBubble);
            return;
        }

        if (page == PostcardMatcher.Page.UNKNOWN
                && postcardAutomation.step() == PostcardAutomation.Step.FIND_FLOWER) {
            postcardUnknownFrames++;
            if (postcardUnknownFrames >= 8) {
                stopWithError(getString(R.string.status_postcard_returned_bubble_missing));
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_previous_bubble));
                schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            }
            return;
        }
        if (page == PostcardMatcher.Page.UNKNOWN) {
            postcardUnknownFrames++;
            if (postcardUnknownFrames >= 8) {
                stopWithError(getString(R.string.status_postcard_unknown_stopped));
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_page));
                schedule(900);
            }
            return;
        }
        postcardUnknownFrames = 0;

        if (page == PostcardMatcher.Page.FLOWER_DETAIL
                && postcardAutomation.step() == PostcardAutomation.Step.FIND_FLOWER
                && postcardAutomation.completedCount() > 0) {
            returnToMapFromFlowerDetail();
            return;
        }
        if (page == PostcardMatcher.Page.MAP) {
            postcardBackAttempts = 0;
        }

        switch (page) {
            case POSTCARD_RECEIVED -> receivePostcard(tokens);
            case PIKMIN_SELECTION -> handlePikminSelection(tokens, width, height);
            case PETAL_SELECTION -> handlePostcardPetalSelection(tokens, bitmap);
            case WARNING -> acceptPostcardWarning(tokens, width, height);
            case FLOWER_DETAIL -> openPostcardFromFlower(tokens, flowerDetailAction);
            case MAP -> openPreviousPostcardBubble(previousFlowerBubble);
            default -> schedule(900);
        }
    }

    private static PostcardMatcher.Target findPostcardReturnFlower(
            Bitmap bitmap, PostcardMatcher.Page page) {
        if (page != PostcardMatcher.Page.UNKNOWN && page != PostcardMatcher.Page.MAP) {
            return null;
        }
        MapSceneDetector.Detection detection = MapSceneDetector.detect(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        if (detection.flowers().size() < 2 && detection.mushrooms().size() < 16) {
            return null;
        }
        int targetX = Math.round(bitmap.getWidth() * 0.52f);
        int targetY = Math.round(bitmap.getHeight() * 0.44f);
        return new PostcardMatcher.Target("postcard-return-flower", targetX, targetY);
    }

    private static boolean isFlowerNavigationStep(PostcardAutomation.Step step) {
        return step == PostcardAutomation.Step.FIND_FLOWER
                || step == PostcardAutomation.Step.OPEN_FLOWER;
    }

    private static boolean isPetalSearchStep(PostcardAutomation.Step step) {
        return step == PostcardAutomation.Step.OPEN_PETAL_SEARCH
                || step == PostcardAutomation.Step.ENTER_PETAL_SEARCH
                || step == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD
                || step == PostcardAutomation.Step.SELECT_PETAL
                || step == PostcardAutomation.Step.TAP_NEXT;
    }

    private void acceptPostcardWarning(
            List<PetalMatcher.Token> tokens, int width, int height) {
        PostcardMatcher.Target accept = PostcardMatcher.findAcceptContinue(tokens);
        if (accept == null) {
            accept = new PostcardMatcher.Target(
                    "warning-image-accept",
                    Math.round(width * 0.69f),
                    Math.round(height * 0.58f));
        }
        tapPostcardTarget(
                accept,
                PostcardAutomation.Step.OPEN_PETAL_SEARCH,
                getString(R.string.status_postcard_accepting),
                700);
    }

    private boolean confirmPostcardReceiptExit() {
        postcardReturnGuard.reset();
        if (!postcardAutomation.confirmReceiptExit()) {
            return false;
        }
        int persistedRemaining = settings.recordConfirmedPostcardReceipt();
        if (persistedRemaining < 0) {
            stopWithError(getString(R.string.status_postcard_progress_save_failed));
            return false;
        }
        actionAttempts = 0;
        postcardReceiptWaitFrames = 0;
        postcardPikminCountConfirmations = 0;
        postcardLastPikminCount = -1;
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        if (postcardAutomation.isComplete()) {
            finishPostcardAutomation();
            return false;
        }
        setPostcardStatus(
                OverlayRunStatus.Kind.SUCCESS,
                getString(
                        R.string.status_postcard_progress,
                        postcardAutomation.completedCount(),
                        postcardAutomation.collectionLimit()),
                getString(
                        R.string.overlay_postcard_progress_detail,
                        postcardAutomation.completedCount(),
                        postcardAutomation.collectionLimit()));
        return true;
    }

    /** 點擊前次領取明信片後留在地圖上的黑色資訊框；完全不讀取框內文字。 */
    private void openPreviousPostcardBubble(MapPostcardBubbleDetector.Target bubble) {
        if (bubble == null) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_previous_bubble));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        tapPostcardTarget(
                new PostcardMatcher.Target("previous-postcard-bubble", bubble.x(), bubble.y()),
                PostcardAutomation.Step.OPEN_FLOWER,
                getString(R.string.status_postcard_opening_previous_bubble),
                POSTCARD_VERIFY_DELAY_MILLIS);
    }

    private void openPostcardFromFlower(
            List<PetalMatcher.Token> tokens,
            FlowerDetailActionDetector.Target detectedAction) {
        PostcardMatcher.Target usePetals = PostcardMatcher.findUsePetals(tokens);
        if (usePetals == null && detectedAction != null) {
            usePetals = new PostcardMatcher.Target(
                    "detail-image-button",
                    detectedAction.x(),
                    detectedAction.y());
        }
        if (usePetals == null) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        postcardMissingControlFrames = 0;
        tapPostcardTarget(
                usePetals,
                PostcardAutomation.Step.USE_PETALS,
                getString(R.string.status_postcard_using_petals),
                700);
    }

    /**
     * Language-independent warning modal check. The fallback is gated by the
     * USE_PETALS step and requires both the large white dialog and red accept
     * control, so ordinary detail/Pikmin sheets cannot trigger it.
     */
    private boolean looksLikeWarningDialog(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = Math.round(width * 0.06f);
        int right = Math.round(width * 0.94f);
        int top = Math.round(height * 0.38f);
        int bottom = Math.round(height * 0.66f);
        int step = Math.max(4, width / 96);
        int sampled = 0;
        int white = 0;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                int color = bitmap.getPixel(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                sampled++;
                if (red >= 235 && green >= 235 && blue >= 235) {
                    white++;
                }
            }
        }
        int redControlPixels = 0;
        int buttonLeft = Math.round(width * 0.52f);
        int buttonRight = Math.round(width * 0.86f);
        int buttonTop = Math.round(height * 0.53f);
        int buttonBottom = Math.round(height * 0.62f);
        for (int y = buttonTop; y < buttonBottom; y += step) {
            for (int x = buttonLeft; x < buttonRight; x += step) {
                int color = bitmap.getPixel(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                if (red >= 180 && red - green >= 35 && red - blue >= 35) {
                    redControlPixels++;
                }
            }
        }
        return sampled > 0
                && white * 100 >= sampled * 58
                && redControlPixels >= 8;
    }

    private void handlePostcardPetalSelection(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (postcardAutomation.step() == PostcardAutomation.Step.OPEN_PETAL_SEARCH) {
            openPostcardPetalSearch(bitmap);
            return;
        }
        if (postcardAutomation.step() == PostcardAutomation.Step.ENTER_PETAL_SEARCH) {
            enterPostcardPetalSearch();
            return;
        }
        if (postcardAutomation.step() == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
            closePostcardKeyboard(bitmap);
            return;
        }
        if (postcardAutomation.step() == PostcardAutomation.Step.TAP_NEXT) {
            tapNextAfterPostcardPetal(tokens);
            return;
        }
        if (postcardAutomation.step() == PostcardAutomation.Step.NEXT
                || postcardAutomation.step() == PostcardAutomation.Step.OPEN_SORT) {
            PostcardMatcher.Target next = PostcardMatcher.findNext(tokens);
            if (next == null) {
                setPostcardStatus(getString(R.string.status_postcard_waiting_next));
                schedule(700);
                return;
            }
            tapPostcardTarget(
                    next,
                    PostcardAutomation.Step.OPEN_SORT,
                    getString(R.string.status_postcard_next),
                    850);
            return;
        }

        String expectedQuery = PetalCatalog.searchQuery(
                postcardAutomation.petalPotName());
        if (!gameEditableTextMatches(expectedQuery)) {
            postcardAutomation.moveTo(PostcardAutomation.Step.ENTER_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_confirming_search_text));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }

        PostcardMatcher.PetalPot pot = PostcardMatcher.findSingleVisiblePetalPot(
                tokens, postcardAutomation.petalPotName(), 80, width, height);
        if (pot == null) {
            scanFocusedPetalRegion(bitmap);
            return;
        }
        confirmPostcardPetalPot(pot, width, height);
    }

    private void openPostcardPetalSearch(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CardHighlight.PetalSearchAnalysis searchAnalysis = CardHighlight.analyzePetalSearchControls(
                width, height, bitmap::getPixel);
        if (searchAnalysis.searchOpen()) {
            postcardPetalSearchMissingFrames = 0;
            postcardAutomation.moveTo(PostcardAutomation.Step.ENTER_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_search_opened));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point search = searchAnalysis.searchButton();
        if (search == null && ++postcardPetalSearchMissingFrames >= 6) {
            stopWithError(getString(R.string.status_postcard_search_open_failed));
            return;
        }
        if (search == null) {
            setPostcardStatus(getString(R.string.status_postcard_opening_search));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        postcardPetalSearchMissingFrames = 0;
        tapPostcardTarget(
                new PostcardMatcher.Target(
                        "petal-search",
                        search.x(),
                        search.y()),
                PostcardAutomation.Step.OPEN_PETAL_SEARCH,
                getString(R.string.status_postcard_opening_search),
                POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    private void enterPostcardPetalSearch() {
        String query = PetalCatalog.searchQuery(postcardAutomation.petalPotName());
        if (query.isBlank()) {
            stopWithError(getString(R.string.status_postcard_invalid_search_name));
            return;
        }
        if (!setGameEditableText(query)) {
            postcardPetalInputAttempts++;
            if (postcardPetalInputAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_postcard_search_input_failed));
            } else {
                postcardAutomation.moveTo(PostcardAutomation.Step.OPEN_PETAL_SEARCH);
                setPostcardStatus(getString(R.string.status_postcard_retrying_search_input));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            }
            return;
        }
        postcardPetalInputAttempts = 0;
        postcardSearchKeyboardGuard.reset();
        postcardAutomation.moveTo(PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD);
        setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /**
     * 依 Android 視窗類型關閉輸入法，不辨識任何廠牌鍵盤上的完成、返回或箭頭按鍵。
     * 只有在 TYPE_INPUT_METHOD 確實存在時才送出系統返回，並要求連續兩幀看不到鍵盤後
     * 才進入花盆辨識，避免鍵盤動畫或視窗事件延遲造成過早 OCR。
     */
    private void closePostcardKeyboard(Bitmap bitmap) {
        String query = PetalCatalog.searchQuery(postcardAutomation.petalPotName());
        boolean searchPageConfirmed = CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)
                && gameEditableTextMatches(query);
        SearchKeyboardGuard.Action action = observeSearchKeyboard(postcardSearchKeyboardGuard, query, searchPageConfirmed);
        if (action == SearchKeyboardGuard.Action.COMPLETE) {
            postcardSearchKeyboardGuard.reset();
            postcardAutomation.moveTo(PostcardAutomation.Step.SELECT_PETAL);
            setPostcardStatus(getString(R.string.status_postcard_keyboard_closed));
            schedule(700);
            return;
        }
        if (action == SearchKeyboardGuard.Action.SEND_BACK) {
            if (!sendSearchKeyboardBack(postcardSearchKeyboardGuard, query, searchPageConfirmed)) {
                stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
                return;
            }
            setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
            schedule(600);
            return;
        }
        if (action == SearchKeyboardGuard.Action.WAIT) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_keyboard_close));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
    }

    /**
     * Accessibility exposes no direct IME-to-editor binding. Require the game's focused
     * editor/window and a nonempty IME on the same display above that window.
     */
    private SearchKeyboardGuard.Evidence searchKeyboardEvidence(
            String query, boolean searchPageConfirmed) {
        AccessibilityNodeInfo editor = findFocusedGameEditableText();
        boolean focused = editor != null && editableTextMatches(editor, query);
        boolean foreground = activeGameBoundsStrict() != null;
        boolean imeVisible = false;
        boolean associated = false;
        String identity = "";
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityWindowInfo gameWindow = null;
        if (windows != null && focused) {
            for (AccessibilityWindowInfo window : windows) {
                if (window != null && window.getId() == editor.getWindowId()
                        && window.isFocused()
                        && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    gameWindow = window;
                    break;
                }
            }
        }
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    continue;
                }
                Rect imeBounds = new Rect();
                window.getBoundsInScreen(imeBounds);
                if (imeBounds.isEmpty()) {
                    continue;
                }
                imeVisible = true;
                if (gameWindow != null && window.getDisplayId() == gameWindow.getDisplayId()
                        && window.getLayer() > gameWindow.getLayer()) {
                    associated = true;
                    identity = editor.getWindowId() + ":" + editor.hashCode() + ":"
                            + window.getId() + ":"
                            + editor.getViewIdResourceName() + ":" + PetalMatcher.normalize(query);
                }
            }
        }
        return new SearchKeyboardGuard.Evidence(
                foreground, imeVisible, searchPageConfirmed, focused, associated, identity);
    }

    private SearchKeyboardGuard.Action observeSearchKeyboard(
            SearchKeyboardGuard guard, String query, boolean searchPageConfirmed) {
        SearchKeyboardGuard.Evidence evidence = searchKeyboardEvidence(query, searchPageConfirmed);
        SearchKeyboardGuard.Action action = guard.observe(evidence);
        Log.i(TAG, "SEARCH_KEYBOARD mode=" + automationMode + " event=observe action=" + action
                + " imeVisible=" + evidence.inputMethodVisible()
                + " searchPageConfirmed=" + evidence.searchPageConfirmed()
                + " focusedInput=" + evidence.focusedSearchInput()
                + " imeAssociated=" + evidence.imeAssociated());
        return action;
    }

    private boolean sendSearchKeyboardBack(
            SearchKeyboardGuard guard, String query, boolean searchPageConfirmed) {
        SearchKeyboardGuard.Evidence fresh = searchKeyboardEvidence(query, searchPageConfirmed);
        boolean permitted = guard.permitsDispatch(fresh);
        Log.i(TAG, "SEARCH_KEYBOARD mode=" + automationMode
                + " event=back-check permitted=" + permitted
                + " imeVisible=" + fresh.inputMethodVisible()
                + " searchPageConfirmed=" + fresh.searchPageConfirmed()
                + " focusedInput=" + fresh.focusedSearchInput()
                + " imeAssociated=" + fresh.imeAssociated());
        if (automationMode == AutomationMode.PLANTING) {
            logPlantingSwitch("keyboard-back-check", searchPageConfirmed);
        }
        return permitted && performGameGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void logPlantingSwitch(String event, boolean searchPageConfirmed) {
        Log.i(TAG, "PLANTING_SWITCH event=" + event
                + " currentFlower=" + currentFlower + " targetFlower=" + targetFlower
                + " targetCount=" + targetCount + " automationStep=" + automationStep
                + " imeVisible=" + isInputMethodWindowVisible()
                + " searchPageConfirmed=" + searchPageConfirmed);
    }

    /** 讀取互動視窗清單，跨 Gboard、三星、小米等輸入法判斷軟鍵盤是否仍顯示。 */
    private boolean isInputMethodWindowVisible() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            if (window != null
                    && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                return true;
            }
        }
        return false;
    }

    /** 派遣搜尋只接受目前聚焦的欄位，避免 Unity 隱藏 EditText 造成誤判。 */
    private boolean hasFocusedGameEditableText() {
        return findFocusedGameEditableText() != null;
    }

    private AccessibilityNodeInfo findFocusedGameEditableText() {
        return findGameNode(node ->
                node.isEditable() && node.isEnabled() && node.isFocused());
    }

    private boolean setFocusedGameEditableText(String value) {
        return setEditableText(findFocusedGameEditableText(), value);
    }

    private boolean focusedGameEditableTextMatches(String value) {
        return editableTextMatches(findFocusedGameEditableText(), value);
    }

    private boolean setGameEditableText(String value) {
        return setEditableText(findGameNode(node ->
                node.isEditable() && node.isEnabled()), value);
    }

    /** Applies the final capture/window admission immediately before an editable node mutation. */
    private boolean performGameEditableNodeAction(
            AccessibilityNodeInfo editable, int action, Bundle arguments) {
        ActionAdmission.FrameContext actionContext = currentActionContext();
        ActionGateway.Result result;
        if (action == AccessibilityNodeInfo.ACTION_FOCUS) {
            result = actionGateway().editableFocus(
                    actionContext,
                    editable,
                    candidate -> candidate instanceof AccessibilityNodeInfo current
                            && isCurrentGameEditableNode(current, actionContext));
        } else if (action == AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) {
            result = actionGateway().editableClearFocus(
                    actionContext,
                    editable,
                    candidate -> candidate instanceof AccessibilityNodeInfo current
                            && current.isFocused()
                            && isCurrentGameEditableNode(current, actionContext));
        } else if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
            CharSequence value = arguments == null ? null : arguments.getCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE);
            result = actionGateway().editableSetText(
                    actionContext,
                    editable,
                    value == null ? "" : value.toString(),
                    candidate -> candidate instanceof AccessibilityNodeInfo current
                            && isCurrentGameEditableNode(current, actionContext));
        } else {
            return false;
        }
        if (!result.decision().allowed()) {
            handleStaleAction(() -> { });
            return false;
        }
        return result.dispatched();
    }

    private boolean isCurrentGameEditableNode(
            AccessibilityNodeInfo editable, ActionAdmission.FrameContext actionContext) {
        if (editable == null
                || actionContext == null
                || !editable.isEditable()
                || !editable.isEnabled()) {
            return false;
        }
        CharSequence packageName = editable.getPackageName();
        return packageName != null
                && GAME_PACKAGE.contentEquals(packageName)
                && (actionContext.windowId() < 0
                        || editable.getWindowId() == actionContext.windowId());
    }

    private boolean focusGameEditableText(AccessibilityNodeInfo editable) {
        return performGameEditableNodeAction(
                editable, AccessibilityNodeInfo.ACTION_FOCUS, null);
    }

    private boolean clearFocusedGameEditableText() {
        AccessibilityNodeInfo editable = findFocusedGameEditableText();
        return editable == null || performGameEditableNodeAction(
                editable, AccessibilityNodeInfo.ACTION_CLEAR_FOCUS, null);
    }

    private boolean setEditableText(AccessibilityNodeInfo editable, String value) {
        if (editable == null) {
            return false;
        }
        String current = editable.getText() == null ? "" : editable.getText().toString();
        if (PetalMatcher.normalize(current).equals(PetalMatcher.normalize(value))) {
            return true;
        }
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        boolean accepted = performGameEditableNodeAction(
                editable, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        if (!accepted) {
            if (!isActionAdmitted(currentActionContext())) {
                return false;
            }
            focusGameEditableText(editable);
            if (!isActionAdmitted(currentActionContext())) {
                return false;
            }
            accepted = performGameEditableNodeAction(
                    editable, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
        return accepted;
    }

    private boolean gameEditableTextMatches(String value) {
        return editableTextMatches(findGameNode(node ->
                node.isEditable() && node.isEnabled()), value);
    }

    private boolean editableTextMatches(AccessibilityNodeInfo editable, String value) {
        return TextNormalizer.matchesEditor(editable != null,
                editable == null ? null : editable.getText(), value);
    }

    /** 花盆已由名稱與數量確認並完成點擊；畫面穩定後直接點擊下一步。 */
    private void tapNextAfterPostcardPetal(List<PetalMatcher.Token> tokens) {
        PostcardMatcher.Target next = PostcardMatcher.findNext(tokens);
        if (next == null) {
            postcardMissingControlFrames++;
            if (postcardMissingControlFrames >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_postcard_control_missing));
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_next));
                schedule(700);
            }
            return;
        }
        postcardMissingControlFrames = 0;
        resetPostcardPotConfirmation();
        tapPostcardTarget(
                next,
                PostcardAutomation.Step.OPEN_SORT,
                getString(R.string.status_postcard_selection_confirmed),
                700);
    }

    /**
     * 第二次只裁切可滾動清單並放大兩倍，以中文模型重讀完整單列名稱與數量。
     * 原始畫面的相對座標會在回呼中還原，點擊仍使用實機尺寸。
     */
    private void scanFocusedPetalRegion(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        ActionAdmission.FrameContext sourceContext = currentActionContext();
        long generation = sourceContext.runGeneration();
        long admissionEpoch = sourceContext.admissionEpoch();
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_focused_petal_ocr),
                postcardAutomation.petalPotName());
        startOcrTransaction(
                bitmap,
                OcrScan.Profile.PETAL_LIST,
                captureGeometry,
                admissionEpoch,
                "postcard-focused",
                false,
                (ocrTransaction, frame) -> {
                runWithActionContext(ocrTransaction, frame, () -> {
                if (!isActiveRun(generation)) {
                    return;
                }
                recordOcrDiagnostic("postcard-focused", frame);
                if (postcardAutomation.step() != PostcardAutomation.Step.SELECT_PETAL) {
                    schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                    return;
                }
                PostcardMatcher.PetalPot focusedPot =
                        PostcardMatcher.findSingleVisiblePetalPot(
                                frame.tokens(),
                                postcardAutomation.petalPotName(),
                                80,
                                width,
                                height);
                if (focusedPot == null) {
                    handleFocusedPetalMiss();
                } else {
                    confirmPostcardPetalPot(focusedPot, width, height);
                }
                });
                },
                error -> {
                if (isActiveRun(generation)) {
                    recordOcrDiagnosticFailure(
                            "postcard-focused", OcrScan.Profile.PETAL_LIST, captureGeometry, error);
                    handleFocusedPetalMiss();
                }
                });
    }

    private void handleFocusedPetalMiss() {
        postcardPetalSearchMissingFrames++;
        postcardPotStability.miss();
        if (postcardPetalSearchMissingFrames >= 6) {
            stopWithError(getString(
                    R.string.status_postcard_search_result_missing,
                    PetalCatalog.searchQuery(postcardAutomation.petalPotName())));
            return;
        }
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_waiting_search_result),
                PetalCatalog.searchQuery(postcardAutomation.petalPotName()));
        schedule(700);
    }

    private void confirmPostcardPetalPot(
            PostcardMatcher.PetalPot pot,
            int width,
            int height) {
        String canonicalName = PostcardPotCatalog.canonicalName(pot.name());
        ObservationStability.Result stability = postcardPotStability.observe(
                (canonicalName == null ? pot.name() : canonicalName) + ":" + pot.count(),
                pot.x(),
                pot.y(),
                width,
                height);
        postcardPetalSearchMissingFrames = 0;
        if (stability != ObservationStability.Result.STABLE) {
            setPostcardStatus(
                    OverlayRunStatus.Kind.RECOGNIZING,
                    getString(
                            R.string.status_postcard_confirming_petal,
                            pot.name(),
                            pot.count(),
                            postcardPotStability.confirmations(),
                            2),
                    getString(R.string.overlay_ocr_detail));
            schedule(700);
            return;
        }
        postcardReturnGuard.reset();
        tapPostcardTarget(
                postcardPetalTapTarget(pot, width, height),
                PostcardAutomation.Step.TAP_NEXT,
                getString(R.string.status_postcard_selecting_petal, pot.name(), pot.count()),
                650);
    }

    /**
     * 搜尋結果卡的可選取控制位於瓶身上方；只會點擊已由完整單列 OCR
     * 與數量連續確認的同一張卡片。
     */
    private PostcardMatcher.Target postcardPetalTapTarget(
            PostcardMatcher.PetalPot pot, int width, int height) {
        int y = pot.y() - Math.round(height * 0.075f);
        y = Math.max(Math.round(height * 0.50f), Math.min(y, Math.round(height * 0.88f)));
        return new PostcardMatcher.Target(
                pot.name() + "-tap",
                Math.max(0, Math.min(pot.x(), width - 1)),
                y);
    }

    private void resetPostcardPotConfirmation() {
        postcardPotStability.reset();
    }

    private void resetPostcardPetalSearch() {
        postcardPetalSearchMissingFrames = 0;
        postcardPetalInputAttempts = 0;
        postcardSearchKeyboardGuard.reset();
    }

    private void handlePikminSelection(
            List<PetalMatcher.Token> tokens, int width, int height) {
        // 排序選單可能仍顯示底下的 GO；必須先處理「喜愛」，不可誤按 GO。
        // if (PostcardMatcher.isSortMenuVisible(tokens, height)) {
        //     if (postcardAutomation.favoriteApplied()) {
        //         tapPostcardTarget(
        //                 PostcardMatcher.findSortControl(tokens, height),
        //                 PostcardAutomation.Step.SELECT_PIKMIN,
        //                 getString(R.string.status_postcard_close_sort),
        //                 650);
        //         return;
        //     }
        //     PostcardMatcher.Target favorite = PostcardMatcher.findFavoriteMenuItem(tokens, height);
        //     if (favorite == null) {
        //         stopWithError(getString(R.string.status_postcard_favorite_missing));
        //         return;
        //     }
        //     postcardAutomation.markFavoriteApplied();
        //     tapPostcardTarget(
        //             favorite,
        //             PostcardAutomation.Step.SELECT_PIKMIN,
        //             getString(R.string.status_postcard_sort_favorite),
        //             650);
        //     return;
        // }
        // if (!postcardAutomation.favoriteApplied()) {
        //     tapPostcardTarget(
        //             PostcardMatcher.findSortControl(tokens, height),
        //             PostcardAutomation.Step.CHOOSE_FAVORITE,
        //             getString(R.string.status_postcard_open_sort),
        //             500);
        //     return;
        // }

        int selectedCount = PostcardMatcher.selectedPikminCount(tokens);
        int desiredCount = postcardAutomation.pikminCount();
        if (selectedCount > desiredCount) {
            postcardLastPikminCount = selectedCount;
            postcardPikminCountConfirmations = 0;
            List<PostcardMatcher.Target> selectedCandidates =
                    PostcardMatcher.findTopRowPikminSlots(width, height);
            PostcardMatcher.Target extraPikmin = selectedCount <= selectedCandidates.size()
                    ? selectedCandidates.get(selectedCount - 1)
                    : null;
            tapPostcardTarget(
                    extraPikmin,
                    PostcardAutomation.Step.SELECT_PIKMIN,
                    getString(
                            R.string.status_postcard_deselect_pikmin,
                            selectedCount - 1,
                            desiredCount),
                    600);
            return;
        }
        if (selectedCount == desiredCount) {
            if (postcardLastPikminCount == selectedCount) {
                postcardPikminCountConfirmations++;
            } else {
                postcardLastPikminCount = selectedCount;
                postcardPikminCountConfirmations = 1;
            }
            if (postcardPikminCountConfirmations < 2) {
                setPostcardStatus(getString(
                        R.string.status_postcard_confirming_pikmin_count,
                        selectedCount,
                        desiredCount,
                        postcardPikminCountConfirmations,
                        2));
                schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                return;
            }
            tapPostcardTarget(
                    PostcardMatcher.findGo(tokens),
                    PostcardAutomation.Step.GO,
                    getString(R.string.status_postcard_go),
                    650);
            return;
        }

        postcardLastPikminCount = selectedCount;
        postcardPikminCountConfirmations = 0;
        List<PostcardMatcher.Target> candidates =
                PostcardMatcher.findTopRowPikminSlots(width, height);
        PostcardMatcher.Target nextPikmin = selectedCount < candidates.size()
                ? candidates.get(selectedCount)
                : null;
        tapPostcardTarget(
                nextPikmin,
                PostcardAutomation.Step.SELECT_PIKMIN,
                getString(
                        R.string.status_postcard_select_pikmin,
                        selectedCount + 1,
                        desiredCount),
                600);
    }

    private void receivePostcard(List<PetalMatcher.Token> tokens) {
        if (postcardAutomation.receiveTapped()) {
            postcardReceiptWaitFrames++;
            if (postcardReceiptWaitFrames >= 3) {
                postcardReceiptWaitFrames = 0;
                if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_postcard_receive_stuck));
                    return;
                }
                postcardAutomation.retryReceive();
                receivePostcard(tokens);
                return;
            }
            setPostcardStatus(getString(R.string.status_postcard_waiting_receipt_exit));
            schedule(700);
            return;
        }
        PostcardMatcher.Target receive = PostcardMatcher.findReceive(tokens);
        if (receive == null) {
            schedule(700);
            return;
        }
        busy = true;
        setPostcardStatus(getString(R.string.status_postcard_receiving));
        dispatchTap(
                receive.x(),
                receive.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> {
                    busy = false;
                    postcardReceiptWaitFrames = 0;
                    if (usageSession != null) {
                        usageSession.recordPostcard();
                    }
                    postcardAutomation.markReceiveTapped();
                    schedule(POSTCARD_RECEIPT_EXIT_DELAY_MILLIS);
                },
                this::postcardActionFailed);
    }

    private void tapPostcardTarget(
            PostcardMatcher.Target target,
            PostcardAutomation.Step nextStep,
            String message,
            long verifyDelay) {
        if (target == null) {
            postcardMissingControlFrames++;
            if (postcardMissingControlFrames >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_postcard_control_missing));
            } else {
                setPostcardStatus(message);
                schedule(650);
            }
            return;
        }
        busy = true;
        setPostcardStatus(message);
        dispatchTap(
                target.x(),
                target.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                () -> {
                    busy = false;
                    actionAttempts = 0;
                    postcardUnknownFrames = 0;
                    postcardMissingControlFrames = 0;
                    postcardAutomation.moveTo(nextStep);
                    long minimum = isPetalSearchStep(nextStep)
                            ? POSTCARD_PETAL_STEP_DELAY_MILLIS
                            : isFastPostcardStep(nextStep)
                                    ? POSTCARD_FAST_SCAN_DELAY_MILLIS
                                    : POSTCARD_VERIFY_DELAY_MILLIS;
                    schedule(Math.max(verifyDelay, minimum));
                },
                this::postcardActionFailed);
    }

    private void postcardActionFailed() {
        busy = false;
        if (postcardAutomation.step() == PostcardAutomation.Step.WAIT_RECEIPT_EXIT) {
            postcardReturnFlowerTapped = false;
        }
        if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_postcard_action_failed));
        } else {
            schedule(700);
        }
    }

    private void returnToMapFromFlowerDetail() {
        postcardBackAttempts++;
        setPostcardStatus(getString(R.string.status_postcard_returning_map));
        boolean accepted = performGameGlobalAction(GLOBAL_ACTION_BACK);
        if (!accepted || postcardBackAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_postcard_return_failed));
            return;
        }
        schedule(900);
    }

    private void finishPostcardAutomation() {
        String complete = getString(
                R.string.status_postcard_complete,
                postcardAutomation.completedCount());
        finishWithSuccess(complete);
        showOverlayNotice(complete);
    }

    private void setPostcardStatus(String message) {
        OverlayRunStatus.Kind kind = switch (postcardAutomation.step()) {
            case FIND_FLOWER, SELECT_PETAL -> OverlayRunStatus.Kind.SEARCHING;
            default -> OverlayRunStatus.Kind.RECOGNIZING;
        };
        setPostcardStatus(kind, message, "");
    }

    private void setPostcardStatus(
            OverlayRunStatus.Kind kind, String message, String detail) {
        Log.i(TAG, "postcard step=" + postcardAutomation.step() + " status=" + message);
        setStatus(message);
        setRunStatus(AutomationMode.POSTCARD, kind, message, detail);
    }

    private void dispatchPlantingEntryTap(
            PlantingScreenAnalyzer.Point entry,
            String phase,
            Runnable completed,
            Runnable failed) {
        dispatchTap(
                entry.x(),
                entry.y(),
                GAME_ACTION_TAP_DURATION_MILLIS,
                phase,
                completed,
                failed);
    }

    private void handleStaleAction(Runnable failed) {
        if (!running) {
            return;
        }
        dropStaleAction();
        if (running && failed != null) {
            failed.run();
        }
    }

    /** Applies the same foreground/frame admission to global actions driven by OCR state. */
    private boolean performGameGlobalAction(int action) {
        ActionAdmission.FrameContext actionContext = currentActionContext();
        if (action != GLOBAL_ACTION_BACK) {
            return false;
        }
        ActionGateway.Result result = actionGateway().gameBack(actionContext);
        if (!result.decision().allowed()) {
            dropStaleAction();
            return false;
        }
        return result.dispatched();
    }

    /** The final admission check is immediately before every accessibility gesture dispatch. */
    private GestureDispatchResult dispatchGestureSafely(
            GestureDescription gesture,
            ActionAdmission.FrameContext actionContext,
            GestureResultCallback callback,
            Runnable failed) {
        return dispatchGestureSafely(
                ActionGateway.Kind.PATH, gesture, actionContext, callback, failed);
    }

    private GestureDispatchResult dispatchGestureSafely(
            ActionGateway.Kind kind,
            GestureDescription gesture,
            ActionAdmission.FrameContext actionContext,
            GestureResultCallback callback,
            Runnable failed) {
        if (actionContext == null) {
            handleStaleAction(failed);
            return GestureDispatchResult.STALE;
        }
        GestureResultCallback guardedCallback = new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (!isActiveRun(actionContext.runGeneration())) {
                    return;
                }
                callback.onCompleted(gestureDescription);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (!isActiveRun(actionContext.runGeneration())) {
                    return;
                }
                callback.onCancelled(gestureDescription);
            }
        };
        GatewayGesture request = new GatewayGesture(gesture, guardedCallback);
        ActionGateway.Result result = switch (kind) {
            case TAP -> actionGateway().tap(actionContext, request);
            case CONTINUED_GESTURE -> actionGateway().continuedGesture(actionContext, request);
            case PATH, NODE_CLICK, EDITABLE_FOCUS, EDITABLE_CLEAR_FOCUS,
                    EDITABLE_SET_TEXT, GAME_BACK ->
                    actionGateway().path(actionContext, request);
        };
        if (!result.decision().allowed()) {
            handleStaleAction(failed);
            return GestureDispatchResult.STALE;
        }
        boolean sent = result.dispatched();
        if (sent) {
            workflowDiagnostics.recordCount(WorkflowDiagnostics.Counter.ACTIONS_DISPATCHED);
            workflowDiagnostics.recordSinceCapture(
                    WorkflowDiagnostics.Metric.CAPTURE_TO_GESTURE,
                    actionContext,
                    android.os.SystemClock.uptimeMillis());
        }
        ocrRuntime.diagnostics().markGesture(
                actionContext.runGeneration(),
                actionContext.captureSequence(),
                actionContext.ocrRequestSequence(),
                sent);
        return sent ? GestureDispatchResult.SENT : GestureDispatchResult.SYSTEM_REJECTED;
    }

    /** 發送單次手指點擊，並透過 generation 防止舊回呼污染新流程。 */
    private void dispatchTap(
            int x,
            int y,
            long durationMillis,
            Runnable completed,
            Runnable failed) {
        dispatchTap(x, y, durationMillis, null, completed, failed);
    }

    private void dispatchTap(
            int x,
            int y,
            long durationMillis,
            String plantingEntryPhase,
            Runnable completed,
            Runnable failed) {
        ActionAdmission.FrameContext actionContext = currentActionContext();
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        long generation = actionContext.runGeneration();
        long gesturePreparationStartedAtUptimeMillis =
                android.os.SystemClock.uptimeMillis();
        ExpeditionScreenAnalyzer.Point screenPoint = screenPointFromBitmap(
                new ExpeditionScreenAnalyzer.Point(x, y), captureGeometry);
        Path path = new Path();
        path.moveTo(screenPoint.x(), screenPoint.y());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        workflowDiagnostics.recordDuration(
                WorkflowDiagnostics.Metric.GESTURE_PREPARATION,
                android.os.SystemClock.uptimeMillis() - gesturePreparationStartedAtUptimeMillis);
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                ActionGateway.Kind.TAP,
                gesture,
                actionContext,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            if (plantingEntryPhase != null) {
                                showPlantingEntryGestureStatus(
                                        plantingEntryPhase,
                                        R.string.status_planting_entry_tap_completed);
                            }
                            completed.run();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            setStatus(getString(R.string.status_tap_cancelled));
                            failed.run();
                        }
                    }
                },
                failed);
        if (plantingEntryPhase != null
                && dispatchResult == GestureDispatchResult.SENT) {
            showPlantingEntryGestureStatus(
                    plantingEntryPhase,
                    R.string.status_planting_entry_tap_sent);
        }
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED
                && isActiveRun(generation)) {
            setStatus(getString(R.string.status_tap_rejected));
            failed.run();
        }
    }

    /** Sends a point already expressed in physical screen coordinates. */
    private void dispatchScreenTap(
            int screenX,
            int screenY,
            long durationMillis,
            Runnable completed,
            Runnable failed) {
        dispatchScreenTap(
                screenX,
                screenY,
                durationMillis,
                currentActionContext(),
                completed,
                failed);
    }

    private void dispatchScreenTap(
            int screenX,
            int screenY,
            long durationMillis,
            ActionAdmission.FrameContext actionContext,
            Runnable completed,
            Runnable failed) {
        long generation = actionContext.runGeneration();
        Path path = new Path();
        path.moveTo(screenX, screenY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                ActionGateway.Kind.TAP,
                gesture,
                actionContext,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            completed.run();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            setStatus(getString(R.string.status_tap_cancelled));
                            failed.run();
                        }
                    }
                },
                failed);
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED
                && isActiveRun(generation)) {
            setStatus(getString(R.string.status_tap_rejected));
            failed.run();
        }
    }

    private void showPlantingEntryGestureStatus(String phase, int messageResource) {
        String message = getString(messageResource);
        if (phase.startsWith("initial")) {
            setPlantingNoticeText(message, false);
        } else {
            setStatus(message);
        }
    }

    /** 發送一條已在正確選皮頁確認過的單指拖曳路徑。 */
    private void dispatchPath(Path path, long durationMillis, Runnable completed, Runnable failed) {
        dispatchPath(path, durationMillis, currentActionContext(), completed, failed);
    }

    private void dispatchPath(
            Path path,
            long durationMillis,
            ActionAdmission.FrameContext actionContext,
            Runnable completed,
            Runnable failed) {
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        dispatchGesturePlan(gesture, actionContext, completed, failed);
    }

    private GestureDispatchResult dispatchGesturePlan(GestureDescription gesture,
            ActionAdmission.FrameContext actionContext, Runnable completed, Runnable failed) {
        long generation = actionContext.runGeneration();
        GestureDispatchResult dispatchResult = dispatchGestureSafely(
                gesture,
                actionContext,
                new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    completed.run();
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    failed.run();
                }
            }
        }, failed);
        if (dispatchResult == GestureDispatchResult.SYSTEM_REJECTED
                && isActiveRun(generation)) {
            failed.run();
        }
        return dispatchResult;
    }

    /** 以無障礙根節點與最近事件判斷 Pikmin Bloom 是否在前景。 */
    private boolean isGameForeground() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && GAME_PACKAGE.contentEquals(root.getPackageName())) {
            return true;
        }
        return GAME_PACKAGE.equals(recentPackage)
                && android.os.SystemClock.elapsedRealtime() - recentPackageAt < 10_000;
    }

    /** 收取模式每次手勢都要求目前根視窗確實屬於遊戲，不採用十秒事件容錯。 */
    private Rect activeGameBoundsStrict() {
        CurrentGameWindow window = currentGameWindow();
        if (window == null) {
            return null;
        }
        CaptureGeometry.Bounds bounds = window.bounds();
        return new Rect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    /** 判斷非同步回呼是否仍屬於目前這一輪自動化。 */
    private boolean isActiveRun(long generation) {
        return running && generation == runGeneration
                && (automationMode != AutomationMode.DISPATCH
                        || settings.expeditionSettingsRevision() == dispatchSettingsRevision);
    }

    private void startOcrTransaction(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry geometry,
            long admissionEpoch,
            String diagnosticSource,
            boolean recycleSourceAtTerminal,
            OcrFrameConsumer success,
            OcrFailureConsumer failure) {
        startOcrTransaction(bitmap, profile, geometry, admissionEpoch, diagnosticSource,
                recycleSourceAtTerminal, success, failure, null);
    }

    private void startOcrTransaction(Bitmap bitmap, OcrScan.Profile profile, CaptureGeometry geometry,
            long admissionEpoch, String diagnosticSource, boolean recycleSourceAtTerminal,
            OcrFrameConsumer success, OcrFailureConsumer failure,
            ScreenCoordinateTransform.ScreenshotRect region) {
        long generation = runGeneration;
        OcrRuntime.Transaction started = ocrRuntime.request(new OcrRuntime.Request(
                bitmap,
                profile,
                geometry,
                generation,
                admissionEpoch,
                recycleSourceAtTerminal,
                () -> isActiveRun(generation),
                new OcrRuntime.Callback() {
                    @Override
                    public void onSuccess(
                            OcrRuntime.Transaction transaction, OcrScan.Frame frame) {
                        handleOcrSuccess(transaction, frame, success);
                    }

                    @Override
                    public void onFailure(
                            OcrRuntime.Transaction transaction, OcrRuntime.Failure runtimeFailure) {
                        handleOcrFailure(
                                transaction,
                                runtimeFailure,
                                diagnosticSource,
                                profile,
                                geometry,
                                generation,
                                failure);
                    }

                    @Override
                    public void onTerminal(
                            OcrRuntime.Transaction transaction,
                            OcrScanner.TerminalState state) {
                        busy = false;
                    }
                }).withRegion(region));
        if (started != null) {
            workflowDiagnostics.recordCount(WorkflowDiagnostics.Counter.OCR_TRANSACTIONS);
        }
        busy = ocrRuntime.hasActiveTransaction();
    }

    /** Workflow/admission compatibility adapter for immutable OCR evidence. */
    private void handleOcrSuccess(
            OcrRuntime.Transaction completed, OcrScan.Frame frame, OcrFrameConsumer success) {
        long callbackStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            if (completed == null || !isActiveRun(completed.id().runGeneration())) {
                return;
            }
            consecutiveOcrEngineFailures = 0;
            latestActionableOcrRequestSequence = completed.id().ocrRequestSequence();
            if (frame == null) {
                completed.setPostProcessingOutcome("NULL_FRAME");
                dropStaleAction();
                return;
            }
            ActionAdmission.FrameContext actionContext = actionContextForFrame(frame);
            long ocrCompletedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
            boolean frameSafe = frame.canDriveAction(completed.id())
                    && frame.admissionEpoch() == completed.admissionEpoch();
            ActionAdmission.Decision admission = frameSafe
                    ? evaluateActionAdmission(actionContext)
                    : new ActionAdmission.Decision(
                            false, ActionAdmission.RejectionReason.FRAME_NOT_ACTION_SAFE);
            recordOcrAdmissionDiagnostic(
                    frame,
                    actionContext,
                    ocrCompletedAtUptimeMillis,
                    android.os.SystemClock.uptimeMillis(),
                    admission);
            if (!frameSafe || !admission.allowed()) {
                completed.setPostProcessingOutcome("ADMISSION_REJECTED");
                // Do not route a stale frame through a workflow failure callback: some
                // failure paths intentionally retry with a gesture based on the old state.
                dropStaleAction();
                return;
            }
            success.accept(completed, frame);
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.OCR_CALLBACK,
                    android.os.SystemClock.uptimeMillis() - callbackStartedAtUptimeMillis);
        }
    }

    private void handleOcrFailure(
            OcrRuntime.Transaction transaction,
            OcrRuntime.Failure runtimeFailure,
            String diagnosticSource,
            OcrScan.Profile profile,
            CaptureGeometry geometry,
            long generation,
            OcrFailureConsumer failure) {
        long callbackStartedAtUptimeMillis = android.os.SystemClock.uptimeMillis();
        try {
            if (!isActiveRun(generation)) {
                return;
            }
            if (transaction != null
                    && runtimeFailure.engineFailure()
                    && ++consecutiveOcrEngineFailures >= MAX_ACTION_ATTEMPTS) {
                recordOcrDiagnosticFailure(
                        diagnosticSource, profile, geometry, runtimeFailure.error());
                stopWithError(getString(R.string.status_ocr_failed));
                return;
            }
            failure.accept(runtimeFailure.error());
        } finally {
            workflowDiagnostics.recordDuration(
                    WorkflowDiagnostics.Metric.OCR_CALLBACK,
                    android.os.SystemClock.uptimeMillis() - callbackStartedAtUptimeMillis);
        }
    }

    private void cancelActiveOcrTransaction() {
        if (ocrRuntime != null) {
            ocrRuntime.cancelActive();
        }
    }

    private boolean isTransientScreenshotFailure(int errorCode) {
        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR
                || errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                // Window/display IDs may change during an activity or fold transition.
                || errorCode == ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY) {
            return true;
        }
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && errorCode == ERROR_TAKE_SCREENSHOT_INVALID_WINDOW;
    }

    /** Handles a terminal capture failure after CaptureCoordinator has exhausted retries. */
    private void screenshotFailed(String message, long generation) {
        if (!isActiveRun(generation)) {
            return;
        }
        busy = false;
        setStatus(message);
        stopWithError(message);
    }

    /** 將截圖或 OCR 失敗轉成狀態文字並安排下一次重試。 */
    private void scanFailed(String message, long generation) {
        if (!isActiveRun(generation)) {
            return;
        }
        busy = false;
        setStatus(message);
        if (automationMode == AutomationMode.FEED
                && feedStep == FeedStep.FEEDING
                && feedHoldStroke != null
                && feedHoldLifecycle.canContinue()
                && android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt
                        >= FEED_HOLD_MAX_MILLIS) {
            releaseFeedHoldGesture("ocr-timeout");
            return;
        }
        scheduleNext();
    }

    /** 依使用者設定安排下一次掃描。 */
    private void scheduleNext() {
        schedule(automationMode == AutomationMode.FEED
                ? FEED_OCR_RETRY_MILLIS
                : SCAN_INTERVAL_MILLIS);
    }

    private void postDelayedAutomationStart(AutomationMode mode, Runnable startTask) {
        if (!admitWorkflowStart(mode)) {
            return;
        }
        pendingAutomationStartMode = mode;
        long scheduledGeneration = runGeneration;
        long scheduledSettingsRevision = settings.expeditionSettingsRevision();
        if (mode == AutomationMode.DISPATCH) pendingDispatchSettingsRevision = scheduledSettingsRevision;
        Runnable guardedTask = new Runnable() {
            @Override
            public void run() {
                pendingAutomationStartTasks.remove(this);
                if (pendingAutomationStartTasks.isEmpty()) {
                    pendingAutomationStartMode = AutomationMode.NONE;
                }
                if (!AutomationStartGuard.isCurrent(scheduledGeneration, runGeneration)
                        || mode == AutomationMode.DISPATCH
                                && scheduledSettingsRevision != settings.expeditionSettingsRevision()) {
                    return;
                }
                startTask.run();
            }
        };
        pendingAutomationStartTasks.add(guardedTask);
        handler.postDelayed(guardedTask, AUTOMATION_START_DELAY_MILLIS);
    }

    private void cancelDelayedAutomationStarts() {
        for (Runnable task : new ArrayList<>(pendingAutomationStartTasks)) {
            handler.removeCallbacks(task);
        }
        pendingAutomationStartTasks.clear();
        pendingAutomationStartMode = AutomationMode.NONE;
    }

    /** 清掉舊排程後建立新的最小延遲排程。 */
    private void schedule(long delayMillis) {
        handler.removeCallbacks(scanTask);
        if (running) {
            long minimum = switch (automationMode) {
                case FEED -> 200L;
                case DISPATCH -> dispatchMinimumScanDelay();
                case RETURN_REWARD -> RETURN_REWARD_SCAN_DELAY_MILLIS;
                case POSTCARD -> isPetalSearchStep(postcardAutomation.step())
                        ? POSTCARD_PETAL_STEP_DELAY_MILLIS
                        : isFastPostcardStep(postcardAutomation.step())
                                ? POSTCARD_FAST_SCAN_DELAY_MILLIS
                                : POSTCARD_MIN_SCAN_DELAY_MILLIS;
                default -> 200L;
            };
            handler.postDelayed(scanTask, Math.max(minimum, delayMillis));
        }
    }

    private long dispatchMinimumScanDelay() {
        if (expeditionDispatchSession == null) {
            return DISPATCH_SCAN_DELAY_MILLIS;
        }
        return switch (expeditionDispatchSession.stage()) {
            case LIST_SEARCH -> DISPATCH_AFTER_SCROLL_DELAY_MILLIS;
            case SELECTION -> dispatchAutoTapAttempts > 0 && !dispatchPikminSelected
                    ? DISPATCH_AUTO_VERIFY_DELAY_MILLIS
                    : dispatchSelectionMethod == DispatchSelectionMethod.DRAG_12
                            && dispatchColorSelected && !dispatchPikminSelected
                            ? DISPATCH_PIKMIN_TAP_DELAY_MILLIS
                            : DISPATCH_SCAN_DELAY_MILLIS;
            default -> DISPATCH_SCAN_DELAY_MILLIS;
        };
    }

    private static boolean isFastPostcardStep(PostcardAutomation.Step step) {
        return switch (step) {
            case OPEN_PETAL_SEARCH,
                    ENTER_PETAL_SEARCH,
                    CLOSE_PETAL_KEYBOARD,
                    SELECT_PETAL,
                    TAP_NEXT,
                    GO,
                    RECEIVE -> true;
            default -> false;
        };
    }

    /** 建立 50x50dp 懸浮 ICON；點擊行為仍沿用原本流程。 */
    private boolean showOverlay() {
        if (overlayHost == null) {
            overlayHost = createOverlayHost();
        }
        return overlayHost.attach();
    }

    private void finishUsageSession(String outcome) {
        if (usageSession == null) {
            return;
        }
        UsageTelemetryClient.Session completed = usageSession;
        usageSession = null;
        completed.finish(outcome);
    }

    /** 停止流程後保留錯誤卡，讓原因不會在短暫提示後消失。 */
    private void stopWithError(String message) {
        if (automationMode == AutomationMode.DISPATCH && expeditionDispatchSession != null) {
            expeditionDispatchSession.abort();
            Log.i(TAG, "EXPEDITION_OUTCOME outcome=ABORTED stage=" + expeditionDispatchSession.stage());
            finishUsageSession("ABORTED");
        }
        if (automationMode == AutomationMode.PLANTING) {
            logPlantingSwitch("stopped", false);
            Log.i(TAG, "PLANTING_SWITCH reason=" + message);
        }
        AutomationMode stoppedMode = automationMode;
        finishUsageSession("stopped");
        pause(message);
        setRunStatus(
                stoppedMode,
                OverlayRunStatus.Kind.ERROR,
                message,
                getString(R.string.overlay_error_detail));
    }

    /** 完成流程後短暫保留成功卡，然後回到可開啟設定的圖示。 */
    private void finishWithSuccess(String message) {
        AutomationMode completedMode = automationMode;
        if (completedMode == AutomationMode.RETURN_REWARD && usageSession != null) {
            usageSession.recordReturnRewardSession();
        }
        finishUsageSession("completed");
        pause(message);
        setRunStatus(completedMode, OverlayRunStatus.Kind.SUCCESS, message, "");
        OverlayRunStatus completedStatus = overlayHost == null ? null : overlayHost.runStatus();
        handler.postDelayed(() -> {
            if (!running
                    && overlayHost != null
                    && overlayHost.runStatus() == completedStatus) {
                clearPlantingNotice();
            }
        }, 2600L);
    }

    /** 停止所有掃描排程並將流程狀態重設為可重新開始。 */
    private void pause(String message) {
        // Expedition owns no continued hold. Invalidate it before any cleanup callback.
        boolean expeditionInvalidated = automationMode == AutomationMode.DISPATCH
                || pendingAutomationStartMode == AutomationMode.DISPATCH;
        if (expeditionInvalidated) {
            runGeneration++;
            running = false;
            if (actionGateway != null) actionGateway.stop();
        }
        abortFeedHoldForLifecycle();
        if (actionGateway != null) {
            actionGateway.stop();
        }
        cancelDelayedAutomationStarts();
        cancelH10aAnalysis();
        finishUsageSession("paused");
        cancelActiveOcrTransaction();
        if (nectarTemplateMatcher != null) {
            nectarTemplateMatcher.clearCache();
        }
        consecutiveOcrEngineFailures = 0;
        if (!expeditionInvalidated) runGeneration++;
        latestActionableOcrRequestSequence = 0L;
        clearScreenshotPipeline();
        pendingCaptureOnlyAction = null;
        if (admissionDiagnostics.snapshot().admissionCheckCount() > 0L) {
            Log.i(TAG, "ACTION_ADMISSION_SUMMARY " + admissionDiagnostics.summary());
        }
        WorkflowDiagnostics.Snapshot workflowSnapshot = workflowDiagnostics.snapshot();
        if (workflowSnapshot.count(WorkflowDiagnostics.Counter.SCREENSHOT_REQUESTS) > 0L
                || workflowSnapshot.count(WorkflowDiagnostics.Counter.OCR_TRANSACTIONS) > 0L) {
            Log.i(TAG, "WORKFLOW_DIAGNOSTICS_SUMMARY " + workflowDiagnostics.summary());
        }
        OcrRuntimeDiagnostics.Snapshot ocrRuntimeSnapshot = ocrRuntime.diagnostics().snapshot(
                android.os.SystemClock.uptimeMillis());
        if (ocrRuntimeSnapshot.count(OcrRuntimeDiagnostics.Counter.SCREENSHOTS) > 0L
                || ocrRuntimeSnapshot.count(OcrRuntimeDiagnostics.Counter.OCR_TRANSACTIONS) > 0L) {
            Log.i(TAG, "OCR_DIAGNOSTICS_SUMMARY "
                    + ocrRuntime.diagnostics().summary(android.os.SystemClock.uptimeMillis()));
        }
        running = false;
        automationMode = AutomationMode.NONE;
        expeditionDispatchSession = null;
        if (dispatchVisionCancellation != null) dispatchVisionCancellation.set(true);
        dispatchVisionCancellation = null;
        dispatchInspectedVisual.clear();
        if (expeditionTemplateMatcher != null) expeditionTemplateMatcher.clearObservationCache();
        expeditionRecognitionConsensus.reset();
        dispatchCurrentItemKind = null;
        dispatchColorSelected = false;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchResultMissingFrames = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchSelectionTargetCount = 0;
        dispatchSelectionBeforeCount = -1;
        dispatchSelectionGesturePending = false;
        dispatchReturnRevealPending = false;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoAnchorMissingFrames = 0;
        dispatchUnknownFrames = 0;
        clearReturnRewardRoiOverlays();
        returnRewardRoi.reset();
        returnRewardScanGuard.reset();
        returnRewardStartedAt = 0L;
        returnRewardLastTapAt = 0L;
        returnRewardContinueOnNectarWarning = false;
        returnRewardNectarWarningFrames = 0;
        returnRewardNectarWarningActive = false;
        resetReturnRewardPostcard();
        switchGuard.reset();
        resetPlantingSearch();
        resetPlantingNavigation();
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        postcardUnknownFrames = 0;
        postcardMissingControlFrames = 0;
        postcardReceiptWaitFrames = 0;
        postcardBackAttempts = 0;
        postcardReturnFlowerTapped = false;
        feedSettings = new FeedSettingsInput(6, 0, 40, 1200);
        feedStep = FeedStep.WAITING_GAME_READY;
        feedFlowerSequence = List.of();
        feedFlowerSequenceIndex = 0;
        feedTargetFlower = "";
        feedRequireNectarSearch = false;
        feedRound = 0;
        feedAttemptCount = 0;
        feedSquadSwitchCount = 0;
        feedTargetMissingFrames = 0;
        feedReadyMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchMissingFrames = 0;
        feedReadyStability.reset();
        feedSearchActionAttempts = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.NONE;
        feedSearchKeyboardGuard.reset();
        feedNectarCandidate = null;
        feedSelectedNectar = null;
        feedNectarFreshFrameRequired = false;
        feedNectarCandidateFrames = 0;
        feedNectarTapAttempts = 0;
        feedPanelCloseWaitFrames = 0;
        feedPanelClosedConfirmationFrames = 0;
        feedDetailCloseAttempts = 0;
        feedNectarBeforeRound = -1;
        feedCollectAfterCount = false;
        feedNoEffectStartedAt = 0L;
        feedZoomReady = false;
        feedCollectedPetals = 0;
        feedCollectReturningFromDetail = false;
        feedCollectReturningFromShare = false;
        feedCollectReturnFrames = 0;
        feedCollectionOnlyRecovery = false;
        resetFeedSpiralState();
        resetFeedHoldState();
        automationStep = AutomationStep.CHECKING_PLANTING_ENTRY;
        targetFlower = "";
        actionAttempts = 0;
        startAfterSelection = false;
        selectionFromSearch = false;
        targetSelectionX = 0;
        targetSelectionY = 0;
        handler.removeCallbacks(scanTask);
        clearPlantingNotice();
        renderWorkflowStopped();
        setStatus(message);
    }

    /** 將狀態同步到設定卡片；卡片關閉時不建立額外視窗。 */
    private void setStatus(String message) {
        if (overlayHost != null) {
            overlayHost.setPanelStatus(message);
        }
    }

    /** 建立懸浮窗按鈕，保留至少 32dp 觸控區域。 */
    /** 顯示經 OCR 確認的目前花名與花瓣餘量。 */
    private void showPlantingStatus(String flower, int remaining) {
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.SUCCESS,
                getString(R.string.status_current_remaining, flower, remaining),
                "");
    }

    /** 相容舊呼叫點；正常長句視為辨識中，警告則保留到使用者處理。 */
    private void setPlantingNoticeText(String message, boolean warning) {
        setRunStatus(
                automationMode,
                warning ? OverlayRunStatus.Kind.ERROR : OverlayRunStatus.Kind.RECOGNIZING,
                message,
                warning ? getString(R.string.overlay_error_detail) : "");
    }

    /** 將既有流程狀態投影到收合列，不新增或改寫任何自動化判斷。 */
    private void setRunStatus(
            AutomationMode mode,
            OverlayRunStatus.Kind kind,
            String message,
            String detail) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isEmpty()) {
            clearPlantingNotice();
            return;
        }
        int stageResource = switch (mode) {
            case FEED -> R.string.overlay_stage_feed;
            case POSTCARD -> R.string.overlay_stage_postcard;
            case DISPATCH -> R.string.overlay_stage_reward;
            case RETURN_REWARD -> R.string.overlay_stage_return_reward;
            default -> R.string.overlay_stage_planting;
        };
        String stage = getString(stageResource)
                + " · " + runStageLabel(kind);
        OverlayRunStatus next = new OverlayRunStatus(kind, stage, normalizedMessage, detail);
        if (overlayHost != null) {
            overlayHost.setRunStatus(next);
        }
    }

    private String runStageLabel(OverlayRunStatus.Kind kind) {
        return switch (kind) {
            case IDLE -> getString(R.string.overlay_stage_waiting);
            case SEARCHING -> getString(R.string.overlay_stage_searching);
            case RECOGNIZING -> getString(R.string.overlay_stage_recognizing);
            case SUCCESS -> getString(R.string.overlay_stage_success);
            case ERROR -> getString(R.string.overlay_stage_error);
        };
    }

    private void clearPlantingNotice() {
        if (overlayHost != null) {
            overlayHost.clearRunStatus();
        }
    }

    /** WindowManager 失敗時保留服務程序，讓使用者仍可回到主畫面修復設定。 */
    private boolean safeAddOverlayView(
            View view, WindowManager.LayoutParams params, String windowName) {
        try {
            returnRewardWindowManager.addView(view, params);
            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to add " + windowName + " overlay", exception);
            return false;
        }
    }

    /** 重連與銷毀可能交錯；嘗試移除已登記視窗並吸收競態例外。 */
    private void safeRemoveOverlayView(View view, String windowName) {
        if (view == null || returnRewardWindowManager == null) {
            return;
        }
        try {
            returnRewardWindowManager.removeViewImmediate(view);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove " + windowName + " overlay", exception);
        }
    }

    /** 將 dp 轉成目前螢幕的像素。 */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 回程明信片處理方式；刪除只能由使用者在此明確選取。 */
    private final class ReturnRewardAnchorTouchListener implements View.OnTouchListener {
        private float downX;
        private float downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                moved = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                moved = moved
                        || Math.abs(event.getRawX() - downX) > dp(12)
                        || Math.abs(event.getRawY() - downY) > dp(12);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moved) {
                    if (overlayHost != null
                            && overlayHost.isTouchInsideIcon(event.getRawX(), event.getRawY())) {
                        overlayHost.showSettingsOverlay();
                    } else {
                        armReturnRewardRoi(
                                Math.round(event.getRawX()), Math.round(event.getRawY()));
                    }
                    view.performClick();
                }
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_CANCEL;
        }
    }

    /** Non-touchable screen frame showing the active return-reward detector area. */
    private final class ReturnRewardRoiView extends View {
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);

        ReturnRewardRoiView() {
            super(PetalAccessibilityService.this);
            outline.setColor(Color.rgb(255, 96, 48));
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(3));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = outline.getStrokeWidth() / 2f;
            canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset, outline);
        }
    }

    /** 只在標題列接收拖曳，避免誤觸開始與設定按鈕。 */
}
