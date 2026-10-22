package com.blackfalcon.nearestmasjid;

import java.util.Locale;

public class Place {
    public String id = "";
    public String en = "", ur = "", ar = "", kind = "";
    public double lat, lon;
    public boolean osm;

    public String name(String lang) {
        String s;
        if (lang.equals("ur")) s = ur.length() > 0 ? ur : en;
        else if (lang.equals("ar")) s = ar.length() > 0 ? ar : en;
        else s = en;
        if (s.length() == 0) s = ur.length() > 0 ? ur : ar;
        return s;
    }

    public String key() {
        if (id.length() > 0) return id;
        String n = en.length() > 0 ? en : (ur.length() > 0 ? ur : ar);
        return n.toLowerCase(Locale.ROOT) + "|" + Math.round(lat * 100) + "|" + Math.round(lon * 100);
    }
}
