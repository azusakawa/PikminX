package com.pikminx.helper.platform.update;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.pikminx.helper.platform.config.RemoteConfigClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 下載、驗證並交由 Android 系統覆蓋安裝同套件新版 APK。 */
public final class ApkUpdateManager {
    public static final String ACTION_INSTALL_STATUS =
            "com.pikminx.helper.action.UPDATE_INSTALL_STATUS";
    public static final String EXTRA_INSTALL_ATTEMPT_ID =
            "com.pikminx.helper.extra.UPDATE_ATTEMPT_ID";
    public static final String EXTRA_INSTALL_SESSION_ID =
            "com.pikminx.helper.extra.UPDATE_SESSION_ID";
    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final String PREFS = "pikminx_update_install";
    private static final String STATE_KEY = "state";
    private static final String ATTEMPT_ID_KEY = "attempt_id";
    private static final String SESSION_ID_KEY = "session_id";
    private static final String MESSAGE_KEY = "message";
    private static final String ATTEMPT_SEQUENCE_KEY = "attempt_sequence";
    private static final Object STATE_LOCK = new Object();
    private static WeakReference<Callback> observer = new WeakReference<>(null);
    private static long observerGeneration;

    public interface Callback {
        void onStateChanged(InstallStateModel.Snapshot state);
    }

    private ApkUpdateManager() {}

    public static InstallStateModel.Snapshot currentState(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (STATE_LOCK) {
            return readStateLocked(appContext);
        }
    }

    public static boolean isInFlight(Context context) {
        return InstallStateModel.isInFlight(currentState(context));
    }

    /** 讓目前的 Activity 取得持久化狀態；觀察者以弱引用保存，避免 Activity 洩漏。 */
    public static void observe(Context context, Callback callback) {
        if (callback == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        final long registration;
        synchronized (STATE_LOCK) {
            observer = new WeakReference<>(callback);
            registration = ++observerGeneration;
        }
        mainHandler().post(() -> {
            synchronized (STATE_LOCK) {
                if (registration != observerGeneration || observer.get() != callback) {
                    return;
                }
            }
            callback.onStateChanged(currentState(appContext));
        });
    }

    public static void clearObserver(Callback callback) {
        synchronized (STATE_LOCK) {
            if (observer.get() == callback) {
                observer = new WeakReference<>(null);
                observerGeneration++;
            }
        }
    }

    public static void downloadAndInstall(
            Context context,
            RemoteConfigClient.Status status,
            Callback callback,
            ComponentName installStatusReceiver) {
        Context appContext = context.getApplicationContext();
        if (callback != null) {
            observe(appContext, callback);
        }

        final long attemptId;
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(appContext);
            if (InstallStateModel.isInFlight(current)) {
                notifyObserver(appContext);
                return;
            }
            SharedPreferences preferences = appContext.getSharedPreferences(
                    PREFS, Context.MODE_PRIVATE);
            attemptId = Math.max(
                    preferences.getLong(ATTEMPT_SEQUENCE_KEY, 0L), current.attemptId()) + 1L;
            writeStateLocked(appContext, InstallStateModel.start(attemptId));
        }
        notifyObserver(appContext);

        new Thread(() -> {
            File apk = null;
            try {
                apk = download(appContext, status.downloadUrl());
                if (!isCurrentAttempt(appContext, attemptId)) {
                    return;
                }
                verify(appContext, apk, status);
                if (!transitionVerified(appContext, attemptId)) {
                    return;
                }
                install(appContext, apk, attemptId, installStatusReceiver);
            } catch (Exception exception) {
                transitionFailure(appContext, attemptId, failureMessage(exception));
            } finally {
                if (apk != null && apk.exists()) {
                    apk.delete();
                }
            }
        }, "pikminx-apk-update").start();
    }

