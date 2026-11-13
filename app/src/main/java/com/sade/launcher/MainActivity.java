package com.sade.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sade Launcher
 * - Siyah arka plan, beyaz yazi, ikon yok, rozet yok.
 * - Ustte saat, tarih, pil. Saate dokun: alarmlar. Tarihe dokun: takvim.
 * - Favoriler: saatin altinda buyuk yaziyla (en fazla 7).
 * - Uzun bas: Favori / Yeniden adlandir / Gizle / Bilgi / Kaldir.
 * - Gizli uygulamalar sadece aramayla bulunur ve acmadan once 5 saniye bekletir.
 * - Aramada sonuc yoksa "Git" -> internette arar.
 * - Hicbir izin yok, internet yok, takip yok.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "sade";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_NAME_PREFIX = "name:";
    private static final int MAX_FAVS = 7;
    private static final int HIDDEN_DELAY_SEC = 5;
    private static final int REQ_HOME = 1;
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
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

        String key() {
            return component.flattenToString();
        }
    }

    private final List<App> allApps = new ArrayList<>();
    private final List<App> visible = new ArrayList<>();
    private final List<String> favs = new ArrayList<>();
    private Set<String> hidden = new HashSet<>();

    private TextView setDefaultBanner;
    private LinearLayout header;
    private LinearLayout favBox;
    private TextView battery;
    private EditText search;
    private AppAdapter adapter;

    // ------------------------------------------------------------------ arayuz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);
        int pad = dp(24);
        root.setPadding(pad, dp(16), pad, 0);

        // Varsayilan ana ekran degilsek ustte gorunen uyari.
        setDefaultBanner = new TextView(this);
        setDefaultBanner.setText("Ana ekran olarak ayarla →");
        setDefaultBanner.setTextColor(Color.BLACK);
        setDefaultBanner.setBackgroundColor(Color.WHITE);
        setDefaultBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        setDefaultBanner.setPadding(dp(16), dp(14), dp(16), dp(14));
        setDefaultBanner.setOnClickListener(v -> askToBeDefaultHome());
        root.addView(setDefaultBanner, matchWrap());

        // ---- Ust kisim: saat, tarih, pil, favoriler (arama yapinca gizlenir)
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(24), 0, 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setOnClickListener(v -> safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        header.addView(clock, wrapWrap());

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(Color.parseColor("#888888"));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        dateRow.addView(date, wrapWrap());

        battery = new TextView(this);
        battery.setTextColor(Color.parseColor("#888888"));
        battery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        battery.setPadding(dp(12), 0, 0, 0);
        dateRow.addView(battery, wrapWrap());
        header.addView(dateRow, wrapWrap());

        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        favBox.setPadding(0, dp(32), 0, dp(16));
        header.addView(favBox, matchWrap());

        root.addView(header, matchWrap());

        // ---- Arama kutusu
        search = new EditText(this);
        search.setHint("ara");
        search.setHintTextColor(Color.parseColor("#555555"));
        search.setTextColor(Color.WHITE);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        search.setSingleLine(true);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(0, dp(12), 0, dp(12));
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                header.setVisibility(s.length() > 0 ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
        // Klavyede "Git": ilk sonucu ac, sonuc yoksa internette ara.
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (!go) return false;
            String q = search.getText().toString().trim();
            if (!visible.isEmpty()) launch(visible.get(0));
            else if (!q.isEmpty()) webSearch(q);
            return true;
        });
        root.addView(search, matchWrap());

        // ---- Tum uygulamalar listesi
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setFastScrollEnabled(true);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(android.R.color.transparent);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> launch(visible.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            showOptions(visible.get(pos));
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

        hidden = new HashSet<>(prefs().getStringSet(KEY_HIDDEN, new HashSet<>()));
        String favStr = prefs().getString(KEY_FAVS, "");
        for (String k : favStr.split("\n")) if (!k.isEmpty()) favs.add(k);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ana ekrana her donuste: aramayi temizle, listeyi yenile.
        search.setText("");
        hideKeyboard();
        loadApps();
        applyFilter();
        renderFavorites();
        updateBattery();
        setDefaultBanner.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        search.setText("");
        hideKeyboard();
    }

    // ------------------------------------------------------------------ veri

    private void loadApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : infos) {
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

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(TR);
        visible.clear();
        for (App a : allApps) {
            boolean isHidden = hidden.contains(a.key());
            if (q.isEmpty()) {
                if (!isHidden) visible.add(a);
            } else if (a.labelLower.contains(q)) {
                visible.add(a); // gizliler aramada cikar: yasak degil, zahmetli
            }
        }
        if (!q.isEmpty()) {
            visible.sort((a, b) -> Boolean.compare(
                    !a.labelLower.startsWith(q), !b.labelLower.startsWith(q)));
        }
        adapter.notifyDataSetChanged();
    }

    private void renderFavorites() {
        favBox.removeAllViews();
        int shown = 0;
        for (String key : favs) {
            App app = findApp(key);
            if (app == null) continue; // kaldirilmis uygulama
            TextView tv = new TextView(this);
            tv.setText(app.label);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            tv.setPadding(0, dp(8), 0, dp(8));
            tv.setSingleLine(true);
            tv.setOnClickListener(v -> launch(app));
            tv.setOnLongClickListener(v -> { showOptions(app); return true; });
            favBox.addView(tv, matchWrap());
            shown++;
        }
        if (shown == 0) {
            TextView hint = new TextView(this);
            hint.setText("Bir uygulamaya uzun bas → Favorilere ekle");
            hint.setTextColor(Color.parseColor("#444444"));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            favBox.addView(hint, matchWrap());
        }
    }

    private void saveFavs() {
        prefs().edit().putString(KEY_FAVS, String.join("\n", favs)).apply();
    }

    private void saveHidden() {
        prefs().edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    private void refreshAll() {
        loadApps();
        applyFilter();
        renderFavorites();
    }

    private void updateBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        battery.setText(level > 0 ? "%" + level : "");
    }

    // ------------------------------------------------------------------ eylemler

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
        AlertDialog d = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(app.label)
                .setMessage("Bunu gizlemiştin. Gerçekten açmak istiyor musun?")
                .setPositiveButton("Aç", (x, w) -> doLaunch(app))
                .setNegativeButton("Vazgeç", null)
                .create();
        d.setOnShowListener(x -> {
            Button b = d.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setEnabled(false);
            Handler h = new Handler(Looper.getMainLooper());
            final int[] left = {HIDDEN_DELAY_SEC};
            h.post(new Runnable() {
                public void run() {
                    if (!d.isShowing()) return;
                    if (left[0] <= 0) {
                        b.setText("Aç");
                        b.setEnabled(true);
                        return;
                    }
                    b.setText("Aç (" + left[0] + ")");
                    left[0]--;
                    h.postDelayed(this, 1000);
                }
            });
        });
        d.show();
    }

    private void showOptions(App app) {
        boolean isFav = favs.contains(app.key());
        boolean isHidden = hidden.contains(app.key());
        String[] items = {
                isFav ? "Favorilerden çıkar" : "Favorilere ekle",
                "Yeniden adlandır",
                isHidden ? "Listeye geri al" : "Gizle (sadece aramayla açılır)",
                "Uygulama bilgisi",
                "Kaldır"
        };
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(app.label)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: toggleFav(app); break;
                        case 1: rename(app); break;
                        case 2:
                            if (isHidden) hidden.remove(app.key());
                            else { hidden.add(app.key()); favs.remove(app.key()); saveFavs(); }
                            saveHidden();
                            refreshAll();
                            break;
                        case 3:
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + app.component.getPackageName())));
                            break;
                        default:
                            safeStart(new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + app.component.getPackageName())));
                    }
                })
                .show();
    }

    private void toggleFav(App app) {
        if (favs.contains(app.key())) {
            favs.remove(app.key());
        } else {
            if (favs.size() >= MAX_FAVS) {
                new AlertDialog.Builder(this, DIALOG_THEME)
                        .setMessage("En fazla " + MAX_FAVS + " favori. Sade kalsın :)")
                        .setPositiveButton("Tamam", null)
                        .show();
                return;
            }
            favs.add(app.key());
        }
        saveFavs();
        renderFavorites();
    }

    private void rename(App app) {
        EditText input = new EditText(this);
        input.setText(app.label);
        input.setSingleLine(true);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Yeniden adlandır")
                .setMessage("Boş bırakırsan orijinal adına döner.")
                .setView(input)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String n = input.getText().toString().trim();
                    String key = KEY_NAME_PREFIX + app.key();
                    if (n.isEmpty()) prefs().edit().remove(key).apply();
                    else prefs().edit().putString(key, n).apply();
                    refreshAll();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
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

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ varsayilan ana ekran

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

    // ------------------------------------------------------------------ yardimcilar

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int p) { return visible.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View convert, ViewGroup parent) {
            TextView tv = (TextView) convert;
            if (tv == null) {
                tv = new TextView(MainActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(0, dp(10), 0, dp(10));
                tv.setSingleLine(true);
            }
            App a = visible.get(pos);
            tv.setText(a.label);
            tv.setAlpha(hidden.contains(a.key()) ? 0.4f : 1f);
            return tv;
        }
    }
}
