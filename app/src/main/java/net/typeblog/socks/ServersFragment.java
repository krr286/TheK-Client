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

import net.typeblog.socks.util.ProfileManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ServersFragment extends Fragment implements ServerUnifiedAdapter.Listener {

    private ServerUnifiedAdapter mAdapter;
    private final List<ServerItem> mData = new ArrayList<>();
    private RecyclerView mRv;
    private TextView mEmpty;
    private TextView mActionBtn;
    private android.widget.ImageButton mRefreshBtn;
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

        mRefreshBtn = v.findViewById(R.id.btn_refresh_servers);
        if (mRefreshBtn != null) {
            mRefreshBtn.setOnClickListener(x -> {
                if (mLoading) {
                    android.widget.Toast.makeText(getActivity(), "Уже обновляется...", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    refreshAll();
                }
            });
        }

        if (mActionBtn != null) {
            mActionBtn.setOnClickListener(x -> onActionBtnClick());
        }

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
        // Сбрасываем зависший флаг (если прошлое обновление упало)
        if (mLoading) {
            mLoading = false;
        }
        mLoading = true;
        setRefreshSpinning(true);
        final String code = getSubCode();

        if (code.isEmpty()) {
            mLoading = false;
            setRefreshSpinning(false);
            mData.clear();
            mAdapter.notifyDataSetChanged();
            mEmpty.setVisibility(View.VISIBLE);
            mRv.setVisibility(View.GONE);
            if (mActionBtn != null) mActionBtn.setVisibility(View.GONE);
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
                    // ============ MTProto ============
                    JSONArray mt = resp.optJSONArray("mtproto");
                    if (mt != null && mt.length() > 0) {
                        ServerItem head = new ServerItem(ServerItem.TYPE_HEADER);
                        head.name = "Telegram \u00B7 MTProto";
                        list.add(head);

                        for (int i = 0; i < mt.length(); i++) {
                            JSONObject o = mt.getJSONObject(i);
                            ServerItem it = new ServerItem(ServerItem.TYPE_MTPROTO);
                            it.name = o.optString("name", "Telegram");
                            it.flag = o.optString("flag", "\u2708\uFE0F");
                            it.host = o.optString("server", "");
                            it.port = o.optInt("port", 8443);
                            it.mtprotoLink = o.optString("link", "");
                            it.expiresAt = o.optString("expires_at", "");
                            it.loadCur = o.optInt("load_cur", 0);
                            it.loadMax = o.optInt("load_max", 10);
                            list.add(it);
                        }
                    }

                    // ============ SOCKS5 ============
                    JSONArray socks = resp.optJSONArray("servers");
                    List<ServerItem> socksList = new ArrayList<>();
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
                            // Пропускаем скрытые серверы
                            if (isHidden(it.host + ":" + it.port)) continue;
                            socksList.add(it);
                        }
                    }

                    if (!socksList.isEmpty()) {
                        ServerItem head = new ServerItem(ServerItem.TYPE_HEADER);
                        head.name = "Сетевые прокси \u00B7 SOCKS5";
                        list.add(head);

                        // Отмечаем выбранный
                        String defName = mManager.getDefault().getName();
                        for (ServerItem it : socksList) {
                            if (it.name.equals(defName)) it.selected = true;
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
                setRefreshSpinning(false);
                updateActionButton();
                pingAll();
            });
        }).start();
    }

    private void pingAll() {
        for (final ServerItem it : new ArrayList<>(mData)) {
            if (it.type == ServerItem.TYPE_HEADER) continue;
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
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress(host, port), 2500);
                s.close();
                return (int) (java.lang.System.currentTimeMillis() - start);
            } catch (Exception e2) {
                return -2;
            }
        }
    }

    private void updateActionButton() {
        if (mActionBtn == null) return;
        ServerItem selected = null;
        for (ServerItem s : mData) {
            if (s.selected && s.type == ServerItem.TYPE_SOCKS) { selected = s; break; }
        }
        if (selected != null) {
            mActionBtn.setVisibility(View.VISIBLE);
            mActionBtn.setText("\uD83D\uDD0C Подключить: " + selected.name);
        } else {
            mActionBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSelect(ServerItem it) {
        if (it.type == ServerItem.TYPE_HEADER) return;
        if (it.type != ServerItem.TYPE_SOCKS) return;

        for (ServerItem s : mData) {
            if (s.type == ServerItem.TYPE_SOCKS) s.selected = false;
        }
        it.selected = true;
        mAdapter.notifyDataSetChanged();
        updateActionButton();

        try {
            if (mManager.getProfile(it.name) == null) {
                mManager.addProfile(it.name);
            }
            net.typeblog.socks.util.Profile p = mManager.getProfile(it.name);
            if (p != null) {
                p.setServer(it.host);
                p.setPort(it.port);
                p.setIsUserpw(it.user != null && !it.user.isEmpty());
                p.setUsername(it.user == null ? "" : it.user);
                p.setPassword(it.pass == null ? "" : it.pass);
                p.setRoute("all");
                p.setDns("8.8.8.8");
                p.setDnsPort(53);
                p.setHasIPv6(false);
                p.setUDPGW("77.239.101.146:7300");
                mManager.switchDefault(it.name);
            }
        } catch (Exception e) {
            android.util.Log.e("THEK_SEL", "save: " + e.getMessage());
        }
    }

    @Override
    public void onMtprotoOpen(ServerItem it) {
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

    @Override
    public void onLongClick(ServerItem it) {
        if (it.type == ServerItem.TYPE_MTPROTO) {
            Toast.makeText(getActivity(), "Telegram-прокси привязан к подписке", Toast.LENGTH_SHORT).show();
            return;
        }
        showDeleteDialog(it);
    }

    private void onActionBtnClick() {
        ServerItem selected = null;
        for (ServerItem s : mData) {
            if (s.selected && s.type == ServerItem.TYPE_SOCKS) { selected = s; break; }
        }
        if (selected != null) {
            try {
                if (mManager.getProfile(selected.name) == null) {
                    mManager.addProfile(selected.name);
                }
                net.typeblog.socks.util.Profile p = mManager.getProfile(selected.name);
                p.setServer(selected.host);
                p.setPort(selected.port);
                p.setIsUserpw(selected.user != null && !selected.user.isEmpty());
                p.setUsername(selected.user == null ? "" : selected.user);
                p.setPassword(selected.pass == null ? "" : selected.pass);
                p.setRoute("all");
                p.setDns("8.8.8.8");
                p.setDnsPort(53);
                p.setHasIPv6(false);
                mManager.switchDefault(selected.name);

                // Флаг авто-подключения
                android.content.SharedPreferences prefs =
                    getActivity().getSharedPreferences("thek_prefs", 0);
                prefs.edit().putBoolean("auto_connect", true).apply();

                // Переключаемся на главную
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showHomeAndConnect();
                }
            } catch (Exception e) {
                android.widget.Toast.makeText(getActivity(),
                    "Ошибка: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        for (ServerItem s : mData) {
            if (s.type == ServerItem.TYPE_MTPROTO) {
                onMtprotoClick(s);
                return;
            }
        }
    }

    private void setRefreshSpinning(boolean spinning) {
        if (mRefreshBtn == null) return;
        mRefreshBtn.setEnabled(!spinning);
        mRefreshBtn.setAlpha(spinning ? 0.4f : 1.0f);
    }

    private void showDeleteDialog(ServerItem it) {
        android.view.View v = getActivity().getLayoutInflater()
            .inflate(R.layout.dialog_delete_server, null);
        ((android.widget.TextView) v.findViewById(R.id.del_name)).setText(it.name);
        ((android.widget.TextView) v.findViewById(R.id.del_addr)).setText(it.host + ":" + it.port);

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(getActivity())
            .setView(v)
            .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        v.findViewById(R.id.del_cancel).setOnClickListener(x -> dialog.dismiss());
        v.findViewById(R.id.del_confirm).setOnClickListener(x -> {
            hideServer(it.host + ":" + it.port);
            try { mManager.removeProfile(it.name); } catch (Exception ignored) {}
            dialog.dismiss();
            Toast.makeText(getActivity(), "Удалено: " + it.name, Toast.LENGTH_SHORT).show();
            refreshAll();
        });

        dialog.show();
    }

    private java.util.Set<String> getHidden() {
        android.content.SharedPreferences sp = getActivity().getSharedPreferences("thek_prefs", 0);
        String raw = sp.getString("hidden_servers", "");
        java.util.Set<String> set = new java.util.HashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split(",")) {
                if (!s.isEmpty()) set.add(s);
            }
        }
        return set;
    }

    private boolean isHidden(String key) {
        return getHidden().contains(key);
    }

    private void hideServer(String key) {
        android.content.SharedPreferences sp = getActivity().getSharedPreferences("thek_prefs", 0);
        java.util.Set<String> set = getHidden();
        set.add(key);
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        sp.edit().putString("hidden_servers", sb.toString()).apply();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (!mLoading) refreshAll();
    }

}
