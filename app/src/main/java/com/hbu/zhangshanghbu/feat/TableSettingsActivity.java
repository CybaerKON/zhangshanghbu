// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.campus.Engine;
import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Demo;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.ui.Dialog;
import com.hbu.zhangshanghbu.ui.Picker;

/**
 * 课表设置（从「课表 → 设置」进来）。
 *
 * 这里集中了所有和课表有关的开关：作息、多课表、调课、通知、节数、开学日期。
 * 「我的」页面不再放这些，避免同一个设置有两个入口。
 */
public class TableSettingsActivity extends PageActivity {

    private Store store;

    @Override
    protected String title() {
        return "课表设置";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        Table t = store.activeTable();

        content.addView(Kit.bold(this, t.name, 16f, Kit.ink(this)));
        content.addView(Kit.text(this, t.semester.name + " · " + t.courses.size() + " 门课",
                12f, Kit.sub(this)));
        content.addView(Kit.gap(this, 14));

        section("作息");
        LinearLayout time = card();
        time.addView(Kit.row(this, "调整上课时间", store.schedule().firstStart + " 开始",
                () -> startActivity(new Intent(this, TimeSettingsActivity.class))));
        TextView sum = Kit.text(this, store.schedule().summary(), 10.5f, Kit.faint(this));
        sum.setPadding(Kit.dp(this, 13), 0, Kit.dp(this, 13), Kit.dp(this, 10));
        time.addView(sum);
        time.addView(Kit.hr(this));
        time.addView(Kit.row(this, "每天节数", store.schedule().sectionCount + " 节", this::pickCount));
        content.addView(time);

        gap(16);
        section("课表");
        LinearLayout tables = card();
        tables.addView(Kit.row(this, "课表管理", "共 " + store.tables().size() + " 份",
                () -> startActivity(new Intent(this, TablesActivity.class))));
        tables.addView(Kit.hr(this));
        tables.addView(Kit.row(this, "开学日期", t.semester.start.isEmpty() ? "未设置" : t.semester.start,
                this::pickStart));
        tables.addView(Kit.hr(this));
        tables.addView(Kit.row(this, "调课 / 停课记录", t.adjusts.size() + " 条",
                () -> startActivity(new Intent(this, AdjustActivity.class))));
        tables.addView(Kit.hr(this));
        tables.addView(Kit.row(this, "从教务导入", "自动读取并覆盖", this::importNow));
        content.addView(tables);

        gap(16);
        section("提醒");
        LinearLayout notice = card();
        notice.addView(Kit.row(this, "上课通知", store.noticeOn()
                        ? ("提前 " + store.noticeLead() + " 分钟") : "已关闭",
                () -> startActivity(new Intent(this, NoticeActivity.class))));
        content.addView(notice);

        gap(16);
        section("数据");
        LinearLayout data = card();
        data.addView(Kit.row(this, "放一份示例课表", "", () -> Dialog.confirm(this, "示例课表",
                "会覆盖当前这份课表的课程（手动加的课保留），随时可以重新导入。", "取消", "放进去", () -> {
                    Demo.load(store);
                    finish();
                })));
        data.addView(Kit.hr(this));
        data.addView(Kit.row(this, "清空这份课表", "", () -> Dialog.confirm(this, "清空课表",
                "只清空当前这份课表的课程，其它课表不受影响。", "取消", "清空", () -> {
                    Table cur = store.activeTable();
                    cur.courses.clear();
                    cur.adjusts.clear();
                    store.saveTable(cur);
                    finish();
                })));
        content.addView(data);
    }

    private void pickCount() {
        final int[] opts = {8, 9, 10, 11, 12, 13, 14, 15, 16};
        String[] labels = new String[opts.length];
        int cur = 4;
        for (int i = 0; i < opts.length; i++) {
            labels[i] = opts[i] + " 节";
            if (opts[i] == store.schedule().sectionCount) cur = i;
        }
        Picker.show(this, "每天节数", labels, cur, i -> {
            com.hbu.zhangshanghbu.data.Schedule s = store.schedule();
            s.sectionCount = opts[i];
            store.saveSchedule(s);
            NoticeCenter.reschedule(this);
            recreate();
        });
    }

    private void pickStart() {
        Table t = store.activeTable();
        String cur = t.semester.start.isEmpty() ? Cal.guessStart(t.semester.id) : t.semester.start;
        java.util.List<String> opts = new java.util.ArrayList<>();
        java.util.List<String> vals = new java.util.ArrayList<>();
        String base = cur.isEmpty() ? Cal.monday(Cal.today()) : Cal.monday(cur);
        for (int i = -4; i <= 6; i++) {
            String d = Cal.addDays(base, i * 7);
            opts.add(d + "（第 " + (i + 1) + " 周）");
            vals.add(d);
        }
        int sel = 4;
        for (int i = 0; i < vals.size(); i++) if (vals.get(i).equals(cur)) sel = i;
        Picker.show(this, "开学日期（第 1 周周一）", opts.toArray(new String[0]), sel, i -> {
            Table tt = store.activeTable();
            tt.semester.start = vals.get(i);
            store.saveTable(tt);
            NoticeCenter.reschedule(this);
            recreate();
        });
    }

    private void importNow() {
        Session.ensure(this, new String[]{Portal.P_JW}, () -> {
            Kit.toast(this, "正在读取教务课表…");
            Engine.fetchFirst(this, com.hbu.zhangshanghbu.campus.Portal.courseUrls(), "courses", (ok, json, msg) -> {
                if (!ok) {
                    Kit.toast(this, msg);
                    return;
                }
                Table fresh = Parse.courses(json, store.activeId(), store.tableName(store.activeId()));
                if (fresh.empty()) {
                    Kit.toast(this, "没读到课，可能页面结构变了");
                    return;
                }
                Table old = store.activeTable();
                for (Table.Course c : old.courses) {
                    if (Table.SRC_CUSTOM.equals(c.source)) fresh.courses.add(c);
                }
                if (fresh.semester.start.isEmpty()) {
                    fresh.semester.start = old.semester.start.isEmpty()
                            ? Cal.guessStart(fresh.semester.id) : old.semester.start;
                }
                store.saveTable(fresh);
                NoticeCenter.reschedule(this);
                Kit.toast(this, "已导入 " + fresh.courses.size() + " 门课");
                recreate();
            });
        });
    }
}
