// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Profile;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.feat.AboutActivity;
import com.hbu.zhangshanghbu.ui.Dialog;
import com.hbu.zhangshanghbu.ui.Picker;

/**
 * 我的：账号与登录、外观、关于。
 *
 * 课表的所有设置都在「课表 → 设置」里，这里只留跟人相关的东西。
 *
 * 登录之后会自动去学籍页把姓名、学号、院系、专业、班级和头像读回来，
 * 所以这里显示的是真的学生信息，而不是让用户手填的一串数字。
 */
public class MinePage extends ScrollView {

    private final Activity host;
    private final Store store;
    private final LinearLayout page;

    private boolean refreshing;

    public MinePage(Activity host, Store store) {
        super(host);
        this.host = host;
        this.store = store;
        setFillViewport(true);
        setBackgroundColor(Kit.page(host));
        page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Kit.dp(host, 18), Kit.dp(host, 12), Kit.dp(host, 18), 0);
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        reload();
        autoRefresh();
    }

    /** 已登录但没有资料、或者资料超过 6 小时，就静默补一次。 */
    private void autoRefresh() {
        boolean logged = Session.ok(Portal.P_JW);
        if (!logged || Profile.fresh(store) || refreshing) return;
        refreshing = true;
        Profile.refresh(host, store, ok -> {
            refreshing = false;
            if (ok) reload();
        });
    }

    public void reload() {
        page.removeAllViews();
        page.addView(Kit.bold(host, "我的", 19f, Kit.ink(host)));
        page.addView(Kit.gap(host, 12));
        page.addView(account());

        page.addView(Kit.gap(host, 16));
        page.addView(Kit.section(host, "应用"));
        LinearLayout app = Kit.card(host, 2);
        app.addView(Kit.row(host, "主题", themeName(), this::pickTheme));
        app.addView(Kit.hr(host));
        app.addView(Kit.row(host, "关于掌上hbu", "版本 / 数据来源 / 开发者",
                () -> host.startActivity(new android.content.Intent(host, AboutActivity.class))));
        page.addView(app);

        page.addView(Kit.gap(host, 16));
        page.addView(Kit.section(host, "登录状态"));
        LinearLayout st = Kit.card(host, 2);
        st.addView(statusRow("校园网（WebVPN）", Session.state("webvpn")));
        st.addView(Kit.hr(host));
        st.addView(statusRow("本科教务（URP）", Session.state("jw")));
        st.addView(Kit.hr(host));
        st.addView(statusRow("办事大厅", Session.state("ehall")));
        page.addView(st);

        TextView foot = Kit.text(host, "密码只在学校的网页里输入，App 不保存\n数据仅存本机，不上传", 10.5f, Kit.faint(host));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Kit.dp(host, 16), 0, Kit.dp(host, 10));
        page.addView(foot);
    }

    private View statusRow(String name, int state) {
        String text = state == Session.OK ? "在线" : (state == Session.EXPIRED ? "已失效" : "未确认");
        int color = state == Session.OK ? 0xFF3FA37A
                : (state == Session.EXPIRED ? 0xFFC0392B : Kit.faint(host));
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Kit.dp(host, 13), Kit.dp(host, 11), Kit.dp(host, 13), Kit.dp(host, 11));
        row.addView(Kit.text(host, name, 14f, Kit.ink(host)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Kit.text(host, text, 12.5f, color);
        row.addView(v);
        return row;
    }

    private View account() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Kit.dp(host, 18), Kit.dp(host, 16), Kit.dp(host, 18), Kit.dp(host, 16));
        card.setBackground(Kit.round(host, 0xFF0C4C8C, 20));

        String user = store.user();
        String name = store.studentName();
        String no = store.studentNo();

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> showProfile());

        int size = Kit.dp(host, 46);
        View avatar = avatarView(size, name, user);
        row.addView(avatar, new LinearLayout.LayoutParams(size, size));

        LinearLayout info = new LinearLayout(host);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = Kit.dp(host, 12);

        String title = !name.isEmpty() ? name : (user.isEmpty() ? "未登录" : user);
        info.addView(Kit.bold(host, title, 16.5f, 0xFFFFFFFF));

        String line;
        if (!no.isEmpty() && !name.isEmpty()) {
            String cls = store.clazz();
            line = no + (cls.isEmpty() ? "" : " · " + cls);
        } else if (!user.isEmpty()) {
            line = user + " · " + loginAgo();
        } else {
            line = "用校园网账号登录一次即可";
        }
        TextView sub = Kit.text(host, line, 11.5f, 0xFFBBD6F2);
        sub.setPadding(0, Kit.dp(host, 3), 0, 0);
        info.addView(sub);

        String dept = store.dept();
        if (!dept.isEmpty()) {
            String major = store.major();
            TextView d = Kit.text(host, dept + (major.isEmpty() ? "" : " · " + major), 10.5f, 0xFF9FC4E8);
            d.setPadding(0, Kit.dp(host, 2), 0, 0);
            d.setSingleLine(true);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(d);
        }
        row.addView(info, ilp);
        card.addView(row);

        LinearLayout btns = new LinearLayout(host);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Kit.dp(host, 14), 0, 0);

        TextView login = Kit.text(host, user.isEmpty() ? "登录校园网" : "重新登录", 13f, 0xFF0C4C8C);
        login.setGravity(Gravity.CENTER);
        login.setTypeface(Typeface.DEFAULT_BOLD);
        login.setBackground(Kit.round(host, 0xFFFFFFFF, 10));
        login.setPadding(0, Kit.dp(host, 9), 0, Kit.dp(host, 9));
        login.setOnClickListener(v -> {
            if (host instanceof MainActivity) ((MainActivity) host).login();
        });
        btns.addView(login, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sync = Kit.text(host, refreshing ? "读取中…" : "同步资料", 13f, 0xFFD6E6F8);
        sync.setGravity(Gravity.CENTER);
        sync.setPadding(0, Kit.dp(host, 9), 0, Kit.dp(host, 9));
        sync.setOnClickListener(v -> syncProfile());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = Kit.dp(host, 10);
        btns.addView(sync, slp);

        TextView out = Kit.text(host, "退出登录", 13f, 0xFFD6E6F8);
        out.setGravity(Gravity.CENTER);
        out.setPadding(0, Kit.dp(host, 9), 0, Kit.dp(host, 9));
        out.setOnClickListener(v -> Dialog.confirm(host, "退出登录",
                "会清掉本机保存的校园网 Cookie 与登录状态，课表仍然保留。", "取消", "退出", () -> {
                    Session.logout(host);
                    store.setUser("");
                    store.setStudentName("");
                    store.clearProfile();
                    reload();
                }));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        olp.leftMargin = Kit.dp(host, 6);
        btns.addView(out, olp);
        card.addView(btns);
        return card;
    }

    /** 有头像就画头像，没有就用名字首字（或学号首位）占位。 */
    private View avatarView(int size, String name, String user) {
        if (store.hasAvatar()) {
            Bitmap src = BitmapFactory.decodeFile(store.avatarFile().getAbsolutePath());
            Bitmap round = Kit.circle(src, size);
            if (round != null) {
                ImageView iv = new ImageView(host);
                iv.setImageBitmap(round);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return iv;
            }
        }
        String ch = name.isEmpty() ? (user.isEmpty() ? "河" : user.substring(0, 1)) : name.substring(0, 1);
        TextView tv = Kit.bold(host, ch, 18f, 0xFF0C4C8C);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Kit.round(host, 0xFFFFFFFF, 40));
        return tv;
    }

    private void syncProfile() {
        if (refreshing) return;
        if (!Session.ok(Portal.P_JW)) {
            Dialog.alert(host, "还没登录教务", "先登录一次校园网 / 教务，再点同步资料。");
            return;
        }
        refreshing = true;
        reload();
        Profile.refresh(host, store, ok -> {
            refreshing = false;
            reload();
            Kit.toast(host, ok ? "资料已更新" : "没读到资料，稍后再试");
        });
    }

    private void showProfile() {
        if (store.studentNo().isEmpty()) {
            editUser();
            return;
        }
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 20), 0, Kit.dp(host, 20), Kit.dp(host, 18));
        box.addView(Kit.bold(host, "学生信息", 16f, Kit.ink(host)));
        box.addView(Kit.gap(host, 10));

        LinearLayout card = Kit.card(host, 2);
        card.addView(Kit.row(host, "姓名", store.studentName(), null));
        card.addView(Kit.hr(host));
        card.addView(Kit.row(host, "学号", store.studentNo(), null));
        if (!store.dept().isEmpty()) {
            card.addView(Kit.hr(host));
            card.addView(Kit.row(host, "院系", store.dept(), null));
        }
        if (!store.major().isEmpty()) {
            card.addView(Kit.hr(host));
            card.addView(Kit.row(host, "专业", store.major(), null));
        }
        if (!store.clazz().isEmpty()) {
            card.addView(Kit.hr(host));
            card.addView(Kit.row(host, "班级", store.clazz(), null));
        }
        box.addView(card);

        TextView tip = Kit.text(host, "这些信息是登录后自动从教务读回来的，点「同步资料」可以刷新。",
                10.5f, Kit.faint(host));
        tip.setPadding(0, Kit.dp(host, 10), 0, Kit.dp(host, 6));
        box.addView(tip);

        com.hbu.zhangshanghbu.ui.Sheet.show(host, box, Kit.dp(host, 340));
    }

    private String loginAgo() {
        long at = store.loginAt();
        if (at <= 0) return "未登录";
        long min = (System.currentTimeMillis() - at) / 60000;
        if (min < 1) return "刚刚确认";
        if (min < 60) return min + " 分钟前";
        if (min < 1440) return (min / 60) + " 小时前";
        return (min / 1440) + " 天前";
    }

    private void editUser() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 20), 0, Kit.dp(host, 20), Kit.dp(host, 16));
        box.addView(Kit.bold(host, "学号", 16f, Kit.ink(host)));
        TextView hint = Kit.text(host, "登录后会自动从教务读回来，这里只是手动兜底。", 11.5f, Kit.faint(host));
        hint.setPadding(0, Kit.dp(host, 6), 0, Kit.dp(host, 10));
        box.addView(hint);

        android.widget.EditText input = new android.widget.EditText(host);
        input.setText(store.user());
        input.setHint("如 2023xxxxxx");
        input.setTextSize(15f);
        input.setSingleLine(true);
        input.setTextColor(Kit.ink(host));
        input.setHintTextColor(Kit.faint(host));
        input.setBackground(Kit.stroke(host, Kit.night(host) ? 0xFF1F2836 : 0xFFF3F5F9, 10, Kit.line(host)));
        input.setPadding(Kit.dp(host, 12), Kit.dp(host, 10), Kit.dp(host, 12), Kit.dp(host, 10));
        box.addView(input);

        box.addView(Kit.gap(host, 14));
        com.hbu.zhangshanghbu.ui.Sheet.Handle[] h = new com.hbu.zhangshanghbu.ui.Sheet.Handle[1];
        TextView save = Kit.primary(host, "保存", () -> {
            store.setUser(input.getText().toString().trim());
            if (h[0] != null) h[0].dismiss();
            reload();
        });
        box.addView(save);
        h[0] = com.hbu.zhangshanghbu.ui.Sheet.show(host, box, Kit.dp(host, 260));
    }

    private String themeName() {
        switch (store.themeMode()) {
            case Store.THEME_LIGHT: return "浅色";
            case Store.THEME_DARK: return "深色";
            default: return "跟随系统";
        }
    }

    private void pickTheme() {
        String[] labels = {"跟随系统", "浅色", "深色"};
        final int[] modes = {Store.THEME_SYSTEM, Store.THEME_LIGHT, Store.THEME_DARK};
        int cur = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == store.themeMode()) cur = i;
        Picker.show(host, "主题", labels, cur, i -> {
            store.setThemeMode(modes[i]);
            if (host instanceof MainActivity) {
                ((MainActivity) host).recreate();
            }
        });
    }
}
