package com.pikminx.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.mlkit.common.MlKitException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/* JADX INFO: loaded from: classes.dex */
public final class PetalAccessibilityService extends AccessibilityService {
    private static final long DISPATCH_AFTER_SCROLL_DELAY_MILLIS = 250;
    private static final long DISPATCH_AFTER_TAP_DELAY_MILLIS = 1600;
    private static final long DISPATCH_PIKMIN_TAP_DELAY_MILLIS = 350;
    private static final long DISPATCH_SCAN_DELAY_MILLIS = 850;
    private static final long GAME_ACTION_TAP_DURATION_MILLIS = 180;
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final int MAX_ACTION_ATTEMPTS = 3;
    private static final int OVERLAY_PADDING_DP = 5;
    static final int OVERLAY_SIZE_DP = 40;
    private static final long POSTCARD_FAST_SCAN_DELAY_MILLIS = 450;
    private static final long POSTCARD_MIN_SCAN_DELAY_MILLIS = 1200;
    private static final long POSTCARD_PETAL_STEP_DELAY_MILLIS = 1000;
    private static final long RETURN_REWARD_SCAN_DELAY_MILLIS = 300;
    private static final long RETURN_REWARD_SETTLE_MILLIS = 1500;
    private static final long RETURN_REWARD_TIMEOUT_MILLIS = 300000;
    private static final long SCAN_INTERVAL_MILLIS = 3000;
    private static final String TAG = "PikminX";
    private int actionAttempts;
    private boolean busy;
    private boolean dispatchColorSelected;
    private int dispatchKeyboardAbsentFrames;
    private int dispatchKeyboardCloseAttempts;
    private boolean dispatchPikminSelected;
    private int dispatchPikminTapIndex;
    private int dispatchSearchInputAttempts;
    private boolean dispatchSearchOpened;
    private int dispatchUnknownFrames;
    private ExpeditionDispatchSession expeditionDispatchSession;
    private View noticeOverlay;
    private WindowManager.LayoutParams noticeParams;
    private View overlay;
    private WindowManager.LayoutParams overlayParams;
    private int plantingKeyboardAbsentFrames;
    private int plantingKeyboardCloseAttempts;
    private OverlayRunStatus plantingNoticeStatus;
    private PetalMatcher.Selection plantingPendingPot;
    private int plantingPotConfirmations;
    private int plantingPotMissingFrames;
    private int plantingSearchInputAttempts;
    private int plantingSearchMinimumCount;
    private int plantingSearchMissingFrames;
    private int postcardBackAttempts;
    private int postcardKeyboardAbsentFrames;
    private int postcardKeyboardCloseAttempts;
    private int postcardMissingControlFrames;
    private PostcardMatcher.PetalPot postcardPendingPot;
    private int postcardPetalInputAttempts;
    private int postcardPetalSearchMissingFrames;
    private int postcardPikminCountConfirmations;
    private int postcardPotConfirmations;
    private int postcardPotMissingFrames;
    private int postcardReceiptWaitFrames;
    private int postcardUnknownFrames;
    private long recentPackageAt;
    private long returnRewardLastTapAt;
    private int returnRewardPostcardAttempts;
    private int returnRewardPostcardConfirmations;
    private PostcardMatcher.Target returnRewardPostcardTarget;
    private long returnRewardStartedAt;
    private boolean returnRewardWaitingPostcardExit;
    private long runGeneration;
    private boolean running;
    private OcrScanner scanner;
    private boolean selectionFromSearch;
    private SettingsStore settings;
    private View settingsOverlay;
    private boolean startAfterSelection;
    private int startMissingConfirmations;
    private TextView status;
    private int targetCount;
    private int targetSelectionX;
    private int targetSelectionY;
    private Button toggle;
    private WindowManager windowManager;
    private static final long POSTCARD_VERIFY_DELAY_MILLIS = 1800;
    private static final long POSTCARD_RECEIPT_RETURN_VERIFY_DELAY_MILLIS = PostcardTiming.receiptReturnDelayMillis(POSTCARD_VERIFY_DELAY_MILLIS);
    private static final long RETURN_REWARD_AFTER_TAP_DELAY_MILLIS = 900;
    private static final long POSTCARD_RECEIPT_EXIT_DELAY_MILLIS = PostcardTiming.receiptReturnDelayMillis(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
    private static final int OVERLAY_GREEN = Color.rgb(25, 92, 57);
    private static final int OVERLAY_SURFACE = Color.rgb(251, 253, 249);
    private static final int OVERLAY_BORDER = Color.rgb(198, 220, MlKitException.CODE_SCANNER_CANCELLED);
    private static final int OVERLAY_MUTED = Color.rgb(78, 96, 83);
    private static final int OVERLAY_MINT = Color.rgb(229, 244, 232);
    private static final int OVERLAY_CREAM = Color.rgb(255, 249, 237);
    private static final int OVERLAY_ACCENT = Color.rgb(181, 63, 43);
    private static final int OVERLAY_WARNING = Color.rgb(156, 39, 39);
    private static final int OVERLAY_SEARCH = Color.rgb(62, 113, 137);
    private static final int OVERLAY_RECOGNIZING = Color.rgb(167, 120, 33);
    private static WeakReference<PetalAccessibilityService> connectedService = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTask = new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda46
        @Override // java.lang.Runnable
        public final void run() {
            this.f$0.requestScan();
        }
    };
    private final SwitchGuard switchGuard = new SwitchGuard();
    private final PostcardAutomation postcardAutomation = new PostcardAutomation();
    private final PostcardReturnGuard postcardReturnGuard = new PostcardReturnGuard();
    private AutomationMode automationMode = AutomationMode.NONE;
    private int postcardLastPikminCount = -1;
    private ExpeditionTargetMode expeditionTargetMode = ExpeditionTargetMode.FRUIT_AND_POT;
    private DispatchSelectionMethod dispatchSelectionMethod = DispatchSelectionMethod.AUTO;
    private DispatchPikminType dispatchPikminType = DispatchPikminType.MIXED;
    private final ReturnRewardScanGuard returnRewardScanGuard = new ReturnRewardScanGuard();
    private boolean returnRewardReceivePostcard = true;
    private String currentFlower = "";
    private AutomationStep automationStep = AutomationStep.MONITORING;
    private String targetFlower = "";
    private String recentPackage = "";

    private enum AutomationMode {
        NONE,
        PLANTING,
        POSTCARD,
        DISPATCH,
        RETURN_REWARD
    }

    private enum AutomationStep {
        MONITORING,
        REVEALING_SEARCH_PANEL,
        OPENING_SEARCH,
        ENTERING_SEARCH,
        CLOSING_SEARCH_KEYBOARD,
        SELECTING_SEARCH_RESULT,
        VERIFYING_SELECTION,
        WAITING_START,
        VERIFYING_START
    }

    static /* synthetic */ void lambda$handleDispatchDetail$2() {
    }

    static /* synthetic */ void lambda$handleDispatchListTarget$1() {
    }

    static /* synthetic */ void lambda$handleDispatchResult$5() {
    }

