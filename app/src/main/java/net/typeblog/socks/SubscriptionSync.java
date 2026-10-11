package net.typeblog.socks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionSync {

    private static final String TAG = "THEK_SYNC";
    private static final long SYNC_INTERVAL_MS = 30 * 60 * 1000;  // 30 минут

    public interface Callback {
        void onDone(int added, int removed, String error);
    }

    /** Сохранить код юзера (после ввода). */
    public static void saveCode(Context ctx, String code) {
        SharedPreferences sp = ctx.getSharedPreferences("thek_prefs", 0);
        sp.edit()
            .putBoolean("has_subscription", true)
            .putString("sub_code", code)
            .putLong("last_sync", 0)  // обнуляем — сразу синканём
            .apply();
    }

    public static String getCode(Context ctx) {
        return ctx.getSharedPreferences("thek_prefs", 0)
            .getString("sub_code", null);
    }

    /** Авто-sync при возврате в приложение. */
    public static void autoSync(Context ctx) {
        String code = getCode(ctx);
        if (code == null) return;

        long lastSync = ctx.getSharedPreferences("thek_prefs", 0)
            .getLong("last_sync", 0);
        long now = java.lang.System.currentTimeMillis();
        if (now - lastSync < SYNC_INTERVAL_MS) return;

        sync(ctx, code, null);
    }

    /** Принудительный sync. */
    public static void forceSync(Context ctx, Callback cb) {
        String code = getCode(ctx);
        if (code == null) {
            if (cb != null) cb.onDone(0, 0, "no_code");
            return;
        }
        // Сбрасываем timestamp — чтобы сработал
        ctx.getSharedPreferences("thek_prefs", 0)
            .edit().putLong("last_sync", 0).apply();
        sync(ctx, code, cb);
    }

    /** Основной метод — качает /check/{code} и обновляет профили. */
    public static void sync(Context ctx, String code, Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            int added = 0, removed = 0;
            String error = null;

            try {
                URL url = new URL("http://77.239.101.146/check/" + code);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);

                int rc = c.getResponseCode();
                if (rc != 200) {
                    error = "HTTP " + rc;
                    c.disconnect();
                    if (cb != null) cb.onDone(0, 0, error);
                    return;
                }

                BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                c.disconnect();

                JSONObject obj = new JSONObject(sb.toString());
                JSONArray arr = obj.optJSONArray("servers");
                if (arr == null) {
                    if (cb != null) cb.onDone(0, 0, "no_servers");
                    return;
                }

                // Парсим сервера с сервера
                List<String[]> newServers = new ArrayList<>();  // [name, host, port, user, pass]
                for (int i = 0; i < arr.length(); i++) {
                    String link = arr.getString(i);
                    String[] parsed = parseSocksLink(link);
                    if (parsed != null) newServers.add(parsed);
                }

                // Считаем что уже есть у юзера
                ProfileManager m = new ProfileManager(app);
                String[] existing = m.getProfiles();

                // Удаляем старые sub-профили, которых больше нет
                List<String> newNames = new ArrayList<>();
                for (String[] s : newServers) newNames.add(s[0]);
                for (String name : existing) {
                    if (name.startsWith("sub_") && !newNames.contains(name)) {
                        m.removeProfile(name);
                        removed++;
                    }
                }

                // Добавляем новые
                for (String[] s : newServers) {
                    String name = s[0];
                    String host = s[1];
                    int port = Integer.parseInt(s[2]);
                    String user = s[3];
                    String pass = s[4];

                    Profile p = m.getProfile(name);
                    boolean isNew = (p == null);
                    if (isNew) {
                        p = m.addProfile(name);
                    }
                    if (p == null) continue;

                    p.setServer(host);
                    p.setPort(port);
                    p.setIsUserpw(user != null && !user.isEmpty());
                    p.setUsername(user != null ? user : "");
                    p.setPassword(pass != null ? pass : "");
                    p.setRoute("all");
                    p.setDns("8.8.8.8");
                    p.setDnsPort(53);
                    p.setIsPerApp(false);
                    p.setIsBypassApp(false);
                    p.setAppList("");
                    p.setHasIPv6(false);
                    p.setHasUDP(true);
                    p.setUDPGW("77.239.101.146:7300");
                    p.setAutoConnect(false);

                    if (isNew) added++;
                }

                // Обновляем timestamp
                app.getSharedPreferences("thek_prefs", 0)
                    .edit().putLong("last_sync", java.lang.System.currentTimeMillis()).apply();

            } catch (Exception e) {
                error = e.getMessage();
                Log.e(TAG, "sync error: " + e.getMessage(), e);
            }

            final int fa = added, fr = removed;
            final String fe = error;
            if (cb != null) {
                cb.onDone(fa, fr, fe);
            }
        }).start();
    }

    /** Парсит socks5://user:pass@host:port#name */
    private static String[] parseSocksLink(String link) {
        try {
            String body = link.replace("socks5://", "").replace("socks://", "");
            String name = "Server";
            if (body.contains("#")) {
                name = URLDecoder.decode(body.substring(body.indexOf('#') + 1), "UTF-8");
                body = body.substring(0, body.indexOf('#'));
            }
            String user = "", pass = "";
            if (body.contains("@")) {
                String auth = body.substring(0, body.indexOf('@'));
                body = body.substring(body.indexOf('@') + 1);
                if (auth.contains(":")) {
                    user = auth.substring(0, auth.indexOf(':'));
                    pass = auth.substring(auth.indexOf(':') + 1);
                }
            }
            String[] hp = body.split(":");
            if (hp.length < 2) return null;
            // Имя с префиксом sub_ — чтобы отличать подписные от ручных
            return new String[]{"sub_" + name, hp[0], hp[1], user, pass};
        } catch (Exception e) {
            return null;
        }
    }
}
