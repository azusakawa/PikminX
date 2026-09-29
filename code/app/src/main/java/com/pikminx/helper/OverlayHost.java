package com.pikminx.helper;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import com.pikminx.helper.platform.settings.SettingsStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Presentation owner for the accessibility overlay.
 *
 * <p>The nested state is deliberately projection-only: workflow truth stays with the service
 * and is rendered here without creating implicit workflow transitions.</p>
 */
final class OverlayHost extends ContextWrapper {
    static final int OVERLAY_SIZE_DP = 50;

    private static final String TAG = "PikminX";
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
    private static final int OVERLAY_RECOGNIZING = Color.rgb(167, 120, 33);
    private static final boolean MUSHROOM_DISABLED = true;

    interface WorkflowCallbacks {
        void startPlanting();

        void scheduleFeedStart(FeedSettingsInput input);

        void startPostcard(int collectionLimit, String petalPotName, int pikminCount);

        void scheduleDispatchStart(
                ExpeditionTargetMode targetMode,
                DispatchSelectionMethod selectionMethod,
                DispatchPikminType pikminType);

        void scheduleReturnRewardStart(boolean receivePostcards, boolean continueOnNectarWarning);

        boolean isAnyWorkflowActive();

        void requestGlobalStop(String reason);

    }

    interface DiagnosticContext {
        String describe();
    }

    private final Handler handler;
    private final SettingsStore settings;
    private final WorkflowCallbacks workflowCallbacks;
    private final BooleanSupplier overlayEnabled;
    private final DiagnosticContext diagnosticContext;
    private final PresentationState presentationState = PresentationState.initial();
    private final Runnable hideFloatingNoticeTask = this::hideFloatingNotice;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlay;
    private View settingsOverlay;
    private TextView status;
    private Button toggle;
    private View noticeOverlay;
    private WindowManager.LayoutParams noticeParams;
    private OverlayRunStatus runStatus;
    private CaptureViewState captureViewState;

    private record CaptureViewState(
            CaptureToken token,
            View icon,
            View settings,
            View notice,
            int iconVisibility,
            int settingsVisibility,
            int noticeVisibility) {}

    private record OverlayTraceState(
            boolean panelAttached,
            boolean panelExpanded,
            boolean captureMasked,
            boolean hiddenForAutomation) {}

