package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;

/**
 * The Prime Video style wallpaper treatment: the photo stays sharp in one big region of the
 * screen but fades into blur towards both screen edges — so the buttons up top and the app
 * rows below always sit on a calm background, whatever photo is on screen. One texture isn't
 * blurred in two resolutions here: the sharp wallpaper image is always drawn fully, and on
 * top this view draws the small blurred layer shared with the glass surfaces (GlassBackdrop),
 * masked offscreen with a vertical alpha gradient: opaque at the screen edges, transparent
 * across the sharp region. Re-composed once per wallpaper change at 1/8 resolution, so every
 * draw is a single bitmap blit.
 */
final class WallpaperVeil extends View {

    private final GlassBackdrop backdrop;

    // where the sharp region starts and ends, and how far the blur ramp runs (per-mille of height)
    private final float top, bottom, fade;
    private final Matrix m = new Matrix();
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
    private final PorterDuffXfermode dstIn = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private Bitmap composed;

    WallpaperVeil(Context c, GlassBackdrop backdrop, int top, int bottom, int fade) {
        super(c);
        this.backdrop = backdrop;
        this.top = clamp(top, 200, 900) / 1000f;
        this.bottom = clamp(bottom, 50, 950) / 1000f;
        this.fade = clamp(fade, 20, 500) / 1000f;
    }

    private static float clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Call on the main thread after each GlassBackdrop rebuild. */
    void refresh() {
        Bitmap coarse = backdrop.get();
        if (coarse == null) {
            if (composed != null) {
                composed = null;
                invalidate();
            }
            return;
        }
        if (coarse == composed) return;
        int w = coarse.getWidth(), h = coarse.getHeight();

        // opaque black/transparent black gradient: full blur over the top and bottom bands,
        // none across the sharp region. Positions must be ascending and within [0..1].
        float[] want = {0f, top - fade, top, bottom, bottom + fade, 1f};
        float[] pos = new float[6];
        pos[0] = 0f;
        for (int i = 1; i < 6; i++) pos[i] = Math.min(1f, Math.max(pos[i - 1] + 0.004f, want[i]));
        LinearGradient g = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF000000, 0xFF000000, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000},
                pos, Shader.TileMode.CLAMP);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cs = new Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint();
        cs.drawBitmap(coarse, 0, 0, p);
        p.setShader(g);
        p.setXfermode(dstIn);   // keep the blur layer only where the gradient is opaque
        cs.drawRect(0, 0, w, h, p);
        composed = out;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap b = composed;
        if (b == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // same CENTER_CROP maths as the sharp wallpaper behind and GlassBackdrop.project
        float scale = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
        m.reset();
        m.setScale(scale, scale);
        m.postTranslate((w - b.getWidth() * scale) / 2f, (h - b.getHeight() * scale) / 2f);
        canvas.drawBitmap(b, m, paint);
    }
}
