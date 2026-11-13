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
import android.graphics.Color;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
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
import java.util.function.IntConsumer;

/**
 * Sade Launcher
 *
 * Gorunum: siyah, ince yazilar, ikon yok, rozet yok.
 * - Ustte saat (dokun: alarmlar), tarih (dokun: takvim), pil, bugunku ekran suresi.
 * - Favoriler buyuk yaziyla (en fazla 7).
 * - Tum uygulamalar harf basliklariyla alfabetik.
 * - Asagi kaydir: bildirim paneli.
 * - Uzun bas: alttan acilan menu (favori, yeniden adlandir, gizle, bilgi, kaldir)
 *   + o uygulamada bugun ne kadar zaman gecirdigin.
 * - Gizli uygulamalar sadece aramayla cikar, acmadan once 5 saniye bekletir.
 *
 * Izinler: sadece EXPAND_STATUS_BAR (bildirim panelini acmak icin) ve istege bagli
 * kullanim erisimi (ekran suresi icin, sen acarsan). INTERNET izni YOK.
 */
public class MainActivity extends Activity {

    // ---- Renkler
    private static final int C_BG = Color.BLACK;
    private static final int C_TEXT = Color.WHITE;
    private static final int C_DIM = 0xFF8A8A8A;
    private static final int C_FAINT = 0xFF4A4A4A;
    private static final int C_SURFACE = 0xFF141414;
    private static final int C_SURFACE_2 = 0xFF1E1E1E;
    private static final int C_DANGER = 0xFFFF6B6B;
    private static final int C_RIPPLE = 0x22FFFFFF;

    // ---- Ayarlar
    private static final String PREFS = "sade";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_NAME_PREFIX = "name:";
    private static final String KEY_USAGE_OFF = "usage_off";
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
    /** Listede gosterilen satirlar: String = harf basligi, App = uygulama. */
    private final List<Object> rows = new ArrayList<>();
    private App firstMatch = null;
    private final List<String> favs = new ArrayList<>();
    private Set<String> hidden = new HashSet<>();
    private Map<String, Long> usageToday = new HashMap<>();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView setDefaultBanner;
    private LinearLayout header;
    private LinearLayout favBox;
    private TextView battery;
    private TextView usageView;
    private EditText search;
    private ListView list;
    private AppAdapter adapter;
    private GestureDetector gestures;

    // =================================================================== arayuz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(28), dp(12), dp(28), 0);

        // ---- "Ana ekran olarak ayarla" seridi
        setDefaultBanner = new TextView(this);
        setDefaultBanner.setText("Ana ekran olarak ayarla  →");
        setDefaultBanner.setTextColor(Color.BLACK);
        setDefaultBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        setDefaultBanner.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setDefaultBanner.setPadding(dp(18), dp(14), dp(18), dp(14));
        setDefaultBanner.setBackground(rounded(Color.WHITE, 16));
        setDefaultBanner.setOnClickListener(v -> askToBeDefaultHome());
        root.addView(setDefaultBanner, matchWrap());

        // ---- Ust kisim (arama yapinca kaybolur)
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(40), 0, 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(C_TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 76);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setOnClickListener(v -> safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        header.addView(clock, wrapWrap());

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(4), dp(6), 0, 0);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("d MMMM EEEE");
        date.setFormat12Hour("d MMMM EEEE");
        date.setTextColor(C_DIM);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        infoRow.addView(date, wrapWrap());

        battery = new TextView(this);
        battery.setTextColor(C_DIM);
        battery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        battery.setPadding(dp(10), 0, 0, 0);
        infoRow.addView(battery, wrapWrap());
        header.addView(infoRow, wrapWrap());

        usageView = new TextView(this);
        usageView.setTextColor(C_FAINT);
        usageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        usageView.setPadding(dp(4), dp(4), 0, 0);
        usageView.setOnClickListener(v -> onUsageClicked());
        header.addView(usageView, wrapWrap());

        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        favBox.setPadding(0, dp(40), 0, dp(20));
        header.addView(favBox, matchWrap());

        root.addView(header, matchWrap());

        // ---- Arama kutusu (yuvarlak, koyu)
        search = new EditText(this);
        search.setHint("Ara");
        search.setHintTextColor(C_FAINT);
        search.setTextColor(C_TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        search.setSingleLine(true);
        search.setBackground(rounded(C_SURFACE, 26));
        search.setPadding(dp(20), dp(14), dp(20), dp(14));
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                setHeaderVisible(s.length() == 0);
                applyFilter();
            }
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
        LinearLayout.LayoutParams sp = matchWrap();
        sp.setMargins(0, 0, 0, dp(8));
        root.addView(search, sp);

        // ---- Tum uygulamalar
        list = new ListView(this);
        list.setDivider(null);
        list.setFastScrollEnabled(true);
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
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        // ---- Asagi kaydir: bildirim paneli
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                boolean listAtTop = !list.canScrollVertically(-1);
                if (dy > dp(80) && Math.abs(dy) > Math.abs(dx) * 1.5f && listAtTop) {
                    expandNotifications();
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
    protected void onResume() {
        super.onResume();
        search.setText("");
        hideKeyboard();
        list.setSelection(0);
        refreshAll();
        updateBattery();
        refreshUsage();
        setDefaultBanner.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        search.setText("");
        hideKeyboard();
        list.smoothScrollToPosition(0);
    }

    private void setHeaderVisible(boolean show) {
        if (show && header.getVisibility() != View.VISIBLE) {
            header.setAlpha(0f);
            header.setVisibility(View.VISIBLE);
            header.animate().alpha(1f).setDuration(180).start();
        } else if (!show && header.getVisibility() == View.VISIBLE) {
            header.animate().cancel();
            header.setVisibility(View.GONE);
        }
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
            String lastSection = null;
            for (App a : allApps) {
                if (hidden.contains(a.key())) continue;
                String s = sectionOf(a);
                if (!s.equals(lastSection)) { rows.add(s); lastSection = s; }
                rows.add(a);
            }
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
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            tv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            tv.setPadding(dp(4), dp(7), dp(4), dp(7));
            tv.setSingleLine(true);
            tv.setBackground(ripple());
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
            hint.setText("Bir uygulamaya uzun bas → Favorilere ekle");
            hint.setTextColor(C_FAINT);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            hint.setPadding(dp(4), 0, 0, 0);
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
        battery.setText(level > 0 ? "·  %" + level : "");
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
            usageView.setText("Ekran süresini göster");
            return;
        }
        new Thread(() -> {
            Map<String, Long> m = computeUsageToday();
            ui.post(() -> {
                usageToday = m;
                long total = 0;
                for (long v : m.values()) total += v;
                usageView.setText("Bugün ekranda " + formatDuration(total));
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
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tv.setPadding(dp(8), dp(15), dp(8), dp(15));
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
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    tv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setPadding(dp(4), dp(11), dp(4), dp(11));
                    tv.setSingleLine(true);
                    tv.setBackground(ripple());
                }
                App a = (App) o;
                tv.setText(a.label);
                tv.setAlpha(hidden.contains(a.key()) ? 0.4f : 1f);
            } else if (o instanceof WebRow) {
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(C_DIM);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    tv.setPadding(dp(4), dp(14), dp(4), dp(14));
                    tv.setBackground(ripple());
                }
                tv.setText("İnternette ara: \u201C" + ((WebRow) o).query + "\u201D  \u2192");
            } else {
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(C_FAINT);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    tv.setLetterSpacing(0.1f);
                    tv.setPadding(dp(4), dp(18), dp(4), dp(4));
                }
                tv.setText((String) o);
            }
            return tv;
        }
    }
}
