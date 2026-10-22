package com.blackfalcon.nearestmasjid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity implements Tracker.Listener {

    static final int GREEN = 0xFF2E7D32;
    static final String CACHE_FILE = "masjids.json";
    static final int MAX_SHOWN = 100;

    static class Hit {
        Place p;
        double d, b;
    }

    Store st;
    Tracker tracker;
    ArrayList<Place> db = new ArrayList<Place>();
    Location cur, lastFetch, lastBuildLoc;
    float heading = -1f;
    boolean busy = false;
    long lastBuild = 0;
    int fetchState = 0; // 0 idle, 1 loading, 2 failed (offline)

    int cBg, cCard, cText, cSub;
    TextView[] tabViews;
    ScrollView[] scrolls;
    LinearLayout listBox, setBox, bigBox;
    TextView tvState, tvPos;
    Button btnGps;
    TextView bigArrow;
    double bigBearing = 0;
    boolean hasBig = false;
    final ArrayList<TextView> arrows = new ArrayList<TextView>();
    final ArrayList<Double> brgs = new ArrayList<Double>();
    RadioGroup rgLang, rgTheme;
    int[] langIds = new int[3];
    int[] themeIds = new int[2];
    CheckBox cbAuto;
    EditText etKey;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        db = Cache.load(this, CACHE_FILE);
        tracker = new Tracker(this, this);
        buildUI();
        showTab(0);
        requestPermissions(new String[] {"android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"}, 31);
    }

    boolean hasPerm() {
        return checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPerm()) tracker.start();
        updateState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tracker.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (hasPerm()) tracker.start();
        updateState();
    }

    // ---------- helpers ----------
    String tr(String ur, String en, String ar) { return st.tr(ur, en, ar); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout cardBox() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(cCard, 14));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), dp(5), dp(10), dp(5));
        c.setLayoutParams(lp);
        return c;
    }

    Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(color, 10));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    String[] windNames() {
        String l = st.lang();
        if (l.equals("en")) return new String[] {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        if (l.equals("ar")) return new String[] {"شمال", "شمال شرق", "شرق", "جنوب شرق",
            "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        return new String[] {"شمال", "شمال مشرق", "مشرق", "جنوب مشرق",
            "جنوب", "جنوب مغرب", "مغرب", "شمال مغرب"};
    }

    String dirText(double deg) {
        return windNames()[Geo.wind(deg)] + " " + Math.round(deg) + "\u00B0";
    }

    String mosqueName(Place p) {
        String n = p.name(st.lang());
        if (n.length() == 0) n = tr("مسجد (بے نام)", "Mosque (unnamed)", "مسجد (بدون اسم)");
        return n;
    }

    void openMaps(Place p) {
        try {
            String label = Uri.encode(mosqueName(p));
            Uri u = Uri.parse("geo:" + p.lat + "," + p.lon + "?q=" + p.lat + "," + p.lon + "(" + label + ")");
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, u),
                    tr("نقشے کی ایپ منتخب کریں", "Choose a maps app", "اختر تطبيق خرائط")));
        } catch (Exception e) {
            Toast.makeText(this, tr("نقشے کی ایپ نہیں ملی", "No maps app found", "لا يوجد تطبيق خرائط"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens a maps app with the search "mosque" around the current position (like typing "nearby mosques"). */
    void searchInMaps() {
        if (cur == null) {
            Toast.makeText(this, tr("پہلے مقام معلوم ہونے دیں", "Wait for a position first",
                    "انتظر تحديد الموقع أولًا"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri u = Uri.parse("geo:" + cur.getLatitude() + "," + cur.getLongitude() + "?q=" + Uri.encode("mosque"));
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, u),
                    tr("نقشے کی ایپ منتخب کریں", "Choose a maps app", "اختر تطبيق خرائط")));
        } catch (Exception e) {
            Toast.makeText(this, tr("نقشے کی ایپ نہیں ملی", "No maps app found", "لا يوجد تطبيق خرائط"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- UI ----------
    void buildUI() {
        boolean dark = st.dark();
        cBg = dark ? 0xFF000000 : 0xFFF2F2F2;
        cCard = dark ? 0xFF1A1A1A : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF111111;
        cSub = dark ? 0xFFAAAAAA : 0xFF555555;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setLayoutDirection(st.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView title = tv("Nearest Masjid", 22, cText, true);
        title.setPadding(dp(14), dp(14), dp(14), dp(6));
        root.addView(title);

        String[] names = {tr("قریب ترین مسجد", "Nearest", "الأقرب"), tr("سیٹنگز", "Settings", "الإعدادات")};
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(4), dp(8), dp(8));
        tabViews = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView t = tv(names[i], 15, cText, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(8), dp(9), dp(8), dp(9));
            t.setOnClickListener(v -> showTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(t, lp);
            tabViews[i] = t;
        }
        root.addView(tabs);

        FrameLayout content = new FrameLayout(this);
        scrolls = new ScrollView[2];
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        setBox = new LinearLayout(this);
        setBox.setOrientation(LinearLayout.VERTICAL);
        setBox.setPadding(dp(14), dp(4), dp(14), dp(16));
        LinearLayout[] boxes = {main, setBox};
        for (int i = 0; i < 2; i++) {
            scrolls[i] = new ScrollView(this);
            scrolls[i].addView(boxes[i]);
            content.addView(scrolls[i], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bigBox = new LinearLayout(this);
        bigBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(bigBox);

        LinearLayout st1 = cardBox();
        tvPos = tv("", 13, cText, false);
        tvState = tv("", 12, cSub, false);
        st1.addView(tvPos);
        st1.addView(tvState);
        btnGps = btn(tr("لوکیشن سیٹنگز کھولیں", "Open location settings", "فتح إعدادات الموقع"),
                0xFF455A64, v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        st1.addView(btnGps);
        st1.addView(btn(tr("مساجد ریفریش کریں (انٹرنیٹ سے)", "Refresh mosques (online)",
                "تحديث المساجد (عبر الإنترنت)"), GREEN, v -> fetch(false)));
        st1.addView(btn(tr("نقشے کی ایپ میں تمام قریبی مساجد (Google Maps جیسا)",
                "All nearby mosques in a maps app (like Google Maps)",
                "كل المساجد القريبة في تطبيق خرائط (مثل Google Maps)"), 0xFF1565C0, v -> searchInMaps()));
        main.addView(st1);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(listBox);

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 13, cSub, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(8), 0, dp(10));
        root.addView(credit);

        setContentView(root);
        buildSettings();
        updateState();
        rebuild();
    }

    void showTab(int i) {
        for (int k = 0; k < 2; k++) {
            boolean sel = k == i;
            tabViews[k].setTextColor(sel ? Color.WHITE : cText);
            tabViews[k].setBackground(round(sel ? GREEN : cCard, 20));
            scrolls[k].setVisibility(sel ? View.VISIBLE : View.GONE);
        }
    }

    void updateState() {
        if (tvState == null) return;
        String s;
        boolean needGps = false;
        if (!hasPerm()) {
            s = tr("لوکیشن کی اجازت درکار ہے۔", "Location permission is needed.", "مطلوب إذن الموقع.");
        } else if (!tracker.gpsOn() && cur == null) {
            s = tr("GPS بند ہے؛ آن کریں۔", "GPS is off; please turn it on.", "GPS مغلق؛ شغّله.");
            needGps = true;
        } else if (cur == null) {
            s = tr("مقام کا انتظار…", "Waiting for your position…", "بانتظار تحديد موقعك…");
        } else if (fetchState == 1) {
            s = tr("مساجد ڈھونڈی جا رہی ہیں…", "Searching for mosques…", "جارٍ البحث عن المساجد…");
        } else if (fetchState == 2) {
            s = tr("انٹرنیٹ نہیں؛ پچھلی محفوظ فہرست استعمال ہو رہی ہے۔",
                    "No internet; using the last saved list.", "لا إنترنت؛ تُستخدم القائمة المحفوظة.");
        } else {
            s = tr("محفوظ مساجد: ", "Saved mosques: ", "المساجد المحفوظة: ") + db.size();
        }
        tvState.setText(s);
        btnGps.setVisibility(needGps ? View.VISIBLE : View.GONE);
        if (cur != null) {
            tvPos.setText(String.format(Locale.ENGLISH, "%.5f, %.5f", cur.getLatitude(), cur.getLongitude())
                + (cur.hasAccuracy() ? "   \u00B1" + Math.round(cur.getAccuracy()) + " m" : ""));
        } else {
            tvPos.setText("\u2014");
        }
    }

    // ---------- tracker ----------
    @Override
    public void onLoc(Location l) {
        cur = l;
        updateState();
        long now = System.currentTimeMillis();
        boolean moved = lastBuildLoc == null || l.distanceTo(lastBuildLoc) > 10f;
        if (lastBuildLoc == null || (moved && now - lastBuild > 2000) || now - lastBuild > 30000) {
            lastBuild = now;
            lastBuildLoc = l;
            rebuild();
        }
        if (st.auto() && !busy && (lastFetch == null || l.distanceTo(lastFetch) > 1500f)) {
            lastFetch = l;
            if (!st.nearTile(l.getLatitude(), l.getLongitude(), 1000)) fetch(true);
        }
    }

    @Override
    public void onHeading(float deg) {
        heading = deg;
        if (hasBig && bigArrow != null) bigArrow.setRotation((float) (bigBearing - heading));
        for (int i = 0; i < arrows.size(); i++) {
            arrows.get(i).setRotation((float) (brgs.get(i) - heading));
        }
    }

    // ---------- display ----------
    void rebuild() {
        if (listBox == null) return;
        listBox.removeAllViews();
        bigBox.removeAllViews();
        arrows.clear();
        brgs.clear();
        hasBig = false;
        if (cur == null) {
            TextView t = tv(tr("مقام معلوم ہوتے ہی قریب ترین مسجد یہاں دکھے گی۔",
                    "The nearest masjid will appear here once your position is known.",
                    "سيظهر أقرب مسجد هنا بعد تحديد موقعك."), 14, cSub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(20), dp(30), dp(20), dp(30));
            bigBox.addView(t);
            return;
        }
        double la = cur.getLatitude(), lo = cur.getLongitude();
        ArrayList<Hit> hs = new ArrayList<Hit>(db.size());
        for (Place p : db) {
            Hit h = new Hit();
            h.p = p;
            h.d = Geo.dist(la, lo, p.lat, p.lon);
            hs.add(h);
        }
        Collections.sort(hs, new Comparator<Hit>() {
            @Override public int compare(Hit a, Hit b) { return Double.compare(a.d, b.d); }
        });
        if (hs.isEmpty()) {
            TextView t = tv(tr("اس علاقے کی کوئی مسجد محفوظ نہیں۔ انٹرنیٹ آن کر کے \"ریفریش\" دبائیں۔",
                    "No mosque saved for this area. Turn on internet and tap Refresh.",
                    "لا توجد مساجد محفوظة لهذه المنطقة. شغّل الإنترنت واضغط تحديث."), 14, cSub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(20), dp(30), dp(20), dp(30));
            bigBox.addView(t);
            return;
        }
        // the same mosque can come from two sources (OSM + Google): keep one within 40 m
        ArrayList<Hit> uniq = new ArrayList<Hit>();
        for (Hit h : hs) {
            boolean dup = false;
            for (int k = uniq.size() - 1; k >= 0; k--) {
                Hit u = uniq.get(k);
                if (u.d < h.d - 40) break;
                if (Geo.dist(u.p.lat, u.p.lon, h.p.lat, h.p.lon) < 40) { dup = true; break; }
            }
            if (!dup) uniq.add(h);
            if (uniq.size() > MAX_SHOWN) break;
        }
        // nearest (big card)
        final Hit first = uniq.get(0);
        first.b = Geo.bearing(la, lo, first.p.lat, first.p.lon);
        LinearLayout big = cardBox();
        big.setOrientation(LinearLayout.HORIZONTAL);
        big.setGravity(Gravity.CENTER_VERTICAL);
        bigArrow = tv("\u2191", 64, GREEN, true);
        bigArrow.setGravity(Gravity.CENTER);
        bigBearing = first.b;
        hasBig = true;
        bigArrow.setRotation((float) (heading >= 0 ? first.b - heading : first.b));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(84), dp(84));
        ap.setMargins(0, 0, dp(12), 0);
        big.addView(bigArrow, ap);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(tr("قریب ترین مسجد", "Nearest masjid", "أقرب مسجد"), 12, cSub, false));
        col.addView(tv(mosqueName(first.p), 19, cText, true));
        col.addView(tv(Geo.fmtDist(first.d), 34, GREEN, true));
        col.addView(tv(dirText(first.b) + "  \u2022  " + tr("سیدھی لکیر میں", "straight line", "بخط مستقيم"),
                12, cSub, false));
        big.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        big.setOnClickListener(v -> openMaps(first.p));
        bigBox.addView(big);

        // next ones
        int n = 0;
        for (int i = 1; i < uniq.size() && n < MAX_SHOWN; i++) {
            Hit h = uniq.get(i);
            h.b = Geo.bearing(la, lo, h.p.lat, h.p.lon);
            listBox.addView(row(h));
            n++;
        }
    }

    View row(final Hit h) {
        LinearLayout c = cardBox();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        TextView ar = tv("\u2191", 28, GREEN, true);
        ar.setGravity(Gravity.CENTER);
        ar.setRotation((float) (heading >= 0 ? h.b - heading : h.b));
        arrows.add(ar);
        brgs.add(h.b);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(42), dp(42));
        ap.setMargins(0, 0, dp(12), 0);
        c.addView(ar, ap);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(mosqueName(h.p), 16, cText, true));
        col.addView(tv(Geo.fmtDist(h.d) + "  \u2022  " + dirText(h.b), 12, cSub, false));
        c.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.setOnClickListener(v -> openMaps(h.p));
        return c;
    }

    // ---------- download ----------
    void fetch(final boolean silent) {
        if (cur == null) {
            if (!silent) Toast.makeText(this, tr("پہلے مقام معلوم ہونے دیں", "Wait for a position first",
                    "انتظر تحديد الموقع أولًا"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (busy) return;
        busy = true;
        fetchState = 1;
        updateState();
        final double la = cur.getLatitude(), lo = cur.getLongitude();
        final ArrayList<Place> snapshot = new ArrayList<Place>(db);
        final String gkey = st.gkey().trim();
        new Thread(() -> {
            int[] radii = {3000, 8000, 20000};
            ArrayList<Place> all = new ArrayList<Place>();
            boolean osmFailed = false;
            for (int r : radii) {
                ArrayList<Place> got = Net.mosques(la, lo, r);
                if (got == null) { osmFailed = true; break; }
                all = got;
                if (got.size() >= 8) break;
            }
            ArrayList<Place> gl = null;
            String gerr = "";
            if (gkey.length() > 0) {
                gl = Net.google(gkey, la, lo, 5000);
                if (gl == null) gerr = Net.err;
                else all.addAll(gl);
            }
            boolean failed = osmFailed && gl == null;
            ArrayList<Place> merged = snapshot;
            int added = 0;
            if (all.size() > 0) {
                HashSet<String> keys = new HashSet<String>();
                for (Place p : merged) keys.add(p.key());
                for (Place p : all) {
                    if (merged.size() >= 20000) break;
                    if (keys.add(p.key())) { merged.add(p); added++; }
                }
                Cache.save(this, CACHE_FILE, merged);
            }
            final ArrayList<Place> fm = merged;
            final int fa = added;
            final boolean ff = failed;
            final boolean any = all.size() > 0;
            final String ge = gerr;
            runOnUiThread(() -> {
                busy = false;
                db = fm;
                fetchState = ff ? 2 : 0;
                if (any && !ff) st.addTile(la, lo);
                updateState();
                rebuild();
                if (!silent) Toast.makeText(this, ff
                    ? tr("انٹرنیٹ نہیں ملا", "No internet connection", "لا يوجد اتصال بالإنترنت")
                    : tr("نئی مساجد: ", "New mosques: ", "مساجد جديدة: ") + fa, Toast.LENGTH_LONG).show();
                if (!silent && ge.length() > 0) Toast.makeText(this, "Google: " + ge, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ---------- settings ----------
    TextView label(String s) {
        TextView t = tv(s, 16, cText, true);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    RadioGroup radios(String[] labels, int checked, int[] ids) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(labels[i]);
            r.setTextColor(cText);
            r.setTextSize(15);
            r.setId(View.generateViewId());
            ids[i] = r.getId();
            g.addView(r);
        }
        g.check(ids[checked]);
        return g;
    }

    void buildSettings() {
        setBox.removeAllViews();
        setBox.addView(label(tr("زبان", "Language", "اللغة")));
        String l = st.lang();
        rgLang = radios(new String[] {"اردو", "English", "العربية"},
                l.equals("en") ? 1 : (l.equals("ar") ? 2 : 0), langIds);
        setBox.addView(rgLang);

        setBox.addView(label(tr("تھیم", "Theme", "المظهر")));
        rgTheme = radios(new String[] {tr("ڈارک موڈ", "Dark mode", "الوضع الداكن"),
            tr("لائٹ موڈ", "Light mode", "الوضع الفاتح")}, st.dark() ? 0 : 1, themeIds);
        setBox.addView(rgTheme);

        cbAuto = new CheckBox(this);
        cbAuto.setText(tr("نئے علاقے میں انٹرنیٹ ہو تو خود مساجد محفوظ کریں",
                "Auto-download mosques in a new area when online",
                "تنزيل المساجد تلقائيًا في منطقة جديدة عند توفر الإنترنت"));
        cbAuto.setTextColor(cText);
        cbAuto.setChecked(st.auto());
        cbAuto.setPadding(0, dp(12), 0, 0);
        setBox.addView(cbAuto);

        setBox.addView(label(tr("Google Places API کی (اختیاری)", "Google Places API key (optional)",
                "مفتاح Google Places API (اختياري)")));
        etKey = new EditText(this);
        etKey.setText(st.gkey());
        etKey.setHint("AIza\u2026");
        etKey.setHintTextColor(0xFF777777);
        etKey.setTextColor(Color.BLACK);
        etKey.setTextSize(14);
        etKey.setBackground(round(0xFFFFFFFF, 8));
        etKey.setPadding(dp(10), dp(8), dp(10), dp(8));
        etKey.setSingleLine(true);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        setBox.addView(etKey);

        setBox.addView(btn(tr("محفوظ کریں", "Save", "حفظ"), GREEN, v -> saveSettings()));

        TextView info = tv(tr(
            "مساجد کا ڈیٹا OpenStreetMap سے آتا ہے اور ڈاؤن لوڈ کے بعد فون میں محفوظ رہتا ہے، اس لیے اسی علاقے میں بغیر انٹرنیٹ بھی فاصلہ دکھتا ہے۔ ڈاؤن لوڈ کے وقت آپ کا مقام OpenStreetMap کے سرور کو جاتا ہے؛ نہ چاہیں تو اوپر والا آپشن بند کریں۔\n\nفاصلہ سیدھی لکیر میں ہے، پیدل یا گاڑی کے راستے کا نہیں۔ نقشہ OpenStreetMap رضاکار بناتے ہیں، اس لیے کچھ مساجد درج نہ ہوں یا نام نہ ہو سکتا ہے۔ کسی مسجد پر ٹیپ کریں تو نقشے کی ایپس کی فہرست کھلتی ہے جہاں سے آپ اپنی پسند کی ایپ چن سکتے ہیں۔ Google Maps جیسی مکمل فہرست کے لیے \"تمام قریبی مساجد\" بٹن دبائیں، یا اپنی Google Places API کی ڈالیں (کی کے لیے Google Cloud میں بلنگ آن ہونا ضروری ہے؛ ہر ڈاؤن لوڈ پر آپ کے مقام سمیت درخواست Google کو جاتی ہے اور اس کی فیس آپ کے اکاؤنٹ پر لگ سکتی ہے)۔",
            "Mosque data comes from OpenStreetMap and is saved on your phone after download, so distances also work without internet in the same area. While downloading, your position is sent to the OpenStreetMap server; turn the option above off if you do not want that.\n\nDistance is straight-line, not the walking or driving route. The map is volunteer-made, so some mosques may be missing or unnamed. Tap a mosque to get a list of maps apps and pick one. For the full Google Maps-style list press \"All nearby mosques\", or add your own Google Places API key (the key needs billing enabled in Google Cloud; each download sends a request including your position to Google and may be charged to your account).",
            "تأتي بيانات المساجد من OpenStreetMap وتُحفظ على هاتفك بعد التنزيل، فتعمل المسافات دون إنترنت في المنطقة نفسها. أثناء التنزيل يُرسل موقعك إلى خادم OpenStreetMap؛ أوقف الخيار أعلاه إن لم ترغب.\n\nالمسافة بخط مستقيم لا بطريق المشي أو القيادة. الخريطة من عمل متطوعين، فقد تغيب بعض المساجد أو أسماؤها. اضغط على مسجد لفتحه في تطبيق الخرائط."),
            12, cSub, false);
        info.setPadding(0, dp(18), 0, 0);
        setBox.addView(info);
    }

    void saveSettings() {
        String[] langs = {"ur", "en", "ar"};
        int sel = rgLang.getCheckedRadioButtonId();
        for (int i = 0; i < 3; i++) if (langIds[i] == sel) st.setLang(langs[i]);
        st.setDark(rgTheme.getCheckedRadioButtonId() == themeIds[0]);
        st.setAuto(cbAuto.isChecked());
        st.setGkey(etKey.getText().toString().trim());
        buildUI();
        showTab(1);
        Toast.makeText(this, tr("محفوظ ہو گیا", "Saved", "تم الحفظ"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle(tr("ایپ بند کریں؟", "Exit app?", "إغلاق التطبيق؟"))
            .setMessage(tr("کیا آپ واقعی باہر نکلنا چاہتے ہیں؟", "Do you really want to exit?",
                    "هل تريد الخروج فعلًا؟"))
            .setPositiveButton(tr("ہاں", "Yes", "نعم"), (dlg, w) -> finish())
            .setNegativeButton(tr("نہیں", "No", "لا"), null)
            .show();
    }
}
