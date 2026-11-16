// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.hbu.zhangshanghbu.campus.LoginDialog;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.pages.CirclePage;
import com.hbu.zhangshanghbu.pages.HomePage;
import com.hbu.zhangshanghbu.pages.MinePage;
import com.hbu.zhangshanghbu.pages.TablePage;
import com.hbu.zhangshanghbu.ui.Deck;
import com.hbu.zhangshanghbu.ui.Dock;

/**
 * 主界面：页面带 + 底部胶囊导航（首页 / 课表 / 圈子 / 我的）。
 *
 * 登录不是一页，而是「按需弹出的窗口」：由这里统一发，任何页面要用登录态就调 login()。
 * 冷启动后台会探一次三个平台，掉线了在首页挂提示条，点它才弹窗。
 */
public class MainActivity extends BaseActivity {

    private Store store;
    private Deck deck;
    private Dock dock;
    private LoginDialog loginDialog;

    private HomePage home;
    private TablePage table;
    private CirclePage circle;
    private MinePage mine;

    private int index = 0;
    private int dockBand;
    private boolean checked;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = store();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pageColor());

        deck = new Deck(this);
        deck.setSlotWidth(Kit.contentWidth(this));
        /* 平板 / 分屏下让内容居中一列，而不是被拉满整屏 */
        FrameLayout.LayoutParams deckLp = new FrameLayout.LayoutParams(
                Kit.contentWidth(this), ViewGroup.LayoutParams.MATCH_PARENT);
        deckLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(deck, deckLp);

        FrameLayout dockBox = new FrameLayout(this);
        float d = getResources().getDisplayMetrics().density;
        dockBand = Math.round(12 * d + 64 * d + 16 * d);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                Kit.contentWidth(this), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        root.addView(dockBox, dlp);

        dock = new Dock(this);
        FrameLayout.LayoutParams box = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(62 * d));
        box.leftMargin = Math.round(20 * d);
        box.rightMargin = Math.round(20 * d);
        box.topMargin = Math.round(10 * d);
        box.bottomMargin = Math.round(14 * d);
        dockBox.addView(dock, box);

        dock.setOnTab(this::show);
        dock.setOnReselect(this::reselect);

        setContentView(root);
        applyInsets(root, dockBox);

        if (b != null) index = b.getInt("tab", 0);
        show(index, false);

        // 首次进入没有课表：直接把课表页的空态摆出来，不自动弹登录（用户点了才弹）
        root.postDelayed(this::autoCheck, 1200);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", index);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        /* 旋转 / 分屏 / 折叠屏展开都要重算：内容宽度跟着变，槽位也要跟着变 */
        deck.cancelAnimation();
        int w = Kit.contentWidth(this);
        deck.setSlotWidth(w);
        android.view.ViewGroup.LayoutParams lp = deck.getLayoutParams();
        if (lp != null && lp.width != w) {
            lp.width = w;
            deck.setLayoutParams(lp);
        }
        deck.jumpTo(index);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mine != null) mine.reload();
        if (circle != null) circle.reload();
    }

    // ---------- 登录 ----------

    /** 弹登录窗口；已全在线也照弹（用户是主动来重新登录的）。 */
    public void login() {
        loginDialog = LoginDialog.show(this, Portal.loginOrder(true), () -> {
            refreshAll();
            /* 登录完顺手把本人信息读回来：姓名、学号、院系、头像。
               失败也不打扰用户，只是「我的」里继续显示占位头像。 */
            com.hbu.zhangshanghbu.campus.Profile.refresh(this, store, ok -> {
                if (ok && mine != null) mine.reload();
            });
            Kit.toast(this, "登录完成");
        });
    }

    /** 需要某几个平台时用这个：登录成功后再执行。 */
    public void loginFor(String[] platforms, Runnable then) {
        Session.ensure(this, platforms, then);
    }

    /** 启动后静默检查一次登录态，掉线了才提示。 */
    private void autoCheck() {
        if (checked) return;
        checked = true;
        if (store.loginAt() <= 0) return;
        Session.check(this, allOk -> {
            if (!allOk && home != null) home.reload();
            if (!allOk && circle != null) circle.reload();
            if (allOk) {
                /* 校园网还活着就顺手把教务也激活一次：CAS 票在服务端，
                   这一下会把教务的会话建起来，并把菜单 id 缓存好，
                   用户点课表/成绩时就不用再等一次登录跳转。 */
                com.hbu.zhangshanghbu.campus.Engine.ensureMenu(this, () -> {
                    /* 顺手补一次本人资料；6 小时内的缓存直接跳过，
                       否则每次冷启动都要多打一次学籍页 */
                    if (com.hbu.zhangshanghbu.campus.Profile.fresh(store)) return;
                    com.hbu.zhangshanghbu.campus.Profile.refresh(this, store, ok -> {
                        if (ok && mine != null) mine.reload();
                    });
                });
            }
        });
    }

    // ---------- 页面 ----------

    public void switchTab(int i) {
        show(i);
    }

    public void refreshAll() {
        if (home != null) home.reload();
        if (table != null) table.reload();
        if (circle != null) circle.reload();
        if (mine != null) mine.reload();
    }

    public void onTableChanged() {
        if (home != null) home.reload();
        if (table != null) table.reload();
    }

    private void show(int i) {
        show(i, true);
    }

    private void show(int i, boolean animate) {
        int from = index;
        index = i;
        if (dock.selected() != i) dock.setSelected(i, true);

        View page = page(i);
        applyPadding(page);
        deck.put(i, page);

        for (int k = Math.min(from, i); k <= Math.max(from, i); k++) {
            if (k == i) continue;
            View mid = page(k);
            applyPadding(mid);
            deck.put(k, mid);
        }

        float remain = Math.abs(i - deck.position());
        long dur = Math.min(400L, Math.max(150L, Math.round(280 + 40 * Math.max(0f, remain - 1f))));
        deck.cancelAnimation();
        if (animate && remain > 0.01f && !isFinishing()) deck.animateTo(i, dur);
        else deck.jumpTo(i);

        if (page instanceof HomePage) ((HomePage) page).reload();
        if (page instanceof TablePage) ((TablePage) page).onShown();
        if (page instanceof CirclePage) ((CirclePage) page).reload();
        if (page instanceof MinePage) ((MinePage) page).reload();
    }

    private void reselect(int i) {
        if (i == 0 && home != null) home.reload();
        if (i == 1 && table != null) table.today();
        if (i == 2 && circle != null) circle.reload();
        if (i == 3 && mine != null) mine.reload();
    }

    private View page(int i) {
        switch (i) {
            case 1:
                if (table == null) table = new TablePage(this, store);
                return table;
            case 2:
                if (circle == null) circle = new CirclePage(this, store);
                return circle;
            case 3:
                if (mine == null) mine = new MinePage(this, store);
                return mine;
            default:
                if (home == null) home = new HomePage(this, store);
                return home;
        }
    }

    private void applyPadding(View page) {
        if (page instanceof android.widget.ScrollView) {
            android.widget.ScrollView sv = (android.widget.ScrollView) page;
            sv.setClipToPadding(false);
            sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(),
                    dockBand + safeBottom());
        } else {
            page.setPadding(page.getPaddingLeft(), page.getPaddingTop(),
                    page.getPaddingRight(), dockBand);
        }
    }

    private void applyInsets(View root, View dockBox) {
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, 0);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dockBox.getLayoutParams();
                lp.bottomMargin = bars.bottom;
                dockBox.setLayoutParams(lp);
                return insets;
            });
        } else {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) root.setPadding(0, getResources().getDimensionPixelSize(id), 0, 0);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private int safeBottom() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsets in = getWindow().getDecorView().getRootWindowInsets();
            if (in != null) return in.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return 0;
    }

    @Override
    public void onBackPressed() {
        if (index != 0) {
            show(0);
            return;
        }
        super.onBackPressed();
    }

    /** 供页面拿当前表（避免各页各读一遍）。 */
    public Table table() {
        return store.activeTable();
    }
}
