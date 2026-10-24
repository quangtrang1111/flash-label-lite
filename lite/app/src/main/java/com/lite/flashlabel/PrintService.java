package com.lite.flashlabel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.List;

/**
 * Foreground service that runs the print job, so printing keeps going when the app is in the
 * background or the screen is off. Shows progress in a notification; cancelling is only possible
 * from the progress dialog inside the app.
 *
 * The print screen talks to it through static methods: {@link #start}, {@link #cancel},
 * {@link #isRunning}, and a listener it attaches while visible. A result that arrives while the
 * screen is away is kept until {@link #takeResult} picks it up.
 */
public class PrintService extends Service {
    private static final String CHANNEL_PROGRESS = "printing";
    /** Channel of the old "finished" notification; deleted on start so it doesn't linger in settings. */
    private static final String OLD_CHANNEL_DONE = "print_done";
    private static final int NOTIFY_PROGRESS = 1;
    private static final long WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L;

    /** Outcome of a finished job, kept until the print screen reads it. */
    static final class Result {
        final boolean printed;
        final String message;

        Result(boolean printed, String message) {
            this.printed = printed;
            this.message = message;
        }
    }

    // Shared state (main thread only).
    private static PrintJob job;
    private static PrintJob pending;
    private static PrintJob.Listener uiListener;
    private static String lastMessage = Strings.PRINTING;
    private static Result result;

    private PowerManager.WakeLock wakeLock;

    /** Starts printing {@code items} in the foreground service. */
    static void start(Context c, List<PrintJob.Item> items, Prefs.Settings settings) {
        result = null;
        lastMessage = Strings.PRINTING;
        pending = new PrintJob(items, settings, new PrintJob.Listener() {
            @Override
            public void onProgress(String message) {
                lastMessage = message;
                if (uiListener != null) uiListener.onProgress(message);
                PrintService s = instance;
                if (s != null) s.showProgress(message);
            }

            @Override
            public void onFinished(boolean printed, String message) {
                job = null;
                PrintService s = instance;
                if (uiListener != null) {
                    uiListener.onFinished(printed, message);
                } else {
                    result = new Result(printed, message);
                }
                if (s != null) s.finish();
            }
        });
        job = pending;
        Intent i = new Intent(c, PrintService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void cancel() {
        if (job != null) job.cancel();
    }

    static boolean isRunning() {
        return job != null;
    }

    static String lastMessage() {
        return lastMessage;
    }

    /** Listener for the visible print screen; pass null when it goes away. */
    static void setListener(PrintJob.Listener l) {
        uiListener = l;
    }

    /** Returns and clears the result of a job that finished while no screen was listening. */
    static Result takeResult() {
        Result r = result;
        result = null;
        return r;
    }

    private static PrintService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = progressNotification(lastMessage);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFY_PROGRESS, n);
        }
        PrintJob toRun = pending;
        pending = null;
        if (toRun == null) {
            // Nothing to do (e.g. restarted by the system): stop right away.
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlashLabelLite:print");
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        toRun.start();
        return START_NOT_STICKY;
    }

    private void finish() {
        releaseWakeLock();
        stopForegroundCompat(); // removes the "printing" notification; no separate "done" notification
        stopSelf();
    }

    private void showProgress(String message) {
        notificationManager().notify(NOTIFY_PROGRESS, progressNotification(message));
    }

    private Notification progressNotification(String message) {
        Notification.Builder b = builder(CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(Strings.PRINTING)
                .setContentText(message)
                .setContentIntent(openApp())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true);
        return b.build();
    }

    private PendingIntent openApp() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = notificationManager();
        NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS, Strings.PRINTING, NotificationManager.IMPORTANCE_LOW);
        progress.setShowBadge(false);
        nm.createNotificationChannel(progress);
        nm.deleteNotificationChannel(OLD_CHANNEL_DONE);
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
