package com.kulwant.atvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote-friendly settings. Everything here is written to SharedPreferences and picked up by
 * HomeActivity on resume, so no adb or rebuild is needed. Reached from the gear pill on the
 * home screen and from the apps list of any other launcher (LAUNCHER category).
 */
public class LauncherSettingsActivity extends Activity {
    private static final String RESTORE = "restore";
    private static final String TV_LAUNCHER = "com.google.android.tvlauncher";

    private FrameLayout root;
    private LinearLayout list;
    private ScrollView scroll;
    private TextView greetingValue, columnsValue, wallpaperValue, rotateValue, backupValue, restoreValue;
    private boolean pickerShown;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0D);
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        showMain();
    }

    @Override
    public void onBackPressed() {
        if (pickerShown) {
            showMain();
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- main screen

    private void showMain() {
        pickerShown = false;
        root.removeAllViews();
        list = column();
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setClipToPadding(false);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        addHeader("Settings");

        greetingValue = addValueRow("Greeting name",
                "Shown after Good morning / afternoon / evening",
                greetingLabel(Prefs.greetingName(this)));
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> editGreeting());

        columnsValue = addValueRow("Apps per row", "Dock and app grid, 3 to 6",
                String.valueOf(Prefs.columns(this)));
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> {
            int next = Prefs.columns(this) + 1;
            if (next > 6) next = 3;
            Prefs.setColumns(this, next);
            columnsValue.setText(String.valueOf(next));
        });

        wallpaperValue = addValueRow("Wallpaper", "Photo behind the home screen",
                Prefs.useWallpaper(this) ? "On" : "Off");
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> {
            boolean on = !Prefs.useWallpaper(this);
            Prefs.setUseWallpaper(this, on);
            wallpaperValue.setText(on ? "On" : "Off");
        });

        rotateValue = addValueRow("Change wallpaper", "How often the photos rotate",
                rotateLabel(Prefs.wallpaperSeconds(this)));
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> {
            int secs = nextRotation(Prefs.wallpaperSeconds(this));
            Prefs.setWallpaperSeconds(this, secs);
            rotateValue.setText(rotateLabel(secs));
        });

        addActionRow("Dock apps", "Choose which apps sit in the bottom row", this::showDockPicker);
        addActionRow("Hidden apps", "Apps never shown in the app grid", this::showHiddenPicker);

        // ------------------------------------------------------------ backup home
        addHeader("Backup home screen");
        boolean other = otherHomeAvailable();
        backupValue = addValueRow("Google TV launcher",
                other ? "Ready — takes over if this launcher stops"
                        : "Disabled — enable it to have a fallback home",
                other ? "Available" : "Disabled");
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> openAppInfo(TV_LAUNCHER));

        restoreValue = addValueRow("Hand over", "Switch the TV back to the Google TV launcher",
                homeEnabled() ? "CupiDroiD Home active" : "CupiDroiD Home disabled");
        list.getChildAt(list.getChildCount() - 1).setOnClickListener(v -> toggleHandover(other));

        if (watchdogFellBack()) {
            addActionRow("Restore as home screen",
                    "The watchdog switched away after a crash — turn CupiDroiD Home back on",
                    this::restoreHome);
        }

        addHeader("Troubleshooting");
        addActionRow("System app settings", "Enable or disable installed apps",
                () -> launch(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        addActionRow("Watchdog status",
                watchdogFellBack() ? "Last launch failed; fallback used" : "Running — pings every 8 s",
                () -> Toast.makeText(this, watchdogFellBack()
                        ? "Fallback was used. Use Restore as home screen."
                        : "Launcher pings the watchdog every 8 seconds.", Toast.LENGTH_LONG).show());

        addFooter();
        scroll.post(() -> list.getChildAt(1).requestFocus());
    }

    private static String greetingLabel(String name) {
        return name == null || name.trim().isEmpty() ? "No name" : name.trim();
    }

    private void editGreeting() {
        EditText et = new EditText(this);
        et.setText(Prefs.greetingName(this));
        et.setSingleLine(true);
        et.setSelectAllOnFocus(false);
        et.setHint("No name");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(4), dp(24), 0);
        box.addView(et, new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Greeting name")
                .setMessage("Shown after Good morning / afternoon / evening.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String v = et.getText().toString().trim();
                    Prefs.setGreetingName(this, v);
                    greetingValue.setText(greetingLabel(v));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addFooter() {
        TextView t = Ui.text(this, 400, 13, 0x59FFFFFF);
        t.setText("Defaults live in config.xml. Changes here are stored on the TV.");
        t.setSingleLine(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(40);
        lp.leftMargin = dp(4);
        list.addView(t, lp);
    }

    // ---------------------------------------------------------------- pickers

    private void showDockPicker() {
        pickerShown = true;
        List<Prefs2App> apps = launchableApps();
        List<String> selected = new ArrayList<>(Prefs.dockPackages(this));
        buildPicker("Dock apps",
                "OK adds or removes an app. New apps go to the end of the row.",
                apps, selected, true, packages -> Prefs.setDockPackages(this, packages));
    }

    private void showHiddenPicker() {
        pickerShown = true;
        List<Prefs2App> apps = launchableApps();
        List<String> selected = new ArrayList<>(Prefs.hiddenPackages(this));
        buildPicker("Hidden apps",
                "OK hides or shows an app in the grid. Dock apps still show in the dock.",
                apps, selected, false, packages -> Prefs.setHiddenPackages(this, packages));
    }

    private void buildPicker(String title, String hint, List<Prefs2App> apps,
                             List<String> selected, boolean dock,
                             java.util.function.Consumer<List<String>> save) {
        root.removeAllViews();
        LinearLayout box = column();
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(box, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        addHeaderTo(box, title);
        TextView sub = Ui.text(this, 400, 14, 0x80FFFFFF);
        sub.setText(hint);
        sub.setSingleLine(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.leftMargin = dp(4);
        slp.bottomMargin = dp(12);
        box.addView(sub, slp);

        List<TextView> marks = new ArrayList<>();
        List<View> rows = new ArrayList<>();
        for (Prefs2App app : apps) {
            TextView mark = Ui.text(this, 500, 16, 0xFFFFFFFF);
            mark.setWidth(dp(28));
            mark.setText(selected.contains(app.pkg) ? "✓" : "");
            TextView label = Ui.text(this, 400, 19, 0xFFFFFFFF);
            label.setText(app.label);
            LinearLayout row = rowShell();
            row.addView(mark);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.leftMargin = dp(6);
            row.addView(label, llp);
            row.setOnClickListener(v -> {
                if (selected.contains(app.pkg)) selected.remove(app.pkg);
                else selected.add(app.pkg);
                mark.setText(selected.contains(app.pkg) ? "✓" : "");
                save.accept(new ArrayList<>(selected));
            });
            box.addView(row, rowParams());
            rows.add(row);
            marks.add(mark);
        }
        if (rows.isEmpty()) {
            TextView empty = Ui.text(this, 400, 16, 0x80FFFFFF);
            empty.setText("No launchable apps found.");
            box.addView(empty, rowParams());
        }
        scroll.post(() -> {
            if (!rows.isEmpty()) rows.get(0).requestFocus();
        });
    }

    // ---------------------------------------------------------------- home switching

    private boolean homeEnabled() {
        return getPackageManager().getComponentEnabledSetting(
                new ComponentName(this, HomeActivity.class))
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    /**
     * True when a real launcher other than this app and the system "no launcher" fallback
     * screen is enabled and resolvable.
     */
    private boolean otherHomeAvailable() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            if (getPackageName().equals(pkg)) continue;
            if ("com.android.tv.settings".equals(pkg) || "com.android.settings".equals(pkg)) continue;
            if (cls != null && cls.endsWith("FallbackHome")) continue;
            return true;
        }
        return false;
    }

    private boolean watchdogFellBack() {
        return getSharedPreferences("watchdog", MODE_PRIVATE).getBoolean("fallback", false)
                || !homeEnabled();
    }

    private void toggleHandover(boolean otherHome) {
        if (!homeEnabled()) {
            restoreHome();
            return;
        }
        if (!otherHome) {
            new AlertDialog.Builder(this)
                    .setTitle("No backup home screen")
                    .setMessage("Enable the Google TV launcher first (System app settings), "
                            + "otherwise the TV would have no home screen left.")
                    .setPositiveButton("Open app info", (d, w) -> openAppInfo(TV_LAUNCHER))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Hand over to Google TV launcher?")
                .setMessage("CupiDroiD Home turns off and the TV falls back to the Google TV "
                        + "launcher. To come back, open CupiDroiD Settings from the apps list "
                        + "and choose Restore as home screen.")
                .setPositiveButton("Hand over", (d, w) -> {
                    getSharedPreferences("watchdog", MODE_PRIVATE).edit()
                            .putBoolean(RESTORE, true).apply();
                    setHomeEnabled(false);
                    launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreHome() {
        getSharedPreferences("watchdog", MODE_PRIVATE).edit()
                .putBoolean(RESTORE, false).putBoolean("fallback", false).apply();
        setHomeEnabled(true);
        restoreValue.setText("CupiDroiD Home active");
        new AlertDialog.Builder(this)
                .setTitle("CupiDroiD Home enabled")
                .setMessage("If pressing HOME still opens the other launcher, pick CupiDroiD "
                        + "Home under Settings > Apps > Default apps.")
                .setPositiveButton("Home app settings", (d, w) -> openHomeSettings())
                .setNegativeButton("Close", (d, w) -> showMain())
                .show();
    }

    private void setHomeEnabled(boolean enabled) {
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, HomeActivity.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Use Settings > Apps > Default apps", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            launch(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        }
    }

    private void launch(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- app inventory

    private static final class Prefs2App {
        final String pkg;
        final String label;

        Prefs2App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    private List<Prefs2App> launchableApps() {
        PackageManager pm = getPackageManager();
        Map<String, Prefs2App> byPkg = new LinkedHashMap<>();
        collect(pm, byPkg, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, byPkg, Intent.CATEGORY_LAUNCHER);
        byPkg.remove(getPackageName());
        List<Prefs2App> apps = new ArrayList<>(byPkg.values());
        Collator col = Collator.getInstance();
        Collections.sort(apps, (a, b) -> col.compare(a.label, b.label));
        return apps;
    }

    private void collect(PackageManager pm, Map<String, Prefs2App> out, String category) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (out.containsKey(pkg)) continue;
            out.put(pkg, new Prefs2App(pkg, String.valueOf(ri.loadLabel(pm)).trim()));
        }
    }

    // ---------------------------------------------------------------- row builders

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(80), dp(28), dp(80), dp(28));
        return l;
    }

    private void addHeader(String text) {
        addHeaderTo(list, text);
    }

    private void addHeaderTo(LinearLayout parent, String text) {
        TextView t = Ui.text(this, 600, 30, 0xFFFFFFFF);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(4);
        lp.topMargin = dp(18);
        lp.bottomMargin = dp(10);
        parent.addView(t, lp);
    }

    private LinearLayout rowShell() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},
                Ui.rounded(0x2EFFFFFF, dp(14), dp(2), 0x80FFFFFF));
        bg.addState(new int[]{}, Ui.rounded(0x12FFFFFF, dp(14), dp(1), 0x1FFFFFFF));
        row.setBackground(bg);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        return lp;
    }

    private LinearLayout addRow(String title, String subtitle, View extra) {
        LinearLayout row = rowShell();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 500, 18, 0xFFFFFFFF);
        t.setText(title);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = Ui.text(this, 400, 13, 0x73FFFFFF);
            s.setText(subtitle);
            s.setSingleLine(false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = dp(2);
            texts.addView(s, slp);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (extra != null) row.addView(extra);
        list.addView(row, rowParams());
        return row;
    }

    private TextView addValueRow(String title, String subtitle, String value) {
        TextView v = Ui.text(this, 600, 18, 0xB3FFFFFF);
        v.setText(value);
        addRow(title, subtitle, v);
        return v;
    }

    private void addActionRow(String title, String subtitle, Runnable action) {
        addRow(title, subtitle, null).setOnClickListener(v -> action.run());
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private static String rotateLabel(int secs) {
        if (secs <= 0) return "Off";
        if (secs < 3600) return secs + " s";
        if (secs < 21600) return Math.round(secs / 3600f) + " hour" + (secs == 3600 ? "" : "s");
        return Math.round(secs / 3600f) + " hours";
    }

    private static int nextRotation(int secs) {
        if (secs <= 0) return 3600;
        if (secs < 3600) return 3600;
        if (secs < 21600) return 21600;
        if (secs < 86400) return 86400;
        return 0;
    }
}
