// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.campus.Engine;
import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Demo;
import com.hbu.zhangshanghbu.data.Palette;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.feat.TableSettingsActivity;
import com.hbu.zhangshanghbu.ui.Grid;
import com.hbu.zhangshanghbu.ui.Sheet;
import com.hbu.zhangshanghbu.ui.Dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表页：一周一屏，左右翻周；顶部是这份课表的名字与周次，右上角进设置。
 *
 * 「导入」现在是自动的：点一下 → 检查/补齐登录 → 无头 WebView 进教务 → agent.js
 * 自动找课表页 → 返回记录 → 落盘。没有「去网页里手动点一下读取」这种步骤。
 */
public class TablePage extends FrameLayout {

    private final Activity host;
    private final Store store;

    private final TextView title;
    private final TextView sub;
    private final TextView weekLabel;
    private final TextView range;
    private final Grid grid;
    private final LinearLayout emptyBox;
    private final LinearLayout weekRow;
    private TextView zoomBtn;

    private Table table;
    private Schedule schedule;
    private int week = 1;
    private int weeks = 20;
    private Sheet.Handle sheet;

    public TablePage(Activity host, Store store) {
        super(host);
        this.host = host;
        this.store = store;
        setBackgroundColor(Kit.page(host));

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Kit.dp(host, 18), Kit.dp(host, 8), Kit.dp(host, 14), Kit.dp(host, 2));

        LinearLayout line = new LinearLayout(host);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        title = Kit.bold(host, "课表", 17f, Kit.ink(host));
        line.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        /* 缩放按钮：双指也能缩放，但双指在模拟器/部分平板上不好按，
           给一个点得动的入口（1× → 1.5× → 2× 循环）。 */
        zoomBtn = Kit.text(host, "缩放", 12.5f, Kit.brand(host));
        zoomBtn.setPadding(Kit.dp(host, 11), Kit.dp(host, 6), Kit.dp(host, 11), Kit.dp(host, 6));
        zoomBtn.setBackground(Kit.round(host, Kit.night(host) ? 0xFF12253C : 0xFFE7EFF9, 10));
        zoomBtn.setOnClickListener(v -> cycleZoom());
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zlp.rightMargin = Kit.dp(host, 8);
        line.addView(zoomBtn, zlp);

        /* 缩放按钮：双指也能缩放，但双指在模拟器/部分平板上不好按，
           给一个点得动的入口（1× → 1.5× → 2× 循环）。 */
        TextView set = Kit.text(host, "设置", 12.5f, Kit.brand(host));
        set.setPadding(Kit.dp(host, 12), Kit.dp(host, 6), Kit.dp(host, 12), Kit.dp(host, 6));
        set.setBackground(Kit.round(host, Kit.night(host) ? 0xFF12253C : 0xFFE7EFF9, 10));
        set.setOnClickListener(v -> host.startActivity(new android.content.Intent(host, TableSettingsActivity.class)));
        line.addView(set);
        head.addView(line);

        sub = Kit.text(host, "", 11.5f, Kit.faint(host));
        sub.setPadding(0, Kit.dp(host, 2), 0, 0);
        head.addView(sub);

        // 周次
        weekRow = new LinearLayout(host);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setGravity(Gravity.CENTER_VERTICAL);
        weekRow.setPadding(0, Kit.dp(host, 6), 0, Kit.dp(host, 6));

        TextView prev = navBtn("‹", () -> setWeek(week - 1));
        weekRow.addView(prev);

        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.setOnClickListener(v -> today());
        weekLabel = Kit.bold(host, "第 1 周", 15f, Kit.ink(host));
        weekLabel.setGravity(Gravity.CENTER);
        mid.addView(weekLabel);
        range = Kit.text(host, "", 10.5f, Kit.faint(host));
        range.setGravity(Gravity.CENTER);
        mid.addView(range);
        weekRow.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        weekRow.addView(navBtn("›", () -> setWeek(week + 1)));
        head.addView(weekRow);
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* 网格外面套两层滚动容器：竖向 ScrollView + 横向 HorizontalScrollView。
           缩放是整页等比的，所以两个方向都可能溢出；只套竖向的话，
           放大后右半边会被裁掉、还不回来。 */
        final com.hbu.zhangshanghbu.ui.PinchScroll.Vertical vScroll =
                new com.hbu.zhangshanghbu.ui.PinchScroll.Vertical(host);
        vScroll.setFillViewport(true);
        vScroll.setVerticalScrollBarEnabled(false);
        final com.hbu.zhangshanghbu.ui.PinchScroll.Horizontal hScroll =
                new com.hbu.zhangshanghbu.ui.PinchScroll.Horizontal(host);
        hScroll.setFillViewport(true);
        hScroll.setHorizontalScrollBarEnabled(false);

