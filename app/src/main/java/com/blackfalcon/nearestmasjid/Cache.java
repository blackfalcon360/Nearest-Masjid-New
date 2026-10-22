package com.blackfalcon.nearestmasjid;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Saves downloaded places in a file so they are available offline. */
public class Cache {
    public static ArrayList<Place> load(Context c, String file) {
        ArrayList<Place> out = new ArrayList<Place>();
        try {
            File f = new File(c.getFilesDir(), file);
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            JSONArray a = new JSONArray(new String(buf, 0, off, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Place p = new Place();
                p.id = o.optString("i", "");
                p.en = o.optString("e", "");
                p.ur = o.optString("u", "");
                p.ar = o.optString("a", "");
                p.kind = o.optString("k", "");
                p.lat = o.optDouble("la", 0);
                p.lon = o.optDouble("lo", 0);
                p.osm = true;
                out.add(p);
            }
        } catch (Exception e) { }
        return out;
    }

    public static void save(Context c, String file, ArrayList<Place> list) {
        try {
            JSONArray a = new JSONArray();
            for (Place p : list) {
                JSONObject o = new JSONObject();
                o.put("i", p.id);
                o.put("e", p.en);
                o.put("u", p.ur);
                o.put("a", p.ar);
                o.put("k", p.kind);
                o.put("la", p.lat);
                o.put("lo", p.lon);
                a.put(o);
            }
            FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), file));
            out.write(a.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception e) { }
    }
}
