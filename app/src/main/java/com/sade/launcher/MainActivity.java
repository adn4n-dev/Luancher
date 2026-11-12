package com.sade.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sade Launcher
 * - Siyah arka plan, beyaz yazi, ikon yok, bildirim rozeti yok.
 * - Uygulamalar alfabetik liste.
 * - Uzun bas: "Gizle" -> uygulama ana listeden kalkar, sadece aramayla bulunur.
 *   (Instagram, TikTok vb. icin bilerek zahmet ekler.)
 * - Hicbir izin yok, internet yok, reklam yok, takip yok.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "sade";
    private static final String KEY_HIDDEN = "hidden";
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
    private Set<String> hidden = new HashSet<>();

    private EditText search;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);
        int pad = dp(24);
        root.setPadding(pad, dp(16), pad, 0);

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
            public void afterTextChanged(Editable s) { applyFilter(); }
        });
        // Klavyede "Git"e basinca ilk sonucu ac.
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go && !visible.isEmpty()) {
                launch(visible.get(0));
                return true;
            }
            return false;
        });
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(android.R.color.transparent);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> launch(visible.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            showOptions(visible.get(pos));
            return true;
        });
        // Kaydirmaya baslayinca klavyeyi kapat.
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Her ana ekrana donuste: aramayi temizle, listeyi yenile
        // (yeni yuklenen / silinen uygulamalar gorunsun).
        search.setText("");
        hideKeyboard();
        loadApps();
        applyFilter();
    }

    @Override
    public void onBackPressed() {
        // Launcher'da geri tusu sadece aramayi temizler.
        search.setText("");
        hideKeyboard();
    }

    private void loadApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : infos) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = ri.loadLabel(pm).toString().trim();
            ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            allApps.add(new App(label, cn));
        }
        Collator c = Collator.getInstance(TR);
        allApps.sort((a, b) -> c.compare(a.label, b.label));
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(TR);
        visible.clear();
        for (App a : allApps) {
            boolean isHidden = hidden.contains(a.key());
            if (q.isEmpty()) {
                if (!isHidden) visible.add(a);
            } else if (a.labelLower.contains(q)) {
                // Gizli uygulamalar aramada yine cikar: yasak degil, sadece zahmetli.
                visible.add(a);
            }
        }
        // Aramada adi sorguyla baslayanlari one al.
        if (!q.isEmpty()) {
            visible.sort((a, b) -> Boolean.compare(
                    !a.labelLower.startsWith(q), !b.labelLower.startsWith(q)));
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(app.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            loadApps();
            applyFilter();
        }
    }

    private void showOptions(App app) {
        boolean isHidden = hidden.contains(app.key());
        String[] items = {
                isHidden ? "Listeye geri al" : "Gizle (sadece aramayla açılır)",
                "Uygulama bilgisi",
                "Kaldır"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        if (isHidden) hidden.remove(app.key());
                        else hidden.add(app.key());
                        prefs().edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
                        applyFilter();
                    } else if (which == 1) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + app.component.getPackageName()));
                        startActivity(i);
                    } else {
                        Intent i = new Intent(Intent.ACTION_DELETE,
                                Uri.parse("package:" + app.component.getPackageName()));
                        startActivity(i);
                    }
                })
                .show();
    }

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

    private class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int p) { return visible.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View convert, ViewGroup parent) {
            TextView tv = (TextView) convert;
            if (tv == null) {
                tv = new TextView(MainActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(0, dp(12), 0, dp(12));
                tv.setSingleLine(true);
            }
            App a = visible.get(pos);
            tv.setText(a.label);
            // Gizli uygulamalar aramada soluk gorunur.
            tv.setAlpha(hidden.contains(a.key()) ? 0.4f : 1f);
            return tv;
        }
    }
}
