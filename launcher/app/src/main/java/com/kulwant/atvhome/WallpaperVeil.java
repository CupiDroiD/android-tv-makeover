package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The Prime Video style wallpaper treatment, in two steps:
 *
 * 1. A light blur layer (the /8 coarse texture shared with the glass surfaces) is masked by a
 *    vertical ramp: opaque over the screen edges, transparent across the sharp middle region.
 * 2. A heavy blur layer (the same texture downsampled further) is masked to thin bands that
 *    hug the very top and bottom edges, so the blur strengthens towards the edges instead of
 *    being one flat fade. Android 9/11 has no RenderEffect, so "more blur" is done by
 *    downsampling further, not by a bigger kernel.
 *
 * Optionally, feathered "frost spots" (rounded rects softened with a BlurMaskFilter) are
 * unioned into the light mask, so the clock block and the pill row always sit on frosted
 * glass even when the sharp region passes under them; the wallpaper keeps its photo look
 * everywhere else. Re-composed once per wallpaper change at 1/8 resolution: every frame is
 * then a single bitmap blit.
 */
final class WallpaperVeil extends View {

    private static final int OPAQUE = 0xFF000000;
    private static final int CLEAR = 0x00000000;

    private final GlassBackdrop backdrop;

    // where the sharp region starts and ends, and how far the light ramp runs (per-mille)
    private final float top, bottom, fade;
    private final boolean progressive, spotFrost;
    private final int featherPx;

    private final Matrix m = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final PorterDuffXfermode dstIn = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private Bitmap composed;

    // screen-space rects that must always sit on frost, sent in after first layout
    private RectF[] spots, applied;
    private float spotW, spotH;

    WallpaperVeil(Context c, GlassBackdrop backdrop, int top, int bottom, int fade,
                  boolean progressive, boolean spotFrost, int featherDp) {
        super(c);
        this.backdrop = backdrop;
        this.top = clamp(top, 200, 900) / 1000f;
        this.bottom = clamp(bottom, 50, 950) / 1000f;
        this.fade = clamp(fade, 20, 500) / 1000f;
        this.progressive = progressive;
        this.spotFrost = spotFrost;
        this.featherPx = Math.max(0, featherDp);
    }

    private static float clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Gradient stops must ascend inside [0..1]; nudge them apart when values collapse. */
    private static float[] ascending(float[] want) {
        float[] pos = new float[want.length];
        pos[0] = 0f;
        for (int i = 1; i < want.length; i++) {
            pos[i] = Math.min(1f, Math.max(pos[i - 1] + 0.004f, want[i]));
        }
        return pos;
    }

    /**
     * Blur these screen rects (frosted panels) no matter what the mask ramp does there.
     * Call on the main thread after the first layout pass.
     */
    void setSpots(RectF[] screenRects) {
        // the same rects arrive on every focus tick while the content isn't scrolling: motion
        // under 1 px is invisible, so skip the whole re-composition in that case
        int n = screenRects == null ? 0 : screenRects.length;
        int m = applied == null ? 0 : applied.length;
        boolean same = n == m && spotW == getWidth() && spotH == getHeight();
        if (same) {
            for (int i = 0; i < n && same; i++) {
                RectF a = screenRects[i], b = applied[i];
                same = Math.abs(a.left - b.left) < 1f && Math.abs(a.top - b.top) < 1f;
            }
        }
        if (same) {
            spots = screenRects;
            return;
        }
        spots = screenRects;
        spotW = getWidth();
        spotH = getHeight();
        if (composed != null) refresh();
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

        // 1. light layer masked by the wide ramp: full blur at the screen edges, gone across
        //    the sharp region. Positions must ascend within [0..1].
        LinearGradient light = new LinearGradient(0, 0, 0, h,
                new int[]{OPAQUE, CLEAR, CLEAR, OPAQUE},
                ascending(new float[]{top - fade, top, bottom, bottom + fade}),
                Shader.TileMode.CLAMP);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas co = new Canvas(out);
        co.drawBitmap(coarse, 0, 0, paint);

        // mask 1: the ramp, plus optional frosted spots behind the clock and the pill row
        Bitmap m1 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cm = new Canvas(m1);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setShader(light);
        cm.drawRect(0, 0, w, h, mp);
        mp.setShader(null);
        if (spotFrost && spots != null && spotW > 0) {
            // screen -> texture-local, the inverse of the CENTER_CROP maths in onDraw()
            float s = Math.max(spotW / (float) w, spotH / (float) h);
            float offX = (spotW - w * s) / 2f, offY = (spotH - h * s) / 2f;
            mp.setColor(OPAQUE);
            if (featherPx > 0) {
                mp.setMaskFilter(new BlurMaskFilter(featherPx / s, BlurMaskFilter.Blur.NORMAL));
            }
            for (RectF r : spots) {
                if (r == null) continue;
                cm.drawRoundRect(
                        (r.left - offX) / s, (r.top - offY) / s,
                        (r.right - offX) / s, (r.bottom - offY) / s,
                        featherPx / s, featherPx / s, mp);
            }
            mp.setMaskFilter(null);
        }
        paint.setXfermode(dstIn);
        co.drawBitmap(m1, 0, 0, paint);
        paint.setXfermode(null);

        // 2. heavy layer, downsampled further, masked to thin bands hugging the screen edges
        if (progressive) {
            int hw = Math.max(1, w / 4), hh = Math.max(1, h / 4);
            Bitmap heavy = Bitmap.createScaledBitmap(coarse, hw, hh, true);
            Bitmap t2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c2 = new Canvas(t2);
            c2.drawBitmap(heavy, null, new RectF(0, 0, w, h), paint);
            // top: fully blurred until most of the ramp has passed, gone just before "top";
            // bottom: arrives later and stays at full strength into the very edge corner
            LinearGradient thick = new LinearGradient(0, 0, 0, h,
                    new int[]{OPAQUE, CLEAR, CLEAR, OPAQUE},
                    ascending(new float[]{top - fade * 0.55f, top - fade * 0.10f,
                            bottom + fade * 0.25f, bottom + fade * 0.85f}),
                    Shader.TileMode.CLAMP);
            Paint g2 = new Paint();
            g2.setShader(thick);
            g2.setXfermode(dstIn);
            c2.drawRect(0, 0, w, h, g2);
            co.drawBitmap(t2, 0, 0, paint);
        }

        composed = out;
        applied = spotFrost && spots != null && spotW > 0 ? spots : applied;
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
