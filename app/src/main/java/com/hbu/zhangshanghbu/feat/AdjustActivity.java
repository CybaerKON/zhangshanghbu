// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.ui.Dialog;

/**
 * 调课 / 停课记录。
 *
 * 入口在课表里点某节课（「本周停课」「本周调课」），这里只负责把已加的记录列出来、能撤销。
 */
public class AdjustActivity extends PageActivity {

    private Store store;

    @Override
    protected String title() {
        return "调课 / 停课";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        content.removeAllViews();
        Table t = store.activeTable();

        content.addView(Kit.text(this, "只影响指定的那一周；点课程就能临时停课或调到别的时间。",
                12f, Kit.sub(this)));
        gap(12);

        if (t.adjusts.isEmpty()) {
            LinearLayout card = card();
            TextView empty = Kit.text(this, "还没有调课记录。", 13f, Kit.sub(this));
            empty.setPadding(Kit.dp(this, 14), Kit.dp(this, 16), Kit.dp(this, 14), Kit.dp(this, 16));
            card.addView(empty);
            content.addView(card);
            return;
        }

        LinearLayout card = card();
        for (int i = 0; i < t.adjusts.size(); i++) {
            Table.Adjust a = t.adjusts.get(i);
            final int idx = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 10), Kit.dp(this, 12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(Kit.text(this, Table.cleanName(a.course), 14f, Kit.ink(this)));
            String desc = Table.Adjust.CANCEL.equals(a.type)
                    ? ("第 " + a.week + " 周 " + Cal.dayName(a.day) + " 第 " + a.start + " 节 · 停课")
                    : ("第 " + a.week + " 周 " + Cal.dayName(a.day) + " 第 " + a.start + " 节 → 第 "
                    + a.toWeek + " 周 " + Cal.dayName(a.toDay) + " 第 " + a.toStart + " 节");
            TextView sub = Kit.text(this, desc, 11.5f, Kit.faint(this));
            sub.setPadding(0, Kit.dp(this, 3), 0, 0);
            info.addView(sub);
            row.addView(info, new LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = Kit.text(this, "撤销", 12.5f, Kit.brand(this));
            del.setPadding(Kit.dp(this, 10), Kit.dp(this, 6), Kit.dp(this, 8), Kit.dp(this, 6));
            del.setOnClickListener(v -> Dialog.confirm(this, "撤销这条调整",
                    "撤销后这节课会回到原来的位置。", "取消", "撤销", () -> {
                        Table cur = store.activeTable();
                        if (idx < cur.adjusts.size()) {
                            cur.adjusts.remove(idx);
                            store.saveTable(cur);
                            rebuild();
                        }
                    }));
            row.addView(del);
            card.addView(row);
            if (i < t.adjusts.size() - 1) card.addView(Kit.hr(this));
        }
        content.addView(card);
    }

    private void rebuild() {
        build(content);
    }
}
