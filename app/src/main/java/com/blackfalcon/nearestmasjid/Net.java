package com.blackfalcon.nearestmasjid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** Downloads mosques around a point from OpenStreetMap (Overpass API). */
public class Net {
    static final String[] SERVERS = {
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    };

    static String get(String server, String query) {
        HttpURLConnection c = null;
        try {
            String u = server + "?data=" + URLEncoder.encode(query, "UTF-8");
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(40000);
            c.setRequestProperty("User-Agent", "BlackFalconNearestMasjid/1.0");
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Last error text from the optional Google Places call. */
    public static String err = "";

    /**
     * Optional: Google Places API (New) "Nearby Search", nearest first (max 20 results).
     * Needs the user's own API key. Returns null on error (see err).
     */
    public static ArrayList<Place> google(String key, double lat, double lon, int radius) {
        err = "";
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://places.googleapis.com/v1/places:searchNearby").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-Goog-Api-Key", key);
            c.setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.location");
            String body = "{\"includedTypes\":[\"mosque\"],\"maxResultCount\":20,"
                + "\"rankPreference\":\"DISTANCE\",\"locationRestriction\":{\"circle\":{\"center\":"
                + "{\"latitude\":" + lat + ",\"longitude\":" + lon + "},\"radius\":" + radius + ".0}}}";
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    code == 200 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            if (code != 200) {
                try {
                    err = new JSONObject(sb.toString()).getJSONObject("error").optString("message", "HTTP " + code);
                } catch (Exception e) {
                    err = "HTTP " + code;
                }
                return null;
            }
            ArrayList<Place> out = new ArrayList<Place>();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray a = root.optJSONArray("places");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                JSONObject loc = o.optJSONObject("location");
                if (loc == null) continue;
                Place p = new Place();
                JSONObject dn = o.optJSONObject("displayName");
                if (dn != null) p.en = dn.optString("text", "");
                p.lat = loc.getDouble("latitude");
                p.lon = loc.getDouble("longitude");
                p.id = "g" + o.optString("id", "");
                p.kind = "google";
                p.osm = true;
                out.add(p);
            }
            return out;
        } catch (Exception e) {
            err = "network";
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Returns null if the network failed, otherwise the (possibly empty) list. */
    public static ArrayList<Place> mosques(double lat, double lon, int radius) {
        String ar = "(around:" + radius + "," + lat + "," + lon + ")";
        String q = "[out:json][timeout:30];("
            + "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]" + ar + ";"
            + "nwr[\"building\"=\"mosque\"]" + ar + ";"
            + ");out center 500;";
        String body = null;
        for (String s : SERVERS) {
            body = get(s, q);
            if (body != null) break;
        }
        if (body == null) return null;
        ArrayList<Place> out = new ArrayList<Place>();
        try {
            JSONArray el = new JSONObject(body).getJSONArray("elements");
            for (int i = 0; i < el.length(); i++) {
                JSONObject o = el.getJSONObject(i);
                double la, lo;
                if (o.has("lat") && o.has("lon")) {
                    la = o.getDouble("lat");
                    lo = o.getDouble("lon");
                } else {
                    JSONObject c = o.optJSONObject("center");
                    if (c == null) continue;
                    la = c.getDouble("lat");
                    lo = c.getDouble("lon");
                }
                JSONObject t = o.optJSONObject("tags");
                Place p = new Place();
                if (t != null) {
                    String n = t.optString("name", "");
                    p.en = t.optString("name:en", "");
                    if (p.en.length() == 0) p.en = n;
                    p.ur = t.optString("name:ur", "");
                    p.ar = t.optString("name:ar", "");
                }
                p.lat = la;
                p.lon = lo;
                p.id = "osm" + o.optString("type", "n") + o.optLong("id", 0);
                p.osm = true;
                out.add(p);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }
}
