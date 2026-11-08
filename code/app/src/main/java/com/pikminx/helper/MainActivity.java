package com.pikminx.helper;

import android.Manifest;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.pikminx.helper.platform.config.RemoteConfigClient;

/**
 * 應用程式的主導覽與設定入口。
 *
 * <p>此 Activity 僅負責一般設定、權限與系統設定入口。</p>
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
    private static final int WARNING_TEXT = Color.rgb(155, 106, 0);
    private static final String SPONSOR_URL =
            "https://payment.opay.tw/Broadcaster/Donate/0CB6EDA6EAB8577A8D33F1E8E346BC2A";
    private TextView homeServiceDot;
    private TextView homeServiceStatus;
    private TextView homeOverlayDot;
    private TextView homeOverlayStatus;
    private Button accessibilityAction;
    private TextView remoteConfigNotice;
    private Button remoteConfigUpdate;
    private Switch overlayToggle;
    private long remoteConfigRequestToken;
    /** 建立乾淨、可捲動且適合小螢幕的設定頁。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PRIMARY_DARK);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(buildScreen());
    }

    /** 每次回到頁面時同步無障礙服務狀態與懸浮窗狀態。 */
    @Override
    protected void onResume() {
        super.onResume();
        refreshDeviceStatus();
        refreshRemoteConfig();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        remoteConfigRequestToken++;
        super.onDestroy();
    }

    /** 建立畫布中的單一底部導航；各頁只投影既有設定與入口。 */
    private View buildScreen() {
        LinearLayout root = verticalLayout(0, 0, 0, 0);
        root.setBackgroundColor(BACKGROUND);

        FrameLayout pages = new FrameLayout(this);
        View home = buildHomeScreen();
        View preferences = buildSettingsScreen();
        View[] screens = {home, preferences};
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

        content.addView(sectionTitle(R.string.main_device_status_title));
        addSpace(content, 9);
        LinearLayout statuses = card();
        homeServiceDot = text("●", 20, MUTED);
        homeServiceStatus = text(getString(R.string.main_state_disabled), 13, MUTED);
        statuses.addView(deviceStatusRow(
                homeServiceDot,
                R.string.main_service_row_title,
                R.string.main_home_service_description,
                homeServiceStatus));
        statuses.addView(divider(), dividerParams());
        homeOverlayDot = text("●", 20, MUTED);
        homeOverlayStatus = text(getString(R.string.main_state_disabled), 13, MUTED);
        statuses.addView(deviceStatusRow(
                homeOverlayDot,
                R.string.main_overlay_setting_title,
                R.string.main_home_overlay_description,
                homeOverlayStatus));
        content.addView(statuses, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_features_title));
        addSpace(content, 9);
        content.addView(featureCard(
                "🌱", R.string.main_feature_planting,
                R.string.main_feature_planting_description), matchParams());
        addSpace(content, 10);
        content.addView(featureCard(
                "🌸", R.string.main_feature_feed,
                R.string.main_feature_feed_description), matchParams());
        addSpace(content, 10);
        content.addView(featureCard(
                "🧭", R.string.main_feature_dispatch,
                R.string.main_feature_dispatch_description), matchParams());
        addSpace(content, 10);
        content.addView(featureCard(
                "💌", R.string.main_feature_postcard,
                R.string.main_feature_postcard_description), matchParams());
        addSpace(content, 10);
        content.addView(featureCard(
                "🎁", R.string.main_feature_return,
                R.string.main_feature_return_description), matchParams());
        return scroll;
    }

    /** 建立只包含 App 與 Android 整合設定的頁面。 */
    private View buildSettingsScreen() {
        ScrollView scroll = screenScroll();
        LinearLayout content = screenContent(
                getString(R.string.main_title), getString(R.string.main_settings_title));
        scroll.addView(content);

        content.addView(sectionTitle(R.string.main_pikmin_bloom_section));
        addSpace(content, 9);
        LinearLayout game = card();
        Button openGame = compactActionButton(R.string.main_open_action);
        openGame.setOnClickListener(view -> openGame());
        game.addView(settingRow(
                R.string.main_open_game,
                getString(R.string.main_open_game_description),
                openGame));
        content.addView(game, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_pikminx_section));
        addSpace(content, 9);
        LinearLayout app = card();
        remoteConfigUpdate = compactActionButton(R.string.main_check_update);
        remoteConfigUpdate.setOnClickListener(
                view -> refreshRemoteConfig());
        app.addView(settingRow(
                R.string.main_version_title,
                getString(R.string.main_current_version, BuildConfig.VERSION_NAME),
                remoteConfigUpdate));
        remoteConfigNotice = text("", 14, MUTED);
        remoteConfigNotice.setVisibility(View.GONE);
        remoteConfigNotice.setPadding(0, dp(6), 0, dp(8));
        app.addView(remoteConfigNotice, matchParams());
        app.addView(divider(), dividerParams());

        overlayToggle = new Switch(this);
        overlayToggle.setShowText(false);
        overlayToggle.setContentDescription(getString(R.string.main_overlay_setting_title));
        overlayToggle.setOnClickListener(view -> toggleOverlay());
        app.addView(settingRow(
                R.string.main_overlay_setting_title,
                getString(R.string.main_overlay_setting_description),
                overlayToggle));
        app.addView(divider(), dividerParams());

        accessibilityAction = compactActionButton(R.string.main_state_disabled);
        accessibilityAction.setOnClickListener(
                view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        app.addView(settingRow(
                R.string.main_service_row_title,
                getString(R.string.main_accessibility_setting_description),
                accessibilityAction));
        content.addView(app, matchParams());
        addSpace(content, 18);

        content.addView(sectionTitle(R.string.main_support_section));
        addSpace(content, 9);
        LinearLayout support = card();
        Button sponsor = compactActionButton(R.string.main_support_action);
        sponsor.setOnClickListener(view -> openExternalLink(SPONSOR_URL));
        support.addView(settingRow(
                R.string.main_sponsor_title,
                getString(R.string.main_sponsor_description),
                sponsor));
        content.addView(support, matchParams());
        return scroll;
    }

    private void refreshDeviceStatus() {
        boolean serviceEnabled = isServiceEnabled();
        renderStatus(homeServiceDot, homeServiceStatus, serviceEnabled);
        if (accessibilityAction != null) {
            accessibilityAction.setText(serviceEnabled
                    ? R.string.main_state_enabled : R.string.main_state_disabled);
            accessibilityAction.setTextColor(serviceEnabled ? PRIMARY : MUTED);
        }

        boolean overlayVisible = PetalAccessibilityService.isOverlayVisible();
        renderStatus(homeOverlayDot, homeOverlayStatus, overlayVisible);
        if (overlayToggle != null && overlayToggle.isChecked() != overlayVisible) {
            overlayToggle.setChecked(overlayVisible);
        }
    }

    private void renderStatus(TextView dot, TextView status, boolean enabled) {
        if (dot != null) {
            dot.setTextColor(enabled ? PRIMARY : MUTED);
        }
        if (status != null) {
            status.setText(enabled ? R.string.main_state_enabled : R.string.main_state_disabled);
            status.setTextColor(enabled ? PRIMARY : MUTED);
        }
    }

    private View deviceStatusRow(
            TextView dot,
            int titleResource,
            int descriptionResource,
            TextView status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setMinimumHeight(dp(60));

        dot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(28), dp(44));
        dotParams.setMargins(0, 0, dp(8), 0);
        row.addView(dot, dotParams);

        LinearLayout detail = verticalLayout(0, 0, 0, 0);
        TextView title = text(getString(titleResource), 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detail.addView(title);
        detail.addView(text(getString(descriptionResource), 13, MUTED));
        row.addView(detail, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(status);
        return row;
    }

    private View featureCard(String iconValue, int titleResource, int descriptionResource) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(88));
        row.setBackground(rounded(SURFACE, BORDER, 20));
        row.setClickable(false);
        row.setFocusable(false);

        TextView icon = text(iconValue, 22, PRIMARY_DARK);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(BRAND_BACKGROUND, 0, 14));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout detail = verticalLayout(0, 0, 0, 0);
        TextView title = text(getString(titleResource), 17, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detail.addView(title);
        detail.addView(text(getString(descriptionResource), 13, MUTED));
        row.addView(detail, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View settingRow(int titleResource, String description, View trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.setMinimumHeight(dp(60));

        LinearLayout detail = verticalLayout(0, 0, 0, 0);
        TextView title = text(getString(titleResource), 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detail.addView(title);
        detail.addView(text(description, 13, MUTED));
        row.addView(detail, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trailingParams.setMargins(dp(10), 0, 0, 0);
        row.addView(trailing, trailingParams);
        return row;
    }

    private Button compactActionButton(int resource) {
        Button action = secondaryButton(resource);
        action.setTextSize(13);
        action.setMinHeight(dp(40));
        return action;
    }

    /** 讀取遠端版本與服務狀態。更新相關功能已凍結。 */
    private void refreshRemoteConfig() {
        if (remoteConfigNotice == null || remoteConfigUpdate == null) {
            return;
        }
        remoteConfigUpdate.setTag(null);
        remoteConfigUpdate.setEnabled(false);
        remoteConfigUpdate.setText(R.string.main_checking_update);
        remoteConfigUpdate.setVisibility(View.VISIBLE);
        final long requestToken = ++remoteConfigRequestToken;
        RemoteConfigClient.fetch(this, remoteConfig -> {
            if (requestToken != remoteConfigRequestToken || isFinishing() || isDestroyed()) {
                return;
            }
            if (remoteConfig == null) {
                remoteConfigNotice.setVisibility(View.GONE);
                resetUpdateAction();
                return;
            }
            boolean blocked = remoteConfig.blocksAutomation(BuildConfig.VERSION_CODE);
            boolean updateAvailable = remoteConfig.updateAvailable(BuildConfig.VERSION_CODE);
            if (!blocked && !updateAvailable) {
                remoteConfigNotice.setVisibility(View.GONE);
                resetUpdateAction();
                return;
            }

            String notice;
            if (blocked) {
                notice = remoteConfig.message();
            } else if (!remoteConfig.latestVersionName().isEmpty()) {
                notice = getString(
                        R.string.main_update_available,
                        remoteConfig.latestVersionName());
            } else {
                notice = getString(R.string.main_update_available_generic);
            }
            remoteConfigNotice.setText(notice);
            remoteConfigNotice.setTextColor(blocked ? WARNING_TEXT : PRIMARY_DARK);
            remoteConfigNotice.setVisibility(View.VISIBLE);
            resetUpdateAction();
        });
    }

    private void resetUpdateAction() {
        if (remoteConfigUpdate == null) {
            return;
        }
        remoteConfigUpdate.setTag(null);
        remoteConfigUpdate.setEnabled(true);
        remoteConfigUpdate.setText(R.string.main_check_update);
        remoteConfigUpdate.setVisibility(View.VISIBLE);
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

    /** 切換懸浮窗；服務未啟用時以提示取代無效操作。 */
    private void toggleOverlay() {
        boolean visible = PetalAccessibilityService.isOverlayVisible();
        if (!PetalAccessibilityService.setOverlayVisible(!visible)) {
            refreshDeviceStatus();
            Toast.makeText(
                    this,
                    isServiceEnabled()
                            ? R.string.main_service_connecting
                            : R.string.main_enable_service_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        refreshDeviceStatus();
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
