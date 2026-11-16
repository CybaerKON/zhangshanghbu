// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.hbu.zhangshanghbu.BaseActivity;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.ui.Bar;

/**
 * 二级页的共同骨架：状态栏内边距 + 标题栏 + 可滚动内容。
 * 子类只实现 title() 和 build(content)。
 */
public abstract class PageActivity extends BaseActivity {

    protected LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pageColor());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(Bar.create(this, title(), v -> finish()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Kit.dp(this, 18), 0, Kit.dp(this, 18), Kit.dp(this, 24));
        sc.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, insets.getInsets(WindowInsets.Type.systemBars()).top, 0,
                        insets.getInsets(WindowInsets.Type.systemBars()).bottom);
                return insets;
            });
        } else {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) root.setPadding(0, getResources().getDimensionPixelSize(id), 0, 0);
        }
        build(content);
    }

    protected abstract String title();

    protected abstract void build(LinearLayout content);

    /** 一张设置卡：行之间自动加分隔线。 */
    protected LinearLayout card() {
        LinearLayout c = Kit.card(this, 2);
        return c;
    }

    protected void gap(int dp) {
        content.addView(Kit.gap(this, dp));
    }

    protected void section(String s) {
        content.addView(Kit.section(this, s));
    }

    protected View.OnClickListener back() {
        return v -> finish();
    }
}
