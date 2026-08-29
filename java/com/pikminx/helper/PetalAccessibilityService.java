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
    private static final long RETURN_REWARD_SCAN_DELAY_MILLIS = 1000L;
    private static final long RETURN_REWARD_AFTER_TAP_DELAY_MILLIS = 1000L;
    private static final long RETURN_REWARD_SETTLE_MILLIS = 1500L;
    private static final long RETURN_REWARD_PERSISTENT_TARGET_REARM_MILLIS = 3000L;
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
        CHECKING_PLANTING_ENTRY,
        WAITING_INITIAL_PLANTING_MENU,
        MONITORING,
        REVEALING_SEARCH_PANEL,
        OPENING_SEARCH,
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
    private final ObservationStability postcardPotStability =
            new ObservationStability(2, 2, 0.08f, 0.07f);
    private int postcardPetalSearchMissingFrames;
    private int postcardPetalInputAttempts;
    private int postcardKeyboardCloseAttempts;
    private int postcardKeyboardAbsentFrames;
    private final ObservationStability plantingPotStability =
            new ObservationStability(2, 2, 0.08f, 0.07f);
    private final ObservationStability plantingEntryStability =
            new ObservationStability(2, 1, 0.025f, 0.025f);
    private final ObservationStability plantingMenuStability =
            new ObservationStability(2, 0, 0.01f, 0.01f);
    private final ObservationStability plantingActiveStability =
            new ObservationStability(2, 1, 0.01f, 0.01f);
    private int plantingSearchMissingFrames;
    private int plantingMonitorMissingFrames;
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
    private boolean dispatchSearchTextConfirmed;
    private int dispatchSearchOpenAttempts;
    private int dispatchSearchInputAttempts;
    private int dispatchKeyboardCloseAttempts;
    private int dispatchKeyboardAbsentFrames;
    private int dispatchPikminTapIndex;
    private int dispatchAutoTapAttempts;
    private int dispatchAutoResultMissingFrames;
    private int dispatchAutoControlMissingFrames;
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
    private int stopMissingConfirmations;
    private int plantingTransitionFrames;
    private boolean plantingStartTapped;
    private boolean startAfterSelection;
    private boolean selectionFromSearch;
    private int targetSelectionX;
    private int targetSelectionY;
    private long captureSequence;
    private final ThreadLocal<CaptureGeometry> handlingCaptureGeometry = new ThreadLocal<>();
    private long runGeneration;
    private String recentPackage = "";
    private long recentPackageAt;
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
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoControlMissingFrames = 0;
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
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_return_reward_left_game));
            return;
        }
        runGeneration++;
        running = true;
        busy = false;
        automationMode = AutomationMode.RETURN_REWARD;
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
            takeScreenshotOfWindow(
                    root.getWindowId(),
                    getMainExecutor(),
                    screenshotCallback(
                            List.of(), generation, CaptureGeometry.Mode.WINDOW,
                            captureBounds(gameBounds), captureBounds(gameBounds),
                            Display.DEFAULT_DISPLAY, sequence));
            return;
        }

        // Android 13 and older can only capture the whole display. Keep the
        // visible overlays stable, then remove their pixels from the copy used
        // by OCR so the user never sees a hide/show cycle.
        List<ScreenshotOverlayMask.Region> overlayRegions = captureVisibleOverlayRegions();
        takeScreenshot(
                Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                screenshotCallback(
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
                        sequence));
    }

    /** 建立截圖回呼，統一處理 bitmap、OCR 與失敗重試。 */
    private TakeScreenshotCallback screenshotCallback(
            List<ScreenshotOverlayMask.Region> overlayRegions,
            long generation,
            CaptureGeometry.Mode captureMode,
            CaptureGeometry.Bounds expectedSourceBoundsOnScreen,
            CaptureGeometry.Bounds targetWindowBoundsOnScreen,
            int displayId,
            long sequence) {
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
                CaptureGeometry captureGeometry = new CaptureGeometry(
                        captureMode,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        expectedSourceBoundsOnScreen,
                        targetWindowBoundsOnScreen,
                        displayId,
                        sequence,
                        result.getTimestamp());
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
                OcrScan.Profile profile = ocrProfileForCurrentStep();
                OcrScanner.FrameCallback ocrCallback = new OcrScanner.FrameCallback() {
                    @Override
                    public void onSuccess(OcrScan.Frame frame) {
                        if (!isActiveRun(generation)) {
                            bitmap.recycle();
                            return;
                        }
                        busy = false;
                        try {
                            // 地圖探測會由 OCR 回呼同步使用這張截圖；不可在 Scanner 端提早釋放。
                            runWithCaptureGeometry(
                                    frame.captureGeometry(), () -> handleTokens(frame, bitmap));
                        } finally {
                            bitmap.recycle();
                        }
                    }

                    @Override
                    public void onFailure(Exception error) {
                        try {
                            if (isActiveRun(generation)) {
                            }
                            scanFailed(getString(R.string.status_ocr_failed), generation);
                        } finally {
                            bitmap.recycle();
                        }
                    }
                };
                scanner.scan(bitmap, profile, captureGeometry, getMainExecutor(), ocrCallback);
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
    private OcrScan.Profile ocrProfileForCurrentStep() {
        if (automationMode == AutomationMode.RETURN_REWARD) {
            return OcrScan.Profile.FULL_CHINESE;
        }
        if (automationMode == AutomationMode.DISPATCH) {
            return OcrScan.Profile.FULL_CHINESE;
        }
        if (automationMode == AutomationMode.PLANTING) {
            boolean chineseOnly = automationStep == AutomationStep.REVEALING_SEARCH_PANEL
                    || automationStep == AutomationStep.OPENING_SEARCH
                    || automationStep == AutomationStep.ENTERING_SEARCH
                    || automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD
                    || automationStep == AutomationStep.SELECTING_SEARCH_RESULT
                    || automationStep == AutomationStep.CLOSING_SEARCH_AFTER_SELECTION;
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
            // 搜尋後才以最新畫面的播放／停止鈕判斷是否已在種花。
            beginPlantingFlowerSearch(firstFlower, 0, true);
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
        busy = true;
        setRunStatus(
                AutomationMode.DISPATCH,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_reward_scanning),
                getString(R.string.overlay_reward_safety_items));
        scanner.scan(
                bitmap,
                OcrScan.Profile.DISPATCH_LIST,
                captureGeometry,
                getMainExecutor(),
                new OcrScanner.FrameCallback() {
            @Override
            public void onSuccess(OcrScan.Frame frame) {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
                if (!isActiveRun(generation)
                        || expeditionDispatchSession == null
                        || expeditionDispatchSession.stage()
                                != ExpeditionDispatchSession.Stage.LIST_SEARCH) {
                    return;
                }
                busy = false;
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
            }

            @Override
            public void onFailure(Exception error) {
                if (isActiveRun(generation)) {
                    busy = false;
                    handleDispatchListMiss(
                            listStartVisible, android.os.SystemClock.elapsedRealtime());
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
        ExpeditionScreenAnalyzer.Point action = ExpeditionScreenAnalyzer.findDetailAction(
                tokens, bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
        if (expeditionDispatchSession.transitionPending()) {
            if (action != null && expeditionDispatchSession.shouldRetryDetailTap(screen, now)) {
                ExpeditionDispatchSession.Confirmation retryConfirmation =
                        expeditionDispatchSession.confirm(
                                "DETAIL_RETRY:" + action.x() / 24 + ":" + action.y() / 24,
                                now);
                if (!handleDispatchConfirmation(retryConfirmation)) {
                    waitForDispatchFrame(getString(R.string.status_reward_go_explore_retrying));
                    return;
                }
                dispatchDetailActionTap(action, now, true);
                return;
            }
            ExpeditionDispatchSession.Confirmation timeout =
                    expeditionDispatchSession.confirm("", now);
            if (!handleDispatchConfirmation(timeout)) {
                waitForDispatchFrame(getString(R.string.status_reward_go_explore_waiting));
            }
            return;
        }
        if (screen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            waitForDispatchFrame(getString(R.string.status_reward_opening_detail));
            return;
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
        dispatchDetailActionTap(action, now, false);
    }

    private void dispatchDetailActionTap(
            ExpeditionScreenAnalyzer.Point action, long now, boolean retry) {
        if (!expeditionDispatchSession.beginDetailTapTransition(now)) {
            stopWithError(getString(R.string.status_reward_stage_timeout));
            return;
        }
        dispatchActionTap(
                action,
                getString(retry
                        ? R.string.status_reward_go_explore_retrying
                        : R.string.status_reward_go_explore),
                () -> {
                    setStatus(getString(R.string.status_reward_go_explore_waiting));
                    setRunStatus(
                            AutomationMode.DISPATCH,
                            OverlayRunStatus.Kind.RECOGNIZING,
                            getString(R.string.status_reward_go_explore_waiting),
                            getString(R.string.overlay_reward_safety_items));
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
                ExpeditionScreenAnalyzer.Point visibleGo =
                        ExpeditionScreenAnalyzer.findPikminGoButton(
                                tokens, bitmap.getWidth(), bitmap.getHeight());
                int selectedCount = ExpeditionScreenAnalyzer.selectedPikminCount(tokens);
                if (selectedCount > 0 || visibleGo != null) {
                    String evidenceKey = "AUTO_SELECTED:" + selectedCount + ":"
                            + (visibleGo == null ? "NO_GO"
                                    : visibleGo.x() / 24 + ":" + visibleGo.y() / 24);
                    ExpeditionDispatchSession.Confirmation selectedConfirmation =
                            expeditionDispatchSession.confirm(evidenceKey, now);
                    if (!handleDispatchConfirmation(selectedConfirmation)) {
                        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                        return;
                    }
                    dispatchPikminSelected = true;
                    dispatchAutoTapAttempts = 0;
                    dispatchAutoResultMissingFrames = 0;
                    dispatchAutoControlMissingFrames = 0;
                    expeditionDispatchSession.recordProgress(now);
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                if (dispatchAutoTapAttempts > 0 && dispatchAutoResultMissingFrames < 2) {
                    dispatchAutoResultMissingFrames++;
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                if (dispatchAutoTapAttempts >= MAX_ACTION_ATTEMPTS) {
                    stopWithError(getString(R.string.status_reward_selection_missing));
                    return;
                }
                ExpeditionScreenAnalyzer.Point automatic =
                        ExpeditionScreenAnalyzer.findPikminAutoButton(
                                tokens, bitmap.getWidth(), bitmap.getHeight());
                if (automatic == null) {
                    dispatchAutoControlMissingFrames++;
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "DISPATCH_AUTO_MISSING "
                                + ExpeditionScreenAnalyzer.pikminAutoDiagnostic(
                                        tokens, bitmap.getWidth(), bitmap.getHeight()));
                    }
                    if (dispatchAutoControlMissingFrames >= MAX_ACTION_ATTEMPTS) {
                        stopWithError(getString(R.string.status_reward_selection_missing));
                    } else {
                        waitForDispatchFrame(
                                getString(R.string.status_reward_selection_retrying));
                    }
                    return;
                }
                dispatchAutoControlMissingFrames = 0;
                ExpeditionDispatchSession.Confirmation autoConfirmation =
                        expeditionDispatchSession.confirm(
                                "AUTO:" + automatic.x() / 24 + ":" + automatic.y() / 24,
                                now);
                if (!handleDispatchConfirmation(autoConfirmation)) {
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                }
                dispatchAutoTapAttempts++;
                dispatchAutoResultMissingFrames = 0;
                dispatchActionTap(
                        automatic,
                        getString(R.string.status_reward_selecting_pikmin),
                        () -> {});
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
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoControlMissingFrames = 0;
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
        if (!gameEditableTextMatches(label)) {
            dispatchSearchOpened = false;
            dispatchSearchTextConfirmed = false;
            dispatchKeyboardAbsentFrames = 0;
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
        if (action == PlantingFlowPolicy.EntryAction.BEGIN_POT_SEARCH) {
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
            automationStep = AutomationStep.MONITORING;
            setPlantingNoticeText(
                    getString(R.string.status_planting_menu_ready), false);
            schedule(300);
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
        if (entryAction == PlantingFlowPolicy.EntryAction.BEGIN_POT_SEARCH) {
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
                automationStep = AutomationStep.MONITORING;
                setPlantingNoticeText(
                        getString(R.string.status_planting_menu_ready), false);
                schedule(300);
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
            case BEGIN_POT_SEARCH, WAIT_FOR_SCREEN -> null;
        };
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
            List<PetalMatcher.Token> tokens, Bitmap bitmap, OcrScan.Frame frame) {
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
        scanFocusedPlantingPetalRegion(bitmap, false);
    }

    private void scanFocusedPlantingMonitorRegion(Bitmap bitmap) {
        scanFocusedPlantingPetalRegion(bitmap, true);
    }

    private void scanFocusedPlantingPetalRegion(Bitmap bitmap, boolean monitorRead) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CaptureGeometry captureGeometry = currentCaptureGeometry();
        long generation = runGeneration;
        String scanTarget = monitorRead ? currentFlower : targetFlower;
        busy = true;
        setRunStatus(
                AutomationMode.PLANTING,
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_flower_search_focused_ocr),
                scanTarget);
        scanner.scan(
                bitmap,
                OcrScan.Profile.PETAL_LIST,
                captureGeometry,
                getMainExecutor(),
                new OcrScanner.FrameCallback() {
            @Override
            public void onSuccess(OcrScan.Frame frame) {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
                if (!isActiveRun(generation)) {
                    return;
                }
                busy = false;
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
                PetalMatcher.Selection pot = PetalMatcher.findSearchedFlower(
                        frame.tokens(),
                        targetFlower,
                        plantingSearchMinimumCount,
                        width,
                        height);
                if (pot == null) {
                    handlePlantingSearchMiss();
                } else {
                    confirmPlantingSearchResult(pot, width, height);
                }
                });
            }

            @Override
            public void onFailure(Exception error) {
                if (isActiveRun(generation)) {
                    busy = false;
                    if (monitorRead) {
                        setPlantingNoticeText(
                                getString(R.string.overlay_planting_unreadable, scanTarget), false);
                        scheduleNext();
                    } else {
                        handlePlantingSearchMiss();
                    }
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
        schedule(700);
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
            schedule(700);
            return;
        }
        boolean shouldStart = startAfterSelection;
        resetPlantingSearch();
        tapFlower(pot, shouldStart, true);
    }

    /** 清除自動種花搜尋流程的暫存，不修改目前設定中的目標花名。 */
    private void resetPlantingSearch() {
        plantingPotStability.reset();
        plantingSearchMissingFrames = 0;
        plantingSearchInputAttempts = 0;
        plantingKeyboardCloseAttempts = 0;
        plantingKeyboardAbsentFrames = 0;
        plantingSearchMinimumCount = 0;
    }

    private void resetPlantingNavigation() {
        plantingEntryStability.reset();
        plantingMenuStability.reset();
        plantingActiveStability.reset();
        plantingTransitionFrames = 0;
        plantingMonitorMissingFrames = 0;
        stopMissingConfirmations = 0;
        plantingStartTapped = false;
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
        plantingStartTapped = true;
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
        recordPlantingStartIfNeeded(newlyStarted);
        resetPlantingNavigation();
        scheduleNext();
    }

    /** 返回種花面板後，再搜尋一次當前花盆，讓數量卡回到可監控位置。 */
    private void resumePlantingAfterMenuReturn() {
        recordPlantingStartIfNeeded(true);
        startAfterSelection = false;
        actionAttempts = 0;
        resetPlantingNavigation();
        if (currentFlower.isEmpty()) {
            automationStep = AutomationStep.MONITORING;
            scheduleNext();
            return;
        }
        beginPlantingFlowerSearch(currentFlower, 0, false);
    }

    private void recordPlantingStartIfNeeded(boolean newlyStarted) {
        plantingStartTapped = false;
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

        ReturnRewardDetector.Target rewardTarget = ReturnRewardDetector.find(
                width, height, bitmap::getPixel);

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
        ReturnRewardScanGuard.Decision decision = returnRewardScanGuard.observe(
                postcardPage,
                rewardTarget,
                width,
                height,
                persistentTargetRearmEligible);
        if (decision == ReturnRewardScanGuard.Decision.TARGET_CONFIRMED) {
            handleReturnRewardTarget(rewardTarget, width, height);
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
        busy = true;
        setPostcardStatus(
                OverlayRunStatus.Kind.RECOGNIZING,
                getString(R.string.status_postcard_focused_petal_ocr),
                postcardAutomation.petalPotName());
        scanner.scan(
                bitmap,
                OcrScan.Profile.PETAL_LIST,
                captureGeometry,
                getMainExecutor(),
                new OcrScanner.FrameCallback() {
            @Override
            public void onSuccess(OcrScan.Frame frame) {
                runWithCaptureGeometry(frame.captureGeometry(), () -> {
                if (!isActiveRun(generation)) {
                    return;
                }
                busy = false;
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
            }

            @Override
            public void onFailure(Exception error) {
                if (isActiveRun(generation)) {
                    busy = false;
                    handleFocusedPetalMiss();
                }
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
        if (settingsOverlay != null || overlay == null || windowManager == null) {
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

        TextView title = formText(
                getString(R.string.overlay_brand_title) + " " + BuildConfig.VERSION_NAME,
                20,
                Color.rgb(23, 59, 42));
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

    /** 停止流程後保留錯誤卡，讓原因不會在短暫提示後消失。 */
    private void stopWithError(String message) {
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
        runGeneration++;
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
        dispatchKeyboardCloseAttempts = 0;
        dispatchKeyboardAbsentFrames = 0;
        dispatchPikminTapIndex = 0;
        dispatchAutoTapAttempts = 0;
        dispatchAutoResultMissingFrames = 0;
        dispatchAutoControlMissingFrames = 0;
        dispatchUnknownFrames = 0;
        returnRewardScanGuard.reset();
        returnRewardStartedAt = 0L;
        returnRewardLastTapAt = 0L;
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
