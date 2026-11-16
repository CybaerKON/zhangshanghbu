// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;

import com.hbu.zhangshanghbu.data.Store;

/**
 * 所有界面的基类：把「浅色 / 深色 / 跟随系统」翻译成 Configuration 的夜间位。
 *
 * 坑（上一版踩过、这版保留结论）：
 *   manifest 里声明了 configChanges=uiMode，系统会用 applyOverrideConfiguration
 *   把不带夜间位的配置覆盖回来，所以必须在这个唯一切口里重新注入；
 *   跟随系统时读「交付给本 App 的配置」，不能用 Resources.getSystem()。
 */
public class BaseActivity extends Activity {

    private static final int MASK = Configuration.UI_MODE_NIGHT_MASK;
    private static final int YES = Configuration.UI_MODE_NIGHT_YES;
    private static final int NO = Configuration.UI_MODE_NIGHT_NO;

    private int applied = -1;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(wrap(base));
    }

    @Override
    public void applyOverrideConfiguration(Configuration cfg) {
        if (cfg != null) {
            int mode = new Store(this).themeMode();
            if (mode == Store.THEME_DARK) cfg.uiMode = (cfg.uiMode & ~MASK) | YES;
            else if (mode == Store.THEME_LIGHT) cfg.uiMode = (cfg.uiMode & ~MASK) | NO;
            applied = cfg.uiMode & MASK;
        }
        super.applyOverrideConfiguration(cfg);
    }

    private Context wrap(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int mode = new Store(base).themeMode();
        boolean night;
        if (mode == Store.THEME_DARK) night = true;
        else if (mode == Store.THEME_LIGHT) night = false;
        else night = (cfg.uiMode & MASK) == YES;
        cfg.uiMode = (cfg.uiMode & ~MASK) | (night ? YES : NO);
        applied = cfg.uiMode & MASK;
        return base.createConfigurationContext(cfg);
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        int sys = cfg.uiMode & MASK;
        if (sys != applied && new Store(this).themeMode() == Store.THEME_SYSTEM) {
            applied = sys;
            recreate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (new Store(this).themeMode() == Store.THEME_SYSTEM) {
            try {
                registerReceiver(uiMode, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(uiMode);
        } catch (Exception ignored) {
        }
    }

    private final UiMode uiMode = new UiMode(this);

    private static final class UiMode extends BroadcastReceiver {
        private final BaseActivity host;

        UiMode(BaseActivity h) {
            host = h;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            if (host.isFinishing()) return;
            int sys = host.getApplicationContext().getResources().getConfiguration().uiMode & MASK;
            if (sys != host.applied) {
                host.applied = sys;
                host.recreate();
            }
        }
    }

    protected Store store() {
        return new Store(this);
    }

    protected boolean night() {
        return store().darkNow();
    }

    protected int pageColor() {
        return night() ? 0xFF0D131C : getColor(R.color.bg_page);
    }

    protected int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
