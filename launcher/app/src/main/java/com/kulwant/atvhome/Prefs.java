package com.kulwant.atvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settings the TV user can change without a PC. Defaults come from config.xml, overrides
 * are stored in the "settings" SharedPreferences and win when present.
 */
final class Prefs {
    private static final String FILE = "settings";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- greeting

    static String greetingName(Context c) {
        String v = sp(c).getString("greeting_name", null);
        return v != null ? v : c.getString(R.string.greeting_name);
    }

    static void setGreetingName(Context c, String v) {
        sp(c).edit().putString("greeting_name", v.trim()).apply();
        bump(c);
    }

    // ---------------------------------------------------------------- layout

    static int columns(Context c) {
        return clamp(sp(c).getInt("columns", c.getResources().getInteger(R.integer.columns)), 3, 6);
    }

    static void setColumns(Context c, int v) {
        sp(c).edit().putInt("columns", clamp(v, 3, 6)).apply();
        bump(c);
    }

    // ---------------------------------------------------------------- wallpaper

    static boolean useWallpaper(Context c) {
        return sp(c).getBoolean("use_wallpaper", c.getResources().getBoolean(R.bool.use_wallpaper));
    }

    static void setUseWallpaper(Context c, boolean v) {
        sp(c).edit().putBoolean("use_wallpaper", v).apply();
        bump(c);
    }

    /** Seconds between wallpaper changes; 0 means never rotate. */
    static int wallpaperSeconds(Context c) {
        return Math.max(0, sp(c).getInt("wallpaper_seconds",
                c.getResources().getInteger(R.integer.wallpaper_seconds)));
    }

    static void setWallpaperSeconds(Context c, int v) {
        sp(c).edit().putInt("wallpaper_seconds", Math.max(0, v)).apply();
    }

    // ---------------------------------------------------------------- app lists

    static List<String> dockPackages(Context c) {
        return list(c, "dock", R.array.dock_packages);
    }

    static void setDockPackages(Context c, List<String> packages) {
        putList(c, "dock", packages);
        bump(c);
    }

    static List<String> hiddenPackages(Context c) {
        return list(c, "hidden", R.array.hidden_packages);
    }

    static void setHiddenPackages(Context c, List<String> packages) {
        putList(c, "hidden", packages);
        bump(c);
    }

    private static List<String> list(Context c, String key, int array) {
        String raw = sp(c).getString(key, null);
        if (raw == null) return new ArrayList<>(Arrays.asList(c.getResources().getStringArray(array)));
        List<String> out = new ArrayList<>();
        for (String p : raw.split("\n")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    private static void putList(Context c, String key, List<String> packages) {
        sp(c).edit().putString(key, android.text.TextUtils.join("\n", packages)).apply();
    }

    // ---------------------------------------------------------------- change tracking

    /** HomeActivity recreates itself when this number changes (layout depends on settings). */
    static int version(Context c) {
        return sp(c).getInt("version", 0);
    }

    private static void bump(Context c) {
        sp(c).edit().putInt("version", version(c) + 1).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
