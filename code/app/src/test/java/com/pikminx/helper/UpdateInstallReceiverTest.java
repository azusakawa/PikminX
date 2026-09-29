package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageInstaller;

import com.pikminx.helper.platform.update.ApkUpdateManager;
import com.pikminx.helper.platform.update.InstallStateModel;

import org.junit.Test;

public final class UpdateInstallReceiverTest {
    @Test
    public void manifestAdapterMapsEveryPackageInstallerTerminalCategory() {
        assertEquals(InstallStateModel.CallbackStatus.PENDING_USER_ACTION,
                UpdateInstallReceiver.callbackStatus(PackageInstaller.STATUS_PENDING_USER_ACTION));
        assertEquals(InstallStateModel.CallbackStatus.SUCCESS,
                UpdateInstallReceiver.callbackStatus(PackageInstaller.STATUS_SUCCESS));
        assertEquals(InstallStateModel.CallbackStatus.FAILURE,
                UpdateInstallReceiver.callbackStatus(PackageInstaller.STATUS_FAILURE_ABORTED));
        assertEquals("com.pikminx.helper.action.UPDATE_INSTALL_STATUS",
                ApkUpdateManager.ACTION_INSTALL_STATUS);
        assertEquals(ApkUpdateManager.ACTION_INSTALL_STATUS,
                UpdateInstallReceiver.ACTION_INSTALL_STATUS);
        assertEquals(ApkUpdateManager.EXTRA_INSTALL_ATTEMPT_ID,
                UpdateInstallReceiver.EXTRA_INSTALL_ATTEMPT_ID);
        assertEquals(ApkUpdateManager.EXTRA_INSTALL_SESSION_ID,
                UpdateInstallReceiver.EXTRA_INSTALL_SESSION_ID);
    }
}
