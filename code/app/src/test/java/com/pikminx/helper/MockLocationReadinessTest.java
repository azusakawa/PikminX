package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import org.junit.Test;

public final class MockLocationReadinessTest {
    @Test
    public void missingPermissionHasHighestPriority() {
        assertEquals(
                MockLocationReadiness.LocationPermissionStatus.NO_LOCATION_PERMISSION,
                MockLocationReadiness.locationPermissionStatus(false, false));
    }

    @Test
    public void coarsePermissionIsNotPrecise() {
        assertEquals(
                MockLocationReadiness.LocationPermissionStatus.COARSE_ONLY,
                MockLocationReadiness.locationPermissionStatus(false, true));
    }

    @Test
    public void finePermissionIsPrecise() {
        assertEquals(
                MockLocationReadiness.LocationPermissionStatus.PRECISE_LOCATION_READY,
                MockLocationReadiness.locationPermissionStatus(true, true));
    }

    @Test
    public void noPermissionBlocksBeforeOtherChecks() {
        assertEquals(
                MockLocationReadiness.Status.NO_LOCATION_PERMISSION,
                MockLocationReadiness.evaluate(
                        MockLocationReadiness.LocationPermissionStatus.NO_LOCATION_PERMISSION,
                        false,
                        false));
    }

    @Test
    public void coarsePermissionBlocksPrecisePatrol() {
        assertEquals(
                MockLocationReadiness.Status.COARSE_ONLY,
                MockLocationReadiness.evaluate(
                        MockLocationReadiness.LocationPermissionStatus.COARSE_ONLY,
                        true,
                        true));
    }

    @Test
    public void disabledLocationServiceBlocksBeforeMockAppCheck() {
        assertEquals(
                MockLocationReadiness.Status.LOCATION_SERVICE_DISABLED,
                MockLocationReadiness.evaluate(
                        MockLocationReadiness.LocationPermissionStatus.PRECISE_LOCATION_READY,
                        false,
                        false));
    }

    @Test
    public void missingMockLocationAppIsNotReady() {
        assertEquals(
                MockLocationReadiness.Status.MOCK_LOCATION_APP_REQUIRED,
                MockLocationReadiness.evaluate(
                        MockLocationReadiness.LocationPermissionStatus.PRECISE_LOCATION_READY,
                        true,
                        false));
    }

    @Test
    public void allPrerequisitesAreReadyOnlyWhenAllChecksPass() {
        assertEquals(
                MockLocationReadiness.Status.MOCK_LOCATION_READY,
                MockLocationReadiness.evaluate(
                        MockLocationReadiness.LocationPermissionStatus.PRECISE_LOCATION_READY,
                        true,
                        true));
    }

    @Test
    public void onlyAllowedMockLocationAppOpIsReady() {
        assertTrue(MockLocationReadiness.isMockLocationModeAllowed(
                AppOpsManager.MODE_ALLOWED));
        assertFalse(MockLocationReadiness.isMockLocationModeAllowed(
                AppOpsManager.MODE_IGNORED));
        assertFalse(MockLocationReadiness.isMockLocationModeAllowed(
                AppOpsManager.MODE_ERRORED));
        assertFalse(MockLocationReadiness.isMockLocationModeAllowed(
                AppOpsManager.MODE_DEFAULT));
    }
}
