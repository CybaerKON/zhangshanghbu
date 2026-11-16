// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;

/**
 * 启动页：还 Cookie → 进主界面。
 *
 * 这里**没有自动登录**：河大的登录要过 WebVPN 和验证码，客户端硬登既不可靠也不该存密码。
 * 冷启动永远直接进课表（课表是本地的，断网也能看），需要登录时才弹窗口。
 */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C4C8C);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        TextView logo = Kit.bold(this, "hbu", 34f, 0xFF0C4C8C);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Kit.round(this, 0xFFFFFFFF, 22));
        col.addView(logo, new LinearLayout.LayoutParams(dp(66), dp(66)));

        TextView name = Kit.bold(this, "掌上hbu", 21f, 0xFFFFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.06f);
        name.setPadding(0, dp(14), 0, 0);
        col.addView(name);

        TextView sub = Kit.text(this, "河北大学 · 课表 / 圈子 / 空教室", 12f, 0xFFBBD6F2);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);

        TextView tip = Kit.text(this, "非学校官方发布 · 数据仅存本机", 10.5f, 0x99D6E6F8);
        tip.setTypeface(Typeface.DEFAULT);
        tip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tlp.bottomMargin = dp(34);

        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(tip, tlp);
        setContentView(root);

        // Cookie 先还回去，再初始化登录态；真正的探测放到主界面后台做
        Session.restore(this);
        Session.bootstrap(this);

        root.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 420);
    }
}