        FrameLayout box = new FrameLayout(host);
        box.setPadding(Kit.dp(host, 8), 0, Kit.dp(host, 8), Kit.dp(host, 2));
        grid = new Grid(host);
        grid.setOnCourse(this::openCourse);
        grid.setOnEmpty((day, section) -> CourseEditor.show(host, store, table, day, section, null, () -> {
            store.saveTable(table);
            changed();
        }));
        box.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyBox = new LinearLayout(host);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        buildEmpty();
        box.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hScroll.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        vScroll.addView(hScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* 把可视区域告诉网格：zoom=1 时正好铺满，放大后按比例溢出、可两向滚动 */
        vScroll.addOnLayoutChangeListener((v, l2, t2, r2, b2, ol, ot, or, ob) -> {
            int vw = r2 - l2;
            int vh = b2 - t2;
            // 内边距要扣掉，否则 zoom=1 时会多出一小截可滚动区域
            vw -= box.getPaddingLeft() + box.getPaddingRight();
            grid.setViewport(vw, vh);
        });
        col.addView(vScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final GestureDetector gd = new GestureDetector(host, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                /* 放大状态下横向拖动是「看右半边」，不能再当翻周用 */
                if (grid.zoom() > 1.01f) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > Math.abs(e2.getY() - e1.getY()) && Math.abs(dx) > Kit.dp(host, 48)) {
                    setWeek(week + (dx < 0 ? 1 : -1));
                    return true;
                }
                return false;
            }
        });
        grid.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return false;
        });
    }

    /** 1× → 1.5× → 2× → 1×，和双指缩放共用同一个 zoom。 */
    private void cycleZoom() {
        float z = grid.zoom();
        float next = z < 1.3f ? 1.5f : (z < 1.9f ? 2f : 1f);
        grid.setZoom(next);
        updateZoomLabel();
        if (next > 1f) Kit.toast(host, "已放大到 " + (next == 1.5f ? "1.5×" : "2×") + "，双指也可缩放");
    }

    private void updateZoomLabel() {
        if (zoomBtn == null) return;
        float z = grid.zoom();
        zoomBtn.setText(z < 1.05f ? "缩放" : (z < 1.6f ? "1.5×" : "2×"));
    }

    private TextView navBtn(String s, Runnable r) {
        TextView t = Kit.text(host, s, 18f, Kit.sub(host));
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(Kit.dp(host, 40), Kit.dp(host, 34)));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void buildEmpty() {
        LinearLayout card = Kit.card(host, 20);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(Kit.bold(host, "还没有课表", 16f, Kit.ink(host)));
        TextView s = Kit.text(host, "用校园网账号登录一次，程序会自己进教务把课表读回来；\n之后断网也能看。", 12.5f, Kit.sub(host));
        s.setGravity(Gravity.CENTER);
        s.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        s.setPadding(0, Kit.dp(host, 6), 0, Kit.dp(host, 14));
        card.addView(s);

        card.addView(Kit.primary(host, "登录并自动导入", this::importFromCampus));

        TextView demo = Kit.text(host, "先放一份示例课表看看", 12.5f, Kit.brand(host));
        demo.setGravity(Gravity.CENTER);
        demo.setPadding(0, Kit.dp(host, 12), 0, 0);
        demo.setOnClickListener(v -> {
            Demo.load(store);
            reload();
            changed();
        });
        card.addView(demo);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels - Kit.dp(host, 60),
                        Kit.dp(host, 330)), ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyBox.addView(card, lp);
    }

    // ---------- 数据 ----------

    public void reload() {
        table = store.activeTable();
        schedule = store.schedule();
        boolean empty = table.empty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        grid.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        weekRow.setVisibility(empty ? View.GONE : View.VISIBLE);

        title.setText(table.name);
        if (empty) {
            sub.setText("没有本地课表");
            grid.setData(null, 1);
            return;
        }

        String start = table.semester.start;
        weeks = Math.max(table.semester.weeks, Math.max(18, table.maxWeek()));
        if (start.isEmpty()) {
            start = Cal.guessStart(table.semester.id);
            table.semester.start = start;
            store.saveTable(table);
        }
        int cur = Cal.currentWeek(start, weeks);
        if (week <= 1 || week > weeks) week = cur;

        sub.setText(table.semester.name + " · " + table.courses.size() + " 门课"
                + (store.activeId().equals(table.id) ? "" : ""));
        grid.setSchedule(schedule);
        grid.setData(table, week);
        applyWeek();
    }

    public void onShown() {
        reload();
    }

    public void today() {
        table = store.activeTable();
        String start = table.semester.start.isEmpty()
                ? Cal.guessStart(table.semester.id) : table.semester.start;
        if (start.isEmpty()) return;
        week = Cal.currentWeek(start, Math.max(18, table.maxWeek()));
        applyWeek();
    }

    private void setWeek(int w) {
        if (table == null || table.empty()) return;
        week = Math.max(1, Math.min(weeks, w));
        applyWeek();
    }

    private void applyWeek() {
        if (table == null) return;
        weekLabel.setText("第 " + week + " 周");
        range.setText(Cal.weekRange(table.semester.start, week));
        grid.setWeekDates(Cal.weekDates(table.semester.start, week));
        grid.setData(table, week);
    }

    private void changed() {
        reload();
        if (host instanceof MainActivity) ((MainActivity) host).onTableChanged();
    }

    // ---------- 导入 ----------

    private void importFromCampus() {
        Session.ensure(host, new String[]{Portal.P_JW}, () -> {
            Kit.toast(host, "正在读取教务课表…");
            Engine.fetchUrpByLabel(host, Portal.URP_COURSE, Portal.courseLabel(), Portal.MENU_COURSE,
                    "courses", (ok, json, msg) -> {
                if (!ok) {
                    /* 失败原因要看得见：是没登录、没找到入口，还是页面结构变了 */
                    com.hbu.zhangshanghbu.ui.Dialog.alert(host, "读取课表失败", msg);
                    return;
                }
                Table fresh = Parse.urpCourses(json, store.activeId(), store.tableName(store.activeId()));
                if (fresh.empty()) {
                    Kit.toast(host, "没读到课，可能是页面结构变了");
                    return;
                }
                // 保留手动加的课，其余以教务为准
                Table old = store.activeTable();
                for (Table.Course c : old.courses) {
                    if (Table.SRC_CUSTOM.equals(c.source)) fresh.courses.add(c);
                }
                if (fresh.semester.start.isEmpty()) {
                    fresh.semester.start = old.semester.start.isEmpty()
                            ? Cal.guessStart(fresh.semester.id) : old.semester.start;
                }
                store.saveTable(fresh);
                Kit.toast(host, "已导入 " + fresh.courses.size() + " 门课");
                changed();
            });
        });
    }

    // ---------- 课程详情 ----------

    private void openCourse(Table.Course c, Table.Session s, int day, int start) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 20), 0, Kit.dp(host, 20), Kit.dp(host, 16));

        box.addView(Kit.bold(host, Table.cleanName(c.name), 18f, Kit.ink(host)));

        StringBuilder meta = new StringBuilder();
        if (!c.teacher.isEmpty()) meta.append(c.teacher);
        if (c.credit > 0) meta.append(meta.length() > 0 ? " · " : "").append(Parse.fmt(c.credit)).append(" 学分");
        if (!c.attr.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(c.attr);
        if (meta.length() > 0) {
            TextView m = Kit.text(host, meta.toString(), 12.5f, Kit.sub(host));
            m.setPadding(0, Kit.dp(host, 6), 0, 0);
            box.addView(m);
        }

        String time = schedule.startTime(s.start) + " - " + schedule.endTime(s.end);
        TextView when = Kit.text(host, Cal.dayName(day) + " · 第 " + s.start + "-" + s.end + " 节 · " + time, 13f, Kit.ink(host));
        when.setPadding(0, Kit.dp(host, 12), 0, 0);
        box.addView(when);

        if (!s.room.isEmpty()) {
            TextView room = Kit.text(host, "教室：" + s.room, 13f, Kit.sub(host));
            room.setPadding(0, Kit.dp(host, 4), 0, 0);
            box.addView(room);
        }
        TextView wk = Kit.text(host, "周次：" + (s.raw.isEmpty() ? Cal.compress(s.weeks) + " 周" : s.raw), 12f, Kit.faint(host));
        wk.setPadding(0, Kit.dp(host, 6), 0, 0);
        box.addView(wk);

        box.addView(Kit.gap(host, 14));
        box.addView(Kit.hr(host));
        box.addView(Kit.gap(host, 8));

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Kit.dp(host, 4), 0, 0);

        actions.addView(action("本周停课", 0xFFC0392B, () -> {
            Table.Adjust a = new Table.Adjust();
            a.id = "a" + System.currentTimeMillis();
            a.type = Table.Adjust.CANCEL;
            a.course = c.name;
            a.week = week;
            a.day = day;
            a.start = s.start;
            a.end = s.end;
            table.adjusts.add(a);
            store.saveTable(table);
            dismissSheet();
            changed();
            Kit.toast(host, "第 " + week + " 周这节已停课");
        }));

        actions.addView(action("本周调课", Kit.brand(host), () -> {
            dismissSheet();
            AdjustDialog.show(host, table, c, s, week, day, () -> {
                store.saveTable(table);
                changed();
            });
        }));

        actions.addView(action("编辑", Kit.brand(host), () -> {
            dismissSheet();
            CourseEditor.show(host, store, table, day, s.start, c, () -> {
                store.saveTable(table);
                changed();
            });
        }));

        actions.addView(action("删除", 0xFFC0392B, () -> {
            Dialog.confirm(host, "删除课程", "「" + c.name + "」会从这份课表里移除。", "取消", "删除", () -> {
                table.courses.remove(c);
                store.saveTable(table);
                dismissSheet();
                changed();
            });
        }));

        box.addView(actions);

        ScrollView sc = new ScrollView(host);
        sc.addView(box);
        sheet = Sheet.show(host, sc, Kit.dp(host, 360));
    }

    private TextView action(String text, int color, Runnable r) {
        TextView t = Kit.text(host, text, 13f, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Kit.dp(host, 8), Kit.dp(host, 18), Kit.dp(host, 8));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void dismissSheet() {
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
        }
    }
}
