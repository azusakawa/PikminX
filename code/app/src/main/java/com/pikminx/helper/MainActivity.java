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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pikminx.helper.platform.config.RemoteConfigClient;
import com.pikminx.helper.platform.settings.SettingsStore;
import com.pikminx.helper.platform.update.ApkUpdateManager;
import com.pikminx.helper.platform.update.InstallStateModel;

/**
 * 應用程式的主導覽與設定入口。
 *
 * <p>蘑菇功能身份保留在懸浮控制中但目前凍結且不可互動；此 Activity 僅負責一般設定、
 * 權限與系統設定入口。</p>
 */
public final class MainActivity extends Activity {
    static final String EXTRA_REQUEST_LOCATION_PERMISSION =
            "com.pikminx.helper.REQUEST_LOCATION_PERMISSION";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 4201;
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
    private static final String SPONSOR_URL =
            "https://payment.opay.tw/Broadcaster/Donate/0CB6EDA6EAB8577A8D33F1E8E346BC2A";
    private TextView serviceStatus;
    private TextView remoteConfigNotice;
    private Button remoteConfigUpdate;
    private Button overlayToggle;
    private SettingsStore settings;
    private boolean updateInProgress;
    private ApkUpdateManager.Callback installStateCallback;
    private long remoteConfigRequestToken;
    private TextView mockLocationStatus;
    private TextView locationPermissionStatus;
    private TextView locationServiceStatus;
    private boolean finishAfterLocationPermission;
    /** 建立乾淨、可捲動且適合小螢幕的設定頁。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        installStateCallback = this::renderInstallState;
        getWindow().setStatusBarColor(PRIMARY_DARK);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(buildScreen());
        if (getIntent().getBooleanExtra(EXTRA_REQUEST_LOCATION_PERMISSION, false)) {
            finishAfterLocationPermission = true;
            getWindow().getDecorView().post(this::requestLocationPermission);
        }
    }

    /** 每次回到頁面時同步無障礙服務狀態與懸浮窗狀態。 */
    @Override
    protected void onResume() {
        super.onResume();
        ApkUpdateManager.observe(this, installStateCallback);
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
        refreshLocationReadiness();
        refreshRemoteConfig();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_REQUEST_LOCATION_PERMISSION, false)) {
            finishAfterLocationPermission = true;
            getWindow().getDecorView().post(this::requestLocationPermission);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            refreshLocationReadiness();
            if (finishAfterLocationPermission) {
                finishAfterLocationPermission = false;
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        remoteConfigRequestToken++;
        ApkUpdateManager.clearObserver(installStateCallback);
        installStateCallback = null;
        super.onDestroy();
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

        serviceStatus = text("", 15, MUTED);
        serviceStatus.setContentDescription(getString(R.string.main_service_status_description));
        permissions.addView(statusRow(
                R.string.main_service_row_title,
                R.string.main_service_row_description,
                serviceStatus));
        permissions.addView(divider(), matchParams());

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

        Button mockLocationSettings = secondaryButton(R.string.main_open_mock_location_settings);
        mockLocationSettings.setOnClickListener(view -> openMockLocationSettings());
        permissions.addView(mockLocationSettings, matchParams());
        mockLocationStatus = text("", 14, MUTED);
        mockLocationStatus.setContentDescription(
                getString(R.string.main_mock_location_status_description));
        mockLocationStatus.setPadding(0, dp(6), 0, 0);
        permissions.addView(mockLocationStatus, matchParams());
        addSpace(permissions, 8);

        Button requestLocation = secondaryButton(R.string.main_request_location_permission);
        requestLocation.setOnClickListener(view -> requestLocationPermission());
        permissions.addView(requestLocation, matchParams());
        locationPermissionStatus = text("", 14, MUTED);
        locationPermissionStatus.setContentDescription(
                getString(R.string.main_location_permission_status_description));
        locationPermissionStatus.setPadding(0, dp(6), 0, 0);
        permissions.addView(locationPermissionStatus, matchParams());
        locationServiceStatus = text("", 14, MUTED);
        locationServiceStatus.setContentDescription(
                getString(R.string.main_location_service_status_description));
        locationServiceStatus.setPadding(0, dp(6), 0, 0);
        permissions.addView(locationServiceStatus, matchParams());
        Button locationSettings = secondaryButton(R.string.main_open_location_settings);
        locationSettings.setOnClickListener(view -> openLocationSettings());
        permissions.addView(locationSettings, matchParams());
        addSpace(permissions, 8);

        remoteConfigNotice = text("", 14, MUTED);
        remoteConfigNotice.setVisibility(View.GONE);
        permissions.addView(remoteConfigNotice, matchParams());
        remoteConfigUpdate = secondaryButton(R.string.main_download_update);
        remoteConfigUpdate.setVisibility(View.GONE);
        remoteConfigUpdate.setOnClickListener(view -> {
            Object tag = view.getTag();
            if (tag instanceof RemoteConfigClient.Status) {
                startUpdate((RemoteConfigClient.Status) tag);
            }
        });
        permissions.addView(remoteConfigUpdate, matchParams());
        content.addView(permissions, matchParams());
        addSpace(content, 18);

        LinearLayout links = card();
        links.addView(sectionTitle(R.string.main_links_title));
        addSpace(links, 12);

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

    /** Refreshes all location prerequisites when the Activity becomes visible again. */
    private void refreshLocationReadiness() {
        if (mockLocationStatus == null
                || locationPermissionStatus == null
                || locationServiceStatus == null) {
            return;
        }
        MockLocationReadiness.LocationPermissionStatus permissionStatus =
                MockLocationReadiness.locationPermissionStatus(this);
        boolean locationServiceReady = MockLocationReadiness.isLocationServiceEnabled(this);
        boolean mockLocationSelected = MockLocationReadiness.isMockLocationAllowed(this);

        locationPermissionStatus.setText(locationPermissionStatusResource(permissionStatus));
        locationPermissionStatus.setTextColor(
                permissionStatus == MockLocationReadiness.LocationPermissionStatus.PRECISE_LOCATION_READY
                        ? PRIMARY : WARNING_TEXT);
        locationServiceStatus.setText(locationServiceReady
                ? R.string.main_location_service_ready
                : R.string.main_location_service_disabled);
        locationServiceStatus.setTextColor(locationServiceReady ? PRIMARY : WARNING_TEXT);
        mockLocationStatus.setText(mockLocationSelected
                ? R.string.main_mock_location_selected
                : R.string.main_mock_location_not_selected);
        mockLocationStatus.setTextColor(mockLocationSelected ? PRIMARY : WARNING_TEXT);
    }

    private int locationPermissionStatusResource(
            MockLocationReadiness.LocationPermissionStatus status) {
        if (status == null) {
            return R.string.main_location_permission_required;
        }
        return switch (status) {
            case PRECISE_LOCATION_READY -> R.string.main_location_permission_ready;
            case COARSE_ONLY -> R.string.main_location_permission_coarse;
            case NO_LOCATION_PERMISSION -> R.string.main_location_permission_required;
        };
    }

    private void requestLocationPermission() {
        if (MockLocationReadiness.hasFineLocationPermission(this)) {
            refreshLocationReadiness();
            if (finishAfterLocationPermission) {
                finishAfterLocationPermission = false;
                finish();
            }
            return;
        }
        requestPermissions(
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    /** Opens the public Developer Options page; Android owns mock-location selection. */
    private void openMockLocationSettings() {
        Intent developerSettings = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        try {
            if (developerSettings.resolveActivity(getPackageManager()) != null) {
                startActivity(developerSettings);
            } else {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
            Toast.makeText(
                    this,
                    R.string.main_mock_location_settings_guidance,
                    Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    R.string.main_mock_location_settings_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Opens the Android system location switch without changing it programmatically. */
    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    R.string.main_location_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** 讀取遠端版本與服務狀態，並在更新資料完整時提供覆蓋安裝入口。 */
    private void refreshRemoteConfig() {
        if (remoteConfigNotice == null || remoteConfigUpdate == null) {
            return;
        }
        final long requestToken = ++remoteConfigRequestToken;
        RemoteConfigClient.fetch(this, remoteConfig -> {
            if (requestToken != remoteConfigRequestToken || isFinishing() || isDestroyed()) {
                return;
            }
            InstallStateModel.Snapshot installState = ApkUpdateManager.currentState(this);
            boolean installActive = InstallStateModel.isInFlight(installState);
            if (remoteConfig == null) {
                remoteConfigNotice.setVisibility(View.GONE);
                if (installActive) {
                    remoteConfigUpdate.setTag(null);
                    renderInstallState(installState);
                } else {
                    remoteConfigUpdate.setVisibility(View.GONE);
                }
                return;
            }
            boolean blocked = remoteConfig.blocksAutomation(BuildConfig.VERSION_CODE);
            boolean updateAvailable = remoteConfig.updateAvailable(BuildConfig.VERSION_CODE);
            if (!blocked && !updateAvailable) {
                remoteConfigNotice.setVisibility(View.GONE);
                if (installActive) {
                    remoteConfigUpdate.setTag(null);
                    renderInstallState(installState);
                } else {
                    remoteConfigUpdate.setVisibility(View.GONE);
                }
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

            if (installActive) {
                remoteConfigUpdate.setTag(
                        remoteConfig.hasInstallableUpdate(BuildConfig.VERSION_CODE)
                                ? remoteConfig
                                : null);
                renderInstallState(installState);
            } else if (remoteConfig.hasInstallableUpdate(BuildConfig.VERSION_CODE)) {
                remoteConfigUpdate.setTag(remoteConfig);
                remoteConfigUpdate.setEnabled(true);
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

    private void renderInstallState(InstallStateModel.Snapshot installState) {
        if (installState == null || remoteConfigUpdate == null
                || isFinishing() || isDestroyed()) {
            return;
        }
        updateInProgress = InstallStateModel.isInFlight(installState);
        if (updateInProgress) {
            remoteConfigUpdate.setEnabled(false);
            remoteConfigUpdate.setText(
                    installState.state() == InstallStateModel.State.DOWNLOADING
                            || installState.state() == InstallStateModel.State.VERIFIED
                            ? R.string.main_downloading_update
                            : R.string.main_update_installer_ready);
            remoteConfigUpdate.setVisibility(View.VISIBLE);
            return;
        }
        Object tag = remoteConfigUpdate.getTag();
        if (tag instanceof RemoteConfigClient.Status status) {
            remoteConfigUpdate.setEnabled(true);
            remoteConfigUpdate.setText(status.forceUpdate()
                    ? R.string.main_download_required_update
                    : R.string.main_download_update);
        } else if (installState.state() == InstallStateModel.State.SUCCESS
                || installState.state() == InstallStateModel.State.FAILURE) {
            remoteConfigUpdate.setVisibility(View.GONE);
        }
    }

    /** 取得未知來源權限後下載、驗證並交由 Android 系統覆蓋安裝。 */
    private void startUpdate(RemoteConfigClient.Status status) {
        if (ApkUpdateManager.isInFlight(this)) {
            renderInstallState(ApkUpdateManager.currentState(this));
            return;
        }
        if (!status.hasInstallableUpdate(BuildConfig.VERSION_CODE)) {
            Toast.makeText(this, R.string.main_update_config_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(
                    this, R.string.main_update_permission_required, Toast.LENGTH_LONG).show();
            return;
        }

        updateInProgress = true;
        remoteConfigUpdate.setEnabled(false);
        remoteConfigUpdate.setText(R.string.main_downloading_update);
        ApkUpdateManager.downloadAndInstall(
                this,
                status,
                installStateCallback,
                UpdateInstallReceiver.statusReceiverComponent(this));
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
