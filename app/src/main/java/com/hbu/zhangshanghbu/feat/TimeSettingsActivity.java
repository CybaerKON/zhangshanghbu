// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.ui.Dialog;
import com.hbu.zhangshanghbu.ui.Picker;

import java.util.ArrayList;
import java.util.List;

/**
 * 调整上课时间。
 *
 * 河大的作息不是全校长年一套：不同校区、冬夏令时、上午四节/下午四节的安排都变过。
 * 所以这里把「第 1 节几点开始、每节多长、普通课间多长、大课间在第几节之后、多长」
 * 全部做成可改的，并在底部实时排出每一节的起止时间给你核对。
 *
 * 「课程可以越过大课间」是这里最容易搞错的一点：大课间只是「第 N 节之后的休息变长」，
 * 课程块按节次画，3-4 节连上的课不会被切断，只是它的结束时间把大课间算进去了。
 */
public class TimeSettingsActivity extends PageActivity {

    private Store store;
    private Schedule schedule;

    @Override
    protected String title() {
        return "调整上课时间";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        schedule = store.schedule();
        render();
    }

    private void render() {
        content.removeAllViews();

        content.addView(Kit.text(this, "改完立即生效，课表上的时间与上课通知都会跟着变。",
                12f, Kit.sub(this)));
        gap(14);

        section("基本");
        LinearLayout basic = card();
        basic.addView(Kit.row(this, "第 1 节开始", schedule.firstStart, this::pickFirst));
        basic.addView(Kit.hr(this));
        basic.addView(Kit.row(this, "每节课时长", schedule.period + " 分钟", this::pickPeriod));
        basic.addView(Kit.hr(this));
        basic.addView(Kit.row(this, "普通课间", schedule.rest + " 分钟", this::pickRest));
        basic.addView(Kit.hr(this));
        basic.addView(Kit.row(this, "每天节数", schedule.sectionCount + " 节", this::pickCount));
        content.addView(basic);

        gap(16);
        section("大课间");
        LinearLayout bigs = card();
        if (schedule.bigs.isEmpty()) {
            TextView empty = Kit.text(this, "还没有设置大课间。可以按上午/下午各加一个。", 12.5f, Kit.sub(this));
            empty.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
            bigs.addView(empty);
        } else {
            for (int i = 0; i < schedule.bigs.size(); i++) {
                Schedule.Big b = schedule.bigs.get(i);
                final int idx = i;
                LinearLayout row = Kit.row(this, "第 " + b.after + " 节之后", b.minutes + " 分钟", () -> editBig(idx));
                bigs.addView(row);
                if (i < schedule.bigs.size() - 1) bigs.addView(Kit.hr(this));
            }
        }
        bigs.addView(Kit.hr(this));
        bigs.addView(Kit.row(this, "添加一个课间", "", this::addBig));
        content.addView(bigs);

        gap(16);
        section("时间预览");
        content.addView(preview());
        gap(10);

        TextView reset = Kit.text(this, "恢复默认（8:00 开始 / 45 分钟 / 课间 10 分钟 / 两节后 20 分钟）",
                12.5f, Kit.brand(this));
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 10));
        reset.setOnClickListener(v -> Dialog.confirm(this, "恢复默认作息",
                "会覆盖当前的开始时间、节长、课间与大课间。", "取消", "恢复", () -> {
                    schedule = Schedule.defaults();
                    save();
                    render();
                }));
        content.addView(reset);
    }

    // ---------- 选择 ----------

    private void pickFirst() {
        List<String> opts = new ArrayList<>();
        for (int m = 6 * 60; m <= 10 * 60; m += 5) opts.add(Schedule.hm(m));
        int cur = Math.max(0, opts.indexOf(schedule.firstStart));
        Picker.show(this, "第 1 节开始时间", opts.toArray(new String[0]), cur, i -> {
            schedule.firstStart = opts.get(i);
            save();
            render();
        });
    }

    private void pickPeriod() {
        final int[] vals = {30, 35, 40, 45, 50, 55, 60};
        String[] labels = new String[vals.length];
        int cur = 3;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = vals[i] + " 分钟";
            if (vals[i] == schedule.period) cur = i;
        }
        Picker.show(this, "每节课时长", labels, cur, i -> {
            schedule.period = vals[i];
            save();
            render();
        });
    }

    private void pickRest() {
        final int[] vals = {0, 5, 10, 15, 20};
        String[] labels = new String[vals.length];
        int cur = 2;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = vals[i] == 0 ? "不休息" : vals[i] + " 分钟";
            if (vals[i] == schedule.rest) cur = i;
        }
        Picker.show(this, "普通课间", labels, cur, i -> {
            schedule.rest = vals[i];
            save();
            render();
        });
    }

    private void pickCount() {
        final int[] vals = {8, 9, 10, 11, 12, 13, 14, 15, 16};
        String[] labels = new String[vals.length];
        int cur = 4;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = vals[i] + " 节";
            if (vals[i] == schedule.sectionCount) cur = i;
        }
        Picker.show(this, "每天节数", labels, cur, i -> {
            schedule.sectionCount = vals[i];
            save();
            render();
        });
    }

    private void addBig() {
        List<String> opts = new ArrayList<>();
        for (int i = 1; i < schedule.sectionCount; i++) opts.add("第 " + i + " 节之后");
        Picker.show(this, "大课间位置", opts.toArray(new String[0]), 1, i -> {
            final int after = i + 1;
            final int[] mins = {15, 20, 25, 30, 40};
            String[] labels = new String[mins.length];
            for (int k = 0; k < mins.length; k++) labels[k] = mins[k] + " 分钟";
            Picker.show(this, "大课间时长", labels, 1, k -> {
                schedule.bigs.add(new Schedule.Big(after, mins[k]));
                save();
                render();
            });
        });
    }

    private void editBig(int index) {
        if (index < 0 || index >= schedule.bigs.size()) return;
        Schedule.Big b = schedule.bigs.get(index);
        final int[] mins = {5, 10, 15, 20, 25, 30, 40, 50};
        String[] labels = new String[mins.length + 1];
        int cur = 3;
        for (int i = 0; i < mins.length; i++) {
            labels[i] = mins[i] + " 分钟";
            if (mins[i] == b.minutes) cur = i;
        }
        labels[mins.length] = "删除这个大课间";
        Picker.show(this, "第 " + b.after + " 节之后", labels, cur, i -> {
            if (i == mins.length) schedule.bigs.remove(index);
            else {
                b.minutes = mins[i];
            }
            save();
            render();
        });
    }

    // ---------- 预览 ----------

    private TextView preview() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= schedule.sectionCount; i++) {
            sb.append(String.format(java.util.Locale.US, "%2d", i)).append("  ")
                    .append(schedule.startTime(i)).append(" - ").append(schedule.endTime(i));
            if (schedule.hasBigAfter(i)) sb.append("   ← 大课间 ").append(schedule.restAfter(i)).append(" 分钟");
            if (i < schedule.sectionCount) sb.append('\n');
        }
        TextView t = Kit.text(this, sb.toString(), 12.5f, Kit.ink(this));
        t.setTypeface(Typeface.MONOSPACE);
        t.setLineSpacing(Kit.dp(this, 5), 1f);
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
        t.setBackground(Kit.stroke(this, Kit.surface(this), 16, Kit.line(this)));
        return t;
    }

    private void save() {
        store.saveSchedule(schedule);
        NoticeCenter.reschedule(this);
    }
}
