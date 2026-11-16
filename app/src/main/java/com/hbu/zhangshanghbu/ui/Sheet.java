// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.hbu.zhangshanghbu.core.Kit;

/**
 * 底部弹层：内容自己搭，容器负责遮罩、圆角、下落动画、下拉关闭。
 * 全工程所有「详情 / 编辑 / 选择」都走它，保证手感一致。
 */
public final class Sheet {

    public static final class Handle {
        private final Dialog dialog;
        private final View card;
        private final View scrim;
        private final int height;
        private boolean closing;

        Handle(Dialog d, View card, View scrim, int height) {
            this.dialog = d;
            this.card = card;
            this.scrim = scrim;
            this.height = height;
        }

        public void dismiss() {
            if (closing) return;
            closing = true;
            card.animate().translationY(height).setDuration(180)
                    .withEndAction(() -> {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {
                        }
                    }).start();
            scrim.animate().alpha(0f).setDuration(150).start();
        }

        public boolean showing() {
            return dialog.isShowing();
        }
    }

    private Sheet() {}

    public static Handle show(Activity host, View content, int heightPx) {
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        int h = heightPx > 0 ? Math.min(heightPx, screenH - Kit.dp(host, 60))
                : Math.min(screenH * 3 / 4, Kit.dp(host, 420));

        Dialog dlg = new Dialog(host, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(host);

        View scrim = new View(host);
        scrim.setBackgroundColor(0x66000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Kit.surface(host));
        bg.setCornerRadii(new float[]{Kit.dp(host, 20), Kit.dp(host, 20),
                Kit.dp(host, 20), Kit.dp(host, 20), 0, 0, 0, 0});
        card.setBackground(bg);
        card.setElevation(Kit.dp(host, 12));

        FrameLayout barWrap = new FrameLayout(host);
        barWrap.setPadding(0, Kit.dp(host, 8), 0, Kit.dp(host, 4));
        View bar = new View(host);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Kit.line(host));
        g.setCornerRadius(Kit.dp(host, 2));
        bar.setBackground(g);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Kit.dp(host, 36), Kit.dp(host, 4));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        barWrap.addView(bar, blp);
        card.addView(barWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        clp.gravity = Gravity.BOTTOM;
        root.addView(card, clp);

        dlg.setContentView(root);
        Window win = dlg.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x00000000));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setGravity(Gravity.BOTTOM);
            win.setStatusBarColor(0x66000000);
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        Handle handle = new Handle(dlg, card, scrim, h);
        scrim.setOnClickListener(v -> handle.dismiss());

        // 下拉关闭：拖过 80dp 或快速下滑
        final float[] down = {0f, 0f};
        barWrap.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                down[0] = e.getRawY();
                down[1] = e.getRawY();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dy = Math.max(0, e.getRawY() - down[0]);
                card.setTranslationY(dy);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                float dy = Math.max(0, e.getRawY() - down[0]);
                if (dy > Kit.dp(host, 80)) {
                    handle.dismiss();
                } else {
                    card.animate().translationY(0).setDuration(160).start();
                }
                return true;
            }
            return false;
        });

        dlg.setOnShowListener(x -> {
            card.setTranslationY(h);
            card.animate().translationY(0).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(180).start();
        });
        dlg.show();
        return handle;
    }

    /** 常用小工具：给弹层内容加统一内边距。 */
    public static LinearLayout column(Activity host, float padDp) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Kit.dp(host, padDp);
        box.setPadding(p, 0, p, p);
        return box;
    }

    public static Context ctx(Activity a) {
        return a;
    }
}
