package com.blackfalcon.nearestmasjid;

import android.content.Context;
import android.content.SharedPreferences;

public class Store {
    private final SharedPreferences p;

    public Store(Context c) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public String lang() { return p.getString("lang", "ur"); }
    public void setLang(String l) { p.edit().putString("lang", l).apply(); }
    public boolean dark() { return p.getBoolean("dark", true); }
    public void setDark(boolean v) { p.edit().putBoolean("dark", v).apply(); }
    public boolean auto() { return p.getBoolean("auto", true); }
    public void setAuto(boolean v) { p.edit().putBoolean("auto", v).apply(); }
    public boolean rtl() { return !lang().equals("en"); }
    public String gkey() { return p.getString("gkey", ""); }
    public void setGkey(String k) { p.edit().putString("gkey", k).apply(); }

    public String tr(String ur, String en, String ar) {
        String l = lang();
        if (l.equals("en")) return en;
        if (l.equals("ar")) return ar;
        return ur;
    }

    /** Places where data was already downloaded ("lat,lon;lat,lon"). */
    public void addTile(double lat, double lon) {
        String s = p.getString("tiles", "");
        s = s + (s.length() > 0 ? ";" : "") + lat + "," + lon;
        p.edit().putString("tiles", s).apply();
    }

    public boolean nearTile(double lat, double lon, double meters) {
        String s = p.getString("tiles", "");
        if (s.length() == 0) return false;
        for (String t : s.split(";")) {
            String[] a = t.split(",");
            if (a.length != 2) continue;
            try {
                double la = Double.parseDouble(a[0]), lo = Double.parseDouble(a[1]);
                if (Geo.dist(lat, lon, la, lo) <= meters) return true;
            } catch (NumberFormatException e) { }
        }
        return false;
    }
}
