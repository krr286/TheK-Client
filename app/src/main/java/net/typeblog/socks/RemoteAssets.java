package net.typeblog.socks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RemoteAssets {
    private static final String TAG = "RemoteAssets";
    private static final String BASE = "http://77.239.101.146";
    private static final String PREFS = "thek_assets";
    private static final String KEY_VERSION = "version";

    public static File cacheDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "thek_assets");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static void sync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String cfg = httpGet(BASE + "/app/config");
                if (cfg == null) return;
                org.json.JSONObject j = new org.json.JSONObject(cfg);
                int newVer = j.optInt("version", 1);
                SharedPreferences sp = app.getSharedPreferences(PREFS, 0);
                int oldVer = sp.getInt(KEY_VERSION, 0);

                // тексты — всегда обновляем
                sp.edit()
                  .putString("text_about",   j.optString("text_about", ""))
                  .putString("text_welcome", j.optString("text_welcome", ""))
                  .putString("notification", j.optString("notification", ""))
                  .apply();

                sp.edit().putInt(KEY_VERSION, newVer).apply();
            } catch (Exception e) {
                Log.e(TAG, "sync: " + e.getMessage());
            }
        }).start();
    }

    public static Bitmap getBitmap(Context ctx, String name) {
        File f = new File(cacheDir(ctx), name + ".png");
        if (!f.exists()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    public static String getText(Context ctx, String key, String def) {
        String v = ctx.getSharedPreferences(PREFS, 0).getString(key, null);
        return (v == null || v.isEmpty()) ? def : v;
    }


    public static java.util.List<org.json.JSONObject> getMtProxies(Context ctx) {
        java.util.List<org.json.JSONObject> result = new java.util.ArrayList<>();
        try {
            String cfg = httpGet(BASE + "/app/config");
            if (cfg == null) return result;
            org.json.JSONObject j = new org.json.JSONObject(cfg);
            org.json.JSONArray arr = j.optJSONArray("mtproxies");
            if (arr == null) return result;
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getJSONObject(i));
            }
        } catch (Exception e) {
            android.util.Log.e("THEK_NET", "getMtProxies error: " + e.getMessage(), e);
        }
        android.util.Log.d("THEK_NET", "getMtProxies returned " + result.size() + " items");
        return result;
    }

    private static String httpGet(String url) {
        byte[] b = httpGetBytes(url);
        return b == null ? null : new String(b);
    }

    private static byte[] httpGetBytes(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }


    // ===== Получить серверы юзера по коду подписки =====
    public static org.json.JSONObject getCheckResult(Context ctx, String code) {
        try {
            String raw = httpGet(BASE + "/check/" + code);
            if (raw == null) return null;
            return new org.json.JSONObject(raw);
        } catch (Exception e) {
            android.util.Log.e("THEK_NET", "getCheckResult error: " + e.getMessage(), e);
            return null;
        }
    }
}
