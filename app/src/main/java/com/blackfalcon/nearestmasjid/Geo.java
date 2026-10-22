package com.blackfalcon.nearestmasjid;

import java.util.Locale;

/** Pure maths: great-circle distance and bearing (no Android classes). */
public class Geo {
    static final double R = 6371000.0;

    /** Distance in metres (haversine). */
    public static double dist(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1), p2 = Math.toRadians(la2);
        double dp = p2 - p1, dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Initial bearing in degrees from true north, 0..360. */
    public static double bearing(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1), p2 = Math.toRadians(la2);
        double dl = Math.toRadians(lo2 - lo1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** 0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW */
    public static int wind(double deg) {
        int i = (int) Math.floor((deg + 22.5) / 45.0);
        return ((i % 8) + 8) % 8;
    }

    public static String fmtDist(double m) {
        if (m < 1000) return Math.round(m) + " m";
        if (m < 10000) return String.format(Locale.ENGLISH, "%.2f km", m / 1000.0);
        if (m < 100000) return String.format(Locale.ENGLISH, "%.1f km", m / 1000.0);
        return Math.round(m / 1000.0) + " km";
    }
}
