package com.kulwant.atvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Runs in the :watchdog process. On boot, re-enables our HOME activity (in case a
 * previous crash left it disabled) and (re)starts the watchdog service so the fallback
 * logic stays active even if the launcher was never launched.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        PackageManager pm = c.getPackageManager();
        ComponentName home = new ComponentName(c, HomeActivity.class);
        boolean handedOver = c.getSharedPreferences("watchdog", Context.MODE_PRIVATE)
                .getBoolean("restore", false);
        if (handedOver) return;   // user switched back to the TV launcher on purpose
        pm.setComponentEnabledSetting(home, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        try {
            Intent s = new Intent(c, WatchdogService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(s);
            } else {
                c.startService(s);
            }
        } catch (Exception e) {
            // watchdog will be started by the launcher on first launch instead
        }
    }
}
