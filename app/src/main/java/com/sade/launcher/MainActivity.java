package com.sade.launcher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sade Launcher — Niagara tarzi duzen.
 *
 * Ana ekran: ustte saat/tarih/pil/ekran suresi, altta (basparmak hizasinda) favoriler.
 * Sag kenar: harf seridi. Parmagini uzerinde gezdir -> harfler dalga gibi kayar,
 *            tum uygulamalar o harfe atlar.
 * Yukari kaydir: tum uygulamalar + arama (klavye acik).
 * Asagi kaydir: bildirim paneli (cekmecedeyken: ana ekrana don).
 * Uzun bas (uygulama): alttan menu. Uzun bas (bos alan): ayarlar.
 * Gizli uygulamalar: sadece aramayla, 5 sn bekletmeyle acilir.
 *
 * INTERNET izni YOK.
 */
public class MainActivity extends Activity {

    // ---- Renkler
    private static final int C_BG = Color.BLACK;
    private static final int C_TEXT = Color.WHITE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_FAINT = 0xFF5C5C5C;
    private static final int C_SURFACE = 0xFF171717;
    private static final int C_SURFACE_2 = 0xFF242424;
    private static final int C_DANGER = 0xFFFF6B6B;
    private static final int C_RIPPLE = 0x22FFFFFF;

    // ---- Ayarlar
    private static final String PREFS = "sade";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_NAME_PREFIX = "name:";
    private static final String KEY_USAGE_OFF = "usage_off";
    private static final String KEY_ICONS = "icons"; // 0 kapali, 1 gri, 2 renkli
    private static final String KEY_WALLPAPER = "wallpaper"; // false siyah, true duvar kagidi
    private static final int MAX_FAVS = 7;
    private static final int HIDDEN_DELAY_SEC = 5;
    private static final int REQ_HOME = 1;
    private static final Locale TR = new Locale("tr", "TR");

    private static class App {
        final String label;
        final String labelLower;
        final ComponentName component;

        App(String label, ComponentName component) {
            this.label = label;
            this.labelLower = label.toLowerCase(TR);
            this.component = component;
        }

        String key() { return component.flattenToString(); }
        String pkg() { return component.getPackageName(); }
    }

    private static class WebRow {
        final String query;
        WebRow(String q) { query = q; }
    }

    private final List<App> allApps = new ArrayList<>();
    /** Cekmecedeki satirlar: String = harf basligi, App = uygulama, WebRow = internette ara. */
    private final List<Object> rows = new ArrayList<>();
    private final Map<String, Integer> sectionRow = new HashMap<>();
    private final List<String> sections = new ArrayList<>();
    private App firstMatch = null;
    private final List<String> favs = new ArrayList<>();
    private Set<String> hidden = new HashSet<>();
    private Map<String, Long> usageToday = new HashMap<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView setDefaultBanner;
    private LinearLayout homeView;
    private LinearLayout drawerView;
    private LinearLayout favBox;
    private FrameLayout root;
    private TextView battery;
    private TextView usageView;
    private EditText search;
    private ListView list;
    private AppAdapter adapter;
    private AlphaStrip strip;
    private TextView bubble;
    private GestureDetector gestures;
    private boolean drawerOpen = false;

