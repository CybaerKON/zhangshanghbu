// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;

/** 两个按钮的确认框。 */
public final class Dialog {

    private Dialog() {}

    /** 只有一个「知道了」的提示框，用来把失败原因说清楚。 */
    public static void alert(Activity host, String title, String message) {
        confirm(host, title, message, null, "知道了", null);
    }

    public static void confirm(Activity host, String title, String message,
                               String cancel, String ok, Runnable onOk) {
        if (host == null || host.isFinishing()) return;

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(host, 22), Kit.dp(host, 22), Kit.dp(host, 22), Kit.dp(host, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Kit.surface(host));
        bg.setCornerRadius(Kit.dp(host, 18));
        box.setBackground(bg);

        box.addView(Kit.bold(host, title, 16f, Kit.ink(host)));
        TextView msg = Kit.text(host, message, 13f, Kit.sub(host));
        msg.setPadding(0, Kit.dp(host, 8), 0, Kit.dp(host, 18));
        box.addView(msg);

        LinearLayout btns = new LinearLayout(host);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);

        final android.app.Dialog[] dlg = new android.app.Dialog[1];

        if (cancel != null && !cancel.isEmpty()) {
            TextView c = Kit.text(host, cancel, 14f, Kit.sub(host));
            c.setPadding(Kit.dp(host, 14), Kit.dp(host, 9), Kit.dp(host, 14), Kit.dp(host, 9));
            c.setClickable(true);
            c.setOnClickListener(v -> dlg[0].dismiss());
            btns.addView(c);
        }

        TextView o = Kit.text(host, ok, 14f, Kit.brand(host));
        o.setTypeface(Typeface.DEFAULT_BOLD);
        o.setPadding(Kit.dp(host, 14), Kit.dp(host, 9), Kit.dp(host, 8), Kit.dp(host, 9));
        o.setClickable(true);
        o.setOnClickListener(v -> {
            dlg[0].dismiss();
            if (onOk != null) onOk.run();
        });
        btns.addView(o);

        box.addView(btns, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.app.Dialog d = new android.app.Dialog(host, android.R.style.Theme_Translucent_NoTitleBar);
        android.widget.FrameLayout root = new android.widget.FrameLayout(host);
        android.view.View scrim = new android.view.View(host);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> d.dismiss());
        root.addView(scrim, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                Kit.dp(host, 300), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(box, lp);
        d.setContentView(root);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            /* 弹窗铺满整屏时，系统栏也跟着变成半透明遮罩，否则顶上会留一条纯黑 */
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(0x66000000);
            w.setNavigationBarColor(0x66000000);
        }
        dlg[0] = d;
        d.show();
    }
}
