// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.core;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全工程共用的界面小工具：dp、语义色、圆角卡片、文字、可点行。
 *
 * 只用手写 View，不引 androidx —— 这套界面从零写起，坐标、颜色、字号都在这里统一，
 * 免得每页各写一套 dp() 和颜色常量。
 */
public final class Kit {

    private Kit() {}

    /**
     * 内容区宽度：手机上就是满屏，平板 / 分屏 / 平行视界下居中一列，不把内容拉散。
     * 840dp 是阅读舒适上限，再宽文字行就太长了。
     */
    public static int contentWidth(android.content.Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        // 不要强制转成 Activity：这个工具随时可能从非 Activity 的 Context 调
        return Math.min(m.widthPixels, Math.round(840 * m.density));
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static boolean night(Context c) {
        return new com.hbu.zhangshanghbu.data.Store(c).darkNow();
    }

    /** 主文字色 */
    public static int ink(Context c) {
        return night(c) ? 0xFFF0F3F8 : 0xFF151A22;
    }

    /** 次要文字 */
    public static int sub(Context c) {
        return night(c) ? 0xFF9CA6B7 : 0xFF5B6474;
    }

    /** 弱化文字 */
    public static int faint(Context c) {
        return night(c) ? 0xFF6D7787 : 0xFF8B93A3;
    }

    public static int surface(Context c) {
        return night(c) ? 0xFF161D29 : 0xFFFFFFFF;
    }

    public static int page(Context c) {
        return night(c) ? 0xFF0D131C : 0xFFF2F4F8;
    }

    public static int line(Context c) {
        return night(c) ? 0xFF263040 : 0xFFE4E8F0;
    }

    public static int brand(Context c) {
        return night(c) ? 0xFF63A8F0 : 0xFF0C4C8C;
    }

    public static int onBrand() {
        return 0xFFFFFFFF;
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable stroke(Context c, int color, float radiusDp, int strokeColor) {
        GradientDrawable g = round(c, color, radiusDp);
        g.setStroke(Math.max(1, dp(c, 1)), strokeColor);
        return g;
    }

    public static Drawable ripple(Context c, float radiusDp) {
        GradientDrawable base = round(c, 0x00000000, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x220C4C8C), base, null);
    }

    /** 白卡片：圆角 + 描边 + 极轻阴影。 */
    public static LinearLayout card(Context c, float paddingDp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, paddingDp);
        box.setPadding(p, p, p, p);
        box.setBackground(stroke(c, surface(c), 18, line(c)));
        box.setElevation(dp(c, 1));
        return box;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView bold(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 12f, faint(c));
        t.setPadding(dp(c, 4), dp(c, 2), 0, dp(c, 6));
        t.setLetterSpacing(0.06f);
        return t;
    }

    public static View hr(Context c) {
        View v = new View(c);
        v.setBackgroundColor(line(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(c, 1))));
        return v;
    }

    public static View gap(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
        return v;
    }

    /** 设置行：左标题 + 右值 + 箭头。 */
    public static LinearLayout row(Context c, String title, String value, Runnable onClick) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, 13);
        r.setPadding(p, p, p, p);

        TextView t = text(c, title, 14.5f, ink(c));
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null && !value.isEmpty()) {
            TextView v = text(c, value, 12.5f, sub(c));
            v.setMaxLines(1);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setGravity(Gravity.END);
            v.setPadding(0, 0, dp(c, 6), 0);
            v.setMaxWidth(Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.46f));
            r.addView(v);
        }
        r.addView(text(c, "›", 17f, faint(c)));

        if (onClick != null) {
            r.setClickable(true);
            r.setOnClickListener(v -> onClick.run());
            r.setBackground(ripple(c, 12));
        }
        return r;
    }

    /** 主按钮 */
    public static TextView primary(Context c, String s, Runnable onClick) {
        TextView t = text(c, s, 14.5f, onBrand());
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(round(c, brand(c), 12));
        t.setPadding(0, dp(c, 12), 0, dp(c, 12));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    public static TextView chip(Context c, String s, int fg, int bg) {
        TextView t = text(c, s, 10.5f, fg);
        t.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2));
        t.setBackground(round(c, bg, 6));
        return t;
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    /** 把 View 从父容器摘掉（弹层关闭时用）。 */
    public static void detach(View v) {
        if (v == null) return;
        ViewGroup p = (ViewGroup) v.getParent();
        if (p != null) p.removeView(v);
    }

    public static int widthPx(Activity a) {
        return a.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * 把头像裁成圆形。
     *
     * ImageView 自己不会把矩形图裁圆，用 ClipPath 又要额外开层，
     * 这里直接画一张圆形位图，省得每处都写一遍。
     */
    public static android.graphics.Bitmap circle(android.graphics.Bitmap src, int size) {
        if (src == null || size <= 0) return null;
        try {
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.BitmapShader sh = new android.graphics.BitmapShader(src,
                    android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
            // 按「填满取中间」缩放，避免人像被拉扁
            float scale = Math.max((float) size / src.getWidth(), (float) size / src.getHeight());
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(scale, scale);
            m.postTranslate((size - src.getWidth() * scale) / 2f,
                    (size - src.getHeight() * scale) / 2f);
            sh.setLocalMatrix(m);
            p.setShader(sh);
            c.drawCircle(size / 2f, size / 2f, size / 2f, p);
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
