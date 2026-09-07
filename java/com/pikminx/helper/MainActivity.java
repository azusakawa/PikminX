package com.pikminx.helper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 應用程式的設定入口。
 *
 * <p>這個 Activity 只負責設定資料與開啟系統權限頁面；實際的畫面擷取與點擊
 * 由 {@link PetalAccessibilityService} 執行，避免 UI 層和自動化流程互相耦合。</p>
 */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(243, 246, 242);
    private static final int SURFACE = Color.WHITE;
    private static final int SURFACE_SOFT = Color.rgb(246, 248, 246);
    private static final int PRIMARY = Color.rgb(35, 122, 80);
    private static final int PRIMARY_DARK = Color.rgb(24, 60, 49);
    private static final int TEXT = Color.rgb(24, 60, 49);
    private static final int MUTED = Color.rgb(99, 118, 111);
    private static final int BORDER = Color.rgb(223, 231, 225);
    private static final int BRAND_BACKGROUND = Color.rgb(231, 245, 236);
    private static final int WARNING_BACKGROUND = Color.rgb(255, 245, 217);
    private static final int WARNING_TEXT = Color.rgb(155, 106, 0);
    private static final String COMMUNITY_URL =
            "https://line.me/ti/g2/kBeFvQzEdGSJ3J48e9tkkEljK2wq0Mxb_FauOA?utm_source=invitation&utm_medium=link_copy&utm_campaign=default";
    private static final String SPONSOR_URL =
            "https://payment.opay.tw/Broadcaster/Donate/0CB6EDA6EAB8577A8D33F1E8E346BC2A";

    private TextView serviceStatus;
    private Button overlayToggle;
    private SettingsStore settings;

    /** 建立乾淨、可捲動且適合小螢幕的設定頁。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        getWindow().setStatusBarColor(PRIMARY_DARK);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(buildScreen());
    }

    /** 每次回到頁面時同步無障礙服務狀態與懸浮窗狀態。 */
    @Override
    protected void onResume() {
        super.onResume();
        if (serviceStatus != null) {
            boolean enabled = isServiceEnabled();
            serviceStatus.setText(enabled
                    ? (PetalAccessibilityService.isConnected()
                            ? R.string.main_service_enabled
                            : R.string.main_service_connecting)
                    : R.string.main_service_disabled);
            serviceStatus.setTextColor(enabled ? PRIMARY : MUTED);
            updateOverlayButton();
        }
    }

    /** 建立畫布中的單一底部導航；各頁只投影既有設定與入口。 */
    private View buildScreen() {
        LinearLayout root = verticalLayout(0, 0, 0, 0);
        root.setBackgroundColor(BACKGROUND);

        FrameLayout pages = new FrameLayout(this);
        View home = buildHomeScreen();
        View automations = buildAutomationScreen();
        View preferences = buildSettingsScreen();
        View[] screens = {home, automations, preferences};
        for (View screen : screens) {
            pages.addView(screen, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            screen.setVisibility(View.GONE);
        }
        home.setVisibility(View.VISIBLE);
        root.addView(pages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(8), dp(5), dp(8), dp(7));
        navigation.setBackgroundColor(SURFACE);
        Button[] tabs = {
                navigationButton(R.string.main_nav_home),
                navigationButton(R.string.main_nav_automations),
                navigationButton(R.string.main_nav_settings)
        };
        for (int index = 0; index < tabs.length; index++) {
            final int selected = index;
            tabs[index].setOnClickListener(view -> showScreen(selected, screens, tabs));
            navigation.addView(tabs[index], new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        styleNavigation(tabs, 0);
        root.addView(navigation, matchParams());
        return root;
    }

    private View buildHomeScreen() {
        ScrollView scroll = screenScroll();
        LinearLayout content = screenContent(
                getString(R.string.main_title), getString(R.string.main_home_subtitle));
        scroll.addView(content);

        boolean serviceReady = isServiceEnabled();
        LinearLayout hero = card(BRAND_BACKGROUND, 0);
        hero.addView(sectionTitle(serviceReady
                ? R.string.main_device_ready_title : R.string.main_device_action_title));
        hero.addView(sectionDescription(serviceReady
                ? R.string.main_device_ready_description : R.string.main_device_action_description));
        addSpace(hero, 14);
        Button openGame = primaryButton(R.string.main_open_game);
        openGame.setOnClickListener(view -> openGame());
        hero.addView(openGame, matchParams());
        content.addView(hero, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_available_title));
        addSpace(content, 9);
        LinearLayout features = verticalLayout(0, 0, 0, 0);
        LinearLayout firstFeatureRow = new LinearLayout(this);
        firstFeatureRow.addView(featureTile(
                R.string.main_feature_planting,
                getString(R.string.main_feature_planting_summary,
                        settings.allowedFlowers().size(), settings.threshold()),
                R.string.main_status_ready), tileParams(false));
        firstFeatureRow.addView(featureTile(
                R.string.main_feature_feed,
                getString(R.string.main_feature_feed_summary,
                        settings.feedsPerSquad(), settings.maxSquadSwitches()),
                R.string.main_status_action), tileParams(true));
        features.addView(firstFeatureRow, matchParams());
        features.addView(featureTile(
                R.string.main_feature_postcard,
                getString(R.string.main_feature_postcard_summary,
                        settings.postcardCollectionLimit()),
                R.string.main_status_idle), matchParams());
        content.addView(features, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_device_check_title));
        addSpace(content, 9);
        LinearLayout checks = card();
        checks.addView(simpleStatusRow(R.string.main_service_row_title, serviceReady
                ? R.string.main_status_normal : R.string.main_status_action));
        checks.addView(simpleStatusRow(R.string.main_overlay_row_title, serviceReady
                ? R.string.main_status_allowed : R.string.main_status_action));
        checks.addView(simpleStatusRow(
                R.string.main_game_screen_title, R.string.main_status_not_detected));
        content.addView(checks, matchParams());
        addSpace(content, 12);
        content.addView(callout(R.string.main_device_ready_note, BRAND_BACKGROUND), matchParams());
        return scroll;
    }

    private View buildAutomationScreen() {
        ScrollView scroll = screenScroll();
        LinearLayout content = screenContent(
                getString(R.string.main_automations_title),
                getString(R.string.main_automation_subtitle));
        scroll.addView(content);
        content.addView(sectionTitle(R.string.main_automation_features_title));
        addSpace(content, 9);
        LinearLayout list = card();
        list.addView(automationEntry(
                R.string.main_feature_planting,
                getString(R.string.main_feature_planting_summary,
                        settings.allowedFlowers().size(), settings.threshold()),
                R.string.main_status_ready));
        list.addView(divider(), dividerParams());
        list.addView(automationEntry(
                R.string.main_feature_feed,
                getString(R.string.main_feature_feed_summary,
                        settings.feedsPerSquad(), settings.maxSquadSwitches()),
                R.string.main_status_action));
        list.addView(divider(), dividerParams());
        list.addView(automationEntry(
                R.string.main_feature_postcard,
                getString(R.string.main_feature_postcard_summary,
                        settings.postcardCollectionLimit()),
                R.string.main_status_idle));
        list.addView(divider(), dividerParams());
        list.addView(automationEntry(
                R.string.main_feature_dispatch,
                getString(R.string.main_feature_dispatch_summary),
                R.string.main_status_idle));
        list.addView(divider(), dividerParams());
        list.addView(automationEntry(
                R.string.main_feature_return,
                getString(R.string.main_feature_return_summary),
                R.string.main_status_idle));
        content.addView(list, matchParams());
        addSpace(content, 12);
        content.addView(callout(R.string.main_automation_state_note, BRAND_BACKGROUND), matchParams());
        return scroll;
    }

    /** 依預覽稿重組設定畫面的資訊層級；所有操作仍沿用既有 listener。 */
    private View buildSettingsScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout content = verticalLayout(dp(18), dp(22), dp(18), dp(32));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(getString(R.string.main_settings_title), 28, PRIMARY_DARK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        content.addView(sectionDescription(R.string.main_settings_subtitle));
        addSpace(content, 16);

        LinearLayout hero = card(BRAND_BACKGROUND, 0);
        hero.addView(sectionTitle(R.string.main_quick_start_title));
        hero.addView(sectionDescription(R.string.main_quick_start_description));
        addSpace(hero, 14);
        Button openGame = primaryButton(R.string.main_open_game);
        openGame.setOnClickListener(view -> openGame());
        hero.addView(openGame, matchParams());
        content.addView(hero, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_device_status_title));
        addSpace(content, 9);

        LinearLayout service = card();

        serviceStatus = text("", 15, MUTED);
        serviceStatus.setContentDescription(getString(R.string.main_service_status_description));
        service.addView(statusRow(
                R.string.main_service_row_title,
                R.string.main_service_row_description,
                serviceStatus));
        service.addView(divider(), matchParams());

        LinearLayout overlayRow = verticalLayout(0, dp(12), 0, 0);
        overlayRow.addView(text(getString(R.string.main_overlay_row_title), 15, TEXT));
        overlayRow.addView(sectionDescription(R.string.main_overlay_row_description));
        addSpace(overlayRow, 8);
        overlayToggle = secondaryButton(R.string.main_show_overlay);
        overlayToggle.setOnClickListener(view -> toggleOverlay());
        overlayRow.addView(overlayToggle, matchParams());
        service.addView(overlayRow, matchParams());
        content.addView(service, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_permissions_title));
        addSpace(content, 9);

        LinearLayout permissions = card();
        permissions.addView(warningCard(), matchParams());
        addSpace(permissions, 12);

        CheckBox riskAccepted = new CheckBox(this);
        riskAccepted.setText(R.string.main_risk_acknowledgement);
        riskAccepted.setTextColor(TEXT);
        riskAccepted.setTextSize(14);
        riskAccepted.setMinHeight(dp(48));
        riskAccepted.setPadding(0, 0, 0, 0);
        permissions.addView(riskAccepted, matchParams());
        addSpace(permissions, 8);

        Button accessibility = secondaryButton(R.string.main_open_accessibility);
        accessibility.setOnClickListener(view -> {
            if (!riskAccepted.isChecked()) {
                Toast.makeText(this, R.string.main_accept_risk_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        permissions.addView(accessibility, matchParams());
        addSpace(permissions, 8);

        content.addView(permissions, matchParams());
        addSpace(content, 18);

        LinearLayout links = card();
        links.addView(sectionTitle(R.string.main_links_title));
        addSpace(links, 12);

        Button community = secondaryButton(R.string.main_open_community);
        community.setOnClickListener(view -> openExternalLink(COMMUNITY_URL));
        links.addView(sectionDescription(R.string.main_links_description_community));
        links.addView(community, matchParams());
        addSpace(links, 8);

        Button sponsor = secondaryButton(R.string.main_open_sponsor);
        sponsor.setOnClickListener(view -> openExternalLink(SPONSOR_URL));
        links.addView(sectionDescription(R.string.main_links_description_sponso));
        links.addView(sponsor, matchParams());
        content.addView(links, matchParams());
        addSpace(content, 18);

        LinearLayout help = card();
        help.addView(sectionTitle(R.string.main_help_title));
        help.addView(sectionDescription(R.string.main_help_description));
        content.addView(help, matchParams());
        return scroll;
    }

    private ScrollView screenScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        return scroll;
    }

    private LinearLayout screenContent(String titleValue, String subtitleValue) {
        LinearLayout content = verticalLayout(dp(18), dp(22), dp(18), dp(28));
        TextView title = text(titleValue, 28, PRIMARY_DARK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        content.addView(text(subtitleValue, 14, MUTED));
        addSpace(content, 18);
        return content;
    }

    private Button navigationButton(int resource) {
        Button button = button(getString(resource), Color.TRANSPARENT, MUTED);
        button.setTextSize(12);
        button.setMinHeight(dp(48));
        return button;
    }

    private void showScreen(int selected, View[] screens, Button[] tabs) {
        for (int index = 0; index < screens.length; index++) {
            screens[index].setVisibility(index == selected ? View.VISIBLE : View.GONE);
        }
        styleNavigation(tabs, selected);
    }

    private void styleNavigation(Button[] tabs, int selected) {
        for (int index = 0; index < tabs.length; index++) {
            boolean active = index == selected;
            tabs[index].setTextColor(active ? PRIMARY : MUTED);
            tabs[index].setTypeface(Typeface.DEFAULT,
                    active ? Typeface.BOLD : Typeface.NORMAL);
            tabs[index].setBackground(active
                    ? rounded(BRAND_BACKGROUND, 0, 12)
                    : rounded(Color.TRANSPARENT, 0, 12));
            tabs[index].setSelected(active);
        }
    }

    private View featureTile(int titleResource, String detail, int stateResource) {
        LinearLayout row = verticalLayout(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rounded(SURFACE, BORDER, 16));
        row.setMinimumHeight(dp(132));
        TextView title = text(getString(titleResource), 16, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title);
        row.addView(text(detail, 13, MUTED));
        TextView state = text(getString(stateResource), 12,
                stateResource == R.string.main_status_action ? WARNING_TEXT : PRIMARY);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        state.setPadding(0, dp(5), 0, 0);
        row.addView(state);
        return row;
    }

    private LinearLayout.LayoutParams tileParams(boolean right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(right ? dp(5) : 0, dp(5), right ? 0 : dp(5), dp(5));
        return params;
    }

    private View automationEntry(int titleResource, String detail, int stateResource) {
        Button entry = button(
                getString(titleResource) + "\n" + detail + "  ·  " + getString(stateResource),
                Color.TRANSPARENT,
                TEXT);
        entry.setTextSize(14);
        entry.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        entry.setMinHeight(dp(68));
        entry.setOnClickListener(view -> showOverlayAndGame());
        return entry;
    }

    private View simpleStatusRow(int titleResource, int stateResource) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView title = text(getString(titleResource), 14, TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = text(getString(stateResource), 13,
                stateResource == R.string.main_status_action ? WARNING_TEXT : PRIMARY);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(state);
        return row;
    }

    private TextView callout(int resource, int background) {
        TextView view = text(getString(resource), 13, MUTED);
        view.setPadding(dp(13), dp(11), dp(13), dp(11));
        view.setBackground(rounded(background, 0, 14));
        return view;
    }

    private void showOverlayAndGame() {
        if (!PetalAccessibilityService.setOverlayVisible(true)) {
            Toast.makeText(this,
                    isServiceEnabled()
                            ? R.string.main_service_connecting
                            : R.string.main_enable_service_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        updateOverlayButton();
        openGame();
    }

    /** 顯示必要的風險提示，但不讓提示搶走主要設定操作。 */
    private View warningCard() {
        LinearLayout warning = verticalLayout(dp(14), dp(12), dp(14), dp(12));
        warning.setBackground(rounded(WARNING_BACKGROUND, 0, 14));
        TextView title = text(getString(R.string.main_warning_title), 15, WARNING_TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        warning.addView(title);
        warning.addView(text(getString(R.string.main_warning_body), 14, WARNING_TEXT));
        return warning;
    }

    /** 切換懸浮窗；服務未啟用時以提示取代無效操作。 */
    private void toggleOverlay() {
        boolean visible = PetalAccessibilityService.isOverlayVisible();
        if (!PetalAccessibilityService.setOverlayVisible(!visible)) {
            Toast.makeText(
                    this,
                    isServiceEnabled()
                            ? R.string.main_service_connecting
                            : R.string.main_enable_service_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        updateOverlayButton();
    }

    /** 依目前狀態更新按鈕文字，讓使用者知道下一步會做什麼。 */
    private void updateOverlayButton() {
        if (overlayToggle == null) {
            return;
        }
        overlayToggle.setText(PetalAccessibilityService.isOverlayVisible()
                ? R.string.main_hide_overlay
                : R.string.main_show_overlay);
    }

    /** 開啟已安裝的 Pikmin Bloom；找不到套件時給出可理解的錯誤提示。 */
    private void openGame() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.nianticlabs.pikmin");
        if (intent == null) {
            Toast.makeText(this, R.string.main_game_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    /** 以系統預設瀏覽器或對應 App 開啟主畫面的外部連結。 */
    private void openExternalLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.main_link_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** 查詢系統無障礙服務清單，避免僅依賴本地按鈕狀態。 */
    private boolean isServiceEnabled() {
        if (PetalAccessibilityService.isConnected()) {
            return true;
        }
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isBlank()) {
            return false;
        }

        ComponentName expected = new ComponentName(this, PetalAccessibilityService.class);
        for (String flattenedName : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(flattenedName);
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /** 建立卡片容器，統一圓角、邊框與內距。 */
    private LinearLayout card() {
        return card(SURFACE, BORDER);
    }

    private LinearLayout card(int fill, int stroke) {
        LinearLayout layout = verticalLayout(dp(15), dp(15), dp(15), dp(15));
        layout.setBackground(rounded(fill, stroke, 22));
        return layout;
    }

    /** 將狀態名稱與目前值分層，避免把裝置就緒誤認成特定功能已可執行。 */
    private View statusRow(int titleResource, int descriptionResource, TextView statusView) {
        LinearLayout row = verticalLayout(0, dp(12), 0, dp(12));
        TextView title = text(getString(titleResource), 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title);
        row.addView(sectionDescription(descriptionResource));
        statusView.setPadding(0, dp(5), 0, 0);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(statusView, matchParams());
        return row;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        divider.setMinimumHeight(dp(1));
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
    }

    /** 建立垂直排列的容器。 */
    private LinearLayout verticalLayout(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(left, top, right, bottom);
        return layout;
    }

    /** 建立分區標題。 */
    private TextView sectionTitle(int resource) {
        TextView view = text(getString(resource), 17, PRIMARY_DARK);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    /** 建立分區說明文字。 */
    private TextView sectionDescription(int resource) {
        return text(getString(resource), 14, MUTED);
    }

    /** 建立主要操作按鈕。 */
    private Button primaryButton(int resource) {
        Button button = button(getString(resource), PRIMARY, Color.WHITE);
        button.setContentDescription(getString(resource));
        return button;
    }

    /** 建立次要操作按鈕。 */
    private Button secondaryButton(int resource) {
        Button button = button(getString(resource), SURFACE_SOFT, PRIMARY_DARK);
        button.setContentDescription(getString(resource));
        return button;
    }

    /** 建立按鈕的共同視覺樣式與觸控尺寸。 */
    private Button button(String value, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setStateListAnimator(null);
        button.setBackground(rounded(backgroundColor, BORDER, 14));
        return button;
    }

    /** 建立文字元件並統一行距。 */
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    /** 建立一致的圓角背景。 */
    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    /** 在垂直版面插入固定高度的空白。 */
    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    /** 建立可填滿父容器寬度的版面參數。 */
    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 將 dp 轉成目前螢幕的像素。 */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
