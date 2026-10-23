package com.kulwant.atvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every launchable app with a search box. The TV remote can't type, so the field is filled by the
 * phone page's keyboard (see KeyInjector): the TV's soft keyboard never opens here. Focus is driven
 * by this activity, not by Android, so the D-pad always moves exactly one tile.
 */
public class AllAppsActivity extends Activity {

    private enum Zone { SEARCH, GRID }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<AppRepo.App> all = new ArrayList<>();
    private final List<AppRepo.App> shown = new ArrayList<>();

    private int W, H, side, COLS, cardW, cardH, rowH, gridTop;
    private FrameLayout root;
    private EditText search;
    private TextView count, empty;
    private RecyclerView rv;
    private TileAdapter adapter;
    private View ring;

    private Zone zone = Zone.SEARCH;
    private int idx;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        setContentView(root);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        io.execute(() -> {
            List<AppRepo.App> apps = AppRepo.all(getApplicationContext());
            main.post(() -> {
                loaded = true;
                all.clear();
                all.addAll(apps);
                filter();
                if (zone == Zone.SEARCH) search.requestFocus();
            });
        });
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private FrameLayout.LayoutParams at(int w, int h, int x, int y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private static final class SearchField extends EditText {
        SearchField(Activity a) {
            super(a);
        }

        /** No soft keyboard: the TV's IME would eat the first BACK, and can't be driven by a D-pad. */
        @Override
        public boolean onCheckIsTextEditor() {
            return false;
        }
    }

    private void buildUi() {
        W = getResources().getDisplayMetrics().widthPixels;
        H = getResources().getDisplayMetrics().heightPixels;
        side = dp(40);
        COLS = Prefs.columns(this);

        int gap = dp(24);
        int cell = (W - 2 * side + gap) / COLS;
        cardW = cell - gap;
        cardH = cardW * 9 / 16;
        rowH = cardH + dp(40);
        gridTop = dp(178);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0C);
        root.setClipChildren(false);

        FrameLayout header = new FrameLayout(this);
        TextView title = Ui.text(this, 550, 27, 0xFFFFFFFF);
        title.setText("All apps");
        title.setLetterSpacing(-0.01f);
        header.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        count = Ui.text(this, 400, 14, 0x80FFFFFF);
        count.setText("Loading\u2026");
        header.addView(count, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(header, at(W - 2 * side, dp(44), side, dp(30)));

        search = new SearchField(this);
        search.setSingleLine(true);
        search.setTypeface(Ui.font(this, 400));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0x80FFFFFF);
        search.setHint("Search apps");
        search.setBackground(Ui.rounded(0x1FFFFFFF, dp(22), dp(1), 0x26FFFFFF));
        search.setPadding(dp(20), 0, dp(20), 0);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setShowSoftInputOnFocus(false);   // the phone page's keyboard is the one that types
        search.setFocusableInTouchMode(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(search, at(W - 2 * side, dp(46), side, dp(82)));

        empty = Ui.text(this, 400, 18, 0x99FFFFFF);
        empty.setVisibility(View.GONE);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        elp.topMargin = dp(40);
        root.addView(empty, elp);

        adapter = new TileAdapter(cell, cardW, cardH, rowH - cardH, true);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, COLS));
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        rv.setClipChildren(false);
        rv.setClipToPadding(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rv.setNestedScrollingEnabled(false);
        rv.setPadding(0, dp(6), 0, dp(24));
        root.addView(rv, at(cell * COLS, H - gridTop, side - (cell - cardW) / 2, gridTop));

        ring = new View(this);
        ring.setBackground(Ui.rounded(0, dp(15), Math.max(2, dp(2)), 0xF2FFFFFF));
        ring.setVisibility(View.INVISIBLE);
        root.addView(ring, at(dp(10), dp(10), 0, 0));
    }

