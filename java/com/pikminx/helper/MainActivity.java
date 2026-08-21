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
    private static final int BACKGROUND = Color.rgb(246, 249, 244);
    private static final int SURFACE = Color.WHITE;
    private static final int PRIMARY = Color.rgb(37, 99, 62);
    private static final int PRIMARY_DARK = Color.rgb(25, 72, 43);
    private static final int TEXT = Color.rgb(32, 48, 38);
    private static final int MUTED = Color.rgb(92, 108, 96);
    private static final int BORDER = Color.rgb(211, 224, 214);
    private static final int WARNING_BACKGROUND = Color.rgb(255, 244, 224);
    private static final int WARNING_TEXT = Color.rgb(123, 79, 17);
    private static final String COMMUNITY_URL =
            "https://line.me/ti/g2/kBeFvQzEdGSJ3J48e9tkkEljK2wq0Mxb_FauOA?utm_source=invitation&utm_medium=link_copy&utm_campaign=default";
    private static final String SPONSOR_URL =
            "https://payment.opay.tw/Broadcaster/Donate/0CB6EDA6EAB8577A8D33F1E8E346BC2A";

    private TextView serviceStatus;
    private TextView remoteConfigNotice;
    private Button remoteConfigUpdate;
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
        refreshRemoteConfig();
    }

    /** 組合主畫面，讓每個區塊有一致的間距與卡片背景。 */
    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout content = verticalLayout(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = verticalLayout(0, 0, 0, 0);
        TextView title = text(getString(R.string.main_title), 30, PRIMARY_DARK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);
        content.addView(header, matchParams());
        addSpace(content, 18);

        content.addView(warningCard(), matchParams());
        addSpace(content, 14);

        LinearLayout service = card();
        service.addView(sectionTitle(R.string.main_service_title));
        service.addView(sectionDescription(R.string.main_service_description));
        addSpace(service, 12);

        serviceStatus = text("", 15, MUTED);
        serviceStatus.setContentDescription(getString(R.string.main_service_status_description));
        service.addView(serviceStatus, matchParams());
        addSpace(service, 10);

        remoteConfigNotice = text("", 14, MUTED);
        remoteConfigNotice.setVisibility(View.GONE);
        service.addView(remoteConfigNotice, matchParams());
        remoteConfigUpdate = secondaryButton(R.string.main_download_update);
        remoteConfigUpdate.setVisibility(View.GONE);
        remoteConfigUpdate.setOnClickListener(view -> {
            String url = (String) view.getTag();
            if (url != null && !url.isBlank()) {
                openExternalLink(url);
            }
        });
        service.addView(remoteConfigUpdate, matchParams());
        addSpace(service, 8);

        CheckBox riskAccepted = new CheckBox(this);
        riskAccepted.setText(R.string.main_risk_acknowledgement);
        riskAccepted.setTextColor(TEXT);
        riskAccepted.setPadding(0, 0, 0, 0);
        service.addView(riskAccepted, matchParams());
        addSpace(service, 8);

        Button accessibility = secondaryButton(R.string.main_open_accessibility);
        accessibility.setOnClickListener(view -> {
            if (!riskAccepted.isChecked()) {
                Toast.makeText(this, R.string.main_accept_risk_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        service.addView(accessibility, matchParams());
        addSpace(service, 8);

        overlayToggle = secondaryButton(R.string.main_show_overlay);
        overlayToggle.setOnClickListener(view -> toggleOverlay());
        service.addView(overlayToggle, matchParams());
        addSpace(service, 8);

        Button openGame = secondaryButton(R.string.main_open_game);
        openGame.setOnClickListener(view -> openGame());
        service.addView(openGame, matchParams());
        content.addView(service, matchParams());
        addSpace(content, 14);

        LinearLayout links = card();
        links.addView(sectionTitle(R.string.main_links_title));
        addSpace(links, 12);

        Button community = secondaryButton(R.string.main_open_community);
        community.setOnClickListener(view -> openExternalLink(COMMUNITY_URL));
        links.addView(sectionDescription(R.string.main_links_description_community));
        links.addView(community, matchParams());
        addSpace(links, 8);

        Button sponsor = primaryButton(R.string.main_open_sponsor);
        sponsor.setOnClickListener(view -> openExternalLink(SPONSOR_URL));
        links.addView(sectionDescription(R.string.main_links_description_sponso));
        links.addView(sponsor, matchParams());
        content.addView(links, matchParams());
        addSpace(content, 14);

        LinearLayout help = card();
        help.addView(sectionTitle(R.string.main_help_title));
        help.addView(sectionDescription(R.string.main_help_description));
        content.addView(help, matchParams());
        return scroll;
    }

    /** 讀取遠端版本與服務狀態；只顯示通知，不在背景自動安裝 APK。 */
    private void refreshRemoteConfig() {
        if (remoteConfigNotice == null || remoteConfigUpdate == null) {
            return;
        }
        RemoteConfigClient.fetch(this, remoteConfig -> {
            if (remoteConfig == null) {
                remoteConfigNotice.setVisibility(View.GONE);
                remoteConfigUpdate.setVisibility(View.GONE);
                return;
            }
            boolean blocked = remoteConfig.blocksAutomation(BuildConfig.VERSION_CODE);
            boolean updateAvailable = remoteConfig.updateAvailable(BuildConfig.VERSION_CODE);
            if (!blocked && !updateAvailable) {
                remoteConfigNotice.setVisibility(View.GONE);
                remoteConfigUpdate.setVisibility(View.GONE);
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

            String downloadUrl = remoteConfig.downloadUrl();
            if (updateAvailable && !downloadUrl.isEmpty()) {
                remoteConfigUpdate.setTag(downloadUrl);
                remoteConfigUpdate.setText(remoteConfig.forceUpdate()
                        ? R.string.main_download_required_update
                        : R.string.main_download_update);
                remoteConfigUpdate.setVisibility(View.VISIBLE);
            } else {
                remoteConfigUpdate.setTag(null);
                remoteConfigUpdate.setVisibility(View.GONE);
            }
        });
    }

    /** 顯示必要的風險提示，但不讓提示搶走主要設定操作。 */
    private View warningCard() {
        LinearLayout warning = verticalLayout(dp(14), dp(12), dp(14), dp(12));
        warning.setBackground(rounded(WARNING_BACKGROUND, 0, 12));
        TextView title = text(getString(R.string.main_warning_title), 16, WARNING_TEXT);
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
        LinearLayout layout = verticalLayout(dp(18), dp(16), dp(18), dp(16));
        layout.setBackground(rounded(SURFACE, BORDER, 16));
        return layout;
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
        TextView view = text(getString(resource), 19, PRIMARY_DARK);
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
        Button button = button(getString(resource), Color.rgb(238, 246, 239), PRIMARY_DARK);
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
        button.setMinHeight(dp(50));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setStateListAnimator(null);
        button.setBackground(rounded(backgroundColor, BORDER, 12));
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
