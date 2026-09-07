package com.pikminx.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * 以無障礙服務讀取遊戲畫面，依使用者輸入的花朵順序執行點擊。
 *
 * <p>使用者先在遊戲中開啟種花選單；服務會以搜尋欄篩選設定中的目標花盆，
 * 確認搜尋文字、完整目標名稱與右下角數量後點擊，不會滾動花盆清單。</p>
 */
public final class PetalAccessibilityService extends AccessibilityService {
    private record ScreenshotRequest(
            List<ScreenshotOverlayMask.Region> overlayRegions,
            long generation,
            CaptureGeometry.Mode captureMode,
            CaptureGeometry.Bounds expectedSourceBoundsOnScreen,
            CaptureGeometry.Bounds targetWindowBoundsOnScreen,
            int displayId,
            int windowId,
            long sequence) {}

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
    private static final long POSTCARD_FAST_SCAN_DELAY_MILLIS = 450L;
    private static final long DISPATCH_SCAN_DELAY_MILLIS = 850L;
    private static final long DISPATCH_AFTER_TAP_DELAY_MILLIS = 1600L;
    private static final long DISPATCH_AUTO_VERIFY_DELAY_MILLIS = 450L;
    private static final long DISPATCH_PIKMIN_TAP_DELAY_MILLIS = 250L;
    private static final long DISPATCH_AFTER_SCROLL_DELAY_MILLIS = 250L;
    private static final long SCREENSHOT_CALLBACK_TIMEOUT_MILLIS = 6000L;
    private static final long OCR_CALLBACK_TIMEOUT_MILLIS = 6000L;
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
    static final int OVERLAY_SIZE_DP = 50;
    private static final int NOTICE_MAX_WIDTH_DP = 190;
    private static final int NOTICE_GAP_DP = 4;
    private static final int NOTICE_EDGE_DP = 8;
    private static final int OVERLAY_GREEN = Color.rgb(35, 122, 80);
    private static final int OVERLAY_SURFACE = Color.rgb(251, 252, 251);
    private static final int OVERLAY_BORDER = Color.rgb(223, 231, 225);
    private static final int OVERLAY_MUTED = Color.rgb(99, 118, 111);
    private static final int OVERLAY_MINT = Color.rgb(231, 245, 236);
    private static final int OVERLAY_SURFACE_2 = Color.rgb(246, 248, 246);
    private static final int OVERLAY_INFO = Color.rgb(234, 242, 249);
    private static final int OVERLAY_CREAM = Color.rgb(255, 245, 217);
    private static final int OVERLAY_ACCENT = Color.rgb(168, 67, 61);
    private static final int OVERLAY_WARNING = Color.rgb(156, 39, 39);
    private static final int OVERLAY_SEARCH = Color.rgb(62, 113, 137);
    private static final int OVERLAY_RECOGNIZING = Color.rgb(167, 120, 33);
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
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlay;
    private View settingsOverlay;
    private TextView status;
    private Button toggle;
    private View noticeOverlay;
    private WindowManager.LayoutParams noticeParams;
    private final Runnable hideFloatingNoticeTask = this::hideFloatingNotice;
    private OverlayRunStatus plantingNoticeStatus;
    private SettingsStore settings;
    private OcrScanner scanner;
    private NectarTemplateMatcher nectarTemplateMatcher;
    private final OcrScanner.TransactionRegistry ocrTransactions =
            new OcrScanner.TransactionRegistry();
    private ActiveOcrTransaction activeOcrTransaction;
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
    private final SearchKeyboardGuard dispatchSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private int dispatchPikminTapIndex;
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
    private long captureSequence;
    private final ScreenshotRequestQueue<ScreenshotRequest> screenshotRequests =
            new ScreenshotRequestQueue<>();
    private final Runnable screenshotTimeoutTask = this::screenshotTimedOut;
    private final ThreadLocal<CaptureGeometry> handlingCaptureGeometry = new ThreadLocal<>();
    private long runGeneration;
    private String recentPackage = "";
    private long recentPackageAt;
    private FeedSettingsInput feedSettings = new FeedSettingsInput(6, 0, 40, 1200);
    private FeedStep feedStep = FeedStep.WAITING_GAME_READY;
    private String feedTargetFlower = "";
    private int feedRound;
    private int feedAttemptCount;
    private int feedSquadSwitchCount;
    private int feedTargetMissingFrames;
    private int feedReadyMissingFrames;
    private int feedNectarOpenAttempts;
    private int feedSearchMissingFrames;
    private int feedSearchActionAttempts;
    private int feedSearchInputAttempts;
    private int feedSearchOpenConfirmationFrames;
    private int feedSearchTextConfirmationFrames;
    private FeedSearchResetPhase feedSearchResetPhase = FeedSearchResetPhase.NONE;
    private final SearchKeyboardGuard feedSearchKeyboardGuard =
            new SearchKeyboardGuard(MAX_ACTION_ATTEMPTS);
    private FeedScreenAnalyzer.NectarSelection feedNectarCandidate;
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

    private interface OcrFrameConsumer {
        void accept(OcrScan.Frame frame);
    }

    private interface OcrFailureConsumer {
        void accept(Exception error);
    }

    private final class ActiveOcrTransaction {
        final OcrScanner.Transaction transaction;
        final String diagnosticSource;
        final OcrScan.Profile profile;
        final CaptureGeometry geometry;
        final OcrFrameConsumer success;
        final OcrFailureConsumer failure;
        final Runnable sourceCleanup;
        final Runnable watchdog;

        ActiveOcrTransaction(
                OcrScanner.Transaction transaction,
                String diagnosticSource,
                OcrScan.Profile profile,
                CaptureGeometry geometry,
                OcrFrameConsumer success,
                OcrFailureConsumer failure,
                Runnable sourceCleanup) {
            this.transaction = transaction;
            this.diagnosticSource = diagnosticSource;
            this.profile = profile;
            this.geometry = geometry;
            this.success = success;
            this.failure = failure;
            this.sourceCleanup = sourceCleanup;
            watchdog = () -> ocrTimedOut(this);
        }
    }