    OverlayHost(
            Context context,
            Handler handler,
            SettingsStore settings,
            WorkflowCallbacks workflowCallbacks,
            BooleanSupplier overlayEnabled,
            DiagnosticContext diagnosticContext) {
        super(Objects.requireNonNull(context, "context"));
        this.handler = Objects.requireNonNull(handler, "handler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.workflowCallbacks = Objects.requireNonNull(workflowCallbacks, "workflowCallbacks");
        this.overlayEnabled = Objects.requireNonNull(overlayEnabled, "overlayEnabled");
        this.diagnosticContext = Objects.requireNonNull(diagnosticContext, "diagnosticContext");
    }

    enum FeatureTab {
        PLANTING,
        FEED,
        POSTCARD,
        DISPATCH,
        RETURN_REWARD,
        MUSHROOM
    }

    enum Workflow {
        NONE,
        PLANTING,
        FEED,
        POSTCARD,
        DISPATCH,
        RETURN_REWARD,
        MUSHROOM_DISABLED
    }

    static final class WorkflowProjection {
        private final Workflow workflow;
        private final boolean enabled;
        private final boolean running;
        private final boolean patrolPaused;

        WorkflowProjection(Workflow workflow, boolean enabled, boolean running, boolean patrolPaused) {
            this.workflow = workflow == null ? Workflow.NONE : workflow;
            this.enabled = enabled;
            this.running = running;
            this.patrolPaused = patrolPaused;
        }

        Workflow workflow() {
            return workflow;
        }

        boolean enabled() {
            return enabled;
        }

        boolean running() {
            return running;
        }

        boolean patrolPaused() {
            return patrolPaused;
        }

    }

    static final class CaptureToken {
        private final long id;

        private CaptureToken(long id) {
            this.id = id;
        }
    }

    boolean attach() {
        OverlayTraceState before = overlayTraceState();
        if (!overlayEnabled.getAsBoolean()) {
            logOverlayTrace("attach", "overlay-disabled", before);
            return false;
        }
        if (overlay != null) {
            logOverlayTrace("attach", "icon-already-present", before);
            return true;
        }
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            logOverlayTrace("attach", "window-manager-unavailable", before);
            return false;
        }
        DraggableIcon icon = new DraggableIcon();
        icon.setImageResource(R.drawable.ic_overlay_flower);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        icon.setFocusable(true);
        icon.setElevation(0);
        icon.setPadding(0, 0, 0, 0);
        icon.setBackground(null);
        icon.setOnClickListener(view -> {
            if (workflowCallbacks.isAnyWorkflowActive()) {
                workflowCallbacks.requestGlobalStop("floating-icon-tap");
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
        icon.setOnTouchListener(new DragListener());
        if (!safeAddOverlayView(icon, params, "icon")) {
            logOverlayTrace("attach", "icon-add-failed", before);
            return false;
        }
        overlay = icon;
        overlayParams = params;
        renderOverlayStatus(false);
        logOverlayTrace("attach", "icon-added", before);
        return true;
    }

    boolean isIconVisible() {
        return overlay != null && overlay.getVisibility() == View.VISIBLE;
    }

    boolean isTouchInsideIcon(float rawX, float rawY) {
        if (overlay == null || overlay.getVisibility() != View.VISIBLE) {
            return false;
        }
        Rect bounds = new Rect();
        return overlay.getGlobalVisibleRect(bounds) && bounds.contains(
                Math.round(rawX), Math.round(rawY));
    }

    boolean isAttached() {
        return overlay != null;
    }

    void setOverlayVisible(boolean visible) {
        OverlayTraceState before = overlayTraceState();
        if (!visible) {
            closeSettingsOverlay(false);
            hideFloatingNotice();
        }
        setIconVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            presentationState.collapsePanel();
        } else {
            presentationState.hideIcon();
        }
        settings.setOverlayVisible(visible);
        logOverlayTrace("set-overlay-visible", Boolean.toString(visible), before);
    }

    void showSettingsOverlay() {
        OverlayTraceState before = overlayTraceState();
        boolean enabled = overlayEnabled.getAsBoolean();
        if (!enabled || presentationState.captureMasked()) {
            logOverlayTrace("show-settings", enabled ? "capture-masked" : "overlay-disabled", before);
            return;
        }
        if (settingsOverlay != null) {
            logOverlayTrace("show-settings", "panel-already-present", before);
            return;
        }
        if (overlay == null || windowManager == null) {
            logOverlayTrace("show-settings", "icon-or-window-manager-unavailable", before);
            return;
        }
        View panel = buildSettingsOverlay();
        if (panel == null) {
            logOverlayTrace("show-settings", "panel-build-failed", before);
            return;
        }
        WindowManager.LayoutParams params = settingsOverlayLayoutParams();
        if (!safeAddOverlayView(panel, params, "settings")) {
            logOverlayTrace("show-settings", "panel-add-failed", before);
            return;
        }
        settingsOverlay = panel;
        if (overlay != null) {
            setIconVisibility(View.GONE);
        }
        presentationState.expandPanel();
        logOverlayTrace("show-settings", "panel-added", before);
    }

    void closeSettingsOverlay(boolean saved) {
        OverlayTraceState before = overlayTraceState();
        View closing = settingsOverlay;
        if (closing == null) {
            logOverlayTrace("close-settings", "panel-absent", before);
            return;
        }
        settingsOverlay = null;
        status = null;
        toggle = null;
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(closing.getWindowToken(), 0);
        safeRemoveOverlayView(closing, "settings");
        if (overlay != null) {
            setIconVisibility(settings.overlayVisible() ? View.VISIBLE : View.GONE);
            overlay.requestFocus();
        }
        presentationState.collapsePanel();
        if (!settings.overlayVisible()) {
            presentationState.hideIcon();
        }
        if (workflowCallbacks.isAnyWorkflowActive() && runStatus != null) {
            showFloatingNotice(runStatus.visibleText(), false);
        } else if (saved) {
            showFloatingNotice(getString(R.string.overlay_saved));
        }
        logOverlayTrace("close-settings", saved ? "saved" : "unsaved", before);
    }

    void detach() {
        OverlayTraceState before = overlayTraceState();
        restoreCaptureMaskAfterAbort();
        hideFloatingNotice();
        safeRemoveOverlayView(settingsOverlay, "settings");
        safeRemoveOverlayView(overlay, "icon");
        settingsOverlay = null;
        overlay = null;
        overlayParams = null;
        status = null;
        toggle = null;
        noticeOverlay = null;
        noticeParams = null;
        captureViewState = null;
        presentationState.destroy();
        logOverlayTrace("detach", "service-teardown", before);
    }

    void setPanelStatus(String message) {
        if (status == null || message == null) {
            return;
        }
        status.setText(message);
        status.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                getString(R.string.overlay_status_label),
                message));
    }