    static /* synthetic */ void lambda$handleDispatchSelection$4() {
    }

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = new WeakReference<>(this);
        this.settings = new SettingsStore(this);
        this.scanner = new OcrScanner();
        if (showOverlay()) {
            this.overlay.setVisibility(this.settings.overlayVisible() ? 0 : 8);
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        CharSequence packageName = accessibilityEvent.getPackageName();
        if (packageName != null) {
            this.recentPackage = packageName.toString();
            this.recentPackageAt = SystemClock.elapsedRealtime();
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
        pause(getString(R.string.status_service_interrupted));
    }

    @Override // android.app.Service
    public void onDestroy() {
        pause(getString(R.string.status_service_closed));
        if (connectedService.get() == this) {
            connectedService.clear();
        }
        OcrScanner ocrScanner = this.scanner;
        if (ocrScanner != null) {
            ocrScanner.close();
        }
        safeRemoveOverlayView(this.settingsOverlay, "settings");
        safeRemoveOverlayView(this.noticeOverlay, "notice");
        safeRemoveOverlayView(this.overlay, "icon");
        this.overlay = null;
        this.settingsOverlay = null;
        this.noticeOverlay = null;
        this.status = null;
        this.toggle = null;
        super.onDestroy();
    }

    static boolean isOverlayVisible() {
        View view;
        PetalAccessibilityService petalAccessibilityService = connectedService.get();
        return (petalAccessibilityService == null || (view = petalAccessibilityService.overlay) == null || view.getVisibility() != 0) ? false : true;
    }

    static boolean isConnected() {
        return connectedService.get() != null;
    }

    static boolean setOverlayVisible(boolean z) {
        PetalAccessibilityService petalAccessibilityService = connectedService.get();
        if (petalAccessibilityService == null) {
            return false;
        }
        if (petalAccessibilityService.overlay == null && !petalAccessibilityService.showOverlay()) {
            return false;
        }
        petalAccessibilityService.applyOverlayVisibility(z);
        return true;
    }

    private void applyOverlayVisibility(boolean z) {
        if (!z) {
            pause(getString(R.string.status_paused));
        }
        if (this.settingsOverlay != null) {
            closeSettingsOverlay(false);
        }
        View view = this.overlay;
        if (view != null) {
            view.setVisibility(z ? 0 : 8);
        }
        this.settings.setOverlayVisible(z);
    }

    private void startAutomation() {
        if (this.settings.allowedFlowers().isEmpty()) {
            setStatus(getString(R.string.status_need_flowers));
            return;
        }
        this.runGeneration++;
        this.running = true;
        this.automationMode = AutomationMode.PLANTING;
        this.busy = false;
        this.switchGuard.reset();
        resetPlantingSearch();
        this.currentFlower = "";
        this.automationStep = AutomationStep.MONITORING;
        this.targetFlower = "";
        this.targetCount = 0;
        this.actionAttempts = 0;
        this.startMissingConfirmations = 0;
        this.startAfterSelection = false;
        this.selectionFromSearch = false;
        this.targetSelectionX = 0;
        this.targetSelectionY = 0;
        Button button = this.toggle;
        if (button != null) {
            button.setText(R.string.action_pause);
            this.toggle.setContentDescription(getString(R.string.action_pause));
        }
        View view = this.overlay;
        if (view != null) {
            view.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_stop_description), getString(R.string.overlay_icon_move_hint)}));
        }
        setStatus(getString(R.string.status_waiting_menu));
        setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.overlay_planting_checking), getString(R.string.overlay_ocr_detail));
        schedule(200L);
    }

    private void startPostcardAutomation(int i, String str, int i2) {
        this.runGeneration++;
        this.running = true;
        this.busy = false;
        this.automationMode = AutomationMode.POSTCARD;
        this.postcardAutomation.start(i, str, i2);
        this.postcardReturnGuard.reset();
        this.postcardUnknownFrames = 0;
        this.postcardMissingControlFrames = 0;
        this.postcardReceiptWaitFrames = 0;
        this.postcardBackAttempts = 0;
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        this.postcardPikminCountConfirmations = 0;
        this.postcardLastPikminCount = -1;
        Button button = this.toggle;
        if (button != null) {
            button.setText(R.string.action_pause);
            this.toggle.setContentDescription(getString(R.string.action_pause));
        }
        View view = this.overlay;
        if (view != null) {
            view.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_stop_description), getString(R.string.overlay_icon_move_hint)}));
        }
        setPostcardStatus(getString(R.string.status_postcard_progress, new Object[]{Integer.valueOf(this.postcardAutomation.completedCount()), Integer.valueOf(this.postcardAutomation.collectionLimit())}));
        schedule(200L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: startExpeditionDispatch, reason: merged with bridge method [inline-methods] */
    public void lambda$showSettingsOverlay$44(int i, ExpeditionTargetMode expeditionTargetMode, DispatchSelectionMethod dispatchSelectionMethod, DispatchPikminType dispatchPikminType) {
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_reward_wrong_page));
            return;
        }
        this.runGeneration++;
        this.running = true;
        this.busy = false;
        this.automationMode = AutomationMode.DISPATCH;
        if (expeditionTargetMode == null) {
            expeditionTargetMode = ExpeditionTargetMode.FRUIT_AND_POT;
        }
        this.expeditionTargetMode = expeditionTargetMode;
        if (dispatchSelectionMethod == null) {
            dispatchSelectionMethod = DispatchSelectionMethod.AUTO;
        }
        this.dispatchSelectionMethod = dispatchSelectionMethod;
        if (dispatchPikminType == null) {
            dispatchPikminType = DispatchPikminType.MIXED;
        }
        this.dispatchPikminType = dispatchPikminType;
        this.expeditionDispatchSession = new ExpeditionDispatchSession(i, SystemClock.elapsedRealtime());
        this.dispatchColorSelected = this.dispatchPikminType == DispatchPikminType.MIXED;
        this.dispatchPikminSelected = false;
        this.dispatchSearchOpened = false;
        this.dispatchSearchInputAttempts = 0;
        this.dispatchKeyboardCloseAttempts = 0;
        this.dispatchKeyboardAbsentFrames = 0;
        this.dispatchPikminTapIndex = 0;
        this.dispatchUnknownFrames = 0;
        View view = this.overlay;
        if (view != null) {
            view.setVisibility(0);
            this.overlay.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_stop_description), getString(R.string.overlay_icon_move_hint)}));
        }
        setStatus(getString(R.string.status_reward_started));
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_reward_started), getString(R.string.status_reward_scanning));
        schedule(DISPATCH_SCAN_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: startReturnRewardCollection, reason: merged with bridge method [inline-methods] */
    public void lambda$showSettingsOverlay$48(boolean z) {
        if (activeGameBoundsStrict() == null) {
            showFloatingNotice(getString(R.string.status_return_reward_left_game));
            return;
        }
        this.runGeneration++;
        this.running = true;
        this.busy = false;
        this.automationMode = AutomationMode.RETURN_REWARD;
        this.returnRewardScanGuard.reset();
        this.returnRewardStartedAt = SystemClock.elapsedRealtime();
        this.returnRewardLastTapAt = 0L;
        this.returnRewardReceivePostcard = z;
        resetReturnRewardPostcard();
        View view = this.overlay;
        if (view != null) {
            view.setVisibility(0);
            this.overlay.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_stop_description), getString(R.string.overlay_icon_move_hint)}));
        }
        setStatus(getString(R.string.status_return_reward_started));
        setRunStatus(AutomationMode.RETURN_REWARD, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_return_reward_started), getString(R.string.overlay_return_reward_safety));
        schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestScan() {
        int i;
        if (!this.running || this.busy) {
            return;
        }
        if ((this.automationMode == AutomationMode.DISPATCH || this.automationMode == AutomationMode.RETURN_REWARD) && activeGameBoundsStrict() == null) {
            if (this.automationMode == AutomationMode.RETURN_REWARD) {
                i = R.string.status_return_reward_left_game;
            } else {
                i = R.string.status_reward_left_game;
            }
            stopWithError(getString(i));
            return;
        }
        if (!isGameForeground()) {
            setStatus(getString(R.string.status_waiting_game));
            setRunStatus(this.automationMode, OverlayRunStatus.Kind.IDLE, getString(R.string.status_waiting_game), getString(R.string.overlay_waiting_game_detail));
            schedule(RETURN_REWARD_SETTLE_MILLIS);
        } else {
            this.busy = true;
            takeGameScreenshot(this.runGeneration);
        }
    }

    private void takeGameScreenshot(long j) {
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (Build.VERSION.SDK_INT >= 34 && rootInActiveWindow != null && GAME_PACKAGE.contentEquals(rootInActiveWindow.getPackageName())) {
            takeScreenshotOfWindow(rootInActiveWindow.getWindowId(), getMainExecutor(), screenshotCallback(List.of(), j));
        } else {
            takeScreenshot(0, getMainExecutor(), screenshotCallback(captureVisibleOverlayRegions(), j));
        }
    }

    /* JADX INFO: renamed from: com.pikminx.helper.PetalAccessibilityService$1, reason: invalid class name */
    class AnonymousClass1 implements AccessibilityService.TakeScreenshotCallback {
        final /* synthetic */ long val$generation;
        final /* synthetic */ List val$overlayRegions;

        AnonymousClass1(long j, List list) {
            this.val$generation = j;
            this.val$overlayRegions = list;
        }

        @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
        public void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
            final Bitmap bitmapCopyBitmap = PetalAccessibilityService.this.copyBitmap(screenshotResult);
            if (!PetalAccessibilityService.this.isActiveRun(this.val$generation)) {
                if (bitmapCopyBitmap != null) {
                    bitmapCopyBitmap.recycle();
                    return;
                }
                return;
            }
            if (bitmapCopyBitmap == null) {
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                petalAccessibilityService.scanFailed(petalAccessibilityService.getString(R.string.status_copy_failed), this.val$generation);
                return;
            }
            PetalAccessibilityService.maskOverlayRegions(bitmapCopyBitmap, this.val$overlayRegions);
            if (PetalAccessibilityService.this.automationMode == AutomationMode.POSTCARD && PetalAccessibilityService.this.postcardAutomation.step() == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
                PetalAccessibilityService.this.busy = false;
                try {
                    PetalAccessibilityService.this.closePostcardKeyboard();
                    return;
                } finally {
                    bitmapCopyBitmap.recycle();
                }
            }
            if (PetalAccessibilityService.this.automationMode == AutomationMode.PLANTING && PetalAccessibilityService.this.automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
                PetalAccessibilityService.this.busy = false;
                try {
                    PetalAccessibilityService.this.closePlantingSearchKeyboard();
                    return;
                } finally {
                    bitmapCopyBitmap.recycle();
                }
            }
            if (PetalAccessibilityService.this.automationMode == AutomationMode.RETURN_REWARD) {
                int width = bitmapCopyBitmap.getWidth();
                int height = bitmapCopyBitmap.getHeight();
                Objects.requireNonNull(bitmapCopyBitmap);
                ReturnRewardDetector.Target targetFind = ReturnRewardDetector.find(width, height, new IntBinaryOperator() { // from class: com.pikminx.helper.PetalAccessibilityService$1$$ExternalSyntheticLambda0
                    @Override // java.util.function.IntBinaryOperator
                    public final int applyAsInt(int i, int i2) {
                        return bitmapCopyBitmap.getPixel(i, i2);
                    }
                });
                if (targetFind != null) {
                    PetalAccessibilityService.this.busy = false;
                    try {
                        PetalAccessibilityService.this.handleReturnRewardTarget(targetFind, bitmapCopyBitmap.getWidth(), bitmapCopyBitmap.getHeight());
                        return;
                    } finally {
                        bitmapCopyBitmap.recycle();
                    }
                }
            }
            OcrScanner.Callback callback = new OcrScanner.Callback() { // from class: com.pikminx.helper.PetalAccessibilityService.1.1
                @Override // com.pikminx.helper.OcrScanner.Callback
                public void onSuccess(List<PetalMatcher.Token> list) {
                    if (!PetalAccessibilityService.this.isActiveRun(AnonymousClass1.this.val$generation)) {
                        bitmapCopyBitmap.recycle();
                        return;
                    }
                    PetalAccessibilityService.this.busy = false;
                    try {
                        PetalAccessibilityService.this.handleTokens(list, bitmapCopyBitmap);
                    } finally {
                        bitmapCopyBitmap.recycle();
                    }
                }

                @Override // com.pikminx.helper.OcrScanner.Callback
                public void onFailure(Exception exc) {
                    try {
                        PetalAccessibilityService.this.scanFailed(PetalAccessibilityService.this.getString(R.string.status_ocr_failed), AnonymousClass1.this.val$generation);
                    } finally {
                        bitmapCopyBitmap.recycle();
                    }
                }
            };
            if (PetalAccessibilityService.this.shouldUseFastChineseOcr()) {
                PetalAccessibilityService.this.scanner.scanChinese(bitmapCopyBitmap, PetalAccessibilityService.this.getMainExecutor(), callback);
            } else {
                PetalAccessibilityService.this.scanner.scan(bitmapCopyBitmap, PetalAccessibilityService.this.getMainExecutor(), callback);
            }
        }

        @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
        public void onFailure(int i) {
            if (PetalAccessibilityService.this.isActiveRun(this.val$generation)) {
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                petalAccessibilityService.scanFailed(petalAccessibilityService.getString(R.string.status_capture_failed, new Object[]{Integer.valueOf(i)}), this.val$generation);
            }
        }
    }

    private AccessibilityService.TakeScreenshotCallback screenshotCallback(List<ScreenshotOverlayMask.Region> list, long j) {
        return new AnonymousClass1(j, list);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean shouldUseFastChineseOcr() {
        if (this.automationMode == AutomationMode.RETURN_REWARD || this.automationMode == AutomationMode.DISPATCH) {
            return true;
        }
        if (this.automationMode == AutomationMode.PLANTING) {
            return this.automationStep == AutomationStep.REVEALING_SEARCH_PANEL || this.automationStep == AutomationStep.OPENING_SEARCH || this.automationStep == AutomationStep.ENTERING_SEARCH || this.automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD || this.automationStep == AutomationStep.SELECTING_SEARCH_RESULT;
        }
        if (this.automationMode != AutomationMode.POSTCARD) {
            return false;
        }
        switch (AnonymousClass8.$SwitchMap$com$pikminx$helper$PostcardAutomation$Step[this.postcardAutomation.step().ordinal()]) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                return true;
            default:
                return false;
        }
    }

    private List<ScreenshotOverlayMask.Region> captureVisibleOverlayRegions() {
        ArrayList arrayList = new ArrayList();
        addVisibleOverlayRegion(arrayList, this.overlay);
        addVisibleOverlayRegion(arrayList, this.noticeOverlay);
        return MainActivity$$ExternalSyntheticBackport0.m((Collection) arrayList);
    }

    private static void addVisibleOverlayRegion(List<ScreenshotOverlayMask.Region> list, View view) {
        if (view != null && view.getVisibility() == 0 && view.isAttachedToWindow()) {
            Rect rect = new Rect();
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty()) {
                return;
            }
            list.add(new ScreenshotOverlayMask.Region(rect.left, rect.top, rect.right, rect.bottom));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void maskOverlayRegions(final Bitmap bitmap, List<ScreenshotOverlayMask.Region> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int width = bitmap.getWidth();
        final int[] iArr = new int[width];
        ScreenshotOverlayMask.erase(width, bitmap.getHeight(), new ScreenshotOverlayMask.PixelBuffer() { // from class: com.pikminx.helper.PetalAccessibilityService.2
            @Override // com.pikminx.helper.ScreenshotOverlayMask.PixelBuffer
            public int get(int i, int i2) {
                return bitmap.getPixel(i, i2);
            }

            @Override // com.pikminx.helper.ScreenshotOverlayMask.PixelBuffer
            public void fillRow(int i, int i2, int i3, int i4) {
                int i5 = i3 - i2;
                Arrays.fill(iArr, 0, i5, i4);
                bitmap.setPixels(iArr, 0, i5, i2, i, i5, 1);
            }
        }, list, Math.max(4, Math.round(width * 0.01f)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Bitmap copyBitmap(AccessibilityService.ScreenshotResult screenshotResult) {
        HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
        try {
            Bitmap bitmapWrapHardwareBuffer = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.getColorSpace());
            return bitmapWrapHardwareBuffer == null ? null : bitmapWrapHardwareBuffer.copy(Bitmap.Config.ARGB_8888, true);
        } finally {
            hardwareBuffer.close();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleTokens(List<PetalMatcher.Token> list, final Bitmap bitmap) {
        String string;
        if (this.automationMode == AutomationMode.RETURN_REWARD) {
            handleReturnRewardTokens(list, bitmap.getWidth(), bitmap.getHeight());
            return;
        }
        if (this.automationMode == AutomationMode.DISPATCH) {
            handleExpeditionDispatch(list, bitmap);
            return;
        }
        if (this.automationMode == AutomationMode.POSTCARD) {
            handlePostcardTokens(list, bitmap);
            return;
        }
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        int iThreshold = this.settings.threshold();
        List<String> listAllowedFlowers = this.settings.allowedFlowers();
        if (isPlantingSearchStep()) {
            handlePlantingFlowerSearch(list, bitmap);
            return;
        }
        PetalMatcher.Selection selectionFindHighlightedFlower = PetalMatcher.findHighlightedFlower(list, PetalCatalog.petals(), width, height, new ToIntFunction() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda28
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PetalAccessibilityService.lambda$handleTokens$0(width, height, bitmap, (PetalMatcher.Selection) obj);
            }
        });
        Objects.requireNonNull(bitmap);
        CardHighlight.Point pointFindStartButton = CardHighlight.findStartButton(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        if (this.automationStep == AutomationStep.VERIFYING_SELECTION) {
            verifyFlowerSelection(selectionFindHighlightedFlower, bitmap);
            return;
        }
        if (this.automationStep == AutomationStep.WAITING_START) {
            startPlanting(list, width, height, pointFindStartButton);
            return;
        }
        if (this.automationStep == AutomationStep.VERIFYING_START) {
            verifyPlantingStarted(list, width, height, pointFindStartButton != null);
            return;
        }
        boolean z = pointFindStartButton != null || hasStartPlantingControl(list, width, height);
        String str = listAllowedFlowers.get(0);
        if (this.currentFlower.isEmpty() && (selectionFindHighlightedFlower == null || !str.equals(selectionFindHighlightedFlower.name()))) {
            beginPlantingFlowerSearch(str, 0, z);
            return;
        }
        if (!PetalMatcher.hasVisibleFlowerCard(list, PetalCatalog.petals(), width, height)) {
            setStatus(getString(R.string.status_waiting_menu));
            scheduleNext();
            return;
        }
        if (z) {
            if (selectionFindHighlightedFlower != null && str.equals(selectionFindHighlightedFlower.name())) {
                this.currentFlower = selectionFindHighlightedFlower.name();
                showPlantingStatus(selectionFindHighlightedFlower.name(), selectionFindHighlightedFlower.count());
                this.automationStep = AutomationStep.WAITING_START;
                this.actionAttempts = 0;
                startPlanting(list, width, height, pointFindStartButton);
                return;
            }
            beginPlantingFlowerSearch(str, 0, true);
            return;
        }
        if (selectionFindHighlightedFlower == null) {
            setStatus(getString(R.string.status_selected_not_visible));
            if (this.currentFlower.isEmpty()) {
                setPlantingNoticeText(getString(R.string.overlay_planting_checking), false);
            } else {
                PetalMatcher.Selection selectionFindFlower = PetalMatcher.findFlower(list, this.currentFlower, width, height);
                if (selectionFindFlower != null) {
                    showPlantingStatus(selectionFindFlower.name(), selectionFindFlower.count());
                } else {
                    setPlantingNoticeText(getString(R.string.overlay_planting_unreadable, new Object[]{this.currentFlower}), false);
                }
            }
            scheduleNext();
            return;
        }
        if (this.currentFlower.isEmpty()) {
            this.currentFlower = selectionFindHighlightedFlower.name();
        } else if (PetalMatcher.needsSelectionCorrection(this.currentFlower, selectionFindHighlightedFlower)) {
            beginPlantingFlowerSearch(this.currentFlower, 0, false);
            return;
        }
        int iCount = selectionFindHighlightedFlower.count();
        showPlantingStatus(selectionFindHighlightedFlower.name(), iCount);
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long jCooldownRemainingMillis = this.switchGuard.cooldownRemainingMillis(jElapsedRealtime);
        boolean zShouldSwitch = this.switchGuard.shouldSwitch(Integer.valueOf(iCount), iThreshold, jElapsedRealtime);
        if (jCooldownRemainingMillis > 0) {
            setStatus(getString(R.string.status_switch_cooldown, new Object[]{Long.valueOf((jCooldownRemainingMillis + 999) / POSTCARD_PETAL_STEP_DELAY_MILLIS)}));
            scheduleNext();
            return;
        }
        if (iCount > iThreshold) {
            if (this.currentFlower.isEmpty()) {
                string = getString(R.string.status_remaining, new Object[]{Integer.valueOf(iCount), Integer.valueOf(iThreshold)});
            } else {
                string = getString(R.string.status_current_remaining, new Object[]{this.currentFlower, Integer.valueOf(iCount)});
            }
            setStatus(string);
            scheduleNext();
            return;
        }
        if (!zShouldSwitch) {
            setStatus(getString(R.string.status_confirming_low, new Object[]{Integer.valueOf(iCount), Integer.valueOf(this.switchGuard.confirmations()), 2}));
            scheduleNext();
            return;
        }
        String strNextTarget = PetalMatcher.nextTarget(listAllowedFlowers, this.currentFlower);
        if (strNextTarget == null) {
            finishWithSuccess(getString(R.string.status_sequence_complete, new Object[]{this.currentFlower}));
        } else {
            beginPlantingFlowerSearch(strNextTarget, iThreshold + 1, false);
        }
    }

    static /* synthetic */ int lambda$handleTokens$0(int i, int i2, Bitmap bitmap, PetalMatcher.Selection selection) {
        int iX = selection.x();
        int iY = selection.y();
        Objects.requireNonNull(bitmap);
        return CardHighlight.score(i, i2, iX, iY, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
    }

    private void handleExpeditionDispatch(List<PetalMatcher.Token> list, Bitmap bitmap) {
        if (this.expeditionDispatchSession == null || activeGameBoundsStrict() == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Objects.requireNonNull(bitmap);
        ExpeditionScreenAnalyzer.Screen screenClassify = ExpeditionScreenAnalyzer.classify(list, width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        ExpeditionDispatchSession.Stage stage = this.expeditionDispatchSession.stage();
        this.expeditionDispatchSession.advanceForVerifiedScreen(screenClassify, jElapsedRealtime);
        if (stage == ExpeditionDispatchSession.Stage.SELECTION && screenClassify == ExpeditionScreenAnalyzer.Screen.UNKNOWN && ExpeditionScreenAnalyzer.findResultClose(bitmap) != null) {
            this.expeditionDispatchSession.advance(ExpeditionDispatchSession.Stage.SELECTION, ExpeditionDispatchSession.Stage.WAIT_RESULT, jElapsedRealtime);
        }
        ExpeditionDispatchSession.Stage stage2 = this.expeditionDispatchSession.stage();
        if (screenClassify == ExpeditionScreenAnalyzer.Screen.UNKNOWN) {
            this.dispatchUnknownFrames++;
        } else {
            this.dispatchUnknownFrames = 0;
        }
        if (this.dispatchUnknownFrames >= 8 && stage2 != ExpeditionDispatchSession.Stage.WAIT_RESULT) {
            stopWithError(getString(R.string.status_reward_stuck));
            return;
        }
        int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[stage2.ordinal()];
        if (i == 1) {
            handleDispatchList(list, bitmap, screenClassify, jElapsedRealtime);
            return;
        }
        if (i == 2) {
            handleDispatchDetail(list, bitmap, screenClassify, jElapsedRealtime);
            return;
        }
        if (i == 3) {
            handleDispatchSelection(list, bitmap, screenClassify, jElapsedRealtime);
        } else if (i == 4) {
            handleDispatchResult(list, bitmap, screenClassify, jElapsedRealtime);
        } else {
            if (i != 5) {
                return;
            }
            handleDispatchReturn(list, screenClassify, jElapsedRealtime);
        }
    }

    private void handleDispatchList(List<PetalMatcher.Token> list, Bitmap bitmap, ExpeditionScreenAnalyzer.Screen screen, long j) {
        if (screen != ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j));
            waitForDispatchFrame(getString(R.string.status_reward_wrong_page));
            return;
        }
        ExpeditionDispatchSession.BottomSettleDecision bottomSettleDecisionObserveListForBottom = this.expeditionDispatchSession.observeListForBottom(ExpeditionScreenAnalyzer.isExplorePanelExpanded(list, bitmap.getHeight()), j);
        if (bottomSettleDecisionObserveListForBottom == ExpeditionDispatchSession.BottomSettleDecision.SWIPE_UP) {
            revealDispatchExplorePanel(ExpeditionScreenAnalyzer.findExploreTabAnchor(list, bitmap.getWidth(), bitmap.getHeight()), bitmap);
            return;
        }
        if (bottomSettleDecisionObserveListForBottom == ExpeditionDispatchSession.BottomSettleDecision.FAILED) {
            stopWithError(getString(R.string.status_reward_bottom_failed));
            return;
        }
        ExpeditionTargetMode expeditionTargetMode = this.expeditionTargetMode;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Objects.requireNonNull(bitmap);
        ExpeditionScreenAnalyzer.Target targetFindTarget = ExpeditionScreenAnalyzer.findTarget(list, expeditionTargetMode, width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        if (targetFindTarget == null) {
            scanFocusedDispatchList(bitmap, ExpeditionScreenAnalyzer.isExploreListStart(list));
        } else {
            handleDispatchListTarget(targetFindTarget, bitmap.getWidth(), bitmap.getHeight(), j);
        }
    }

    private void scanFocusedDispatchList(Bitmap bitmap, boolean z) {
        Bitmap bitmapCreateBitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float f = height;
        int iRound = Math.round(0.18f * f);
        int iMax = Math.max(1, Math.round(f * 0.9f) - iRound);
        try {
            bitmapCreateBitmap = Bitmap.createBitmap(bitmap, 0, iRound, width, iMax);
            try {
                Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmapCreateBitmap, width * 2, iMax * 2, true);
                if (bitmapCreateScaledBitmap != bitmapCreateBitmap) {
                    bitmapCreateBitmap.recycle();
                }
                long j = this.runGeneration;
                this.busy = true;
                setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_reward_scanning), getString(R.string.overlay_reward_safety_items));
                this.scanner.scanChinese(bitmapCreateScaledBitmap, getMainExecutor(), new AnonymousClass3(j, width, height, iRound, bitmapCreateScaledBitmap, z));
            } catch (RuntimeException unused) {
                if (bitmapCreateBitmap != null && !bitmapCreateBitmap.isRecycled()) {
                    bitmapCreateBitmap.recycle();
                }
                handleDispatchListMiss(z, SystemClock.elapsedRealtime());
            }
        } catch (RuntimeException unused2) {
            bitmapCreateBitmap = null;
        }
    }

    /* JADX INFO: renamed from: com.pikminx.helper.PetalAccessibilityService$3, reason: invalid class name */
    class AnonymousClass3 implements OcrScanner.Callback {
        final /* synthetic */ int val$cropTop;
        final /* synthetic */ Bitmap val$enlarged;
        final /* synthetic */ long val$generation;
        final /* synthetic */ int val$height;
        final /* synthetic */ boolean val$listStartVisible;
        final /* synthetic */ int val$width;

        AnonymousClass3(long j, int i, int i2, int i3, Bitmap bitmap, boolean z) {
            this.val$generation = j;
            this.val$width = i;
            this.val$height = i2;
            this.val$cropTop = i3;
            this.val$enlarged = bitmap;
            this.val$listStartVisible = z;
        }

        @Override // com.pikminx.helper.OcrScanner.Callback
        public void onSuccess(List<PetalMatcher.Token> list) {
            try {
                if (PetalAccessibilityService.this.isActiveRun(this.val$generation) && PetalAccessibilityService.this.expeditionDispatchSession != null && PetalAccessibilityService.this.expeditionDispatchSession.stage() == ExpeditionDispatchSession.Stage.LIST_SEARCH) {
                    PetalAccessibilityService.this.busy = false;
                    long jElapsedRealtime = SystemClock.elapsedRealtime();
                    ExpeditionTargetMode expeditionTargetMode = PetalAccessibilityService.this.expeditionTargetMode;
                    int i = this.val$width;
                    int i2 = this.val$height;
                    int i3 = this.val$cropTop;
                    int width = this.val$enlarged.getWidth();
                    int height = this.val$enlarged.getHeight();
                    final Bitmap bitmap = this.val$enlarged;
                    Objects.requireNonNull(bitmap);
                    ExpeditionScreenAnalyzer.Target targetFindFocusedTarget = ExpeditionScreenAnalyzer.findFocusedTarget(list, expeditionTargetMode, i, i2, i3, 2, width, height, new IntBinaryOperator() { // from class: com.pikminx.helper.PetalAccessibilityService$3$$ExternalSyntheticLambda0
                        @Override // java.util.function.IntBinaryOperator
                        public final int applyAsInt(int i4, int i5) {
                            return bitmap.getPixel(i4, i5);
                        }
                    });
                    if (targetFindFocusedTarget == null) {
                        PetalAccessibilityService.this.handleDispatchListMiss(this.val$listStartVisible, jElapsedRealtime);
                    } else {
                        PetalAccessibilityService.this.handleDispatchListTarget(targetFindFocusedTarget, this.val$width, this.val$height, jElapsedRealtime);
                    }
                }
            } finally {
                this.val$enlarged.recycle();
            }
        }

        @Override // com.pikminx.helper.OcrScanner.Callback
        public void onFailure(Exception exc) {
            try {
                if (PetalAccessibilityService.this.isActiveRun(this.val$generation)) {
                    PetalAccessibilityService.this.busy = false;
                    PetalAccessibilityService.this.handleDispatchListMiss(this.val$listStartVisible, SystemClock.elapsedRealtime());
                }
            } finally {
                this.val$enlarged.recycle();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDispatchListMiss(boolean z, long j) {
        if (handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j)) || !this.running) {
            return;
        }
        ExpeditionDispatchSession.ListScanDecision listScanDecisionRecordListMiss = this.expeditionDispatchSession.recordListMiss(z, j);
        if (listScanDecisionRecordListMiss == ExpeditionDispatchSession.ListScanDecision.SCROLL) {
            scrollDispatchListTowardEarlierItems();
        } else if (listScanDecisionRecordListMiss == ExpeditionDispatchSession.ListScanDecision.AT_LIST_START) {
            stopWithError(getString(R.string.status_reward_target_missing));
        } else {
            waitForDispatchFrame(getString(R.string.status_reward_target_missing));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDispatchListTarget(ExpeditionScreenAnalyzer.Target target, int i, int i2, long j) {
        this.expeditionDispatchSession.recordListTargetFound();
        if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm(target.confirmationKey(), j))) {
            waitForDispatchFrame(getString(R.string.status_reward_confirming));
        } else {
            dispatchActionTap(screenPointFromBitmap(new ExpeditionScreenAnalyzer.Point(target.x(), target.y()), i, i2), getString(R.string.status_reward_opening_detail), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda30
                @Override // java.lang.Runnable
                public final void run() {
                    PetalAccessibilityService.lambda$handleDispatchListTarget$1();
                }
            });
        }
    }

    private void handleDispatchDetail(List<PetalMatcher.Token> list, Bitmap bitmap, ExpeditionScreenAnalyzer.Screen screen, long j) {
        if (screen == ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            waitForDispatchFrame(getString(R.string.status_reward_opening_detail));
            return;
        }
        ExpeditionScreenAnalyzer.Point pointFindTextAction = ExpeditionScreenAnalyzer.findTextAction(list, "前往探險", "前往探险", "前往探索", "前往探臉");
        if (pointFindTextAction == null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Objects.requireNonNull(bitmap);
            FlowerDetailActionDetector.Target targetFind = FlowerDetailActionDetector.find(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
            if (targetFind != null) {
                pointFindTextAction = new ExpeditionScreenAnalyzer.Point(targetFind.x(), targetFind.y());
            }
        }
        if (pointFindTextAction == null) {
            if (handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j))) {
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_go_explore));
        } else if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("DETAIL:" + (pointFindTextAction.x() / 24) + ":" + (pointFindTextAction.y() / 24), j))) {
            waitForDispatchFrame(getString(R.string.status_reward_go_explore));
        } else {
            dispatchActionTap(screenPointFromBitmap(pointFindTextAction, bitmap), getString(R.string.status_reward_go_explore), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda39
                @Override // java.lang.Runnable
                public final void run() {
                    PetalAccessibilityService.lambda$handleDispatchDetail$2();
                }
            });
        }
    }

    private void handleDispatchSelection(List<PetalMatcher.Token> list, Bitmap bitmap, ExpeditionScreenAnalyzer.Screen screen, long j) {
        if (screen != ExpeditionScreenAnalyzer.Screen.PIKMIN_SELECTION) {
            if (handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j))) {
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        if (!this.dispatchColorSelected) {
            handleDispatchPikminFilter(list, bitmap, j);
            return;
        }
        if (!this.dispatchPikminSelected) {
            if (this.dispatchSelectionMethod == DispatchSelectionMethod.AUTO) {
                ExpeditionScreenAnalyzer.Point pointFindTextAction = ExpeditionScreenAnalyzer.findTextAction(list, "自動", "自动");
                if (pointFindTextAction == null) {
                    waitForDispatchFrame(getString(R.string.status_reward_selection_missing));
                    return;
                } else if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("AUTO:" + (pointFindTextAction.x() / 24) + ":" + (pointFindTextAction.y() / 24), j))) {
                    waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                    return;
                } else {
                    dispatchActionTap(screenPointFromBitmap(pointFindTextAction, bitmap), getString(R.string.status_reward_selecting_pikmin), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda54
                        @Override // java.lang.Runnable
                        public final void run() {
                            this.f$0.lambda$handleDispatchSelection$3();
                        }
                    });
                    return;
                }
            }
            selectDispatchPikminFromGrid(list, bitmap, j);
            return;
        }
        ExpeditionScreenAnalyzer.Point pointFindTextAction2 = ExpeditionScreenAnalyzer.findTextAction(list, "GO");
        if (pointFindTextAction2 == null || (this.dispatchSelectionMethod.requiresFullSelection() && !ExpeditionScreenAnalyzer.hasFullSelection(list))) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
        } else if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("GO:" + (pointFindTextAction2.x() / 24) + ":" + (pointFindTextAction2.y() / 24), j))) {
            waitForDispatchFrame(getString(R.string.status_reward_tapping_go));
        } else {
            dispatchActionTap(screenPointFromBitmap(pointFindTextAction2, bitmap), getString(R.string.status_reward_tapping_go), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda55
                @Override // java.lang.Runnable
                public final void run() {
                    PetalAccessibilityService.lambda$handleDispatchSelection$4();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleDispatchSelection$3() {
        this.dispatchPikminSelected = true;
    }

    private void handleDispatchResult(List<PetalMatcher.Token> list, Bitmap bitmap, ExpeditionScreenAnalyzer.Screen screen, long j) {
        ExpeditionScreenAnalyzer.Point pointFindResultClose = ExpeditionScreenAnalyzer.findResultClose(bitmap);
        if (pointFindResultClose == null) {
            if (handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j))) {
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_waiting_result));
        } else if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("CLOSE:" + (pointFindResultClose.x() / 24) + ":" + (pointFindResultClose.y() / 24), j))) {
            waitForDispatchFrame(getString(R.string.status_reward_waiting_result));
        } else {
            dispatchActionTap(screenPointFromBitmap(pointFindResultClose, bitmap), getString(R.string.status_reward_closing_result), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda43
                @Override // java.lang.Runnable
                public final void run() {
                    PetalAccessibilityService.lambda$handleDispatchResult$5();
                }
            });
        }
    }

    private void handleDispatchReturn(List<PetalMatcher.Token> list, ExpeditionScreenAnalyzer.Screen screen, long j) {
        if (screen != ExpeditionScreenAnalyzer.Screen.EXPLORE_LIST) {
            if (handleDispatchConfirmation(this.expeditionDispatchSession.confirm("", j))) {
                return;
            }
            waitForDispatchFrame(getString(R.string.status_reward_returning));
            return;
        }
        if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("RETURN:EXPLORE_LIST", j))) {
            waitForDispatchFrame(getString(R.string.status_reward_returning));
            return;
        }
        if (!this.expeditionDispatchSession.recordReturnedToList(j)) {
            stopWithError(getString(R.string.status_reward_stuck));
            return;
        }
        if (this.settings.recordConfirmedExpeditionDispatch() < 0) {
            stopWithError(getString(R.string.status_reward_progress_save_failed));
            return;
        }
        int iCompletedCount = this.expeditionDispatchSession.completedCount();
        int iTargetCount = this.expeditionDispatchSession.targetCount();
        if (this.expeditionDispatchSession.complete()) {
            finishWithSuccess(getString(R.string.status_reward_complete, new Object[]{Integer.valueOf(iCompletedCount)}));
            return;
        }
        this.dispatchColorSelected = this.dispatchPikminType == DispatchPikminType.MIXED;
        this.dispatchPikminSelected = false;
        this.dispatchSearchOpened = false;
        this.dispatchSearchInputAttempts = 0;
        this.dispatchKeyboardCloseAttempts = 0;
        this.dispatchKeyboardAbsentFrames = 0;
        this.dispatchPikminTapIndex = 0;
        waitForDispatchFrame(getString(R.string.status_reward_progress, new Object[]{Integer.valueOf(iCompletedCount), Integer.valueOf(iTargetCount)}));
    }

    private boolean handleDispatchConfirmation(ExpeditionDispatchSession.Confirmation confirmation) {
        if (confirmation != ExpeditionDispatchSession.Confirmation.STAGE_TIMEOUT) {
            return confirmation == ExpeditionDispatchSession.Confirmation.READY;
        }
        stopWithError(getString(R.string.status_reward_stuck));
        return false;
    }

    private void waitForDispatchFrame(String str) {
        if (this.running && this.automationMode == AutomationMode.DISPATCH) {
            setStatus(str);
            setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.RECOGNIZING, str, getString(R.string.overlay_reward_safety_items));
            schedule(DISPATCH_SCAN_DELAY_MILLIS);
        }
    }

    private void dispatchActionTap(ExpeditionScreenAnalyzer.Point point, String str, Runnable runnable) {
        dispatchActionTap(point, str, DISPATCH_AFTER_TAP_DELAY_MILLIS, runnable);
    }

    private void dispatchActionTap(ExpeditionScreenAnalyzer.Point point, String str, final long j, final Runnable runnable) {
        setStatus(str);
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.SEARCHING, str, getString(R.string.status_reward_progress, new Object[]{Integer.valueOf(this.expeditionDispatchSession.completedCount()), Integer.valueOf(this.expeditionDispatchSession.targetCount())}));
        this.busy = true;
        dispatchTap(point.x(), point.y(), GAME_ACTION_TAP_DURATION_MILLIS, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda47
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$dispatchActionTap$6(runnable, j);
            }
        }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda48
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$dispatchActionTap$7();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$dispatchActionTap$6(Runnable runnable, long j) {
        ExpeditionDispatchSession expeditionDispatchSession = this.expeditionDispatchSession;
        if (expeditionDispatchSession != null) {
            expeditionDispatchSession.recordProgress(SystemClock.elapsedRealtime());
        }
        runnable.run();
        this.busy = false;
        schedule(j);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$dispatchActionTap$7() {
        this.busy = false;
        stopWithError(getString(R.string.status_reward_gesture_failed));
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(ExpeditionScreenAnalyzer.Point point, Bitmap bitmap) {
        return screenPointFromBitmap(point, bitmap.getWidth(), bitmap.getHeight());
    }

    private ExpeditionScreenAnalyzer.Point screenPointFromBitmap(ExpeditionScreenAnalyzer.Point point, int i, int i2) {
        Rect rectActiveGameBoundsStrict = activeGameBoundsStrict();
        if (rectActiveGameBoundsStrict == null) {
            return point;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            return new ExpeditionScreenAnalyzer.Point(rectActiveGameBoundsStrict.left + Math.round((point.x() * rectActiveGameBoundsStrict.width()) / i), rectActiveGameBoundsStrict.top + Math.round((point.y() * rectActiveGameBoundsStrict.height()) / i2));
        }
        return new ExpeditionScreenAnalyzer.Point(Math.round((point.x() * getResources().getDisplayMetrics().widthPixels) / i), Math.round((point.y() * getResources().getDisplayMetrics().heightPixels) / i2));
    }

    private void handleDispatchPikminFilter(List<PetalMatcher.Token> list, Bitmap bitmap, long j) {
        String strLabel = this.dispatchPikminType.label();
        if (!this.dispatchSearchOpened) {
            ExpeditionScreenAnalyzer.Point pointFindPikminSearchButton = ExpeditionScreenAnalyzer.findPikminSearchButton(list, bitmap.getWidth(), bitmap.getHeight());
            if (pointFindPikminSearchButton == null) {
                waitForDispatchFrame(getString(R.string.status_reward_search_missing));
                return;
            } else if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("PIKMIN_SEARCH:" + (pointFindPikminSearchButton.x() / 24) + ":" + (pointFindPikminSearchButton.y() / 24), j))) {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            } else {
                dispatchActionTap(screenPointFromBitmap(pointFindPikminSearchButton, bitmap), getString(R.string.status_reward_selecting_pikmin), new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda52
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$handleDispatchPikminFilter$8();
                    }
                });
                return;
            }
        }
        if (!gameEditableTextMatches(strLabel)) {
            this.dispatchSearchInputAttempts++;
            boolean gameEditableText = setGameEditableText(strLabel);
            int i = this.dispatchSearchInputAttempts;
            if (i >= 3 && !gameEditableText) {
                stopWithError(getString(R.string.status_reward_search_input_failed));
                return;
            } else if (i > 3) {
                stopWithError(getString(R.string.status_reward_search_input_failed));
                return;
            } else {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
        }
        this.dispatchSearchInputAttempts = 0;
        if (isInputMethodWindowVisible()) {
            this.dispatchKeyboardAbsentFrames = 0;
            int i2 = this.dispatchKeyboardCloseAttempts;
            if (i2 >= 3) {
                stopWithError(getString(R.string.status_reward_keyboard_failed));
                return;
            }
            this.dispatchKeyboardCloseAttempts = i2 + 1;
            if (!performGlobalAction(1) && this.dispatchKeyboardCloseAttempts >= 3) {
                stopWithError(getString(R.string.status_reward_keyboard_failed));
                return;
            } else {
                waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
                return;
            }
        }
        int i3 = this.dispatchKeyboardAbsentFrames + 1;
        this.dispatchKeyboardAbsentFrames = i3;
        if (i3 < 2) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        this.dispatchColorSelected = true;
        this.expeditionDispatchSession.recordProgress(j);
        this.dispatchKeyboardCloseAttempts = 0;
        this.dispatchKeyboardAbsentFrames = 0;
        this.dispatchPikminTapIndex = 0;
        waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleDispatchPikminFilter$8() {
        this.dispatchSearchOpened = true;
    }

    private void selectDispatchPikminFromGrid(List<PetalMatcher.Token> list, Bitmap bitmap, long j) {
        if (ExpeditionScreenAnalyzer.hasFullSelection(list)) {
            this.dispatchPikminSelected = true;
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
            return;
        }
        List<PostcardMatcher.Target> listFindPikminSelectionSlots = PostcardMatcher.findPikminSelectionSlots(bitmap.getWidth(), bitmap.getHeight());
        if (this.dispatchPikminTapIndex >= listFindPikminSelectionSlots.size()) {
            waitForDispatchFrame(getString(R.string.status_reward_selection_missing));
            return;
        }
        PostcardMatcher.Target target = listFindPikminSelectionSlots.get(this.dispatchPikminTapIndex);
        if (!handleDispatchConfirmation(this.expeditionDispatchSession.confirm("PIKMIN:" + this.dispatchPikminTapIndex + ":" + (target.x() / 24) + ":" + (target.y() / 24), j, 1))) {
            waitForDispatchFrame(getString(R.string.status_reward_selecting_pikmin));
        } else {
            dispatchActionTap(screenPointFromBitmap(new ExpeditionScreenAnalyzer.Point(target.x(), target.y()), bitmap), getString(R.string.status_reward_selecting_pikmin), DISPATCH_PIKMIN_TAP_DELAY_MILLIS, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda49
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$selectDispatchPikminFromGrid$9();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$selectDispatchPikminFromGrid$9() {
        this.dispatchPikminTapIndex++;
    }

    private void revealDispatchExplorePanel(ExpeditionScreenAnalyzer.Point point, Bitmap bitmap) {
        Rect rectActiveGameBoundsStrict = activeGameBoundsStrict();
        if (rectActiveGameBoundsStrict == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        if (point == null) {
            point = new ExpeditionScreenAnalyzer.Point(Math.round(bitmap.getWidth() * 0.65f), Math.round(bitmap.getHeight() * 0.45f));
        }
        ExpeditionScreenAnalyzer.Point pointScreenPointFromBitmap = screenPointFromBitmap(point, bitmap);
        Path path = new Path();
        float fMax = Math.max(rectActiveGameBoundsStrict.top + (rectActiveGameBoundsStrict.height() * 0.08f), pointScreenPointFromBitmap.y() - (rectActiveGameBoundsStrict.height() * 0.2f));
        path.moveTo(pointScreenPointFromBitmap.x(), pointScreenPointFromBitmap.y());
        path.lineTo(pointScreenPointFromBitmap.x(), fMax);
        this.busy = true;
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.SEARCHING, getString(R.string.status_reward_settling_bottom), getString(R.string.status_reward_scanning));
        dispatchPath(path, 500L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$revealDispatchExplorePanel$10();
            }
        }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$revealDispatchExplorePanel$11();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$revealDispatchExplorePanel$10() {
        this.busy = false;
        schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$revealDispatchExplorePanel$11() {
        this.busy = false;
        stopWithError(getString(R.string.status_reward_gesture_failed));
    }

    private void scrollDispatchListTowardEarlierItems() {
        Rect rectActiveGameBoundsStrict = activeGameBoundsStrict();
        if (rectActiveGameBoundsStrict == null) {
            stopWithError(getString(R.string.status_reward_left_game));
            return;
        }
        Path path = new Path();
        float fWidth = rectActiveGameBoundsStrict.left + (rectActiveGameBoundsStrict.width() * 0.5f);
        path.moveTo(fWidth, rectActiveGameBoundsStrict.top + (rectActiveGameBoundsStrict.height() * 0.6f));
        path.lineTo(fWidth, rectActiveGameBoundsStrict.top + (rectActiveGameBoundsStrict.height() * 0.78f));
        this.busy = true;
        setRunStatus(AutomationMode.DISPATCH, OverlayRunStatus.Kind.SEARCHING, getString(R.string.status_reward_scrolling), getString(R.string.status_reward_scanning));
        dispatchPath(path, 720L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda32
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$scrollDispatchListTowardEarlierItems$12();
            }
        }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda33
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$scrollDispatchListTowardEarlierItems$13();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scrollDispatchListTowardEarlierItems$12() {
        this.busy = false;
        schedule(DISPATCH_AFTER_SCROLL_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scrollDispatchListTowardEarlierItems$13() {
        this.busy = false;
        stopWithError(getString(R.string.status_reward_gesture_failed));
    }

    private boolean isPlantingSearchStep() {
        return this.automationStep == AutomationStep.REVEALING_SEARCH_PANEL || this.automationStep == AutomationStep.OPENING_SEARCH || this.automationStep == AutomationStep.ENTERING_SEARCH || this.automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD || this.automationStep == AutomationStep.SELECTING_SEARCH_RESULT;
    }

    private void beginPlantingFlowerSearch(String str, int i, boolean z) {
        String strSearchQuery = PetalCatalog.searchQuery(str);
        if (MainActivity$$ExternalSyntheticBackport0.m(strSearchQuery)) {
            stopWithError(getString(R.string.status_flower_search_invalid_name));
            return;
        }
        this.targetFlower = PetalCatalog.canonicalName(str);
        resetPlantingSearch();
        this.plantingSearchMinimumCount = Math.max(0, i);
        this.startAfterSelection = z;
        this.selectionFromSearch = false;
        this.actionAttempts = 0;
        this.automationStep = AutomationStep.REVEALING_SEARCH_PANEL;
        setStatus(getString(R.string.status_searching_flower, new Object[]{this.targetFlower}));
        setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.SEARCHING, getString(R.string.overlay_planting_searching, new Object[]{this.targetFlower}), strSearchQuery);
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    private void handlePlantingFlowerSearch(List<PetalMatcher.Token> list, Bitmap bitmap) {
        if (this.automationStep == AutomationStep.REVEALING_SEARCH_PANEL) {
            revealPlantingSearchPanel();
            return;
        }
        if (this.automationStep == AutomationStep.OPENING_SEARCH) {
            openPlantingFlowerSearch(bitmap);
            return;
        }
        if (this.automationStep == AutomationStep.ENTERING_SEARCH) {
            enterPlantingFlowerSearch();
            return;
        }
        if (this.automationStep == AutomationStep.CLOSING_SEARCH_KEYBOARD) {
            closePlantingSearchKeyboard();
            return;
        }
        if (!gameEditableTextMatches(PetalCatalog.searchQuery(this.targetFlower))) {
            this.automationStep = AutomationStep.ENTERING_SEARCH;
            setStatus(getString(R.string.status_flower_search_confirming_text));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
        } else {
            PetalMatcher.Selection selectionFindSingleVisibleSearchedFlower = PetalMatcher.findSingleVisibleSearchedFlower(list, this.targetFlower, this.plantingSearchMinimumCount, bitmap.getWidth(), bitmap.getHeight());
            if (selectionFindSingleVisibleSearchedFlower == null) {
                scanFocusedPlantingPetalRegion(bitmap);
            } else {
                confirmPlantingSearchResult(selectionFindSingleVisibleSearchedFlower, bitmap.getWidth(), bitmap.getHeight(), bitmap);
            }
        }
    }

    private void revealPlantingSearchPanel() {
        Rect rectActiveGameBoundsStrict = activeGameBoundsStrict();
        if (rectActiveGameBoundsStrict == null) {
            stopWithError(getString(R.string.status_flower_panel_reveal_failed));
            return;
        }
        PetalMatcher.PanelPull panelPullPlantingPanelPull = PetalMatcher.plantingPanelPull(rectActiveGameBoundsStrict.width(), rectActiveGameBoundsStrict.height());
        Path path = new Path();
        path.moveTo(rectActiveGameBoundsStrict.left + panelPullPlantingPanelPull.x(), rectActiveGameBoundsStrict.top + panelPullPlantingPanelPull.startY());
        path.lineTo(rectActiveGameBoundsStrict.left + panelPullPlantingPanelPull.x(), rectActiveGameBoundsStrict.top + panelPullPlantingPanelPull.endY());
        this.busy = true;
        setStatus(getString(R.string.status_flower_panel_revealing));
        dispatchPath(path, 500L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$revealPlantingSearchPanel$14();
            }
        }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$revealPlantingSearchPanel$15();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$revealPlantingSearchPanel$14() {
        this.busy = false;
        this.automationStep = AutomationStep.OPENING_SEARCH;
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$revealPlantingSearchPanel$15() {
        this.busy = false;
        stopWithError(getString(R.string.status_flower_panel_reveal_failed));
    }

    private void openPlantingFlowerSearch(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Objects.requireNonNull(bitmap);
        if (CardHighlight.isPetalSearchOpen(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap))) {
            this.actionAttempts = 0;
            this.automationStep = AutomationStep.ENTERING_SEARCH;
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        Objects.requireNonNull(bitmap);
        CardHighlight.Point pointFindPetalSearchButton = CardHighlight.findPetalSearchButton(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        if (pointFindPetalSearchButton != null) {
            int i = this.actionAttempts + 1;
            this.actionAttempts = i;
            if (i <= 3) {
                setStatus(getString(R.string.status_flower_search_opening));
                dispatchTap(pointFindPetalSearchButton.x(), pointFindPetalSearchButton.y(), GAME_ACTION_TAP_DURATION_MILLIS, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda44
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$openPlantingFlowerSearch$16();
                    }
                }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda45
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$openPlantingFlowerSearch$17();
                    }
                });
                return;
            }
        }
        stopWithError(getString(R.string.status_flower_search_open_failed));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openPlantingFlowerSearch$16() {
        schedule(700L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openPlantingFlowerSearch$17() {
        this.automationStep = AutomationStep.OPENING_SEARCH;
        scheduleNext();
    }

    private void enterPlantingFlowerSearch() {
        String strSearchQuery = PetalCatalog.searchQuery(this.targetFlower);
        if (MainActivity$$ExternalSyntheticBackport0.m(strSearchQuery)) {
            stopWithError(getString(R.string.status_flower_search_invalid_name));
            return;
        }
        if (!setGameEditableText(strSearchQuery)) {
            int i = this.plantingSearchInputAttempts + 1;
            this.plantingSearchInputAttempts = i;
            if (i >= 3) {
                stopWithError(getString(R.string.status_flower_search_input_failed));
                return;
            }
            this.automationStep = AutomationStep.ENTERING_SEARCH;
            setStatus(getString(R.string.status_flower_search_retrying_input));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        this.plantingSearchInputAttempts = 0;
        this.plantingKeyboardCloseAttempts = 0;
        this.plantingKeyboardAbsentFrames = 0;
        this.automationStep = AutomationStep.CLOSING_SEARCH_KEYBOARD;
        setStatus(getString(R.string.status_flower_search_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closePlantingSearchKeyboard() {
        if (!isInputMethodWindowVisible()) {
            int i = this.plantingKeyboardAbsentFrames + 1;
            this.plantingKeyboardAbsentFrames = i;
            if (i < 2) {
                setStatus(getString(R.string.status_flower_search_waiting_keyboard));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                return;
            }
            this.plantingKeyboardCloseAttempts = 0;
            this.plantingKeyboardAbsentFrames = 0;
            this.automationStep = AutomationStep.SELECTING_SEARCH_RESULT;
            setStatus(getString(R.string.status_flower_search_keyboard_closed));
            schedule(700L);
            return;
        }
        this.plantingKeyboardAbsentFrames = 0;
        int i2 = this.plantingKeyboardCloseAttempts;
        if (i2 >= 3) {
            stopWithError(getString(R.string.status_flower_search_keyboard_failed));
            return;
        }
        this.plantingKeyboardCloseAttempts = i2 + 1;
        if (!performGlobalAction(1) && this.plantingKeyboardCloseAttempts >= 3) {
            stopWithError(getString(R.string.status_flower_search_keyboard_failed));
        } else {
            setStatus(getString(R.string.status_flower_search_closing_keyboard));
            schedule(600L);
        }
    }

    private void scanFocusedPlantingPetalRegion(Bitmap bitmap) {
        Bitmap bitmapCreateBitmap;
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        float f = height;
        final int iRound = Math.round(0.44f * f);
        int iMax = Math.max(1, Math.round(f * 0.96f) - iRound);
        try {
            bitmapCreateBitmap = Bitmap.createBitmap(bitmap, 0, iRound, width, iMax);
            try {
                final Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmapCreateBitmap, width * 2, iMax * 2, true);
                if (bitmapCreateScaledBitmap != bitmapCreateBitmap) {
                    bitmapCreateBitmap.recycle();
                }
                final long j = this.runGeneration;
                this.busy = true;
                setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_flower_search_focused_ocr), this.targetFlower);
                this.scanner.scanChinese(bitmapCreateScaledBitmap, getMainExecutor(), new OcrScanner.Callback() { // from class: com.pikminx.helper.PetalAccessibilityService.4
                    @Override // com.pikminx.helper.OcrScanner.Callback
                    public void onSuccess(List<PetalMatcher.Token> list) {
                        try {
                            if (PetalAccessibilityService.this.isActiveRun(j)) {
                                PetalAccessibilityService.this.busy = false;
                                if (PetalAccessibilityService.this.automationStep != AutomationStep.SELECTING_SEARCH_RESULT) {
                                    PetalAccessibilityService.this.schedule(PetalAccessibilityService.POSTCARD_VERIFY_DELAY_MILLIS);
                                } else {
                                    ArrayList arrayList = new ArrayList(list.size());
                                    for (PetalMatcher.Token token : list) {
                                        arrayList.add(new PetalMatcher.Token(token.text(), token.left() / 2, iRound + (token.top() / 2), token.right() / 2, iRound + (token.bottom() / 2)));
                                    }
                                    PetalMatcher.Selection selectionFindSingleVisibleSearchedFlower = PetalMatcher.findSingleVisibleSearchedFlower(arrayList, PetalAccessibilityService.this.targetFlower, PetalAccessibilityService.this.plantingSearchMinimumCount, width, height);
                                    if (selectionFindSingleVisibleSearchedFlower == null) {
                                        PetalAccessibilityService.this.handlePlantingSearchMiss();
                                    } else {
                                        PetalAccessibilityService.this.confirmPlantingSearchResult(selectionFindSingleVisibleSearchedFlower, width, height, null);
                                    }
                                }
                            }
                        } finally {
                            bitmapCreateScaledBitmap.recycle();
                        }
                    }

                    @Override // com.pikminx.helper.OcrScanner.Callback
                    public void onFailure(Exception exc) {
                        try {
                            if (PetalAccessibilityService.this.isActiveRun(j)) {
                                PetalAccessibilityService.this.busy = false;
                                PetalAccessibilityService.this.handlePlantingSearchMiss();
                            }
                        } finally {
                            bitmapCreateScaledBitmap.recycle();
                        }
                    }
                });
            } catch (RuntimeException unused) {
                if (bitmapCreateBitmap != null && !bitmapCreateBitmap.isRecycled()) {
                    bitmapCreateBitmap.recycle();
                }
                handlePlantingSearchMiss();
            }
        } catch (RuntimeException unused2) {
            bitmapCreateBitmap = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handlePlantingSearchMiss() {
        int i;
        int i2 = this.plantingSearchMissingFrames + 1;
        this.plantingSearchMissingFrames = i2;
        if (this.plantingPendingPot != null && (i = this.plantingPotMissingFrames) < 2) {
            this.plantingPotMissingFrames = i + 1;
        } else {
            this.plantingPendingPot = null;
            this.plantingPotConfirmations = 0;
            this.plantingPotMissingFrames = 0;
        }
        if (i2 >= 6) {
            stopWithError(getString(R.string.status_flower_search_result_missing, new Object[]{PetalCatalog.searchQuery(this.targetFlower)}));
        } else {
            setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_flower_search_waiting_result), PetalCatalog.searchQuery(this.targetFlower));
            schedule(700L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void confirmPlantingSearchResult(PetalMatcher.Selection selection, int i, int i2, Bitmap bitmap) {
        if (isSamePlantingSearchCandidate(this.plantingPendingPot, selection, i, i2)) {
            this.plantingPotConfirmations++;
            this.plantingPendingPot = selection;
        } else {
            this.plantingPendingPot = selection;
            this.plantingPotConfirmations = 1;
        }
        this.plantingPotMissingFrames = 0;
        this.plantingSearchMissingFrames = 0;
        if (this.plantingPotConfirmations < 2 || bitmap == null) {
            setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_flower_search_confirming_result, new Object[]{selection.name(), Integer.valueOf(selection.count()), Integer.valueOf(this.plantingPotConfirmations), 2}), getString(R.string.overlay_ocr_detail));
            schedule(700L);
        } else {
            boolean z = this.startAfterSelection;
            resetPlantingSearch();
            tapFlower(selection, z, true);
        }
    }

    private boolean isSamePlantingSearchCandidate(PetalMatcher.Selection selection, PetalMatcher.Selection selection2, int i, int i2) {
        return selection != null && selection2 != null && selection.name().equals(selection2.name()) && ((float) Math.abs(selection.x() - selection2.x())) <= ((float) i) * 0.08f && ((float) Math.abs(selection.y() - selection2.y())) <= ((float) i2) * 0.07f;
    }

    private void resetPlantingSearch() {
        this.plantingPendingPot = null;
        this.plantingPotConfirmations = 0;
        this.plantingPotMissingFrames = 0;
        this.plantingSearchMissingFrames = 0;
        this.plantingSearchInputAttempts = 0;
        this.plantingKeyboardCloseAttempts = 0;
        this.plantingKeyboardAbsentFrames = 0;
        this.plantingSearchMinimumCount = 0;
    }

    private void tapFlower(PetalMatcher.Selection selection, boolean z, boolean z2) {
        this.targetFlower = selection.name();
        this.targetCount = selection.count();
        this.startAfterSelection = z;
        this.selectionFromSearch = z2;
        this.targetSelectionX = selection.x();
        this.targetSelectionY = selection.y();
        this.actionAttempts = 0;
        this.automationStep = AutomationStep.VERIFYING_SELECTION;
        this.switchGuard.requestSwitch(selection.name());
        setStatus(getString(R.string.status_confirming_selection, new Object[]{selection.name()}));
        setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.overlay_planting_switching, new Object[]{selection.name()}), getString(R.string.overlay_ocr_detail));
        dispatchTap(selection.x(), selection.tapY(), 80L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$tapFlower$18();
            }
        }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$tapFlower$19();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$tapFlower$18() {
        schedule(500L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$tapFlower$19() {
        this.switchGuard.cancelSwitch();
        this.automationStep = AutomationStep.MONITORING;
        scheduleNext();
    }

    /* JADX WARN: Code duplicated, block: B:17:0x003d  */
    private void verifyFlowerSelection(PetalMatcher.Selection selection, Bitmap bitmap) {
        boolean z;
        boolean z2 = selection != null && this.targetFlower.equals(selection.name());
        if (!this.selectionFromSearch || this.targetSelectionX <= 0 || this.targetSelectionY <= 0) {
            z = false;
        } else {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int i = this.targetSelectionX;
            int i2 = this.targetSelectionY;
            Objects.requireNonNull(bitmap);
            if (CardHighlight.score(width, height, i, i2, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap)) >= 245) {
                z = true;
            } else {
                z = false;
            }
        }
        String strName = z2 ? selection.name() : this.targetFlower;
        if ((z2 || z) && this.switchGuard.confirmSwitch(strName, SystemClock.elapsedRealtime())) {
            int iCount = z2 ? selection.count() : this.targetCount;
            this.currentFlower = strName;
            showPlantingStatus(strName, iCount);
            this.actionAttempts = 0;
            this.selectionFromSearch = false;
            this.targetSelectionX = 0;
            this.targetSelectionY = 0;
            if (this.startAfterSelection) {
                this.automationStep = AutomationStep.WAITING_START;
                setStatus(getString(R.string.status_starting_planting, new Object[]{this.currentFlower}));
                schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                return;
            } else {
                this.automationStep = AutomationStep.MONITORING;
                this.targetFlower = "";
                setStatus(getString(R.string.status_switched, new Object[]{this.currentFlower, Integer.valueOf(this.targetCount)}));
                scheduleNext();
                return;
            }
        }
        int i3 = this.actionAttempts + 1;
        this.actionAttempts = i3;
        if (i3 >= 3) {
            this.switchGuard.cancelSwitch();
            stopWithError(getString(R.string.status_selection_unconfirmed, new Object[]{this.targetFlower}));
        } else {
            schedule(500L);
        }
    }

    private void startPlanting(List<PetalMatcher.Token> list, int i, int i2, CardHighlight.Point point) {
        PetalMatcher.Token tokenFindStartPlantingControl = PetalMatcher.findStartPlantingControl(list, i, i2);
        if (clickGameNode(new Predicate() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda56
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return this.f$0.lambda$startPlanting$20((AccessibilityNodeInfo) obj);
            }
        })) {
            this.automationStep = AutomationStep.VERIFYING_START;
            this.actionAttempts = 0;
            this.startMissingConfirmations = 0;
            schedule(700L);
            return;
        }
        if (tokenFindStartPlantingControl != null) {
            this.automationStep = AutomationStep.VERIFYING_START;
            this.actionAttempts = 0;
            this.startMissingConfirmations = 0;
            dispatchTap(tokenFindStartPlantingControl.centerX(), tokenFindStartPlantingControl.centerY(), 80L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda57
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$startPlanting$21();
                }
            }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda58
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$startPlanting$22();
                }
            });
            return;
        }
        if (point != null) {
            this.automationStep = AutomationStep.VERIFYING_START;
            this.actionAttempts = 0;
            this.startMissingConfirmations = 0;
            dispatchTap(point.x(), point.y(), 80L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda59
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$startPlanting$23();
                }
            }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$startPlanting$24();
                }
            });
            return;
        }
        int i3 = this.actionAttempts + 1;
        this.actionAttempts = i3;
        if (i3 >= 3) {
            stopWithError(getString(R.string.status_start_control_unavailable));
        } else {
            schedule(500L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$startPlanting$20(AccessibilityNodeInfo accessibilityNodeInfo) {
        return nodeLabelEquals(accessibilityNodeInfo, "開始種花", "start planting");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startPlanting$21() {
        schedule(700L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startPlanting$22() {
        stopWithError(getString(R.string.status_start_tap_failed));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startPlanting$23() {
        schedule(700L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startPlanting$24() {
        stopWithError(getString(R.string.status_start_tap_failed));
    }

    private void verifyPlantingStarted(List<PetalMatcher.Token> list, int i, int i2, boolean z) {
        if (z || hasStartPlantingControl(list, i, i2)) {
            this.startMissingConfirmations = 0;
            int i3 = this.actionAttempts + 1;
            this.actionAttempts = i3;
            if (i3 >= 3) {
                stopWithError(getString(R.string.status_start_unconfirmed));
                return;
            } else {
                schedule(700L);
                return;
            }
        }
        int i4 = this.startMissingConfirmations + 1;
        this.startMissingConfirmations = i4;
        if (i4 < 2) {
            schedule(500L);
            return;
        }
        this.automationStep = AutomationStep.MONITORING;
        this.targetFlower = "";
        this.startAfterSelection = false;
        setStatus(getString(R.string.status_planting_started, new Object[]{this.currentFlower}));
        scheduleNext();
    }

    private boolean hasStartPlantingControl(List<PetalMatcher.Token> list, int i, int i2) {
        return (PetalMatcher.findStartPlantingControl(list, i, i2) == null && findGameNode(new Predicate() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda35
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return this.f$0.lambda$hasStartPlantingControl$25((AccessibilityNodeInfo) obj);
            }
        }) == null) ? false : true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$hasStartPlantingControl$25(AccessibilityNodeInfo accessibilityNodeInfo) {
        return nodeLabelEquals(accessibilityNodeInfo, "開始種花", "start planting");
    }

    private boolean clickGameNode(Predicate<AccessibilityNodeInfo> predicate) {
        for (AccessibilityNodeInfo accessibilityNodeInfoFindGameNode = findGameNode(predicate); accessibilityNodeInfoFindGameNode != null; accessibilityNodeInfoFindGameNode = accessibilityNodeInfoFindGameNode.getParent()) {
            if (accessibilityNodeInfoFindGameNode.isClickable()) {
                return accessibilityNodeInfoFindGameNode.performAction(16);
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findGameNode(Predicate<AccessibilityNodeInfo> predicate) {
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (rootInActiveWindow == null || !GAME_PACKAGE.contentEquals(rootInActiveWindow.getPackageName())) {
            return null;
        }
        return findNode(rootInActiveWindow, predicate);
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo accessibilityNodeInfo, Predicate<AccessibilityNodeInfo> predicate) {
        AccessibilityNodeInfo accessibilityNodeInfoFindNode;
        if (predicate.test(accessibilityNodeInfo)) {
            return accessibilityNodeInfo;
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
            if (child != null && (accessibilityNodeInfoFindNode = findNode(child, predicate)) != null) {
                return accessibilityNodeInfoFindNode;
            }
        }
        return null;
    }

    private boolean nodeLabelEquals(AccessibilityNodeInfo accessibilityNodeInfo, String... strArr) {
        String strNormalize = PetalMatcher.normalize(accessibilityNodeInfo.getText() == null ? "" : accessibilityNodeInfo.getText().toString());
        String strNormalize2 = PetalMatcher.normalize(accessibilityNodeInfo.getContentDescription() != null ? accessibilityNodeInfo.getContentDescription().toString() : "");
        for (String str : strArr) {
            String strNormalize3 = PetalMatcher.normalize(str);
            if (strNormalize.equals(strNormalize3) || strNormalize2.equals(strNormalize3)) {
                return true;
            }
        }
        return false;
    }

    private String nodeLabel(AccessibilityNodeInfo accessibilityNodeInfo) {
        String string;
        CharSequence text = accessibilityNodeInfo.getText();
        CharSequence contentDescription = accessibilityNodeInfo.getContentDescription();
        CharSequence hintText = accessibilityNodeInfo.getHintText();
        String string2 = "";
        String string3 = text == null ? "" : text.toString();
        if (contentDescription == null) {
            string = "";
        } else {
            string = contentDescription.toString();
        }
        if (hintText != null) {
            string2 = hintText.toString();
        }
        return PetalMatcher.normalize(string3 + " " + string + " " + string2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleReturnRewardTarget(ReturnRewardDetector.Target target, int i, int i2) {
        if (returnRewardTimedOut()) {
            stopWithError(getString(R.string.status_return_reward_timeout));
            return;
        }
        if (activeGameBoundsStrict() == null) {
            stopWithError(getString(R.string.status_return_reward_left_game));
            return;
        }
        if (this.returnRewardWaitingPostcardExit) {
            resetReturnRewardPostcard();
        }
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long j = this.returnRewardLastTapAt;
        long j2 = jElapsedRealtime - j;
        if (j > 0 && j2 < RETURN_REWARD_SETTLE_MILLIS) {
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SETTLE_MILLIS - j2);
        } else if (this.returnRewardScanGuard.observe(target, i, i2) != ReturnRewardScanGuard.Decision.TARGET_CONFIRMED) {
            setReturnRewardStatus(getString(R.string.status_return_reward_confirming));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
        } else {
            this.returnRewardLastTapAt = jElapsedRealtime;
            setReturnRewardStatus(getString(R.string.status_return_reward_tapping));
            dispatchTap(target.x(), target.y(), 70L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda50
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleReturnRewardTarget$26();
                }
            }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda51
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleReturnRewardTarget$27();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleReturnRewardTarget$26() {
        schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleReturnRewardTarget$27() {
        stopWithError(getString(R.string.status_return_reward_gesture_failed));
    }

    private void handleReturnRewardTokens(List<PetalMatcher.Token> list, int i, int i2) {
        PostcardMatcher.Target targetFindDiscard;
        int i3;
        int i4;
        if (returnRewardTimedOut()) {
            stopWithError(getString(R.string.status_return_reward_timeout));
            return;
        }
        if (PostcardMatcher.detectPage(list, i, i2) == PostcardMatcher.Page.POSTCARD_RECEIVED) {
            this.returnRewardScanGuard.reset();
            if (this.returnRewardReceivePostcard) {
                targetFindDiscard = PostcardMatcher.findReceive(list);
            } else {
                targetFindDiscard = PostcardMatcher.findDiscard(list, i, i2);
            }
            if (targetFindDiscard == null) {
                int i5 = this.returnRewardPostcardAttempts + 1;
                this.returnRewardPostcardAttempts = i5;
                if (i5 >= 3) {
                    stopWithError(getString(R.string.status_return_reward_postcard_missing));
                    return;
                } else {
                    schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                    return;
                }
            }
            if (this.returnRewardPostcardTarget != null && Math.abs(targetFindDiscard.x() - this.returnRewardPostcardTarget.x()) <= i * 0.04f && Math.abs(targetFindDiscard.y() - this.returnRewardPostcardTarget.y()) <= i2 * 0.025f) {
                this.returnRewardPostcardConfirmations++;
            } else {
                this.returnRewardPostcardTarget = targetFindDiscard;
                this.returnRewardPostcardConfirmations = 1;
            }
            if (this.returnRewardPostcardConfirmations < 2) {
                if (this.returnRewardReceivePostcard) {
                    i4 = R.string.status_return_reward_postcard_receive;
                } else {
                    i4 = R.string.status_return_reward_postcard_discard;
                }
                setReturnRewardStatus(getString(i4));
                schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
                return;
            }
            int i6 = this.returnRewardPostcardAttempts;
            if (i6 >= 3) {
                stopWithError(getString(R.string.status_return_reward_postcard_missing));
                return;
            }
            this.returnRewardPostcardAttempts = i6 + 1;
            this.returnRewardPostcardTarget = null;
            this.returnRewardPostcardConfirmations = 0;
            this.returnRewardWaitingPostcardExit = true;
            this.returnRewardLastTapAt = SystemClock.elapsedRealtime();
            if (this.returnRewardReceivePostcard) {
                i3 = R.string.status_return_reward_postcard_receive;
            } else {
                i3 = R.string.status_return_reward_postcard_discard;
            }
            setReturnRewardStatus(getString(i3));
            dispatchTap(targetFindDiscard.x(), targetFindDiscard.y(), 85L, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda42
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleReturnRewardTokens$28();
                }
            }, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda53
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleReturnRewardTokens$29();
                }
            });
            return;
        }
        if (this.returnRewardWaitingPostcardExit) {
            resetReturnRewardPostcard();
            this.returnRewardLastTapAt = SystemClock.elapsedRealtime();
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
            return;
        }
        this.returnRewardPostcardTarget = null;
        this.returnRewardPostcardConfirmations = 0;
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long j = this.returnRewardLastTapAt;
        long j2 = jElapsedRealtime - j;
        if (j > 0 && j2 < RETURN_REWARD_SETTLE_MILLIS) {
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SETTLE_MILLIS - j2);
        } else if (this.returnRewardScanGuard.observe(null, i, i2) == ReturnRewardScanGuard.Decision.COMPLETE) {
            finishWithSuccess(getString(R.string.status_return_reward_complete));
        } else {
            setReturnRewardStatus(getString(R.string.status_return_reward_waiting));
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleReturnRewardTokens$28() {
        schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$handleReturnRewardTokens$29() {
        if (this.returnRewardPostcardAttempts >= 3) {
            stopWithError(getString(R.string.status_return_reward_postcard_missing));
        } else {
            schedule(RETURN_REWARD_SCAN_DELAY_MILLIS);
        }
    }

    private boolean returnRewardTimedOut() {
        return this.returnRewardStartedAt > 0 && SystemClock.elapsedRealtime() - this.returnRewardStartedAt >= RETURN_REWARD_TIMEOUT_MILLIS;
    }

    private void setReturnRewardStatus(String str) {
        setStatus(str);
        setRunStatus(AutomationMode.RETURN_REWARD, OverlayRunStatus.Kind.RECOGNIZING, str, getString(R.string.overlay_return_reward_safety));
    }

    private void resetReturnRewardPostcard() {
        this.returnRewardPostcardTarget = null;
        this.returnRewardPostcardConfirmations = 0;
        this.returnRewardPostcardAttempts = 0;
        this.returnRewardWaitingPostcardExit = false;
    }

    private void handlePostcardTokens(List<PetalMatcher.Token> list, Bitmap bitmap) {
        FlowerDetailActionDetector.Target targetFind;
        int i;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        PostcardMatcher.Page pageDetectPage = PostcardMatcher.detectPage(list, width, height);
        boolean zIsFlowerNavigationStep = isFlowerNavigationStep(this.postcardAutomation.step());
        if (zIsFlowerNavigationStep) {
            Objects.requireNonNull(bitmap);
            targetFind = FlowerDetailActionDetector.find(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        } else {
            targetFind = null;
        }
        Objects.requireNonNull(bitmap);
        MapPostcardBubbleDetector.Target targetFind2 = MapPostcardBubbleDetector.find(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        if (pageDetectPage == PostcardMatcher.Page.UNKNOWN && targetFind2 != null && (isFlowerNavigationStep(this.postcardAutomation.step()) || this.postcardAutomation.step() == PostcardAutomation.Step.WAIT_RECEIPT_EXIT)) {
            pageDetectPage = PostcardMatcher.Page.MAP;
        }
        if (this.postcardAutomation.step() == PostcardAutomation.Step.USE_PETALS && pageDetectPage != PostcardMatcher.Page.WARNING && looksLikeWarningDialog(bitmap)) {
            pageDetectPage = PostcardMatcher.Page.WARNING;
        }
        if ((pageDetectPage == PostcardMatcher.Page.UNKNOWN || pageDetectPage == PostcardMatcher.Page.MAP) && zIsFlowerNavigationStep && targetFind != null) {
            pageDetectPage = PostcardMatcher.Page.FLOWER_DETAIL;
        }
        if (pageDetectPage == PostcardMatcher.Page.FLOWER_DETAIL && !zIsFlowerNavigationStep) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
        }
        boolean z = zIsFlowerNavigationStep || this.postcardAutomation.step() == PostcardAutomation.Step.WAIT_RECEIPT_EXIT;
        if (pageDetectPage == PostcardMatcher.Page.MAP && !z) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        if (PostcardPageRecovery.shouldRetryStableFrame(pageDetectPage, this.postcardAutomation.step())) {
            this.postcardUnknownFrames = 0;
            if (isPetalSearchStep(this.postcardAutomation.step())) {
                i = R.string.status_postcard_waiting_search_result;
            } else {
                i = R.string.status_postcard_waiting_page;
            }
            setPostcardStatus(OverlayRunStatus.Kind.RECOGNIZING, getString(i), getString(R.string.overlay_ocr_detail));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
            return;
        }
        if (this.postcardAutomation.receiveTapped() && pageDetectPage != PostcardMatcher.Page.POSTCARD_RECEIVED) {
            PostcardReturnGuard.Decision decisionObserve = this.postcardReturnGuard.observe(true, targetFind2 != null);
            if (decisionObserve == PostcardReturnGuard.Decision.WAIT) {
                setPostcardStatus(getString(R.string.status_postcard_checking_returned_bubble));
                schedule(POSTCARD_RECEIPT_RETURN_VERIFY_DELAY_MILLIS);
                return;
            } else if (decisionObserve == PostcardReturnGuard.Decision.FAILED) {
                stopWithError(getString(R.string.status_postcard_returned_bubble_missing));
                return;
            } else {
                if (confirmPostcardReceiptExit()) {
                    openPreviousPostcardBubble(targetFind2);
                    return;
                }
                return;
            }
        }
        if (pageDetectPage == PostcardMatcher.Page.UNKNOWN && this.postcardAutomation.step() == PostcardAutomation.Step.FIND_FLOWER) {
            int i2 = this.postcardUnknownFrames + 1;
            this.postcardUnknownFrames = i2;
            if (i2 >= 8) {
                stopWithError(getString(R.string.status_postcard_returned_bubble_missing));
                return;
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_previous_bubble));
                schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                return;
            }
        }
        if (pageDetectPage == PostcardMatcher.Page.UNKNOWN) {
            int i3 = this.postcardUnknownFrames + 1;
            this.postcardUnknownFrames = i3;
            if (i3 >= 8) {
                stopWithError(getString(R.string.status_postcard_unknown_stopped));
                return;
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_page));
                schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
                return;
            }
        }
        this.postcardUnknownFrames = 0;
        if (pageDetectPage == PostcardMatcher.Page.FLOWER_DETAIL && this.postcardAutomation.step() == PostcardAutomation.Step.FIND_FLOWER && this.postcardAutomation.completedCount() > 0) {
            returnToMapFromFlowerDetail();
            return;
        }
        if (pageDetectPage == PostcardMatcher.Page.MAP) {
            this.postcardBackAttempts = 0;
        }
        switch (AnonymousClass8.$SwitchMap$com$pikminx$helper$PostcardMatcher$Page[pageDetectPage.ordinal()]) {
            case 1:
                receivePostcard(list);
                break;
            case 2:
                handlePikminSelection(list, width, height);
                break;
            case 3:
                handlePostcardPetalSelection(list, bitmap);
                break;
            case 4:
                acceptPostcardWarning(list, width, height);
                break;
            case 5:
                openPostcardFromFlower(list, targetFind);
                break;
            case 6:
                openPreviousPostcardBubble(targetFind2);
                break;
            default:
                schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
                break;
        }
    }

    private static boolean isFlowerNavigationStep(PostcardAutomation.Step step) {
        return step == PostcardAutomation.Step.FIND_FLOWER || step == PostcardAutomation.Step.OPEN_FLOWER;
    }

    private static boolean isPetalSearchStep(PostcardAutomation.Step step) {
        return step == PostcardAutomation.Step.OPEN_PETAL_SEARCH || step == PostcardAutomation.Step.ENTER_PETAL_SEARCH || step == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD || step == PostcardAutomation.Step.SELECT_PETAL || step == PostcardAutomation.Step.TAP_NEXT;
    }

    private void acceptPostcardWarning(List<PetalMatcher.Token> list, int i, int i2) {
        PostcardMatcher.Target targetFindAcceptContinue = PostcardMatcher.findAcceptContinue(list);
        if (targetFindAcceptContinue == null) {
            targetFindAcceptContinue = new PostcardMatcher.Target("warning-image-accept", Math.round(i * 0.69f), Math.round(i2 * 0.58f));
        }
        tapPostcardTarget(targetFindAcceptContinue, PostcardAutomation.Step.OPEN_PETAL_SEARCH, getString(R.string.status_postcard_accepting), 700L);
    }

    private boolean confirmPostcardReceiptExit() {
        this.postcardReturnGuard.reset();
        if (!this.postcardAutomation.confirmReceiptExit()) {
            return false;
        }
        if (this.settings.recordConfirmedPostcardReceipt() < 0) {
            stopWithError(getString(R.string.status_postcard_progress_save_failed));
            return false;
        }
        this.actionAttempts = 0;
        this.postcardReceiptWaitFrames = 0;
        this.postcardPikminCountConfirmations = 0;
        this.postcardLastPikminCount = -1;
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        if (this.postcardAutomation.isComplete()) {
            finishPostcardAutomation();
            return false;
        }
        setPostcardStatus(OverlayRunStatus.Kind.SUCCESS, getString(R.string.status_postcard_progress, new Object[]{Integer.valueOf(this.postcardAutomation.completedCount()), Integer.valueOf(this.postcardAutomation.collectionLimit())}), getString(R.string.overlay_postcard_progress_detail, new Object[]{Integer.valueOf(this.postcardAutomation.completedCount()), Integer.valueOf(this.postcardAutomation.collectionLimit())}));
        return true;
    }

    private void openPreviousPostcardBubble(MapPostcardBubbleDetector.Target target) {
        if (target == null) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_previous_bubble));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
        } else {
            tapPostcardTarget(new PostcardMatcher.Target("previous-postcard-bubble", target.x(), target.y()), PostcardAutomation.Step.OPEN_FLOWER, getString(R.string.status_postcard_opening_previous_bubble), POSTCARD_VERIFY_DELAY_MILLIS);
        }
    }

    private void openPostcardFromFlower(List<PetalMatcher.Token> list, FlowerDetailActionDetector.Target target) {
        PostcardMatcher.Target targetFindUsePetals = PostcardMatcher.findUsePetals(list);
        if (targetFindUsePetals == null && target != null) {
            targetFindUsePetals = new PostcardMatcher.Target("detail-image-button", target.x(), target.y());
        }
        PostcardMatcher.Target target2 = targetFindUsePetals;
        if (target2 == null) {
            setPostcardStatus(getString(R.string.status_postcard_waiting_page));
            schedule(POSTCARD_VERIFY_DELAY_MILLIS);
        } else {
            this.postcardMissingControlFrames = 0;
            tapPostcardTarget(target2, PostcardAutomation.Step.USE_PETALS, getString(R.string.status_postcard_using_petals), 700L);
        }
    }

    private boolean looksLikeWarningDialog(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float f = width;
        int iRound = Math.round(0.06f * f);
        int iRound2 = Math.round(0.94f * f);
        float f2 = height;
        int iRound3 = Math.round(0.66f * f2);
        int iMax = Math.max(4, width / 96);
        int i = 0;
        int i2 = 0;
        for (int iRound4 = Math.round(0.38f * f2); iRound4 < iRound3; iRound4 += iMax) {
            for (int i3 = iRound; i3 < iRound2; i3 += iMax) {
                int pixel = bitmap.getPixel(i3, iRound4);
                int i4 = (pixel >>> 16) & 255;
                int i5 = (pixel >>> 8) & 255;
                int i6 = pixel & 255;
                i++;
                if (i4 >= 235 && i5 >= 235 && i6 >= 235) {
                    i2++;
                }
            }
        }
        int iRound5 = Math.round(0.52f * f);
        int iRound6 = Math.round(f * 0.86f);
        int iRound7 = Math.round(f2 * 0.62f);
        int i7 = 0;
        for (int iRound8 = Math.round(0.53f * f2); iRound8 < iRound7; iRound8 += iMax) {
            for (int i8 = iRound5; i8 < iRound6; i8 += iMax) {
                int pixel2 = bitmap.getPixel(i8, iRound8);
                int i9 = (pixel2 >>> 16) & 255;
                int i10 = (pixel2 >>> 8) & 255;
                int i11 = pixel2 & 255;
                if (i9 >= 180 && i9 - i10 >= 35 && i9 - i11 >= 35) {
                    i7++;
                }
            }
        }
        return i > 0 && i2 * 100 >= i * 58 && i7 >= 8;
    }

    private void handlePostcardPetalSelection(List<PetalMatcher.Token> list, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (this.postcardAutomation.step() == PostcardAutomation.Step.OPEN_PETAL_SEARCH) {
            openPostcardPetalSearch(bitmap);
            return;
        }
        if (this.postcardAutomation.step() == PostcardAutomation.Step.ENTER_PETAL_SEARCH) {
            enterPostcardPetalSearch();
            return;
        }
        if (this.postcardAutomation.step() == PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD) {
            closePostcardKeyboard();
            return;
        }
        if (this.postcardAutomation.step() == PostcardAutomation.Step.TAP_NEXT) {
            tapNextAfterPostcardPetal(list);
            return;
        }
        if (this.postcardAutomation.step() == PostcardAutomation.Step.NEXT || this.postcardAutomation.step() == PostcardAutomation.Step.OPEN_SORT) {
            PostcardMatcher.Target targetFindNext = PostcardMatcher.findNext(list);
            if (targetFindNext == null) {
                setPostcardStatus(getString(R.string.status_postcard_waiting_next));
                schedule(700L);
                return;
            } else {
                tapPostcardTarget(targetFindNext, PostcardAutomation.Step.OPEN_SORT, getString(R.string.status_postcard_next), DISPATCH_SCAN_DELAY_MILLIS);
                return;
            }
        }
        if (!gameEditableTextMatches(PostcardPotCatalog.searchQuery(this.postcardAutomation.petalPotName()))) {
            this.postcardAutomation.moveTo(PostcardAutomation.Step.ENTER_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_confirming_search_text));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
        } else {
            PostcardMatcher.PetalPot petalPotFindSingleVisiblePetalPot = PostcardMatcher.findSingleVisiblePetalPot(list, this.postcardAutomation.petalPotName(), 80, width, height);
            if (petalPotFindSingleVisiblePetalPot == null) {
                scanFocusedPetalRegion(bitmap);
            } else {
                confirmPostcardPetalPot(petalPotFindSingleVisiblePetalPot, width, height, bitmap);
            }
        }
    }

    private void openPostcardPetalSearch(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Objects.requireNonNull(bitmap);
        if (CardHighlight.isPetalSearchOpen(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap))) {
            this.postcardPetalSearchMissingFrames = 0;
            this.postcardAutomation.moveTo(PostcardAutomation.Step.ENTER_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_search_opened));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        Objects.requireNonNull(bitmap);
        CardHighlight.Point pointFindPetalSearchButton = CardHighlight.findPetalSearchButton(width, height, new PetalAccessibilityService$$ExternalSyntheticLambda31(bitmap));
        if (pointFindPetalSearchButton == null) {
            int i = this.postcardPetalSearchMissingFrames + 1;
            this.postcardPetalSearchMissingFrames = i;
            if (i >= 6) {
                stopWithError(getString(R.string.status_postcard_search_open_failed));
                return;
            }
        }
        if (pointFindPetalSearchButton == null) {
            setPostcardStatus(getString(R.string.status_postcard_opening_search));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
        } else {
            this.postcardPetalSearchMissingFrames = 0;
            tapPostcardTarget(new PostcardMatcher.Target("petal-search", pointFindPetalSearchButton.x(), pointFindPetalSearchButton.y()), PostcardAutomation.Step.OPEN_PETAL_SEARCH, getString(R.string.status_postcard_opening_search), POSTCARD_FAST_SCAN_DELAY_MILLIS);
        }
    }

    private void enterPostcardPetalSearch() {
        String strSearchQuery = PostcardPotCatalog.searchQuery(this.postcardAutomation.petalPotName());
        if (MainActivity$$ExternalSyntheticBackport0.m(strSearchQuery)) {
            stopWithError(getString(R.string.status_postcard_invalid_search_name));
            return;
        }
        if (!setGameEditableText(strSearchQuery)) {
            int i = this.postcardPetalInputAttempts + 1;
            this.postcardPetalInputAttempts = i;
            if (i >= 3) {
                stopWithError(getString(R.string.status_postcard_search_input_failed));
                return;
            }
            this.postcardAutomation.moveTo(PostcardAutomation.Step.OPEN_PETAL_SEARCH);
            setPostcardStatus(getString(R.string.status_postcard_retrying_search_input));
            schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
            return;
        }
        this.postcardPetalInputAttempts = 0;
        this.postcardKeyboardCloseAttempts = 0;
        this.postcardKeyboardAbsentFrames = 0;
        this.postcardAutomation.moveTo(PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD);
        setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
        schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closePostcardKeyboard() {
        if (!isInputMethodWindowVisible()) {
            int i = this.postcardKeyboardAbsentFrames + 1;
            this.postcardKeyboardAbsentFrames = i;
            if (i < 2) {
                setPostcardStatus(getString(R.string.status_postcard_waiting_keyboard_close));
                schedule(POSTCARD_FAST_SCAN_DELAY_MILLIS);
                return;
            }
            this.postcardKeyboardCloseAttempts = 0;
            this.postcardKeyboardAbsentFrames = 0;
            this.postcardAutomation.moveTo(PostcardAutomation.Step.SELECT_PETAL);
            setPostcardStatus(getString(R.string.status_postcard_keyboard_closed));
            schedule(700L);
            return;
        }
        this.postcardKeyboardAbsentFrames = 0;
        int i2 = this.postcardKeyboardCloseAttempts;
        if (i2 >= 3) {
            stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
            return;
        }
        this.postcardKeyboardCloseAttempts = i2 + 1;
        if (!performGlobalAction(1) && this.postcardKeyboardCloseAttempts >= 3) {
            stopWithError(getString(R.string.status_postcard_keyboard_close_failed));
        } else {
            setPostcardStatus(getString(R.string.status_postcard_closing_keyboard));
            schedule(600L);
        }
    }

    private boolean isInputMethodWindowVisible() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo accessibilityWindowInfo : windows) {
            if (accessibilityWindowInfo != null && accessibilityWindowInfo.getType() == 2) {
                return true;
            }
        }
        return false;
    }

    private boolean setGameEditableText(String str) {
        AccessibilityNodeInfo accessibilityNodeInfoFindGameNode = findGameNode(new Predicate() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda41
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PetalAccessibilityService.lambda$setGameEditableText$30((AccessibilityNodeInfo) obj);
            }
        });
        if (accessibilityNodeInfoFindGameNode == null) {
            return false;
        }
        if (PetalMatcher.normalize(accessibilityNodeInfoFindGameNode.getText() == null ? "" : accessibilityNodeInfoFindGameNode.getText().toString()).equals(PetalMatcher.normalize(str))) {
            return true;
        }
        Bundle bundle = new Bundle();
        bundle.putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, str);
        boolean zPerformAction = accessibilityNodeInfoFindGameNode.performAction(2097152, bundle);
        if (zPerformAction) {
            return zPerformAction;
        }
        accessibilityNodeInfoFindGameNode.performAction(1);
        return accessibilityNodeInfoFindGameNode.performAction(2097152, bundle);
    }

    static /* synthetic */ boolean lambda$setGameEditableText$30(AccessibilityNodeInfo accessibilityNodeInfo) {
        return accessibilityNodeInfo.isEditable() && accessibilityNodeInfo.isEnabled();
    }

    private boolean gameEditableTextMatches(String str) {
        AccessibilityNodeInfo accessibilityNodeInfoFindGameNode = findGameNode(new Predicate() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda27
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PetalAccessibilityService.lambda$gameEditableTextMatches$31((AccessibilityNodeInfo) obj);
            }
        });
        if (accessibilityNodeInfoFindGameNode == null || accessibilityNodeInfoFindGameNode.getText() == null) {
            return false;
        }
        return PetalMatcher.normalize(accessibilityNodeInfoFindGameNode.getText().toString()).equals(PetalMatcher.normalize(str));
    }

    static /* synthetic */ boolean lambda$gameEditableTextMatches$31(AccessibilityNodeInfo accessibilityNodeInfo) {
        return accessibilityNodeInfo.isEditable() && accessibilityNodeInfo.isEnabled();
    }

    private void tapNextAfterPostcardPetal(List<PetalMatcher.Token> list) {
        PostcardMatcher.Target targetFindNext = PostcardMatcher.findNext(list);
        if (targetFindNext == null) {
            int i = this.postcardMissingControlFrames + 1;
            this.postcardMissingControlFrames = i;
            if (i >= 3) {
                stopWithError(getString(R.string.status_postcard_control_missing));
                return;
            } else {
                setPostcardStatus(getString(R.string.status_postcard_waiting_next));
                schedule(700L);
                return;
            }
        }
        this.postcardMissingControlFrames = 0;
        resetPostcardPotConfirmation();
        tapPostcardTarget(targetFindNext, PostcardAutomation.Step.OPEN_SORT, getString(R.string.status_postcard_selection_confirmed), 700L);
    }

    private void scanFocusedPetalRegion(Bitmap bitmap) {
        Bitmap bitmapCreateBitmap;
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        float f = height;
        final int iRound = Math.round(0.44f * f);
        int iMax = Math.max(1, Math.round(f * 0.96f) - iRound);
        try {
            bitmapCreateBitmap = Bitmap.createBitmap(bitmap, 0, iRound, width, iMax);
            try {
                final Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmapCreateBitmap, width * 2, iMax * 2, true);
                if (bitmapCreateScaledBitmap != bitmapCreateBitmap) {
                    bitmapCreateBitmap.recycle();
                }
                final long j = this.runGeneration;
                this.busy = true;
                setPostcardStatus(OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_postcard_focused_petal_ocr), this.postcardAutomation.petalPotName());
                this.scanner.scanChinese(bitmapCreateScaledBitmap, getMainExecutor(), new OcrScanner.Callback() { // from class: com.pikminx.helper.PetalAccessibilityService.5
                    @Override // com.pikminx.helper.OcrScanner.Callback
                    public void onSuccess(List<PetalMatcher.Token> list) {
                        try {
                            if (PetalAccessibilityService.this.isActiveRun(j)) {
                                PetalAccessibilityService.this.busy = false;
                                if (PetalAccessibilityService.this.postcardAutomation.step() != PostcardAutomation.Step.SELECT_PETAL) {
                                    PetalAccessibilityService.this.schedule(PetalAccessibilityService.POSTCARD_VERIFY_DELAY_MILLIS);
                                } else {
                                    ArrayList arrayList = new ArrayList(list.size());
                                    for (PetalMatcher.Token token : list) {
                                        arrayList.add(new PetalMatcher.Token(token.text(), token.left() / 2, iRound + (token.top() / 2), token.right() / 2, iRound + (token.bottom() / 2)));
                                    }
                                    PostcardMatcher.PetalPot petalPotFindSingleVisiblePetalPot = PostcardMatcher.findSingleVisiblePetalPot(arrayList, PetalAccessibilityService.this.postcardAutomation.petalPotName(), 80, width, height);
                                    if (petalPotFindSingleVisiblePetalPot == null) {
                                        PetalAccessibilityService.this.handleFocusedPetalMiss();
                                    } else {
                                        PetalAccessibilityService.this.confirmPostcardPetalPot(petalPotFindSingleVisiblePetalPot, width, height, null);
                                    }
                                }
                            }
                        } finally {
                            bitmapCreateScaledBitmap.recycle();
                        }
                    }

                    @Override // com.pikminx.helper.OcrScanner.Callback
                    public void onFailure(Exception exc) {
                        try {
                            if (PetalAccessibilityService.this.isActiveRun(j)) {
                                PetalAccessibilityService.this.busy = false;
                                PetalAccessibilityService.this.handleFocusedPetalMiss();
                            }
                        } finally {
                            bitmapCreateScaledBitmap.recycle();
                        }
                    }
                });
            } catch (RuntimeException unused) {
                if (bitmapCreateBitmap != null && !bitmapCreateBitmap.isRecycled()) {
                    bitmapCreateBitmap.recycle();
                }
                handleFocusedPetalMiss();
            }
        } catch (RuntimeException unused2) {
            bitmapCreateBitmap = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleFocusedPetalMiss() {
        int i;
        this.postcardPetalSearchMissingFrames++;
        if (this.postcardPendingPot != null && (i = this.postcardPotMissingFrames) < 2) {
            this.postcardPotMissingFrames = i + 1;
        } else {
            resetPostcardPotConfirmation();
        }
        if (this.postcardPetalSearchMissingFrames >= 6) {
            stopWithError(getString(R.string.status_postcard_search_result_missing, new Object[]{PostcardPotCatalog.searchQuery(this.postcardAutomation.petalPotName())}));
        } else {
            setPostcardStatus(OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_postcard_waiting_search_result), PostcardPotCatalog.searchQuery(this.postcardAutomation.petalPotName()));
            schedule(700L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void confirmPostcardPetalPot(PostcardMatcher.PetalPot petalPot, int i, int i2, Bitmap bitmap) {
        if (isSamePostcardPotCandidate(this.postcardPendingPot, petalPot, i, i2)) {
            this.postcardPotConfirmations++;
            this.postcardPendingPot = petalPot;
        } else {
            this.postcardPendingPot = petalPot;
            this.postcardPotConfirmations = 1;
        }
        this.postcardPotMissingFrames = 0;
        this.postcardPetalSearchMissingFrames = 0;
        if (this.postcardPotConfirmations < 2 || bitmap == null) {
            setPostcardStatus(OverlayRunStatus.Kind.RECOGNIZING, getString(R.string.status_postcard_confirming_petal, new Object[]{petalPot.name(), Integer.valueOf(petalPot.count()), Integer.valueOf(this.postcardPotConfirmations), 2}), getString(R.string.overlay_ocr_detail));
            schedule(700L);
        } else {
            this.postcardReturnGuard.reset();
            tapPostcardTarget(postcardPetalTapTarget(petalPot, i, i2), PostcardAutomation.Step.TAP_NEXT, getString(R.string.status_postcard_selecting_petal, new Object[]{petalPot.name(), Integer.valueOf(petalPot.count())}), 650L);
        }
    }

    private PostcardMatcher.Target postcardPetalTapTarget(PostcardMatcher.PetalPot petalPot, int i, int i2) {
        float f = i2;
        return new PostcardMatcher.Target(petalPot.name() + "-tap", Math.max(0, Math.min(petalPot.x(), i - 1)), Math.max(Math.round(0.5f * f), Math.min(petalPot.y() - Math.round(0.075f * f), Math.round(f * 0.88f))));
    }

    private boolean isSamePostcardPotCandidate(PostcardMatcher.PetalPot petalPot, PostcardMatcher.PetalPot petalPot2, int i, int i2) {
        if (petalPot == null || petalPot2 == null) {
            return false;
        }
        String strCanonicalName = PostcardPotCatalog.canonicalName(petalPot.name());
        return strCanonicalName != null && strCanonicalName.equals(PostcardPotCatalog.canonicalName(petalPot2.name())) && ((float) Math.abs(petalPot.x() - petalPot2.x())) <= ((float) i) * 0.08f && ((float) Math.abs(petalPot.y() - petalPot2.y())) <= ((float) i2) * 0.07f;
    }

    private void resetPostcardPotConfirmation() {
        this.postcardPendingPot = null;
        this.postcardPotConfirmations = 0;
        this.postcardPotMissingFrames = 0;
    }

    private void resetPostcardPetalSearch() {
        this.postcardPetalSearchMissingFrames = 0;
        this.postcardPetalInputAttempts = 0;
        this.postcardKeyboardCloseAttempts = 0;
        this.postcardKeyboardAbsentFrames = 0;
    }

    private void handlePikminSelection(List<PetalMatcher.Token> list, int i, int i2) {
        int iSelectedPikminCount = PostcardMatcher.selectedPikminCount(list);
        int iPikminCount = this.postcardAutomation.pikminCount();
        if (iSelectedPikminCount > iPikminCount) {
            this.postcardLastPikminCount = iSelectedPikminCount;
            this.postcardPikminCountConfirmations = 0;
            List<PostcardMatcher.Target> listFindTopRowPikminSlots = PostcardMatcher.findTopRowPikminSlots(i, i2);
            tapPostcardTarget(iSelectedPikminCount <= listFindTopRowPikminSlots.size() ? listFindTopRowPikminSlots.get(iSelectedPikminCount - 1) : null, PostcardAutomation.Step.SELECT_PIKMIN, getString(R.string.status_postcard_deselect_pikmin, new Object[]{Integer.valueOf(iSelectedPikminCount - 1), Integer.valueOf(iPikminCount)}), 600L);
            return;
        }
        if (iSelectedPikminCount == iPikminCount) {
            if (this.postcardLastPikminCount == iSelectedPikminCount) {
                this.postcardPikminCountConfirmations++;
            } else {
                this.postcardLastPikminCount = iSelectedPikminCount;
                this.postcardPikminCountConfirmations = 1;
            }
            if (this.postcardPikminCountConfirmations < 2) {
                setPostcardStatus(getString(R.string.status_postcard_confirming_pikmin_count, new Object[]{Integer.valueOf(iSelectedPikminCount), Integer.valueOf(iPikminCount), Integer.valueOf(this.postcardPikminCountConfirmations), 2}));
                schedule(POSTCARD_VERIFY_DELAY_MILLIS);
                return;
            } else {
                tapPostcardTarget(PostcardMatcher.findGo(list), PostcardAutomation.Step.GO, getString(R.string.status_postcard_go), 650L);
                return;
            }
        }
        this.postcardLastPikminCount = iSelectedPikminCount;
        this.postcardPikminCountConfirmations = 0;
        List<PostcardMatcher.Target> listFindTopRowPikminSlots2 = PostcardMatcher.findTopRowPikminSlots(i, i2);
        tapPostcardTarget(iSelectedPikminCount < listFindTopRowPikminSlots2.size() ? listFindTopRowPikminSlots2.get(iSelectedPikminCount) : null, PostcardAutomation.Step.SELECT_PIKMIN, getString(R.string.status_postcard_select_pikmin, new Object[]{Integer.valueOf(iSelectedPikminCount + 1), Integer.valueOf(iPikminCount)}), 600L);
    }

    private void receivePostcard(List<PetalMatcher.Token> list) {
        if (this.postcardAutomation.receiveTapped()) {
            int i = this.postcardReceiptWaitFrames + 1;
            this.postcardReceiptWaitFrames = i;
            if (i >= 3) {
                this.postcardReceiptWaitFrames = 0;
                int i2 = this.actionAttempts + 1;
                this.actionAttempts = i2;
                if (i2 >= 3) {
                    stopWithError(getString(R.string.status_postcard_receive_stuck));
                    return;
                } else {
                    this.postcardAutomation.retryReceive();
                    receivePostcard(list);
                    return;
                }
            }
            setPostcardStatus(getString(R.string.status_postcard_waiting_receipt_exit));
            schedule(700L);
            return;
        }
        PostcardMatcher.Target targetFindReceive = PostcardMatcher.findReceive(list);
        if (targetFindReceive == null) {
            schedule(700L);
            return;
        }
        this.busy = true;
        setPostcardStatus(getString(R.string.status_postcard_receiving));
        dispatchTap(targetFindReceive.x(), targetFindReceive.y(), GAME_ACTION_TAP_DURATION_MILLIS, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda37
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$receivePostcard$32();
            }
        }, new PetalAccessibilityService$$ExternalSyntheticLambda38(this));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$receivePostcard$32() {
        this.busy = false;
        this.postcardReceiptWaitFrames = 0;
        this.postcardAutomation.markReceiveTapped();
        schedule(POSTCARD_RECEIPT_EXIT_DELAY_MILLIS);
    }

    private void tapPostcardTarget(PostcardMatcher.Target target, final PostcardAutomation.Step step, String str, final long j) {
        if (target == null) {
            int i = this.postcardMissingControlFrames + 1;
            this.postcardMissingControlFrames = i;
            if (i >= 3) {
                stopWithError(getString(R.string.status_postcard_control_missing));
                return;
            } else {
                setPostcardStatus(str);
                schedule(650L);
                return;
            }
        }
        this.busy = true;
        setPostcardStatus(str);
        dispatchTap(target.x(), target.y(), GAME_ACTION_TAP_DURATION_MILLIS, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda40
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$tapPostcardTarget$33(step, j);
            }
        }, new PetalAccessibilityService$$ExternalSyntheticLambda38(this));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$tapPostcardTarget$33(PostcardAutomation.Step step, long j) {
        long j2;
        this.busy = false;
        this.actionAttempts = 0;
        this.postcardUnknownFrames = 0;
        this.postcardMissingControlFrames = 0;
        this.postcardAutomation.moveTo(step);
        if (isPetalSearchStep(step)) {
            j2 = POSTCARD_PETAL_STEP_DELAY_MILLIS;
        } else {
            j2 = isFastPostcardStep(step) ? POSTCARD_FAST_SCAN_DELAY_MILLIS : POSTCARD_VERIFY_DELAY_MILLIS;
        }
        schedule(Math.max(j, j2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void postcardActionFailed() {
        this.busy = false;
        int i = this.actionAttempts + 1;
        this.actionAttempts = i;
        if (i >= 3) {
            stopWithError(getString(R.string.status_postcard_action_failed));
        } else {
            schedule(700L);
        }
    }

    private void returnToMapFromFlowerDetail() {
        this.postcardBackAttempts++;
        setPostcardStatus(getString(R.string.status_postcard_returning_map));
        if (!performGlobalAction(1) || this.postcardBackAttempts >= 3) {
            stopWithError(getString(R.string.status_postcard_return_failed));
        } else {
            schedule(RETURN_REWARD_AFTER_TAP_DELAY_MILLIS);
        }
    }

    private void finishPostcardAutomation() {
        String string = getString(R.string.status_postcard_complete, new Object[]{Integer.valueOf(this.postcardAutomation.completedCount())});
        finishWithSuccess(string);
        showFloatingNotice(string);
    }

    private void setPostcardStatus(String str) {
        OverlayRunStatus.Kind kind;
        int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$PostcardAutomation$Step[this.postcardAutomation.step().ordinal()];
        if (i == 4 || i == 9) {
            kind = OverlayRunStatus.Kind.SEARCHING;
        } else {
            kind = OverlayRunStatus.Kind.RECOGNIZING;
        }
        setPostcardStatus(kind, str, "");
    }

    private void setPostcardStatus(OverlayRunStatus.Kind kind, String str, String str2) {
        Log.i(TAG, "postcard step=" + this.postcardAutomation.step() + " status=" + str);
        setStatus(str);
        setRunStatus(AutomationMode.POSTCARD, kind, str, str2);
    }

    private void dispatchTap(int i, int i2, long j, final Runnable runnable, final Runnable runnable2) {
        final long j2 = this.runGeneration;
        Path path = new Path();
        path.moveTo(i, i2);
        if (dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, j)).build(), new AccessibilityService.GestureResultCallback() { // from class: com.pikminx.helper.PetalAccessibilityService.6
            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCompleted(GestureDescription gestureDescription) {
                if (PetalAccessibilityService.this.isActiveRun(j2)) {
                    runnable.run();
                }
            }

            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCancelled(GestureDescription gestureDescription) {
                if (PetalAccessibilityService.this.isActiveRun(j2)) {
                    PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                    petalAccessibilityService.setStatus(petalAccessibilityService.getString(R.string.status_tap_cancelled));
                    runnable2.run();
                }
            }
        }, this.handler) || !isActiveRun(j2)) {
            return;
        }
        setStatus(getString(R.string.status_tap_rejected));
        runnable2.run();
    }

    private void dispatchPath(Path path, long j, final Runnable runnable, final Runnable runnable2) {
        final long j2 = this.runGeneration;
        if (dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, j)).build(), new AccessibilityService.GestureResultCallback() { // from class: com.pikminx.helper.PetalAccessibilityService.7
            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCompleted(GestureDescription gestureDescription) {
                if (PetalAccessibilityService.this.isActiveRun(j2)) {
                    runnable.run();
                }
            }

            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCancelled(GestureDescription gestureDescription) {
                if (PetalAccessibilityService.this.isActiveRun(j2)) {
                    runnable2.run();
                }
            }
        }, this.handler) || !isActiveRun(j2)) {
            return;
        }
        runnable2.run();
    }

    private boolean isGameForeground() {
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (rootInActiveWindow == null || !GAME_PACKAGE.contentEquals(rootInActiveWindow.getPackageName())) {
            return GAME_PACKAGE.equals(this.recentPackage) && SystemClock.elapsedRealtime() - this.recentPackageAt < 10000;
        }
        return true;
    }

    private Rect activeGameBoundsStrict() {
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (rootInActiveWindow == null || !GAME_PACKAGE.contentEquals(rootInActiveWindow.getPackageName())) {
            return null;
        }
        Rect rect = new Rect();
        rootInActiveWindow.getBoundsInScreen(rect);
        if (rect.isEmpty()) {
            rect.set(0, 0, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        }
        return rect;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isActiveRun(long j) {
        return this.running && j == this.runGeneration;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scanFailed(String str, long j) {
        if (isActiveRun(j)) {
            this.busy = false;
            setStatus(str);
            scheduleNext();
        }
    }

    private void scheduleNext() {
        schedule(SCAN_INTERVAL_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void schedule(long j) {
        long jDispatchMinimumScanDelay;
        this.handler.removeCallbacks(this.scanTask);
        if (this.running) {
            int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode[this.automationMode.ordinal()];
            if (i == 1) {
                jDispatchMinimumScanDelay = dispatchMinimumScanDelay();
            } else if (i == 2) {
                jDispatchMinimumScanDelay = RETURN_REWARD_SCAN_DELAY_MILLIS;
            } else if (i != 3) {
                jDispatchMinimumScanDelay = 200;
            } else if (isPetalSearchStep(this.postcardAutomation.step())) {
                jDispatchMinimumScanDelay = POSTCARD_PETAL_STEP_DELAY_MILLIS;
            } else {
                jDispatchMinimumScanDelay = isFastPostcardStep(this.postcardAutomation.step()) ? POSTCARD_FAST_SCAN_DELAY_MILLIS : POSTCARD_MIN_SCAN_DELAY_MILLIS;
            }
            this.handler.postDelayed(this.scanTask, Math.max(jDispatchMinimumScanDelay, j));
        }
    }

    private long dispatchMinimumScanDelay() {
        if (this.expeditionDispatchSession == null) {
            return DISPATCH_SCAN_DELAY_MILLIS;
        }
        int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[this.expeditionDispatchSession.stage().ordinal()];
        if (i != 1) {
            return (i == 3 && this.dispatchSelectionMethod == DispatchSelectionMethod.DRAG_12 && this.dispatchColorSelected && !this.dispatchPikminSelected) ? DISPATCH_PIKMIN_TAP_DELAY_MILLIS : DISPATCH_SCAN_DELAY_MILLIS;
        }
        return DISPATCH_AFTER_SCROLL_DELAY_MILLIS;
    }

    private static boolean isFastPostcardStep(PostcardAutomation.Step step) {
        switch (AnonymousClass8.$SwitchMap$com$pikminx$helper$PostcardAutomation$Step[step.ordinal()]) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return true;
            default:
                return false;
        }
    }

    private boolean showOverlay() {
        if (this.overlay != null) {
            return true;
        }
        this.windowManager = (WindowManager) getSystemService("window");
        DraggableIcon draggableIcon = new DraggableIcon();
        draggableIcon.setImageResource(R.drawable.ic_overlay_flower);
        draggableIcon.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_icon_description), getString(R.string.overlay_icon_move_hint)}));
        draggableIcon.setFocusable(true);
        draggableIcon.setElevation(dp(2));
        draggableIcon.setPadding(dp(5), dp(5), dp(5), dp(5));
        draggableIcon.setBackground(roundedBackground(OVERLAY_SURFACE, OVERLAY_GREEN, 10));
        draggableIcon.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showOverlay$34(view);
            }
        });
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(dp(40), dp(40), 2032, 264, -3);
        layoutParams.gravity = 8388659;
        layoutParams.x = 0;
        layoutParams.y = dp(72);
        draggableIcon.setOnTouchListener(new DragListener());
        if (!safeAddOverlayView(draggableIcon, layoutParams, "icon")) {
            return false;
        }
        this.overlay = draggableIcon;
        this.overlayParams = layoutParams;
        renderOverlayStatus(false);
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showOverlay$34(View view) {
        if (this.running) {
            pause(getString(R.string.status_paused));
            showFloatingNotice(getString(R.string.status_paused));
        } else {
            showSettingsOverlay();
        }
    }

    private void showSettingsOverlay() {
        if (this.settingsOverlay != null || this.overlay == null || this.windowManager == null) {
            return;
        }
        pause(getString(R.string.status_paused));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(10), dp(10), dp(10), dp(8));
        linearLayout.setElevation(dp(18));
        linearLayout.setBackground(roundedBackground(OVERLAY_SURFACE, OVERLAY_BORDER, 24));
        linearLayout.setAccessibilityPaneTitle(getString(R.string.overlay_brand_title));
        linearLayout.setImportantForAccessibility(1);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(12), dp(6), dp(4), dp(8));
        TextView textViewFormText = formText(getString(R.string.overlay_brand_title), 20, Color.rgb(23, 59, 42));
        textViewFormText.setTypeface(null, 1);
        linearLayout2.addView(textViewFormText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        String string = getString(R.string.overlay_ready);
        int i = OVERLAY_GREEN;
        TextView textViewFormText2 = formText(string, 11, i);
        textViewFormText2.setGravity(17);
        textViewFormText2.setPadding(dp(12), 0, dp(12), 0);
        textViewFormText2.setBackground(roundedBackground(OVERLAY_MINT, 0, 15));
        textViewFormText2.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_current_status), getString(R.string.overlay_ready)}));
        linearLayout2.addView(textViewFormText2, new LinearLayout.LayoutParams(dp(96), dp(32)));
        Button buttonCompactIconButton = compactIconButton("×", getString(R.string.overlay_close));
        buttonCompactIconButton.setTextColor(Color.rgb(53, 83, 66));
        buttonCompactIconButton.setBackground(roundedBackground(Color.rgb(237, 242, 236), 0, 18));
        buttonCompactIconButton.setContentDescription(getString(R.string.overlay_close));
        buttonCompactIconButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$35(view);
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        layoutParams.setMarginStart(dp(6));
        linearLayout2.addView(buttonCompactIconButton, layoutParams);
        linearLayout.addView(linearLayout2);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setPadding(dp(4), dp(4), dp(4), dp(4));
        linearLayout3.setBackground(roundedBackground(Color.rgb(237, 242, 236), 0, 14));
        final Button buttonOverlayButton = overlayButton(getString(R.string.overlay_tab_planting));
        final Button buttonOverlayButton2 = overlayButton(getString(R.string.overlay_tab_postcard));
        final Button buttonOverlayButton3 = overlayButton(getString(R.string.overlay_tab_reward));
        final Button buttonOverlayButton4 = overlayButton(getString(R.string.overlay_tab_return_reward));
        buttonOverlayButton.setTextSize(11.0f);
        buttonOverlayButton2.setTextSize(11.0f);
        buttonOverlayButton3.setTextSize(11.0f);
        buttonOverlayButton4.setTextSize(11.0f);
        buttonOverlayButton.setContentDescription(getString(R.string.overlay_tab_planting_description));
        buttonOverlayButton2.setContentDescription(getString(R.string.overlay_tab_postcard_description));
        buttonOverlayButton3.setContentDescription(getString(R.string.overlay_tab_reward_description));
        buttonOverlayButton4.setContentDescription(getString(R.string.overlay_tab_return_reward_description));
        linearLayout3.addView(buttonOverlayButton, weightedButtonParams());
        linearLayout3.addView(buttonOverlayButton2, weightedButtonParams());
        linearLayout3.addView(buttonOverlayButton3, weightedButtonParams());
        linearLayout3.addView(buttonOverlayButton4, weightedButtonParams());
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.setMargins(dp(10), 0, dp(10), dp(8));
        linearLayout.addView(linearLayout3, layoutParams2);
        final LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setFocusableInTouchMode(true);
        LinearLayout linearLayout5 = new LinearLayout(this);
        linearLayout5.setOrientation(1);
        linearLayout5.setPadding(dp(12), dp(6), dp(12), dp(10));
        final TextView textViewFormText3 = formText(getString(R.string.overlay_ready_planting), 15, i);
        linearLayout5.addView(settingsStatusCard(textViewFormText3, getString(R.string.overlay_planting_summary, new Object[]{Integer.valueOf(this.settings.allowedFlowers().size()), Integer.valueOf(this.settings.threshold())})));
        linearLayout5.addView(settingsSectionTitle(getString(R.string.overlay_section_switch_condition), ""));
        final StepperField stepperField = new StepperField(getString(R.string.overlay_threshold_short), getString(R.string.overlay_threshold_range), this.settings.threshold(), 1, 1200);
        linearLayout5.addView(stepperField);
        linearLayout5.addView(settingsSectionTitle(getString(R.string.overlay_section_flower_order), getString(R.string.overlay_flower_order_helper)));
        final FlowerOrderEditor flowerOrderEditor = new FlowerOrderEditor(this.settings.allowedFlowers());
        linearLayout5.addView(flowerOrderEditor);
        final TextView textView = settingsErrorView();
        linearLayout5.addView(textView);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.addView(linearLayout5);
        linearLayout4.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        Button buttonOverlayButton5 = overlayButton(getString(R.string.overlay_save));
        final Button buttonOverlayButton6 = overlayButton(getString(R.string.action_start));
        stylePrimaryButton(buttonOverlayButton6);
        final Runnable runnable = new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda17
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$showSettingsOverlay$36(stepperField, flowerOrderEditor);
            }
        };
        buttonOverlayButton5.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$37(runnable, textView, view);
            }
        });
        buttonOverlayButton6.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda19
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$38(runnable, textView, view);
            }
        });
        linearLayout4.addView(settingsFooter(buttonOverlayButton5, buttonOverlayButton6));
        final LinearLayout linearLayout6 = new LinearLayout(this);
        linearLayout6.setOrientation(1);
        linearLayout6.setFocusableInTouchMode(true);
        LinearLayout linearLayout7 = new LinearLayout(this);
        linearLayout7.setOrientation(1);
        linearLayout7.setPadding(dp(12), dp(6), dp(12), dp(10));
        linearLayout7.addView(settingsSectionTitle(getString(R.string.overlay_postcard_settings_section), ""));
        final StepperField stepperField2 = new StepperField(getString(R.string.overlay_collection_short), getString(R.string.overlay_collection_range), this.settings.postcardCollectionLimit(), 0, 15);
        linearLayout7.addView(stepperField2);
        linearLayout7.addView(settingsSectionTitle(getString(R.string.overlay_pikmin_short), getString(R.string.overlay_pikmin_range)));
        final NumberChoiceSelector numberChoiceSelector = new NumberChoiceSelector(getString(R.string.overlay_pikmin_short), this.settings.postcardPikminCount(), 1, 5);
        linearLayout7.addView(numberChoiceSelector);
        linearLayout7.addView(settingsSectionTitle(getString(R.string.overlay_postcard_petal_color_filter), getString(R.string.overlay_postcard_petal_color_filter_helper)));
        final PostcardPotSelector postcardPotSelector = new PostcardPotSelector(this.settings.postcardPetalPotName());
        linearLayout7.addView(postcardPotSelector);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams3.topMargin = dp(8);
        String string2 = getString(R.string.overlay_postcard_petal_requirement);
        int i2 = OVERLAY_CREAM;
        linearLayout7.addView(settingsHelperCard(string2, i2), layoutParams3);
        final TextView textView2 = settingsErrorView();
        linearLayout7.addView(textView2);
        ScrollView scrollView2 = new ScrollView(this);
        scrollView2.setFillViewport(true);
        scrollView2.setClipToPadding(false);
        scrollView2.addView(linearLayout7);
        linearLayout6.addView(scrollView2, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        Button buttonOverlayButton7 = overlayButton(getString(R.string.overlay_save));
        final Button buttonOverlayButton8 = overlayButton(getString(R.string.overlay_postcard_start));
        stylePrimaryButton(buttonOverlayButton8);
        final Runnable runnable2 = new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda21
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$showSettingsOverlay$39(stepperField2, postcardPotSelector, numberChoiceSelector);
            }
        };
        buttonOverlayButton7.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$40(runnable2, textView2, view);
            }
        });
        buttonOverlayButton8.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$41(stepperField2, postcardPotSelector, numberChoiceSelector, textView2, view);
            }
        });
        linearLayout6.addView(settingsFooter(buttonOverlayButton7, buttonOverlayButton8));
        linearLayout6.setVisibility(8);
        final LinearLayout linearLayout8 = new LinearLayout(this);
        linearLayout8.setOrientation(1);
        linearLayout8.setFocusableInTouchMode(true);
        LinearLayout linearLayout9 = new LinearLayout(this);
        linearLayout9.setOrientation(1);
        linearLayout9.setPadding(dp(12), dp(6), dp(12), dp(10));
        final TextView textViewFormText4 = formText(getString(R.string.overlay_reward_status_unselected), 15, i);
        linearLayout9.addView(settingsStatusCard(textViewFormText4, getString(R.string.overlay_reward_status_summary)));
        final StepperField stepperField3 = new StepperField(getString(R.string.overlay_reward_count_section), getString(R.string.overlay_reward_count_helper), this.settings.expeditionDispatchCount(), 1, 99);
        linearLayout9.addView(stepperField3, matchWidthParams(dp(72), dp(4)));
        linearLayout9.addView(settingsSectionTitle(getString(R.string.overlay_reward_target_section), getString(R.string.overlay_reward_target_helper)));
        final DispatchTargetSelector dispatchTargetSelector = new DispatchTargetSelector(this.settings.expeditionTargetMode());
        linearLayout9.addView(dispatchTargetSelector);
        linearLayout9.addView(settingsSectionTitle(getString(R.string.overlay_reward_method_section), ""));
        final DispatchMethodSelector dispatchMethodSelector = new DispatchMethodSelector(this.settings.dispatchSelectionMethod());
        linearLayout9.addView(dispatchMethodSelector);
        linearLayout9.addView(settingsSectionTitle(getString(R.string.overlay_reward_pikmin_section), ""));
        final DispatchPikminTypeSelector dispatchPikminTypeSelector = new DispatchPikminTypeSelector(this.settings.dispatchPikminType());
        linearLayout9.addView(dispatchPikminTypeSelector);
        final TextView textView3 = settingsErrorView();
        linearLayout9.addView(textView3);
        ScrollView scrollView3 = new ScrollView(this);
        scrollView3.setFillViewport(true);
        scrollView3.setClipToPadding(false);
        scrollView3.addView(linearLayout9);
        linearLayout8.addView(scrollView3, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        Button buttonOverlayButton9 = overlayButton(getString(R.string.overlay_save));
        final Button buttonOverlayButton10 = overlayButton(getString(R.string.overlay_reward_start));
        stylePrimaryButton(buttonOverlayButton10);
        final Runnable runnable3 = new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda24
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$showSettingsOverlay$42(stepperField3, dispatchTargetSelector, dispatchMethodSelector, dispatchPikminTypeSelector);
            }
        };
        buttonOverlayButton9.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda25
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$43(runnable3, textView3, view);
            }
        });
        buttonOverlayButton10.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda26
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$45(runnable3, dispatchTargetSelector, dispatchMethodSelector, dispatchPikminTypeSelector, textView3, view);
            }
        });
        linearLayout8.addView(settingsFooter(buttonOverlayButton9, buttonOverlayButton10));
        linearLayout8.setVisibility(8);
        final LinearLayout linearLayout10 = new LinearLayout(this);
        linearLayout10.setOrientation(1);
        linearLayout10.setFocusableInTouchMode(true);
        LinearLayout linearLayout11 = new LinearLayout(this);
        linearLayout11.setOrientation(1);
        linearLayout11.setPadding(dp(12), dp(6), dp(12), dp(10));
        final TextView textViewFormText5 = formText(getString(R.string.overlay_reward_status_unselected), 15, i);
        linearLayout11.addView(settingsStatusCard(textViewFormText5, getString(R.string.overlay_return_reward_status_summary)));
        linearLayout11.addView(settingsSectionTitle(getString(R.string.overlay_return_reward_postcard_section), getString(R.string.overlay_return_reward_postcard_helper)));
        final ReturnPostcardActionSelector returnPostcardActionSelector = new ReturnPostcardActionSelector(this.settings.receiveReturnedPostcards());
        linearLayout11.addView(returnPostcardActionSelector);
        linearLayout11.addView(settingsHelperCard(getString(R.string.overlay_return_reward_safety), i2));
        ScrollView scrollView4 = new ScrollView(this);
        scrollView4.setFillViewport(true);
        scrollView4.setClipToPadding(false);
        scrollView4.addView(linearLayout11);
        linearLayout10.addView(scrollView4, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        Button buttonOverlayButton11 = overlayButton(getString(R.string.overlay_save));
        final Button buttonOverlayButton12 = overlayButton(getString(R.string.overlay_return_reward_start));
        stylePrimaryButton(buttonOverlayButton12);
        final Runnable runnable4 = new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda10
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$showSettingsOverlay$46(returnPostcardActionSelector);
            }
        };
        buttonOverlayButton11.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$47(runnable4, view);
            }
        });
        buttonOverlayButton12.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$49(runnable4, returnPostcardActionSelector, view);
            }
        });
        linearLayout10.addView(settingsFooter(buttonOverlayButton11, buttonOverlayButton12));
        linearLayout10.setVisibility(8);
        linearLayout.addView(linearLayout4, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        linearLayout.addView(linearLayout6, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        linearLayout.addView(linearLayout8, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        linearLayout.addView(linearLayout10, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$50(linearLayout4, linearLayout6, linearLayout8, linearLayout10, buttonOverlayButton, buttonOverlayButton2, buttonOverlayButton3, buttonOverlayButton4, textViewFormText3, buttonOverlayButton6, view);
            }
        });
        buttonOverlayButton2.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$51(linearLayout4, linearLayout6, linearLayout8, linearLayout10, buttonOverlayButton2, buttonOverlayButton, buttonOverlayButton3, buttonOverlayButton4, buttonOverlayButton8, view);
            }
        });
        buttonOverlayButton3.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$52(linearLayout4, linearLayout6, linearLayout8, linearLayout10, buttonOverlayButton3, buttonOverlayButton, buttonOverlayButton2, buttonOverlayButton4, textViewFormText4, buttonOverlayButton10, view);
            }
        });
        buttonOverlayButton4.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$showSettingsOverlay$53(linearLayout4, linearLayout6, linearLayout8, linearLayout10, buttonOverlayButton4, buttonOverlayButton, buttonOverlayButton2, buttonOverlayButton3, textViewFormText5, buttonOverlayButton12, view);
            }
        });
        setSelectedTab(buttonOverlayButton, buttonOverlayButton2, buttonOverlayButton3, buttonOverlayButton4);
        WindowManager.LayoutParams layoutParams4 = new WindowManager.LayoutParams(Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(16)), Math.min(dp(720), getResources().getDisplayMetrics().heightPixels - dp(48)), 2032, 258, -3);
        layoutParams4.gravity = 17;
        layoutParams4.dimAmount = 0.18f;
        layoutParams4.softInputMode = 19;
        if (safeAddOverlayView(linearLayout, layoutParams4, "settings")) {
            this.settingsOverlay = linearLayout;
            this.status = textViewFormText3;
            this.toggle = buttonOverlayButton6;
            this.overlay.setVisibility(8);
            textViewFormText.setFocusable(true);
            textViewFormText.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$35(View view) {
        closeSettingsOverlay(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$36(StepperField stepperField, FlowerOrderEditor flowerOrderEditor) {
        SettingsInput settingsInput = SettingsInput.parse(stepperField.valueText(), flowerOrderEditor.valueText());
        this.settings.save(settingsInput.threshold(), settingsInput.flowers());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$37(Runnable runnable, TextView textView, View view) {
        try {
            runnable.run();
            closeSettingsOverlay(true);
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$38(Runnable runnable, TextView textView, View view) {
        try {
            runnable.run();
            startAutomation();
            if (this.running) {
                closeSettingsOverlay(false);
            }
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$39(StepperField stepperField, PostcardPotSelector postcardPotSelector, NumberChoiceSelector numberChoiceSelector) {
        PostcardSettingsInput postcardSettingsInput = PostcardSettingsInput.parse(stepperField.valueText(), postcardPotSelector.value(), numberChoiceSelector.valueText());
        this.settings.savePostcardSettings(postcardSettingsInput.collectionLimit(), postcardSettingsInput.petalPotName(), postcardSettingsInput.pikminCount());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$40(Runnable runnable, TextView textView, View view) {
        try {
            runnable.run();
            closeSettingsOverlay(true);
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$41(StepperField stepperField, PostcardPotSelector postcardPotSelector, NumberChoiceSelector numberChoiceSelector, TextView textView, View view) {
        try {
            PostcardSettingsInput postcardSettingsInput = PostcardSettingsInput.parse(stepperField.valueText(), postcardPotSelector.value(), numberChoiceSelector.valueText());
            if (postcardSettingsInput.collectionLimit() == 0) {
                throw new IllegalArgumentException(getString(R.string.overlay_postcard_zero_remaining));
            }
            this.settings.savePostcardSettings(postcardSettingsInput.collectionLimit(), postcardSettingsInput.petalPotName(), postcardSettingsInput.pikminCount());
            startPostcardAutomation(postcardSettingsInput.collectionLimit(), postcardSettingsInput.petalPotName(), postcardSettingsInput.pikminCount());
            if (this.running) {
                closeSettingsOverlay(false);
            }
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$42(StepperField stepperField, DispatchTargetSelector dispatchTargetSelector, DispatchMethodSelector dispatchMethodSelector, DispatchPikminTypeSelector dispatchPikminTypeSelector) {
        try {
            int i = Integer.parseInt(stepperField.valueText().trim());
            if (i < 1 || i > 99) {
                throw new IllegalArgumentException(getString(R.string.overlay_reward_count_invalid));
            }
            this.settings.saveExpeditionDispatchSettings(i, dispatchTargetSelector.value(), dispatchMethodSelector.value(), dispatchPikminTypeSelector.value());
        } catch (NumberFormatException unused) {
            throw new IllegalArgumentException(getString(R.string.overlay_reward_count_invalid));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$43(Runnable runnable, TextView textView, View view) {
        try {
            runnable.run();
            closeSettingsOverlay(true);
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$45(Runnable runnable, DispatchTargetSelector dispatchTargetSelector, DispatchMethodSelector dispatchMethodSelector, DispatchPikminTypeSelector dispatchPikminTypeSelector, TextView textView, View view) {
        try {
            runnable.run();
            final int iExpeditionDispatchCount = this.settings.expeditionDispatchCount();
            final ExpeditionTargetMode expeditionTargetModeValue = dispatchTargetSelector.value();
            final DispatchSelectionMethod dispatchSelectionMethodValue = dispatchMethodSelector.value();
            final DispatchPikminType dispatchPikminTypeValue = dispatchPikminTypeSelector.value();
            closeSettingsOverlay(false);
            this.handler.postDelayed(new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda6
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$showSettingsOverlay$44(iExpeditionDispatchCount, expeditionTargetModeValue, dispatchSelectionMethodValue, dispatchPikminTypeValue);
                }
            }, GAME_ACTION_TAP_DURATION_MILLIS);
        } catch (IllegalArgumentException e) {
            textView.setText(e.getMessage());
            textView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$46(ReturnPostcardActionSelector returnPostcardActionSelector) {
        this.settings.saveReturnRewardSettings(returnPostcardActionSelector.receive());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$47(Runnable runnable, View view) {
        runnable.run();
        closeSettingsOverlay(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$49(Runnable runnable, ReturnPostcardActionSelector returnPostcardActionSelector, View view) {
        runnable.run();
        final boolean zReceive = returnPostcardActionSelector.receive();
        closeSettingsOverlay(false);
        this.handler.postDelayed(new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda20
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$showSettingsOverlay$48(zReceive);
            }
        }, GAME_ACTION_TAP_DURATION_MILLIS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$50(LinearLayout linearLayout, LinearLayout linearLayout2, LinearLayout linearLayout3, LinearLayout linearLayout4, Button button, Button button2, Button button3, Button button4, TextView textView, Button button5, View view) {
        linearLayout.setVisibility(0);
        linearLayout2.setVisibility(8);
        linearLayout3.setVisibility(8);
        linearLayout4.setVisibility(8);
        setSelectedTab(button, button2, button3, button4);
        this.status = textView;
        this.toggle = button5;
        linearLayout.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$51(LinearLayout linearLayout, LinearLayout linearLayout2, LinearLayout linearLayout3, LinearLayout linearLayout4, Button button, Button button2, Button button3, Button button4, Button button5, View view) {
        linearLayout.setVisibility(8);
        linearLayout2.setVisibility(0);
        linearLayout3.setVisibility(8);
        linearLayout4.setVisibility(8);
        setSelectedTab(button, button2, button3, button4);
        this.status = null;
        this.toggle = button5;
        linearLayout2.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$52(LinearLayout linearLayout, LinearLayout linearLayout2, LinearLayout linearLayout3, LinearLayout linearLayout4, Button button, Button button2, Button button3, Button button4, TextView textView, Button button5, View view) {
        linearLayout.setVisibility(8);
        linearLayout2.setVisibility(8);
        linearLayout3.setVisibility(0);
        linearLayout4.setVisibility(8);
        setSelectedTab(button, button2, button3, button4);
        this.status = textView;
        this.toggle = button5;
        linearLayout3.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSettingsOverlay$53(LinearLayout linearLayout, LinearLayout linearLayout2, LinearLayout linearLayout3, LinearLayout linearLayout4, Button button, Button button2, Button button3, Button button4, TextView textView, Button button5, View view) {
        linearLayout.setVisibility(8);
        linearLayout2.setVisibility(8);
        linearLayout3.setVisibility(8);
        linearLayout4.setVisibility(0);
        setSelectedTab(button, button2, button3, button4);
        this.status = textView;
        this.toggle = button5;
        linearLayout4.requestFocus();
    }

    private void closeSettingsOverlay(boolean z) {
        View view = this.settingsOverlay;
        if (view == null) {
            return;
        }
        this.settingsOverlay = null;
        this.status = null;
        this.toggle = null;
        ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(view.getWindowToken(), 0);
        safeRemoveOverlayView(view, "settings");
        View view2 = this.overlay;
        if (view2 != null) {
            SettingsStore settingsStore = this.settings;
            view2.setVisibility((settingsStore == null || !settingsStore.overlayVisible()) ? 8 : 0);
            this.overlay.requestFocus();
        }
        if (z) {
            showFloatingNotice(getString(R.string.overlay_saved));
        }
    }

    private LinearLayout settingsStatusCard(TextView textView, String str) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        linearLayout.setBackground(roundedBackground(OVERLAY_MINT, OVERLAY_BORDER, 16));
        String string = getString(R.string.overlay_current_status);
        int i = OVERLAY_MUTED;
        TextView textViewFormText = formText(string, 11, i);
        textViewFormText.setImportantForAccessibility(2);
        linearLayout.addView(textViewFormText);
        textView.setTextSize(15.0f);
        textView.setTextColor(Color.rgb(31, 72, 48));
        textView.setTypeface(null, 1);
        textView.setPadding(0, dp(3), 0, 0);
        textView.setContentDescription(getString(R.string.overlay_status_label));
        textView.setAccessibilityLiveRegion(1);
        linearLayout.addView(textView);
        TextView textViewFormText2 = formText(str, 12, i);
        textViewFormText2.setPadding(0, dp(5), 0, 0);
        linearLayout.addView(textViewFormText2);
        return linearLayout;
    }

    private TextView settingsSectionTitle(String str, String str2) {
        TextView textViewFormText = formText(str, 15, Color.rgb(30, 65, 35));
        textViewFormText.setTypeface(null, 1);
        textViewFormText.setPadding(0, dp(14), 0, dp(MainActivity$$ExternalSyntheticBackport0.m(str2) ? 7 : 2));
        if (!MainActivity$$ExternalSyntheticBackport0.m(str2)) {
            textViewFormText.setText(getString(R.string.overlay_section_with_helper, new Object[]{str, str2}));
            textViewFormText.setLineSpacing(0.0f, 1.2f);
        }
        return textViewFormText;
    }

    private TextView settingsErrorView() {
        TextView textViewFormText = formText("", 13, OVERLAY_ACCENT);
        textViewFormText.setFocusable(true);
        textViewFormText.setAccessibilityLiveRegion(2);
        textViewFormText.setMinHeight(dp(28));
        textViewFormText.setPadding(dp(2), dp(6), dp(2), 0);
        return textViewFormText;
    }

    private LinearLayout settingsFooter(Button button, Button button2) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(12), dp(10), dp(12), dp(6));
        linearLayout.setBackground(roundedBackground(OVERLAY_SURFACE, 0, 14));
        linearLayout.addView(button, new LinearLayout.LayoutParams(0, dp(48), 0.8f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(48), 1.7f);
        layoutParams.setMarginStart(dp(8));
        linearLayout.addView(button2, layoutParams);
        return linearLayout;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public TextView settingsHelperCard(String str, int i) {
        TextView textViewFormText = formText(str, 12, OVERLAY_MUTED);
        textViewFormText.setPadding(dp(12), dp(9), dp(12), dp(9));
        textViewFormText.setBackground(roundedBackground(i, 0, 12));
        return textViewFormText;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public EditText numberField(int i) {
        EditText editText = new EditText(this);
        editText.setText(String.valueOf(i));
        editText.setTextSize(16.0f);
        editText.setTextColor(Color.rgb(32, 48, 38));
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setMinHeight(dp(50));
        editText.setInputType(2);
        editText.setImeOptions(5);
        editText.setSelectAllOnFocus(true);
        return editText;
    }

    private EditText singleLineTextField(String str, int i) {
        EditText editText = new EditText(this);
        editText.setText(str);
        editText.setHint(i);
        editText.setTextSize(16.0f);
        editText.setTextColor(Color.rgb(32, 48, 38));
        editText.setHintTextColor(OVERLAY_MUTED);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setMinHeight(dp(50));
        editText.setSingleLine(true);
        editText.setInputType(1);
        editText.setImeOptions(6);
        editText.setSelectAllOnFocus(true);
        return editText;
    }

    private EditText flowerInput(String str) {
        EditText editText = new EditText(this);
        editText.setText(str);
        editText.setHint(R.string.overlay_manual_flowers_hint);
        editText.setContentDescription(getString(R.string.overlay_manual_flowers_label));
        editText.setTextSize(16.0f);
        editText.setTextColor(Color.rgb(32, 48, 38));
        editText.setHintTextColor(OVERLAY_MUTED);
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        editText.setSingleLine(false);
        editText.setMinLines(4);
        editText.setGravity(8388659);
        return editText;
    }

    private TextView formLabel(int i) {
        TextView textViewFormText = formText(getString(i), 15, Color.rgb(30, 65, 35));
        textViewFormText.setTypeface(null, 1);
        textViewFormText.setPadding(0, dp(12), 0, dp(4));
        return textViewFormText;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public TextView formText(String str, int i, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(10.0f);
        textView.setTextColor(i2);
        textView.setLineSpacing(0.0f, 1.18f);
        return textView;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        layoutParams.setMarginStart(dp(6));
        return layoutParams;
    }

    private LinearLayout.LayoutParams matchWidthParams(int i, int i2) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, i);
        layoutParams.topMargin = i2;
        return layoutParams;
    }

    private void stopWithError(String str) {
        AutomationMode automationMode = this.automationMode;
        pause(str);
        setRunStatus(automationMode, OverlayRunStatus.Kind.ERROR, str, getString(R.string.overlay_error_detail));
    }

    private void finishWithSuccess(String str) {
        AutomationMode automationMode = this.automationMode;
        pause(str);
        setRunStatus(automationMode, OverlayRunStatus.Kind.SUCCESS, str, "");
        final OverlayRunStatus overlayRunStatus = this.plantingNoticeStatus;
        this.handler.postDelayed(new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda29
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$finishWithSuccess$54(overlayRunStatus);
            }
        }, 2600L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$finishWithSuccess$54(OverlayRunStatus overlayRunStatus) {
        if (this.running || this.plantingNoticeStatus != overlayRunStatus) {
            return;
        }
        clearPlantingNotice();
    }

    private void pause(String str) {
        SettingsStore settingsStore;
        this.runGeneration++;
        this.running = false;
        this.automationMode = AutomationMode.NONE;
        this.expeditionDispatchSession = null;
        this.dispatchColorSelected = false;
        this.dispatchPikminSelected = false;
        this.dispatchSearchOpened = false;
        this.dispatchSearchInputAttempts = 0;
        this.dispatchKeyboardCloseAttempts = 0;
        this.dispatchKeyboardAbsentFrames = 0;
        this.dispatchPikminTapIndex = 0;
        this.dispatchUnknownFrames = 0;
        this.returnRewardScanGuard.reset();
        this.returnRewardStartedAt = 0L;
        this.returnRewardLastTapAt = 0L;
        resetReturnRewardPostcard();
        this.switchGuard.reset();
        resetPlantingSearch();
        resetPostcardPotConfirmation();
        resetPostcardPetalSearch();
        this.postcardUnknownFrames = 0;
        this.postcardMissingControlFrames = 0;
        this.postcardReceiptWaitFrames = 0;
        this.postcardBackAttempts = 0;
        this.automationStep = AutomationStep.MONITORING;
        this.targetFlower = "";
        this.actionAttempts = 0;
        this.startMissingConfirmations = 0;
        this.startAfterSelection = false;
        this.selectionFromSearch = false;
        this.targetSelectionX = 0;
        this.targetSelectionY = 0;
        this.handler.removeCallbacks(this.scanTask);
        clearPlantingNotice();
        if (this.overlay != null && this.settingsOverlay == null && (settingsStore = this.settings) != null && settingsStore.overlayVisible()) {
            this.overlay.setVisibility(0);
        }
        Button button = this.toggle;
        if (button != null) {
            button.setText(R.string.action_start);
            this.toggle.setContentDescription(getString(R.string.action_start));
        }
        View view = this.overlay;
        if (view != null) {
            view.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_icon_description), getString(R.string.overlay_icon_move_hint)}));
        }
        setStatus(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setStatus(String str) {
        TextView textView = this.status;
        if (textView != null) {
            textView.setText(str);
            this.status.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{getString(R.string.overlay_status_label), str}));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Button overlayButton(String str) {
        Button button = new Button(this);
        button.setText(str);
        button.setTextSize(14.0f);
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

    private void stylePrimaryButton(Button button) {
        button.setTextColor(-1);
        button.setTextSize(15.0f);
        button.setTypeface(null, 1);
        button.setBackground(roundedBackground(OVERLAY_GREEN, 0, 12));
    }

    private void setSelectedTab(Button button, Button... buttonArr) {
        button.setSelected(true);
        button.setStateDescription(getString(R.string.overlay_tab_selected));
        button.setTextColor(OVERLAY_GREEN);
        button.setBackground(roundedBackground(-1, OVERLAY_BORDER, 11));
        for (Button button2 : buttonArr) {
            button2.setSelected(false);
            button2.setStateDescription(getString(R.string.overlay_tab_not_selected));
            button2.setTextColor(OVERLAY_MUTED);
            button2.setBackground(roundedBackground(0, 0, 11));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public GradientDrawable roundedBackground(int i, int i2, int i3) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(i3));
        if (i2 != 0) {
            gradientDrawable.setStroke(dp(1), i2);
        }
        return gradientDrawable;
    }

    private void showFloatingNotice(String str) {
        View view;
        if (str == null || str.trim().isEmpty() || this.windowManager == null || (view = this.overlay) == null || view.getVisibility() != 0) {
            return;
        }
        hideFloatingNotice();
        TextView textViewFormText = formText(str, 12, -1);
        textViewFormText.setMaxLines(2);
        textViewFormText.setEllipsize(TextUtils.TruncateAt.END);
        textViewFormText.setGravity(16);
        textViewFormText.setPadding(dp(10), dp(6), dp(10), dp(6));
        textViewFormText.setBackground(roundedBackground(OVERLAY_GREEN, 0, 10));
        textViewFormText.setAccessibilityLiveRegion(1);
        textViewFormText.setContentDescription(str);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, 2032, 280, -3);
        layoutParams.gravity = 8388659;
        layoutParams.x = Math.min(Math.max(dp(8), getResources().getDisplayMetrics().widthPixels - dp(230)), this.overlayParams.x + dp(44));
        layoutParams.y = Math.max(dp(16), this.overlayParams.y);
        if (safeAddOverlayView(textViewFormText, layoutParams, "notice")) {
            this.noticeOverlay = textViewFormText;
            this.noticeParams = layoutParams;
            textViewFormText.announceForAccessibility(str);
            this.handler.postDelayed(new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda36
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.hideFloatingNotice();
                }
            }, 2600L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideFloatingNotice() {
        safeRemoveOverlayView(this.noticeOverlay, "notice");
        this.noticeOverlay = null;
        this.noticeParams = null;
    }

    private void showPlantingStatus(String str, int i) {
        setRunStatus(AutomationMode.PLANTING, OverlayRunStatus.Kind.SUCCESS, getString(R.string.status_current_remaining, new Object[]{str, Integer.valueOf(i)}), "");
    }

    private void setPlantingNoticeText(String str, boolean z) {
        setRunStatus(this.automationMode, z ? OverlayRunStatus.Kind.ERROR : OverlayRunStatus.Kind.RECOGNIZING, str, z ? getString(R.string.overlay_error_detail) : "");
    }

    private void setRunStatus(AutomationMode automationMode, OverlayRunStatus.Kind kind, String str, String str2) {
        int i;
        String strTrim = str == null ? "" : str.trim();
        if (strTrim.isEmpty()) {
            clearPlantingNotice();
            return;
        }
        int i2 = AnonymousClass8.$SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode[automationMode.ordinal()];
        boolean z = true;
        if (i2 == 1) {
            i = R.string.overlay_stage_reward;
        } else if (i2 == 2) {
            i = R.string.overlay_stage_return_reward;
        } else if (i2 == 3) {
            i = R.string.overlay_stage_postcard;
        } else {
            i = R.string.overlay_stage_planting;
        }
        OverlayRunStatus overlayRunStatus = new OverlayRunStatus(kind, getString(i) + " · " + runStageLabel(kind), strTrim, str2);
        OverlayRunStatus overlayRunStatus2 = this.plantingNoticeStatus;
        if (overlayRunStatus2 != null && overlayRunStatus2.accessibilityText().equals(overlayRunStatus.accessibilityText())) {
            z = false;
        }
        this.plantingNoticeStatus = overlayRunStatus;
        renderOverlayStatus(z);
    }

    private String runStageLabel(OverlayRunStatus.Kind kind) {
        int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[kind.ordinal()];
        if (i == 1) {
            return getString(R.string.overlay_stage_waiting);
        }
        if (i == 2) {
            return getString(R.string.overlay_stage_searching);
        }
        if (i == 3) {
            return getString(R.string.overlay_stage_recognizing);
        }
        if (i == 4) {
            return getString(R.string.overlay_stage_success);
        }
        if (i != 5) {
            throw new IncompatibleClassChangeError();
        }
        return getString(R.string.overlay_stage_error);
    }

    private void renderOverlayStatus(boolean z) {
        int iRunStatusTone;
        String strAccessibilityText;
        int i;
        if (this.overlay == null) {
            return;
        }
        OverlayRunStatus overlayRunStatus = this.plantingNoticeStatus;
        if (overlayRunStatus == null) {
            iRunStatusTone = OVERLAY_GREEN;
        } else {
            iRunStatusTone = runStatusTone(overlayRunStatus.kind());
        }
        this.overlay.setBackground(roundedBackground(OVERLAY_SURFACE, iRunStatusTone, 10));
        OverlayRunStatus overlayRunStatus2 = this.plantingNoticeStatus;
        if (overlayRunStatus2 == null) {
            if (this.running) {
                i = R.string.overlay_stop_description;
            } else {
                i = R.string.overlay_icon_description;
            }
            strAccessibilityText = getString(i);
        } else {
            strAccessibilityText = overlayRunStatus2.accessibilityText();
        }
        this.overlay.setContentDescription(getString(R.string.overlay_status_accessibility, new Object[]{strAccessibilityText, getString(R.string.overlay_icon_move_hint)}));
        if (z && this.overlay.isAttachedToWindow()) {
            this.overlay.announceForAccessibility(strAccessibilityText);
        }
    }

    private int runStatusTone(OverlayRunStatus.Kind kind) {
        int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[kind.ordinal()];
        if (i == 1) {
            return OVERLAY_MUTED;
        }
        if (i == 2) {
            return OVERLAY_SEARCH;
        }
        if (i == 3) {
            return OVERLAY_RECOGNIZING;
        }
        if (i == 4) {
            return OVERLAY_GREEN;
        }
        if (i != 5) {
            throw new IncompatibleClassChangeError();
        }
        return OVERLAY_WARNING;
    }

    private void clearPlantingNotice() {
        this.plantingNoticeStatus = null;
        renderOverlayStatus(false);
    }

    private boolean safeAddOverlayView(View view, WindowManager.LayoutParams layoutParams, String str) {
        try {
            this.windowManager.addView(view, layoutParams);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to add " + str + " overlay", e);
            return false;
        }
    }

    private void safeRemoveOverlayView(View view, String str) {
        if (view == null || this.windowManager == null || !view.isAttachedToWindow()) {
            return;
        }
        try {
            this.windowManager.removeViewImmediate(view);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to remove " + str + " overlay", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class ReturnPostcardActionSelector extends LinearLayout {
        private final Button discard;
        private final Button receive;
        private boolean receiveSelected;

        ReturnPostcardActionSelector(boolean z) {
            super(PetalAccessibilityService.this);
            setGravity(16);
            this.receiveSelected = z;
            Button buttonOptionButton = PetalAccessibilityService.this.optionButton(R.string.overlay_return_reward_receive, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$ReturnPostcardActionSelector$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$0();
                }
            });
            this.receive = buttonOptionButton;
            Button buttonOptionButton2 = PetalAccessibilityService.this.optionButton(R.string.overlay_return_reward_discard, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$ReturnPostcardActionSelector$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$1();
                }
            });
            this.discard = buttonOptionButton2;
            addView(buttonOptionButton, PetalAccessibilityService.this.dispatchOptionParams(false));
            addView(buttonOptionButton2, PetalAccessibilityService.this.dispatchOptionParams(true));
            refresh();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0() {
            select(true);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$1() {
            select(false);
        }

        boolean receive() {
            return this.receiveSelected;
        }

        private void select(boolean z) {
            this.receiveSelected = z;
            refresh();
        }

        private void refresh() {
            PetalAccessibilityService.this.styleDispatchOption(this.receive, this.receiveSelected);
            PetalAccessibilityService.this.styleDispatchOption(this.discard, !this.receiveSelected);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class DispatchTargetSelector extends LinearLayout {
        private final Button both;
        private final Button fruit;
        private final Button pot;
        private ExpeditionTargetMode selected;

        DispatchTargetSelector(ExpeditionTargetMode expeditionTargetMode) {
            super(PetalAccessibilityService.this);
            setGravity(16);
            this.selected = expeditionTargetMode == null ? ExpeditionTargetMode.FRUIT_AND_POT : expeditionTargetMode;
            Button buttonOptionButton = PetalAccessibilityService.this.optionButton(R.string.overlay_reward_target_fruit, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchTargetSelector$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$0();
                }
            });
            this.fruit = buttonOptionButton;
            Button buttonOptionButton2 = PetalAccessibilityService.this.optionButton(R.string.overlay_reward_target_pot, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchTargetSelector$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$1();
                }
            });
            this.pot = buttonOptionButton2;
            Button buttonOptionButton3 = PetalAccessibilityService.this.optionButton(R.string.overlay_reward_target_both, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchTargetSelector$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$2();
                }
            });
            this.both = buttonOptionButton3;
            addView(buttonOptionButton, PetalAccessibilityService.this.dispatchOptionParams(false));
            addView(buttonOptionButton2, PetalAccessibilityService.this.dispatchOptionParams(true));
            addView(buttonOptionButton3, PetalAccessibilityService.this.dispatchOptionParams(true));
            refresh();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0() {
            select(ExpeditionTargetMode.FRUIT);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$1() {
            select(ExpeditionTargetMode.POT);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$2() {
            select(ExpeditionTargetMode.FRUIT_AND_POT);
        }

        ExpeditionTargetMode value() {
            return this.selected;
        }

        private void select(ExpeditionTargetMode expeditionTargetMode) {
            this.selected = expeditionTargetMode;
            refresh();
        }

        private void refresh() {
            PetalAccessibilityService.this.styleDispatchOption(this.fruit, this.selected == ExpeditionTargetMode.FRUIT);
            PetalAccessibilityService.this.styleDispatchOption(this.pot, this.selected == ExpeditionTargetMode.POT);
            PetalAccessibilityService.this.styleDispatchOption(this.both, this.selected == ExpeditionTargetMode.FRUIT_AND_POT);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class DispatchMethodSelector extends LinearLayout {
        private final Button automatic;
        private final Button drag;
        private DispatchSelectionMethod selected;

        DispatchMethodSelector(DispatchSelectionMethod dispatchSelectionMethod) {
            super(PetalAccessibilityService.this);
            setGravity(16);
            this.selected = dispatchSelectionMethod == null ? DispatchSelectionMethod.AUTO : dispatchSelectionMethod;
            Button buttonOptionButton = PetalAccessibilityService.this.optionButton(R.string.overlay_reward_method_auto, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchMethodSelector$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$0();
                }
            });
            this.automatic = buttonOptionButton;
            Button buttonOptionButton2 = PetalAccessibilityService.this.optionButton(R.string.overlay_reward_method_drag, new Runnable() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchMethodSelector$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$new$1();
                }
            });
            this.drag = buttonOptionButton2;
            addView(buttonOptionButton, PetalAccessibilityService.this.dispatchOptionParams(false));
            addView(buttonOptionButton2, PetalAccessibilityService.this.dispatchOptionParams(true));
            refresh();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0() {
            select(DispatchSelectionMethod.AUTO);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$1() {
            select(DispatchSelectionMethod.DRAG_12);
        }

        DispatchSelectionMethod value() {
            return this.selected;
        }

        private void select(DispatchSelectionMethod dispatchSelectionMethod) {
            this.selected = dispatchSelectionMethod;
            refresh();
        }

        private void refresh() {
            PetalAccessibilityService.this.styleDispatchOption(this.automatic, this.selected == DispatchSelectionMethod.AUTO);
            PetalAccessibilityService.this.styleDispatchOption(this.drag, this.selected == DispatchSelectionMethod.DRAG_12);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class DispatchPikminTypeSelector extends LinearLayout {
        private final Map<DispatchPikminType, Button> buttonMap;
        private DispatchPikminType selected;

        DispatchPikminTypeSelector(DispatchPikminType dispatchPikminType) {
            int i;
            super(PetalAccessibilityService.this);
            this.buttonMap = new HashMap();
            setOrientation(1);
            setGravity(17);
            this.selected = dispatchPikminType == null ? DispatchPikminType.MIXED : dispatchPikminType;
            DispatchPikminType[] dispatchPikminTypeArrValues = DispatchPikminType.values();
            int i2 = 0;
            while (i2 < dispatchPikminTypeArrValues.length) {
                LinearLayout linearLayout = new LinearLayout(PetalAccessibilityService.this);
                linearLayout.setOrientation(0);
                linearLayout.setGravity(17);
                linearLayout.setPadding(0, PetalAccessibilityService.this.dp(2), 0, PetalAccessibilityService.this.dp(2));
                int i3 = i2;
                while (true) {
                    i = i2 + 3;
                    if (i3 >= i || i3 >= dispatchPikminTypeArrValues.length) {
                        break;
                    }
                    final DispatchPikminType dispatchPikminType2 = dispatchPikminTypeArrValues[i3];
                    String strLabel = dispatchPikminType2.label();
                    final Button buttonOverlayButton = PetalAccessibilityService.this.overlayButton(strLabel);
                    buttonOverlayButton.setContentDescription(strLabel);
                    buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$DispatchPikminTypeSelector$$ExternalSyntheticLambda0
                        @Override // android.view.View.OnClickListener
                        public final void onClick(View view) {
                            this.f$0.lambda$new$0(dispatchPikminType2, buttonOverlayButton, view);
                        }
                    });
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, PetalAccessibilityService.this.dp(40), 1.0f);
                    layoutParams.setMarginStart(i3 % 3 == 0 ? 0 : PetalAccessibilityService.this.dp(6));
                    linearLayout.addView(buttonOverlayButton, layoutParams);
                    this.buttonMap.put(dispatchPikminType2, buttonOverlayButton);
                    i3++;
                }
                addView(linearLayout);
                i2 = i;
            }
            refresh();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(DispatchPikminType dispatchPikminType, Button button, View view) {
            select(dispatchPikminType);
            button.announceForAccessibility(button.getText());
        }

        DispatchPikminType value() {
            return this.selected;
        }

        private void select(DispatchPikminType dispatchPikminType) {
            this.selected = dispatchPikminType;
            refresh();
        }

        private void refresh() {
            for (Map.Entry<DispatchPikminType, Button> entry : this.buttonMap.entrySet()) {
                PetalAccessibilityService.this.styleDispatchOption(entry.getValue(), entry.getKey() == this.selected);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Button optionButton(int i, final Runnable runnable) {
        final Button buttonOverlayButton = overlayButton(getString(i));
        buttonOverlayButton.setContentDescription(getString(i));
        buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$$ExternalSyntheticLambda34
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                PetalAccessibilityService.lambda$optionButton$55(runnable, buttonOverlayButton, view);
            }
        });
        return buttonOverlayButton;
    }

    static /* synthetic */ void lambda$optionButton$55(Runnable runnable, Button button, View view) {
        runnable.run();
        button.announceForAccessibility(button.getText());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public LinearLayout.LayoutParams dispatchOptionParams(boolean z) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(50), 1.0f);
        if (z) {
            layoutParams.setMarginStart(dp(6));
        }
        return layoutParams;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void styleDispatchOption(Button button, boolean z) {
        int i;
        button.setSelected(z);
        if (z) {
            i = R.string.overlay_tab_selected;
        } else {
            i = R.string.overlay_tab_not_selected;
        }
        button.setStateDescription(getString(i));
        button.setTextColor(z ? -1 : OVERLAY_GREEN);
        button.setBackground(roundedBackground(z ? OVERLAY_GREEN : Color.rgb(241, 245, 239), z ? 0 : OVERLAY_BORDER, 11));
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class StepperField extends LinearLayout {
        private final EditText input;
        private final String label;
        private final int maximum;
        private final int minimum;

        StepperField(String str, String str2, int i, int i2, int i3) {
            super(PetalAccessibilityService.this);
            this.label = str;
            this.minimum = i2;
            this.maximum = i3;
            setGravity(16);
            setPadding(PetalAccessibilityService.this.dp(14), PetalAccessibilityService.this.dp(8), PetalAccessibilityService.this.dp(10), PetalAccessibilityService.this.dp(8));
            setBackground(PetalAccessibilityService.this.roundedBackground(-1, PetalAccessibilityService.OVERLAY_BORDER, 14));
            LinearLayout linearLayout = new LinearLayout(PetalAccessibilityService.this);
            linearLayout.setOrientation(1);
            TextView textViewFormText = PetalAccessibilityService.this.formText(str, 14, Color.rgb(35, 75, 54));
            textViewFormText.setTypeface(null, 1);
            TextView textViewFormText2 = PetalAccessibilityService.this.formText(str2, 11, PetalAccessibilityService.OVERLAY_MUTED);
            linearLayout.addView(textViewFormText);
            linearLayout.addView(textViewFormText2);
            addView(linearLayout, new LinearLayout.LayoutParams(0, -2, 1.0f));
            Button buttonCompactIconButton = PetalAccessibilityService.this.compactIconButton("−", PetalAccessibilityService.this.getString(R.string.overlay_decrease));
            buttonCompactIconButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$StepperField$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$new$0(view);
                }
            });
            addView(buttonCompactIconButton, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(48), PetalAccessibilityService.this.dp(48)));
            EditText editTextNumberField = PetalAccessibilityService.this.numberField(i);
            this.input = editTextNumberField;
            editTextNumberField.setGravity(17);
            editTextNumberField.setPadding(PetalAccessibilityService.this.dp(2), 0, PetalAccessibilityService.this.dp(2), 0);
            editTextNumberField.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_value_description, new Object[]{str, Integer.valueOf(i)}));
            editTextNumberField.setBackgroundColor(0);
            addView(editTextNumberField, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(58), PetalAccessibilityService.this.dp(48)));
            Button buttonCompactIconButton2 = PetalAccessibilityService.this.compactIconButton("+", PetalAccessibilityService.this.getString(R.string.overlay_increase));
            buttonCompactIconButton2.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$StepperField$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$new$1(view);
                }
            });
            addView(buttonCompactIconButton2, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(48), PetalAccessibilityService.this.dp(48)));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(View view) {
            adjust(-1);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$1(View view) {
            adjust(1);
        }

        String valueText() {
            return this.input.getText().toString();
        }

        EditText input() {
            return this.input;
        }

        private void adjust(int i) {
            int i2;
            try {
                i2 = Integer.parseInt(this.input.getText().toString().trim());
            } catch (NumberFormatException unused) {
                i2 = this.minimum;
            }
            int iMax = Math.max(this.minimum, Math.min(this.maximum, i2 + i));
            this.input.setText(String.valueOf(iMax));
            EditText editText = this.input;
            editText.setSelection(editText.length());
            this.input.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_value_description, new Object[]{this.label, Integer.valueOf(iMax)}));
            this.input.announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_value_description, new Object[]{this.label, Integer.valueOf(iMax)}));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class NumberChoiceSelector extends LinearLayout {
        private final List<Button> choices;
        private final String label;
        private int selectedValue;

        NumberChoiceSelector(String str, int i, int i2, int i3) {
            super(PetalAccessibilityService.this);
            this.choices = new ArrayList();
            this.label = str;
            this.selectedValue = Math.max(i2, Math.min(i3, i));
            setGravity(16);
            for (final int i4 = i2; i4 <= i3; i4++) {
                Button buttonOverlayButton = PetalAccessibilityService.this.overlayButton(String.valueOf(i4));
                buttonOverlayButton.setContentDescription(str + " " + i4);
                buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$NumberChoiceSelector$$ExternalSyntheticLambda0
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        this.f$0.lambda$new$0(i4, view);
                    }
                });
                this.choices.add(buttonOverlayButton);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, PetalAccessibilityService.this.dp(48), 1.0f);
                if (i4 > i2) {
                    layoutParams.setMarginStart(PetalAccessibilityService.this.dp(4));
                }
                addView(buttonOverlayButton, layoutParams);
            }
            refreshChoiceStyles();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(int i, View view) {
            select(i, true);
        }

        String valueText() {
            return String.valueOf(this.selectedValue);
        }

        private void select(int i, boolean z) {
            this.selectedValue = i;
            refreshChoiceStyles();
            if (z) {
                announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_value_description, new Object[]{this.label, Integer.valueOf(this.selectedValue)}));
            }
        }

        private void refreshChoiceStyles() {
            int i;
            int i2 = 0;
            while (i2 < this.choices.size()) {
                Button button = this.choices.get(i2);
                i2++;
                boolean z = i2 == this.selectedValue;
                button.setSelected(z);
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                if (z) {
                    i = R.string.overlay_tab_selected;
                } else {
                    i = R.string.overlay_tab_not_selected;
                }
                button.setStateDescription(petalAccessibilityService.getString(i));
                button.setTextColor(z ? -1 : PetalAccessibilityService.OVERLAY_GREEN);
                button.setBackground(PetalAccessibilityService.this.roundedBackground(z ? PetalAccessibilityService.OVERLAY_GREEN : Color.rgb(241, 245, 239), z ? 0 : PetalAccessibilityService.OVERLAY_BORDER, 11));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class PostcardPotSelector extends LinearLayout {
        private final List<Button> colorButtons;
        private String preferredName;
        private PostcardPotCatalog.Color selectedColor;
        private final Spinner spinner;

        PostcardPotSelector(String str) {
            super(PetalAccessibilityService.this);
            this.colorButtons = new ArrayList();
            setOrientation(1);
            String strCanonicalName = PostcardPotCatalog.canonicalName(str);
            this.preferredName = strCanonicalName;
            PostcardPotCatalog.Color colorColorOf = PostcardPotCatalog.colorOf(strCanonicalName);
            this.selectedColor = colorColorOf;
            if (colorColorOf == null) {
                this.selectedColor = PostcardPotCatalog.Color.WHITE;
            }
            LinearLayout linearLayout = new LinearLayout(PetalAccessibilityService.this);
            linearLayout.setGravity(16);
            PostcardPotCatalog.Color[] colorArrValues = PostcardPotCatalog.Color.values();
            for (int i = 0; i < colorArrValues.length; i++) {
                final PostcardPotCatalog.Color color = colorArrValues[i];
                Button buttonOverlayButton = PetalAccessibilityService.this.overlayButton(color.label());
                buttonOverlayButton.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_postcard_petal_color_description, new Object[]{color.label()}));
                buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$PostcardPotSelector$$ExternalSyntheticLambda0
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        this.f$0.lambda$new$0(color, view);
                    }
                });
                this.colorButtons.add(buttonOverlayButton);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, PetalAccessibilityService.this.dp(46), 1.0f);
                if (i > 0) {
                    layoutParams.setMarginStart(PetalAccessibilityService.this.dp(4));
                }
                linearLayout.addView(buttonOverlayButton, layoutParams);
            }
            addView(linearLayout);
            Spinner spinner = new Spinner(PetalAccessibilityService.this);
            this.spinner = spinner;
            spinner.setMinimumHeight(PetalAccessibilityService.this.dp(48));
            spinner.setPadding(PetalAccessibilityService.this.dp(8), 0, PetalAccessibilityService.this.dp(8), 0);
            spinner.setBackground(PetalAccessibilityService.this.roundedBackground(Color.rgb(250, 252, 249), PetalAccessibilityService.OVERLAY_BORDER, 10));
            spinner.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_postcard_petal_pot_label));
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, PetalAccessibilityService.this.dp(50));
            layoutParams2.topMargin = PetalAccessibilityService.this.dp(8);
            addView(spinner, layoutParams2);
            refresh();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(PostcardPotCatalog.Color color, View view) {
            selectColor(color);
        }

        String value() {
            Object selectedItem = this.spinner.getSelectedItem();
            String strCanonicalName = selectedItem == null ? null : PostcardPotCatalog.canonicalName(selectedItem.toString());
            if (strCanonicalName != null) {
                return strCanonicalName;
            }
            throw new IllegalArgumentException(PetalAccessibilityService.this.getString(R.string.overlay_postcard_no_saved_pots_for_color));
        }

        private void selectColor(PostcardPotCatalog.Color color) {
            this.selectedColor = color;
            this.preferredName = "";
            refresh();
        }

        private void refresh() {
            List<String> listNamesForColor = PostcardPotCatalog.namesForColor(this.selectedColor);
            ArrayAdapter arrayAdapter = new ArrayAdapter(PetalAccessibilityService.this, android.R.layout.simple_spinner_item, listNamesForColor.isEmpty() ? List.of(PetalAccessibilityService.this.getString(R.string.overlay_postcard_no_saved_pots_for_color)) : listNamesForColor);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            this.spinner.setAdapter((SpinnerAdapter) arrayAdapter);
            this.spinner.setEnabled(!listNamesForColor.isEmpty());
            int iIndexOf = listNamesForColor.indexOf(this.preferredName);
            if (iIndexOf >= 0) {
                this.spinner.setSelection(iIndexOf);
            }
            refreshColorStyles();
        }

        private void refreshColorStyles() {
            int i;
            PostcardPotCatalog.Color[] colorArrValues = PostcardPotCatalog.Color.values();
            for (int i2 = 0; i2 < this.colorButtons.size(); i2++) {
                Button button = this.colorButtons.get(i2);
                PostcardPotCatalog.Color color = colorArrValues[i2];
                boolean z = color == this.selectedColor;
                button.setSelected(z);
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                if (z) {
                    i = R.string.overlay_tab_selected;
                } else {
                    i = R.string.overlay_tab_not_selected;
                }
                button.setStateDescription(petalAccessibilityService.getString(i));
                button.setTextColor(z ? -1 : Color.rgb(45, 74, 56));
                button.setBackground(PetalAccessibilityService.this.roundedBackground(z ? PetalAccessibilityService.OVERLAY_GREEN : potColorSurface(color), z ? 0 : PetalAccessibilityService.OVERLAY_BORDER, 11));
            }
        }

        private int potColorSurface(PostcardPotCatalog.Color color) {
            int i = AnonymousClass8.$SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color[color.ordinal()];
            if (i == 1) {
                return -1;
            }
            if (i == 2) {
                return Color.rgb(255, 249, 219);
            }
            if (i == 3) {
                return Color.rgb(255, 237, 237);
            }
            if (i != 4) {
                throw new IncompatibleClassChangeError();
            }
            return Color.rgb(235, 244, 255);
        }
    }

    /* JADX INFO: renamed from: com.pikminx.helper.PetalAccessibilityService$8, reason: invalid class name */
    static /* synthetic */ class AnonymousClass8 {
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage;
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind;
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode;
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$PostcardAutomation$Step;
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$PostcardMatcher$Page;
        static final /* synthetic */ int[] $SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color;

        static {
            int[] iArr = new int[PostcardPotCatalog.Color.values().length];
            $SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color = iArr;
            try {
                iArr[PostcardPotCatalog.Color.WHITE.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color[PostcardPotCatalog.Color.YELLOW.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color[PostcardPotCatalog.Color.RED.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardPotCatalog$Color[PostcardPotCatalog.Color.BLUE.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            int[] iArr2 = new int[OverlayRunStatus.Kind.values().length];
            $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind = iArr2;
            try {
                iArr2[OverlayRunStatus.Kind.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[OverlayRunStatus.Kind.SEARCHING.ordinal()] = 2;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[OverlayRunStatus.Kind.RECOGNIZING.ordinal()] = 3;
            } catch (NoSuchFieldError unused7) {
            }
            try {
                $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[OverlayRunStatus.Kind.SUCCESS.ordinal()] = 4;
            } catch (NoSuchFieldError unused8) {
            }
            try {
                $SwitchMap$com$pikminx$helper$OverlayRunStatus$Kind[OverlayRunStatus.Kind.ERROR.ordinal()] = 5;
            } catch (NoSuchFieldError unused9) {
            }
            int[] iArr3 = new int[AutomationMode.values().length];
            $SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode = iArr3;
            try {
                iArr3[AutomationMode.DISPATCH.ordinal()] = 1;
            } catch (NoSuchFieldError unused10) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode[AutomationMode.RETURN_REWARD.ordinal()] = 2;
            } catch (NoSuchFieldError unused11) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PetalAccessibilityService$AutomationMode[AutomationMode.POSTCARD.ordinal()] = 3;
            } catch (NoSuchFieldError unused12) {
            }
            int[] iArr4 = new int[PostcardMatcher.Page.values().length];
            $SwitchMap$com$pikminx$helper$PostcardMatcher$Page = iArr4;
            try {
                iArr4[PostcardMatcher.Page.POSTCARD_RECEIVED.ordinal()] = 1;
            } catch (NoSuchFieldError unused13) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardMatcher$Page[PostcardMatcher.Page.PIKMIN_SELECTION.ordinal()] = 2;
            } catch (NoSuchFieldError unused14) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardMatcher$Page[PostcardMatcher.Page.PETAL_SELECTION.ordinal()] = 3;
            } catch (NoSuchFieldError unused15) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardMatcher$Page[PostcardMatcher.Page.WARNING.ordinal()] = 4;
            } catch (NoSuchFieldError unused16) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardMatcher$Page[PostcardMatcher.Page.FLOWER_DETAIL.ordinal()] = 5;
            } catch (NoSuchFieldError unused17) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardMatcher$Page[PostcardMatcher.Page.MAP.ordinal()] = 6;
            } catch (NoSuchFieldError unused18) {
            }
            int[] iArr5 = new int[ExpeditionDispatchSession.Stage.values().length];
            $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage = iArr5;
            try {
                iArr5[ExpeditionDispatchSession.Stage.LIST_SEARCH.ordinal()] = 1;
            } catch (NoSuchFieldError unused19) {
            }
            try {
                $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[ExpeditionDispatchSession.Stage.DETAIL.ordinal()] = 2;
            } catch (NoSuchFieldError unused20) {
            }
            try {
                $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[ExpeditionDispatchSession.Stage.SELECTION.ordinal()] = 3;
            } catch (NoSuchFieldError unused21) {
            }
            try {
                $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[ExpeditionDispatchSession.Stage.WAIT_RESULT.ordinal()] = 4;
            } catch (NoSuchFieldError unused22) {
            }
            try {
                $SwitchMap$com$pikminx$helper$ExpeditionDispatchSession$Stage[ExpeditionDispatchSession.Stage.VERIFY_RETURN.ordinal()] = 5;
            } catch (NoSuchFieldError unused23) {
            }
            int[] iArr6 = new int[PostcardAutomation.Step.values().length];
            $SwitchMap$com$pikminx$helper$PostcardAutomation$Step = iArr6;
            try {
                iArr6[PostcardAutomation.Step.OPEN_PETAL_SEARCH.ordinal()] = 1;
            } catch (NoSuchFieldError unused24) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.ENTER_PETAL_SEARCH.ordinal()] = 2;
            } catch (NoSuchFieldError unused25) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.CLOSE_PETAL_KEYBOARD.ordinal()] = 3;
            } catch (NoSuchFieldError unused26) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.SELECT_PETAL.ordinal()] = 4;
            } catch (NoSuchFieldError unused27) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.TAP_NEXT.ordinal()] = 5;
            } catch (NoSuchFieldError unused28) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.GO.ordinal()] = 6;
            } catch (NoSuchFieldError unused29) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.RECEIVE.ordinal()] = 7;
            } catch (NoSuchFieldError unused30) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.WAIT_RECEIPT_EXIT.ordinal()] = 8;
            } catch (NoSuchFieldError unused31) {
            }
            try {
                $SwitchMap$com$pikminx$helper$PostcardAutomation$Step[PostcardAutomation.Step.FIND_FLOWER.ordinal()] = 9;
            } catch (NoSuchFieldError unused32) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class FlowerOrderEditor extends LinearLayout {
        private final Button addButton;
        private List<String> availableFlowers;
        private final List<Button> colorButtons;
        private final Spinner flowerSpinner;
        private int selectedCategoryIndex;
        private final LinearLayout selectedRows;
        private final PetalSelection selection;

        FlowerOrderEditor(List<String> list) {
            super(PetalAccessibilityService.this);
            this.colorButtons = new ArrayList();
            this.availableFlowers = List.of();
            setOrientation(1);
            this.selection = new PetalSelection(list);
            LinearLayout linearLayout = new LinearLayout(PetalAccessibilityService.this);
            this.selectedRows = linearLayout;
            linearLayout.setOrientation(1);
            addView(linearLayout);
            LinearLayout linearLayout2 = new LinearLayout(PetalAccessibilityService.this);
            linearLayout2.setGravity(16);
            List<PetalCatalog.Category> listCategories = PetalCatalog.categories();
            for (final int i = 0; i < listCategories.size(); i++) {
                PetalCatalog.Category category = listCategories.get(i);
                Button buttonOverlayButton = PetalAccessibilityService.this.overlayButton(category.name());
                buttonOverlayButton.setTextSize(11.0f);
                buttonOverlayButton.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_flower_color_description, new Object[]{category.name()}));
                buttonOverlayButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$FlowerOrderEditor$$ExternalSyntheticLambda0
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        this.f$0.lambda$new$0(i, view);
                    }
                });
                this.colorButtons.add(buttonOverlayButton);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, PetalAccessibilityService.this.dp(44), 1.0f);
                if (i > 0) {
                    layoutParams.setMarginStart(PetalAccessibilityService.this.dp(4));
                }
                linearLayout2.addView(buttonOverlayButton, layoutParams);
            }
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams2.topMargin = PetalAccessibilityService.this.dp(10);
            addView(linearLayout2, layoutParams2);
            Spinner spinner = new Spinner(PetalAccessibilityService.this);
            this.flowerSpinner = spinner;
            spinner.setMinimumHeight(PetalAccessibilityService.this.dp(48));
            spinner.setPadding(PetalAccessibilityService.this.dp(12), 0, PetalAccessibilityService.this.dp(8), 0);
            spinner.setBackground(PetalAccessibilityService.this.roundedBackground(-1, PetalAccessibilityService.OVERLAY_BORDER, 12));
            spinner.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_flower_dropdown));
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, PetalAccessibilityService.this.dp(52));
            layoutParams3.topMargin = PetalAccessibilityService.this.dp(6);
            addView(spinner, layoutParams3);
            Button buttonOverlayButton2 = PetalAccessibilityService.this.overlayButton("+  " + PetalAccessibilityService.this.getString(R.string.overlay_add_flower));
            this.addButton = buttonOverlayButton2;
            buttonOverlayButton2.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_add_flower));
            buttonOverlayButton2.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$FlowerOrderEditor$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$new$1(view);
                }
            });
            LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, PetalAccessibilityService.this.dp(48));
            layoutParams4.topMargin = PetalAccessibilityService.this.dp(6);
            addView(buttonOverlayButton2, layoutParams4);
            refreshRows();
            selectCategory(this.selection.size() == 0 ? 0 : Math.max(0, PetalCatalog.categoryIndexOf(this.selection.get(0))), false);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(int i, View view) {
            selectCategory(i, true);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$1(View view) {
            addSelectedFlower();
        }

        String valueText() {
            return this.selection.text();
        }

        private void selectCategory(int i, boolean z) {
            this.selectedCategoryIndex = i;
            refreshColorStyles();
            refreshDropdown();
            if (z) {
                announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_flower_color_description, new Object[]{PetalCatalog.categories().get(i).name()}));
            }
        }

        private void refreshColorStyles() {
            int i;
            int i2 = 0;
            while (i2 < this.colorButtons.size()) {
                Button button = this.colorButtons.get(i2);
                boolean z = i2 == this.selectedCategoryIndex;
                button.setSelected(z);
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                if (z) {
                    i = R.string.overlay_tab_selected;
                } else {
                    i = R.string.overlay_tab_not_selected;
                }
                button.setStateDescription(petalAccessibilityService.getString(i));
                button.setTextColor(z ? -1 : PetalAccessibilityService.OVERLAY_GREEN);
                button.setBackground(PetalAccessibilityService.this.roundedBackground(z ? PetalAccessibilityService.OVERLAY_GREEN : Color.rgb(241, 245, 239), z ? 0 : PetalAccessibilityService.OVERLAY_BORDER, 10));
                i2++;
            }
        }

        private void refreshDropdown() {
            List<String> listOf;
            List<String> listAvailable = this.selection.available(this.selectedCategoryIndex);
            this.availableFlowers = listAvailable;
            if (listAvailable.isEmpty()) {
                listOf = List.of(PetalAccessibilityService.this.getString(R.string.overlay_no_available_flowers));
            } else {
                listOf = this.availableFlowers;
            }
            ArrayAdapter arrayAdapter = new ArrayAdapter(PetalAccessibilityService.this, android.R.layout.simple_spinner_item, listOf);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            this.flowerSpinner.setAdapter((SpinnerAdapter) arrayAdapter);
            this.flowerSpinner.setEnabled(!this.availableFlowers.isEmpty());
            this.addButton.setEnabled(!this.availableFlowers.isEmpty());
            this.addButton.setAlpha(this.availableFlowers.isEmpty() ? 0.45f : 1.0f);
        }

        private void addSelectedFlower() {
            int selectedItemPosition = this.flowerSpinner.getSelectedItemPosition();
            if (selectedItemPosition < 0 || selectedItemPosition >= this.availableFlowers.size()) {
                return;
            }
            String str = this.availableFlowers.get(selectedItemPosition);
            if (this.selection.add(str)) {
                refreshRows();
                refreshDropdown();
                announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_flower_added_description, new Object[]{str}));
            }
        }

        private void refreshRows() {
            this.selectedRows.removeAllViews();
            for (int i = 0; i < this.selection.size(); i++) {
                addSelectedRow(i);
            }
            if (this.selection.size() == 0) {
                PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
                this.selectedRows.addView(petalAccessibilityService.settingsHelperCard(petalAccessibilityService.getString(R.string.overlay_no_selected_flowers), PetalAccessibilityService.OVERLAY_CREAM));
            }
        }

        private void addSelectedRow(final int i) {
            LinearLayout linearLayout = new LinearLayout(PetalAccessibilityService.this);
            linearLayout.setGravity(16);
            linearLayout.setPadding(PetalAccessibilityService.this.dp(6), PetalAccessibilityService.this.dp(4), PetalAccessibilityService.this.dp(4), PetalAccessibilityService.this.dp(4));
            linearLayout.setBackground(PetalAccessibilityService.this.roundedBackground(-1, PetalAccessibilityService.OVERLAY_BORDER, 12));
            int i2 = i + 1;
            TextView textViewFormText = PetalAccessibilityService.this.formText(String.valueOf(i2), 13, PetalAccessibilityService.OVERLAY_MUTED);
            textViewFormText.setGravity(17);
            textViewFormText.setImportantForAccessibility(2);
            linearLayout.addView(textViewFormText, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(28), PetalAccessibilityService.this.dp(48)));
            TextView textViewFormText2 = PetalAccessibilityService.this.formText(this.selection.get(i), 14, Color.rgb(35, 75, 54));
            textViewFormText2.setContentDescription(PetalAccessibilityService.this.getString(R.string.overlay_flower_item_description, new Object[]{Integer.valueOf(i2)}));
            linearLayout.addView(textViewFormText2, new LinearLayout.LayoutParams(0, PetalAccessibilityService.this.dp(48), 1.0f));
            PetalAccessibilityService petalAccessibilityService = PetalAccessibilityService.this;
            Button buttonCompactIconButton = petalAccessibilityService.compactIconButton("↑", petalAccessibilityService.getString(R.string.overlay_move_up));
            buttonCompactIconButton.setEnabled(i > 0);
            buttonCompactIconButton.setAlpha(i > 0 ? 1.0f : 0.35f);
            buttonCompactIconButton.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$FlowerOrderEditor$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$addSelectedRow$2(i, view);
                }
            });
            linearLayout.addView(buttonCompactIconButton, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(48), PetalAccessibilityService.this.dp(48)));
            PetalAccessibilityService petalAccessibilityService2 = PetalAccessibilityService.this;
            Button buttonCompactIconButton2 = petalAccessibilityService2.compactIconButton("↓", petalAccessibilityService2.getString(R.string.overlay_move_down));
            buttonCompactIconButton2.setEnabled(i < this.selection.size() - 1);
            buttonCompactIconButton2.setAlpha(i >= this.selection.size() - 1 ? 0.35f : 1.0f);
            buttonCompactIconButton2.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$FlowerOrderEditor$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$addSelectedRow$3(i, view);
                }
            });
            linearLayout.addView(buttonCompactIconButton2, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(48), PetalAccessibilityService.this.dp(48)));
            PetalAccessibilityService petalAccessibilityService3 = PetalAccessibilityService.this;
            Button buttonCompactIconButton3 = petalAccessibilityService3.compactIconButton("×", petalAccessibilityService3.getString(R.string.overlay_remove_flower));
            buttonCompactIconButton3.setOnClickListener(new View.OnClickListener() { // from class: com.pikminx.helper.PetalAccessibilityService$FlowerOrderEditor$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.lambda$addSelectedRow$4(i, view);
                }
            });
            linearLayout.addView(buttonCompactIconButton3, new LinearLayout.LayoutParams(PetalAccessibilityService.this.dp(48), PetalAccessibilityService.this.dp(48)));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) {
                layoutParams.topMargin = PetalAccessibilityService.this.dp(6);
            }
            this.selectedRows.addView(linearLayout, layoutParams);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$addSelectedRow$2(int i, View view) {
            move(i, -1);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$addSelectedRow$3(int i, View view) {
            move(i, 1);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$addSelectedRow$4(int i, View view) {
            remove(i);
        }

        private void move(int i, int i2) {
            int i3 = i + i2;
            if (i3 < 0 || i3 >= this.selection.size()) {
                return;
            }
            this.selection.move(i, i2);
            refreshRows();
            announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_flower_item_description, new Object[]{Integer.valueOf(i3 + 1)}));
        }

        private void remove(int i) {
            String str = this.selection.get(i);
            this.selection.remove(i);
            refreshRows();
            refreshDropdown();
            announceForAccessibility(PetalAccessibilityService.this.getString(R.string.overlay_flower_removed_description, new Object[]{str}));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Button compactIconButton(String str, String str2) {
        Button buttonOverlayButton = overlayButton(str);
        buttonOverlayButton.setTextSize(18.0f);
        buttonOverlayButton.setContentDescription(str2);
        buttonOverlayButton.setPadding(0, 0, 0, 0);
        return buttonOverlayButton;
    }

    private final class DragListener implements View.OnTouchListener {
        private boolean moved;
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;

        private DragListener() {
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() == 0) {
                this.startX = PetalAccessibilityService.this.overlayParams.x;
                this.startY = PetalAccessibilityService.this.overlayParams.y;
                this.touchX = motionEvent.getRawX();
                this.touchY = motionEvent.getRawY();
                this.moved = false;
                return true;
            }
            if (motionEvent.getAction() == 2) {
                this.moved = this.moved || Math.abs(motionEvent.getRawX() - this.touchX) > ((float) PetalAccessibilityService.this.dp(6)) || Math.abs(motionEvent.getRawY() - this.touchY) > ((float) PetalAccessibilityService.this.dp(6));
                PetalAccessibilityService.this.overlayParams.x = this.startX + Math.round(motionEvent.getRawX() - this.touchX);
                PetalAccessibilityService.this.overlayParams.y = this.startY + Math.round(motionEvent.getRawY() - this.touchY);
                if (PetalAccessibilityService.this.overlay != null && PetalAccessibilityService.this.overlay.isAttachedToWindow()) {
                    try {
                        PetalAccessibilityService.this.windowManager.updateViewLayout(PetalAccessibilityService.this.overlay, PetalAccessibilityService.this.overlayParams);
                    } catch (RuntimeException e) {
                        Log.w(PetalAccessibilityService.TAG, "Unable to move icon overlay", e);
                    }
                }
                return true;
            }
            if (motionEvent.getAction() != 1) {
                return false;
            }
            if (!this.moved) {
                view.performClick();
            }
            return true;
        }
    }

    private final class DraggableIcon extends ImageButton {
        DraggableIcon() {
            super(PetalAccessibilityService.this);
        }

        @Override // android.view.View
        public boolean performClick() {
            return super.performClick();
        }
    }
}