    /** 由 Receiver 呼叫；只有 matching attempt/session 的系統結果可以改變狀態。 */
    public static InstallStateModel.CallbackResult handleInstallStatus(
            Context context,
            long callbackAttemptId,
            int callbackSessionId,
            InstallStateModel.CallbackStatus callbackStatus,
            boolean confirmationAvailable,
            String message) {
        Context appContext = context.getApplicationContext();
        InstallStateModel.CallbackResult result;
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(appContext);
            long effectiveAttemptId = callbackAttemptId == InstallStateModel.NO_ATTEMPT_ID
                    ? current.attemptId()
                    : callbackAttemptId;
            result = InstallStateModel.applyCallback(
                    current,
                    effectiveAttemptId,
                    callbackSessionId,
                    callbackStatus,
                    confirmationAvailable,
                    message);
            if (result.accepted()) {
                writeStateLocked(appContext, result.state());
            }
        }
        if (result.accepted()) {
            notifyObserver(appContext);
        }
        return result;
    }

    private static File download(Context context, String downloadUrl) throws IOException {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create update directory");
        }
        File outputFile = File.createTempFile("pikminx-update-", ".apk", directory);
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("APK download HTTP " + connection.getResponseCode());
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_APK_BYTES) {
                throw new IOException("APK exceeds size limit");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                    OutputStream output = new BufferedOutputStream(
                            new FileOutputStream(outputFile))) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK_BYTES) {
                        throw new IOException("APK exceeds size limit");
                    }
                    output.write(buffer, 0, count);
                }
                if (total == 0 || (contentLength >= 0 && total != contentLength)) {
                    throw new IOException("Incomplete APK download");
                }
            }
            return outputFile;
        } catch (IOException exception) {
            outputFile.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static void verify(
            Context context, File apk, RemoteConfigClient.Status status) throws Exception {
        try (InputStream input = new BufferedInputStream(new FileInputStream(apk))) {
            if (!status.sha256().equals(sha256(input))) {
                throw new IOException("APK SHA-256 mismatch");
            }
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo archive = packageManager.getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = packageManager.getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null
                || !context.getPackageName().equals(archive.packageName)
                || archive.getLongVersionCode() != status.latestVersionCode()
                || archive.getLongVersionCode() <= installed.getLongVersionCode()) {
            throw new IOException("APK package or version mismatch");
        }
    }

    public static String sha256(InputStream input) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
        }
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private static void install(
            Context context,
            File apk,
            long attemptId,
            ComponentName installStatusReceiver) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            if (!associateSession(context, attemptId, sessionId)) {
                throw new IOException("Update attempt was superseded");
            }
            try (InputStream input = new BufferedInputStream(new FileInputStream(apk));
                    OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                session.fsync(output);
            }
            if (!isCurrentAttempt(context, attemptId)) {
                throw new IOException("Update attempt was superseded");
            }
            Intent statusIntent = new Intent(ACTION_INSTALL_STATUS)
                    .setComponent(installStatusReceiver)
                    .putExtra(EXTRA_INSTALL_ATTEMPT_ID, attemptId)
                    .putExtra(EXTRA_INSTALL_SESSION_ID, sessionId);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pendingIntent.getIntentSender());
            committed = true;
            transitionCommitted(context, attemptId, sessionId);
        } catch (IOException | RuntimeException exception) {
            if (!committed) {
                throw exception;
            }
            // The commit was submitted; a close/notification cleanup error must
            // not replace the receiver's eventual result.
        } finally {
            if (!committed) {
                try {
                    installer.abandonSession(sessionId);
                } catch (RuntimeException ignored) {
                    // The session may already have been removed by PackageInstaller.
                }
            }
        }
    }

    private static boolean associateSession(Context context, long attemptId, int sessionId) {
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(context);
            if (current.attemptId() != attemptId
                    || current.state() != InstallStateModel.State.VERIFIED) {
                return false;
            }
            writeStateLocked(context, InstallStateModel.withSession(current, sessionId));
            return true;
        }
    }

    private static boolean transitionVerified(Context context, long attemptId) {
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(context);
            if (current.attemptId() != attemptId
                    || current.state() != InstallStateModel.State.DOWNLOADING) {
                return false;
            }
            writeStateLocked(context, InstallStateModel.verified(current));
        }
        notifyObserver(context);
        return true;
    }

    private static boolean transitionCommitted(Context context, long attemptId, int sessionId) {
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(context);
            if (current.attemptId() != attemptId
                    || current.state() != InstallStateModel.State.VERIFIED
                    || current.sessionId() != sessionId) {
                return false;
            }
            writeStateLocked(context, InstallStateModel.committed(current, sessionId));
        }
        notifyObserver(context);
        return true;
    }

    private static void transitionFailure(Context context, long attemptId, String message) {
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(context);
            if (current.attemptId() != attemptId
                    || (current.state() != InstallStateModel.State.DOWNLOADING
                    && current.state() != InstallStateModel.State.VERIFIED)) {
                return;
            }
            writeStateLocked(context, InstallStateModel.failure(current, message));
        }
        notifyObserver(context);
    }

    private static boolean isCurrentAttempt(Context context, long attemptId) {
        synchronized (STATE_LOCK) {
            InstallStateModel.Snapshot current = readStateLocked(context);
            return current.attemptId() == attemptId
                    && InstallStateModel.isInFlight(current);
        }
    }

    private static InstallStateModel.Snapshot readStateLocked(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(STATE_KEY, null);
        if (value == null) {
            return InstallStateModel.idle();
        }
        try {
            InstallStateModel.State state = InstallStateModel.State.valueOf(value);
            return InstallStateModel.restore(
                    preferences.getLong(ATTEMPT_ID_KEY, InstallStateModel.NO_ATTEMPT_ID),
                    preferences.getInt(SESSION_ID_KEY, InstallStateModel.NO_SESSION_ID),
                    state,
                    preferences.getString(MESSAGE_KEY, ""));
        } catch (RuntimeException ignored) {
            return InstallStateModel.invalid(
                    preferences.getLong(ATTEMPT_ID_KEY, InstallStateModel.NO_ATTEMPT_ID),
                    preferences.getInt(SESSION_ID_KEY, InstallStateModel.NO_SESSION_ID),
                    "Invalid persisted install state");
        }
    }

    private static void writeStateLocked(
            Context context, InstallStateModel.Snapshot state) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(STATE_KEY, state.state().name())
                .putLong(ATTEMPT_ID_KEY, state.attemptId())
                .putInt(SESSION_ID_KEY, state.sessionId())
                .putString(MESSAGE_KEY, state.message())
                .putLong(ATTEMPT_SEQUENCE_KEY, Math.max(
                        preferences.getLong(ATTEMPT_SEQUENCE_KEY, 0L), state.attemptId()))
                .apply();
    }

    private static void notifyObserver(Context context) {
        Context appContext = context.getApplicationContext();
        final Callback callback;
        final long registration;
        synchronized (STATE_LOCK) {
            callback = observer.get();
            registration = observerGeneration;
        }
        if (callback == null) {
            return;
        }
        mainHandler().post(() -> {
            synchronized (STATE_LOCK) {
                if (registration != observerGeneration || observer.get() != callback) {
                    return;
                }
            }
            callback.onStateChanged(currentState(appContext));
        });
    }

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static Handler mainHandler() {
        return new Handler(Looper.getMainLooper());
    }

}