    void renderPrimaryAction() {
        if (toggle != null) {
            toggle.setText(R.string.action_start);
            toggle.setContentDescription(getString(R.string.action_start));
        }
        renderOverlayStatus(false);
    }

    void renderWorkflow(WorkflowProjection projection) {
        presentationState.renderWorkflow(projection);
    }

    void showIconForActiveWorkflow() {
        if (!presentationState.captureMasked()
                && overlay != null
                && settingsOverlay == null
                && settings.overlayVisible()) {
            setIconVisibility(View.VISIBLE);
        }
        renderOverlayStatus(false);
    }

    void setRunStatus(OverlayRunStatus next) {
        boolean changed = runStatus == null
                || next == null
                || !runStatus.accessibilityText().equals(next.accessibilityText());
        runStatus = next;
        presentationState.renderStatus(next);
        renderOverlayStatus(changed);
        if (changed && next != null) {
            showFloatingNotice(next.visibleText(), false);
        }
    }

    OverlayRunStatus runStatus() {
        return runStatus;
    }

    void clearRunStatus() {
        runStatus = null;
        presentationState.renderStatus(null);
        hideFloatingNotice();
        renderOverlayStatus(false);
    }

    void showNotice(String message) {
        showFloatingNotice(message);
    }
    Object prepareCaptureMask() {
        OverlayTraceState before = overlayTraceState();
        CaptureToken token = presentationState.prepareCaptureMask();
        if (token == null) {
            logOverlayTrace("prepare-capture-mask", "token-unavailable", before);
            return null;
        }
        if (captureViewState == null) {
            int iconVisibility = visibilityOf(overlay);
            int settingsVisibility = visibilityOf(settingsOverlay);
            int noticeVisibility = visibilityOf(noticeOverlay);
            captureViewState = new CaptureViewState(
                    token, overlay, settingsOverlay, noticeOverlay,
                    iconVisibility, settingsVisibility, noticeVisibility);
            setCaptureVisibility(overlay, View.INVISIBLE);
            setCaptureVisibility(settingsOverlay, View.INVISIBLE);
            setCaptureVisibility(noticeOverlay, View.INVISIBLE);
        } else {
            captureViewState = new CaptureViewState(
                    token,
                    captureViewState.icon(),
                    captureViewState.settings(),
                    captureViewState.notice(),
                    captureViewState.iconVisibility(),
                    captureViewState.settingsVisibility(),
                    captureViewState.noticeVisibility());
        }
        logOverlayTrace("prepare-capture-mask", "token=" + token.id, before);
        return token;
    }

    void restoreCaptureMask(Object value) {
        OverlayTraceState before = overlayTraceState();
        if (!(value instanceof CaptureToken token)
                || !presentationState.restoreCaptureMask(token)) {
            return;
        }
        CaptureViewState saved = captureViewState;
        captureViewState = null;
        if (saved == null || saved.token() != token) {
            return;
        }
        restoreCaptureVisibility(saved.icon(), saved.iconVisibility());
        restoreCaptureVisibility(saved.settings(), saved.settingsVisibility());
        restoreCaptureVisibility(saved.notice(), saved.noticeVisibility());
        logOverlayTrace("restore-capture-mask", "token=" + token.id, before);
    }

