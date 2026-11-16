// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Palette;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页：今天上什么课 + 登录提示 + 常用入口。
 *
 * 数据全部来自本地，所以早八路上、电梯里、断网都能看。
 * 登录掉线时这里会挂一条提示，点一下弹登录窗口 —— 但只有成绩/考试这些真需要网络的功能
 * 会被拦住，课表照常显示。
 */
public class HomePage extends ScrollView {

    private final Activity host;
    private final Store store;
    private final LinearLayout page;

    public HomePage(Activity host, Store store) {
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
    }

    public void reload() {
        page.removeAllViews();
        Table t = store.activeTable();
        Schedule sch = store.schedule();

        page.addView(header(t));
        page.addView(Kit.gap(host, 12));

        String warn = Session.expiredText();
        if (!warn.isEmpty()) {
            page.addView(banner(warn));
            page.addView(Kit.gap(host, 12));
        }

        page.addView(today(t, sch));

        page.addView(Kit.gap(host, 16));
        page.addView(Kit.section(host, "快捷"));
        page.addView(quick());

        TextView foot = Kit.text(host, "课表只存本机 · 密码不经过 App", 10.5f, Kit.faint(host));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Kit.dp(host, 18), 0, Kit.dp(host, 8));
        page.addView(foot);
    }

    // ---------- 顶部 ----------

    private View header(Table t) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Kit.dp(host, 20), Kit.dp(host, 18), Kit.dp(host, 20), Kit.dp(host, 18));
        card.setBackground(Kit.round(host, 0xFF0C4C8C, 22));

        TextView brand = Kit.text(host, "河北大学 · 掌上hbu", 11.5f, 0xFFBBD6F2);
        brand.setLetterSpacing(0.08f);
        card.addView(brand);

        String[] cn = {"一", "二", "三", "四", "五", "六", "日"};
        int dow = Cal.todayDow();
        TextView date = Kit.bold(host, Cal.ymdCn(Cal.today()) + " 星期" + cn[dow - 1], 18f, 0xFFFFFFFF);
        date.setPadding(0, Kit.dp(host, 6), 0, 0);
        card.addView(date);

        String start = t.semester.start;
        String status;
        if (t.empty()) {
            status = "还没有课表，去课表页导入一次";
        } else if (start.isEmpty()) {
            status = "去课表设置里填开学日期，周次才准";
        } else {
            int week = Cal.currentWeek(start, Math.max(t.semester.weeks, t.maxWeek()));
            int left = Cal.daysToStart(start) > 0 ? Cal.daysToStart(start) : 0;
            status = "第 " + week + " 周 · " + Cal.weekRange(start, week)
                    + (left > 0 ? " · 距开学 " + left + " 天" : "")
                    + " · " + t.name;
        }
        TextView line = Kit.text(host, status, 12f, 0xFFD6E6F8);
        line.setPadding(0, Kit.dp(host, 6), 0, 0);
        card.addView(line);
        return card;
    }

    private View banner(String text) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Kit.dp(host, 14), Kit.dp(host, 11), Kit.dp(host, 12), Kit.dp(host, 11));
        box.setBackground(Kit.stroke(host, Kit.night(host) ? 0xFF2C2018 : 0xFFFFF6E8, 14, 0x33E8734A));

        TextView t = Kit.text(host, text + "\n成绩、考试等需要校园网的功能暂不可用", 11.5f, 0xFF9A5B12);
        box.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView go = Kit.text(host, "去登录", 12f, 0xFFE8734A);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setPadding(Kit.dp(host, 8), Kit.dp(host, 4), Kit.dp(host, 4), Kit.dp(host, 4));
        go.setOnClickListener(v -> {
            if (host instanceof MainActivity) ((MainActivity) host).login();
        });
        box.addView(go);
        return box;
    }

    // ---------- 今日 ----------

    private View today(Table t, Schedule sch) {
        LinearLayout card = Kit.card(host, 14);
        card.addView(Kit.section(host, "今天"));

        if (t.empty()) {
            card.addView(Kit.text(host, "还没有课表。", 13.5f, Kit.sub(host)));
            card.addView(Kit.gap(host, 10));
            TextView go = Kit.primary(host, "去课表页导入", () -> {
                if (host instanceof MainActivity) ((MainActivity) host).switchTab(1);
            });
            card.addView(go);
            return card;
        }

        int start = 0;
        int week = 1;
        if (!t.semester.start.isEmpty()) {
            week = Cal.currentWeek(t.semester.start, Math.max(t.semester.weeks, t.maxWeek()));
        }
        List<Table.Item> items = t.itemsOn(week, Cal.todayDow());
        if (items.isEmpty()) {
            card.addView(Kit.text(host, "今天没课，好好休息。", 13.5f, Kit.sub(host)));
            return card;
        }

        // 按开始节次排
        items.sort((a, b) -> Integer.compare(a.start, b.start));
        for (int i = 0; i < items.size(); i++) {
            Table.Item it = items.get(i);
            if (i > 0) card.addView(Kit.hr(host));
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Kit.dp(host, 9), 0, Kit.dp(host, 9));

            TextView time = Kit.bold(host, sch.startTime(it.start), 13f, Kit.ink(host));
            row.addView(time, new LinearLayout.LayoutParams(Kit.dp(host, 52),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            View bar = new View(host);
            bar.setBackground(Kit.round(host, Palette.main(it.course.color), 3));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Kit.dp(host, 3),
                    Kit.dp(host, 26));
            blp.rightMargin = Kit.dp(host, 10);
            row.addView(bar, blp);

            LinearLayout info = new LinearLayout(host);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(Kit.text(host, Table.cleanName(it.course.name), 14f, Kit.ink(host)));
            StringBuilder meta = new StringBuilder();
            if (!it.session.room.isEmpty()) meta.append(it.session.room);
            if (!it.session.teacher.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(it.session.teacher);
            meta.append(meta.length() > 0 ? " · " : "").append("第 ").append(it.start);
            if (it.end != it.start) meta.append("-").append(it.end);
            meta.append(" 节");
            if (!it.label.isEmpty()) meta.append(" · ").append(it.label);
            TextView m = Kit.text(host, meta.toString(), 11.5f, Kit.sub(host));
            m.setPadding(0, Kit.dp(host, 2), 0, 0);
            info.addView(m);
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(row);
        }
        return card;
    }

    // ---------- 快捷 ----------

    private View quick() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tile("课表", "本周安排", 0xFF3B7BE8, () -> {
            if (host instanceof MainActivity) ((MainActivity) host).switchTab(1);
        }));
        row.addView(tile("圈子", "成绩 / 体测 / 公交", 0xFF3FA37A, () -> {
            if (host instanceof MainActivity) ((MainActivity) host).switchTab(2);
        }));
        row.addView(tile("登录", Session.expired().isEmpty() ? "校园网账号" : "有平台掉线", 0xFFE8734A,
                () -> {
                    if (host instanceof MainActivity) ((MainActivity) host).login();
                }));
        return row;
    }

    private View tile(String title, String sub, int color, Runnable onClick) {
        LinearLayout card = Kit.card(host, 12);
        card.setClickable(true);
        card.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Kit.dp(host, 8);
        card.setLayoutParams(lp);

        View dot = new View(host);
        dot.setBackground(Kit.round(host, color, 8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Kit.dp(host, 22), Kit.dp(host, 22));
        dlp.bottomMargin = Kit.dp(host, 8);
        card.addView(dot, dlp);

        card.addView(Kit.text(host, title, 13.5f, Kit.ink(host)));
        TextView s = Kit.text(host, sub, 10f, Kit.faint(host));
        s.setPadding(0, Kit.dp(host, 2), 0, 0);
        card.addView(s);
        return card;
    }
}