    /** 服務啟動後初始化 OCR、偏好設定與可拖曳懸浮窗。 */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = new WeakReference<>(this);
        settings = new SettingsStore(this);
        scanner = new OcrScanner();
        nectarTemplateMatcher = new NectarTemplateMatcher(this);
        if (showOverlay()) {
            overlay.setVisibility(settings.overlayVisible() ? View.VISIBLE : View.GONE);
        }
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
        pause(getString(R.string.status_service_interrupted));
    }

    /** 服務銷毀時釋放 OCR、懸浮窗與弱引用。 */
    @Override
    public void onDestroy() {
        pause(getString(R.string.status_service_closed));
        if (connectedService.get() == this) {
            connectedService.clear();
        }
        if (scanner != null) {
            scanner.close();
        }
        safeRemoveOverlayView(settingsOverlay, "settings");
        safeRemoveOverlayView(noticeOverlay, "notice");
        safeRemoveOverlayView(returnRewardAnchorOverlay, "return-reward-anchor");
        safeRemoveOverlayView(returnRewardRoiOverlay, "return-reward-roi");
        safeRemoveOverlayView(overlay, "icon");
        overlay = null;
        settingsOverlay = null;
        noticeOverlay = null;
        status = null;
        toggle = null;
        super.onDestroy();
    }

    /** 提供 Activity 查詢目前懸浮窗是否可見。 */
    static boolean isOverlayVisible() {
        PetalAccessibilityService service = connectedService.get();
        return service != null
                && service.overlay != null
                && service.overlay.getVisibility() == View.VISIBLE;
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
        if (service.overlay == null && !service.showOverlay()) {
            return false;
        }
        service.applyOverlayVisibility(visible);
        return true;
    }

    /** 套用懸浮窗狀態並在隱藏時同步暫停自動化。 */
    private void applyOverlayVisibility(boolean visible) {
        if (!visible) {
            pause(getString(R.string.status_paused));
        }
        if (settingsOverlay != null) {
            closeSettingsOverlay(false);
        }
        if (overlay != null) {
            overlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        settings.setOverlayVisible(visible);
    }

    /** 重設流程狀態並開始週期性截圖。 */
    private void startAutomation() {
        if (settings.allowedFlowers().isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        runGeneration++;
        running = true;
        automationMode = AutomationMode.PLANTING;
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
        if (toggle != null) {
            toggle.setText(R.string.action_pause);
            toggle.setContentDescription(getString(R.string.action_pause));
        }
        if (overlay != null) {
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
        setStatus(getString(R.string.status_planting_checking_entry));
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.overlay_planting_checking),
                getString(R.string.overlay_ocr_detail));
        schedule(200);
    }

    /** 啟動花瓣生產；先處理目前隊伍，三擊哨子只計入後續換隊次數。 */
    private void startFeedAutomation(FeedSettingsInput input) {
        List<String> sequence = settings.allowedFlowers();
        if (sequence.isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.FEED;
        feedSettings = input;
        feedStep = FeedStep.WAITING_GAME_READY;
        feedTargetFlower = sequence.get(0);
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
        resetFeedSpiralState();
        resetFeedHoldState();
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
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
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.POSTCARD;
        postcardAutomation.start(collectionLimit, petalPotName, pikminCount);
        postcardReturnGuard.reset();
        postcardUnknownFrames = 0;
        postcardMissingControlFrames = 0;
        postcardReceiptWaitFrames = 0;
        postcardBackAttempts = 0;
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        postcardPikminCountConfirmations = 0;
        postcardLastPikminCount = -1;
        if (toggle != null) {
            toggle.setText(R.string.action_pause);
            toggle.setContentDescription(getString(R.string.action_pause));
        }
        if (overlay != null) {
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
        setPostcardStatus(getString(
                R.string.status_postcard_progress,
                postcardAutomation.completedCount(),
                postcardAutomation.collectionLimit()));
        schedule(200);
    }

    /** 啟動  的派遣頁面順序，但所有 OCR、像素與前景判斷都由 PikminX 執行。 */
    private void startExpeditionDispatch(
            int count,
            ExpeditionTargetMode targetMode,
            DispatchSelectionMethod selectionMethod,
            DispatchPikminType pikminType) {
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_reward_wrong_page));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.DISPATCH;
        expeditionTargetMode = targetMode == null
                ? ExpeditionTargetMode.FRUIT_AND_POT : targetMode;
        dispatchSelectionMethod = selectionMethod == null
                ? DispatchSelectionMethod.AUTO : selectionMethod;
        dispatchPikminType = pikminType == null ? DispatchPikminType.MIXED : pikminType;
        expeditionDispatchSession = new ExpeditionDispatchSession(
                count, android.os.SystemClock.elapsedRealtime());
        dispatchColorSelected = dispatchPikminType == DispatchPikminType.MIXED;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchPikminTapIndex = 0;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoAnchorMissingFrames = 0;
        dispatchUnknownFrames = 0;
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
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
        Rect gameBounds = activeGameBoundsStrict();
        if (gameBounds == null) {
            showFloatingNotice(getString(R.string.status_return_reward_left_game));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.RETURN_REWARD;
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
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
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
        if (windowManager == null) {
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
        if (bounds == null || windowManager == null) {
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

    /** 排程回呼：只在遊戲前景且沒有其他掃描時擷取畫面。 */
    private void requestScan() {
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
            hideReturnRewardRoiForCapture();
            long generation = runGeneration;
            handler.postDelayed(() -> {
                if (!isActiveRun(generation)
                        || automationMode != AutomationMode.RETURN_REWARD) {
                    busy = false;
                    restoreReturnRewardRoiAfterCapture();
                    return;
                }
                takeGameScreenshot(generation);
            }, RETURN_REWARD_FRAME_HIDE_DELAY_MILLIS);
            return;
        }
        takeGameScreenshot(runGeneration);
    }

    /** 建立截圖要求；所有流程皆由同一個序列化管線送出。 */
    private void takeGameScreenshot(long generation) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        long sequence = ++captureSequence;
        Rect gameBounds = null;
        boolean gameWindowAvailable = root != null && GAME_PACKAGE.contentEquals(root.getPackageName());
        if (gameWindowAvailable) {
            gameBounds = new Rect();
            root.getBoundsInScreen(gameBounds);
            if (gameBounds.isEmpty()) {
                gameBounds.set(
                        0,
                        0,
                        getResources().getDisplayMetrics().widthPixels,
                        getResources().getDisplayMetrics().heightPixels);
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && gameWindowAvailable) {
            screenshotRequests.enqueue(new ScreenshotRequest(
                    List.of(), generation, CaptureGeometry.Mode.WINDOW,
                    captureBounds(gameBounds), captureBounds(gameBounds),
                    Display.DEFAULT_DISPLAY, root.getWindowId(), sequence));
            dispatchNextScreenshot();
            return;
        }

        // Android 13 and older can only capture the whole display. Keep the
        // visible overlays stable, then remove their pixels from the copy used
        // by OCR so the user never sees a hide/show cycle.
        List<ScreenshotOverlayMask.Region> overlayRegions = captureVisibleOverlayRegions();
        screenshotRequests.enqueue(new ScreenshotRequest(
                overlayRegions,
                generation,
                CaptureGeometry.Mode.DISPLAY,
                new CaptureGeometry.Bounds(
                        0,
                        0,
                        getResources().getDisplayMetrics().widthPixels,
                        getResources().getDisplayMetrics().heightPixels),
                gameBounds == null ? null : captureBounds(gameBounds),
                Display.DEFAULT_DISPLAY,
                -1,
                sequence));
        dispatchNextScreenshot();
    }

    private void dispatchNextScreenshot() {
        ScreenshotRequestQueue.Entry<ScreenshotRequest> entry = screenshotRequests.startNext();
        if (entry == null) {
            return;
        }
        handler.removeCallbacks(screenshotTimeoutTask);
        handler.postDelayed(screenshotTimeoutTask, SCREENSHOT_CALLBACK_TIMEOUT_MILLIS);
        ScreenshotRequest request = entry.request();
        TakeScreenshotCallback callback = screenshotCallback(entry.id(), request);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && request.captureMode() == CaptureGeometry.Mode.WINDOW) {
            takeScreenshotOfWindow(request.windowId(), getMainExecutor(), callback);
        } else {
            takeScreenshot(request.displayId(), getMainExecutor(), callback);
        }
    }

    private boolean completeScreenshotRequest(long requestId) {
        if (!screenshotRequests.finish(requestId)) {
            return false;
        }
        handler.removeCallbacks(screenshotTimeoutTask);
        return true;
    }

    private void screenshotTimedOut() {
        ScreenshotRequestQueue.Entry<ScreenshotRequest> active = screenshotRequests.active();
        if (active == null || !screenshotRequests.finish(active.id())) {
            return;
        }
        restoreReturnRewardRoiAfterCapture();
        scanFailed(getString(R.string.status_capture_failed, -1), active.request().generation());
    }

    private void clearScreenshotPipeline() {
        handler.removeCallbacks(screenshotTimeoutTask);
        screenshotRequests.clear();
        restoreReturnRewardRoiAfterCapture();
    }

    /** 建立截圖回呼，統一處理 bitmap、OCR 與失敗重試。 */
    private TakeScreenshotCallback screenshotCallback(
            long requestId, ScreenshotRequest request) {
        long generation = request.generation();
        return new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                restoreReturnRewardRoiAfterCapture();
                Bitmap bitmap;
                try {
                    bitmap = copyBitmap(result);
                } catch (RuntimeException error) {
                    if (completeScreenshotRequest(requestId)) {
                        scanFailed(getString(R.string.status_copy_failed), generation);
                    }
                    return;
                }
                if (!completeScreenshotRequest(requestId) || !isActiveRun(generation)) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap == null) {
                    scanFailed(getString(R.string.status_copy_failed), generation);
                    return;
                }
                CaptureGeometry captureGeometry = new CaptureGeometry(
                        request.captureMode(),
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        request.expectedSourceBoundsOnScreen(),
                        request.targetWindowBoundsOnScreen(),
                        request.displayId(),
                        request.sequence(),
                        result.getTimestamp());
                maskOverlayRegions(bitmap, request.overlayRegions());
                // 鍵盤會遮住花盆清單並令整頁 OCR 變成 UNKNOWN。這個狀態只依系統視窗
                // 判斷，因此必須在 OCR 前執行，確保任何鍵盤語言或版面都能離開。
                if (automationMode == AutomationMode.POSTCARD
                        && postcardAutomation.step()
                                == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
                    busy = false;
                    try {
                        closePostcardKeyboard(bitmap);
                    } finally {
                        bitmap.recycle();
                    }
                    return;
                }
                if (automationMode == AutomationMode.PLANTING
                        && automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
                    busy = false;
                    try {
                        closePlantingSearchKeyboard(bitmap);
                    } finally {
                        bitmap.recycle();
                    }
                    return;
                }
                if (automationMode == AutomationMode.FEED
                        && feedStep == FeedStep.CLOSING_NECTAR_KEYBOARD) {
                    busy = false;
                    try {
                        closeFeedSearchKeyboard(bitmap);
                    } finally {
                        bitmap.recycle();
                    }
                    return;
                }
                OcrScan.Profile profile = ocrProfileForCurrentStep();
                startOcrTransaction(
                        bitmap,
                        profile,
                        captureGeometry,
                        "full",
                        true,
                        frame -> {
                        recordOcrDiagnostic("full", frame);
                        // Focused OCR copies this bitmap synchronously before this callback returns.
                        runWithCaptureGeometry(
                                frame.captureGeometry(), () -> handleTokens(frame, bitmap));
                        },
                        error -> {
                            recordOcrDiagnosticFailure("full", profile, captureGeometry, error);
                            scanFailed(getString(R.string.status_ocr_failed), generation);
                        });
            }

            @Override
            public void onFailure(int errorCode) {
                restoreReturnRewardRoiAfterCapture();
                if (!completeScreenshotRequest(requestId) || !isActiveRun(generation)) {
                    return;
                }
                scanFailed(getString(R.string.status_capture_failed, errorCode), generation);
            }
        };
    }

    /** 花盆搜尋與接收頁只需中文 UI，避免等待五個文字系統模型全部完成。 */
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
            boolean chineseOnly = automationStep == AutomationStep.REVEALING_SEARCH_PANEL
                    || automationStep == AutomationStep.CHECKING_PLANTING_ENTRY
                    || automationStep == AutomationStep.WAITING_INITIAL_PLANTING_MENU
                    || automationStep == AutomationStep.WAITING_MENU_AFTER_START
                    || automationStep == AutomationStep.OPENING_SEARCH
                    || automationStep == AutomationStep.CLEARING_SEARCH
                    || automationStep == AutomationStep.ENTERING_SEARCH
                    || automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD
                    || automationStep == AutomationStep.SELECTING_SEARCH_RESULT
                    || automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
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
    }

    private void recordOcrDiagnosticFailure(
            String source,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Exception error) {
        if (isPlantingEntryStep()) {
            logPlantingEntryFailure(profile, error);
        }
    }


    private static CaptureGeometry.Bounds captureBounds(Rect bounds) {
        return new CaptureGeometry.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
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

    /** Captures physical screen bounds before the asynchronous screenshot starts. */
    private List<ScreenshotOverlayMask.Region> captureVisibleOverlayRegions() {
        List<ScreenshotOverlayMask.Region> regions = new ArrayList<>();
        addVisibleOverlayRegion(regions, overlay);
        addVisibleOverlayRegion(regions, noticeOverlay);
        return List.copyOf(regions);
    }

    private static void addVisibleOverlayRegion(
            List<ScreenshotOverlayMask.Region> regions, View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isAttachedToWindow()) {
            return;
        }
        Rect bounds = new Rect();
        if (view.getGlobalVisibleRect(bounds) && !bounds.isEmpty()) {
            regions.add(new ScreenshotOverlayMask.Region(
                    bounds.left, bounds.top, bounds.right, bounds.bottom));
        }
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

    /** 執行一次 OCR 結果狀態機，依序選花、確認並開始種花。 */
    private void handleTokens(OcrScan.Frame frame, Bitmap bitmap) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        if (automationMode == AutomationMode.FEED) {
            handleFeedTokens(tokens, bitmap);
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
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        List<String> sequence = settings.allowedFlowers();
        if (isPlantingSearchStep()) {
            handlePlantingFlowerSearch(tokens, bitmap, frame);
            return;
        }
        PlantingScreenAnalyzer.Detection plantingScreen = PlantingScreenAnalyzer.analyze(
                tokens,
                PetalCatalog.petals(),
                width,
                height,
                bitmap::getPixel);
        if (isPlantingEntryStep()) {
            logPlantingEntryFrame(frame, plantingScreen);
        }
        PlantingControlEvidence plantingControls = collectPlantingControlEvidence(
                tokens, width, height, plantingScreen);

        switch (automationStep) {
            case CHECKING_PLANTING_ENTRY -> {
                handleInitialPlantingEntry(plantingScreen);
                return;
            }
            case WAITING_INITIAL_PLANTING_MENU -> {
                verifyPlantingMenuOpened(plantingScreen, false);
                return;
            }
            case WAITING_MENU_AFTER_START -> {
                verifyPlantingMenuOpened(plantingScreen, true);
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
                handlePlantingFlowerSearch(tokens, bitmap, frame);
                return;
            }
        }

        PetalMatcher.Selection highlighted = PetalMatcher.findHighlightedFlower(
                tokens,
                PetalCatalog.petals(),
                width,
                height,
                flower -> CardHighlight.score(
                        width, height, flower.x(), flower.y(), bitmap::getPixel));
        if (automationStep == AutomationStep.VERIFYING_SELECTION) {
            verifyFlowerSelection(highlighted, bitmap);
            return;
        }

        boolean plantingCanStart = plantingControls.startVisible();
        String firstFlower = sequence.get(0);

        // 每次開始都先搜尋第一順位；搜尋結果已連續確認名稱與數量，不再重讀全畫面。
        if (currentFlower.isEmpty()) {
            returnToInitialPlantingEntry();
            return;
        }

        if (!PetalMatcher.hasVisibleFlowerCard(
                tokens, PetalCatalog.petals(), width, height)) {
            setStatus(getString(R.string.status_waiting_menu));
            handlePlantingMonitorMiss(bitmap);
            return;
        }

        if (plantingCanStart) {
            if (highlighted != null
                    && firstFlower.equals(highlighted.name())) {
                currentFlower = highlighted.name();
                showPlantingStatus(highlighted.name(), highlighted.count());
                automationStep = AutomationStep.WAITING_START;
                actionAttempts = 0;
                startPlanting(plantingControls);
                return;
            }
            beginPlantingFlowerSearch(firstFlower, 0, true);
            return;
        }

        PetalMatcher.Selection visibleCurrent = highlighted == null
                ? PetalMatcher.findFlower(tokens, currentFlower, width, height)
                : null;
        PetalMatcher.Selection monitored = PlantingFlowPolicy.monitoringSelection(
                highlighted, visibleCurrent);
        if (monitored == null) {
            setStatus(getString(R.string.status_selected_not_visible));
            setPlantingNoticeText(
                    getString(R.string.overlay_planting_unreadable, currentFlower), false);
            handlePlantingMonitorMiss(bitmap);
            return;
        }

        handlePlantingMonitorSelection(monitored);
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
    private void handleFeedTokens(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        switch (feedStep) {
            case WAITING_GAME_READY -> waitForFeedGameReady(tokens, bitmap);
            case OPENING_NECTAR -> {
                if (FeedScreenAnalyzer.isNectarPanelOpen(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)) {
                    ObservationStability.Result stability = feedReadyStability.observe(
                            "nectar-panel",
                            bitmap.getWidth() / 2,
                            bitmap.getHeight() / 5,
                            bitmap.getWidth(),
                            bitmap.getHeight());
                    statusFeed(getString(R.string.status_feed_confirming_panel_open));
                    if (stability == ObservationStability.Result.STABLE) {
                        feedReadyStability.reset();
                        feedNectarOpenAttempts = 0;
                        feedStep = FeedStep.OPENING_NECTAR_SEARCH;
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
                dispatchTap(
                        Math.round(bitmap.getWidth() * 0.52f),
                        Math.round(bitmap.getHeight() * 0.90f),
                        90L,
                        () -> schedule(700L),
                        () -> stopWithError(getString(R.string.status_feed_open_failed)));
            }
            case OPENING_NECTAR_SEARCH -> openFeedNectarSearch(bitmap);
            case CLEARING_NECTAR_SEARCH -> clearFeedNectarSearch(bitmap);
            case ENTERING_NECTAR_SEARCH -> enterFeedNectarSearch();
            case CONFIRMING_NECTAR_SEARCH -> confirmFeedNectarSearch(bitmap);
            case CLOSING_NECTAR_KEYBOARD -> closeFeedSearchKeyboard(bitmap);
            case SELECTING_NECTAR -> {
                String query = FeedScreenAnalyzer.nectarSearchQuery(feedTargetFlower);
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
                FeedScreenAnalyzer.NectarSearchAnalysis nectarAnalysis =
                        FeedScreenAnalyzer.analyzeSearchedNectar(
                                tokens,
                                feedTargetFlower,
                                bitmap.getWidth(),
                                bitmap.getHeight());
                FeedScreenAnalyzer.NectarSelection selection = nectarAnalysis.selection();
                if (selection == null) {
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
                NectarTemplateMatcher.Evidence visualEvidence = nectarTemplateMatcher.match(
                        bitmap,
                        feedTargetFlower,
                        selection.x(),
                        selection.tapY());
                Log.i(TAG, "FEED_NECTAR_TEMPLATE status=" + visualEvidence.status()
                        + " expectedScore=" + visualEvidence.expectedScore()
                        + " bestScore=" + visualEvidence.bestScore());
                if (visualEvidence.status() == NectarTemplateMatcher.Status.CONFLICT) {
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
                int requiredTargetFrames = NectarTemplateMatcher.requiredStableFrames(
                        visualEvidence.status(), FEED_REQUIRED_NECTAR_TARGET_FRAMES);
                String stabilityReason = FeedScreenAnalyzer.nectarSelectionStabilityReason(
                        feedNectarCandidate,
                        selection,
                        bitmap.getWidth(),
                        bitmap.getHeight());
                if (!"stable".equals(stabilityReason)) {
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

    private void waitForFeedGameReady(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        boolean panelOpen = FeedScreenAnalyzer.isNectarPanelOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
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

    private void openFeedNectarSearch(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CardHighlight.Point search = CardHighlight.findPetalSearchButton(
                width, height, bitmap::getPixel);
        if (search != null) {
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
        boolean searchOpen = CardHighlight.isPetalSearchOpen(
                width, height, bitmap::getPixel);
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
        if (!CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)) {
            feedSearchActionAttempts = 0;
            feedSearchMissingFrames = 0;
            feedSearchResetPhase = FeedSearchResetPhase.NONE;
            feedStep = FeedStep.OPENING_NECTAR_SEARCH;
            schedule(FEED_OCR_RETRY_MILLIS);
            return;
        }
        CardHighlight.Point clear = CardHighlight.findPetalSearchCloseButton(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
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
                        && !searchInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
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
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        Integer nectarCount = FeedScreenAnalyzer.currentNectarCount(
                tokens, bitmap.getWidth(), bitmap.getHeight());
        if (FeedScreenAnalyzer.isFeedViewReadyAfterNectarSelection(
                panelVisible, detailOpen, nectarCount)) {
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
        long generation = runGeneration;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        left, 0L, FEED_ZOOM_MILLIS))
                .addStroke(new GestureDescription.StrokeDescription(
                        right, 0L, FEED_ZOOM_MILLIS))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    handler.postDelayed(() -> {
                        if (isActiveRun(generation)) {
                            feedZoomReady = true;
                            completed.run();
                        }
                    }, FEED_ZOOM_SETTLE_MILLIS);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    stopWithError(getString(R.string.status_feed_zoom_failed));
                }
            }
        }, handler);
        if (!accepted && isActiveRun(generation)) {
            stopWithError(getString(R.string.status_feed_zoom_failed));
        }
    }

    private void selectFeedNectar(FeedScreenAnalyzer.NectarSelection selection) {
        feedTargetMissingFrames = 0;
        feedNectarCandidate = null;
        feedNectarCandidateFrames = 0;
        int effectiveLimit = feedSettings.effectivePetalLimit();
        if (FeedScreenAnalyzer.hasReachedPetalLimit(
                selection.petalCount(), effectiveLimit)) {
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
            if (advanceFeedNectar()) {
                schedule(FEED_OCR_RETRY_MILLIS);
            }
            return;
        }
        statusFeed(getString(
                R.string.status_feed_selecting_nectar,
                selection.name(),
                selection.count(),
                selection.petalCount()));
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

    /** 只前進到下一項；PetalMatcher.nextTarget 在尾端回傳 null，不循環。 */
    private boolean advanceFeedNectar() {
        String next = PetalMatcher.nextTarget(settings.allowedFlowers(), feedTargetFlower);
        if (next == null) {
            finishWithSuccess(getString(R.string.status_feed_no_more_nectar));
            return false;
        }
        feedTargetFlower = next;
        feedTargetMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchActionAttempts = 0;
        feedSearchMissingFrames = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.CLOSING;
        feedNectarCandidate = null;
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
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
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
        readFeedConsumedNectar(true);
    }

    private void recordFeedPetalGain(int gain) {
        feedCollectedPetals += gain;
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
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
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
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
            stopWithError(getString(R.string.status_feed_detail_close_failed));
        } else {
            schedule(FEED_DETAIL_CLOSE_SETTLE_MILLIS);
        }
        return true;
    }

    private void logFeedSpiral(String event, String details) {
        if (BuildConfig.GEOMETRY_VALIDATION) {
            Log.i(TAG, "FEED_SPIRAL event=" + event + " " + details);
        }
    }

    private void completeFeedPetalCollection() {
        feedRound++;
        feedAttemptCount = 0;
        feedNectarBeforeRound = -1;
        feedCollectAfterCount = false;
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
        tapFeedWhistle(1);
    }

    private void tapFeedWhistle(int tapNumber) {
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            stopWithError(getString(R.string.status_feed_left_game));
            return;
        }
        dispatchScreenTap(
                Math.round(bounds.left + bounds.width() * 0.87f),
                Math.round(bounds.top + bounds.height() * 0.915f),
                90L,
                () -> {
                    if (tapNumber < FEED_WHISTLE_TAP_COUNT) {
                        handler.postDelayed(
                                () -> tapFeedWhistle(tapNumber + 1),
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
        feedTargetMissingFrames = 0;
        feedNectarOpenAttempts = 0;
        feedSearchActionAttempts = 0;
        feedSearchMissingFrames = 0;
        feedSearchInputAttempts = 0;
        feedSearchOpenConfirmationFrames = 0;
        feedSearchTextConfirmationFrames = 0;
        feedSearchResetPhase = FeedSearchResetPhase.CLOSING;
        feedNectarCandidate = null;
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
        feedHoldX = endX;
        feedHoldY = endY;
        feedHoldLastCount = feedNectarBeforeRound >= 0 ? feedNectarBeforeRound : null;
        feedHoldCompleted = completed;
        feedHoldFailed = failed;
        long generation = runGeneration;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(dragStroke)
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (!isActiveRun(generation)) {
                    return;
                }
                feedHoldStroke = dragStroke;
                feedHoldStartedAt = android.os.SystemClock.elapsedRealtime();
                dispatchNextFeedHoldSlice();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (isActiveRun(generation)) {
                    finishFeedHoldGesture(false);
                }
            }
        }, handler);
        if (!accepted) {
            finishFeedHoldGesture(false);
        }
    }

    private void dispatchNextFeedHoldSlice() {
        if (feedHoldStroke == null || feedHoldReleasing) {
            return;
        }
        long elapsed = android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt;
        if (elapsed >= FEED_HOLD_MAX_MILLIS) {
            releaseFeedHoldGesture("timeout");
            return;
        }
        long duration = Math.min(FEED_HOLD_SAMPLE_MILLIS, FEED_HOLD_MAX_MILLIS - elapsed);
        float nextX = feedHoldX + feedHoldDirection;
        Path hold = feedHoldPath(nextX);
        GestureDescription.StrokeDescription nextStroke = feedHoldStroke.continueStroke(
                hold, 0L, Math.max(1L, duration), true);
        long generation = runGeneration;
        boolean accepted = dispatchGesture(
                new GestureDescription.Builder().addStroke(nextStroke).build(),
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
                            finishFeedHoldGesture(false);
                        }
                    }
                },
                handler);
        if (!accepted) {
            finishFeedHoldGesture(false);
        }
    }

    private void observeFeedHold(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (feedHoldStroke == null || feedHoldReleasing) {
            return;
        }
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
            releaseFeedHoldGesture(elapsed >= FEED_HOLD_MAX_MILLIS ? "timeout" : "stable");
            return;
        }
        if (current == null) {
            statusFeed(getString(R.string.status_feed_count_retry));
        }
        dispatchNextFeedHoldSlice();
    }

    private void releaseFeedHoldGesture(String reason) {
        if (feedHoldStroke == null || feedHoldReleasing) {
            return;
        }
        feedHoldReleasing = true;
        float releaseX = feedHoldX + feedHoldDirection;
        GestureDescription.StrokeDescription releaseStroke = feedHoldStroke.continueStroke(
                feedHoldPath(releaseX), 0L, 1L, false);
        long generation = runGeneration;
        logFeedSpiral("hold-release",
                "reason=" + reason
                        + " elapsedMs="
                        + (android.os.SystemClock.elapsedRealtime() - feedHoldStartedAt)
                        + " count=" + feedHoldLastCount
                        + " stableReads=" + feedHoldStableReads);
        boolean accepted = dispatchGesture(
                new GestureDescription.Builder().addStroke(releaseStroke).build(),
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            finishFeedHoldGesture(true);
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (isActiveRun(generation)) {
                            finishFeedHoldGesture(false);
                        }
                    }
                },
                handler);
        if (!accepted) {
            finishFeedHoldGesture(false);
        }
    }

    private Path feedHoldPath(float endX) {
        Path path = new Path();
        path.moveTo(feedHoldX, feedHoldY);
        path.lineTo(endX, feedHoldY);
        return path;
    }

    private void finishFeedHoldGesture(boolean succeeded) {
        Runnable callback = succeeded ? feedHoldCompleted : feedHoldFailed;
        resetFeedHoldState();
        if (callback != null) {
            callback.run();
        }
    }

    private void resetFeedHoldState() {
        feedHoldStroke = null;
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
        ExpeditionScreenAnalyzer.Screen ocrScreen = ExpeditionScreenAnalyzer.classify(tokens);
        ExpeditionScreenAnalyzer.Screen screen = ExpeditionScreenAnalyzer.classify(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
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
                && screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN
                && ExpeditionScreenAnalyzer.findResultClose(bitmap) != null) {
            expeditionDispatchSession.advance(
                    ExpeditionDispatchSession.Stage.SELECTION,
                    ExpeditionDispatchSession.Stage.WAIT_RESULT,
                    now);
        }
        ExpeditionDispatchSession.Stage stage = expeditionDispatchSession.stage();
        if (previousStage == ExpeditionDispatchSession.Stage.DETAIL
                && stage == ExpeditionDispatchSession.Stage.SELECTION) {
            recordDispatchDetailDiagnostic(
                    "selection-confirmed", "destination-confirmed", "unknown",
                    previousDetailTapAttempts, false,
                    bitmap.getWidth(), bitmap.getHeight(), null);
        }
        if (BuildConfig.DEBUG && previousStage != stage) {
            Log.d(TAG, "DISPATCH_STAGE from=" + previousStage
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

    private void handleDispatchList(
            OcrScan.Frame frame,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        List<PetalMatcher.Token> tokens = frame.tokens();
        if (expeditionDispatchSession.transitionPending()) {
            ExpeditionDispatchSession.Confirmation timeout =
                    expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_opening_detail));
            }
            return;
        }
        if (screen != ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            if (screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN
                    && ExpeditionScreenAnalyzer.hasExploreNavigationAnchor(
                            tokens, bitmap.getWidth(), bitmap.getHeight())) {
                scanFocusedDispatchList(
                        bitmap, ExpeditionScreenAnalyzer.isExploreListStart(tokens));
                return;
            }
            handleDispatchConfirmation(expeditionDispatchSession.confirm("", now));
            waitForDispatchFrame(getString(R.string.status_reward_wrong_page));
            return;
        }
        ExpeditionDispatchSession.BottomSettleDecision bottomDecision =
                expeditionDispatchSession.observeListForBottom(
                        ExpeditionScreenAnalyzer.isExplorePanelExpanded(
                                tokens, bitmap.getWidth(), bitmap.getHeight()),
                        now);
        if (bottomDecision == ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP) {
            revealDispatchExplorePanel(
                    ExpeditionScreenAnalyzer.findExploreTabAnchor(
                            tokens, bitmap.getWidth(), bitmap.getHeight()),
                    bitmap);
            return;
        }
        if (bottomDecision == ExpeditionDispatchSession.BottomSettleDecision.FAILED) {
            stopWithError(getString(R.string.status_reward_bottom_failed));
            return;
        }
        ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                tokens,
                expeditionTargetMode,
                bitmap.getWidth(),
                bitmap.getHeight(),
                bitmap::getPixel);
        if (target == null) {
            scanFocusedDispatchList(
                    bitmap, ExpeditionScreenAnalyzer.isExploreListStart(tokens));
            return;
        }
        handleDispatchListTarget(
                target, bitmap.getWidth(), bitmap.getHeight(), now);
    }

    /** 全畫面漏讀小字時，放大清單區域再辨識一次，避免直接滑過可見目標。 */
    private void scanFocusedDispatchList(Bitmap bitmap, boolean listStartVisible) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        long generation = runGeneration;
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_reward_scanning),
                getString(R.string.overlay_reward_safety_items));
        startOcrTransaction(
                bitmap,
                OcrScan.Profile.DISPATCH_LIST,
                captureGeometry,
                "dispatch-focused",
                false,
                frame -> {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
                if (!isActiveRun(generation)
                        || expeditionDispatchSession == null
                        || expeditionDispatchSession.stage()
                                != ExpeditionDispatchSession.Stage.LIST_SEARCH) {
                    return;
                }
                recordOcrDiagnostic("dispatch-focused", frame);
                long now = android.os.SystemClock.elapsedRealtime();
                ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                        frame.tokens(),
                        expeditionTargetMode,
                        width,
                        height,
                        frame::pixelAtSource);
                if (target == null) {
                    handleDispatchListMiss(listStartVisible, now);
                } else {
                    handleDispatchListTarget(target, width, height, now);
                }
                });
                },
                error -> {
                if (isActiveRun(generation)) {
                    recordOcrDiagnosticFailure(
                            "dispatch-focused", OcrScan.Profile.DISPATCH_LIST, captureGeometry, error);
                    handleDispatchListMiss(
                            listStartVisible, android.os.SystemClock.elapsedRealtime());
                }
                });
    }

    private void handleDispatchListMiss(boolean listStartVisible, long now) {
        ExpeditionDispatchSession.Confirmation timeout =
                expeditionDispatchSession.confirm("", now);
        if (handleDispatchConfirmation(timeout) || !running) {
            return;
        }
        ExpeditionDispatchSession.ListScanDecision decision =
                expeditionDispatchSession.recordListMiss(listStartVisible, now);
        if (decision == ExpeditionDispatchSession.ListScanDecision.SCROLL) {
            scrollDispatchListTowardEarlierItems();
        } else if (decision == ExpeditionDispatchSession.ListScanDecision.AT_LIST_START) {
            stopWithError(getString(R.string.status_reward_target_missing));
        } else {
            waitForDispatchFrame(getString(R.string.status_reward_target_missing));
        }
    }

    private void handleDispatchListTarget(
            ExpeditionScreenAnalyzer.Target target, int width, int height, long now) {
        expeditionDispatchSession.recordListTargetFound();
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                target.confirmationKey(width, height), now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_confirming));
            return;
        }
        dispatchCurrentItemKind = target.kind();
        expeditionDispatchSession.beginTransition(now);
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
        ExpeditionScreenAnalyzer.Point action = screen == ExpeditionScreenAnalyzer.Screen.DETAIL
                ? ExpeditionScreenAnalyzer.findDetailAction(
                        tokens, width, height, bitmap::getPixel)
                : null;
        if (expeditionDispatchSession.transitionPending()) {
            if (expeditionDispatchSession.shouldRetryDetailTap(screen, action != null, now)) {
                ExpeditionDispatchSession.Confirmation retryConfirmation =
                        expeditionDispatchSession.confirm(
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
                expeditionDispatchSession.confirm(
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
                if (selectedCount > 0 || visibleGo != null) {
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
        if (go == null
                || (dispatchSelectionMethod.requiresFullSelection()
                        && !ExpeditionScreenAnalyzer.hasFullSelection(tokens))) {
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
        dispatchActionTap(
                go,
                getString(R.string.status_reward_tapping_go),
                () -> {});
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
                "CLOSE:" + close.x() / 24 + ":" + close.y() / 24, now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_waiting_result));
            return;
        }
        expeditionDispatchSession.beginTransition(now);
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
        if (settings.recordConfirmedExpeditionDispatch() < 0) {
            stopWithError(getString(R.string.status_reward_progress_save_failed));
            return;
        }
        dispatchCurrentItemKind = null;
        int completed = expeditionDispatchSession.completedCount();
        int target = expeditionDispatchSession.targetCount();
        if (expeditionDispatchSession.complete()) {
            finishWithSuccess(getString(R.string.status_reward_complete, completed));
            return;
        }
        dispatchColorSelected = dispatchPikminType == DispatchPikminType.MIXED;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchPikminTapIndex = 0;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoAnchorMissingFrames = 0;
        waitForDispatchFrame(getString(R.string.status_reward_progress, completed, target));
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
    }

    private void recordFeedNectarSelectionDiagnostic(
            String outcome,
            String reason,
            int scan,
            int candidateFrames,
            int missingFrames,
            boolean nectarCountFound,
            boolean petalCountFound) {
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
                        expeditionDispatchSession.completedCount(),
                        expeditionDispatchSession.targetCount()));
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
        ScreenCoordinateTransform.Point mapped = ScreenCoordinateTransform.toScreen(
                point.x(),
                point.y(),
                captureGeometry);
        return new ExpeditionScreenAnalyzer.Point(mapped.x(), mapped.y());
    }

    private void handleDispatchPikminFilter(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            long now) {
        String label = dispatchPikminType.label();
        if (!dispatchSearchOpened) {
            if (hasFocusedGameEditableText()) {
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
            if (!focusedGameEditableTextMatches(label)) {
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
                    expeditionDispatchSession.confirm("PIKMIN_FILTER:" + label, now);
            if (!handleDispatchConfirmation(confirmation)) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
            dispatchSearchTextConfirmed = true;
            dispatchSearchInputAttempts = 0;
            expeditionDispatchSession.recordProgress(now);
        }

        boolean searchPageConfirmed = dispatchSearchOpened
                && dispatchSearchTextConfirmed
                && focusedGameEditableTextMatches(label);
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
        if (!gameEditableTextMatches(label)) {
            dispatchSearchOpened = false;
            dispatchSearchTextConfirmed = false;
            dispatchSearchKeyboardGuard.reset();
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        dispatchColorSelected = true;
        expeditionDispatchSession.recordProgress(now);
        dispatchSearchKeyboardGuard.reset();
        dispatchPikminTapIndex = 0;
        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
    }

    /** 每張畫面依相對 5 欄網格點一隻，避免長拖曳被判成左右滑動。 */
    private void selectDispatchPikminFromGrid(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            long now) {
        if (ExpeditionScreenAnalyzer.hasFullSelection(tokens)) {
            dispatchPikminSelected = true;
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }

        List<PostcardMatcher.Target> candidates = PostcardMatcher.findPikminSelectionSlots(
                bitmap.getWidth(), bitmap.getHeight());
        if (dispatchPikminTapIndex >= candidates.size()) {
            waitForDispatchFrame(getString(R.string.status_reward_selection_missing));
            return;
        }
        PostcardMatcher.Target candidate = candidates.get(dispatchPikminTapIndex);
        ExpeditionDispatchSession.Confirmation confirmation =
                expeditionDispatchSession.confirm(
                        "PIKMIN:" + dispatchPikminTapIndex + ":"
                                + candidate.x() / 24 + ":" + candidate.y() / 24,
                        now,
                        1);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        dispatchActionTap(
                new ExpeditionScreenAnalyzer.Point(candidate.x(), candidate.y()),
                getString(R.string.status_reward_selecting_pikmin),
                DISPATCH_PIKMIN_TAP_DELAY_MILLIS,
                () -> dispatchPikminTapIndex++);
    }
    
    /** 由 OCR 探險頁籤錨點向上拉起面板；完成後重新辨識，最多由狀態機重試四次。 */
    private void revealDispatchExplorePanel(
            ExpeditionScreenAnalyzer.Point anchor,
            Bitmap bitmap) {
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        ExpeditionScreenAnalyzer.Point start = anchor == null
                ? new ExpeditionScreenAnalyzer.Point(
                        Math.round(bitmap.getWidth() * 0.65f),
                        Math.round(bitmap.getHeight() * 0.45f))
                : anchor;
        ExpeditionScreenAnalyzer.Point screenStart = screenPointFromBitmap(start, bitmap);
        Path path = new Path();
        float endY = Math.max(
                bounds.top + bounds.height() * 0.08f,
                screenStart.y() - bounds.height() * 0.20f);
        path.moveTo(screenStart.x(), screenStart.y());
        path.lineTo(screenStart.x(), endY);
        busy = true;
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.SEARCHING,
                getString(R.string.status_reward_settling_bottom),
                getString(R.string.status_reward_scanning));
        dispatchPath(path, 500L, () -> {
            busy = false;
            schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
        }, () -> {
            busy = false;
            stopWithError(getString(R.string.status_reward_gesture_failed));
        });
    }

    /** 面板展開後持續往清單前段搜尋，直到 OCR 看見蘑菇頂端標記。 */
    private void scrollDispatchListTowardEarlierItems() {
        Rect bounds = activeGameBoundsStrict();
        if (bounds == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        Path path = new Path();
        float x = bounds.left + bounds.width() * 0.50f;
        path.moveTo(x, bounds.top + bounds.height() * 0.60f);
        path.lineTo(x, bounds.top + bounds.height() * 0.78f);
        busy = true;
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.SEARCHING,
                getString(R.string.status_reward_scrolling),
                getString(R.string.status_reward_scanning));
        dispatchPath(path, 720L, () -> {
            busy = false;
            schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
        }, () -> {
            busy = false;
            stopWithError(getString(R.string.status_reward_gesture_failed));
        });
    }

    /** 啟動時先確認地圖種花入口或種花面板，未確認前不發送滑動。 */
    private void handleInitialPlantingEntry(
            PlantingScreenAnalyzer.Detection detection) {
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
            beginInitialPlantingFlowerSearch();
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
            PlantingScreenAnalyzer.Detection detection, boolean afterStart) {
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
                beginInitialPlantingFlowerSearch();
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

    private void beginInitialPlantingFlowerSearch() {
        if (!initialPlantingMenuConfirmed) {
            returnToInitialPlantingEntry();
            return;
        }
        beginPlantingFlowerSearch(settings.allowedFlowers().get(0), 0, true);
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
            List<PetalMatcher.Token> tokens, Bitmap bitmap, OcrScan.Frame frame) {
        logPlantingSwitch("search-frame", CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel));
        if (automationStep == AutomationStep.REVEALING_SEARCH_PANEL) {
            revealPlantingSearchPanel();
            return;
        }
        if (automationStep == AutomationStep.OPENING_SEARCH) {
            openPlantingFlowerSearch(bitmap);
            return;
        }
        if (automationStep == AutomationStep.CLEARING_SEARCH) {
            clearPlantingSearchForNextFlower(bitmap);
            return;
        }
        if (automationStep == AutomationStep.ENTERING_SEARCH) {
            enterPlantingFlowerSearch();
            return;
        }
        if (automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
            closePlantingSearchKeyboard(bitmap);
            return;
        }
        if (automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION) {
            closePlantingSearchAfterSelection(bitmap);
            return;
        }

        String query = PetalCatalog.searchQuery(targetFlower);
        if (!gameEditableTextMatches(query)) {
            automationStep = AutomationStep.ENTERING_SEARCH;
            setStatus(getString(R.string.status_flower_search_confirming_text));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }

        int searchResultsTop = plantingSearchResultsTop(bitmap);
        PetalMatcher.Selection pot = searchResultsTop < 0
                ? null
                : PetalMatcher.findSearchedFlower(
                        tokens,
                        targetFlower,
                        plantingSearchMinimumCount,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        searchResultsTop);
        if (pot == null) {
            handlePlantingSearchMiss();
            return;
        }
        confirmPlantingSearchResult(pot, bitmap.getWidth(), bitmap.getHeight());
    }

    /** 以展開搜尋框的實際中心推算底緣，花盆名稱只在其下方判斷。 */
    private int plantingSearchResultsTop(Bitmap bitmap) {
        CardHighlight.Point search = CardHighlight.findPetalSearchCloseButton(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        return search == null
                ? -1
                : Math.min(
                        bitmap.getHeight() - 1,
                        search.y() + Math.round(bitmap.getHeight() * 0.03f));
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
    private void openPlantingFlowerSearch(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (CardHighlight.isPetalSearchOpen(width, height, bitmap::getPixel)) {
            actionAttempts = 0;
            automationStep = AutomationStep.ENTERING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point search = CardHighlight.findPlantingPetalSearchButton(
                width, height, bitmap::getPixel);
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
        String query = PetalCatalog.searchQuery(targetFlower);
        boolean searchPageConfirmed = CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)
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

    /** 搜尋文字確認後，基礎花盆直接判斷，其餘上滑三次再進入花盆 OCR。 */
    private void scrollPlantingSearchResults(int completedScrolls) {
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
        dispatchPath(
                path,
                FEED_DRAG_MILLIS,
                () -> scrollPlantingSearchResults(completedScrolls + 1),
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
        long generation = runGeneration;
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
                "planting-focused",
                false,
                frame -> {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
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
    private void clearPlantingSearchForNextFlower(Bitmap bitmap) {
        if (!CardHighlight.isPetalSearchOpen(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel)) {
            automationStep = AutomationStep.OPENING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point clear = CardHighlight.findPetalSearchCloseButton(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
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

    /** 點擊已確認花盆，並等待精確名稱或同一卡片高亮背景確認選取結果。 */
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
                () -> schedule(500),
                () -> {
                    switchGuard.cancelSwitch();
                    automationStep = AutomationStep.MONITORING;
                    scheduleNext();
                });
    }

    /** 驗證點擊後的高亮；搜尋結果可依同一卡片背景確認，不再依賴 OCR 花名。 */
    private void verifyFlowerSelection(PetalMatcher.Selection highlighted, Bitmap bitmap) {
        boolean exactNameConfirmed = highlighted != null
                && targetFlower.equals(highlighted.name());
        boolean searchedCardHighlighted = selectionFromSearch
                && targetSelectionX > 0
                && targetSelectionY > 0
                && CardHighlight.score(
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        targetSelectionX,
                        targetSelectionY,
                        bitmap::getPixel) >= 245;
        String confirmedName = exactNameConfirmed ? highlighted.name() : targetFlower;
        if ((exactNameConfirmed || searchedCardHighlighted)
                && switchGuard.confirmSwitch(
                        confirmedName, android.os.SystemClock.elapsedRealtime())) {
            int confirmedCount = exactNameConfirmed ? highlighted.count() : targetCount;
            currentFlower = confirmedName;
            targetCount = confirmedCount;
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
    private void closePlantingSearchAfterSelection(Bitmap bitmap) {
        boolean foreground = activeGameBoundsStrict() != null;
        boolean imeVisible = isInputMethodWindowVisible();
        CardHighlight.Point close = CardHighlight.findPetalSearchCloseButton(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        boolean closedMenu = close == null
                && CardHighlight.findPlantingMenuControls(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel) != null
                && CardHighlight.findPlantingPetalSearchButton(
                        bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel) != null;
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
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            PlantingScreenAnalyzer.Detection detection) {
        return new PlantingControlEvidence(
                detection,
                PetalMatcher.findStartPlantingControl(tokens, width, height),
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
        AccessibilityNodeInfo node = findGameNode(predicate);
        while (node != null) {
            if (node.isClickable()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
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
        PostcardMatcher.Page page = PostcardMatcher.detectPage(tokens, width, height);
        boolean flowerNavigationStep = isFlowerNavigationStep(postcardAutomation.step());
        FlowerDetailActionDetector.Target flowerDetailAction = flowerNavigationStep
                ? FlowerDetailActionDetector.find(width, height, bitmap::getPixel)
                : null;
        MapPostcardBubbleDetector.Target previousFlowerBubble =
                MapPostcardBubbleDetector.find(width, height, bitmap::getPixel);
        if (page == PostcardMatcher.Page.UNKNOWN
                && previousFlowerBubble != null
                && (isFlowerNavigationStep(postcardAutomation.step())
                        || postcardAutomation.step()
                                == PostcardAutomation.Step.WAIT_RECEIPT_EXIT)) {
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
        boolean mapAllowed = flowerNavigationStep
                || postcardAutomation.step() == PostcardAutomation.Step.WAIT_RECEIPT_EXIT;
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

        if (postcardAutomation.receiveTapped()
                && page != PostcardMatcher.Page.POSTCARD_RECEIVED) {
            PostcardReturnGuard.Decision returnDecision =
                    postcardReturnGuard.observe(true, previousFlowerBubble != null);
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

        String expectedQuery = PostcardPotCatalog.searchQuery(
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
        if (CardHighlight.isPetalSearchOpen(width, height, bitmap::getPixel)) {
            postcardPetalSearchMissingFrames = 0;
            postcardAutomation.moveTo(PostcardAutomation.Step.ENTER_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_search_opened));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        CardHighlight.Point search = CardHighlight.findPetalSearchButton(
                width, height, bitmap::getPixel);
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
        String query = PostcardPotCatalog.searchQuery(postcardAutomation.petalPotName());
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
        String query = PostcardPotCatalog.searchQuery(postcardAutomation.petalPotName());
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
        return permitted && performGlobalAction(GLOBAL_ACTION_BACK);
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
        boolean accepted = editable.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        if (!accepted) {
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            accepted = editable.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
        return accepted;
    }

    private boolean gameEditableTextMatches(String value) {
        return editableTextMatches(findGameNode(node ->
                node.isEditable() && node.isEnabled()), value);
    }

    private boolean editableTextMatches(AccessibilityNodeInfo editable, String value) {
        if (editable == null || editable.getText() == null) {
            return false;
        }
        return PetalMatcher.normalize(editable.getText().toString())
                .equals(PetalMatcher.normalize(value));
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
        long generation = runGeneration;
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_focused_petal_ocr),
                postcardAutomation.petalPotName());
        startOcrTransaction(
                bitmap,
                OcrScan.Profile.PETAL_LIST,
                captureGeometry,
                "postcard-focused",
                false,
                frame -> {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
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
                    PostcardPotCatalog.searchQuery(postcardAutomation.petalPotName())));
            return;
        }
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_waiting_search_result),
                PostcardPotCatalog.searchQuery(postcardAutomation.petalPotName()));
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
        if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_postcard_action_failed));
        } else {
            schedule(700);
        }
    }

    private void returnToMapFromFlowerDetail() {
        postcardBackAttempts++;
        setPostcardStatus(getString(R.string.status_postcard_returning_map));
        boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
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
        showFloatingNotice(complete);
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
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        long generation = runGeneration;
        ExpeditionScreenAnalyzer.Point screenPoint = screenPointFromBitmap(
                new ExpeditionScreenAnalyzer.Point(x, y), captureGeometry);
        Path path = new Path();
        path.moveTo(screenPoint.x(), screenPoint.y());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
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
        }, handler);
        if (plantingEntryPhase != null) {
            if (accepted) {
                showPlantingEntryGestureStatus(
                        plantingEntryPhase,
                        R.string.status_planting_entry_tap_sent);
            }
        }
        if (!accepted && isActiveRun(generation)) {
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
        long generation = runGeneration;
        Path path = new Path();
        path.moveTo(screenX, screenY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
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
        }, handler);
        if (!accepted && isActiveRun(generation)) {
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
        long generation = runGeneration;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMillis))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
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
        }, handler);
        if (!accepted && isActiveRun(generation)) {
            failed.run();
        }
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
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !GAME_PACKAGE.contentEquals(root.getPackageName())) {
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
        return bounds;
    }

    /** 判斷非同步回呼是否仍屬於目前這一輪自動化。 */
    private boolean isActiveRun(long generation) {
        return running && generation == runGeneration;
    }

    private void startOcrTransaction(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry geometry,
            String diagnosticSource,
            boolean recycleSourceAtTerminal,
            OcrFrameConsumer success,
            OcrFailureConsumer failure) {
        if (geometry == null) {
            busy = false;
            failure.accept(new IllegalArgumentException("Capture geometry is required"));
            return;
        }
        OcrScanner.Transaction transaction =
                ocrTransactions.begin(runGeneration, geometry.captureSequence());
        Runnable sourceCleanup = recycleSourceAtTerminal
                ? () -> {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
                : () -> { };
        ActiveOcrTransaction active = new ActiveOcrTransaction(
                transaction,
                diagnosticSource,
                profile,
                geometry,
                success,
                failure,
                sourceCleanup);
        activeOcrTransaction = active;
        busy = true;
        handler.postDelayed(active.watchdog, OCR_CALLBACK_TIMEOUT_MILLIS);
        try {
            scanner.scan(
                    bitmap,
                    profile,
                    geometry,
                    transaction,
                    getMainExecutor(),
                    new OcrScanner.FrameCallback() {
                        @Override
                        public void onSuccess(OcrScan.Frame frame) {
                            completeOcrSuccess(active, frame);
                        }

                        @Override
                        public void onFailure(Exception error) {
                            completeOcrFailure(
                                    active,
                                    error,
                                    !(error instanceof OcrScanner.GeometryException));
                        }
                    });
        } catch (RuntimeException error) {
            if (transaction.tryFinish(OcrScanner.TerminalState.FAILURE)) {
                completeOcrFailure(active, error, true);
            }
        }
    }

    private ActiveOcrTransaction clearActiveOcrTransaction(ActiveOcrTransaction expected) {
        if (expected == null
                || activeOcrTransaction != expected
                || !ocrTransactions.clear(expected.transaction)) {
            return null;
        }
        handler.removeCallbacks(expected.watchdog);
        activeOcrTransaction = null;
        busy = false;
        return expected;
    }

    private void completeOcrSuccess(ActiveOcrTransaction expected, OcrScan.Frame frame) {
        ActiveOcrTransaction completed = clearActiveOcrTransaction(expected);
        if (completed == null) {
            return;
        }
        try {
            if (!isActiveRun(completed.transaction.id().runGeneration())) {
                return;
            }
            consecutiveOcrEngineFailures = 0;
            if (!frame.canDriveAction(completed.transaction.id())) {
                completed.failure.accept(
                        new IllegalStateException("OCR frame failed action admission"));
                return;
            }
            try {
                completed.success.accept(frame);
            } catch (RuntimeException error) {
                Log.e(TAG, "OCR success callback failed", error);
                if (isActiveRun(completed.transaction.id().runGeneration())) {
                    completed.failure.accept(error);
                }
            }
        } finally {
            completed.sourceCleanup.run();
        }
    }

    private void completeOcrFailure(
            ActiveOcrTransaction expected, Exception error, boolean engineFailure) {
        ActiveOcrTransaction completed = clearActiveOcrTransaction(expected);
        if (completed == null) {
            return;
        }
        try {
            if (!isActiveRun(completed.transaction.id().runGeneration())) {
                return;
            }
            if (engineFailure && ++consecutiveOcrEngineFailures >= MAX_ACTION_ATTEMPTS) {
                recordOcrDiagnosticFailure(
                        completed.diagnosticSource,
                        completed.profile,
                        completed.geometry,
                        error);
                stopWithError(getString(R.string.status_ocr_failed));
                return;
            }
            completed.failure.accept(error);
        } finally {
            completed.sourceCleanup.run();
        }
    }

    private void ocrTimedOut(ActiveOcrTransaction expected) {
        if (activeOcrTransaction != expected
                || !expected.transaction.tryFinish(OcrScanner.TerminalState.TIMEOUT)) {
            return;
        }
        completeOcrFailure(
                expected, new IllegalStateException("OCR callback timed out"), true);
    }

    private void cancelActiveOcrTransaction() {
        ActiveOcrTransaction active = activeOcrTransaction;
        if (active == null) {
            return;
        }
        active.transaction.tryFinish(OcrScanner.TerminalState.CANCELLED);
        ActiveOcrTransaction cancelled = clearActiveOcrTransaction(active);
        if (cancelled != null) {
            // A callback already dispatched to the main queue may still read source pixels.
            handler.postDelayed(cancelled.sourceCleanup, OCR_CALLBACK_TIMEOUT_MILLIS);
        }
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
        if (overlay != null) {
            return true;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        DraggableIcon icon = new DraggableIcon();
        icon.setImageResource(R.drawable.ic_overlay_flower);
        // 明確讓 drawable 填滿 50dp 視窗，避免只放大透明外框。
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        icon.setFocusable(true);
        icon.setElevation(0);
        icon.setPadding(0, 0, 0, 0);
        icon.setBackground(null);
        icon.setOnClickListener(view -> {
            if (running) {
                pause(getString(R.string.status_paused));
                showFloatingNotice(getString(R.string.status_paused));
            } else {
                showSettingsOverlay();
            }
        });
        icon.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_icon_description),
                getString(R.string.overlay_icon_move_hint)));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(OVERLAY_SIZE_DP),
                dp(OVERLAY_SIZE_DP),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dp(72);

        // 觸控監聽器同時支援拖曳與放開後的 click。
        icon.setOnTouchListener(new DragListener());
        if (!safeAddOverlayView(icon, params, "icon")) {
            return false;
        }
        overlay = icon;
        overlayParams = params;
        renderOverlayStatus(false);
        return true;
    }

    /** 建立可輸入設定的置中卡片，輸入時暫停背景自動化。 */
    private void showSettingsOverlay() {
        if (settingsOverlay != null || overlay == null || windowManager == null) {
            return;
        }
        pause(getString(R.string.status_paused));

        List<EditText> numberInputs = new ArrayList<>();
        LinearLayout panel = new LinearLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                dismissNumberKeyboardOnOutsideTap(this, numberInputs, event);
                return super.dispatchTouchEvent(event);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setElevation(dp(18));
        panel.setBackground(roundedBackground(OVERLAY_SURFACE, OVERLAY_BORDER, 22));
        panel.setAccessibilityPaneTitle(getString(R.string.overlay_brand_title));
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(4), dp(8));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = formText(getString(R.string.overlay_page_planting_title), 20,
                Color.rgb(23, 59, 42));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(title);
        TextView subtitle = formText(
                getString(R.string.overlay_page_planting_subtitle), 12, OVERLAY_MUTED);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView readyChip = formText(
                getString(R.string.overlay_canvas_ready), 11, OVERLAY_GREEN);
        readyChip.setGravity(Gravity.CENTER);
        readyChip.setPadding(dp(12), 0, dp(12), 0);
        readyChip.setBackground(roundedBackground(OVERLAY_MINT, 0, 15));
        readyChip.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_current_status),
                getString(R.string.overlay_canvas_ready)));
        header.addView(readyChip, new LinearLayout.LayoutParams(dp(96), dp(32)));

        Button close = compactIconButton("×", getString(R.string.overlay_close));
        close.setTextColor(Color.rgb(53, 83, 66));
        close.setBackground(roundedBackground(Color.rgb(237, 242, 236), 0, 18));
        close.setContentDescription(getString(R.string.overlay_close));
        close.setOnClickListener(view -> closeSettingsOverlay(false));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        closeParams.setMarginStart(dp(6));
        header.addView(close, closeParams);
        panel.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(roundedBackground(Color.rgb(237, 242, 236), 0, 14));
        Button plantingTab = overlayButton(getString(R.string.overlay_tab_planting));
        Button feedTab = overlayButton(getString(R.string.overlay_tab_feed));
        Button postcardTab = overlayButton(getString(R.string.overlay_tab_postcard));
        Button rewardTab = overlayButton(getString(R.string.overlay_tab_reward));
        Button returnRewardTab = overlayButton(getString(R.string.overlay_tab_return_reward));
        plantingTab.setTextSize(11);
        feedTab.setTextSize(11);
        postcardTab.setTextSize(11);
        rewardTab.setTextSize(11);
        returnRewardTab.setTextSize(11);
        plantingTab.setContentDescription(getString(R.string.overlay_tab_planting_description));
        feedTab.setContentDescription(getString(R.string.overlay_tab_feed_description));
        postcardTab.setContentDescription(getString(R.string.overlay_tab_postcard_description));
        rewardTab.setContentDescription(getString(R.string.overlay_tab_reward_description));
        returnRewardTab.setContentDescription(
                getString(R.string.overlay_tab_return_reward_description));
        LinearLayout automationTabs = new LinearLayout(this);
        automationTabs.addView(plantingTab, weightedButtonParams());
        automationTabs.addView(feedTab, weightedButtonParams());
        automationTabs.addView(postcardTab, weightedButtonParams());
        tabs.addView(automationTabs, matchWidthParams(dp(48), 0));
        LinearLayout utilityTabs = new LinearLayout(this);
        utilityTabs.addView(rewardTab, weightedButtonParams());
        utilityTabs.addView(returnRewardTab, weightedButtonParams());
        tabs.addView(utilityTabs, matchWidthParams(dp(48), dp(4)));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsParams.setMargins(dp(10), 0, dp(10), dp(8));
        panel.addView(tabs, tabsParams);

        LinearLayout plantingPage = new LinearLayout(this);
        plantingPage.setOrientation(LinearLayout.VERTICAL);
        plantingPage.setFocusableInTouchMode(true);
        LinearLayout plantingContent = new LinearLayout(this);
        plantingContent.setOrientation(LinearLayout.VERTICAL);
        plantingContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        TextView statusView = formText(getString(R.string.overlay_ready_planting), 15, OVERLAY_GREEN);
        plantingContent.addView(settingsHeroCard(
                getString(R.string.overlay_planting_hero_label),
                getString(R.string.overlay_planting_hero_title, settings.allowedFlowers().size()),
                getString(R.string.overlay_planting_hero_detail, settings.threshold()),
                OVERLAY_MINT));
        plantingContent.addView(settingsStatusCard(
                statusView,
                getString(
                        R.string.overlay_planting_summary,
                        settings.allowedFlowers().size(),
                        settings.threshold())));

        plantingContent.addView(settingsSectionTitle(
                getString(R.string.overlay_section_switch_condition), ""));
        StepperField thresholdInput = new StepperField(
                getString(R.string.overlay_threshold_short),
                getString(R.string.overlay_threshold_range),
                settings.threshold(),
                1,
                1200);
        numberInputs.add(thresholdInput.input());
        plantingContent.addView(thresholdInput);
        plantingContent.addView(settingsPresetRow(thresholdInput.input(), 100, 200, 300, 500));

        plantingContent.addView(settingsSectionTitle(
                getString(R.string.overlay_section_flower_order),
                getString(R.string.overlay_flower_order_helper)));
        FlowerOrderEditor flowerEditor = new FlowerOrderEditor(settings.allowedFlowers());
        plantingContent.addView(flowerEditor);
        plantingContent.addView(settingsHelperCard(
                getString(R.string.overlay_planting_advanced_settings), OVERLAY_SURFACE_2));
        TextView error = settingsErrorView();
        plantingContent.addView(error);

        ScrollView plantingScroll = new ScrollView(this);
        plantingScroll.setFillViewport(true);
        plantingScroll.setClipToPadding(false);
        plantingScroll.addView(plantingContent);
        plantingPage.addView(plantingScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button save = overlayButton(getString(R.string.overlay_save));
        Button toggleButton = overlayButton(getString(R.string.action_start));
        stylePrimaryButton(toggleButton);
        Runnable savePlanting = () -> {
            SettingsInput input = SettingsInput.parse(
                    thresholdInput.valueText(), flowerEditor.valueText());
            settings.save(input.threshold(), input.flowers());
        };
        save.setOnClickListener(view -> {
            dismissNumberKeyboard(plantingPage, numberInputs);
            try {
                savePlanting.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
                error.requestFocus();
            }
        });
        toggleButton.setOnClickListener(view -> {
            dismissNumberKeyboard(plantingPage, numberInputs);
            try {
                savePlanting.run();
                startAutomation();
                if (running) {
                    closeSettingsOverlay(false);
                }
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
                error.requestFocus();
            }
        });
        plantingPage.addView(settingsFooter(save, toggleButton));

        LinearLayout feedPage = new LinearLayout(this);
        feedPage.setOrientation(LinearLayout.VERTICAL);
        feedPage.setFocusableInTouchMode(true);
        LinearLayout feedContent = new LinearLayout(this);
        feedContent.setOrientation(LinearLayout.VERTICAL);
        feedContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        TextView feedStatusView = formText(
                getString(R.string.overlay_ready_feed), 15, OVERLAY_GREEN);
        feedContent.addView(settingsHeroCard(
                getString(R.string.overlay_feed_missing_label),
                getString(R.string.overlay_feed_missing_title),
                getString(R.string.overlay_feed_missing_detail),
                OVERLAY_WARNING));
        feedContent.addView(settingsStatusCard(
                feedStatusView,
                getString(
                        R.string.overlay_feed_summary,
                        settings.feedsPerSquad(),
                        settings.maxSquadSwitches(),
                        settings.nectarMinimumThreshold(),
                        settings.feedPetalLimit() - 50)));
        feedContent.addView(settingsSectionTitle(
                getString(R.string.overlay_feed_production_section), ""));

        StepperField feedsPerSquadInput = new StepperField(
                getString(R.string.overlay_feed_rounds),
                getString(R.string.overlay_feed_rounds_range),
                settings.feedsPerSquad(),
                1,
                10);
        numberInputs.add(feedsPerSquadInput.input());
        feedContent.addView(feedsPerSquadInput);
        feedContent.addView(new View(this), matchWidthParams(dp(8), 0));

        StepperField maxSquadSwitchesInput = new StepperField(
                getString(R.string.overlay_feed_switches),
                getString(R.string.overlay_feed_switches_range),
                settings.maxSquadSwitches(),
                0,
                120);
        numberInputs.add(maxSquadSwitchesInput.input());
        feedContent.addView(maxSquadSwitchesInput);
        feedContent.addView(new View(this), matchWidthParams(dp(8), 0));

        StepperField nectarMinimumInput = new StepperField(
                getString(R.string.overlay_feed_nectar_minimum),
                getString(R.string.overlay_feed_nectar_range),
                settings.nectarMinimumThreshold(),
                0,
                1200);
        numberInputs.add(nectarMinimumInput.input());
        feedContent.addView(nectarMinimumInput);
        feedContent.addView(new View(this), matchWidthParams(dp(8), 0));

        StepperField petalLimitInput = new StepperField(
                getString(R.string.overlay_feed_petal_limit),
                getString(R.string.overlay_feed_petal_limit_range),
                settings.feedPetalLimit(),
                300,
                1200,
                50);
        numberInputs.add(petalLimitInput.input());
        feedContent.addView(petalLimitInput);
        feedContent.addView(settingsHelperCard(
                getString(R.string.overlay_feed_stop_line, settings.feedPetalLimit() - 50),
                OVERLAY_WARNING));

        feedContent.addView(settingsSectionTitle(
                getString(R.string.overlay_feed_order_title),
                getString(R.string.overlay_feed_order_helper)));
        FlowerOrderEditor feedFlowerEditor = new FlowerOrderEditor(settings.allowedFlowers(), true);
        feedContent.addView(feedFlowerEditor);
        feedContent.addView(settingsSectionTitle(
                getString(R.string.overlay_feed_safety_section), ""));
        feedContent.addView(settingsHelperCard(
                getString(R.string.overlay_feed_safety), OVERLAY_CREAM));
        TextView feedError = settingsErrorView();
        feedContent.addView(feedError);

        ScrollView feedScroll = new ScrollView(this);
        feedScroll.setFillViewport(true);
        feedScroll.setClipToPadding(false);
        feedScroll.addView(feedContent);
        feedPage.addView(feedScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button feedSave = overlayButton(getString(R.string.overlay_save));
        Button feedStart = overlayButton(getString(R.string.overlay_feed_start));
        stylePrimaryButton(feedStart);
        Runnable saveFeed = () -> {
            FeedSettingsInput input = FeedSettingsInput.parse(
                    feedsPerSquadInput.valueText(),
                    maxSquadSwitchesInput.valueText(),
                    nectarMinimumInput.valueText(),
                    petalLimitInput.valueText());
            SettingsInput flowerInput = SettingsInput.parse(
                    String.valueOf(settings.threshold()), feedFlowerEditor.valueText());
            settings.save(settings.threshold(), flowerInput.flowers());
            settings.saveFeedSettings(
                    input.feedsPerSquad(),
                    input.maxSquadSwitches(),
                    input.nectarMinimumThreshold(),
                    input.petalLimit());
        };
        feedSave.setOnClickListener(view -> {
            dismissNumberKeyboard(feedPage, numberInputs);
            try {
                saveFeed.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                feedError.setText(exception.getMessage());
                feedError.requestFocus();
            }
        });
        feedStart.setOnClickListener(view -> {
            dismissNumberKeyboard(feedPage, numberInputs);
            try {
                saveFeed.run();
                FeedSettingsInput input = new FeedSettingsInput(
                        settings.feedsPerSquad(),
                        settings.maxSquadSwitches(),
                        settings.nectarMinimumThreshold(),
                        settings.feedPetalLimit());
                closeSettingsOverlay(false);
                handler.postDelayed(() -> startFeedAutomation(input), 180L);
            } catch (IllegalArgumentException exception) {
                feedError.setText(exception.getMessage());
                feedError.requestFocus();
            }
        });
        feedPage.addView(settingsFooter(feedSave, feedStart));
        feedPage.setVisibility(View.GONE);

        LinearLayout postcardPage = new LinearLayout(this);
        postcardPage.setOrientation(LinearLayout.VERTICAL);
        postcardPage.setFocusableInTouchMode(true);
        LinearLayout postcardContent = new LinearLayout(this);
        postcardContent.setOrientation(LinearLayout.VERTICAL);
        postcardContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        postcardContent.addView(settingsHeroCard(
                getString(R.string.overlay_postcard_plan_label),
                getString(R.string.overlay_postcard_plan_title, settings.postcardCollectionLimit()),
                getString(R.string.overlay_postcard_plan_detail),
                OVERLAY_MINT));
        postcardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_postcard_settings_section), ""));

        StepperField postcardLimitInput = new StepperField(
                getString(R.string.overlay_collection_short),
                getString(R.string.overlay_collection_range),
                settings.postcardCollectionLimit(),
                0,
                15);
        numberInputs.add(postcardLimitInput.input());
        postcardContent.addView(postcardLimitInput);

        postcardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_pikmin_short),
                getString(R.string.overlay_pikmin_range)));
        NumberChoiceSelector postcardPikminSelector = new NumberChoiceSelector(
                getString(R.string.overlay_pikmin_short),
                settings.postcardPikminCount(),
                1,
                5);
        postcardContent.addView(postcardPikminSelector);

        postcardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_postcard_petal_color_filter),
                getString(R.string.overlay_postcard_petal_color_filter_helper)));
        PostcardPotSelector postcardPotSelector = new PostcardPotSelector(
                settings.postcardPetalPotName());
        postcardContent.addView(postcardPotSelector);
        LinearLayout.LayoutParams requirementParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        requirementParams.topMargin = dp(8);
        postcardContent.addView(settingsHelperCard(
                getString(R.string.overlay_postcard_petal_requirement), OVERLAY_CREAM),
                requirementParams);
        postcardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_postcard_stop_conditions), ""));
        postcardContent.addView(settingsHelperCard(
                getString(R.string.overlay_postcard_stop_condition_items), OVERLAY_SURFACE_2));

        TextView postcardError = settingsErrorView();
        postcardContent.addView(postcardError);

        ScrollView postcardScroll = new ScrollView(this);
        postcardScroll.setFillViewport(true);
        postcardScroll.setClipToPadding(false);
        postcardScroll.addView(postcardContent);
        postcardPage.addView(postcardScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button postcardSave = overlayButton(getString(R.string.overlay_save));
        Button postcardToggle = overlayButton(getString(R.string.overlay_postcard_start));
        stylePrimaryButton(postcardToggle);
        Runnable savePostcard = () -> {
            PostcardSettingsInput input = PostcardSettingsInput.parse(
                    postcardLimitInput.valueText(),
                    postcardPotSelector.value(),
                    postcardPikminSelector.valueText());
            settings.savePostcardSettings(
                    input.collectionLimit(),
                    input.petalPotName(),
                    input.pikminCount());
        };
        postcardSave.setOnClickListener(view -> {
            dismissNumberKeyboard(postcardPage, numberInputs);
            try {
                savePostcard.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                postcardError.setText(exception.getMessage());
                postcardError.requestFocus();
            }
        });
        postcardToggle.setOnClickListener(view -> {
            dismissNumberKeyboard(postcardPage, numberInputs);
            try {
                PostcardSettingsInput input = PostcardSettingsInput.parse(
                        postcardLimitInput.valueText(),
                        postcardPotSelector.value(),
                        postcardPikminSelector.valueText());
                if (input.collectionLimit() == 0) {
                    throw new IllegalArgumentException(
                            getString(R.string.overlay_postcard_zero_remaining));
                }
                settings.savePostcardSettings(
                        input.collectionLimit(),
                        input.petalPotName(),
                        input.pikminCount());
                startPostcardAutomation(
                        input.collectionLimit(),
                        input.petalPotName(),
                        input.pikminCount());
                if (running) {
                    closeSettingsOverlay(false);
                }
            } catch (IllegalArgumentException exception) {
                postcardError.setText(exception.getMessage());
                postcardError.requestFocus();
            }
        });
        postcardPage.addView(settingsFooter(postcardSave, postcardToggle));
        postcardPage.setVisibility(View.GONE);

        LinearLayout rewardPage = new LinearLayout(this);
        rewardPage.setOrientation(LinearLayout.VERTICAL);
        rewardPage.setFocusableInTouchMode(true);
        LinearLayout rewardContent = new LinearLayout(this);
        rewardContent.setOrientation(LinearLayout.VERTICAL);
        rewardContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        TextView rewardStatusView = formText(
                getString(R.string.overlay_reward_status_unselected), 15, OVERLAY_GREEN);
        rewardContent.addView(settingsHeroCard(
                getString(R.string.overlay_reward_missing_label),
                getString(R.string.overlay_reward_missing_title),
                getString(R.string.overlay_reward_missing_detail),
                OVERLAY_WARNING));
        rewardContent.addView(settingsStatusCard(
                rewardStatusView,
                getString(R.string.overlay_reward_status_summary)));

        StepperField rewardCountInput = new StepperField(
                getString(R.string.overlay_reward_count_section),
                getString(R.string.overlay_reward_count_helper),
                settings.expeditionDispatchCount(),
                1,
                99);
        numberInputs.add(rewardCountInput.input());
        rewardContent.addView(rewardCountInput, matchWidthParams(dp(72), dp(4)));

        rewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_reward_dispatch_section), ""));
        rewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_reward_target_section),
                getString(R.string.overlay_reward_target_helper)));
        DispatchTargetSelector rewardTargetSelector = new DispatchTargetSelector(
                settings.expeditionTargetMode());
        rewardContent.addView(rewardTargetSelector);

        rewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_reward_method_section), ""));
        DispatchMethodSelector rewardMethodSelector = new DispatchMethodSelector(
                settings.dispatchSelectionMethod());
        rewardContent.addView(rewardMethodSelector);

        rewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_reward_pikmin_section), ""));
        DispatchPikminTypeSelector rewardPikminSelector = new DispatchPikminTypeSelector(
                settings.dispatchPikminType());
        rewardContent.addView(rewardPikminSelector);
        rewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_reward_protection_section), ""));
        rewardContent.addView(settingsHelperCard(
                getString(R.string.overlay_reward_safety_items), OVERLAY_SURFACE_2));

        TextView rewardError = settingsErrorView();
        rewardContent.addView(rewardError);

        ScrollView rewardScroll = new ScrollView(this);
        rewardScroll.setFillViewport(true);
        rewardScroll.setClipToPadding(false);
        rewardScroll.addView(rewardContent);
        rewardPage.addView(rewardScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button rewardSave = overlayButton(getString(R.string.overlay_save));
        Button rewardStart = overlayButton(getString(R.string.overlay_reward_start));
        stylePrimaryButton(rewardStart);
        Runnable saveReward = () -> {
            int count;
            try {
                count = Integer.parseInt(rewardCountInput.valueText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        getString(R.string.overlay_reward_count_invalid));
            }
            if (count < 1 || count > 99) {
                throw new IllegalArgumentException(
                        getString(R.string.overlay_reward_count_invalid));
            }
            settings.saveExpeditionDispatchSettings(
                    count,
                    rewardTargetSelector.value(),
                    rewardMethodSelector.value(),
                    rewardPikminSelector.value());
        };
        rewardSave.setOnClickListener(view -> {
            dismissNumberKeyboard(rewardPage, numberInputs);
            try {
                saveReward.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                rewardError.setText(exception.getMessage());
                rewardError.requestFocus();
            }
        });
        rewardStart.setOnClickListener(view -> {
            dismissNumberKeyboard(rewardPage, numberInputs);
            try {
                saveReward.run();
                int count = settings.expeditionDispatchCount();
                ExpeditionTargetMode targetMode = rewardTargetSelector.value();
                DispatchSelectionMethod method = rewardMethodSelector.value();
                DispatchPikminType type = rewardPikminSelector.value();
                closeSettingsOverlay(false);
                handler.postDelayed(() -> startExpeditionDispatch(
                        count, targetMode, method, type), 180L);
            } catch (IllegalArgumentException exception) {
                rewardError.setText(exception.getMessage());
                rewardError.requestFocus();
            }
        });
        rewardPage.addView(settingsFooter(rewardSave, rewardStart));
        rewardPage.setVisibility(View.GONE);

        LinearLayout returnRewardPage = new LinearLayout(this);
        returnRewardPage.setOrientation(LinearLayout.VERTICAL);
        returnRewardPage.setFocusableInTouchMode(true);
        LinearLayout returnRewardContent = new LinearLayout(this);
        returnRewardContent.setOrientation(LinearLayout.VERTICAL);
        returnRewardContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        TextView returnRewardStatusView = formText(
                getString(R.string.overlay_reward_status_unselected), 15, OVERLAY_GREEN);
        LinearLayout returnHero = settingsHeroCard(
                getString(R.string.overlay_return_reward_hero_label),
                getString(R.string.overlay_return_reward_hero_title),
                getString(R.string.overlay_return_reward_hero_detail),
                OVERLAY_INFO);
        Button returnSelectArea = overlayButton(
                getString(R.string.overlay_return_reward_select_area));
        LinearLayout.LayoutParams returnSelectAreaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        returnSelectAreaParams.topMargin = dp(12);
        returnHero.addView(returnSelectArea, returnSelectAreaParams);
        returnRewardContent.addView(returnHero);
        returnRewardContent.addView(settingsStatusCard(
                returnRewardStatusView,
                getString(R.string.overlay_return_reward_status_summary)));
        returnRewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_return_reward_postcard_section),
                getString(R.string.overlay_return_reward_postcard_helper)));
        ReturnPostcardActionSelector returnPostcardAction =
                new ReturnPostcardActionSelector(settings.receiveReturnedPostcards());
        returnRewardContent.addView(returnPostcardAction);
        CheckBox continueNectarWarning = new CheckBox(this);
        continueNectarWarning.setText(
                R.string.overlay_return_reward_nectar_warning_continue);
        continueNectarWarning.setTextSize(14);
        continueNectarWarning.setTextColor(OVERLAY_GREEN);
        continueNectarWarning.setPadding(0, dp(8), 0, dp(2));
        continueNectarWarning.setMinHeight(dp(48));
        continueNectarWarning.setContentDescription(getString(
                R.string.overlay_return_reward_nectar_warning_continue));
        continueNectarWarning.setChecked(settings.continueReturnRewardOnNectarWarning());
        returnRewardContent.addView(continueNectarWarning);
        returnRewardContent.addView(settingsHelperCard(
                getString(R.string.overlay_return_reward_nectar_warning_helper), OVERLAY_CREAM));
        returnRewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_return_reward_protection_section), ""));
        returnRewardContent.addView(settingsHelperCard(
                getString(R.string.overlay_return_reward_safety), OVERLAY_CREAM));

        ScrollView returnRewardScroll = new ScrollView(this);
        returnRewardScroll.setFillViewport(true);
        returnRewardScroll.setClipToPadding(false);
        returnRewardScroll.addView(returnRewardContent);
        returnRewardPage.addView(returnRewardScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button returnRewardSave = overlayButton(getString(R.string.overlay_save));
        Button returnRewardStart = overlayButton(
                getString(R.string.overlay_return_reward_start));
        stylePrimaryButton(returnRewardStart);
        Runnable saveReturnReward = () -> settings.saveReturnRewardSettings(
                returnPostcardAction.receive(), continueNectarWarning.isChecked());
        returnSelectArea.setOnClickListener(view -> {
            saveReturnReward.run();
            boolean receive = returnPostcardAction.receive();
            boolean continueOnNectarWarning = continueNectarWarning.isChecked();
            closeSettingsOverlay(false);
            handler.postDelayed(
                    () -> startReturnRewardCollection(receive, continueOnNectarWarning), 180L);
        });
        returnRewardSave.setOnClickListener(view -> {
            saveReturnReward.run();
            closeSettingsOverlay(true);
        });
        returnRewardStart.setOnClickListener(view -> {
            saveReturnReward.run();
            boolean receive = returnPostcardAction.receive();
            boolean continueOnNectarWarning = continueNectarWarning.isChecked();
            closeSettingsOverlay(false);
            handler.postDelayed(
                    () -> startReturnRewardCollection(receive, continueOnNectarWarning), 180L);
        });
        returnRewardPage.addView(settingsFooter(returnRewardSave, returnRewardStart));
        returnRewardPage.setVisibility(View.GONE);


        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panel.addView(plantingPage, pageParams);
        panel.addView(feedPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(postcardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(rewardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(returnRewardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        plantingTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.VISIBLE);
            feedPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(plantingTab, feedTab, postcardTab, rewardTab, returnRewardTab);
            status = statusView;
            toggle = toggleButton;
            setSettingsHeader(title, subtitle, readyChip,
                    R.string.overlay_page_planting_title,
                    R.string.overlay_page_planting_subtitle,
                    R.string.overlay_canvas_ready,
                    false);
            plantingPage.requestFocus();
        });
        feedTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            feedPage.setVisibility(View.VISIBLE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(feedTab, plantingTab, postcardTab, rewardTab, returnRewardTab);
            status = feedStatusView;
            toggle = feedStart;
            setSettingsHeader(title, subtitle, readyChip,
                    R.string.overlay_page_feed_title,
                    R.string.overlay_page_feed_subtitle,
                    R.string.overlay_canvas_action,
                    true);
            feedPage.requestFocus();
        });
        postcardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            feedPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.VISIBLE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(postcardTab, plantingTab, feedTab, rewardTab, returnRewardTab);
            status = null;
            toggle = postcardToggle;
            setSettingsHeader(title, subtitle, readyChip,
                    R.string.overlay_page_postcard_title,
                    R.string.overlay_page_postcard_subtitle,
                    R.string.overlay_canvas_idle,
                    false);
            postcardPage.requestFocus();
        });
        rewardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            feedPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.VISIBLE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(rewardTab, plantingTab, feedTab, postcardTab, returnRewardTab);
            status = rewardStatusView;
            toggle = rewardStart;
            setSettingsHeader(title, subtitle, readyChip,
                    R.string.overlay_page_dispatch_title,
                    R.string.overlay_page_dispatch_subtitle,
                    R.string.overlay_canvas_action,
                    true);
            rewardPage.requestFocus();
        });
        returnRewardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            feedPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.VISIBLE);
            setSelectedTab(returnRewardTab, plantingTab, feedTab, postcardTab, rewardTab);
            status = returnRewardStatusView;
            toggle = returnRewardStart;
            setSettingsHeader(title, subtitle, readyChip,
                    R.string.overlay_page_return_title,
                    R.string.overlay_page_return_subtitle,
                    R.string.overlay_canvas_no_area,
                    true);
            returnRewardPage.requestFocus();
        });
        setSelectedTab(plantingTab, feedTab, postcardTab, rewardTab, returnRewardTab);

        WindowManager.LayoutParams params = settingsOverlayLayoutParams();
        if (!safeAddOverlayView(panel, params, "settings")) {
            return;
        }
        settingsOverlay = panel;
        status = statusView;
        toggle = toggleButton;
        overlay.setVisibility(View.GONE);
        title.setFocusable(true);
        title.requestFocus();
    }

    private WindowManager.LayoutParams settingsOverlayLayoutParams() {
        int width = Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(16));
        int height = Math.min(dp(720), getResources().getDisplayMetrics().heightPixels - dp(48));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.18f;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        return params;
    }

    private void dismissNumberKeyboardOnOutsideTap(
            View overlayRoot,
            List<EditText> numberInputs,
            MotionEvent event) {
        View focused = overlayRoot.findFocus();
        boolean numberFieldFocused = focused instanceof EditText
                && numberInputs.contains(focused);
        boolean touchedNumberField = false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && numberFieldFocused) {
            int x = Math.round(event.getRawX());
            int y = Math.round(event.getRawY());
            Rect bounds = new Rect();
            for (EditText input : numberInputs) {
                if (input.getGlobalVisibleRect(bounds) && bounds.contains(x, y)) {
                    touchedNumberField = true;
                    break;
                }
            }
        }
        if (OverlayWindowPolicy.shouldDismissNumberKeyboard(
                event.getActionMasked() == MotionEvent.ACTION_DOWN,
                numberFieldFocused,
                touchedNumberField)) {
            dismissNumberKeyboard(overlayRoot, numberInputs);
        }
    }

    private void dismissNumberKeyboard(View root, List<EditText> numberInputs) {
        View focused = root.findFocus();
        if (!(focused instanceof EditText input) || !numberInputs.contains(input)) {
            return;
        }
        dismissNumberKeyboard(input);
    }

    private void dismissNumberKeyboard(EditText input) {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    /** 關閉設定卡片、收起鍵盤並恢復主懸浮窗。 */
    private void closeSettingsOverlay(boolean saved) {
        View closing = settingsOverlay;
        if (closing == null) {
            return;
        }
        settingsOverlay = null;
        status = null;
        toggle = null;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(closing.getWindowToken(), 0);
        safeRemoveOverlayView(closing, "settings");
        if (overlay != null) {
            overlay.setVisibility(settings != null && settings.overlayVisible()
                    ? View.VISIBLE
                    : View.GONE);
            overlay.requestFocus();
        }
        if (running && plantingNoticeStatus != null) {
            showFloatingNotice(plantingNoticeStatus.visibleText(), false);
        } else if (saved) {
            showFloatingNotice(getString(R.string.overlay_saved));
        }
    }

    private LinearLayout settingsStatusCard(TextView statusView, String summary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBackground(OVERLAY_MINT, OVERLAY_BORDER, 16));

        TextView label = formText(getString(R.string.overlay_current_status), 11, OVERLAY_MUTED);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(label);
        statusView.setTextSize(15);
        statusView.setTextColor(Color.rgb(31, 72, 48));
        statusView.setTypeface(null, android.graphics.Typeface.BOLD);
        statusView.setPadding(0, dp(3), 0, 0);
        statusView.setContentDescription(getString(R.string.overlay_status_label));
        statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(statusView);
        TextView detail = formText(summary, 12, OVERLAY_MUTED);
        detail.setPadding(0, dp(5), 0, 0);
        card.addView(detail);
        return card;
    }

    private LinearLayout settingsHeroCard(String label, String title, String detail,
            int backgroundColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundedBackground(backgroundColor, 0, 16));
        TextView eyebrow = formText(label, 11, OVERLAY_MUTED);
        card.addView(eyebrow);
        TextView headline = formText(title, 18, Color.rgb(24, 60, 49));
        headline.setTypeface(null, android.graphics.Typeface.BOLD);
        headline.setPadding(0, dp(3), 0, 0);
        card.addView(headline);
        TextView body = formText(detail, 12, OVERLAY_MUTED);
        body.setPadding(0, dp(5), 0, 0);
        card.addView(body);
        return card;
    }

    private void setSettingsHeader(TextView title, TextView subtitle, TextView chip,
            int titleResource, int subtitleResource, int chipResource, boolean warning) {
        title.setText(titleResource);
        subtitle.setText(subtitleResource);
        chip.setText(chipResource);
        chip.setTextColor(warning ? OVERLAY_RECOGNIZING : OVERLAY_GREEN);
        chip.setBackground(roundedBackground(warning ? OVERLAY_CREAM : OVERLAY_MINT, 0, 15));
        chip.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_current_status),
                getString(chipResource)));
    }

    private TextView settingsSectionTitle(String title, String helper) {
        TextView heading = formText(title, 15, Color.rgb(30, 65, 35));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, helper.isBlank() ? dp(7) : dp(2));
        if (!helper.isBlank()) {
            heading.setText(getString(R.string.overlay_section_with_helper, title, helper));
            heading.setLineSpacing(0, 1.2f);
        }
        return heading;
    }

    private TextView settingsErrorView() {
        TextView error = formText("", 13, OVERLAY_ACCENT);
        error.setFocusable(true);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        error.setMinHeight(dp(28));
        error.setPadding(dp(2), dp(6), dp(2), 0);
        return error;
    }

    private LinearLayout settingsFooter(Button secondary, Button primary) {
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(10), dp(12), dp(6));
        footer.setBackground(roundedBackground(OVERLAY_SURFACE, 0, 14));
        footer.addView(secondary, new LinearLayout.LayoutParams(0, dp(48), 0.8f));
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(48), 1.7f);
        primaryParams.setMarginStart(dp(8));
        footer.addView(primary, primaryParams);
        return footer;
    }

    private TextView settingsHelperCard(String text, int backgroundColor) {
        TextView helper = formText(text, 12, OVERLAY_MUTED);
        helper.setPadding(dp(12), dp(9), dp(12), dp(9));
        helper.setBackground(roundedBackground(backgroundColor, 0, 12));
        return helper;
    }

    private LinearLayout settingsPresetRow(EditText input, int... values) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(7), 0, 0);
        for (int value : values) {
            Button preset = overlayButton(String.valueOf(value));
            preset.setTextSize(12);
            preset.setMinHeight(dp(40));
            preset.setOnClickListener(view -> input.setText(String.valueOf(value)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
            params.setMarginEnd(dp(5));
            row.addView(preset, params);
        }
        return row;
    }

    /** 建立數字欄位並統一設定輸入尺寸。 */
    private EditText numberField(int value) {
        EditText field = new EditText(this);
        field.setText(String.valueOf(value));
        field.setTextSize(16);
        field.setTextColor(Color.rgb(32, 48, 38));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setMinHeight(dp(50));
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setOnEditorActionListener((view, actionId, event) -> {
            dismissNumberKeyboard(field);
            return true;
        });
        field.setSelectAllOnFocus(true);
        return field;
    }

    /** 建立明信片分頁的單行花盆名稱欄位。 */
    private EditText singleLineTextField(String value, int hintResource) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(hintResource);
        field.setTextSize(16);
        field.setTextColor(Color.rgb(32, 48, 38));
        field.setHintTextColor(OVERLAY_MUTED);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setMinHeight(dp(50));
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setSelectAllOnFocus(true);
        return field;
    }

    /** 建立多行花朵輸入欄位，支援換行與逗號分隔。 */
    private EditText flowerInput(String value) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(R.string.overlay_manual_flowers_hint);
        field.setContentDescription(getString(R.string.overlay_manual_flowers_label));
        field.setTextSize(16);
        field.setTextColor(Color.rgb(32, 48, 38));
        field.setHintTextColor(OVERLAY_MUTED);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setSingleLine(false);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.START);
        return field;
    }

    private static int parseBoundedInt(String value, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim())));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private LinearLayout.LayoutParams settingsMatchParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(topMarginDp), 0, 0);
        return params;
    }

    /** 建立設定表單的欄位標籤。 */
    private TextView formLabel(int stringResource) {
        TextView label = formText(getString(stringResource), 15, Color.rgb(30, 65, 35));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
    }

    /** 建立設定表單文字並統一行距。 */
    private TextView formText(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    /** 建立設定操作按鈕的等寬版面參數。 */
    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMarginStart(dp(6));
        return params;
    }

    private LinearLayout.LayoutParams matchWidthParams(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.topMargin = topMargin;
        return params;
    }

    /** 停止流程後保留錯誤卡，讓原因不會在短暫提示後消失。 */
    private void stopWithError(String message) {
        if (automationMode == AutomationMode.PLANTING) {
            logPlantingSwitch("stopped", false);
            Log.i(TAG, "PLANTING_SWITCH reason=" + message);
        }
        AutomationMode stoppedMode = automationMode;
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
        pause(message);
        setRunStatus(completedMode, OverlayRunStatus.Kind.SUCCESS, message, "");
        OverlayRunStatus completedStatus = plantingNoticeStatus;
        handler.postDelayed(() -> {
            if (!running && plantingNoticeStatus == completedStatus) {
                clearPlantingNotice();
            }
        }, 2600L);
    }

    /** 停止所有掃描排程並將流程狀態重設為可重新開始。 */
    private void pause(String message) {
        cancelActiveOcrTransaction();
        consecutiveOcrEngineFailures = 0;
        runGeneration++;
        clearScreenshotPipeline();
        running = false;
        automationMode = AutomationMode.NONE;
        expeditionDispatchSession = null;
        dispatchCurrentItemKind = null;
        dispatchColorSelected = false;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchTextConfirmed = false;
        dispatchSearchOpenAttempts = 0;
        dispatchSearchInputAttempts = 0;
        dispatchSearchKeyboardGuard.reset();
        dispatchPikminTapIndex = 0;
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
        feedSettings = new FeedSettingsInput(6, 0, 40, 1200);
        feedStep = FeedStep.WAITING_GAME_READY;
        feedTargetFlower = "";
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
        if (overlay != null
                && settingsOverlay == null
                && settings != null
                && settings.overlayVisible()) {
            overlay.setVisibility(View.VISIBLE);
        }
        if (toggle != null) {
            toggle.setText(R.string.action_start);
            toggle.setContentDescription(getString(R.string.action_start));
        }
        if (overlay != null) {
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_icon_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
        setStatus(message);
    }

    /** 將狀態同步到設定卡片；卡片關閉時不建立額外視窗。 */
    private void setStatus(String message) {
        if (status != null) {
            status.setText(message);
            status.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_status_label),
                    message));
        }
    }

    /** 建立懸浮窗按鈕，保留至少 32dp 觸控區域。 */
    private Button overlayButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(OVERLAY_GREEN);
        button.setAllCaps(false);
        button.setMinWidth(dp(32));
        button.setMinimumWidth(dp(32));
        button.setMinHeight(dp(32));
        button.setMinimumHeight(dp(32));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setStateListAnimator(null);
        button.setBackground(roundedBackground(Color.rgb(237, 247, 239), OVERLAY_BORDER, 10));
        return button;
    }

    /** 主要動作使用高對比實心樣式，不以顏色作為唯一狀態提示。 */
    private void stylePrimaryButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackground(roundedBackground(OVERLAY_GREEN, 0, 12));
    }

    /** 以可被無障礙服務讀取的 selected 狀態呈現目前分頁。 */
    private void setSelectedTab(Button active, Button... tabs) {
        active.setSelected(true);
        active.setStateDescription(getString(R.string.overlay_tab_selected));
        active.setTextColor(OVERLAY_GREEN);
        active.setBackground(roundedBackground(Color.WHITE, OVERLAY_BORDER, 11));
        for (Button tab : tabs) {
            tab.setSelected(false);
            tab.setStateDescription(getString(R.string.overlay_tab_not_selected));
            tab.setTextColor(OVERLAY_MUTED);
            tab.setBackground(roundedBackground(Color.TRANSPARENT, 0, 11));
        }
    }

    /** 建立懸浮窗與設定卡片共用的圓角背景。 */
    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    /** 在圖示旁顯示小字提示，不攔截遊戲觸控。 */
    private void showFloatingNotice(String message) {
        showFloatingNotice(message, true);
    }

    private void showFloatingNotice(String message, boolean autoHide) {
        if (message == null
                || message.trim().isEmpty()
                || windowManager == null
                || overlay == null
                || overlay.getVisibility() != View.VISIBLE) {
            return;
        }
        hideFloatingNotice();

        TextView notice = formText(message, 11, Color.WHITE);
        notice.setMaxLines(3);
        notice.setMaxWidth(dp(NOTICE_MAX_WIDTH_DP));
        notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setIncludeFontPadding(false);
        notice.setLineSpacing(0, 0.95f);
        notice.setPadding(dp(7), dp(3), dp(7), dp(3));
        notice.setBackground(roundedBackground(OVERLAY_GREEN, 0, 8));
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.setContentDescription(message);

        notice.measure(
                View.MeasureSpec.makeMeasureSpec(dp(NOTICE_MAX_WIDTH_DP), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                notice.getMeasuredWidth(),
                notice.getMeasuredHeight(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        positionFloatingNotice(params);
        if (!safeAddOverlayView(notice, params, "notice")) {
            return;
        }
        noticeOverlay = notice;
        noticeParams = params;
        notice.announceForAccessibility(message);
        if (autoHide) {
            handler.postDelayed(hideFloatingNoticeTask, 2600);
        }
    }

    /** 將緊縮狀態文字放在 ICON 左右同一水平列。 */
    private void positionFloatingNotice(WindowManager.LayoutParams params) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.x = OverlayWindowPolicy.horizontalNoticeX(
                overlayParams.x,
                dp(OVERLAY_SIZE_DP),
                params.width,
                metrics.widthPixels,
                dp(NOTICE_GAP_DP),
                dp(NOTICE_EDGE_DP));
        params.y = OverlayWindowPolicy.centeredNoticeY(
                overlayParams.y,
                dp(OVERLAY_SIZE_DP),
                params.height,
                metrics.heightPixels,
                dp(NOTICE_EDGE_DP));
    }

    /** 拖曳 ICON 時讓狀態文字維持水平相鄰。 */
    private void updateFloatingNoticePosition() {
        if (noticeOverlay == null || noticeParams == null || !noticeOverlay.isAttachedToWindow()) {
            return;
        }
        positionFloatingNotice(noticeParams);
        try {
            windowManager.updateViewLayout(noticeOverlay, noticeParams);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to move notice overlay", exception);
        }
    }

    /** 移除目前提示，避免提示文字出現在後續 OCR 截圖中。 */
    private void hideFloatingNotice() {
        handler.removeCallbacks(hideFloatingNoticeTask);
        safeRemoveOverlayView(noticeOverlay, "notice");
        noticeOverlay = null;
        noticeParams = null;
    }


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
        boolean changed = plantingNoticeStatus == null
                || !plantingNoticeStatus.accessibilityText().equals(next.accessibilityText());
        plantingNoticeStatus = next;
        renderOverlayStatus(changed);
        if (changed) {
            showFloatingNotice(next.visibleText(), false);
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

    private void renderOverlayStatus(boolean announce) {
        if (overlay == null) {
            return;
        }
        // 只更新無障礙狀態，不用邊框、陰影或深色容器暗示流程狀態。
        overlay.setBackground(null);
        overlay.setElevation(0);
        String description = plantingNoticeStatus == null
                ? getString(running
                        ? R.string.overlay_stop_description
                        : R.string.overlay_icon_description)
                : plantingNoticeStatus.accessibilityText();
        overlay.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                description,
                getString(R.string.overlay_icon_move_hint)));
        if (announce && overlay.isAttachedToWindow()) {
            overlay.announceForAccessibility(description);
        }
    }

    private int runStatusTone(OverlayRunStatus.Kind kind) {
        return switch (kind) {
            case SEARCHING -> OVERLAY_SEARCH;
            case RECOGNIZING -> OVERLAY_RECOGNIZING;
            case ERROR -> OVERLAY_WARNING;
            case IDLE -> OVERLAY_MUTED;
            case SUCCESS -> OVERLAY_GREEN;
        };
    }

    /** 停止自動化時清除狀態，並把收合列恢復成待命狀態。 */
    private void clearPlantingNotice() {
        plantingNoticeStatus = null;
        hideFloatingNotice();
        renderOverlayStatus(false);
    }

    /** WindowManager 失敗時保留服務程序，讓使用者仍可回到主畫面修復設定。 */
    private boolean safeAddOverlayView(
            View view, WindowManager.LayoutParams params, String windowName) {
        try {
            windowManager.addView(view, params);
            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to add " + windowName + " overlay", exception);
            return false;
        }
    }

    /** 重連與銷毀可能交錯；嘗試移除已登記視窗並吸收競態例外。 */
    private void safeRemoveOverlayView(View view, String windowName) {
        if (!OverlayWindowPolicy.shouldAttemptRemoval(view != null, windowManager != null)) {
            return;
        }
        try {
            windowManager.removeViewImmediate(view);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove " + windowName + " overlay", exception);
        }
    }

    /** 將 dp 轉成目前螢幕的像素。 */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 回程明信片處理方式；刪除只能由使用者在此明確選取。 */
    private final class ReturnPostcardActionSelector extends LinearLayout {
        private final Button receive;
        private final Button discard;
        private boolean receiveSelected;

        ReturnPostcardActionSelector(boolean initialReceive) {
            super(PetalAccessibilityService.this);
            setGravity(Gravity.CENTER_VERTICAL);
            receiveSelected = initialReceive;
            receive = optionButton(R.string.overlay_return_reward_receive,
                    () -> select(true));
            discard = optionButton(R.string.overlay_return_reward_discard,
                    () -> select(false));
            addView(receive, dispatchOptionParams(false));
            addView(discard, dispatchOptionParams(true));
            refresh();
        }

        boolean receive() {
            return receiveSelected;
        }

        private void select(boolean value) {
            receiveSelected = value;
            refresh();
        }

        private void refresh() {
            styleDispatchOption(receive, receiveSelected);
            styleDispatchOption(discard, !receiveSelected);
        }
    }

    /** 派遣目標單選：水果、花盆或兩者。 */
    private final class DispatchTargetSelector extends LinearLayout {
        private final Button fruit;
        private final Button pot;
        private final Button both;
        private ExpeditionTargetMode selected;

        DispatchTargetSelector(ExpeditionTargetMode initial) {
            super(PetalAccessibilityService.this);
            setGravity(Gravity.CENTER_VERTICAL);
            selected = initial == null ? ExpeditionTargetMode.FRUIT_AND_POT : initial;
            fruit = optionButton(R.string.overlay_reward_target_fruit,
                    () -> select(ExpeditionTargetMode.FRUIT));
            pot = optionButton(R.string.overlay_reward_target_pot,
                    () -> select(ExpeditionTargetMode.POT));
            both = optionButton(R.string.overlay_reward_target_both,
                    () -> select(ExpeditionTargetMode.FRUIT_AND_POT));
            addView(fruit, dispatchOptionParams(false));
            addView(pot, dispatchOptionParams(true));
            addView(both, dispatchOptionParams(true));
            refresh();
        }

        ExpeditionTargetMode value() {
            return selected;
        }

        private void select(ExpeditionTargetMode value) {
            selected = value;
            refresh();
        }

        private void refresh() {
            styleDispatchOption(fruit, selected == ExpeditionTargetMode.FRUIT);
            styleDispatchOption(pot, selected == ExpeditionTargetMode.POT);
            styleDispatchOption(both, selected == ExpeditionTargetMode.FRUIT_AND_POT);
        }
    }

    /** 派遣隊伍的建立方式單選。 */
    private final class DispatchMethodSelector extends LinearLayout {
        private final Button automatic;
        private final Button drag;
        private DispatchSelectionMethod selected;

        DispatchMethodSelector(DispatchSelectionMethod initial) {
            super(PetalAccessibilityService.this);
            setGravity(Gravity.CENTER_VERTICAL);
            selected = initial == null ? DispatchSelectionMethod.AUTO : initial;
            automatic = optionButton(R.string.overlay_reward_method_auto,
                    () -> select(DispatchSelectionMethod.AUTO));
            drag = optionButton(R.string.overlay_reward_method_drag,
                    () -> select(DispatchSelectionMethod.DRAG_12));
            addView(automatic, dispatchOptionParams(false));
            addView(drag, dispatchOptionParams(true));
            refresh();
        }

        DispatchSelectionMethod value() {
            return selected;
        }

        private void select(DispatchSelectionMethod value) {
            selected = value;
            refresh();
        }

        private void refresh() {
            styleDispatchOption(automatic, selected == DispatchSelectionMethod.AUTO);
            styleDispatchOption(drag, selected == DispatchSelectionMethod.DRAG_12);
        }
    }

    /** 派遣隊伍的顏色限制選擇器；支援 9 種類型，採用 3x3 網格排版。 */
    private final class DispatchPikminTypeSelector extends LinearLayout {
        private final Map<DispatchPikminType, Button> buttonMap = new HashMap<>();
        private DispatchPikminType selected;

        DispatchPikminTypeSelector(DispatchPikminType initial) {
            super(PetalAccessibilityService.this);
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER);
            selected = initial == null ? DispatchPikminType.MIXED : initial;

            DispatchPikminType[] types = DispatchPikminType.values();

            // 建立 3x3 佈局
            for (int i = 0; i < types.length; i += 3) {
                LinearLayout row = new LinearLayout(PetalAccessibilityService.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, dp(2), 0, dp(2));

                for (int j = i; j < i + 3 && j < types.length; j++) {
                    DispatchPikminType type = types[j];
                    String label = type.label();
                    
                    // 由於 optionButton 接收 int，我們直接手動實作相同的邏輯以支持 String 標籤
                    Button btn = overlayButton(label); 
                    btn.setContentDescription(label);
                    btn.setOnClickListener(view -> {
                        select(type);
                        btn.announceForAccessibility(btn.getText());
                    });

                    // 設定權重讓每行 3 個按鈕等寬
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
                    params.setMarginStart(j % 3 == 0 ? 0 : dp(6));
                    row.addView(btn, params);
                    
                    buttonMap.put(type, btn);
                }
                addView(row);
            }
            refresh();
        }

        DispatchPikminType value() {
            return selected;
        }

        private void select(DispatchPikminType value) {
            selected = value;
            refresh();
        }

        private void refresh() {
            for (Map.Entry<DispatchPikminType, Button> entry : buttonMap.entrySet()) {
                styleDispatchOption(entry.getValue(), entry.getKey() == selected);
            }
        }
    }

    private Button optionButton(int labelResource, Runnable action) {
        Button button = overlayButton(getString(labelResource));
        button.setContentDescription(getString(labelResource));
        button.setOnClickListener(view -> {
            action.run();
            button.announceForAccessibility(button.getText());
        });
        return button;
    }

    private LinearLayout.LayoutParams dispatchOptionParams(boolean marginStart) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        if (marginStart) {
            params.setMarginStart(dp(6));
        }
        return params;
    }

    private void styleDispatchOption(Button button, boolean selectedButton) {
        button.setSelected(selectedButton);
        button.setStateDescription(getString(selectedButton
                ? R.string.overlay_tab_selected
                : R.string.overlay_tab_not_selected));
        button.setTextColor(selectedButton ? Color.WHITE : OVERLAY_GREEN);
        button.setBackground(roundedBackground(
                selectedButton ? OVERLAY_GREEN : Color.rgb(241, 245, 239),
                selectedButton ? 0 : OVERLAY_BORDER,
                11));
    }

    /** 預覽稿的直接數字輸入；範圍仍由既有解析與儲存流程驗證。 */
    private final class StepperField extends LinearLayout {
        private final EditText input;

        StepperField(String label, String helper, int initialValue, int minimum, int maximum) {
            this(label, helper, initialValue, minimum, maximum, 1);
        }

        StepperField(
                String label,
                String helper,
                int initialValue,
                int minimum,
                int maximum,
                int step) {
            super(PetalAccessibilityService.this);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(10), dp(10));
            setBackground(roundedBackground(Color.WHITE, OVERLAY_BORDER, 14));

            LinearLayout copy = new LinearLayout(PetalAccessibilityService.this);
            copy.setOrientation(VERTICAL);
            TextView title = formText(label, 14, Color.rgb(35, 75, 54));
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView hint = formText(helper, 11, OVERLAY_MUTED);
            copy.addView(title);
            copy.addView(hint);
            addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            input = numberField(initialValue);
            input.setGravity(Gravity.CENTER);
            input.setPadding(dp(8), 0, dp(8), 0);
            input.setContentDescription(getString(
                    R.string.overlay_value_description, label, initialValue));
            input.setBackground(roundedBackground(Color.rgb(246, 248, 246), OVERLAY_BORDER, 10));
            addView(input, new LinearLayout.LayoutParams(dp(76), dp(48)));
        }

        String valueText() {
            return input.getText().toString();
        }

        EditText input() {
            return input;
        }
    }

    /** 以 1–5 的分段按鈕代替明信片皮克敏數字輸入。 */
    private final class NumberChoiceSelector extends LinearLayout {
        private final List<Button> choices = new ArrayList<>();
        private final String label;
        private int selectedValue;

        NumberChoiceSelector(String label, int initialValue, int minimum, int maximum) {
            super(PetalAccessibilityService.this);
            this.label = label;
            selectedValue = Math.max(minimum, Math.min(maximum, initialValue));
            setGravity(Gravity.CENTER_VERTICAL);
            for (int value = minimum; value <= maximum; value++) {
                int option = value;
                Button button = overlayButton(String.valueOf(value));
                button.setContentDescription(label + " " + value);
                button.setOnClickListener(view -> select(option, true));
                choices.add(button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
                if (value > minimum) {
                    params.setMarginStart(dp(4));
                }
                addView(button, params);
            }
            refreshChoiceStyles();
        }

        String valueText() {
            return String.valueOf(selectedValue);
        }

        private void select(int value, boolean announce) {
            selectedValue = value;
            refreshChoiceStyles();
            if (announce) {
                announceForAccessibility(getString(
                        R.string.overlay_value_description, label, selectedValue));
            }
        }

        private void refreshChoiceStyles() {
            for (int index = 0; index < choices.size(); index++) {
                Button button = choices.get(index);
                int value = index + 1;
                boolean selected = value == selectedValue;
                button.setSelected(selected);
                button.setStateDescription(getString(selected
                        ? R.string.overlay_tab_selected
                        : R.string.overlay_tab_not_selected));
                button.setTextColor(selected ? Color.WHITE : OVERLAY_GREEN);
                button.setBackground(roundedBackground(
                        selected ? OVERLAY_GREEN : Color.rgb(241, 245, 239),
                        selected ? 0 : OVERLAY_BORDER,
                        11));
            }
        }
    }

    /** 四色單選篩選 APK 內建花盆；真正花盆名稱由下拉選單回傳。 */
    private final class PostcardPotSelector extends LinearLayout {
        private final List<Button> colorButtons = new ArrayList<>();
        private final Spinner spinner;
        private PostcardPotCatalog.Color selectedColor;
        private String preferredName;

        PostcardPotSelector(String selectedName) {
            super(PetalAccessibilityService.this);
            setOrientation(VERTICAL);
            preferredName = PostcardPotCatalog.canonicalName(selectedName);
            selectedColor = PostcardPotCatalog.colorOf(preferredName);
            if (selectedColor == null) {
                selectedColor = PostcardPotCatalog.Color.WHITE;
            }

            LinearLayout colors = new LinearLayout(PetalAccessibilityService.this);
            colors.setGravity(Gravity.CENTER_VERTICAL);
            PostcardPotCatalog.Color[] values = PostcardPotCatalog.Color.values();
            for (int index = 0; index < values.length; index++) {
                PostcardPotCatalog.Color color = values[index];
                Button button = overlayButton(color.label());
                button.setContentDescription(getString(
                        R.string.overlay_postcard_petal_color_description,
                        color.label()));
                button.setOnClickListener(view -> selectColor(color));
                colorButtons.add(button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
                if (index > 0) {
                    params.setMarginStart(dp(4));
                }
                colors.addView(button, params);
            }
            addView(colors);

            spinner = new Spinner(PetalAccessibilityService.this);
            spinner.setMinimumHeight(dp(48));
            spinner.setPadding(dp(8), 0, dp(8), 0);
            spinner.setBackground(roundedBackground(
                    Color.rgb(250, 252, 249), OVERLAY_BORDER, 10));
            spinner.setContentDescription(getString(R.string.overlay_postcard_petal_pot_label));
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            spinnerParams.topMargin = dp(8);
            addView(spinner, spinnerParams);
            refresh();
        }

        String value() {
            Object selected = spinner.getSelectedItem();
            String canonical = selected == null
                    ? null
                    : PostcardPotCatalog.canonicalName(selected.toString());
            if (canonical == null) {
                throw new IllegalArgumentException(
                        getString(R.string.overlay_postcard_no_saved_pots_for_color));
            }
            return canonical;
        }

        private void selectColor(PostcardPotCatalog.Color color) {
            selectedColor = color;
            preferredName = "";
            refresh();
        }

        private void refresh() {
            List<String> filtered = PostcardPotCatalog.namesForColor(selectedColor);
            List<String> display = filtered.isEmpty()
                    ? List.of(getString(R.string.overlay_postcard_no_saved_pots_for_color))
                    : filtered;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    PetalAccessibilityService.this,
                    android.R.layout.simple_spinner_item,
                    display);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setEnabled(!filtered.isEmpty());
            int preferredIndex = filtered.indexOf(preferredName);
            if (preferredIndex >= 0) {
                spinner.setSelection(preferredIndex);
            }
            refreshColorStyles();
        }

        private void refreshColorStyles() {
            PostcardPotCatalog.Color[] values = PostcardPotCatalog.Color.values();
            for (int index = 0; index < colorButtons.size(); index++) {
                Button button = colorButtons.get(index);
                PostcardPotCatalog.Color color = values[index];
                boolean selected = color == selectedColor;
                button.setSelected(selected);
                button.setStateDescription(getString(selected
                        ? R.string.overlay_tab_selected
                        : R.string.overlay_tab_not_selected));
                button.setTextColor(selected ? Color.WHITE : Color.rgb(45, 74, 56));
                button.setBackground(roundedBackground(
                        selected ? OVERLAY_GREEN : potColorSurface(color),
                        selected ? 0 : OVERLAY_BORDER,
                        11));
            }
        }

        private int potColorSurface(PostcardPotCatalog.Color color) {
            return switch (color) {
                case WHITE -> Color.WHITE;
                case YELLOW -> Color.rgb(255, 249, 219);
                case RED -> Color.rgb(255, 237, 237);
                case BLUE -> Color.rgb(235, 244, 255);
            };
        }
    }

    /** 花朵順序編輯器；拖曳的無障礙替代是明確的上移、下移與刪除。 */
    /**
     * 自動換花順序編輯器：先選顏色，再從內建花朵下拉選單加入。
     * 目錄外文字無法進入設定，確保儲存值與 OCR 精確判斷使用同一名稱來源。
     */
    private final class FlowerOrderEditor extends LinearLayout {
        private final PetalSelection selection;
        private final boolean nectarLabels;
        private final LinearLayout selectedRows;
        private final List<Button> colorButtons = new ArrayList<>();
        private final Spinner flowerSpinner;
        private final Button addButton;
        private List<String> availableFlowers = List.of();
        private int selectedCategoryIndex;

        FlowerOrderEditor(List<String> flowers) {
            this(flowers, false);
        }

        FlowerOrderEditor(List<String> flowers, boolean nectarLabels) {
            super(PetalAccessibilityService.this);
            setOrientation(VERTICAL);
            selection = new PetalSelection(flowers);
            this.nectarLabels = nectarLabels;

            selectedRows = new LinearLayout(PetalAccessibilityService.this);
            selectedRows.setOrientation(VERTICAL);
            addView(selectedRows);

            LinearLayout colorRow = new LinearLayout(PetalAccessibilityService.this);
            colorRow.setGravity(Gravity.CENTER_VERTICAL);
            List<PetalCatalog.Category> categories = PetalCatalog.categories();
            for (int index = 0; index < categories.size(); index++) {
                int categoryIndex = index;
                PetalCatalog.Category category = categories.get(index);
                Button button = overlayButton(category.name());
                button.setTextSize(11);
                button.setContentDescription(getString(
                        R.string.overlay_flower_color_description, category.name()));
                button.setOnClickListener(view -> selectCategory(categoryIndex, true));
                colorButtons.add(button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
                if (index > 0) {
                    params.setMarginStart(dp(4));
                }
                colorRow.addView(button, params);
            }
            LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            colorParams.topMargin = dp(10);
            addView(colorRow, colorParams);

            flowerSpinner = new Spinner(PetalAccessibilityService.this);
            flowerSpinner.setMinimumHeight(dp(48));
            flowerSpinner.setPadding(dp(12), 0, dp(8), 0);
            flowerSpinner.setBackground(roundedBackground(Color.WHITE, OVERLAY_BORDER, 12));
            flowerSpinner.setContentDescription(getString(R.string.overlay_flower_dropdown));
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            spinnerParams.topMargin = dp(6);
            addView(flowerSpinner, spinnerParams);

            addButton = overlayButton("+  " + getString(R.string.overlay_add_flower));
            addButton.setContentDescription(getString(R.string.overlay_add_flower));
            addButton.setOnClickListener(view -> addSelectedFlower());
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            addParams.topMargin = dp(6);
            addView(addButton, addParams);

            refreshRows();
            int initialCategory = selection.size() == 0
                    ? 0
                    : Math.max(0, PetalCatalog.categoryIndexOf(selection.get(0)));
            selectCategory(initialCategory, false);
        }

        /** 將已選順序序列化，直接交給既有設定驗證與儲存流程。 */
        String valueText() {
            return selection.text();
        }

        /** 以目前顏色重新載入尚未加入的花朵選項。 */
        private void selectCategory(int categoryIndex, boolean announce) {
            selectedCategoryIndex = categoryIndex;
            refreshColorStyles();
            refreshDropdown();
            if (announce) {
                announceForAccessibility(getString(
                        R.string.overlay_flower_color_description,
                        PetalCatalog.categories().get(categoryIndex).name()));
            }
        }

        private void refreshColorStyles() {
            for (int index = 0; index < colorButtons.size(); index++) {
                Button button = colorButtons.get(index);
                boolean selected = index == selectedCategoryIndex;
                button.setSelected(selected);
                button.setStateDescription(getString(selected
                        ? R.string.overlay_tab_selected
                        : R.string.overlay_tab_not_selected));
                button.setTextColor(selected ? Color.WHITE : OVERLAY_GREEN);
                button.setBackground(roundedBackground(
                        selected ? OVERLAY_GREEN : Color.rgb(241, 245, 239),
                        selected ? 0 : OVERLAY_BORDER,
                        10));
            }
        }

        private void refreshDropdown() {
            availableFlowers = selection.available(selectedCategoryIndex);
            List<String> labels = availableFlowers.isEmpty()
                    ? List.of(getString(R.string.overlay_no_available_flowers))
                    : availableFlowers;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    PetalAccessibilityService.this,
                    android.R.layout.simple_spinner_item,
                    labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            flowerSpinner.setAdapter(adapter);
            flowerSpinner.setEnabled(!availableFlowers.isEmpty());
            addButton.setEnabled(!availableFlowers.isEmpty());
            addButton.setAlpha(availableFlowers.isEmpty() ? 0.45f : 1f);
        }

        private void addSelectedFlower() {
            int position = flowerSpinner.getSelectedItemPosition();
            if (position < 0 || position >= availableFlowers.size()) {
                return;
            }
            String flower = availableFlowers.get(position);
            if (selection.add(flower)) {
                refreshRows();
                refreshDropdown();
                announceForAccessibility(getString(
                        R.string.overlay_flower_added_description, flower));
            }
        }

        /** 重建已選順序；每列只提供排序與移除，不再出現自由文字欄位。 */
        private void refreshRows() {
            selectedRows.removeAllViews();
            for (int index = 0; index < selection.size(); index++) {
                addSelectedRow(index);
            }
            if (selection.size() == 0) {
                TextView empty = settingsHelperCard(
                        getString(R.string.overlay_no_selected_flowers), OVERLAY_CREAM);
                selectedRows.addView(empty);
            }
        }

        private void addSelectedRow(int index) {
            LinearLayout row = new LinearLayout(PetalAccessibilityService.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(4), dp(4), dp(4));
            row.setBackground(roundedBackground(Color.WHITE, OVERLAY_BORDER, 12));

            TextView number = formText(String.valueOf(index + 1), 13, OVERLAY_MUTED);
            number.setGravity(Gravity.CENTER);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(number, new LinearLayout.LayoutParams(dp(28), dp(48)));

            TextView flower = formText(displayName(selection.get(index)), 14, Color.rgb(35, 75, 54));
            flower.setContentDescription(getString(
                    R.string.overlay_flower_item_description, index + 1));
            row.addView(flower, new LinearLayout.LayoutParams(0, dp(48), 1f));

            Button up = compactIconButton("↑", getString(R.string.overlay_move_up));
            up.setEnabled(index > 0);
            up.setAlpha(index > 0 ? 1f : 0.35f);
            up.setOnClickListener(view -> move(index, -1));
            row.addView(up, new LinearLayout.LayoutParams(dp(48), dp(48)));

            Button down = compactIconButton("↓", getString(R.string.overlay_move_down));
            down.setEnabled(index < selection.size() - 1);
            down.setAlpha(index < selection.size() - 1 ? 1f : 0.35f);
            down.setOnClickListener(view -> move(index, 1));
            row.addView(down, new LinearLayout.LayoutParams(dp(48), dp(48)));

            Button remove = compactIconButton("×", getString(R.string.overlay_remove_flower));
            remove.setOnClickListener(view -> remove(index));
            row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (index > 0) {
                rowParams.topMargin = dp(6);
            }
            selectedRows.addView(row, rowParams);
        }

        private String displayName(String value) {
            if (!nectarLabels) {
                return value;
            }
            return switch (value) {
                case "白色花瓣" -> "白色精華";
                case "黃色花瓣" -> "黃色精華";
                case "紅色花瓣" -> "紅色精華";
                case "藍色花瓣" -> "藍色精華";
                default -> value;
            };
        }

        private void move(int index, int delta) {
            int target = index + delta;
            if (target < 0 || target >= selection.size()) {
                return;
            }
            selection.move(index, delta);
            refreshRows();
            announceForAccessibility(getString(
                    R.string.overlay_flower_item_description, target + 1));
        }

        private void remove(int index) {
            String removed = selection.get(index);
            selection.remove(index);
            refreshRows();
            refreshDropdown();
            announceForAccessibility(getString(
                    R.string.overlay_flower_removed_description, removed));
        }
    }

    private Button compactIconButton(String text, String description) {
        Button button = overlayButton(text);
        button.setTextSize(18);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private boolean isInsideOverlay(View view, float screenX, float screenY) {
        if (view == null || !view.isAttachedToWindow()) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return screenX >= location[0]
                && screenX < location[0] + view.getWidth()
                && screenY >= location[1]
                && screenY < location[1] + view.getHeight();
    }

    /** The first stationary tap after Start selects the reward detector ROI. */
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
                    if (isInsideOverlay(overlay, event.getRawX(), event.getRawY())) {
                        pause(getString(R.string.status_paused));
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
    private final class DragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;

        /** 依手指位移更新無障礙懸浮窗座標。 */
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = overlayParams.x;
                startY = overlayParams.y;
                touchX = event.getRawX();
                touchY = event.getRawY();
                moved = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                moved = moved
                        || Math.abs(event.getRawX() - touchX) > dp(6)
                        || Math.abs(event.getRawY() - touchY) > dp(6);
                overlayParams.x = startX + Math.round(event.getRawX() - touchX);
                overlayParams.y = startY + Math.round(event.getRawY() - touchY);
                if (overlay != null && overlay.isAttachedToWindow()) {
                    try {
                        windowManager.updateViewLayout(overlay, overlayParams);
                        updateFloatingNoticePosition();
                    } catch (RuntimeException exception) {
                        Log.w(TAG, "Unable to move icon overlay", exception);
                    }
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moved) {
                    view.performClick();
                }
                return true;
            }
            return false;
        }
    }

    /** 可拖曳的圖示按鈕，明確回報 click 以保留無障礙操作語意。 */
    private final class DraggableIcon extends ImageButton {
        DraggableIcon() {
            super(PetalAccessibilityService.this);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

}
