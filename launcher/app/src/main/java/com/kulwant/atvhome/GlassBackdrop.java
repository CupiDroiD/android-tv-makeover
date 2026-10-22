package com.kulwant.atvhome;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

/**
 * The blur layer behind glass surfaces: the live wallpaper downsampled to 1/8 scale.
 * Android 9/11 (API 28/30) has no RenderEffect, so glass re-projects this cheap layer
 * instead of a real realtime blur. One 160x90-ish RGB_565 bitmap per wallpaper,
 * rebuilt on the executor after each wallpaper rotation; field swaps are atomic.
 */
final class GlassBackdrop {
    private static final int DIV = 8;

    private final ImageView[] views;
    private volatile Bitmap source, coarse;

    GlassBackdrop(ImageView... views) {
        this.views = views;
    }

    private static Bitmap bitmapOf(ImageView v) {
        if (v == null) return null;
        android.graphics.drawable.Drawable d = v.getDrawable();
        return d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
    }

    /** Runs on the background executor after a wallpaper change (and again once the fade settles). */
    void rebuild() {
        ImageView best = null;
        float bestAlpha = -1;
        for (ImageView v : views) {
            Bitmap src = bitmapOf(v);
            if (src == null) continue;
            float a = v.getAlpha();
            // the brighter layer matches what the screen shows most of the fade
            if (a > bestAlpha) {
                bestAlpha = a;
                best = v;
            }
        }
        if (best == null) return;
        Bitmap src = bitmapOf(best);
        if (src == null || src == source) return;
        int w = Math.max(1, src.getWidth() / DIV), h = Math.max(1, src.getHeight() / DIV);
        Bitmap c = Bitmap.createScaledBitmap(src, w, h, true);
        source = src;
        coarse = c;
    }

    /** true while a blur layer is available to sample. */
    boolean has() {
        return coarse != null;
    }

    Bitmap get() {
        return coarse;
    }

    /**
     * Fills m with the mapping that places the coarse bitmap exactly where the
     * CENTER_CROP wallpaper sits behind an (or a whole root-surface) view w x h.
     */
    void project(Matrix m, int w, int h) {
        Bitmap cs = coarse;
        if (cs == null) return;
        float scale = Math.max((float) w / cs.getWidth(), (float) h / cs.getHeight());
        m.setScale(scale, scale);
        m.postTranslate((w - cs.getWidth() * scale) / 2f, (h - cs.getHeight() * scale) / 2f);
    }

    /** Clear when wallpapers are switched off so glass falls back to the plain look. */
    void clear() {
        coarse = null;
        source = null;
    }
}
