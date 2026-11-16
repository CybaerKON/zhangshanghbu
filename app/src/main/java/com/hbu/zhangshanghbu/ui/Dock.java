// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.hbu.zhangshanghbu.core.Kit;

/**
 * 底部导航：四个入口（首页 / 课表 / 圈子 / 我的），选中块带弹性滑动。
 * 图形是 Canvas 里直接画的（房子、格子、双圈、人形），不依赖图标资源。
 */
public class Dock extends View {

    public interface OnTab {
        void onTab(int index);
    }

    private static final String[] LABELS = {"首页", "课表", "圈子", "我的"};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float d;

    private int selected = 0;
    private float pos = 0f;
    private OnTab listener, reselect;
    private android.animation.ValueAnimator anim;
    private float downX, downY;
    private int pressed = -1;

    public Dock(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f * d);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
        setElevation(10f * d);
    }

    public void setOnTab(OnTab l) {
        listener = l;
    }

    public void setOnReselect(OnTab l) {
        reselect = l;
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int i, boolean animate) {
        selected = Math.max(0, Math.min(3, i));
        setContentDescription("底部导航，当前 " + LABELS[selected]);
        if (!animate || getWidth() == 0) {
            cancel();
            pos = selected;
            invalidate();
            return;
        }
        cancel();
        anim = android.animation.ValueAnimator.ofFloat(pos, selected);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator(1.3f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void cancel() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    private float pad() {
        return 6 * d;
    }

    private float cell() {
        return (getWidth() - pad() * 2) / 4f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0) return;
        boolean night = Kit.night(getContext());
        int surface = night ? 0xFF151C28 : 0xFFFFFFFF;
        int line = night ? 0xFF2A3444 : 0xFFE4E8F0;
        int on = Kit.brand(getContext());
        int off = night ? 0xFF7D8798 : 0xFF8B93A3;

        // 胶囊底
        rect.set(0, 0, w, h);
        fill.setColor(surface);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill);
        stroke.setColor(line);
        rect.set(stroke.getStrokeWidth() / 2f, stroke.getStrokeWidth() / 2f,
                w - stroke.getStrokeWidth() / 2f, h - stroke.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, stroke);

        float c = cell(), p = pad();
        float cx = p + c * (pos + 0.5f);

        // 选中块
        float pw = c * 0.92f, ph = h - 10 * d;
        rect.set(cx - pw / 2f, (h - ph) / 2f, cx + pw / 2f, (h + ph) / 2f);
        fill.setColor(night ? 0x3363A8F0 : 0x140C4C8C);
        canvas.drawRoundRect(rect, ph / 2f, ph / 2f, fill);

        for (int i = 0; i < 4; i++) {
            float icx = p + c * (i + 0.5f);
            float near = Math.max(0f, 1f - Math.abs(pos - i));
            int color = blend(off, on, near);
            drawIcon(canvas, i, icx, h * 0.40f, 19 * d, color);
            text.setColor(color);
            text.setFakeBoldText(near > 0.5f);
            canvas.drawText(LABELS[i], icx, h * 0.72f + text.getTextSize() * 0.36f, text);
        }
    }

    private void drawIcon(Canvas canvas, int which, float cx, float cy, float size, int color) {
        float s = size / 2f;
        stroke.setColor(color);
        fill.setColor(color);
        path.reset();
        switch (which) {
            case 0: // 房子
                path.moveTo(cx - s, cy + s * 0.15f);
                path.lineTo(cx - s, cy + s);
                path.lineTo(cx + s, cy + s);
                path.lineTo(cx + s, cy + s * 0.15f);
                path.moveTo(cx - s * 1.15f, cy + s * 0.1f);
                path.lineTo(cx, cy - s);
                path.lineTo(cx + s * 1.15f, cy + s * 0.1f);
                canvas.drawPath(path, stroke);
                break;
            case 1: // 课表格子
                float g = s * 1.9f;
                rect.set(cx - g / 2f, cy - g / 2f, cx + g / 2f, cy + g / 2f);
                canvas.drawRoundRect(rect, 2f * d, 2f * d, stroke);
                stroke.setStrokeWidth(1.1f * d);
                canvas.drawLine(cx - g / 2f, cy - g / 6f, cx + g / 2f, cy - g / 6f, stroke);
                canvas.drawLine(cx - g / 6f, cy - g / 6f, cx - g / 6f, cy + g / 2f, stroke);
                canvas.drawLine(cx + g / 6f, cy - g / 6f, cx + g / 6f, cy + g / 2f, stroke);
                stroke.setStrokeWidth(1.4f * d);
                break;
            case 2: // 双圈（圈子）
                canvas.drawCircle(cx - s * 0.42f, cy, s * 0.78f, stroke);
                canvas.drawCircle(cx + s * 0.42f, cy, s * 0.78f, stroke);
                break;
            default: // 人形
                canvas.drawCircle(cx, cy - s * 0.42f, s * 0.5f, stroke);
                rect.set(cx - s * 0.9f, cy + s * 0.12f, cx + s * 0.9f, cy + s * 1.5f);
                canvas.drawArc(rect, 180, 180, false, stroke);
                break;
        }
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pressed = (int) ((e.getX() - pad()) / cell());
                pressed = Math.max(0, Math.min(3, pressed));
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(e.getX() - downX) < 24 * d && Math.abs(e.getY() - downY) < 24 * d) {
                    int i = (int) ((e.getX() - pad()) / cell());
                    i = Math.max(0, Math.min(3, i));
                    if (i == selected) {
                        if (reselect != null) reselect.onTab(i);
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        if (listener != null) listener.onTab(i);
                    }
                }
                pressed = -1;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
