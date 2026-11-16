// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;

/**
 * 选项选择器：标题 + 一串可点行 + 当前项打勾。
 * 节次、星期、学期、主题、入口……都复用它。
 */
public final class Picker {

    public interface OnPick {
        void onPick(int index);
    }

    private Picker() {}

    public static void show(Activity host, String title, String[] options, int selected, OnPick pick) {
        LinearLayout col = Sheet.column(host, 20);
        col.addView(Kit.bold(host, title, 15.5f, Kit.ink(host)));
        col.addView(Kit.gap(host, 8));

        ScrollView sc = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);

        final Sheet.Handle[] handle = new Sheet.Handle[1];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = Kit.dp(host, 12);
            row.setPadding(p, p, p, p);
            row.setBackground(Kit.ripple(host, 10));

            TextView tv = Kit.text(host, options[i], 14.5f,
                    i == selected ? Kit.brand(host) : Kit.ink(host));
            if (i == selected) tv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == selected) {
                row.addView(Kit.text(host, "✓", 15f, Kit.brand(host)));
            }
            row.setClickable(true);
            row.setOnClickListener(v -> {
                if (handle[0] != null) handle[0].dismiss();
                if (pick != null) pick.onPick(idx);
            });
            list.addView(row);
        }
        col.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int rows = Math.min(options.length, 7);
        int h = Kit.dp(host, 74 + rows * 46);
        handle[0] = Sheet.show(host, col, h);
    }
}
