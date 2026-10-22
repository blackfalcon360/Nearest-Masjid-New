package com.blackfalcon.nearestmasjid;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/** GPS/network location + compass heading (true north, declination corrected). */
public class Tracker implements LocationListener, SensorEventListener {
    public interface Listener {
        void onLoc(Location l);
        void onHeading(float deg);
    }

    final LocationManager lm;
    final SensorManager sm;
    final Sensor rot;
    final Listener lis;
    Location last;
    float decl = 0f;
    float hd = -1f;
    long lastCb = 0;

    public Tracker(Context c, Listener l) {
        lis = l;
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        rot = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public boolean hasCompass() { return rot != null; }

    public boolean gpsOn() {
        try {
            return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public Location last() { return last; }

    public void start() {
        if (lm != null) {
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this); }
            catch (Exception e) { }
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this); }
            catch (Exception e) { }
            Location best = null;
            String[] provs = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER};
            for (String p : provs) {
                try {
                    Location x = lm.getLastKnownLocation(p);
                    if (x != null && (best == null || x.getTime() > best.getTime())) best = x;
                } catch (Exception e) { }
            }
            if (best != null && last == null) accept(best);
        }
        if (sm != null && rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception e) { }
        try { if (sm != null) sm.unregisterListener(this); } catch (Exception e) { }
    }

    void accept(Location l) {
        last = l;
        try {
            decl = new GeomagneticField((float) l.getLatitude(), (float) l.getLongitude(),
                    (float) l.getAltitude(), System.currentTimeMillis()).getDeclination();
        } catch (Exception e) { }
        if (lis != null) lis.onLoc(l);
    }

    @Override
    public void onLocationChanged(Location l) {
        if (last != null && LocationManager.NETWORK_PROVIDER.equals(l.getProvider())
                && LocationManager.GPS_PROVIDER.equals(last.getProvider())
                && l.getTime() - last.getTime() < 15000) return;
        accept(l);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        try {
            float[] v = e.values;
            if (v.length > 4) {
                float[] t = new float[4];
                System.arraycopy(v, 0, t, 0, 4);
                v = t;
            }
            float[] r = new float[9];
            SensorManager.getRotationMatrixFromVector(r, v);
            float[] o = new float[3];
            SensorManager.getOrientation(r, o);
            double az = o[0];
            if (Math.abs(Math.toDegrees(o[1])) > 50) { // phone held upright
                float[] r2 = new float[9];
                if (SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2)) {
                    float[] o2 = new float[3];
                    SensorManager.getOrientation(r2, o2);
                    az = o2[0];
                }
            }
            float t = (float) ((Math.toDegrees(az) + decl + 720.0) % 360.0);
            if (hd < 0) {
                hd = t;
            } else {
                float d = t - hd;
                while (d > 180) d -= 360;
                while (d < -180) d += 360;
                hd = (hd + 0.2f * d + 360f) % 360f;
            }
            long now = System.currentTimeMillis();
            if (now - lastCb > 120) {
                lastCb = now;
                if (lis != null) lis.onHeading(hd);
            }
        } catch (Exception ex) { }
    }
}
