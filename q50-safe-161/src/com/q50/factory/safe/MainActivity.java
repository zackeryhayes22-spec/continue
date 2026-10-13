package com.q50.factory.safe;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative API-10 native host used to establish a stable stock App Garage runtime
 * before enabling richer UI code. No WebView, no JavaScript bridge, no CAN writes.
 */
public final class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensors;
    private TextView status;
    private TextView rpm;
    private TextView speed;
    private TextView probeResult;
    private int sensorCount;
    private int ygomiCount;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 18, 24, 18);
        root.setBackgroundColor(Color.rgb(6, 9, 12));
        scroll.addView(root);

        TextView title = text("INFINITI  |  Q50 OS SAFE 1.6.1", 24, Color.WHITE);
        root.addView(title);
        TextView sub = text("Native API-10 recovery host • lower display • read-only telemetry", 14, Color.rgb(168, 176, 182));
        root.addView(sub);

        status = text("Starting native runtime...", 17, Color.rgb(205, 211, 215));
        status.setPadding(0, 20, 0, 12);
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        rpm = metric("RPM\n—");
        speed = metric("MPH\n—");
        row.addView(rpm, new LinearLayout.LayoutParams(0, 100, 1f));
        row.addView(speed, new LinearLayout.LayoutParams(0, 100, 1f));
        root.addView(row);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        TextView device = text("Viewport " + dm.widthPixels + " x " + dm.heightPixels +
                "  •  Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT +
                "  •  " + Build.CPU_ABI, 14, Color.rgb(145, 155, 162));
        device.setPadding(0, 8, 0, 16);
        root.addView(device);

        Button probe = button("WRITE FIRMWARE PROBE");
        probe.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                probeResult.setText("Collecting factory package/service map...");
                probeResult.postDelayed(new Runnable() {
                    public void run() { probeResult.setText(writeProbe()); }
                }, 50L);
            }
        });
        root.addView(probe, new LinearLayout.LayoutParams(-1, 68));

        Button exit = button("RETURN TO STOCK INTOUCH");
        exit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        root.addView(exit, new LinearLayout.LayoutParams(-1, 68));

        probeResult = text("If this screen remains open, the App Garage runtime is stable.\nTap WRITE FIRMWARE PROBE while parked; it attempts to copy Q50FactoryProbe.txt to writable external/USB storage.", 14, Color.rgb(180, 188, 194));
        probeResult.setPadding(0, 14, 0, 0);
        root.addView(probeResult);

        setContentView(scroll);
        sensors = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView metric(String s) {
        TextView t = text(s, 23, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.rgb(20, 25, 30));
        t.setPadding(8, 8, 8, 8);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(15);
        return b;
    }

    @Override protected void onResume() {
        super.onResume();
        registerSensors();
    }

    @Override protected void onPause() {
        try { if (sensors != null) sensors.unregisterListener(this); } catch (Throwable ignored) {}
        super.onPause();
    }

    private void registerSensors() {
        sensorCount = 0;
        ygomiCount = 0;
        if (sensors == null) {
            status.setText("Native UI OK • SENSOR_SERVICE unavailable");
            return;
        }
        try { sensors.unregisterListener(this); } catch (Throwable ignored) {}
        try {
            List<Sensor> all = sensors.getSensorList(Sensor.TYPE_ALL);
            if (all != null) {
                for (Sensor s : all) {
                    sensorCount++;
                    String vendor = s.getVendor() == null ? "" : s.getVendor();
                    if (vendor.toLowerCase(Locale.US).indexOf("ygomi") >= 0) ygomiCount++;
                    try {
                        int rate = (s.getType() == 13 || s.getType() == 17) ? SensorManager.SENSOR_DELAY_GAME : 200000;
                        sensors.registerListener(this, s, rate);
                    } catch (Throwable ignored) {}
                }
            }
            status.setText("Native UI OK • sensors=" + sensorCount + " • Ygomi=" + ygomiCount + " • no CAN writes");
        } catch (Throwable t) {
            status.setText("Native UI OK • sensor enumeration error: " + t.getClass().getSimpleName());
        }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        try {
            if (e == null || e.sensor == null || e.values == null || e.values.length == 0) return;
            float v = e.values[0];
            if (e.sensor.getType() == 13) rpm.setText("RPM\n" + Math.round(v));
            if (e.sensor.getType() == 17) speed.setText("MPH\n" + Math.round(v * 0.621371f));
        } catch (Throwable ignored) {}
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

    private String writeProbe() {
        StringBuilder b = new StringBuilder(65536);
        b.append("Q50 Factory Probe 1.6.1-safe\n");
        b.append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append(" abi=").append(Build.CPU_ABI).append('\n');
        b.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        b.append("display=").append(dm.widthPixels).append('x').append(dm.heightPixels).append(" density=").append(dm.density).append('\n');
        b.append("sensors=").append(sensorCount).append(" ygomi=").append(ygomiCount).append("\n\n");

        b.append("BINDER SERVICES\n");
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method list = sm.getDeclaredMethod("listServices");
            list.setAccessible(true);
            Object result = list.invoke(null);
            if (result instanceof String[]) {
                String[] names = (String[])result;
                for (int i=0;i<names.length;i++) b.append("  ").append(names[i]).append('\n');
            }
        } catch (Throwable t) {
            b.append("  unavailable: ").append(t.toString()).append('\n');
        }

        b.append("\nSENSORS\n");
        try {
            List<Sensor> all = sensors == null ? null : sensors.getSensorList(Sensor.TYPE_ALL);
            if (all != null) for (Sensor s : all) {
                b.append("  type=").append(s.getType()).append(" name=").append(s.getName())
                 .append(" vendor=").append(s.getVendor()).append(" range=").append(s.getMaximumRange()).append('\n');
            }
        } catch (Throwable t) { b.append("  error: ").append(t.toString()).append('\n'); }

        b.append("\nPACKAGES / IVI METADATA\n");
        PackageManager pm = getPackageManager();
        try {
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA;
            List<PackageInfo> pkgs = pm.getInstalledPackages(flags);
            if (pkgs != null) for (PackageInfo pi : pkgs) {
                String p = pi.packageName == null ? "" : pi.packageName;
                String low = p.toLowerCase(Locale.US);
                boolean rel = relevant(low);
                ApplicationInfo ai0 = pi.applicationInfo;
                Bundle meta = ai0 == null ? null : ai0.metaData;
                boolean hasMeta = meta != null && !meta.isEmpty();
                if (!rel && !hasMeta) continue;
                b.append("PKG ").append(p).append(" version=").append(pi.versionName).append('\n');
                if (ai0 != null) b.append("  uid=").append(ai0.uid).append(" source=").append(ai0.sourceDir).append('\n');
                if (meta != null) {
                    try {
                        for (String k : meta.keySet()) {
                            if (k != null) b.append("  META ").append(k).append('=').append(String.valueOf(meta.get(k))).append('\n');
                        }
                    } catch (Throwable ignored) {}
                }
                if (pi.requestedPermissions != null) for (String perm : pi.requestedPermissions) {
                    if (perm != null && (rel || perm.indexOf("IVI") >= 0 || perm.toLowerCase(Locale.US).indexOf("ygomi") >= 0 || perm.toLowerCase(Locale.US).indexOf("connexis") >= 0))
                        b.append("  PERM ").append(perm).append('\n');
                }
                if (pi.activities != null) for (ActivityInfo a : pi.activities) {
                    if (rel || relevant((a.name == null ? "" : a.name).toLowerCase(Locale.US)))
                        b.append("  ACT ").append(a.exported ? "exported " : "private ").append(a.name).append('\n');
                }
                if (pi.services != null) for (ServiceInfo s : pi.services) {
                    if (rel || relevant((s.name == null ? "" : s.name).toLowerCase(Locale.US)))
                        b.append("  SVC ").append(s.exported ? "exported " : "private ").append(s.name).append('\n');
                }
            }
        } catch (Throwable t) { b.append("PACKAGE ENUM ERROR ").append(t.toString()).append('\n'); }

        String data = b.toString();
        ArrayList<String> written = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        addWriteTarget(written, seen, Environment.getExternalStorageDirectory(), data);
        addWriteTarget(written, seen, new File("/mnt/usb_storage"), data);
        addWriteTarget(written, seen, new File("/mnt/usb"), data);
        addWriteTarget(written, seen, new File("/mnt/udisk"), data);
        addWriteTarget(written, seen, new File("/storage/usb0"), data);
        scanRoot(written, seen, new File("/mnt"), data);
        scanRoot(written, seen, new File("/storage"), data);
        scanRoot(written, seen, new File("/media"), data);
        if (written.size() == 0) return "Probe collected, but no writable external mount was found.";
        StringBuilder out = new StringBuilder("Probe written to:\n");
        for (String p : written) out.append(p).append('\n');
        return out.toString();
    }

    private boolean relevant(String s) {
        return s.indexOf("connexis") >= 0 || s.indexOf("ygomi") >= 0 || s.indexOf("infiniti") >= 0 ||
               s.indexOf("nissan") >= 0 || s.indexOf("ivi") >= 0 || s.indexOf("display") >= 0 ||
               s.indexOf("screen") >= 0 || s.indexOf("home") >= 0 || s.indexOf("appsmanager") >= 0 ||
               s.indexOf("nav") >= 0 || s.indexOf("map") >= 0 || s.indexOf("camera") >= 0 ||
               s.indexOf("avm") >= 0 || s.indexOf("audio") >= 0 || s.indexOf("climate") >= 0;
    }

    private void scanRoot(ArrayList<String> written, Set<String> seen, File root, String data) {
        try {
            if (root == null || !root.isDirectory()) return;
            File[] kids = root.listFiles();
            if (kids == null) return;
            for (File f : kids) if (f != null && f.isDirectory()) addWriteTarget(written, seen, f, data);
        } catch (Throwable ignored) {}
    }

    private void addWriteTarget(ArrayList<String> written, Set<String> seen, File dir, String data) {
        try {
            if (dir == null) return;
            String key = dir.getAbsolutePath();
            if (seen.contains(key)) return;
            seen.add(key);
            if (!dir.exists() || !dir.isDirectory() || !dir.canWrite()) return;
            File out = new File(dir, "Q50FactoryProbe.txt");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
            w.write(data);
            w.close();
            written.add(out.getAbsolutePath());
        } catch (Throwable ignored) {}
    }
}
