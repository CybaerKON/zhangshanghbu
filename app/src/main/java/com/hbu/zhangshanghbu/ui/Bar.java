// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;

/** 二级页面的标题栏：返回 + 标题 + 右侧动作。 */
public final class Bar {

    private Bar() {}

    public static LinearLayout create(Activity host, String title, View.OnClickListener onBack) {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Kit.dp(host, 8), Kit.dp(host, 8), Kit.dp(host, 12), Kit.dp(host, 8));

        TextView back = Kit.text(host, "‹", 26f, Kit.ink(host));
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Kit.dp(host, 8), 0, Kit.dp(host, 10), Kit.dp(host, 4));
        back.setOnClickListener(onBack);
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Kit.dp(host, 42)));

        bar.addView(Kit.bold(host, title, 17f, Kit.ink(host)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    public static TextView action(Activity host, String text, View.OnClickListener onClick) {
        TextView t = Kit.text(host, text, 13.5f, Kit.brand(host));
        t.setPadding(Kit.dp(host, 10), Kit.dp(host, 6), Kit.dp(host, 4), Kit.dp(host, 6));
        t.setClickable(true);
        t.setOnClickListener(onClick);
        return t;
    }
}
