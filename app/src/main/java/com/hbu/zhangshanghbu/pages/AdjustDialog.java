// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.ui.Picker;
import com.hbu.zhangshanghbu.ui.Sheet;

import java.util.ArrayList;
import java.util.List;

/**
 * 临时调课：把「这一周这一节」挪到另一天 / 另一节，原位置显示「调走」。
 *
 * 只影响这一次课，不动课程本身 —— 下周照常。停课走另一条路（详情里的「本周停课」）。
 */
public final class AdjustDialog {

    private AdjustDialog() {}

    public static void show(Activity host, Table table, Table.Course course, Table.Session session,
                            int week, int day, Runnable onSaved) {
        Store store = new Store(host);
        Schedule sch = store.schedule();

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 20), 0, Kit.dp(host, 20), Kit.dp(host, 18));

        box.addView(Kit.bold(host, "临时调课", 17f, Kit.ink(host)));
        TextView hint = Kit.text(host, "把「" + course.name + "」第 " + week + " 周 "
                + Cal.dayName(day) + " 第 " + session.start + " 节 挪到别处，只影响这一次。", 12.5f, Kit.sub(host));
        hint.setPadding(0, Kit.dp(host, 8), 0, Kit.dp(host, 12));
        box.addView(hint);

        final int[] toWeek = {week};
        final int[] toDay = {day};
        final int[] toStart = {Math.min(sch.sectionCount, session.start)};

        TextView weekRow = pick(host, "调到第几周", "第 " + toWeek[0] + " 周");
        weekRow.setOnClickListener(v -> {
            List<String> opts = new ArrayList<>();
            int max = Math.max(table.semester.weeks, week + 4);
            for (int i = 1; i <= max; i++) opts.add("第 " + i + " 周");
            Picker.show(host, "周次", opts.toArray(new String[0]), toWeek[0] - 1, i -> {
                toWeek[0] = i + 1;
                weekRow.setText("调到第几周      第 " + toWeek[0] + " 周");
            });
        });
        box.addView(weekRow);

        TextView dayRow = pick(host, "调到星期", Cal.dayName(toDay[0]));
        dayRow.setOnClickListener(v -> {
            String[] opts = new String[7];
            for (int i = 0; i < 7; i++) opts[i] = Cal.dayName(i + 1);
            Picker.show(host, "星期", opts, toDay[0] - 1, i -> {
                toDay[0] = i + 1;
                dayRow.setText("调到星期      " + Cal.dayName(toDay[0]));
            });
        });
        box.addView(dayRow);

        TextView secRow = pick(host, "调到第几节", "第 " + toStart[0] + " 节");
        secRow.setOnClickListener(v -> {
            List<String> opts = new ArrayList<>();
            for (int i = 1; i <= sch.sectionCount; i++) opts.add("第 " + i + " 节");
            Picker.show(host, "节次", opts.toArray(new String[0]), toStart[0] - 1, i -> {
                toStart[0] = i + 1;
                secRow.setText("调到第几节      第 " + toStart[0] + " 节");
            });
        });
        box.addView(secRow);

        box.addView(Kit.gap(host, 14));
        TextView save = Kit.primary(host, "确定调课", () -> {
            Table.Adjust a = new Table.Adjust();
            a.id = "a" + System.currentTimeMillis();
            a.type = Table.Adjust.MOVE;
            a.course = course.name;
            a.week = week;
            a.day = day;
            a.start = session.start;
            a.end = session.end;
            a.toWeek = toWeek[0];
            a.toDay = toDay[0];
            a.toStart = toStart[0];
            a.note = "第 " + week + " 周 " + Cal.dayName(day) + " → 第 " + toWeek[0] + " 周 " + Cal.dayName(toDay[0]);
            table.adjusts.add(a);
            if (onSaved != null) onSaved.run();
        });
        box.addView(save);

        Sheet.show(host, box, Kit.dp(host, 380));
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
