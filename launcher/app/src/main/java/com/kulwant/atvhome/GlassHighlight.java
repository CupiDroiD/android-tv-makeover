package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The single liquid-glass focus highlight. Drawn directly so moving it never triggers
 * a layout pass. The focused region of the live wallpaper is re-projected through a
 * 1/8-scale blur layer (see GlassBackdrop), then tinted with glass shading: a soft
 * vertical body tint, a top sheen, an inner specular edge and a thin rim.
 */
final class GlassHighlight extends View {
    private static final Shader.TileMode CLAMP = Shader.TileMode.CLAMP;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF shade = new RectF();
    private final Matrix matrix = new Matrix();
    private Path path;
    private float radius;
    private GlassBackdrop backdrop;
    private int surfaceW, surfaceH;

    GlassHighlight(Context c) {
        super(c);
        fill.setColor(0x2EFFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(c, 1.5f));
        rim.setColor(0x70FFFFFF);
    }

    void setBackdrop(GlassBackdrop b) {
        backdrop = b;
        invalidate();
    }

    /** Root surface size the wallpaper sits behind (root FrameLayout is W x H). */
    void setSurface(int w, int h) {
        surfaceW = w;
        surfaceH = h;
        invalidate();
    }

    void set(RectF r, float radius) {
        rect.set(r);
        this.radius = radius;
        setupShaders();
        invalidate();
    }

    /** Gradient shaders depend on the focus geometry, so rebuild them when it moves. */
    private void setupShaders() {
        shade.set(rect);
        tint.setShader(new LinearGradient(0, shade.top, 0, shade.bottom,
                0x24FFFFFF, 0x30101420, CLAMP));
        float sheenTop = shade.top, sheenBottom = shade.top + shade.height() * 0.55f;
        sheen.setShader(new LinearGradient(0, sheenTop, 0, sheenBottom,
                new int[]{0x73FFFFFF, 0x14FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, CLAMP));
        float[] rimPts = {0.5f, shade.top, 0.5f, shade.bottom};
        int[] rimCols = {0xCCFFFFFF, 0x40FFFFFF, 0x18FFFFFF, 0x66FFFFFF};
        rim.setShader(new LinearGradient(rimPts[0], rimPts[1], rimPts[2], rimPts[3], rimCols, null, CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (rect.isEmpty()) return;
        boolean glassOn = backdrop != null && backdrop.has() && surfaceW > 0;
        if (glassOn && path == null) path = new Path();

        int sc = c.save();
        if (glassOn) {
            path.reset();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            c.clipPath(path);
            Bitmap b = backdrop.get();
            backdrop.project(matrix, surfaceW, surfaceH);
            // the highlight canvas scrolls with `content`; the wallpaper stays fixed in the
            // root surface, so the projection must cancel the parent's translation
            android.view.View parent = (android.view.View) getParent();
            matrix.postTranslate(-parent.getTranslationX(), -parent.getTranslationY());
            c.drawBitmap(b, matrix, glass);
            c.drawRect(rect, tint);
            c.drawRect(rect, sheen);
        } else {
            c.drawRoundRect(rect, radius, radius, fill);
        }
        c.restoreToCount(sc);

        float h = rim.getStrokeWidth() / 2f;
        c.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, radius, radius, rim);
    }
}
