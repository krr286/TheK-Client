package net.typeblog.socks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;
import net.typeblog.socks.util.Utility;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ServerAdapter.Listener {

    private ProfileManager mManager;
    private Profile mProfile;
    private FrameLayout mConnectBtn;
    private ImageView mConnectIcon;
    private TextView mStatus, mCurrentServer;
    private RecyclerView mList;
    private ServerAdapter mAdapter;
    private final List<Profile> mProfiles = new ArrayList<>();
    private boolean mRunning = false, mStarting = false, mStopping = false;
    private IVpnService mBinder;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mBinder = IVpnService.Stub.asInterface(b);
            try { mRunning = mBinder.isRunning(); } catch (Exception e) { mRunning = false; }
            updateState();
        }
        @Override public void onServiceDisconnected(ComponentName n) { mBinder = null; }
    };

    private final Runnable mStateTick = new Runnable() {
        @Override public void run() {
            updateState();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle s) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        mManager = new ProfileManager(getActivity().getApplicationContext());


        mConnectBtn = v.findViewById(R.id.connect_button);


        mConnectIcon = v.findViewById(R.id.connect_icon);
        mStatus = v.findViewById(R.id.home_status);
        mCurrentServer = v.findViewById(R.id.current_server);
        mList = v.findViewById(R.id.servers_list);

        // Кнопка обновления на главной
        android.widget.ImageView homeRefresh = v.findViewById(R.id.home_refresh);
        if (homeRefresh != null) {
            homeRefresh.setOnClickListener(x -> {
                android.widget.Toast.makeText(getActivity(), "Обновляю...",
                    android.widget.Toast.LENGTH_SHORT).show();
                SubscriptionSync.forceSync(getActivity(), (added, removed, err) -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (err != null) {
                            android.widget.Toast.makeText(getActivity(),
                                "Ошибка: " + err,
                                android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            loadProfiles();
                            android.widget.Toast.makeText(getActivity(),
                                "Готово: +" + added + " / -" + removed,
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
        }
        }
        mList.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new ServerAdapter(mProfiles, this);
        mList.setAdapter(mAdapter);

        mConnectBtn.setOnClickListener(x -> toggleConnect());

        loadProfiles();
        checkState();
        mHandler.postDelayed(mStateTick, 300);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacks(mStateTick);
        if (mBinder != null) {
            try { getActivity().unbindService(mConn); } catch (Exception ignored) {}
            mBinder = null;
        }
    }

    private void loadProfiles() {
        mProfiles.clear();
        String[] names = mManager.getProfiles();
        String defName = mManager.getDefault().getName();
        int sel = -1;
        for (int i = 0; i < names.length; i++) {
            if ("По умолчанию".equals(names[i])) continue;
            Profile p = mManager.getProfile(names[i]);
            if (p != null) {
                mProfiles.add(p);
                if (names[i].equals(defName)) sel = mProfiles.size() - 1;
            }
        }
        mProfile = mManager.getDefault();
        mAdapter.setSelectedIndex(sel);
        mCurrentServer.setText(mProfile != null ? mProfile.getName() : "");
    }

    @Override public void onSelect(Profile p) {
        mProfile = p;
        mManager.switchDefault(p.getName());
        loadProfiles();
    }

    @Override public void onLongClick(Profile p) {
        new AlertDialog.Builder(getActivity())
            .setItems(new String[]{"Удалить"}, (d, w) -> {
                mManager.removeProfile(p.getName());
                mProfile = mManager.getDefault();
                loadProfiles();
            }).show();
    }

    public void addServer() {
        addManually();
    }

    private void addManually() {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_server, null);
        EditText n = view.findViewById(R.id.dlg_name);
        EditText s = view.findViewById(R.id.dlg_server);
        EditText p = view.findViewById(R.id.dlg_port);
        EditText u = view.findViewById(R.id.dlg_user);
        EditText pw = view.findViewById(R.id.dlg_pass);
        p.setText("1080");

        new AlertDialog.Builder(getActivity())
            .setTitle("Новый сервер")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String name = n.getText().toString().trim();
                String srv = s.getText().toString().trim();
                String port = p.getText().toString().trim();
                String user = u.getText().toString().trim();
                String pass = pw.getText().toString().trim();
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(srv) || TextUtils.isEmpty(port)) {
                    Toast.makeText(getActivity(), "Заполни имя, IP и порт", Toast.LENGTH_SHORT).show();
                    return;
                }
                createProfile(name, srv, port, user, pass);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }




    private void createProfile(String name, String srv, String port, String user, String pass) {
        Profile pr = mManager.addProfile(name);
        if (pr == null) {
            pr = mManager.addProfile(name + "_" + java.lang.System.currentTimeMillis());
            if (pr == null) return;
        }
        pr.setServer(srv);
        try { pr.setPort(Integer.parseInt(port)); } catch (Exception ex) { pr.setPort(1080); }
        pr.setIsUserpw(!TextUtils.isEmpty(user));
        pr.setUsername(user == null ? "" : user);
        pr.setPassword(pass == null ? "" : pass);
        mProfile = pr;
        mManager.switchDefault(name);
        loadProfiles();
    }

    public void toggleConnect() {
        if (mRunning) stopVpn(); else startVpn();
    }

    private void checkState() {
        if (mBinder == null) {
            getActivity().bindService(
                new Intent(getActivity(), SocksVpnService.class), mConn, 0);
        }
    }

    private void updateState() {
        if (mBinder == null) mRunning = false;
        else try { mRunning = mBinder.isRunning(); } catch (Exception e) { mRunning = false; }

        if (mStatus == null || getActivity() == null) return;

        if (mRunning) {
            mStatus.setText("ЗАЩИЩЕНО");
            mStatus.setTextColor(0xFF3FFF7A);
        } else {
            mStatus.setText("НЕ ЗАЩИЩЕНО");
            mStatus.setTextColor(0xFFFF3F3F);
        }

        if (mConnectBtn != null) {
            if (mRunning) mConnectBtn.setBackgroundResource(R.drawable.bg_connect_button_on);
            else mConnectBtn.setBackgroundResource(R.drawable.bg_connect_button);
        }

        if (mStarting && mRunning) mStarting = false;
        if (mStopping && !mRunning) mStopping = false;
    }

    private void startVpn() {
        if (mProfile == null) {
            Toast.makeText(getActivity(), "Добавь сервер", Toast.LENGTH_SHORT).show();
            return;
        }
        mStarting = true;
        Intent i = VpnService.prepare(getActivity());
        if (i != null) startActivityForResult(i, 0);
        else onActivityResult(0, Activity.RESULT_OK, null);
    }

    private void stopVpn() {
        if (mBinder == null) return;
        mStopping = true;
        try { mBinder.stop(); } catch (Exception ignored) {}
        mBinder = null;
        try { getActivity().unbindService(mConn); } catch (Exception ignored) {}
        checkState();
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && mProfile != null) {
            Utility.startVpn(getActivity(), mProfile);
            checkState();
        } else mStarting = false;
    }

    private void verifyCode(String code) {
        android.widget.Toast.makeText(getActivity(), "Проверяю...", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result = null;
            String serversJson = null;
            try {
                java.net.URL url = new java.net.URL("http://77.239.101.146/check/" + code);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                int rc = c.getResponseCode();
                if (rc == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    serversJson = sb.toString();
                    result = "ok";
                } else if (rc == 403) result = "expired";
                else result = "notfound";
                c.disconnect();
            } catch (Exception e) {
                android.util.Log.e("THEK_CODE", "verify error: " + e.getMessage(), e);
                result = "error";
            }

            final String res = result;
            final String json = serversJson;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if ("ok".equals(res)) {
                    int added = parseAndSaveServers(json);
                    android.content.SharedPreferences sp =
                        getActivity().getSharedPreferences("thek_prefs", 0);
                    // Определяем — был ли другой код
                    String oldCode = sp.getString("sub_token", "");
                    boolean codeChanged = !oldCode.isEmpty() && !oldCode.equals(code);

                    // Если код сменился — чистим старые профили и скрытые
                    if (codeChanged) {
                        try {
                            net.typeblog.socks.util.ProfileManager pm =
                                new net.typeblog.socks.util.ProfileManager(getActivity().getApplicationContext());
                            String[] names = pm.getProfiles();
                            for (String n : names) {
                                if (!"По умолчанию".equals(n)) {
                                    try { pm.removeProfile(n); } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception ignored) {}
                        sp.edit().remove("hidden_servers").apply();
                    }

                    sp.edit()
                      .putBoolean("has_subscription", true)
                      .putString("sub_token", code)
                      .putString("code", code)
                      .putString("user_code", code)
                      .apply();

                    if (codeChanged) {
                        android.widget.Toast.makeText(getActivity(),
                            "Аккаунт переключён. Серверы обновлены.",
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                    android.widget.Toast.makeText(getActivity(),
                        "✅ Подписка активирована! Добавлено серверов: " + added,
                        android.widget.Toast.LENGTH_LONG).show();
                    getActivity().recreate();
                } else if ("expired".equals(res)) {
                    android.widget.Toast.makeText(getActivity(),
                        "⚠️ Подписка истекла. Продли в боте.",
                        android.widget.Toast.LENGTH_LONG).show();
                } else if ("notfound".equals(res)) {
                    android.widget.Toast.makeText(getActivity(),
                        "❌ Код не найден. Проверь правильность.",
                        android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(getActivity(),
                        "🌐 Нет связи с сервером",
                        android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private int parseAndSaveServers(String json) {
    int added = 0;
    try {
        org.json.JSONObject obj = new org.json.JSONObject(json);
        org.json.JSONArray arr = obj.optJSONArray("servers");
        if (arr == null) return 0;

        for (int i = 0; i < arr.length(); i++) {
            Object raw = arr.get(i);
            String name, host, user = "", pass = "";
            int port = 1080;

            if (raw instanceof org.json.JSONObject) {
                org.json.JSONObject o = (org.json.JSONObject) raw;
                name = o.optString("name", "Server " + (i + 1));
                host = o.optString("host", "");
                port = o.optInt("port", 1080);
                user = o.optString("user", "");
                pass = o.optString("pass", "");
            } else {
                String link = String.valueOf(raw);
                String body = link.replace("socks5://", "").replace("socks://", "");
                name = "Server " + (i + 1);
                if (body.contains("#")) {
                    name = java.net.URLDecoder.decode(body.substring(body.indexOf('#') + 1), "UTF-8");
                    body = body.substring(0, body.indexOf('#'));
                }
                if (body.contains("@")) {
                    String auth = body.substring(0, body.indexOf('@'));
                    body = body.substring(body.indexOf('@') + 1);
                    if (auth.contains(":")) {
                        user = auth.substring(0, auth.indexOf(':'));
                        pass = auth.substring(auth.indexOf(':') + 1);
                    }
                }
                String[] hp = body.split(":");
                if (hp.length < 2) continue;
                host = hp[0];
                try { port = Integer.parseInt(hp[1]); } catch (Exception e) { port = 1080; }
            }

            if (host == null || host.isEmpty()) continue;

            try {
                if (mManager.getProfile(name) == null) mManager.addProfile(name);
            } catch (Exception ignored) {
                try { mManager.addProfile(name); } catch (Exception ignored2) {}
            }
            net.typeblog.socks.util.Profile p = mManager.getProfile(name);
            if (p == null) continue;
            p.setServer(host);
            p.setPort(port);
            p.setIsUserpw(user != null && !user.isEmpty());
            p.setUsername(user == null ? "" : user);
            p.setPassword(pass == null ? "" : pass);
            p.setRoute("all");
            p.setDns("8.8.8.8");
            p.setDnsPort(53);
            p.setHasIPv6(false);
            p.setUDPGW("77.239.101.146:7300");
            added++;
        }
    } catch (Exception e) {
        android.util.Log.e("THEK_PARSE", "parse error: " + e.getMessage(), e);
    }
    return added;
}


    public void showCodeDialog() {
        final android.widget.EditText input = new android.widget.EditText(getActivity());
        input.setHint("Введи код из бота");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        new android.app.AlertDialog.Builder(getActivity())
            .setTitle("Код подписки")
            .setMessage("Скопируй код в боте после покупки тарифа и вставь сюда.")
            .setView(input)
            .setPositiveButton("Проверить", (d, w) -> {
                String code = input.getText().toString().trim().toUpperCase();
                if (code.isEmpty()) return;
                verifyCode(code);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    
    private void doRefresh() {
        android.widget.Toast.makeText(getActivity(), "Обновляю...", android.widget.Toast.LENGTH_SHORT).show();
        SubscriptionSync.forceSync(getActivity(), (added, removed, error) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (error != null) {
                    android.widget.Toast.makeText(getActivity(),
                        "Ошибка: " + error, android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(getActivity(),
                        "Готово: +" + added + " / -" + removed,
                        android.widget.Toast.LENGTH_SHORT).show();
                    loadProfiles();
                }
            });
        });
    }


    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }


    @Override
    public void onResume() {
        super.onResume();
        try {
            android.content.SharedPreferences sp = getActivity().getSharedPreferences("thek_prefs", 0);
            String pending = sp.getString("pending_code", "");
            if (pending != null && !pending.isEmpty()) {
                sp.edit().remove("pending_code").apply();
                final String _code = pending.trim().toUpperCase();
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> verifyCode(_code), 400);
            }
        } catch (Exception ignored) {}
    }


    private void checkAutoConnect() {
        android.content.SharedPreferences prefs =
            getActivity().getSharedPreferences("thek_prefs", 0);
        boolean auto = prefs.getBoolean("auto_connect", false);
        if (!auto) return;
        prefs.edit().putBoolean("auto_connect", false).apply();
        // Ждём пока сервис биндится
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() == null) return;
            if (!mRunning) {
                try {
                    toggleConnect();
                } catch (Exception e) {
                    android.util.Log.e("THEK_AUTO", "auto connect err: " + e.getMessage());
                }
            }
        }, 600);
    }
}
