package net.typeblog.socks;

import android.app.Fragment;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout mDrawer;
    private Toolbar mToolbar;
    private int mCurrentNav = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawer = findViewById(R.id.drawer_layout);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        mToolbar.setNavigationOnClickListener(v -> mDrawer.openDrawer(GravityCompat.START));

        setNav(R.id.nav_home);
        setNav(R.id.nav_servers);
        setNav(R.id.nav_settings);
        setNav(R.id.nav_about);

        // "Для Telegram" отключено — MTProto теперь в разделе "Сервера"
        View navTg = findViewById(R.id.nav_telegram);
        if (navTg != null) navTg.setVisibility(View.GONE);

        // Синхронизация удалённых ресурсов
        try { RemoteAssets.sync(this); } catch (Exception ignored) {}

        // Welcome — только один раз
        android.content.SharedPreferences welcomePrefs = getSharedPreferences("thek_assets", 0);
        boolean welcomeShown = welcomePrefs.getBoolean("welcome_shown", false);
        if (!welcomeShown && savedInstanceState == null) {
            String welcome = RemoteAssets.getText(this, "text_welcome", "");
            if (welcome != null && !welcome.isEmpty()) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(welcome)
                    .setPositiveButton("OK", null)
                    .show();
                welcomePrefs.edit().putBoolean("welcome_shown", true).apply();
            }
        }

        if (savedInstanceState == null) {
            selectItem(R.id.nav_home);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            SubscriptionSync.autoSync(this);
        } catch (Exception ignored) {}
    }

    private void setNav(int id) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(view -> selectItem(id));
    }

    private void selectItem(int id) {
        if (mCurrentNav == id) {
            mDrawer.closeDrawers();
            return;
        }
        mCurrentNav = id;

        Fragment f;
        if (id == R.id.nav_servers) {
            f = new ServersFragment();
        } else if (id == R.id.nav_settings) {
            f = new SettingsFragment();
        } else if (id == R.id.nav_about) {
            f = new AboutFragment();
        } else {
            f = new HomeFragment();
        }

        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, f)
            .commit();

        mDrawer.closeDrawers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (item.getItemId() == R.id.action_add) {
            showAddDialog(f);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddDialog(Fragment currentFragment) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add, null);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setView(view)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.opt_manual).setOnClickListener(v -> {
            dialog.dismiss();
            if (currentFragment instanceof HomeFragment) {
                ((HomeFragment) currentFragment).addServer();
            } else {
                selectItem(R.id.nav_home);
                new android.os.Handler().postDelayed(() -> {
                    Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
                    if (f instanceof HomeFragment) ((HomeFragment) f).addServer();
                }, 300);
            }
        });

        view.findViewById(R.id.opt_code).setOnClickListener(v -> {
            dialog.dismiss();
            if (currentFragment instanceof HomeFragment) {
                ((HomeFragment) currentFragment).showCodeDialog();
            } else {
                selectItem(R.id.nav_home);
                new android.os.Handler().postDelayed(() -> {
                    Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
                    if (f instanceof HomeFragment) ((HomeFragment) f).showCodeDialog();
                }, 300);
            }
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) mDrawer.closeDrawers();
        else super.onBackPressed();
    }
}
