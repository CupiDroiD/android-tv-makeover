package com.kulwant.atvhome;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Main-process side of the watchdog: starts the :watchdog service and pings it every few
 * seconds while the launcher process is alive. WatchdogService lives in :watchdog so it
 * keeps running even if the launcher crashes.
 */
public class AtvHomeApplication extends Application {
    private volatile boolean running = true;

    @Override public void onCreate() {
        super.onCreate();
        if (!isMainProcess()) return;
        try {
            Intent s = new Intent(this, WatchdogService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(s);
            } else {
                startService(s);
            }
        } catch (Exception e) {
            Log.w("Watchdog", "could not start watchdog service", e);
        }
        new Thread(heartbeat).start();
    }

    /**
     * WatchdogService runs in :watchdog, which also loads this Application class. Only the
     * main process may start the service and send heartbeats. /proc/self/cmdline is NUL
     * padded, so getProcessName() is the reliable check (API 28+).
     */
    private boolean isMainProcess() {
        try {
            return getPackageName().equals(getProcessName());
        } catch (Exception e) {
            return true;
        }
    }

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            while (running) {
                sendBroadcast(new Intent(WatchdogService.ACTION_PING));
                try { Thread.sleep(8000); } catch (InterruptedException e) { running = false; }
            }
        }
    };
}
