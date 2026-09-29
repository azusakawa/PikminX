package com.pikminx.helper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the Mushroom feature surface and its separation from MainActivity settings. */
@RunWith(AndroidJUnit4.class)
public final class MushroomFinderUiInstrumentedTest {
    @Test
    public void mergedProductionManifestDeclaresLocationPermissions() throws Exception {
        PackageManager packageManager = InstrumentationRegistry.getTargetContext()
                .getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(
                InstrumentationRegistry.getTargetContext().getPackageName(),
                PackageManager.GET_PERMISSIONS);
        String[] permissions = packageInfo.requestedPermissions;

        assertNotNull("requested permissions", permissions);
        java.util.List<String> declared = java.util.Arrays.asList(permissions);
        assertTrue("ACCESS_MOCK_LOCATION manifest declaration", declared.contains(
                "android.permission.ACCESS_MOCK_LOCATION"));
        assertTrue("ACCESS_COARSE_LOCATION manifest declaration", declared.contains(
                "android.permission.ACCESS_COARSE_LOCATION"));
        assertTrue("ACCESS_FINE_LOCATION manifest declaration", declared.contains(
                "android.permission.ACCESS_FINE_LOCATION"));
    }

    @Test
    public void mainActivityKeepsMushroomControlsOutOfItsPrimarySurface() {
        Intent launch = InstrumentationRegistry.getTargetContext()
                .getPackageManager()
                .getLaunchIntentForPackage("com.pikminx.helper");
        assertNotNull("PikminX launcher activity", launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launch);
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            View root = activity.getWindow().getDecorView();
            assertNull("MainActivity must not host Mushroom controls",
                    findText(root, "開始掃描"));
            View settingsTab = findText(root, "⚙\n設定");
            assertNotNull("settings navigation", settingsTab);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(settingsTab::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull("Accessibility settings action", findText(root, "開啟無障礙設定"));
            assertNotNull("mock-location settings action", findText(root, "開啟模擬定位設定"));
        } finally {
            activity.finishAndRemoveTask();
        }
    }

    @Test
    public void featurePanelExposesScanAndMapControls() {
        final MushroomOverlayPanel[] panelHolder = new MushroomOverlayPanel[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> panelHolder[0] =
                new MushroomOverlayPanel(
                        InstrumentationRegistry.getTargetContext(),
                        new MushroomOverlayPanel.Listener() {
                            @Override public void onStartScan() {}
                            @Override public void onRescanScan() {}
                            @Override public void onStopScan() {}
                            @Override public void onSelectionChanged(MapSelection selection) {}
                            @Override public void onCurrentLocation() {}
                            @Override public void onViewSelection() {}
                            @Override public void onNavigateToCoordinate(String rawValue) {}
                            @Override public void onAssignResultCoordinate(
                                    MushroomDetectionId detectionId) {}
                            @Override public void onMushroomCoordinateSelected(
                                    MushroomDetectionId detectionId, MapCoordinate coordinate) {}
                            @Override public void onViewMushroom(
                                    MushroomDetectionId detectionId) {}
                            @Override public void onClearSelection() {}
                            @Override public void onRemoveLastWaypoint() {}
                            @Override public void onStartPatrol() {}
                            @Override public void onPausePatrol() {}
                            @Override public void onResumePatrol() {}
                            @Override public void onStopPatrol() {}
                        }));
        try {
            View panel = panelHolder[0];
            assertNotNull("掃描 sub-tab", findText(panel, "掃描"));
            assertNotNull("地圖 sub-tab", findText(panel, "地圖"));
            assertNotNull("開始掃描 action", findText(panel, "開始掃描"));
            View mapTab = findText(panel, "地圖");
            assertNotNull("地圖 sub-tab is actionable", mapTab);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(mapTab::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull("開始巡航 action", findText(panel, "開始巡航"));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    panelHolder[0].dispose());
        }
    }

    private static View findText(View view, String expected) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (view instanceof TextView
                && expected.equals(((TextView) view).getText().toString())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findText(group.getChildAt(index), expected);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