    void restoreCaptureMaskAfterAbort() {
        CaptureToken token = presentationState.activeCaptureToken();
        if (token != null) {
            restoreCaptureMask(token);
        }
    }

    void addVisibleRegions(List<ScreenshotOverlayMask.Region> regions) {
        addVisibleOverlayRegion(regions, overlay);
        addVisibleOverlayRegion(regions, noticeOverlay);
    }

    private View buildSettingsOverlay() {
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
        Button mushroomTab = overlayButton(getString(R.string.overlay_tab_mushroom));
        plantingTab.setTextSize(11);
        feedTab.setTextSize(11);
        postcardTab.setTextSize(11);
        rewardTab.setTextSize(11);
        returnRewardTab.setTextSize(11);
        mushroomTab.setTextSize(11);
        plantingTab.setContentDescription(getString(R.string.overlay_tab_planting_description));
        feedTab.setContentDescription(getString(R.string.overlay_tab_feed_description));
        postcardTab.setContentDescription(getString(R.string.overlay_tab_postcard_description));
        rewardTab.setContentDescription(getString(R.string.overlay_tab_reward_description));
        returnRewardTab.setContentDescription(
                getString(R.string.overlay_tab_return_reward_description));
        mushroomTab.setContentDescription(
                getString(R.string.overlay_tab_mushroom_description));
        if (MUSHROOM_DISABLED) {
            mushroomTab.setEnabled(false);
            mushroomTab.setAlpha(0.55f);
        }
        LinearLayout automationTabs = new LinearLayout(this);
        automationTabs.addView(plantingTab, weightedButtonParams());
        automationTabs.addView(feedTab, weightedButtonParams());
        automationTabs.addView(postcardTab, weightedButtonParams());
        tabs.addView(automationTabs, matchWidthParams(dp(48), 0));
        LinearLayout utilityTabs = new LinearLayout(this);
        utilityTabs.addView(rewardTab, weightedButtonParams());
        utilityTabs.addView(returnRewardTab, weightedButtonParams());
        utilityTabs.addView(mushroomTab, weightedButtonParams());
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
                workflowCallbacks.startPlanting();
                if (workflowCallbacks.isAnyWorkflowActive()) {
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
                workflowCallbacks.scheduleFeedStart(input);
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
                workflowCallbacks.startPostcard(
                        input.collectionLimit(), input.petalPotName(), input.pikminCount());
                if (workflowCallbacks.isAnyWorkflowActive()) {
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
        rewardContent.addView(settingsHelperCard(
                getString(R.string.overlay_reward_full_sweep), OVERLAY_SURFACE_2));
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
        Runnable saveReward = () -> settings.saveExpeditionDispatchSettings(
                rewardTargetSelector.value(), rewardMethodSelector.value(), rewardPikminSelector.value());
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
                ExpeditionTargetMode targetMode = rewardTargetSelector.value();
                DispatchSelectionMethod method = rewardMethodSelector.value();
                DispatchPikminType type = rewardPikminSelector.value();
                closeSettingsOverlay(false);
                workflowCallbacks.scheduleDispatchStart(targetMode, method, type);
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
            workflowCallbacks.scheduleReturnRewardStart(receive, continueOnNectarWarning);
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
            workflowCallbacks.scheduleReturnRewardStart(receive, continueOnNectarWarning);
        });
        returnRewardPage.addView(settingsFooter(returnRewardSave, returnRewardStart));
        returnRewardPage.setVisibility(View.GONE);

        LinearLayout mushroomPage = new LinearLayout(this);
        mushroomPage.setOrientation(LinearLayout.VERTICAL);
        mushroomPage.setFocusableInTouchMode(true);
        TextView mushroomDisabled = formText(
                getString(R.string.status_mushroom_temporarily_disabled),
                14,
                OVERLAY_MUTED);
        mushroomDisabled.setPadding(dp(16), dp(24), dp(16), dp(24));
        mushroomPage.addView(mushroomDisabled, matchWidthParams(0, 0));
        mushroomPage.setVisibility(View.GONE);

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
        panel.addView(mushroomPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        plantingTab.setOnClickListener(view -> selectPage(
                FeatureTab.PLANTING,
                plantingPage,
                feedPage,
                postcardPage,
                rewardPage,
                returnRewardPage,
                mushroomPage,
                plantingTab,
                new Button[] {feedTab, postcardTab, rewardTab, returnRewardTab, mushroomTab},
                statusView,
                toggleButton,
                title,
                subtitle,
                readyChip,
                R.string.overlay_page_planting_title,
                R.string.overlay_page_planting_subtitle,
                R.string.overlay_canvas_ready,
                false));
        feedTab.setOnClickListener(view -> selectPage(
                FeatureTab.FEED,
                feedPage,
                plantingPage,
                postcardPage,
                rewardPage,
                returnRewardPage,
                mushroomPage,
                feedTab,
                new Button[] {plantingTab, postcardTab, rewardTab, returnRewardTab, mushroomTab},
                feedStatusView,
                feedStart,
                title,
                subtitle,
                readyChip,
                R.string.overlay_page_feed_title,
                R.string.overlay_page_feed_subtitle,
                R.string.overlay_canvas_action,
                true));
        postcardTab.setOnClickListener(view -> selectPage(
                FeatureTab.POSTCARD,
                postcardPage,
                plantingPage,
                feedPage,
                rewardPage,
                returnRewardPage,
                mushroomPage,
                postcardTab,
                new Button[] {plantingTab, feedTab, rewardTab, returnRewardTab, mushroomTab},
                null,
                postcardToggle,
                title,
                subtitle,
                readyChip,
                R.string.overlay_page_postcard_title,
                R.string.overlay_page_postcard_subtitle,
                R.string.overlay_canvas_idle,
                false));
        rewardTab.setOnClickListener(view -> selectPage(
                FeatureTab.DISPATCH,
                rewardPage,
                plantingPage,
                feedPage,
                postcardPage,
                returnRewardPage,
                mushroomPage,
                rewardTab,
                new Button[] {plantingTab, feedTab, postcardTab, returnRewardTab, mushroomTab},
                rewardStatusView,
                rewardStart,
                title,
                subtitle,
                readyChip,
                R.string.overlay_page_dispatch_title,
                R.string.overlay_page_dispatch_subtitle,
                R.string.overlay_canvas_action,
                true));
        returnRewardTab.setOnClickListener(view -> selectPage(
                FeatureTab.RETURN_REWARD,
                returnRewardPage,
                plantingPage,
                feedPage,
                postcardPage,
                rewardPage,
                mushroomPage,
                returnRewardTab,
                new Button[] {plantingTab, feedTab, postcardTab, rewardTab, mushroomTab},
                returnRewardStatusView,
                returnRewardStart,
                title,
                subtitle,
                readyChip,
                R.string.overlay_page_return_title,
                R.string.overlay_page_return_subtitle,
                R.string.overlay_canvas_no_area,
                true));
        mushroomTab.setOnClickListener(view -> {
            // Mushroom identity remains visible, but this tab is deliberately disabled.
        });
        setSelectedTab(plantingTab, feedTab, postcardTab, rewardTab, returnRewardTab, mushroomTab);
        status = statusView;
        toggle = toggleButton;
        title.setFocusable(true);
        title.requestFocus();
        return panel;
    }

    private void selectPage(
            FeatureTab tab,
            View activePage,
            View firstInactive,
            View secondInactive,
            View thirdInactive,
            View fourthInactive,
            View fifthInactive,
            Button activeButton,
            Button[] inactiveButtons,
            TextView activeStatus,
            Button activeToggle,
            TextView title,
            TextView subtitle,
            TextView readyChip,
            int titleResource,
            int subtitleResource,
            int chipResource,
            boolean warning) {
        if (MUSHROOM_DISABLED && tab == FeatureTab.MUSHROOM) {
            return;
        }
        activePage.setVisibility(View.VISIBLE);
        firstInactive.setVisibility(View.GONE);
        secondInactive.setVisibility(View.GONE);
        thirdInactive.setVisibility(View.GONE);
        fourthInactive.setVisibility(View.GONE);
        fifthInactive.setVisibility(View.GONE);
        setSelectedTab(activeButton, inactiveButtons);
        status = activeStatus;
        toggle = activeToggle;
        presentationState.selectFeatureTab(tab);
        setSettingsHeader(title, subtitle, readyChip,
                titleResource, subtitleResource, chipResource, warning);
        activePage.requestFocus();
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
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
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
        footer.post(() -> expandPrimaryTouchTarget(footer, primary));
        return footer;
    }

    private void expandPrimaryTouchTarget(LinearLayout footer, Button primary) {
        if (footer.getTouchDelegate() != null || !primary.isShown()) {
            return;
        }
        Rect hitBounds = new Rect();
        primary.getHitRect(hitBounds);
        int padding = dp(8);
        hitBounds.inset(-padding, -padding);
        footer.setTouchDelegate(new TouchDelegate(hitBounds, primary));
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

    private TextView formLabel(int stringResource) {
        TextView label = formText(getString(stringResource), 15, Color.rgb(30, 65, 35));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
    }

    private TextView formText(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

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

    private final class ReturnPostcardActionSelector extends LinearLayout {
        private final Button receive;
        private final Button discard;
        private boolean receiveSelected;

        ReturnPostcardActionSelector(boolean initialReceive) {
            super(OverlayHost.this);
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

    private final class DispatchTargetSelector extends LinearLayout {
        private final Button fruit;
        private final Button pot;
        private final Button both;
        private ExpeditionTargetMode selected;

        DispatchTargetSelector(ExpeditionTargetMode initial) {
            super(OverlayHost.this);
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

    private final class DispatchMethodSelector extends LinearLayout {
        private final Button automatic;
        private final Button drag;
        private DispatchSelectionMethod selected;

        DispatchMethodSelector(DispatchSelectionMethod initial) {
            super(OverlayHost.this);
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

    private final class DispatchPikminTypeSelector extends LinearLayout {
        private final Map<DispatchPikminType, Button> buttonMap = new HashMap<>();
        private DispatchPikminType selected;

        DispatchPikminTypeSelector(DispatchPikminType initial) {
            super(OverlayHost.this);
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER);
            selected = initial == null ? DispatchPikminType.MIXED : initial;
            DispatchPikminType[] types = DispatchPikminType.values();
            for (int i = 0; i < types.length; i += 3) {
                LinearLayout row = new LinearLayout(OverlayHost.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, dp(2), 0, dp(2));
                for (int j = i; j < i + 3 && j < types.length; j++) {
                    DispatchPikminType type = types[j];
                    String label = type.label();
                    Button button = overlayButton(label);
                    button.setContentDescription(label);
                    button.setOnClickListener(view -> {
                        select(type);
                        button.announceForAccessibility(button.getText());
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
                    params.setMarginStart(j % 3 == 0 ? 0 : dp(6));
                    row.addView(button, params);
                    buttonMap.put(type, button);
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
            super(OverlayHost.this);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(10), dp(10));
            setBackground(roundedBackground(Color.WHITE, OVERLAY_BORDER, 14));
            LinearLayout copy = new LinearLayout(OverlayHost.this);
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

    private final class NumberChoiceSelector extends LinearLayout {
        private final List<Button> choices = new ArrayList<>();
        private final String label;
        private int selectedValue;

        NumberChoiceSelector(String label, int initialValue, int minimum, int maximum) {
            super(OverlayHost.this);
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

    private final class PostcardPotSelector extends LinearLayout {
        private final List<Button> colorButtons = new ArrayList<>();
        private final Spinner spinner;
        private PostcardPotCatalog.Color selectedColor;
        private String preferredName;

        PostcardPotSelector(String selectedName) {
            super(OverlayHost.this);
            setOrientation(VERTICAL);
            preferredName = PostcardPotCatalog.canonicalName(selectedName);
            selectedColor = PostcardPotCatalog.colorOf(preferredName);
            if (selectedColor == null) {
                selectedColor = PostcardPotCatalog.Color.WHITE;
            }
            LinearLayout colors = new LinearLayout(OverlayHost.this);
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
            spinner = new Spinner(OverlayHost.this);
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
                    OverlayHost.this,
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
            super(OverlayHost.this);
            setOrientation(VERTICAL);
            selection = new PetalSelection(flowers);
            this.nectarLabels = nectarLabels;
            selectedRows = new LinearLayout(OverlayHost.this);
            selectedRows.setOrientation(VERTICAL);
            addView(selectedRows);
            LinearLayout colorRow = new LinearLayout(OverlayHost.this);
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
            flowerSpinner = new Spinner(OverlayHost.this);
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

        String valueText() {
            return selection.text();
        }

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
                    OverlayHost.this,
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
            LinearLayout row = new LinearLayout(OverlayHost.this);
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

    private void stylePrimaryButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackground(roundedBackground(OVERLAY_GREEN, 0, 12));
    }

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

    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private void showFloatingNotice(String message) {
        showFloatingNotice(message, true);
    }

    private void showFloatingNotice(String message, boolean autoHide) {
        if (message == null
                || message.trim().isEmpty()
                || presentationState.captureMasked()
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

    private void positionFloatingNotice(WindowManager.LayoutParams params) {
        if (overlayParams == null) {
            return;
        }
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

    private void hideFloatingNotice() {
        handler.removeCallbacks(hideFloatingNoticeTask);
        safeRemoveOverlayView(noticeOverlay, "notice");
        noticeOverlay = null;
        noticeParams = null;
    }

    private void renderOverlayStatus(boolean announce) {
        if (overlay == null) {
            return;
        }
        overlay.setBackground(null);
        overlay.setElevation(0);
        String description = workflowCallbacks.isAnyWorkflowActive()
                ? getString(R.string.overlay_stop_description)
                : runStatus == null
                        ? getString(R.string.overlay_icon_description)
                        : runStatus.accessibilityText();
        overlay.setContentDescription(getString(
                R.string.overlay_status_accessibility,
                description,
                getString(R.string.overlay_icon_move_hint)));
        if (announce && overlay.isAttachedToWindow()) {
            overlay.announceForAccessibility(description);
        }
    }

    private void setIconVisibility(int visibility) {
        if (overlay != null
                && (visibility != View.VISIBLE || !presentationState.captureMasked())) {
            overlay.setVisibility(visibility);
        }
    }

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

    private OverlayTraceState overlayTraceState() {
        return new OverlayTraceState(
                settingsOverlay != null && settingsOverlay.isAttachedToWindow(),
                presentationState.panelExpanded(),
                presentationState.captureMasked(),
                false);
    }

    private void logOverlayTrace(String operation, String reason, OverlayTraceState before) {
        OverlayTraceState after = overlayTraceState();
        String runtimeContext;
        try {
            runtimeContext = diagnosticContext.describe();
        } catch (RuntimeException exception) {
            runtimeContext = "foregroundPackage=<unavailable> lifecycle=<unavailable>";
            Log.w(TAG, "Unable to collect overlay diagnostic context", exception);
        }
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String caller = stack.length > 3 ? stack[3].getMethodName() : "unknown";
        Log.i(TAG, "D2_OVERLAY_TRACE tsEpochMs=" + System.currentTimeMillis()
                + " op=" + operation
                + " reason=" + reason
                + " caller=" + caller
                + " panelAttachedBefore=" + before.panelAttached()
                + " panelAttachedAfter=" + after.panelAttached()
                + " expandedBefore=" + before.panelExpanded()
                + " expandedAfter=" + after.panelExpanded()
                + " captureMaskedBefore=" + before.captureMasked()
                + " captureMaskedAfter=" + after.captureMasked()
                + " automationHiddenBefore=" + before.hiddenForAutomation()
                + " automationHiddenAfter=" + after.hiddenForAutomation()
                + " " + runtimeContext);
    }

    private static int visibilityOf(View view) {
        return view == null ? View.GONE : view.getVisibility();
    }

    private static void setCaptureVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private static void restoreCaptureVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private static void addVisibleOverlayRegion(
            List<ScreenshotOverlayMask.Region> regions, View view) {
        if (regions == null || view == null
                || view.getVisibility() != View.VISIBLE || !view.isAttachedToWindow()) {
            return;
        }
        Rect bounds = new Rect();
        if (view.getGlobalVisibleRect(bounds) && !bounds.isEmpty()) {
            regions.add(new ScreenshotOverlayMask.Region(
                    bounds.left, bounds.top, bounds.right, bounds.bottom));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class DragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (overlayParams == null) {
                    return false;
                }
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

    private final class DraggableIcon extends ImageButton {
        DraggableIcon() {
            super(OverlayHost.this);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }



    static final class PresentationState {
        private boolean iconVisible = true;
        private boolean panelExpanded;
        private FeatureTab featureTab = FeatureTab.PLANTING;
        private WorkflowProjection workflow = new WorkflowProjection(
                Workflow.NONE, false, false, false);
        private OverlayRunStatus runStatus;
        private long nextCaptureToken;
        private CaptureToken activeCaptureToken;
        private CaptureSnapshot captureSnapshot;
        private boolean destroyed;

        private record CaptureSnapshot(boolean iconVisible, boolean panelExpanded) {}

        static PresentationState initial() {
            return new PresentationState();
        }

        void expandPanel() {
            if (destroyed || activeCaptureToken != null) {
                return;
            }
            panelExpanded = true;
            iconVisible = false;
        }

        void collapsePanel() {
            if (destroyed || activeCaptureToken != null) {
                return;
            }
            panelExpanded = false;
            iconVisible = true;
        }

        void hideIcon() {
            if (!destroyed && activeCaptureToken == null) {
                iconVisible = false;
            }
        }

        void selectFeatureTab(FeatureTab tab) {
            if (!destroyed && tab != null) {
                featureTab = tab;
            }
        }

        void renderWorkflow(WorkflowProjection projection) {
            if (!destroyed && projection != null) {
                workflow = projection;
            }
        }

        void renderStatus(OverlayRunStatus status) {
            if (!destroyed) {
                runStatus = status;
            }
        }

        CaptureToken prepareCaptureMask() {
            if (destroyed) {
                return null;
            }
            if (captureSnapshot == null) {
                captureSnapshot = new CaptureSnapshot(iconVisible, panelExpanded);
            }
            activeCaptureToken = new CaptureToken(++nextCaptureToken);
            iconVisible = false;
            panelExpanded = false;
            return activeCaptureToken;
        }

        boolean restoreCaptureMask(CaptureToken token) {
            if (destroyed || token == null || token != activeCaptureToken || captureSnapshot == null) {
                return false;
            }
            iconVisible = captureSnapshot.iconVisible();
            panelExpanded = captureSnapshot.panelExpanded();
            activeCaptureToken = null;
            captureSnapshot = null;
            return true;
        }

        CaptureToken activeCaptureToken() {
            return activeCaptureToken;
        }

        void destroy() {
            destroyed = true;
            iconVisible = false;
            panelExpanded = false;
            activeCaptureToken = null;
            captureSnapshot = null;
            runStatus = null;
        }

        boolean iconVisible() {
            return iconVisible;
        }

        boolean panelExpanded() {
            return panelExpanded;
        }

        FeatureTab featureTab() {
            return featureTab;
        }

        WorkflowProjection workflow() {
            return workflow;
        }

        OverlayRunStatus runStatus() {
            return runStatus;
        }

        boolean captureMasked() {
            return activeCaptureToken != null;
        }

        boolean destroyed() {
            return destroyed;
        }
    }
}
