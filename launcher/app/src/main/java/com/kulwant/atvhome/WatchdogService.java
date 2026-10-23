package com.kulwant.atvhome;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Lives in its own process (:watchdog) so it survives a launcher crash. The launcher
 * pings it every few seconds; if pings stop, this service disables our own HOME activity
 * so the system falls back to the next home activity (Google TV launcher / PatchWall /
 * whatever the TV resolves). A boot receiver re-enables our HOME activity on every boot.
 */
public class WatchdogService extends Service {
    private static final String TAG = "Watchdog";
    static final String ACTION_PING = "com.kulwant.atvhome.WATCHDOG_PING";
    private static final int NOTIFICATION_ID = 2;   // PhoneService owns id 1
    private static final long GRACE_MS = 60000;   // let the launcher boot at least once
    private static final long MISS_MS = 45000;    // silent this long = treat as dead
    private static final long CHECK_MS = 5000;

    private final BroadcastReceiver pingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ACTION_PING.equals(i.getAction())) lastPing = System.currentTimeMillis();
        }
    };

    private volatile long startTime = 0;
    private volatile long lastPing = 0;
    private boolean fallingBack = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        startTime = System.currentTimeMillis();
        registerReceiver(pingReceiver, new IntentFilter(ACTION_PING));
        handler.post(check);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, makeNotification());
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(pingReceiver); } catch (Exception ignored) {}
        handler.removeCallbacks(check);
        super.onDestroy();
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (!fallingBack && lastPing != 0 && now - startTime > GRACE_MS
                    && now - lastPing > MISS_MS) {
                fallingBack = true;
                Log.w(TAG, "launcher silent; falling back to system home");
                disableOurHome();
            }
            handler.postDelayed(check, CHECK_MS);
        }
    };

    private void disableOurHome() {
        ComponentName home = new ComponentName(this, HomeActivity.class);
        getPackageManager().setComponentEnabledSetting(
                home, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        getSharedPreferences("watchdog", MODE_PRIVATE).edit()
                .putBoolean("fallback", true).apply();
    }

    private android.app.Notification makeNotification() {
        String channel = "watchdog";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new android.app.NotificationChannel(
                    channel, "Launcher watchdog", android.app.NotificationManager.IMPORTANCE_LOW));
        }
        return new android.app.Notification.Builder(this, channel)
                .setContentTitle("CupiDroiD Home watchdog")
                .setContentText("Falls back to the system home screen if the launcher stops.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }
}