    // =================================================================== arayuz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kenardan kenara cizim: sistem cubuklari seffaf, icerik altina tasmasin.
        Window win = getWindow();
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);

        root = new FrameLayout(this);
        root.setFitsSystemWindows(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(60), 0); // sagda harf seridi icin yer
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- "Ana ekran olarak ayarla" karti
        setDefaultBanner = new TextView(this);
        setDefaultBanner.setText("Sade'yi ana ekran yap");
        setDefaultBanner.setTextColor(onAccent());
        setDefaultBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        setDefaultBanner.setTypeface(font(600));
        setDefaultBanner.setGravity(Gravity.CENTER);
        setDefaultBanner.setPadding(dp(18), dp(14), dp(18), dp(14));
        setDefaultBanner.setBackground(rounded(accent(), 24));
        setDefaultBanner.setOnClickListener(v -> askToBeDefaultHome());
        LinearLayout.LayoutParams bl = matchWrap();
        bl.setMargins(0, dp(8), 0, 0);
        content.addView(setDefaultBanner, bl);

        // ================= ANA EKRAN
        homeView = new LinearLayout(this);
        homeView.setOrientation(LinearLayout.VERTICAL);
        homeView.setPadding(0, dp(40), 0, dp(20));
        homeView.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSettings();
            return true;
        });

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(accent());
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setTypeface(font(500));
        date.setPadding(dp(4), 0, 0, 0);
        date.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        homeView.addView(date, wrapWrap());

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(C_TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 84);
        clock.setTypeface(font(300));
        clock.setLetterSpacing(-0.02f);
        clock.setIncludeFontPadding(false);
        clock.setOnClickListener(v -> safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        homeView.addView(clock, wrapWrap());

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(12), 0, 0);
        battery = chip("");
        chips.addView(battery, wrapWrap());
        usageView = chip("");
        usageView.setOnClickListener(v -> onUsageClicked());
        LinearLayout.LayoutParams up = wrapWrap();
        up.setMargins(dp(8), 0, 0, 0);
        chips.addView(usageView, up);
        homeView.addView(chips, wrapWrap());

        // Bosluk: favoriler basparmak hizasina insin
        homeView.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        homeView.addView(favBox, matchWrap());

        // Alttaki arama cubugu (dokun: tum uygulamalar + klavye)
        TextView homeSearch = new TextView(this);
        homeSearch.setText("Uygulama ara");
        homeSearch.setTextColor(C_DIM);
        homeSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        homeSearch.setTypeface(font(500));
        homeSearch.setGravity(Gravity.CENTER_VERTICAL);
        homeSearch.setPadding(dp(20), 0, dp(20), 0);
        homeSearch.setCompoundDrawablesRelative(searchIcon(C_DIM), null, null, null);
        homeSearch.setCompoundDrawablePadding(dp(12));
        homeSearch.setBackground(new RippleDrawable(ColorStateList.valueOf(C_RIPPLE),
                rounded(C_SURFACE, 28), null));
        homeSearch.setOnClickListener(v -> showDrawer(true));
        LinearLayout.LayoutParams hsp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        hsp.setMargins(0, dp(20), 0, 0);
        homeView.addView(homeSearch, hsp);

        content.addView(homeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ================= CEKMECE (tum uygulamalar)
        drawerView = new LinearLayout(this);
        drawerView.setOrientation(LinearLayout.VERTICAL);
        drawerView.setPadding(0, dp(16), 0, 0);
        drawerView.setVisibility(View.GONE);

        search = new EditText(this);
        search.setHint("Uygulama ara");
        search.setHintTextColor(C_FAINT);
        search.setTextColor(C_TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTypeface(font(500));
        search.setSingleLine(true);
        search.setBackground(rounded(C_SURFACE, 28));
        search.setPadding(dp(20), 0, dp(20), 0);
        search.setCompoundDrawablesRelative(searchIcon(C_DIM), null, null, null);
        search.setCompoundDrawablePadding(dp(12));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(accent());
            cursor.setSize(dp(2), dp(22));
            search.setTextCursorDrawable(cursor);
        }
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(); }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (!go) return false;
            String q = search.getText().toString().trim();
            if (firstMatch != null) launch(firstMatch);
            else if (!q.isEmpty()) webSearch(q);
            return true;
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        sp.setMargins(0, 0, 0, dp(8));
        drawerView.addView(search, sp);

        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(24));
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Object o = rows.get(pos);
            if (o instanceof App) launch((App) o);
            else if (o instanceof WebRow) webSearch(((WebRow) o).query);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Object o = rows.get(pos);
            if (!(o instanceof App)) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showOptions((App) o);
            return true;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int state) {
                if (state != SCROLL_STATE_IDLE) hideKeyboard();
            }
            public void onScroll(AbsListView v, int a, int b, int c) {}
        });
        drawerView.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(drawerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ================= HARF SERIDI + BALON
        // Serit genis cizilir (dalga sigsin diye) ama sadece sag kenara yakin dokunuslari alir.
        strip = new AlphaStrip(this);
        root.addView(strip, new FrameLayout.LayoutParams(dp(130),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        bubble = new TextView(this);
        bubble.setTextColor(onAccent());
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        bubble.setTypeface(font(600));
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(rounded(accent(), 32));
        bubble.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.END | Gravity.TOP);
        bp.setMargins(0, 0, dp(124), 0);
        root.addView(bubble, bp);

        setContentView(root);
        applyBackground();

        // ================= KAYDIRMA HAREKETLERI
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || strip.touching) return false;
                if (e1.getX() > root.getWidth() - dp(60)) return false; // harf seridi
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < dp(80) || Math.abs(dy) < Math.abs(dx) * 1.5f) return false;
                if (!drawerOpen) {
                    if (dy < 0) showDrawer(true);
                    else expandNotifications();
                    return true;
                }
                boolean listAtTop = !list.canScrollVertically(-1);
                if (dy > 0 && listAtTop && search.getText().length() == 0) {
                    showHome();
                    return true;
                }
                return false;
            }
        });

        hidden = new HashSet<>(prefs().getStringSet(KEY_HIDDEN, new HashSet<>()));
        for (String k : prefs().getString(KEY_FAVS, "").split("\n")) if (!k.isEmpty()) favs.add(k);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ana ekrandayken Home tusuna basinca: favorilere don.
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        updateBattery();
        refreshUsage();
        setDefaultBanner.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Bir uygulamaya gidip donunce ana ekranla karsilas.
        showHomeInstant();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) showHome();
    }

    // =================================================================== ekranlar arasi gecis

    private void showDrawer(boolean withKeyboard) {
        if (!drawerOpen) {
            drawerOpen = true;
            applyBackground();
            homeView.setVisibility(View.GONE);
            drawerView.setVisibility(View.VISIBLE);
            drawerView.setAlpha(0f);
            drawerView.setTranslationY(dp(40));
            drawerView.animate().alpha(1f).translationY(0).setDuration(200).start();
            list.setSelection(0);
        }
        if (withKeyboard) {
            search.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void showHome() {
        if (!drawerOpen) return;
        showHomeInstant();
        homeView.setAlpha(0f);
        homeView.setTranslationY(-dp(24));
        homeView.animate().alpha(1f).translationY(0).setDuration(200).start();
    }

    private void showHomeInstant() {
        drawerOpen = false;
        applyBackground();
        search.setText("");
        hideKeyboard();
        drawerView.animate().cancel();
        drawerView.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
        homeView.setAlpha(1f);
        homeView.setTranslationY(0);
    }

    /** Harf seridinde parmak bir harfe gelince. */
    private void onStripLetter(String letter, float y) {
        if (search.getText().length() > 0) search.setText("");
        showDrawer(false);
        hideKeyboard();
        Integer pos = sectionRow.get(letter);
        if (pos != null) list.setSelectionFromTop(pos, 0);
        bubble.setText(letter);
        bubble.setVisibility(View.VISIBLE);
        bubble.setTranslationY(Math.max(0, y - dp(32)));
    }

    private void onStripRelease() {
        bubble.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            bubble.setVisibility(View.GONE);
            bubble.setAlpha(1f);
        }).start();
    }

    // =================================================================== veri

    private void loadApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            String original = ri.loadLabel(pm).toString().trim();
            String custom = prefs().getString(KEY_NAME_PREFIX + cn.flattenToString(), null);
            allApps.add(new App(custom != null ? custom : original, cn));
        }
        Collator c = Collator.getInstance(TR);
        allApps.sort((a, b) -> c.compare(a.label, b.label));
    }

    private App findApp(String key) {
        for (App a : allApps) if (a.key().equals(key)) return a;
        return null;
    }

    private static String sectionOf(App a) {
        if (a.label.isEmpty()) return "#";
        char ch = a.label.charAt(0);
        return Character.isLetter(ch) ? String.valueOf(ch).toUpperCase(TR) : "#";
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(TR);
        rows.clear();
        firstMatch = null;
        if (q.isEmpty()) {
            sectionRow.clear();
            sections.clear();
            String lastSection = null;
            for (App a : allApps) {
                if (hidden.contains(a.key())) continue;
                String s = sectionOf(a);
                if (!s.equals(lastSection)) {
                    sectionRow.put(s, rows.size());
                    sections.add(s);
                    rows.add(s);
                    lastSection = s;
                }
                rows.add(a);
            }
            strip.setLetters(sections);
        } else {
            List<App> starts = new ArrayList<>();
            List<App> contains = new ArrayList<>();
            for (App a : allApps) {
                if (a.labelLower.startsWith(q)) starts.add(a);
                else if (a.labelLower.contains(q)) contains.add(a);
            }
            rows.addAll(starts);
            rows.addAll(contains);
            if (!rows.isEmpty()) firstMatch = (App) rows.get(0);
            else rows.add(new WebRow(search.getText().toString().trim()));
        }
        adapter.notifyDataSetChanged();
    }

    private void renderFavorites() {
        favBox.removeAllViews();
        int shown = 0;
        for (String key : favs) {
            App app = findApp(key);
            if (app == null) continue;
            TextView tv = new TextView(this);
            tv.setText(app.label);
            tv.setTextColor(C_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setTypeface(font(400));
            tv.setMinHeight(dp(60));
            tv.setPadding(dp(8), dp(6), dp(16), dp(6));
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setSingleLine(true);
            tv.setBackground(ripple());
            setIcon(tv, app, 40);
            tv.setOnClickListener(v -> launch(app));
            tv.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showOptions(app);
                return true;
            });
            favBox.addView(tv, wrapWrap());
            shown++;
        }
        if (shown == 0) {
            TextView hint = new TextView(this);
            hint.setText("Favori eklemek için: aşağıdan ara, bir uygulamaya uzun bas.");
            hint.setTextColor(C_FAINT);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            hint.setPadding(dp(8), 0, 0, dp(8));
            favBox.addView(hint, matchWrap());
        }
    }

    private void refreshAll() {
        loadApps();
        applyFilter();
        renderFavorites();
    }

    private void saveFavs() { prefs().edit().putString(KEY_FAVS, String.join("\n", favs)).apply(); }

    private void saveHidden() { prefs().edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply(); }

    private void updateBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        battery.setText(level > 0 ? "Pil %" + level : "");
        battery.setVisibility(level > 0 ? View.VISIBLE : View.GONE);
    }

    // =================================================================== ikonlar

    private int iconMode() { return prefs().getInt(KEY_ICONS, 1); }

    private void setIcon(TextView tv, App app, int sizeDp) {
        int mode = iconMode();
        if (mode == 0) {
            tv.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        Drawable base = iconCache.get(app.key());
        if (base == null) {
            try {
                base = getPackageManager().getActivityIcon(app.component);
            } catch (Exception e) {
                base = getPackageManager().getDefaultActivityIcon();
            }
            iconCache.put(app.key(), base);
        }
        Drawable.ConstantState cs = base.getConstantState();
        Drawable d = (cs != null ? cs.newDrawable(getResources()) : base).mutate();
        if (mode == 1) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            d.setColorFilter(new ColorMatrixColorFilter(cm));
            d.setAlpha(200);
        }
        int px = dp(sizeDp);
        d.setBounds(0, 0, px, px);
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawablePadding(dp(18));
    }

    // =================================================================== ayarlar

    private void showSettings() {
        String[] iconNames = {"Kapalı", "Gri", "Renkli"};
        boolean usageOff = prefs().getBoolean(KEY_USAGE_OFF, false);
        Sheet s = new Sheet("Ayarlar", null);
        s.item("İkonlar: " + iconNames[iconMode()], false, () -> {
            prefs().edit().putInt(KEY_ICONS, (iconMode() + 1) % 3).apply();
            iconCache.clear();
            refreshAll();
            showSettings();
        });
        boolean wp = prefs().getBoolean(KEY_WALLPAPER, false);
        s.item("Arka plan: " + (wp ? "Duvar kağıdı" : "Siyah"), false, () -> {
            prefs().edit().putBoolean(KEY_WALLPAPER, !wp).apply();
            applyBackground();
            showSettings();
        });
        s.item(usageOff ? "Ekran süresini göster" : "Ekran süresini gizle", false, () -> {
            prefs().edit().putBoolean(KEY_USAGE_OFF, !usageOff).apply();
            refreshUsage();
        });
        s.item("Gizli uygulamalar (" + hiddenCount() + ")", false, this::showHiddenApps);
        s.item("Varsayılan ana ekranı değiştir", false, this::openHomeSettings);
        s.show();
    }

    private void applyBackground() {
        boolean wp = prefs().getBoolean(KEY_WALLPAPER, false);
        Window w = getWindow();
        if (wp) {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            root.setBackgroundColor(drawerOpen ? 0xEB000000 : 0x80000000);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            w.setBackgroundDrawable(new ColorDrawable(C_BG));
            root.setBackgroundColor(C_BG);
        }
    }

    private int hiddenCount() {
        int n = 0;
        for (App a : allApps) if (hidden.contains(a.key())) n++;
        return n;
    }

    private void showHiddenApps() {
        Sheet s = new Sheet("Gizli uygulamalar", "Dokun → listeye geri al");
        int n = 0;
        for (App a : allApps) {
            if (!hidden.contains(a.key())) continue;
            s.item(a.label, false, () -> {
                hidden.remove(a.key());
                saveHidden();
                refreshAll();
            });
            n++;
        }
        if (n == 0) s.row("Gizli uygulama yok", "");
        s.show();
    }

    // =================================================================== ekran suresi

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void refreshUsage() {
        if (prefs().getBoolean(KEY_USAGE_OFF, false)) {
            usageView.setVisibility(View.GONE);
            return;
        }
        usageView.setVisibility(View.VISIBLE);
        if (!hasUsageAccess()) {
            usageView.setText("Ekran süresi");
            return;
        }
        new Thread(() -> {
            Map<String, Long> m = computeUsageToday();
            ui.post(() -> {
                usageToday = m;
                long total = 0;
                for (long v : m.values()) total += v;
                usageView.setText("Bugün " + formatDuration(total));
            });
        }).start();
    }

    /** Bugun gece yarisindan beri her uygulamanin on planda kaldigi sure (ms). */
    private Map<String, Long> computeUsageToday() {
        Map<String, Long> result = new HashMap<>();
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return result;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        long now = System.currentTimeMillis();

        Map<String, Long> openSince = new HashMap<>();
        UsageEvents events = usm.queryEvents(start, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            String pkg = e.getPackageName();
            int type = e.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                openSince.put(pkg, e.getTimeStamp());
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long s = openSince.remove(pkg);
                if (s != null) result.merge(pkg, e.getTimeStamp() - s, Long::sum);
            }
        }
        // Hala acik olan (simdiki) uygulama
        for (Map.Entry<String, Long> en : openSince.entrySet()) {
            if (now - en.getValue() < 6 * 3600_000L) {
                result.merge(en.getKey(), now - en.getValue(), Long::sum);
            }
        }
        result.remove(getPackageName()); // launcher'da gecen sure sayilmaz
        return result;
    }

    private static String formatDuration(long ms) {
        long min = ms / 60000;
        if (min < 1) return "1 dk'dan az";
        long h = min / 60;
        long m = min % 60;
        if (h == 0) return m + " dk";
        return m == 0 ? h + " sa" : h + " sa " + m + " dk";
    }

    private void onUsageClicked() {
        if (hasUsageAccess()) {
            showUsageBreakdown();
            return;
        }
        Sheet s = new Sheet("Ekran süresi",
                "Bugün telefonda ne kadar zaman geçirdiğini saatin altında gösterir. "
                        + "Bunun için \"Kullanım erişimi\" izni gerekiyor. Veri telefondan çıkmaz.");
        s.item("İzin ver", false, () -> safeStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        s.item("Gösterme", false, () -> {
            prefs().edit().putBoolean(KEY_USAGE_OFF, true).apply();
            refreshUsage();
        });
        s.show();
    }

    private void showUsageBreakdown() {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(usageToday.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Sheet s = new Sheet("Bugün", null);
        int n = 0;
        for (Map.Entry<String, Long> en : entries) {
            if (en.getValue() < 60000) continue;
            String name = en.getKey();
            for (App a : allApps) if (a.pkg().equals(en.getKey())) { name = a.label; break; }
            s.row(name, formatDuration(en.getValue()));
            if (++n >= 8) break;
        }
        if (n == 0) s.row("Henüz kayda değer bir kullanım yok", "");
        s.item("Gizle", false, () -> {
            prefs().edit().putBoolean(KEY_USAGE_OFF, true).apply();
            refreshUsage();
        });
        s.show();
    }

    // =================================================================== eylemler

    private void launch(App app) {
        if (hidden.contains(app.key())) confirmHiddenLaunch(app);
        else doLaunch(app);
    }

    private void doLaunch(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(app.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            refreshAll();
        }
    }

    /** Gizli uygulamayi acmadan once 5 saniye bekletir. */
    private void confirmHiddenLaunch(App app) {
        Long used = usageToday.get(app.pkg());
        String msg = "Bunu gizlemiştin. Gerçekten açmak istiyor musun?";
        if (used != null && used >= 60000) msg += "\nBugün zaten " + formatDuration(used) + " kullandın.";
        Sheet s = new Sheet(app.label, msg);
        TextView open = s.item("Aç (" + HIDDEN_DELAY_SEC + ")", false, () -> doLaunch(app));
        s.item("Vazgeç", false, () -> { });
        open.setEnabled(false);
        open.setAlpha(0.35f);
        final int[] left = {HIDDEN_DELAY_SEC};
        ui.postDelayed(new Runnable() {
            public void run() {
                if (!s.dialog.isShowing()) return;
                left[0]--;
                if (left[0] <= 0) {
                    open.setText("Aç");
                    open.setEnabled(true);
                    open.setAlpha(1f);
                    return;
                }
                open.setText("Aç (" + left[0] + ")");
                ui.postDelayed(this, 1000);
            }
        }, 1000);
        s.show();
    }

    private void showOptions(App app) {
        boolean isFav = favs.contains(app.key());
        boolean isHidden = hidden.contains(app.key());
        Long used = usageToday.get(app.pkg());
        String sub = (used != null && used >= 60000) ? "Bugün " + formatDuration(used) : null;

        Sheet s = new Sheet(app.label, sub);
        s.item(isFav ? "Favorilerden çıkar" : "Favorilere ekle", false, () -> toggleFav(app));
        s.item("Yeniden adlandır", false, () -> rename(app));
        s.item(isHidden ? "Listeye geri al" : "Gizle  ·  sadece aramayla açılır", false, () -> {
            if (isHidden) {
                hidden.remove(app.key());
            } else {
                hidden.add(app.key());
                favs.remove(app.key());
                saveFavs();
            }
            saveHidden();
            refreshAll();
        });
        s.item("Uygulama bilgisi", false, () -> safeStart(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg()))));
        s.item("Kaldır", true, () -> safeStart(new Intent(
                Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg()))));
        s.show();
    }

    private void toggleFav(App app) {
        if (favs.contains(app.key())) {
            favs.remove(app.key());
        } else {
            if (favs.size() >= MAX_FAVS) {
                Sheet s = new Sheet("Favoriler dolu",
                        "En fazla " + MAX_FAVS + " favori olabilir. Sade kalsın :)");
                s.item("Tamam", false, () -> { });
                s.show();
                return;
            }
            favs.add(app.key());
        }
        saveFavs();
        renderFavorites();
    }

    private void rename(App app) {
        Sheet s = new Sheet("Yeniden adlandır", "Boş bırakırsan orijinal adına döner.");
        EditText input = new EditText(this);
        input.setText(app.label);
        input.setTextColor(C_TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        input.setSingleLine(true);
        input.setBackground(rounded(C_SURFACE_2, 14));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(dp(8), dp(4), dp(8), dp(8));
        s.box.addView(input, lp);
        s.item("Kaydet", false, () -> {
            String n = input.getText().toString().trim();
            String key = KEY_NAME_PREFIX + app.key();
            if (n.isEmpty()) prefs().edit().remove(key).apply();
            else prefs().edit().putString(key, n).apply();
            refreshAll();
        });
        s.dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        s.show();
        input.requestFocus();
    }

    private void webSearch(String q) {
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra(SearchManager.QUERY, q);
        try {
            startActivity(i);
        } catch (Exception e) {
            safeStart(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))));
        }
    }

    @SuppressWarnings({"WrongConstant", "JavaReflectionMemberAccess"})
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) { }
    }

    private void safeStart(Intent i) {
        try { startActivity(i); } catch (Exception ignored) { }
    }

    // =================================================================== alttan acilan menu

    /** Koyu, koseleri yuvarlak, alttan kayarak acilan menu. */
    private class Sheet {
        final Dialog dialog;
        final LinearLayout box;

        Sheet(String title, String subtitle) {
            dialog = new Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(16), dp(10), dp(16), dp(28));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(C_SURFACE);
            float r = dp(28);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            box.setBackground(bg);

            View handle = new View(MainActivity.this);
            handle.setBackground(rounded(0xFF3A3A3A, 3));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
            hp.gravity = Gravity.CENTER_HORIZONTAL;
            hp.setMargins(0, 0, 0, dp(16));
            box.addView(handle, hp);

            TextView t = new TextView(MainActivity.this);
            t.setText(title);
            t.setTextColor(C_TEXT);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            t.setTypeface(font(600));
            t.setPadding(dp(8), 0, dp(8), subtitle == null ? dp(12) : dp(4));
            box.addView(t, matchWrap());

            if (subtitle != null) {
                TextView st = new TextView(MainActivity.this);
                st.setText(subtitle);
                st.setTextColor(C_DIM);
                st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                st.setLineSpacing(0, 1.2f);
                st.setPadding(dp(8), 0, dp(8), dp(14));
                box.addView(st, matchWrap());
            }

            dialog.setContentView(box);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.BOTTOM);
                w.setDimAmount(0.65f);
                w.setWindowAnimations(android.R.style.Animation_InputMethod);
            }
        }

        TextView item(String text, boolean danger, Runnable action) {
            TextView tv = new TextView(MainActivity.this);
            tv.setText(text);
            tv.setTextColor(danger ? C_DANGER : C_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setTypeface(font(500));
            tv.setPadding(dp(14), dp(16), dp(14), dp(16));
            tv.setBackground(ripple());
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                action.run();
            });
            box.addView(tv, matchWrap());
            return tv;
        }

        void row(String left, String right) {
            LinearLayout r = new LinearLayout(MainActivity.this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setPadding(dp(8), dp(9), dp(8), dp(9));
            TextView l = new TextView(MainActivity.this);
            l.setText(left);
            l.setTextColor(C_TEXT);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            l.setSingleLine(true);
            r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView rt = new TextView(MainActivity.this);
            rt.setText(right);
            rt.setTextColor(C_DIM);
            rt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            r.addView(rt, wrapWrap());
            box.addView(r, matchWrap());
        }

        void show() { dialog.show(); }
    }

    // =================================================================== varsayilan ana ekran

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null
                && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void askToBeDefaultHome() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(
                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                }
            } catch (Exception ignored) { }
        }
        openHomeSettings();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            safeStart(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME && !isDefaultHome()) openHomeSettings();
    }

    // =================================================================== yardimcilar

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(C_RIPPLE), null, rounded(Color.WHITE, 12));
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private Typeface font(int weight) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(Typeface.DEFAULT, weight, false);
        }
        return weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    /** Telefonun kendi tema rengi (Material You). Eski Android'de yumusak mavi. */
    private int accent() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return getColor(android.R.color.system_accent1_200);
        }
        return 0xFFA8C7FA;
    }

    /** Vurgu renginin ustune yazilacak koyu renk. */
    private int onAccent() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return getColor(android.R.color.system_accent1_900);
        }
        return 0xFF0B1D36;
    }

    private TextView chip(String text) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextColor(C_DIM);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        c.setTypeface(font(500));
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        c.setBackground(rounded(C_SURFACE, 14));
        return c;
    }

    /** Basit, keskin bir buyutec ikonu (eski sistem ikonu yerine). */
    private class SearchIcon extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        SearchIcon(int color) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = b.width();
            float r = s * 0.30f;
            float cx = b.left + s * 0.42f;
            float cy = b.top + s * 0.42f;
            c.drawCircle(cx, cy, r, p);
            float o = r * 0.72f;
            c.drawLine(cx + o, cy + o, b.left + s * 0.88f, b.top + s * 0.88f, p);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private Drawable searchIcon(int color) {
        Drawable d = new SearchIcon(color);
        d.setBounds(0, 0, dp(20), dp(20));
        return d;
    }

    // =================================================================== liste

    private class AppAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_APP = 1;
        private static final int TYPE_WEB = 2;

        public int getCount() { return rows.size(); }
        public Object getItem(int p) { return rows.get(p); }
        public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int p) {
            Object o = rows.get(p);
            return o instanceof App ? TYPE_APP : (o instanceof WebRow ? TYPE_WEB : TYPE_HEADER);
        }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { return !(rows.get(p) instanceof String); }

        public View getView(int pos, View convert, ViewGroup parent) {
            Object o = rows.get(pos);
            TextView tv = (TextView) convert;
            if (o instanceof App) {
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(C_TEXT);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    tv.setTypeface(font(400));
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setMinHeight(dp(56));
                    tv.setPadding(dp(8), dp(6), dp(8), dp(6));
                    tv.setSingleLine(true);
                    tv.setBackground(ripple());
                }
                App a = (App) o;
                tv.setText(a.label);
                setIcon(tv, a, 36);
                tv.setAlpha(hidden.contains(a.key()) ? 0.4f : 1f);
            } else if (o instanceof WebRow) {
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(accent());
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tv.setTypeface(font(500));
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setMinHeight(dp(56));
                    tv.setPadding(dp(8), dp(6), dp(8), dp(6));
                    tv.setBackground(ripple());
                    tv.setCompoundDrawablesRelative(searchIcon(accent()), null, null, null);
                    tv.setCompoundDrawablePadding(dp(16));
                }
                tv.setText("İnternette ara: “" + ((WebRow) o).query + "”");
            } else {
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(accent());
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tv.setTypeface(font(600));
                    tv.setPadding(dp(8), dp(20), dp(8), dp(6));
                }
                tv.setText((String) o);
            }
            return tv;
        }
    }

    // =================================================================== harf seridi (dalga efekti)

    /**
     * Sag kenardaki harf seridi. Gorunum 130dp genis ama sadece sagdaki ~60dp'lik
     * bolgeye dokunulunca calisir; geri kalaninda dokunuslar alttaki listeye gecer.
     * Parmak uzerinde gezerken yakindaki harfler sola dogru kayip buyur (dalga).
     */
    private class AlphaStrip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<String> letters = new ArrayList<>();
        private float touchY = -1;
        boolean touching = false;
        private int currentIndex = -1;
        private float waveProgress = 0f;
        private android.animation.ValueAnimator anim;

        AlphaStrip(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(font(600));
        }

        void setLetters(List<String> l) {
            letters.clear();
            letters.addAll(l);
            requestLayout();
            invalidate();
        }

        /** Harflerin yatay merkezi: ekran kenarindan 30dp iceride (geri hareketinden uzak). */
        private float baseX() { return getWidth() - dp(30); }
        private float touchZoneLeft() { return getWidth() - dp(60); }

        private float areaTop() { return dp(96); }
        private float areaBottom() { return getHeight() - dp(96); }

        private float step() {
            if (letters.isEmpty()) return 0;
            return Math.min(dp(20), (areaBottom() - areaTop()) / letters.size());
        }

        private float startY() {
            float total = step() * letters.size();
            return areaTop() + ((areaBottom() - areaTop()) - total) / 2f;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            // Android'in "kenardan kaydir = geri" hareketi seridi bozmasin.
            if (android.os.Build.VERSION.SDK_INT >= 29 && !letters.isEmpty()) {
                List<Rect> ex = new ArrayList<>();
                int y0 = (int) startY();
                int y1 = (int) (startY() + step() * letters.size());
                ex.add(new Rect((int) touchZoneLeft(), y0, getWidth(), y1));
                setSystemGestureExclusionRects(ex);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (letters.isEmpty()) return;
            float step = step();
            float y0 = startY();
            float bx = baseX();
            float reach = dp(120);
            int acc = accent();
            for (int i = 0; i < letters.size(); i++) {
                float cy = y0 + step * i + step / 2f;
                float k = 0f;
                if (waveProgress > 0f && touchY >= 0) {
                    float d = Math.abs(cy - touchY);
                    if (d < reach) k = (float) ((Math.cos(Math.PI * d / reach) + 1) / 2) * waveProgress;
                }
                float size = sp(11) * (1f + 0.8f * k);
                paint.setTextSize(size);
                boolean selected = touching && i == currentIndex;
                paint.setColor(selected ? acc : blend(C_FAINT, C_TEXT, k * 0.8f));
                float x = bx - k * dp(56); // en fazla 56dp sola: 130dp icinde rahat sigar
                canvas.drawText(letters.get(i), x, cy + size / 3f, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (letters.isEmpty()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (e.getX() < touchZoneLeft()) return false; // listeye birak
                    touching = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    animateWave(1f);
                    // fallthrough
                case MotionEvent.ACTION_MOVE: {
                    if (!touching) return false;
                    touchY = e.getY();
                    int idx = (int) ((touchY - startY()) / step());
                    idx = Math.max(0, Math.min(letters.size() - 1, idx));
                    if (idx != currentIndex) {
                        currentIndex = idx;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        onStripLetter(letters.get(idx), touchY);
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!touching) return false;
                    touching = false;
                    currentIndex = -1;
                    animateWave(0f);
                    onStripRelease();
                    return true;
            }
            return false;
        }

        private void animateWave(float target) {
            if (anim != null) anim.cancel();
            anim = android.animation.ValueAnimator.ofFloat(waveProgress, target);
            anim.setDuration(180);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                waveProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        private float sp(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                    getResources().getDisplayMetrics());
        }

        private int blend(int a, int b, float t) {
            int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
            int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
            int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
            return Color.rgb(r, g, bl);
        }
    }
}
