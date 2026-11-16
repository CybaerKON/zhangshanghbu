// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/**
 * 两向滚动容器，但**双指时让路**。
 *
 * 普通的 ScrollView 在手指移动超过阈值时会把事件抢走（onInterceptTouchEvent 返回 true），
 * 之后第二根手指的 ACTION_POINTER_DOWN 就送不到子 View，
 * 课表的双指缩放会只生效第一下、然后变成滚动。
 *
 * 这里只在「单指」时按正常逻辑拦截；一旦检测到多指就直接放弃拦截，
 * 缩放事件完整落到 Grid 自己手里。
 */
public final class PinchScroll {

    private PinchScroll() {}

    public static class Vertical extends ScrollView {
        public Vertical(Context c) {
            super(c);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getPointerCount() > 1) return false;
            return super.onInterceptTouchEvent(e);
        }
    }

    public static class Horizontal extends HorizontalScrollView {
        public Horizontal(Context c) {
            super(c);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getPointerCount() > 1) return false;
            return super.onInterceptTouchEvent(e);
        }
    }
}
