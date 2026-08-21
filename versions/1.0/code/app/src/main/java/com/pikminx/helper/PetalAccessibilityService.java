package com.pikminx.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
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
    private static final long DISPATCH_PIKMIN_TAP_DELAY_MILLIS = 350L;
    private static final long DISPATCH_AFTER_SCROLL_DELAY_MILLIS = 250L;
    private static final long RETURN_REWARD_SCAN_DELAY_MILLIS = 300L;
    private static final long RETURN_REWARD_AFTER_TAP_DELAY_MILLIS = 900L;
    private static final long RETURN_REWARD_SETTLE_MILLIS = 1500L;
    private static final long RETURN_REWARD_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    // 搜尋框、鍵盤與 Unity 清單都有轉場動畫；每個搜尋步驟先等一秒。
    private static final long POSTCARD_PETAL_STEP_DELAY_MILLIS = 1000L;
    // 懸浮圖示需要盡量不遮擋遊戲地圖；仍保留足夠的內邊距避免誤觸。
    static final int OVERLAY_SIZE_DP = 40;
    private static final int OVERLAY_PADDING_DP = 5;
    private static final int OVERLAY_GREEN = Color.rgb(25, 92, 57);
    private static final int OVERLAY_SURFACE = Color.rgb(251, 253, 249);
    private static final int OVERLAY_BORDER = Color.rgb(198, 220, 201);
    private static final int OVERLAY_MUTED = Color.rgb(78, 96, 83);
    private static final int OVERLAY_MINT = Color.rgb(229, 244, 232);
    private static final int OVERLAY_CREAM = Color.rgb(255, 249, 237);
    private static final int OVERLAY_ACCENT = Color.rgb(181, 63, 43);
    private static final int OVERLAY_WARNING = Color.rgb(156, 39, 39);
    private static final int OVERLAY_SEARCH = Color.rgb(62, 113, 137);
    private static final int OVERLAY_RECOGNIZING = Color.rgb(167, 120, 33);
    private static WeakReference<PetalAccessibilityService> connectedService =
            new WeakReference<>(null);

    private enum AutomationStep {
        MONITORING,
        REVEALING_SEARCH_PANEL,
        OPENING_SEARCH,
        ENTERING_SEARCH,
        CLOSING_SEARCH_KEYBOARD,
        SELECTING_SEARCH_RESULT,
        VERIFYING_SELECTION,
        CLOSING_SEARCH_AFTER_SELECTION,
        WAITING_START,
        VERIFYING_START
    }

    private enum AutomationMode {
        NONE,
        PLANTING,
        POSTCARD,
        DISPATCH,
        RETURN_REWARD
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
    private PostcardMatcher.PetalPot postcardPendingPot;
    private int postcardPotConfirmations;
    private int postcardPotMissingFrames;
    private int postcardPetalSearchMissingFrames;
    private int postcardPetalInputAttempts;
    private int postcardKeyboardCloseAttempts;
    private int postcardKeyboardAbsentFrames;
    private PetalMatcher.Selection plantingPendingPot;
    private int plantingPotConfirmations;
    private int plantingPotMissingFrames;
    private int plantingSearchMissingFrames;
    private int plantingSearchInputAttempts;
    private int plantingKeyboardCloseAttempts;
    private int plantingKeyboardAbsentFrames;
    private int plantingSearchMinimumCount;
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
    private int dispatchSearchInputAttempts;
    private int dispatchKeyboardCloseAttempts;
    private int dispatchKeyboardAbsentFrames;
    private int dispatchPikminTapIndex;
    private int dispatchUnknownFrames;
    private final ReturnRewardScanGuard returnRewardScanGuard = new ReturnRewardScanGuard();
    private long returnRewardStartedAt;
    private long returnRewardLastTapAt;
    private boolean returnRewardReceivePostcard = true;
    private PostcardMatcher.Target returnRewardPostcardTarget;
    private int returnRewardPostcardConfirmations;
    private int returnRewardPostcardAttempts;
    private boolean returnRewardWaitingPostcardExit;
    private String currentFlower = "";
    private AutomationStep automationStep = AutomationStep.MONITORING;
    private String targetFlower = "";
    private int targetCount;
    private int actionAttempts;
    private int startMissingConfirmations;
    private boolean startAfterSelection;
    private boolean selectionFromSearch;
    private int targetSelectionX;
    private int targetSelectionY;
    private long runGeneration;
    private String recentPackage = "";
    private long recentPackageAt;
    private UsageTelemetryClient.Session usageSession;

    /** 服務啟動後初始化 OCR、偏好設定與可拖曳懸浮窗。 */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = new WeakReference<>(this);
        settings = new SettingsStore(this);
        scanner = new OcrScanner();
        if (showOverlay()) {
            overlay.setVisibility(settings.overlayVisible() ? View.VISIBLE : View.GONE);
        }
        RemoteConfigClient.fetch(this, remoteConfig -> {
            if (remoteConfig == null) {
                return;
            }
            if (!remoteConfig.featureEnabled(RemoteConfigClient.Feature.OVERLAY)
                    && overlay != null) {
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

    private RemoteConfigClient.Feature featureFor(AutomationMode mode) {
        return switch (mode) {
            case PLANTING -> RemoteConfigClient.Feature.PLANTING;
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
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.PLANTING)) {
            return;
        }
        if (settings.allowedFlowers().isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        runGeneration++;
        running = true;
        automationMode = AutomationMode.PLANTING;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.PLANTING, 1, currentRemoteConfigVersion());
        busy = false;
        switchGuard.reset();
        resetPlantingSearch();
        currentFlower = "";
        automationStep = AutomationStep.MONITORING;
        targetFlower = "";
        targetCount = 0;
        actionAttempts = 0;
        startMissingConfirmations = 0;
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
        setStatus(getString(R.string.status_waiting_menu));
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.overlay_planting_checking),
                getString(R.string.overlay_ocr_detail));
        schedule(200);
    }

    /** 啟動獨立的明信片 OCR 狀態機，與種花流程互斥。 */
    private void startPostcardAutomation(
            int collectionLimit,
            String petalPotName,
            int pikminCount) {
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.POSTCARD)) {
            return;
        }
        runGeneration++;
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
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.DISPATCH)) {
            return;
        }
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_reward_wrong_page));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.DISPATCH;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.DISPATCH, count, currentRemoteConfigVersion());
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
        dispatchSearchInputAttempts = 0;
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
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
    private void startReturnRewardCollection(boolean receivePostcard) {
        if (remoteConfigBlocksAutomation(RemoteConfigClient.Feature.RETURN_REWARD)) {
            return;
        }
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_return_reward_left_game));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.RETURN_REWARD;
        usageSession = UsageTelemetryClient.start(
                this, UsageTelemetryClient.Operation.RETURN_REWARD, 1,
                currentRemoteConfigVersion());
        returnRewardScanGuard.reset();
        returnRewardStartedAt = android.os.SystemClock.elapsedRealtime();
        returnRewardLastTapAt = 0L;
        returnRewardReceivePostcard = receivePostcard;
        resetReturnRewardPostcard();
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setContentDescription(getString(
                    R.string.overlay_status_accessibility,
                    getString(R.string.overlay_stop_description),
                    getString(R.string.overlay_icon_move_hint)));
        }
        setStatus(getString(R.string.status_return_reward_started));
        setRunStatus(
                AutomationMode.RETURN_REWARD,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_return_reward_started),
                getString(R.string.overlay_return_reward_safety));
        schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
    }

    /** 排程回呼：只在遊戲前景且沒有其他掃描時擷取畫面。 */
    private void requestScan() {
        if (!running || busy) {
            return;
        }
        if ((automationMode == AutomationMode.DISPATCH
                || automationMode == AutomationMode.RETURN_REWARD)
                && activeGameBoundsStrict() == null) {
            stopWithError(getString(automationMode == AutomationMode.RETURN_REWARD
                    ? R.string.status_return_reward_left_game
                    : R.string.status_reward_left_game));
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
        takeGameScreenshot(runGeneration);
    }

    /** 依 Android 版本選擇視窗截圖或全螢幕截圖。 */
    private void takeGameScreenshot(long generation) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && root != null
                && GAME_PACKAGE.contentEquals(root.getPackageName())) {
            takeScreenshotOfWindow(
                    root.getWindowId(),
                    getMainExecutor(),
                    screenshotCallback(List.of(), generation));
            return;
        }

        // Android 13 and older can only capture the whole display. Keep the
        // visible overlays stable, then remove their pixels from the copy used
        // by OCR so the user never sees a hide/show cycle.
        List<ScreenshotOverlayMask.Region> overlayRegions = captureVisibleOverlayRegions();
        takeScreenshot(
                Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                screenshotCallback(overlayRegions, generation));
    }

    /** 建立截圖回呼，統一處理 bitmap、OCR 與失敗重試。 */
    private TakeScreenshotCallback screenshotCallback(
            List<ScreenshotOverlayMask.Region> overlayRegions, long generation) {
        return new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                Bitmap bitmap = copyBitmap(result);
                if (!isActiveRun(generation)) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap == null) {
                    scanFailed(getString(R.string.status_copy_failed), generation);
                    return;
                }
                maskOverlayRegions(bitmap, overlayRegions);
                // 鍵盤會遮住花盆清單並令整頁 OCR 變成 UNKNOWN。這個狀態只依系統視窗
                // 判斷，因此必須在 OCR 前執行，確保任何鍵盤語言或版面都能離開。
                if (automationMode == AutomationMode.POSTCARD
                        && postcardAutomation.step()
                                == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
                    busy = false;
                    try {
                        closePostcardKeyboard();
                    } finally {
                        bitmap.recycle();
                    }
                    return;
                }
                if (automationMode == AutomationMode.PLANTING
                        && automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
                    busy = false;
                    try {
                        closePlantingSearchKeyboard();
                    } finally {
                        bitmap.recycle();
                    }
                    return;
                }
                if (automationMode == AutomationMode.RETURN_REWARD) {
                    ReturnRewardDetector.Target target = ReturnRewardDetector.find(
                            bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
                    if (target != null) {
                        busy = false;
                        try {
                            handleReturnRewardTarget(target, bitmap.getWidth(), bitmap.getHeight());
                        } finally {
                            bitmap.recycle();
                        }
                        return;
                    }
                }
                OcrScanner.Callback ocrCallback = new OcrScanner.Callback() {
                    @Override
                    public void onSuccess(List<PetalMatcher.Token> tokens) {
                        if (!isActiveRun(generation)) {
                            bitmap.recycle();
                            return;
                        }
                        busy = false;
                        try {
                            // 地圖探測會由 OCR 回呼同步使用這張截圖；不可在 Scanner 端提早釋放。
                            handleTokens(tokens, bitmap);
                        } finally {
                            bitmap.recycle();
                        }
                    }

                    @Override
                    public void onFailure(Exception error) {
                        try {
                            scanFailed(getString(R.string.status_ocr_failed), generation);
                        } finally {
                            bitmap.recycle();
                        }
                    }
                };
                if (shouldUseFastChineseOcr()) {
                    scanner.scanChinese(bitmap, getMainExecutor(), ocrCallback);
                } else {
                    scanner.scan(bitmap, getMainExecutor(), ocrCallback);
                }
            }

            @Override
            public void onFailure(int errorCode) {
                if (!isActiveRun(generation)) {
                    return;
                }
                scanFailed(getString(R.string.status_capture_failed, errorCode), generation);
            }
        };
    }

    /** 花盆搜尋與接收頁只需中文 UI，避免等待五個文字系統模型全部完成。 */
    private boolean shouldUseFastChineseOcr() {
        if (automationMode == AutomationMode.RETURN_REWARD) {
            return true;
        }
        if (automationMode == AutomationMode.DISPATCH) {
            return true;
        }
        if (automationMode == AutomationMode.PLANTING) {
            return automationStep == AutomationStep.REVEALING_SEARCH_PANEL
                    || automationStep == AutomationStep.OPENING_SEARCH
                    || automationStep == AutomationStep.ENTERING_SEARCH
                    || automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD
                    || automationStep == AutomationStep.SELECTING_SEARCH_RESULT
                    || automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
        }
        if (automationMode != AutomationMode.POSTCARD) {
            return false;
        }
        return switch (postcardAutomation.step()) {
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
    private void handleTokens(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (automationMode == AutomationMode.RETURN_REWARD) {
            handleReturnRewardTokens(tokens, bitmap.getWidth(), bitmap.getHeight());
            return;
        }
        if (automationMode == AutomationMode.DISPATCH) {
            handleExpeditionDispatch(tokens, bitmap);
            return;
        }
        if (automationMode == AutomationMode.POSTCARD) {
            handlePostcardTokens(tokens, bitmap);
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int threshold = settings.threshold();
        List<String> sequence = settings.allowedFlowers();
        if (isPlantingSearchStep()) {
            handlePlantingFlowerSearch(tokens, bitmap);
            return;
        }
        PetalMatcher.Selection highlighted = PetalMatcher.findHighlightedFlower(
                tokens,
                PetalCatalog.petals(),
                width,
                height,
                flower -> CardHighlight.score(
                        width, height, flower.x(), flower.y(), bitmap::getPixel));
        CardHighlight.Point visualStartButton = CardHighlight.findStartButton(
                width, height, bitmap::getPixel);

        if (automationStep == AutomationStep.VERIFYING_SELECTION) {
            verifyFlowerSelection(highlighted, bitmap);
            return;
        }
        if (automationStep == AutomationStep.WAITING_START) {
            startPlanting(tokens, width, height, visualStartButton);
            return;
        }
        if (automationStep == AutomationStep.VERIFYING_START) {
            verifyPlantingStarted(tokens, width, height, visualStartButton != null);
            return;
        }

        boolean plantingCanStart = visualStartButton != null
                || hasStartPlantingControl(tokens, width, height);
        String firstFlower = sequence.get(0);

        // 每次開始都先搜尋第一順位；搜尋結果已連續確認名稱與數量，不再重讀全畫面。
        if (currentFlower.isEmpty()) {
            beginPlantingFlowerSearch(firstFlower, 0, plantingCanStart);
            return;
        }

        if (!PetalMatcher.hasVisibleFlowerCard(
                tokens, PetalCatalog.petals(), width, height)) {
            setStatus(getString(R.string.status_waiting_menu));
            scheduleNext();
            return;
        }

        if (plantingCanStart) {
            if (highlighted != null
                    && firstFlower.equals(highlighted.name())) {
                currentFlower = highlighted.name();
                showPlantingStatus(highlighted.name(), highlighted.count());
                automationStep = AutomationStep.WAITING_START;
                actionAttempts = 0;
                startPlanting(tokens, width, height, visualStartButton);
                return;
            }
            beginPlantingFlowerSearch(firstFlower, 0, true);
            return;
        }

        if (highlighted == null) {
            setStatus(getString(R.string.status_selected_not_visible));
            if (currentFlower.isEmpty()) {
                setPlantingNoticeText(getString(R.string.overlay_planting_checking), false);
            } else {
                PetalMatcher.Selection visibleCurrent = PetalMatcher.findFlower(
                        tokens, currentFlower, width, height);
                if (visibleCurrent != null) {
                    showPlantingStatus(visibleCurrent.name(), visibleCurrent.count());
                } else {
                    setPlantingNoticeText(
                            getString(R.string.overlay_planting_unreadable, currentFlower), false);
                }
            }
            scheduleNext();
            return;
        }
        if (currentFlower.isEmpty()) {
            currentFlower = highlighted.name();
        } else if (PetalMatcher.needsSelectionCorrection(currentFlower, highlighted)) {
            // 手動切換到其他花盆時，以搜尋欄重新篩出設定中的目前目標。
            beginPlantingFlowerSearch(currentFlower, 0, false);
            return;
        }
        int remaining = highlighted.count();
        showPlantingStatus(highlighted.name(), remaining);
        long now = android.os.SystemClock.elapsedRealtime();
        long cooldown = switchGuard.cooldownRemainingMillis(now);
        boolean readyToSwitch = switchGuard.shouldSwitch(remaining, threshold, now);

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

        String nextFlower = PetalMatcher.nextTarget(sequence, currentFlower);
        if (nextFlower == null) {
            finishWithSuccess(getString(R.string.status_sequence_complete, currentFlower));
            return;
        }

        // 下一順位花盆一律透過搜尋欄取得，避免清單長度與解析度改變搜尋結果。
        beginPlantingFlowerSearch(nextFlower, 0, false);
    }

    /**  派遣頁面順序：清單 → 詳細頁 → 選皮 → GO → 結果 → 清單。 */
    private void handleExpeditionDispatch(List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (expeditionDispatchSession == null || activeGameBoundsStrict() == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        ExpeditionScreenAnalyzer.Screen screen = ExpeditionScreenAnalyzer.classify(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        ExpeditionDispatchSession.Stage previousStage = expeditionDispatchSession.stage();
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
        if (screen == ExpeditionScreenAnalyzer.Screen.UNKNOWN) {
            dispatchUnknownFrames++;
        } else {
            dispatchUnknownFrames = 0;
        }
        if (dispatchUnknownFrames >= 8 && stage != ExpeditionDispatchSession.Stage.WAIT_RESULT) {
            stopWithError(getString(R.string.status_reward_stuck));
            return;
        }

        switch (stage) {
            case LIST_SEARCH -> handleDispatchList(tokens, bitmap, screen, now);
            case DETAIL -> handleDispatchDetail(tokens, bitmap, screen, now);
            case SELECTION -> handleDispatchSelection(tokens, bitmap, screen, now);
            case WAIT_RESULT -> handleDispatchResult(tokens, bitmap, screen, now);
            case VERIFY_RETURN -> handleDispatchReturn(tokens, screen, now);
        }
    }

    private void handleDispatchList(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        if (screen != ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            handleDispatchConfirmation(expeditionDispatchSession.confirm("", now));
            waitForDispatchFrame(getString(R.string.status_reward_wrong_page));
            return;
        }
        ExpeditionDispatchSession.BottomSettleDecision bottomDecision =
                expeditionDispatchSession.observeListForBottom(
                        ExpeditionScreenAnalyzer.isExplorePanelExpanded(
                                tokens, bitmap.getHeight()),
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
        int cropTop = Math.round(height * 0.18f);
        int cropBottom = Math.round(height * 0.90f);
        int cropHeight = Math.max(1, cropBottom - cropTop);
        Bitmap crop = null;
        Bitmap enlarged;
        try {
            crop = Bitmap.createBitmap(bitmap, 0, cropTop, width, cropHeight);
            enlarged = Bitmap.createScaledBitmap(crop, width * 2, cropHeight * 2, true);
            if (enlarged != crop) {
                crop.recycle();
            }
        } catch (RuntimeException error) {
            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
            handleDispatchListMiss(listStartVisible, android.os.SystemClock.elapsedRealtime());
            return;
        }

        long generation = runGeneration;
        busy = true;
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_reward_scanning),
                getString(R.string.overlay_reward_safety_items));
        scanner.scanChinese(enlarged, getMainExecutor(), new OcrScanner.Callback() {
            @Override
            public void onSuccess(List<PetalMatcher.Token> focusedTokens) {
                try {
                    if (!isActiveRun(generation)
                            || expeditionDispatchSession == null
                            || expeditionDispatchSession.stage()
                                    != ExpeditionDispatchSession.Stage.LIST_SEARCH) {
                        return;
                    }
                    busy = false;
                    long now = android.os.SystemClock.elapsedRealtime();
                    ExpeditionScreenAnalyzer.Target target =
                            ExpeditionScreenAnalyzer.findFocusedTarget(
                                    focusedTokens,
                                    expeditionTargetMode,
                                    width,
                                    height,
                                    cropTop,
                                    2,
                                    enlarged.getWidth(),
                                    enlarged.getHeight(),
                                    enlarged::getPixel);
                    if (target == null) {
                        handleDispatchListMiss(listStartVisible, now);
                    } else {
                        handleDispatchListTarget(target, width, height, now);
                    }
                } finally {
                    enlarged.recycle();
                }
            }

            @Override
            public void onFailure(Exception error) {
                try {
                    if (isActiveRun(generation)) {
                        busy = false;
                        handleDispatchListMiss(
                                listStartVisible, android.os.SystemClock.elapsedRealtime());
                    }
                } finally {
                    enlarged.recycle();
                }
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
                target.confirmationKey(), now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_confirming));
            return;
        }
        dispatchCurrentItemKind = target.kind();
        ExpeditionScreenAnalyzer.Point point = screenPointFromBitmap(
                new ExpeditionScreenAnalyzer.Point(target.x(), target.y()), width, height);
        dispatchActionTap(
                point,
                getString(R.string.status_reward_opening_detail),
                () -> {});
    }

    private void handleDispatchDetail(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
        if (screen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            waitForDispatchFrame(getString(R.string.status_reward_opening_detail));
            return;
        }
        ExpeditionScreenAnalyzer.Point action = ExpeditionScreenAnalyzer.findTextAction(
                tokens, "前往探險", "前往探险", "前往探索", "前往探臉");
        if (action == null) {
            FlowerDetailActionDetector.Target detected = FlowerDetailActionDetector.find(
                    bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
            if (detected != null) {
                action = new ExpeditionScreenAnalyzer.Point(detected.x(), detected.y());
            }
        }
        if (action == null) {
            ExpeditionDispatchSession.Confirmation timeout = expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_go_explore));
            }
            return;
        }
        ExpeditionDispatchSession.Confirmation confirmation = expeditionDispatchSession.confirm(
                "DETAIL:" + action.x() / 24 + ":" + action.y() / 24, now);
        if (!handleDispatchConfirmation(confirmation)) {
            waitForDispatchFrame(getString(R.string.status_reward_go_explore));
            return;
        }
        dispatchActionTap(
                screenPointFromBitmap(action, bitmap),
                getString(R.string.status_reward_go_explore),
                () -> {});
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

        if (!dispatchColorSelected) {
            handleDispatchPikminFilter(tokens, bitmap, now);
            return;
        }

        if (!dispatchPikminSelected) {
            if (dispatchSelectionMethod == DispatchSelectionMethod.AUTO) {
                ExpeditionScreenAnalyzer.Point automatic = ExpeditionScreenAnalyzer.findTextAction(
                        tokens, "自動", "自动");
                if (automatic == null) {
                    waitForDispatchFrame(getString(R.string.status_reward_selection_missing));
                    return;
                }
                ExpeditionDispatchSession.Confirmation autoConfirmation =
                        expeditionDispatchSession.confirm(
                                "AUTO:" + automatic.x() / 24 + ":" + automatic.y() / 24,
                                now);
                if (!handleDispatchConfirmation(autoConfirmation)) {
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                dispatchActionTap(
                        screenPointFromBitmap(automatic, bitmap),
                        getString(R.string.status_reward_selecting_pikmin),
                        () -> dispatchPikminSelected = true);
            } else {
                selectDispatchPikminFromGrid(tokens, bitmap, now);
            }
            return;
        }

        ExpeditionScreenAnalyzer.Point go = ExpeditionScreenAnalyzer.findTextAction(tokens, "GO");
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
        dispatchActionTap(
                screenPointFromBitmap(go, bitmap),
                getString(R.string.status_reward_tapping_go),
                () -> {});
    }

    private void handleDispatchResult(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            ExpeditionScreenAnalyzer.Screen screen,
            long now) {
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
        dispatchActionTap(
                screenPointFromBitmap(close, bitmap),
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
            stopWithError(getString(R.string.status_reward_stuck));
            return;
        }
        if (settings.recordConfirmedExpeditionDispatch() < 0) {
            stopWithError(getString(R.string.status_reward_progress_save_failed));
            return;
        }
        if (usageSession != null) {
            if (dispatchCurrentItemKind == ExpeditionScreenAnalyzer.ItemKind.FRUIT) {
                usageSession.recordDispatchFruit();
            } else if (dispatchCurrentItemKind == ExpeditionScreenAnalyzer.ItemKind.POT) {
                usageSession.recordDispatchPot();
            }
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
        dispatchSearchInputAttempts = 0;
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
        waitForDispatchFrame(getString(R.string.status_reward_progress, completed, target));
    }

    /** 回傳 false 表示尚未可執行；逾時時方法會自行停止流程。 */
    private boolean handleDispatchConfirmation(ExpeditionDispatchSession.Confirmation confirmation) {
        if (confirmation == ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
            stopWithError(getString(R.string.status_reward_stuck));
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
                    if (expeditionDispatchSession != null) {
                        expeditionDispatchSession.recordProgress(
                                android.os.SystemClock.elapsedRealtime());
                    }
                    advance.run();
                    busy = false;
                    schedule(nextScanDelayMillis);
                },
                () -> {
                    busy = false;
                    stopWithError(getString(R.string.status_reward_gesture_failed));
                });
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(
            ExpeditionScreenAnalyzer.Point point,
            Bitmap bitmap) {
        return screenPointFromBitmap(point, bitmap.getWidth(), bitmap.getHeight());
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(
            ExpeditionScreenAnalyzer.Point point,
            int bitmapWidth,
            int bitmapHeight) {
        Rect gameBounds = activeGameBoundsStrict();
        if (gameBounds == null) {
            return point;
        }
        boolean windowLocal = android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
        if (windowLocal) {
            return new ExpeditionScreenAnalyzer.Point(
                    gameBounds.left + Math.round((float) point.x() * gameBounds.width() / bitmapWidth),
                    gameBounds.top + Math.round((float) point.y() * gameBounds.height() / bitmapHeight));
        }
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        return new ExpeditionScreenAnalyzer.Point(
                Math.round((float) point.x() * displayWidth / bitmapWidth),
                Math.round((float) point.y() * displayHeight / bitmapHeight));
    }

    private void handleDispatchPikminFilter(
            List<PetalMatcher.Token> tokens,
            Bitmap bitmap,
            long now) {
        String label = dispatchPikminType.label();
        if (!dispatchSearchOpened) {
            ExpeditionScreenAnalyzer.Point search =
                    ExpeditionScreenAnalyzer.findPikminSearchButton(
                            tokens, bitmap.getWidth(), bitmap.getHeight());
            if (search == null) {
                waitForDispatchFrame(getString(R.string.status_reward_search_missing));
                return;
            }
            ExpeditionDispatchSession.Confirmation confirmation =
                    expeditionDispatchSession.confirm(
                            "PIKMIN_SEARCH:" + search.x() / 24 + ":" + search.y() / 24,
                            now);
            if (!handleDispatchConfirmation(confirmation)) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
            dispatchActionTap(
                    screenPointFromBitmap(search, bitmap),
                    getString(R.string.status_reward_selecting_pikmin),
                    () -> dispatchSearchOpened = true);
            return;
        }

        if (!gameEditableTextMatches(label)) {
            dispatchSearchInputAttempts++;
            boolean accepted = setGameEditableText(label);
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
        dispatchSearchInputAttempts = 0;

        if (isInputMethodWindowVisible()) {
            dispatchKeyboardAbsentFrames = 0;
            if (dispatchKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_reward_keyboard_failed));
                return;
            }
            dispatchKeyboardCloseAttempts++;
            boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
            if (!accepted && dispatchKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_reward_keyboard_failed));
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }

        dispatchKeyboardAbsentFrames++;
        if (dispatchKeyboardAbsentFrames < 2) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        dispatchColorSelected = true;
        expeditionDispatchSession.recordProgress(now);
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
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
                screenPointFromBitmap(
                        new ExpeditionScreenAnalyzer.Point(candidate.x(), candidate.y()),
                        bitmap),
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
        targetFlower = PetalCatalog.canonicalName(flower);
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
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /** 依搜尋子狀態開啟欄位、輸入、關閉鍵盤及辨識完整目標花盆。 */
    private void handlePlantingFlowerSearch(
            List<PetalMatcher.Token> tokens, Bitmap bitmap) {
        if (automationStep == AutomationStep.REVEALING_SEARCH_PANEL) {
            revealPlantingSearchPanel();
            return;
        }
        if (automationStep == AutomationStep.OPENING_SEARCH) {
            openPlantingFlowerSearch(bitmap);
            return;
        }
        if (automationStep == AutomationStep.ENTERING_SEARCH) {
            enterPlantingFlowerSearch();
            return;
        }
        if (automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
            closePlantingSearchKeyboard();
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

        PetalMatcher.Selection pot = PetalMatcher.findSearchedFlower(
                tokens,
                targetFlower,
                plantingSearchMinimumCount,
                bitmap.getWidth(),
                bitmap.getHeight());
        logPlantingOcrTokens("full", tokens, pot);
        if (pot == null) {
            scanFocusedPlantingPetalRegion(bitmap);
            return;
        }
        confirmPlantingSearchResult(pot, bitmap.getWidth(), bitmap.getHeight());
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
        CardHighlight.Point search = CardHighlight.findPetalSearchButton(
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
        plantingKeyboardCloseAttempts = 0;
        plantingKeyboardAbsentFrames = 0;
        automationStep = AutomationStep.CLOSING_SEARCH_KEYBOARD;
        setStatus(getString(R.string.status_flower_search_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /** 以輸入法視窗狀態關閉鍵盤，避免依賴 Sony、三星或 Gboard 的按鍵位置。 */
    private void closePlantingSearchKeyboard() {
        if (!isInputMethodWindowVisible()) {
            plantingKeyboardAbsentFrames++;
            if (plantingKeyboardAbsentFrames < 2) {
                setStatus(getString(R.string.status_flower_search_waiting_keyboard));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                return;
            }
            plantingKeyboardCloseAttempts = 0;
            plantingKeyboardAbsentFrames = 0;
            automationStep = AutomationStep.SELECTING_SEARCH_RESULT;
            setStatus(getString(R.string.status_flower_search_keyboard_closed));
            schedule(700);
            return;
        }

        plantingKeyboardAbsentFrames = 0;
        if (plantingKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_flower_search_keyboard_failed));
            return;
        }
        plantingKeyboardCloseAttempts++;
        boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
        if (!accepted && plantingKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_flower_search_keyboard_failed));
            return;
        }
        setStatus(getString(R.string.status_flower_search_closing_keyboard));
        schedule(600);
    }

    /**
     * 全畫面 OCR 不穩定時，沿用明信片流程裁切花盆清單並放大兩倍重讀。
     * 回呼中的 token 會換算回原始螢幕座標，確保不同解析度仍點擊同一位置。
     */
    private void scanFocusedPlantingPetalRegion(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int cropTop = Math.round(height * 0.44f);
        int cropBottom = Math.round(height * 0.96f);
        int cropHeight = Math.max(1, cropBottom - cropTop);
        Bitmap crop = null;
        Bitmap enlarged;
        try {
            crop = Bitmap.createBitmap(bitmap, 0, cropTop, width, cropHeight);
            enlarged = Bitmap.createScaledBitmap(crop, width * 2, cropHeight * 2, true);
            if (enlarged != crop) {
                crop.recycle();
            }
        } catch (RuntimeException error) {
            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
            handlePlantingSearchMiss();
            return;
        }

        long generation = runGeneration;
        busy = true;
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_flower_search_focused_ocr),
                targetFlower);
        scanner.scanChinese(enlarged, getMainExecutor(), new OcrScanner.Callback() {
            @Override
            public void onSuccess(List<PetalMatcher.Token> focusedTokens) {
                try {
                    if (!isActiveRun(generation)) {
                        return;
                    }
                    busy = false;
                    if (automationStep != AutomationStep.SELECTING_SEARCH_RESULT) {
                        schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                        return;
                    }
                    List<PetalMatcher.Token> mapped = new ArrayList<>(focusedTokens.size());
                    for (PetalMatcher.Token token : focusedTokens) {
                        mapped.add(new PetalMatcher.Token(
                                token.text(),
                                token.left() / 2,
                                cropTop + token.top() / 2,
                                token.right() / 2,
                                cropTop + token.bottom() / 2));
                    }
                    PetalMatcher.Selection pot = PetalMatcher.findSearchedFlower(
                            mapped,
                            targetFlower,
                            plantingSearchMinimumCount,
                            width,
                            height);
                    logPlantingOcrTokens("focused", mapped, pot);
                    if (pot == null) {
                        handlePlantingSearchMiss();
                    } else {
                        confirmPlantingSearchResult(pot, width, height);
                    }
                } finally {
                    enlarged.recycle();
                }
            }

            @Override
            public void onFailure(Exception error) {
                try {
                    if (isActiveRun(generation)) {
                        busy = false;
                        logPlantingOcrError("focused", error);
                        handlePlantingSearchMiss();
                    }
                } finally {
                    enlarged.recycle();
                }
            }
        });
    }

    /** 限制搜尋結果等待次數，避免搜尋不到時無限循環。 */
    private void handlePlantingSearchMiss() {
        plantingSearchMissingFrames++;
        if (plantingPendingPot != null && plantingPotMissingFrames < 2) {
            plantingPotMissingFrames++;
        } else {
            plantingPendingPot = null;
            plantingPotConfirmations = 0;
            plantingPotMissingFrames = 0;
        }
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
        schedule(700);
    }

    /** 要求同一個完整目標結果連續出現兩幀，再交給花盆點擊後確認。 */
    private void confirmPlantingSearchResult(
            PetalMatcher.Selection pot, int width, int height) {
        if (isSamePlantingSearchCandidate(plantingPendingPot, pot, width, height)) {
            plantingPotConfirmations++;
            plantingPendingPot = pot;
        } else {
            plantingPendingPot = pot;
            plantingPotConfirmations = 1;
        }
        plantingPotMissingFrames = 0;
        plantingSearchMissingFrames = 0;
        if (!PetalMatcher.hasStableSearchResult(plantingPotConfirmations)) {
            setRunStatus(
                    AutomationMode.PLANTING,
                    OverlayRunStatus.Kind.RECOGNIZING,
                    getString(
                            R.string.status_flower_search_confirming_result,
                            pot.name(),
                            pot.count(),
                            plantingPotConfirmations,
                            2),
                    getString(R.string.overlay_ocr_detail));
            schedule(700);
            return;
        }
        boolean shouldStart = startAfterSelection;
        resetPlantingSearch();
        tapFlower(pot, shouldStart, true);
    }

    /** OCR 測試 APK 專用：只記錄自動種花搜尋區域的原始 TOKEN 與配對結果。 */
    private void logPlantingOcrTokens(
            String source,
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Selection match) {
        if (!BuildConfig.PLANTING_OCR_DIAGNOSTICS) {
            return;
        }
        String matched = match == null
                ? "none"
                : match.name() + ":" + match.count()
                        + "@" + match.x() + "," + match.y();
        Log.i(TAG, "PLANTING_OCR_FRAME source=" + source
                + " target=\"" + targetFlower + "\""
                + " minimum=" + plantingSearchMinimumCount
                + " matched=\"" + matched + "\""
                + " tokenCount=" + tokens.size());
        for (PetalMatcher.Token token : tokens) {
            String text = token.text().replace('\r', ' ').replace('\n', ' ');
            String canonical = PetalCatalog.canonicalName(text);
            Log.i(TAG, "PLANTING_OCR_TOKEN source=" + source
                    + " text=\"" + text + "\""
                    + " canonical=\"" + (canonical == null ? "" : canonical) + "\""
                    + " bounds=[" + token.left() + "," + token.top()
                    + "][" + token.right() + "," + token.bottom() + "]");
        }
    }

    /** OCR 測試 APK 專用：保留局部辨識例外，正式 APK 不輸出。 */
    private void logPlantingOcrError(String source, Exception error) {
        if (BuildConfig.PLANTING_OCR_DIAGNOSTICS) {
            Log.i(TAG, "PLANTING_OCR_ERROR source=" + source
                    + " target=\"" + targetFlower + "\""
                    + " type=" + error.getClass().getSimpleName()
                    + " message=\"" + String.valueOf(error.getMessage()) + "\"");
        }
    }

    /** 以名稱及解析度比例容差確認連續兩幀仍是同一張搜尋結果卡。 */
    private boolean isSamePlantingSearchCandidate(
            PetalMatcher.Selection previous,
            PetalMatcher.Selection current,
            int width,
            int height) {
        return previous != null
                && current != null
                && previous.name().equals(current.name())
                && Math.abs(previous.x() - current.x()) <= width * 0.08f
                && Math.abs(previous.y() - current.y()) <= height * 0.07f;
    }

    /** 清除自動種花搜尋流程的暫存，不修改目前設定中的目標花名。 */
    private void resetPlantingSearch() {
        plantingPendingPot = null;
        plantingPotConfirmations = 0;
        plantingPotMissingFrames = 0;
        plantingSearchMissingFrames = 0;
        plantingSearchInputAttempts = 0;
        plantingKeyboardCloseAttempts = 0;
        plantingKeyboardAbsentFrames = 0;
        plantingSearchMinimumCount = 0;
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
            actionAttempts = 0;
            boolean shouldCloseSearch = selectionFromSearch;
            selectionFromSearch = false;
            targetSelectionX = 0;
            targetSelectionY = 0;
            if (shouldCloseSearch) {
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
        CardHighlight.Point close = CardHighlight.findPetalSearchCloseButton(
                bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        if (close != null) {
            if (++actionAttempts > MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_flower_search_close_failed));
                return;
            }
            dispatchTap(
                    close.x(),
                    close.y(),
                    GAME_ACTION_TAP_DURATION_MILLIS,
                    () -> schedule(600),
                    () -> stopWithError(getString(R.string.status_flower_search_close_failed)));
            return;
        }
        actionAttempts = 0;
        if (isInputMethodWindowVisible()) {
            if (plantingKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_flower_search_close_failed));
                return;
            }
            plantingKeyboardCloseAttempts++;
            boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
            if (!accepted && plantingKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_flower_search_close_failed));
                return;
            }
            schedule(600);
            return;
        }
        plantingKeyboardCloseAttempts = 0;
        continueAfterConfirmedFlowerSelection();
    }

    /** 搜尋介面清理完成後，接回既有的開始種花或監控流程。 */
    private void continueAfterConfirmedFlowerSelection() {
        if (SwitchGuard.isBelowThreshold(targetCount, settings.threshold())) {
            String nextFlower = PetalMatcher.nextTarget(
                    settings.allowedFlowers(), currentFlower);
            if (nextFlower == null) {
                finishWithSuccess(getString(
                        R.string.status_sequence_complete, currentFlower));
                return;
            }
            beginPlantingFlowerSearch(nextFlower, 0, startAfterSelection);
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
    private void startPlanting(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            CardHighlight.Point visualControl) {
        PetalMatcher.Token control = PetalMatcher.findStartPlantingControl(tokens, width, height);
        if (clickGameNode(node -> nodeLabelEquals(
                node, "開始種花", "start planting"))) {
            automationStep = AutomationStep.VERIFYING_START;
            actionAttempts = 0;
            startMissingConfirmations = 0;
            schedule(700);
            return;
        }
        if (control != null) {
            automationStep = AutomationStep.VERIFYING_START;
            actionAttempts = 0;
            startMissingConfirmations = 0;
            dispatchTap(
                    control.centerX(),
                    control.centerY(),
                    80,
                    () -> schedule(700),
                    () -> stopWithError(getString(R.string.status_start_tap_failed)));
            return;
        }
        if (visualControl != null) {
            automationStep = AutomationStep.VERIFYING_START;
            actionAttempts = 0;
            startMissingConfirmations = 0;
            dispatchTap(
                    visualControl.x(),
                    visualControl.y(),
                    80,
                    () -> schedule(700),
                    () -> stopWithError(getString(R.string.status_start_tap_failed)));
            return;
        }
        if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_start_control_unavailable));
        } else {
            schedule(500);
        }
    }

    /** 透過控制項消失連續確認種花已經開始。 */
    private void verifyPlantingStarted(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            boolean visualStartVisible) {
        if (visualStartVisible || hasStartPlantingControl(tokens, width, height)) {
            startMissingConfirmations = 0;
            if (++actionAttempts >= MAX_ACTION_ATTEMPTS) {
                stopWithError(getString(R.string.status_start_unconfirmed));
            } else {
                schedule(700);
            }
            return;
        }
        if (++startMissingConfirmations < 2) {
            schedule(500);
            return;
        }
        automationStep = AutomationStep.MONITORING;
        targetFlower = "";
        startAfterSelection = false;
        setStatus(getString(R.string.status_planting_started, currentFlower));
        if (usageSession != null) {
            usageSession.recordPlanting();
        }
        scheduleNext();
    }

    /** 同時檢查 OCR 與無障礙節點，提升開始按鈕辨識穩定度。 */
    private boolean hasStartPlantingControl(
            List<PetalMatcher.Token> tokens, int width, int height) {
        return PetalMatcher.findStartPlantingControl(tokens, width, height) != null
                || findGameNode(node -> nodeLabelEquals(
                        node, "開始種花", "start planting")) != null;
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
        if (returnRewardScanGuard.observe(target, width, height)
                != ReturnRewardScanGuard.Decision.TARGET_CONFIRMED) {
            setReturnRewardStatus(getString(R.string.status_return_reward_confirming));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
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

    private void handleReturnRewardTokens(
            List<PetalMatcher.Token> tokens, int width, int height) {
        if (returnRewardTimedOut()) {
            stopWithError(getString(R.string.status_return_reward_timeout));
            return;
        }
        if (PostcardMatcher.detectPage(tokens, width, height)
                == PostcardMatcher.Page.POSTCARD_RECEIVED) {
            returnRewardScanGuard.reset();
            PostcardMatcher.Target target = returnRewardReceivePostcard
                    ? PostcardMatcher.findReceive(tokens)
                    : PostcardMatcher.findDiscard(tokens, width, height);
            if (target == null) {
                if (++returnRewardPostcardAttempts >= MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_return_reward_postcard_missing));
                } else {
                    schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                }
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
        if (returnRewardScanGuard.observe(null, width, height)
                == ReturnRewardScanGuard.Decision.COMPLETE) {
            finishWithSuccess(getString(R.string.status_return_reward_complete));
            return;
        }
        setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
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
        if (usageSession != null) {
            usageSession.recordPostcard();
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
            closePostcardKeyboard();
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
        confirmPostcardPetalPot(pot, width, height, bitmap);
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
        postcardKeyboardCloseAttempts = 0;
        postcardKeyboardAbsentFrames = 0;
        postcardAutomation.moveTo(PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD);
        setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /**
     * 依 Android 視窗類型關閉輸入法，不辨識任何廠牌鍵盤上的完成、返回或箭頭按鍵。
     * 只有在 TYPE_INPUT_METHOD 確實存在時才送出系統返回，並要求連續兩幀看不到鍵盤後
     * 才進入花盆辨識，避免鍵盤動畫或視窗事件延遲造成過早 OCR。
     */
    private void closePostcardKeyboard() {
        if (!isInputMethodWindowVisible()) {
            postcardKeyboardAbsentFrames++;
            if (postcardKeyboardAbsentFrames < 2) {
                setPostcardStatus(getString(R.string.status_postcard_waiting_keyboard_close));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                return;
            }
            postcardKeyboardCloseAttempts = 0;
            postcardKeyboardAbsentFrames = 0;
            postcardAutomation.moveTo(PostcardAutomation.Step.SELECT_PETAL);
            setPostcardStatus(getString(R.string.status_postcard_keyboard_closed));
            schedule(700);
            return;
        }

        postcardKeyboardAbsentFrames = 0;
        if (postcardKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
            return;
        }
        postcardKeyboardCloseAttempts++;
        boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
        if (!accepted && postcardKeyboardCloseAttempts >= MAX_ACTION_ATTEMPTS) {
            stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
            return;
        }
        setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
        schedule(600);
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

    private boolean setGameEditableText(String value) {
        AccessibilityNodeInfo editable = findGameNode(node ->
                node.isEditable() && node.isEnabled());
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
        AccessibilityNodeInfo editable = findGameNode(node ->
                node.isEditable() && node.isEnabled());
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
        int cropTop = Math.round(height * 0.44f);
        int cropBottom = Math.round(height * 0.96f);
        int cropHeight = Math.max(1, cropBottom - cropTop);
        Bitmap crop = null;
        Bitmap enlarged;
        try {
            crop = Bitmap.createBitmap(bitmap, 0, cropTop, width, cropHeight);
            enlarged = Bitmap.createScaledBitmap(crop, width * 2, cropHeight * 2, true);
            if (enlarged != crop) {
                crop.recycle();
            }
        } catch (RuntimeException error) {
            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
            handleFocusedPetalMiss();
            return;
        }
        long generation = runGeneration;
        busy = true;
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_focused_petal_ocr),
                postcardAutomation.petalPotName());
        scanner.scanChinese(enlarged, getMainExecutor(), new OcrScanner.Callback() {
            @Override
            public void onSuccess(List<PetalMatcher.Token> focusedTokens) {
                try {
                    if (!isActiveRun(generation)) {
                        return;
                    }
                    busy = false;
                    if (postcardAutomation.step() != PostcardAutomation.Step.SELECT_PETAL) {
                        schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                        return;
                    }
                    List<PetalMatcher.Token> mapped = new ArrayList<>(focusedTokens.size());
                    for (PetalMatcher.Token token : focusedTokens) {
                        mapped.add(new PetalMatcher.Token(
                                token.text(),
                                token.left() / 2,
                                cropTop + token.top() / 2,
                                token.right() / 2,
                                cropTop + token.bottom() / 2));
                    }
                    PostcardMatcher.PetalPot focusedPot =
                            PostcardMatcher.findSingleVisiblePetalPot(
                                    mapped,
                                    postcardAutomation.petalPotName(),
                                    80,
                                    width,
                                    height);
                    if (focusedPot == null) {
                        handleFocusedPetalMiss();
                    } else {
                        confirmPostcardPetalPot(focusedPot, width, height, null);
                    }
                } finally {
                    enlarged.recycle();
                }
            }

            @Override
            public void onFailure(Exception error) {
                try {
                    if (isActiveRun(generation)) {
                        busy = false;
                        handleFocusedPetalMiss();
                    }
                } finally {
                    enlarged.recycle();
                }
            }
        });
    }

    private void handleFocusedPetalMiss() {
        postcardPetalSearchMissingFrames++;
        if (postcardPendingPot != null && postcardPotMissingFrames < 2) {
            postcardPotMissingFrames++;
        } else {
            resetPostcardPotConfirmation();
        }
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
            int height,
            Bitmap bitmap) {
        if (isSamePostcardPotCandidate(postcardPendingPot, pot, width, height)) {
            postcardPotConfirmations++;
            postcardPendingPot = pot;
        } else {
            postcardPendingPot = pot;
            postcardPotConfirmations = 1;
        }
        postcardPotMissingFrames = 0;
        postcardPetalSearchMissingFrames = 0;
        if (postcardPotConfirmations < 2 || bitmap == null) {
            setPostcardStatus(
                    OverlayRunStatus.Kind.RECOGNIZING,
                    getString(
                            R.string.status_postcard_confirming_petal,
                            pot.name(),
                            pot.count(),
                            postcardPotConfirmations,
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

    private boolean isSamePostcardPotCandidate(
            PostcardMatcher.PetalPot previous,
            PostcardMatcher.PetalPot current,
            int width,
            int height) {
        if (previous == null || current == null) {
            return false;
        }
        String previousName = PostcardPotCatalog.canonicalName(previous.name());
        String currentName = PostcardPotCatalog.canonicalName(current.name());
        return previousName != null
                && previousName.equals(currentName)
                && Math.abs(previous.x() - current.x()) <= width * 0.08f
                && Math.abs(previous.y() - current.y()) <= height * 0.07f;
    }

    private void resetPostcardPotConfirmation() {
        postcardPendingPot = null;
        postcardPotConfirmations = 0;
        postcardPotMissingFrames = 0;
    }

    private void resetPostcardPetalSearch() {
        postcardPetalSearchMissingFrames = 0;
        postcardPetalInputAttempts = 0;
        postcardKeyboardCloseAttempts = 0;
        postcardKeyboardAbsentFrames = 0;
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

    /** 發送單次手指點擊，並透過 generation 防止舊回呼污染新流程。 */
    private void dispatchTap(
            int x,
            int y,
            long durationMillis,
            Runnable completed,
            Runnable failed) {
        long generation = runGeneration;
        Path path = new Path();
        path.moveTo(x, y);
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

    /** 將截圖或 OCR 失敗轉成狀態文字並安排下一次重試。 */
    private void scanFailed(String message, long generation) {
        if (!isActiveRun(generation)) {
            return;
        }
        busy = false;
        setStatus(message);
        scheduleNext();
    }

    /** 依使用者設定安排下一次掃描。 */
    private void scheduleNext() {
        schedule(SCAN_INTERVAL_MILLIS);
    }

    /** 清掉舊排程後建立新的最小延遲排程。 */
    private void schedule(long delayMillis) {
        handler.removeCallbacks(scanTask);
        if (running) {
            long minimum = switch (automationMode) {
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
            case SELECTION -> dispatchSelectionMethod == DispatchSelectionMethod.DRAG_12
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

    /** 建立整潔、可拖曳且不阻擋遊戲操作的主懸浮窗。 */
    private boolean showOverlay() {
        if (overlay != null) {
            return true;
        }
        RemoteConfigClient.Status remoteConfig = RemoteConfigClient.cached(this);
        if (remoteConfig != null
                && !remoteConfig.featureEnabled(RemoteConfigClient.Feature.OVERLAY)) {
            return false;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 只保留一個可拖曳圖示；狀態、按鈕與輸入欄位全部移到設定卡片。
        DraggableIcon icon = new DraggableIcon();
        icon.setImageResource(R.drawable.ic_overlay_flower);
        icon.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_icon_description),
                getString(R.string.overlay_icon_move_hint)));
        icon.setFocusable(true);
        icon.setElevation(dp(2));
        icon.setPadding(
                dp(OVERLAY_PADDING_DP),
                dp(OVERLAY_PADDING_DP),
                dp(OVERLAY_PADDING_DP),
                dp(OVERLAY_PADDING_DP));
        icon.setBackground(roundedBackground(OVERLAY_SURFACE, OVERLAY_GREEN, 10));
        icon.setOnClickListener(view -> {
            if (running) {
                pause(getString(R.string.status_paused));
                showFloatingNotice(getString(R.string.status_paused));
            } else {
                showSettingsOverlay();
            }
        });

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
        RemoteConfigClient.Status remoteConfig = RemoteConfigClient.cached(this);
        if (settingsOverlay != null || overlay == null || windowManager == null
                || (remoteConfig != null
                && !remoteConfig.featureEnabled(RemoteConfigClient.Feature.OVERLAY))) {
            return;
        }
        pause(getString(R.string.status_paused));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(8));
        panel.setElevation(dp(18));
        panel.setBackground(roundedBackground(OVERLAY_SURFACE, OVERLAY_BORDER, 24));
        panel.setAccessibilityPaneTitle(getString(R.string.overlay_brand_title));
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(4), dp(8));

        TextView title = formText(getString(R.string.overlay_brand_title), 20, Color.rgb(23, 59, 42));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView readyChip = formText(getString(R.string.overlay_ready), 11, OVERLAY_GREEN);
        readyChip.setGravity(Gravity.CENTER);
        readyChip.setPadding(dp(12), 0, dp(12), 0);
        readyChip.setBackground(roundedBackground(OVERLAY_MINT, 0, 15));
        readyChip.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_current_status),
                getString(R.string.overlay_ready)));
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
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(roundedBackground(Color.rgb(237, 242, 236), 0, 14));
        Button plantingTab = overlayButton(getString(R.string.overlay_tab_planting));
        Button postcardTab = overlayButton(getString(R.string.overlay_tab_postcard));
        Button rewardTab = overlayButton(getString(R.string.overlay_tab_reward));
        Button returnRewardTab = overlayButton(getString(R.string.overlay_tab_return_reward));
        plantingTab.setTextSize(11);
        postcardTab.setTextSize(11);
        rewardTab.setTextSize(11);
        returnRewardTab.setTextSize(11);
        plantingTab.setContentDescription(getString(R.string.overlay_tab_planting_description));
        postcardTab.setContentDescription(getString(R.string.overlay_tab_postcard_description));
        rewardTab.setContentDescription(getString(R.string.overlay_tab_reward_description));
        returnRewardTab.setContentDescription(
                getString(R.string.overlay_tab_return_reward_description));
        tabs.addView(plantingTab, weightedButtonParams());
        tabs.addView(postcardTab, weightedButtonParams());
        tabs.addView(rewardTab, weightedButtonParams());
        tabs.addView(returnRewardTab, weightedButtonParams());
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
        plantingContent.addView(thresholdInput);

        plantingContent.addView(settingsSectionTitle(
                getString(R.string.overlay_section_flower_order),
                getString(R.string.overlay_flower_order_helper)));
        FlowerOrderEditor flowerEditor = new FlowerOrderEditor(settings.allowedFlowers());
        plantingContent.addView(flowerEditor);
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
            try {
                savePlanting.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
                error.requestFocus();
            }
        });
        toggleButton.setOnClickListener(view -> {
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

        LinearLayout postcardPage = new LinearLayout(this);
        postcardPage.setOrientation(LinearLayout.VERTICAL);
        postcardPage.setFocusableInTouchMode(true);
        LinearLayout postcardContent = new LinearLayout(this);
        postcardContent.setOrientation(LinearLayout.VERTICAL);
        postcardContent.setPadding(dp(12), dp(6), dp(12), dp(10));
        postcardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_postcard_settings_section), ""));

        StepperField postcardLimitInput = new StepperField(
                getString(R.string.overlay_collection_short),
                getString(R.string.overlay_collection_range),
                settings.postcardCollectionLimit(),
                0,
                15);
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
            try {
                savePostcard.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                postcardError.setText(exception.getMessage());
                postcardError.requestFocus();
            }
        });
        postcardToggle.setOnClickListener(view -> {
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
        rewardContent.addView(settingsStatusCard(
                rewardStatusView,
                getString(R.string.overlay_reward_status_summary)));

        StepperField rewardCountInput = new StepperField(
                getString(R.string.overlay_reward_count_section),
                getString(R.string.overlay_reward_count_helper),
                settings.expeditionDispatchCount(),
                1,
                99);
        rewardContent.addView(rewardCountInput, matchWidthParams(dp(72), dp(4)));

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
            try {
                saveReward.run();
                closeSettingsOverlay(true);
            } catch (IllegalArgumentException exception) {
                rewardError.setText(exception.getMessage());
                rewardError.requestFocus();
            }
        });
        rewardStart.setOnClickListener(view -> {
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
        returnRewardContent.addView(settingsStatusCard(
                returnRewardStatusView,
                getString(R.string.overlay_return_reward_status_summary)));
        returnRewardContent.addView(settingsSectionTitle(
                getString(R.string.overlay_return_reward_postcard_section),
                getString(R.string.overlay_return_reward_postcard_helper)));
        ReturnPostcardActionSelector returnPostcardAction =
                new ReturnPostcardActionSelector(settings.receiveReturnedPostcards());
        returnRewardContent.addView(returnPostcardAction);
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
                returnPostcardAction.receive());
        returnRewardSave.setOnClickListener(view -> {
            saveReturnReward.run();
            closeSettingsOverlay(true);
        });
        returnRewardStart.setOnClickListener(view -> {
            saveReturnReward.run();
            boolean receive = returnPostcardAction.receive();
            closeSettingsOverlay(false);
            handler.postDelayed(() -> startReturnRewardCollection(receive), 180L);
        });
        returnRewardPage.addView(settingsFooter(returnRewardSave, returnRewardStart));
        returnRewardPage.setVisibility(View.GONE);

        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panel.addView(plantingPage, pageParams);
        panel.addView(postcardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(rewardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(returnRewardPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        plantingTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.VISIBLE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(plantingTab, postcardTab, rewardTab, returnRewardTab);
            status = statusView;
            toggle = toggleButton;
            plantingPage.requestFocus();
        });
        postcardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.VISIBLE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(postcardTab, plantingTab, rewardTab, returnRewardTab);
            status = null;
            toggle = postcardToggle;
            postcardPage.requestFocus();
        });
        rewardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.VISIBLE);
            returnRewardPage.setVisibility(View.GONE);
            setSelectedTab(rewardTab, plantingTab, postcardTab, returnRewardTab);
            status = rewardStatusView;
            toggle = rewardStart;
            rewardPage.requestFocus();
        });
        returnRewardTab.setOnClickListener(view -> {
            plantingPage.setVisibility(View.GONE);
            postcardPage.setVisibility(View.GONE);
            rewardPage.setVisibility(View.GONE);
            returnRewardPage.setVisibility(View.VISIBLE);
            setSelectedTab(returnRewardTab, plantingTab, postcardTab, rewardTab);
            status = returnRewardStatusView;
            toggle = returnRewardStart;
            returnRewardPage.requestFocus();
        });
        setSelectedTab(plantingTab, postcardTab, rewardTab, returnRewardTab);

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

    /** 建立數字欄位並統一設定輸入尺寸。 */
    private EditText numberField(int value) {
        EditText field = new EditText(this);
        field.setText(String.valueOf(value));
        field.setTextSize(16);
        field.setTextColor(Color.rgb(32, 48, 38));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setMinHeight(dp(50));
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT);
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
        view.setTextSize(10);
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
        OverlayRunStatus completedStatus = plantingNoticeStatus;
        handler.postDelayed(() -> {
            if (!running && plantingNoticeStatus == completedStatus) {
                clearPlantingNotice();
            }
        }, 2600L);
    }

    /** 停止所有掃描排程並將流程狀態重設為可重新開始。 */
    private void pause(String message) {
        finishUsageSession("paused");
        runGeneration++;
        running = false;
        automationMode = AutomationMode.NONE;
        expeditionDispatchSession = null;
        dispatchCurrentItemKind = null;
        dispatchColorSelected = false;
        dispatchPikminSelected = false;
        dispatchSearchOpened = false;
        dispatchSearchInputAttempts = 0;
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
        dispatchUnknownFrames = 0;
        returnRewardScanGuard.reset();
        returnRewardStartedAt = 0L;
        returnRewardLastTapAt = 0L;
        resetReturnRewardPostcard();
        switchGuard.reset();
        resetPlantingSearch();
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        postcardUnknownFrames = 0;
        postcardMissingControlFrames = 0;
        postcardReceiptWaitFrames = 0;
        postcardBackAttempts = 0;
        automationStep = AutomationStep.MONITORING;
        targetFlower = "";
        actionAttempts = 0;
        startMissingConfirmations = 0;
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

        TextView notice = formText(message, 12, Color.WHITE);
        notice.setMaxLines(3);
        notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(10), dp(6), dp(10), dp(6));
        notice.setBackground(roundedBackground(OVERLAY_GREEN, 0, 10));
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.setContentDescription(message);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        int maxX = Math.max(dp(8), getResources().getDisplayMetrics().widthPixels - dp(230));
        params.x = Math.min(maxX, overlayParams.x + dp(44));
        params.y = Math.max(dp(16), overlayParams.y);
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

    /** 將流程狀態收斂到 32dp 圖示的邊框與無障礙描述。 */
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
        int tone = plantingNoticeStatus == null
                ? OVERLAY_GREEN
                : runStatusTone(plantingNoticeStatus.kind());
        overlay.setBackground(roundedBackground(OVERLAY_SURFACE, tone, 10));
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

    /** 停止自動化時清除狀態，並把 32dp 圖示恢復成待命色。 */
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

    /** Concept v2 的數字步進列，同時保留鍵盤直接輸入。 */
    private final class StepperField extends LinearLayout {
        private final EditText input;
        private final int minimum;
        private final int maximum;
        private final String label;

        StepperField(String label, String helper, int initialValue, int minimum, int maximum) {
            super(PetalAccessibilityService.this);
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(8), dp(10), dp(8));
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

            Button decrease = compactIconButton("−", getString(R.string.overlay_decrease));
            decrease.setOnClickListener(view -> adjust(-1));
            addView(decrease, new LinearLayout.LayoutParams(dp(48), dp(48)));

            input = numberField(initialValue);
            input.setGravity(Gravity.CENTER);
            input.setPadding(dp(2), 0, dp(2), 0);
            input.setContentDescription(getString(
                    R.string.overlay_value_description, label, initialValue));
            input.setBackgroundColor(Color.TRANSPARENT);
            addView(input, new LinearLayout.LayoutParams(dp(58), dp(48)));

            Button increase = compactIconButton("+", getString(R.string.overlay_increase));
            increase.setOnClickListener(view -> adjust(1));
            addView(increase, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        String valueText() {
            return input.getText().toString();
        }

        EditText input() {
            return input;
        }

        private void adjust(int delta) {
            int current;
            try {
                current = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException exception) {
                current = minimum;
            }
            int next = Math.max(minimum, Math.min(maximum, current + delta));
            input.setText(String.valueOf(next));
            input.setSelection(input.length());
            input.setContentDescription(getString(
                    R.string.overlay_value_description, label, next));
            input.announceForAccessibility(getString(
                    R.string.overlay_value_description, label, next));
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
        private final LinearLayout selectedRows;
        private final List<Button> colorButtons = new ArrayList<>();
        private final Spinner flowerSpinner;
        private final Button addButton;
        private List<String> availableFlowers = List.of();
        private int selectedCategoryIndex;

        FlowerOrderEditor(List<String> flowers) {
            super(PetalAccessibilityService.this);
            setOrientation(VERTICAL);
            selection = new PetalSelection(flowers);

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

            TextView flower = formText(selection.get(index), 14, Color.rgb(35, 75, 54));
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
