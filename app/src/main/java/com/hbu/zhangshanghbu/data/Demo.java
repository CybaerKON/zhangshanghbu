// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 示例课表：首次打开、断网、或者只想看看界面时用。
 * 只写进当前这份课表，随时可以删表或重新导入覆盖。
 */
public final class Demo {

    private Demo() {}

    private static final String[][] DATA = {
            {"高等数学 A(二)", "王建国", "七教 302", "1", "1", "2", "1-16"},
            {"大学物理", "李红", "九教 105", "1", "3", "4", "1-16"},
            {"大学英语（四）", "张敏", "文科楼 208", "1", "5", "6", "1-16"},
            {"数据结构", "刘鹏", "计算机楼 401", "2", "1", "2", "1-16"},
            {"体育（羽毛球）", "赵磊", "体育馆", "2", "7", "8", "1-16"},
            {"马克思主义基本原理", "陈晓东", "文科楼 101", "3", "3", "4", "1-16"},
            {"计算机网络", "孙浩", "计算机楼 305", "3", "5", "6", "1-16"},
            {"操作系统", "周颖", "计算机楼 402", "4", "1", "2", "1-16"},
            {"概率论与数理统计", "王建国", "七教 302", "4", "5", "6", "1-16"},
            {"软件工程", "刘鹏", "计算机楼 401", "5", "3", "4", "1-16"},
            {"形势与政策", "陈晓东", "文科楼 101", "5", "7", "8", "1-8"},
            {"中国近现代史纲要", "赵敏", "文科楼 305", "3", "7", "8", "9-16"},
    };

    public static Table build(String tableId, String tableName) {
        Table t = new Table();
        t.id = tableId;
        t.name = tableName;
        int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        int m = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
        int first = m >= 9 ? y : (m <= 1 ? y - 1 : y);
        int term = (m >= 9 || m == 1) ? 1 : 2;
        t.semester.id = first + "-" + (first + 1) + "-" + term;
        t.semester.name = first + "-" + (first + 1) + " 学年" + (term == 1 ? "第一学期" : "第二学期");
        t.semester.weeks = 18;
        t.semester.start = Cal.monday(Cal.today());

        int color = 0;
        for (String[] d : DATA) {
            Table.Course c = new Table.Course();
            c.key = "demo_" + d[0];
            c.name = d[0];
            c.teacher = d[1];
            c.credit = 3;
            c.attr = "必修";
            c.source = Table.SRC_DEMO;
            c.color = color++ % Palette.SIZE;

            Table.Session s = new Table.Session();
            s.day = Integer.parseInt(d[3]);
            s.start = Integer.parseInt(d[4]);
            s.end = Integer.parseInt(d[5]);
            s.room = d[2];
            s.raw = d[6] + " 周";
            s.weeks.addAll(Cal.parseWeeks(d[6]));
            c.sessions.add(s);
            t.courses.add(c);
        }
        return t;
    }

    public static void load(Store store) {
        String id = store.activeId();
        Table t = build(id, store.tableName(id));
        store.saveTable(t);
    }

    public static List<Table.Course> today(Table t, int week) {
        List<Table.Course> out = new ArrayList<>();
        if (t == null) return out;
        int day = Cal.todayDow();
        for (Table.Item it : t.itemsOn(week, day)) {
            if (!out.contains(it.course)) out.add(it.course);
        }
        return out;
    }
}
