package com.pikminx.helper;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Process;

/** Supported readiness checks for the user-selected Android mock-location provider. */
final class MockLocationReadiness {
    enum LocationPermissionStatus {
        NO_LOCATION_PERMISSION,
        COARSE_ONLY,
        PRECISE_LOCATION_READY
    }

    enum Status {
        MOCK_LOCATION_READY,
        NO_LOCATION_PERMISSION,
        COARSE_ONLY,
        LOCATION_SERVICE_DISABLED,
        MOCK_LOCATION_APP_REQUIRED
    }

    private MockLocationReadiness() {}

    static Status evaluate(Context context) {
        return evaluate(
                locationPermissionStatus(context),
                isLocationServiceEnabled(context),
                isMockLocationAllowed(context));
    }

    /** Pure decision function used by tests and by the Android adapter. */
    static Status evaluate(
            LocationPermissionStatus permissionStatus,
            boolean locationServiceEnabled,
            boolean mockLocationAllowed) {
        LocationPermissionStatus permission = permissionStatus == null
                ? LocationPermissionStatus.NO_LOCATION_PERMISSION : permissionStatus;
        if (permission == LocationPermissionStatus.NO_LOCATION_PERMISSION) {
            return Status.NO_LOCATION_PERMISSION;
        }
        if (permission == LocationPermissionStatus.COARSE_ONLY) {
            return Status.COARSE_ONLY;
        }
        if (!locationServiceEnabled) {
            return Status.LOCATION_SERVICE_DISABLED;
        }
        return mockLocationAllowed
                ? Status.MOCK_LOCATION_READY : Status.MOCK_LOCATION_APP_REQUIRED;
    }

    static LocationPermissionStatus locationPermissionStatus(Context context) {
        if (context == null) {
            return LocationPermissionStatus.NO_LOCATION_PERMISSION;
        }
        return locationPermissionStatus(
                hasFineLocationPermission(context), hasCoarseLocationPermission(context));
    }

    /** Pure precise/approximate permission classification. */
    static LocationPermissionStatus locationPermissionStatus(
            boolean fineLocationPermission, boolean coarseLocationPermission) {
        if (fineLocationPermission) {
            return LocationPermissionStatus.PRECISE_LOCATION_READY;
        }
        return coarseLocationPermission
                ? LocationPermissionStatus.COARSE_ONLY
                : LocationPermissionStatus.NO_LOCATION_PERMISSION;
    }

    static boolean hasFineLocationPermission(Context context) {
        return context != null
                && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasCoarseLocationPermission(Context context) {
        return context != null
                && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isLocationServiceEnabled(Context context) {
        if (context == null) {
            return false;
        }
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        try {
            return locationManager.isLocationEnabled();
        } catch (SecurityException error) {
            return false;
        }
    }

    static boolean isMockLocationAllowed(Context context) {
        if (context == null) {
            return false;
        }
        AppOpsManager appOps =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        try {
            int mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.getPackageName());
            return isMockLocationModeAllowed(mode);
        } catch (SecurityException | IllegalArgumentException error) {
            return false;
        }
    }

    /** Only the explicit Android AppOp allow state authorizes mock-location use. */
    static boolean isMockLocationModeAllowed(int mode) {
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
