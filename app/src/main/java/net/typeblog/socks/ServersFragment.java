package net.typeblog.socks;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ServersFragment extends Fragment implements ServerUnifiedAdapter.Listener {

    private ServerUnifiedAdapter mAdapter;
    private final List<ServerItem> mData = new ArrayList<>();
    private RecyclerView mRv;
    private TextView mEmpty;
    private TextView mActionBtn;
    private ProfileManager mManager;
    private Handler mHandler;
    private Runnable mTicker;
    private boolean mLoading = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle s) {
        View v = inflater.inflate(R.layout.fragment_servers, c, false);
        mManager = new ProfileManager(getActivity().getApplicationContext());
        mRv = v.findViewById(R.id.servers_rv);
        mEmpty = v.findViewById(R.id.servers_empty);
        mActionBtn = v.findViewById(R.id.servers_action_btn);
        if (mActionBtn != null) {
            mActionBtn.setOnClickListener(x -> onActionBtnClick());
        }

        android.widget.ImageButton btnRefresh = v.findViewById(R.id.btn_refresh_servers);
        if (btnRefresh != null) btnRefresh.setOnClickListener(x -> refreshAll());

        mRv.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new ServerUnifiedAdapter(mData, this);
        mRv.setAdapter(mAdapter);

        mHandler = new Handler(Looper.getMainLooper());
        mTicker = new Runnable() {
            @Override public void run() {
                refreshAll();
                mHandler.postDelayed(this, 60_000);
            }
        };

        refreshAll();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(mTicker, 60_000);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTicker);
    }

    private String getSubCode() {
        SharedPreferences p = getActivity().getSharedPreferences("thek_prefs", 0);
        String c = p.getString("sub_token", "");
        if (c == null || c.isEmpty()) c = p.getString("code", "");
        if (c == null || c.isEmpty()) c = p.getString("user_code", "");
        return c == null ? "" : c.trim();
    }

    private void refreshAll() {
        if (mLoading) return;
        mLoading = true;
        final String code = getSubCode();

        if (code.isEmpty()) {
            mLoading = false;
            mData.clear();
            mAdapter.notifyDataSetChanged();
            mEmpty.setVisibility(View.VISIBLE);
            mRv.setVisibility(View.GONE);
            mEmpty.setText("Введи код подписки в разделе «Главная»\nи серверы появятся здесь");
            return;
        }

        new Thread(() -> {
            final List<ServerItem> list = new ArrayList<>();
            String err = null;
            try {
                JSONObject resp = RemoteAssets.getCheckResult(getActivity(), code);
                if (resp == null) {
                    err = "Нет ответа от сервера";
                } else if (!resp.optBoolean("valid", false)) {
                    err = "Код недействителен";
                } else {
                    // ===== MTProto =====
                    JSONArray mt = resp.optJSONArray("mtproto");
                    boolean hasMtproto = false;
                    if (mt != null) {
                        for (int i = 0; i < mt.length(); i++) {
                            JSONObject o = mt.getJSONObject(i);
                            ServerItem it = new ServerItem(ServerItem.TYPE_MTPROTO);
                            it.name = "Telegram";
                            it.host = o.optString("server", "");
                            it.port = o.optInt("port", 8443);
                            it.mtprotoLink = o.optString("link", "");
                            it.expiresAt = o.optString("expires_at", "");
                            it.loadCur = o.optInt("load_cur", 0);
                            it.loadMax = o.optInt("load_max", 10);
                            it.serverUsed = o.optInt("server_used", 0);
                            it.serverTotal = o.optInt("server_total", 0);
                            list.add(it);
                            hasMtproto = true;
                        }
                    }

                    // ===== SOCKS5 =====
                    JSONArray socks = resp.optJSONArray("servers");
                    if (socks != null) {
                        for (int i = 0; i < socks.length(); i++) {
                            Object raw = socks.get(i);
                            if (!(raw instanceof JSONObject)) continue;
                            JSONObject o = (JSONObject) raw;
                            ServerItem it = new ServerItem(ServerItem.TYPE_SOCKS);
                            it.name = o.optString("name", "Сервер");
                            it.host = o.optString("host", "");
                            it.port = o.optInt("port", 1080);
                            it.user = o.optString("user", "");
                            it.pass = o.optString("pass", "");
                            it.loadCur = o.optInt("load_cur", 0);
                            it.loadMax = o.optInt("load_max", 10);
                            if (hasMtproto && i == 0) {
                                it.showDivider = true;
                            }
                            list.add(it);
                        }
                    }
                }
            } catch (Exception e) {
                err = e.getMessage();
            }

            final String ferr = err;
            final List<ServerItem> fl = list;

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                mData.clear();
                mData.addAll(fl);

                String defName = mManager.getDefault().getName();
                for (ServerItem it : mData) {
                    if (it.type == ServerItem.TYPE_SOCKS && it.name.equals(defName)) {
                        it.selected = true;
                    }
                }

                mAdapter.notifyDataSetChanged();

                if (mData.isEmpty()) {
                    mEmpty.setVisibility(View.VISIBLE);
                    mRv.setVisibility(View.GONE);
                    mEmpty.setText(ferr != null ? ("Ошибка: " + ferr) : "Серверов нет");
                } else {
                    mEmpty.setVisibility(View.GONE);
                    mRv.setVisibility(View.VISIBLE);
                }

                mLoading = false;
                updateActionButton();
                pingAll();
            });
        }).start();
    }

    private void pingAll() {
        for (final ServerItem it : new ArrayList<>(mData)) {
            if (it.showDivider) continue;
            if (it.host == null || it.host.isEmpty()) continue;
            new Thread(() -> {
                final int ms = pingHost(it.host, it.port);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    it.pingMs = ms;
                    mAdapter.notifyDataSetChanged();
                });
            }).start();
        }
    }

    private int pingHost(String host, int port) {
        try {
            long start = java.lang.System.currentTimeMillis();
            URL url = new URL("http://" + host + ":8080/health");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setRequestMethod("GET");
            c.getResponseCode();
            int ms = (int) (java.lang.System.currentTimeMillis() - start);
            c.disconnect();
            return ms;
        } catch (Exception e) {
            try {
                long start = java.lang.System.currentTimeMillis();
                Socket s = new Socket();
                s.connect(new java.net.InetSocketAddress(host, port), 2500);
                s.close();
                return (int) (java.lang.System.currentTimeMillis() - start);
            } catch (Exception e2) {
                return -2;
            }
        }
    }

    @Override
    public void onSelect(ServerItem it) {
        try {
            Profile p = mManager.getProfile(it.name);
            if (p == null) {
                p = mManager.addProfile(it.name);
            }
            if (p == null) return;
            p.setServer(it.host);
            p.setPort(it.port);
            p.setIsUserpw(it.user != null && !it.user.isEmpty());
            p.setUsername(it.user == null ? "" : it.user);
            p.setPassword(it.pass == null ? "" : it.pass);
            mManager.switchDefault(it.name);
            Toast.makeText(getActivity(), "Выбран: " + it.name, Toast.LENGTH_SHORT).show();
            updateActionButton();
            // Обновляем галочки
            for (ServerItem s : mData) {
                s.selected = s.name.equals(it.name) && s.type == ServerItem.TYPE_SOCKS;
            }
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(getActivity(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLongClick(ServerItem it) {
        Toast.makeText(getActivity(), "Долгое нажатие: " + it.name, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMtprotoClick(ServerItem it) {
        if (it.mtprotoLink == null || it.mtprotoLink.isEmpty()) {
            Toast.makeText(getActivity(), "Ссылка недоступна", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(it.mtprotoLink));
            i.setPackage("org.telegram.messenger");
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(it.mtprotoLink.replace("tg://", "https://t.me/")));
                startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(getActivity(), "Telegram не установлен", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateActionButton() {
        if (mActionBtn == null) return;
        ServerItem selected = null;
        for (ServerItem s : mData) {
            if (s.selected && s.type == ServerItem.TYPE_SOCKS) { selected = s; break; }
        }
        boolean hasMt = false;
        for (ServerItem s : mData) {
            if (s.type == ServerItem.TYPE_MTPROTO) { hasMt = true; break; }
        }
        if (selected != null) {
            mActionBtn.setVisibility(View.VISIBLE);
            mActionBtn.setText("\uD83D\uDD0C Подключить: " + selected.name);
        } else if (hasMt) {
            mActionBtn.setVisibility(View.VISIBLE);
            mActionBtn.setText("\u2708\uFE0F Открыть Telegram");
        } else {
            mActionBtn.setVisibility(View.GONE);
        }
    }

    private void onActionBtnClick() {
        ServerItem selected = null;
        for (ServerItem s : mData) {
            if (s.selected && s.type == ServerItem.TYPE_SOCKS) { selected = s; break; }
        }
        if (selected != null) {
            // Запускаем VPN на выбранном SOCKS5
            android.widget.Toast.makeText(getActivity(), "Подключаю: " + selected.name, android.widget.Toast.LENGTH_SHORT).show();
            // Переключаемся на главную и запускаем VPN
            try {
                android.content.Intent i = new android.content.Intent(getActivity(), MainActivity.class);
                i.putExtra("auto_connect", true);
                startActivity(i);
            } catch (Exception e) {
                android.widget.Toast.makeText(getActivity(), "Ошибка: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        // Если SOCKS5 не выбран, но есть MTProto — открываем Telegram
        for (ServerItem s : mData) {
            if (s.type == ServerItem.TYPE_MTPROTO) {
                onMtprotoClick(s);
                return;
            }
        }
    }

}
