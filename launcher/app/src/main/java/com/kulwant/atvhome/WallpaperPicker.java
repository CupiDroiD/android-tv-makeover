package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * D-pad wallpaper chooser, opened from the top-right "Wallpaper" button: a dim full-screen
 * panel with a grid of thumbnails. OK shows the selected photo right away, BACK closes.
 */
final class WallpaperPicker {

    interface Listener {
        void pick(int index);
    }

    final FrameLayout panel;
    private final Context c;
    private final LinearLayout cellList;
    private final TextView status;
    private final List<View> tiles = new ArrayList<>();
    private List<Object> sources = new ArrayList<>();
    private Listener listener;
    private ExecutorService io;
    private int sel;
    private boolean loading;

    WallpaperPicker(Context c) {
        this.c = c;
        panel = new FrameLayout(c);
        panel.setBackground(new android.graphics.drawable.ColorDrawable(0xB8000000));
        panel.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        panel.addView(box, frame(Ui.dp(c, 42), Ui.dp(c, 36), Ui.dp(c, 42), Ui.dp(c, 36)));

        TextView title = Ui.text(c, 500, 22, 0xF2FFFFFF);
        title.setText("Wallpapers");
        box.addView(title, new LinearLayout.LayoutParams(-2, -2));

        status = Ui.text(c, 400, 14, 0x80FFFFFF);
        status.setText("Choose a photo, then press OK to show it now");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(c, 6);
        box.addView(status, slp);

        cellList = new LinearLayout(c);
        cellList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -1);
        clp.topMargin = Ui.dp(c, 18);
        box.addView(cellList, clp);
    }

    void setExecutor(ExecutorService io) {
        this.io = io;
    }

    boolean isOpen() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** Rebuilds the panel from the current photo list; thumbnails are decoded in the background. */
    void open(List<Object> srcs, Listener l) {
        if (loading || isOpen()) return;
        listener = l;
        sources = srcs == null ? new ArrayList<>() : new ArrayList<>(srcs);
        cellList.removeAllViews();
        tiles.clear();
        sel = 0;
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(140).start();
        if (sources.isEmpty()) {
            status.setText("No wallpapers yet — push one from your phone, or add one to /sdcard/Wallpapers");
            return;
        }

        // decode all thumbnails off the main thread, then build the grid at once
        loading = true;
        status.setText("Loading…");
        final int cols = 6;
        final int cw = (c.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 84) - (cols - 1) * Ui.dp(c, 14)) / cols;
        final int ch = cw * 9 / 16;
        final List<List<Bitmap>> rows = new ArrayList<>();
        io.execute(() -> {
            for (int r = 0; r * cols < sources.size(); r++) {
                List<Bitmap> row = new ArrayList<>();
                for (int i = r * cols; i < sources.size() && i < (r + 1) * cols; i++) {
                    row.add(thumb(sources.get(i), cw, ch));
                }
                rows.add(row);
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                loading = false;
                status.setText("Choose a photo, then press OK to show it now");
                if (!isOpen()) return;
                for (List<Bitmap> row : rows) {
                    LinearLayout line = new LinearLayout(c);
                    cellList.addView(line, new LinearLayout.LayoutParams(-2, -2));
                    for (Bitmap b : row) {
                        FrameLayout tile = new FrameLayout(c);
                        tile.setBackground(Ui.rounded(0xFF282830, Ui.dp(c, 10), 0, 0));
                        tile.setForeground(Ui.rounded(0, Ui.dp(c, 10), 0, 0));
                        tile.setClipToOutline(true);
                        if (b != null) {
                            ImageView iv = new ImageView(c);
                            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            iv.setImageBitmap(b);
                            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                        }
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, ch);
                        lp.setMargins(0, 0, Ui.dp(c, 14), Ui.dp(c, 14));
                        line.addView(tile, lp);
                        tiles.add(tile);
                    }
                }
                if (sel >= tiles.size()) sel = 0;
                paint();
            });
        });
    }

    private static FrameLayout.LayoutParams frame(int l, int t, int r, int b) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void paint() {
        for (int i = 0; i < tiles.size(); i++) {
            boolean here = i == sel;
            FrameLayout tile = (FrameLayout) tiles.get(i);
            tile.setAlpha(here ? 1f : 0.6f);
            GradientDrawable ring = (GradientDrawable) tile.getForeground();
            ring.setStroke(Ui.dp(c, here ? 2 : 0), Color.WHITE);
        }
    }

    void move(int d) {
        int n = Math.max(0, Math.min(sources.size() - 1, sel + d));
        if (n == sel) return;
        sel = n;
        paint();
    }

    void choose() {
        if (!tiles.isEmpty() && listener != null) listener.pick(sel);
        close();
    }

    void close() {
        panel.animate().alpha(0f).setDuration(110).withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    /** Small downsampled sample of one photo, sized for its cell. */
    private static Bitmap thumb(Object src, int maxW, int maxH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        decode(src, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return decode(src, o);
    }

    private static Bitmap decode(Object src, BitmapFactory.Options o) {
        if (src instanceof Integer) throw new IllegalArgumentException("resource wallpapers not supported here");
        return BitmapFactory.decodeFile(((java.io.File) src).getPath(), o);
    }
}
