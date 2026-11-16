// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;
import com.hbu.zhangshanghbu.ui.Dialog;
import com.hbu.zhangshanghbu.ui.Sheet;

import java.util.List;

/**
 * 课表管理：可以有多份课表（本学期、下学期、考研自习…），自由增删切换。
 *
 * 数据按 id 分开存，切换只是改一个指针，不会互相覆盖。
 */
public class TablesActivity extends PageActivity {

    private Store store;

    @Override
    protected String title() {
        return "课表管理";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        content.addView(Kit.text(this, "一份课表对应一个学期或一套安排，随时切换，互不影响。",
                12f, Kit.sub(this)));
        gap(12);

        List<Store.Meta> all = store.tables();
        String active = store.activeId();

        LinearLayout card = card();
        for (int i = 0; i < all.size(); i++) {
            Store.Meta m = all.get(i);
            Table t = store.loadTable(m.id);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 10), Kit.dp(this, 12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = Kit.text(this, m.name, 14.5f, Kit.ink(this));
            if (m.id.equals(active)) name.setTypeface(Typeface.DEFAULT_BOLD);
            info.addView(name);
            TextView sub = Kit.text(this, t.semester.name + " · " + t.courses.size() + " 门课"
                    + (m.id.equals(active) ? " · 当前使用" : ""), 11f,
                    m.id.equals(active) ? Kit.brand(this) : Kit.faint(this));
            sub.setPadding(0, Kit.dp(this, 2), 0, 0);
            info.addView(sub);
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView more = Kit.text(this, "⋯", 18f, Kit.faint(this));
            more.setPadding(Kit.dp(this, 12), 0, Kit.dp(this, 8), Kit.dp(this, 4));
            more.setOnClickListener(v -> menu(m));
            row.addView(more);

            row.setClickable(true);
            row.setBackground(Kit.ripple(this, 12));
            row.setOnClickListener(v -> {
                store.setActive(m.id);
                recreate();
            });

            card.addView(row);
            if (i < all.size() - 1) card.addView(Kit.hr(this));
        }
        content.addView(card);

        gap(14);
        content.addView(Kit.primary(this, "新建一份课表", this::add));

        gap(10);
        content.addView(Kit.text(this, "新建后是一张空表：可以手动加课，也可以进「课表设置 → 从教务导入」。",
                11f, Kit.faint(this)));
    }

    private void menu(Store.Meta m) {
        String[] items = {"重命名", "删除"};
        com.hbu.zhangshanghbu.ui.Picker.show(this, m.name, items, -1, i -> {
            if (i == 0) rename(m);
            else remove(m);
        });
    }

    private void rename(Store.Meta m) {
        EditText input = new EditText(this);
        input.setText(m.name);
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(Kit.ink(this));
        input.setBackground(Kit.stroke(this, Kit.night(this) ? 0xFF1F2836 : 0xFFF3F5F9,
                10, Kit.line(this)));
        input.setPadding(Kit.dp(this, 12), Kit.dp(this, 10), Kit.dp(this, 12), Kit.dp(this, 10));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(this, 20), 0, Kit.dp(this, 20), Kit.dp(this, 16));
        box.addView(Kit.bold(this, "重命名课表", 16f, Kit.ink(this)));
        box.addView(Kit.gap(this, 10));
        box.addView(input);
        box.addView(Kit.gap(this, 14));

        Sheet.Handle[] h = new Sheet.Handle[1];
        box.addView(Kit.primary(this, "保存", () -> {
            String n = input.getText().toString().trim();
            if (n.isEmpty()) {
                input.setError("名字不能为空");
                return;
            }
            store.renameTable(m.id, n);
            if (h[0] != null) h[0].dismiss();
            recreate();
        }));
        h[0] = Sheet.show(this, box, Kit.dp(this, 250));
    }

    private void remove(Store.Meta m) {
        if (store.tables().size() <= 1) {
            Kit.toast(this, "至少保留一份课表");
            return;
        }
        Dialog.confirm(this, "删除课表", "「" + m.name + "」和里面的课程会一起删掉。", "取消", "删除", () -> {
            store.deleteTable(m.id);
            recreate();
        });
    }

    private void add() {
        EditText input = new EditText(this);
        input.setHint("例如 2026-2027 第二学期");
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(Kit.ink(this));
        input.setHintTextColor(Kit.faint(this));
        input.setBackground(Kit.stroke(this, Kit.night(this) ? 0xFF1F2836 : 0xFFF3F5F9,
                10, Kit.line(this)));
        input.setPadding(Kit.dp(this, 12), Kit.dp(this, 10), Kit.dp(this, 12), Kit.dp(this, 10));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(this, 20), 0, Kit.dp(this, 20), Kit.dp(this, 16));
        box.addView(Kit.bold(this, "新建课表", 16f, Kit.ink(this)));
        box.addView(Kit.gap(this, 10));
        box.addView(input);
        box.addView(Kit.gap(this, 14));

        Sheet.Handle[] h = new Sheet.Handle[1];
        box.addView(Kit.primary(this, "创建", () -> {
            String n = input.getText().toString().trim();
            store.addTable(n.isEmpty() ? null : n);
            if (h[0] != null) h[0].dismiss();
            recreate();
        }));
        h[0] = Sheet.show(this, box, Kit.dp(this, 250));
    }
}
