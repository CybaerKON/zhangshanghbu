// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * 横向页面容器：四个主页面并排，切 tab 时整条带平移（不重建页面）。
 *
 * 自己实现而不上 ViewPager 的原因：全工程不引 androidx；而且这里只需要
 * 「按索引平移 + 支持手指拖动过程中的小数位置」，逻辑不到 100 行。
 */
public class Deck extends ViewGroup {

    private final Map<Integer, View> pages = new HashMap<>();
    private int slotWidth;
    private float pos = 0f;

    public Deck(Context c) {
        super(c);
    }

    public void setSlotWidth(int w) {
        if (w <= 0) return;
        slotWidth = w;
        requestLayout();
    }

    public void put(int slot, View page) {
        if (page == null) return;
        View old = pages.get(slot);
        if (old == page) return;
        if (old != null) removeView(old);
        pages.put(slot, page);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(slotWidth,
                LayoutParams.MATCH_PARENT);
        addView(page, lp);
        page.setTranslationX(slot * (float) slotWidth);
        requestLayout();
    }

    public View pageAt(int slot) {
        return pages.get(slot);
    }

    public float position() {
        return pos;
    }

    public void jumpTo(float p) {
        pos = p;
        applyTranslation();
    }

    public void animateTo(float target, long duration) {
        final float from = pos;
        if (duration <= 0 || Math.abs(target - from) < 0.0001f) {
            jumpTo(target);
            return;
        }
        final android.animation.ValueAnimator va = android.animation.ValueAnimator
                .ofFloat(from, target);
        va.setDuration(duration);
        va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        va.addUpdateListener(a -> jumpTo((float) a.getAnimatedValue()));
        va.start();
        anim = va;
    }

    private android.animation.ValueAnimator anim;

    public void cancelAnimation() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    private void applyTranslation() {
        for (Map.Entry<Integer, View> e : pages.entrySet()) {
            e.getValue().setTranslationX((e.getKey() - pos) * (float) slotWidth);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = slotWidth > 0 ? slotWidth : MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (View v : pages.values()) v.measure(exactW, exactH);
        setMeasuredDimension(Math.max(w, MeasureSpec.getSize(widthSpec)), h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = slotWidth > 0 ? slotWidth : (r - l);
        /* 所有页面都摆在原点，靠 translationX 分槽 ——
           如果这里按 slot 摆，再加上 translation，就会位移两倍、页面全跑到屏幕外 */
        for (Map.Entry<Integer, View> e : pages.entrySet()) {
            e.getValue().layout(0, 0, w, b - t);
        }
        applyTranslation();
    }
}
