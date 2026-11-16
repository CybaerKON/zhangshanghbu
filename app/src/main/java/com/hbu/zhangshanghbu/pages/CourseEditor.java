// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.ui.Picker;
import com.hbu.zhangshanghbu.ui.Sheet;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动加课 / 改课。
 *
 * 自动导入之外总要留一条手动路：老师临时加课、教室改了、或者某个学院的课表压根抓不到，
 * 手动加的课标成 custom，重新导入时不会被覆盖。
 */
public final class CourseEditor {

    private CourseEditor() {}

    public static void show(Activity host, Store store, Table table, int day, int section,
                            Table.Course editing, Runnable onSaved) {
        if (table == null) return;

        Table.Session base = null;
        if (editing != null) {
            for (Table.Session s : editing.sessions) {
                if (s.day == day) { base = s; break; }
            }
            if (base == null && !editing.sessions.isEmpty()) base = editing.sessions.get(0);
        }
        final Table.Session src = base;

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 20), 0, Kit.dp(host, 20), Kit.dp(host, 16));

        box.addView(Kit.bold(host, editing == null ? "添加课程" : "编辑课程", 17f, Kit.ink(host)));
        box.addView(Kit.gap(host, 8));

        box.addView(label(host, "课程名"));
        EditText name = field(host, editing == null ? "" : editing.name, "例如 高等数学 A(二)");
        box.addView(name);

        box.addView(label(host, "教师"));
        EditText teacher = field(host, editing == null ? "" : editing.teacher, "可留空");
        box.addView(teacher);

        box.addView(label(host, "教室"));
        EditText room = field(host, src == null ? "" : src.room, "例如 七教 302");
        box.addView(room);

        final int[] daySel = {src == null ? day : src.day, src == null ? section : src.start,
                src == null ? section : src.end};
        TextView dayRow = pick(host, "星期", Cal.dayName(daySel[0]));
        dayRow.setOnClickListener(v -> {
            String[] opts = new String[7];
            for (int i = 0; i < 7; i++) opts[i] = Cal.dayName(i + 1);
            Picker.show(host, "星期", opts, daySel[0] - 1, i -> {
                daySel[0] = i + 1;
                dayRow.setText("星期      " + Cal.dayName(daySel[0]));
            });
        });
        box.addView(dayRow);

        TextView secRow = pick(host, "节次", daySel[1] + " - " + daySel[2] + " 节");
        secRow.setOnClickListener(v -> {
            int n = store.schedule().sectionCount;
            List<String> opts = new ArrayList<>();
            for (int i = 1; i <= n; i++) opts.add("第 " + i + " 节");
            Picker.show(host, "开始节次", opts.toArray(new String[0]), daySel[1] - 1, i -> {
                daySel[1] = i + 1;
                if (daySel[2] < daySel[1]) daySel[2] = daySel[1];
                secRow.setText("节次      " + daySel[1] + " - " + daySel[2] + " 节");
            });
        });
        box.addView(secRow);

        TextView endRow = pick(host, "结束节次", "第 " + daySel[2] + " 节");
        endRow.setOnClickListener(v -> {
            int n = store.schedule().sectionCount;
            List<String> opts = new ArrayList<>();
            for (int i = daySel[1]; i <= n; i++) opts.add("第 " + i + " 节");
            Picker.show(host, "结束节次", opts.toArray(new String[0]), daySel[2] - daySel[1], i -> {
                daySel[2] = daySel[1] + i;
                endRow.setText("结束节次  第 " + daySel[2] + " 节");
                secRow.setText("节次      " + daySel[1] + " - " + daySel[2] + " 节");
            });
        });
        box.addView(endRow);

        box.addView(label(host, "周次（1-16 / 1-8,10-16 / 1-15 单周）"));
        EditText weeks = field(host, src == null ? "1-16" : (src.raw.isEmpty()
                ? Cal.compress(src.weeks) : src.raw), "1-16");
        weeks.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(weeks);

        box.addView(Kit.gap(host, 14));
        TextView save = Kit.primary(host, "保存", () -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) {
                name.setError("课程名不能为空");
                return;
            }
            Table.Session s = src != null ? src : new Table.Session();
            s.day = daySel[0];
            s.start = Math.min(daySel[1], daySel[2]);
            s.end = Math.max(daySel[1], daySel[2]);
            s.room = room.getText().toString().trim();
            s.raw = weeks.getText().toString().trim();
            s.weeks.clear();
            s.weeks.addAll(Cal.parseWeeks(s.raw));

            if (editing != null) {
                editing.name = nm;
                editing.teacher = teacher.getText().toString().trim();
                editing.source = Table.SRC_CUSTOM;
                if (src == null) editing.sessions.add(s);
            } else {
                Table.Course c = new Table.Course();
                c.key = "manual_" + System.currentTimeMillis();
                c.name = nm;
                c.teacher = teacher.getText().toString().trim();
                c.source = Table.SRC_CUSTOM;
                c.color = table.courses.size() % com.hbu.zhangshanghbu.data.Palette.SIZE;
                c.sessions.add(s);
                table.courses.add(c);
            }
            if (onSaved != null) onSaved.run();
        });
        box.addView(save);

        if (editing != null) {
            box.addView(Kit.gap(host, 8));
            TextView del = Kit.text(host, "删除这门课", 13f, 0xFFC0392B);
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, Kit.dp(host, 8), 0, Kit.dp(host, 8));
            del.setOnClickListener(v -> {
                table.courses.remove(editing);
                if (onSaved != null) onSaved.run();
            });
            box.addView(del);
        }

        ScrollView sc = new ScrollView(host);
        sc.addView(box);
        Sheet.show(host, sc, Math.min(Kit.dp(host, 640),
                host.getResources().getDisplayMetrics().heightPixels - Kit.dp(host, 70)));
    }

    private static TextView label(Activity host, String s) {
        TextView t = Kit.text(host, s, 11.5f, Kit.faint(host));
        t.setPadding(0, Kit.dp(host, 10), 0, Kit.dp(host, 4));
        return t;
    }

    private static EditText field(Activity host, String value, String hint) {
        EditText e = new EditText(host);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(14.5f);
        e.setSingleLine(true);
        e.setTextColor(Kit.ink(host));
        e.setHintTextColor(Kit.faint(host));
        e.setBackground(Kit.stroke(host, Kit.night(host) ? 0xFF1F2836 : 0xFFF3F5F9,
                10, Kit.line(host)));
        e.setPadding(Kit.dp(host, 12), Kit.dp(host, 10), Kit.dp(host, 12), Kit.dp(host, 10));
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private static TextView pick(Activity host, String title, String value) {
        TextView t = Kit.text(host, title + "      " + value, 14f, Kit.ink(host));
        t.setPadding(Kit.dp(host, 12), Kit.dp(host, 11), Kit.dp(host, 12), Kit.dp(host, 11));
        t.setBackground(Kit.stroke(host, Kit.night(host) ? 0xFF1F2836 : 0xFFF3F5F9,
                10, Kit.line(host)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Kit.dp(host, 8);
        t.setLayoutParams(lp);
        return t;
    }
}