    private void filter() {
        String raw = search.getText().toString();
        String q = raw.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppRepo.App a : all) {
            if (q.isEmpty()
                    || (a.label != null && a.label.toLowerCase(Locale.ROOT).contains(q))
                    || (a.pkg != null && a.pkg.toLowerCase(Locale.ROOT).contains(q))) {
                shown.add(a);
            }
        }
        adapter.setApps(shown);
        count.setText(!loaded ? "Loading\u2026"
                : shown.size() + (shown.size() == 1 ? " app" : " apps"));
        boolean none = loaded && shown.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) empty.setText("No apps match \u201C" + raw.trim() + "\u201D");
        if (idx >= shown.size()) idx = Math.max(0, shown.size() - 1);
        applyFocus(false);
    }

    private void applyFocus(boolean animate) {
        adapter.setFocused(rv, zone == Zone.GRID ? idx : -1);
        if (zone != Zone.GRID) {
            ring.setVisibility(View.INVISIBLE);
            return;
        }
        if (animate) rv.smoothScrollToPosition(idx);
        else rv.scrollToPosition(idx);
        placeRing(3);
    }

    /** The ring sits over the focused tile; the holder may not exist yet right after a scroll. */
    private void placeRing(int tries) {
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(idx);
        if (!(vh instanceof TileAdapter.Holder)) {
            if (tries > 0) rv.postDelayed(() -> placeRing(tries - 1), 40);
            else ring.setVisibility(View.INVISIBLE);
            return;
        }
        View card = vh.itemView.findViewById(R.id.card);
        float cx = rv.getLeft() + vh.itemView.getLeft() + card.getLeft() + card.getWidth() / 2f;
        float cy = rv.getTop() + vh.itemView.getTop() + card.getTop() + card.getHeight() / 2f;
        float hw = card.getWidth() * TileAdapter.FOCUS_SCALE / 2f + dp(6);
        float hh = card.getHeight() * TileAdapter.FOCUS_SCALE / 2f + dp(6);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ring.getLayoutParams();
        lp.width = Math.round(hw * 2);
        lp.height = Math.round(hh * 2);
        lp.leftMargin = Math.round(cx - hw);
        lp.topMargin = Math.round(cy - hh);
        ring.setLayoutParams(lp);
        ring.setVisibility(View.VISIBLE);
    }

    private void focusSearch() {
        zone = Zone.SEARCH;
        adapter.setFocused(rv, -1);
        ring.setVisibility(View.INVISIBLE);
        search.requestFocus();
    }

    private void launch(AppRepo.App a) {
        try {
            startActivity(AppRepo.launchIntent(getPackageManager(), a));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BACK;
        if (!nav) {
            // typing while a tile is focused jumps to the field, so search is one key away
            if (zone != Zone.SEARCH && e.getAction() == KeyEvent.ACTION_DOWN && isTyping(e)) {
                focusSearch();
            }
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;

        boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;

        if (zone == Zone.SEARCH) {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!shown.isEmpty()) {
                    zone = Zone.GRID;
                    idx = 0;
                    search.clearFocus();
                    applyFocus(true);
                }
                return true;
            }
            if (select) {
                if (e.getRepeatCount() == 0 && !shown.isEmpty()) launch(shown.get(0));
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) {
                if (search.getText().length() > 0) search.setText("");
                else finish();
                return true;
            }
            return super.dispatchKeyEvent(e);   // left/right move the caret
        }

        int n = shown.size();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (idx >= COLS) {
                    idx -= COLS;
                    applyFocus(true);
                } else {
                    focusSearch();
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (idx + COLS < n) {
                    idx += COLS;
                    applyFocus(true);
                } else if (idx / COLS < (n - 1) / COLS) {
                    idx = n - 1;
                    applyFocus(true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (idx % COLS > 0) {
                    idx--;
                    applyFocus(true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (idx % COLS < COLS - 1 && idx + 1 < n) {
                    idx++;
                    applyFocus(true);
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                finish();
                break;
            default:
                if (e.getRepeatCount() == 0 && idx < n) launch(shown.get(idx));
        }
        return true;
    }

    private static boolean isTyping(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_DEL) return true;
        return e.getUnicodeChar() != 0;
    }
}
