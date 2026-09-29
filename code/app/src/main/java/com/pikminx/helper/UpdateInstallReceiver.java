package com.pikminx.helper;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import com.pikminx.helper.platform.update.ApkUpdateManager;
import com.pikminx.helper.platform.update.InstallStateModel;

/** 接收 PackageInstaller 結果，並在系統要求時顯示安裝確認畫面。 */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS =
            ApkUpdateManager.ACTION_INSTALL_STATUS;
    public static final String EXTRA_INSTALL_ATTEMPT_ID =
            ApkUpdateManager.EXTRA_INSTALL_ATTEMPT_ID;
    public static final String EXTRA_INSTALL_SESSION_ID =
            ApkUpdateManager.EXTRA_INSTALL_SESSION_ID;

    static ComponentName statusReceiverComponent(Context context) {
        return new ComponentName(context, UpdateInstallReceiver.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        int sessionId = intent.getIntExtra(
                PackageInstaller.EXTRA_SESSION_ID, InstallStateModel.NO_SESSION_ID);
        if (sessionId == InstallStateModel.NO_SESSION_ID) {
            sessionId = intent.getIntExtra(
                    EXTRA_INSTALL_SESSION_ID, InstallStateModel.NO_SESSION_ID);
        }
        long attemptId = intent.getLongExtra(
                EXTRA_INSTALL_ATTEMPT_ID, InstallStateModel.NO_ATTEMPT_ID);
        InstallStateModel.CallbackStatus callbackStatus = callbackStatus(status);
        if (callbackStatus == InstallStateModel.CallbackStatus.PENDING_USER_ACTION) {
            Intent confirmation = confirmationIntent(intent);
            InstallStateModel.CallbackResult result = ApkUpdateManager.handleInstallStatus(
                    context,
                    attemptId,
                    sessionId,
                    callbackStatus,
                    confirmation != null,
                    statusMessage(intent));
            if (!result.accepted()) {
                return;
            }
            if (confirmation == null) {
                showFailure(context, result.state().message());
                return;
            }
            if (!result.launchConfirmation()) {
                return;
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirmation);
            } catch (RuntimeException exception) {
                ApkUpdateManager.handleInstallStatus(
                        context,
                        attemptId,
                        sessionId,
                        callbackStatus,
                        false,
                        exception.getMessage());
                showFailure(context, "Unable to open installer confirmation");
            }
            return;
        }
        InstallStateModel.CallbackResult result = ApkUpdateManager.handleInstallStatus(
                context,
                attemptId,
                sessionId,
                callbackStatus,
                false,
                statusMessage(intent));
        if (!result.accepted()) {
            return;
        }
        if (result.state().state() == InstallStateModel.State.SUCCESS) {
            Toast.makeText(context, R.string.main_update_installed, Toast.LENGTH_LONG).show();
        } else {
            showFailure(context, result.state().message());
        }
    }

    private static String statusMessage(Intent intent) {
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        return message == null ? "" : message;
    }

    static InstallStateModel.CallbackStatus callbackStatus(int packageInstallerStatus) {
        if (packageInstallerStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            return InstallStateModel.CallbackStatus.PENDING_USER_ACTION;
        }
        return packageInstallerStatus == PackageInstaller.STATUS_SUCCESS
                ? InstallStateModel.CallbackStatus.SUCCESS
                : InstallStateModel.CallbackStatus.FAILURE;
    }

    private static void showFailure(Context context, String message) {
        Toast.makeText(
                context,
                message == null || message.isEmpty() ? context.getString(R.string.main_update_failed)
                        : message,
                Toast.LENGTH_LONG).show();
    }

    @SuppressWarnings("deprecation")
    private static Intent confirmationIntent(Intent result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        return result.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
