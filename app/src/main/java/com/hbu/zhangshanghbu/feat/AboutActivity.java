// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;

/**
 * 关于：版本、数据来源、参考项目、开发者与项目地址。
 *
 * 项目地址先留个占位，等仓库公开后只改 {@link #PROJECT_URL} 一处即可。
 */
public class AboutActivity extends PageActivity {

    /** 发布后替换成真实仓库地址。 */
    private static final String PROJECT_URL = "https://github.com/CybaerKON/zhangshanghbu";
    private static final String PROJECT_URL_TEXT = "CybaerKON/zhangshanghbu";
    private static final String DEVELOPER = "@二相穆者波形";

    @Override
    protected String title() {
        return "关于";
    }

    @Override
    protected void build(LinearLayout content) {
        // 头部
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 20));

        TextView logo = Kit.bold(this, "hbu", 22f, 0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Kit.round(this, 0xFF0C4C8C, 18));
        head.addView(logo, new LinearLayout.LayoutParams(Kit.dp(this, 62), Kit.dp(this, 62)));

        TextView name = Kit.bold(this, "掌上hbu", 19f, Kit.ink(this));
        name.setPadding(0, Kit.dp(this, 10), 0, 0);
        head.addView(name);

        head.addView(Kit.text(this, "版本 " + version(), 12f, Kit.sub(this)));

        TextView honor = Kit.text(this, "河北大学吧荣誉出品", 12f, Kit.brand(this));
        honor.setPadding(0, Kit.dp(this, 6), 0, 0);
        head.addView(honor);
        content.addView(head);

        section("数据来源");
        LinearLayout src = card();
        src.addView(row("本科教务（URP）", "zhjw.hbu.cn"));
        src.addView(Kit.hr(this));
        src.addView(row("校园网 / WebVPN", "v.hbu.cn"));
        src.addView(Kit.hr(this));
        src.addView(row("办事大厅", "ehall.hbu.edu.cn"));
        src.addView(Kit.hr(this));
        src.addView(row("学生事务平台", "xsfwpt.hbu.cn"));
        content.addView(src);

        gap(16);
        section("参考项目");
        LinearLayout ref = card();
        ref.addView(refLink("掌上唐师（Chanmingfei/zstangshi）：原生零依赖架构、课表交互思路",
                "https://github.com/Chanmingfei/zstangshi"));
        ref.addView(Kit.hr(this));
        ref.addView(refLink("河大课表（sasaju/NormalSchedule，Apache-2.0）：河大教务的登录方式与功能清单",
                "https://github.com/sasaju/NormalSchedule"));
        ref.addView(Kit.hr(this));
        ref.addView(refLink("OpenWakeUp（LonelyMarch）：作息设置、多课表、调课与上课通知的产品思路",
                "https://github.com/LonelyMarch/OpenWakeUp"));
        content.addView(ref);
        gap(8);
        content.addView(Kit.text(this, "本项目代码为重新实现，未直接复制上述项目源码；"
                + "思路与界面取舍参考了它们，在此致谢。", 11f, Kit.faint(this)));

        gap(16);
        section("开发者");
        LinearLayout dev = card();
        dev.addView(row("开发者", DEVELOPER));
        dev.addView(Kit.hr(this));
        dev.addView(Kit.row(this, "项目地址", PROJECT_URL_TEXT, this::openProject));
        content.addView(dev);

        gap(16);
        section("许可");
        LinearLayout lic = card();
        lic.addView(Kit.row(this, "本应用代码", "AGPL-3.0", this::showLicense));
        lic.addView(Kit.hr(this));
        lic.addView(Kit.row(this, "第三方来源", "3 个参考项目", this::showLicense));
        content.addView(lic);
        gap(8);
        content.addView(Kit.text(this, "AGPL-3.0 允许修改与商用，但改过的代码必须同样开源，"
                + "因此不能做成闭源商业版。完整条款与第三方说明见仓库根目录的 "
                + "LICENSE 与 THIRD-PARTY.md。", 10.5f, Kit.faint(this)));


        gap(16);
        section("说明");
        LinearLayout info = card();
        info.addView(note("本应用为个人开发，非河北大学官方发布。"));
        info.addView(Kit.hr(this));
        info.addView(note("账号密码只在学校的网页里输入，App 不保存；课表、成绩等数据只存本机。"));
        info.addView(Kit.hr(this));
        info.addView(note("教务改版可能导致读取失败，届时会在功能里给出提示，不会伪造数据。"));
        content.addView(info);

        gap(18);
        TextView copy = Kit.text(this, "复制项目地址", 12.5f, Kit.brand(this));
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(0, Kit.dp(this, 8), 0, Kit.dp(this, 8));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("project", PROJECT_URL.isEmpty()
                        ? PROJECT_URL_TEXT : PROJECT_URL));
                Kit.toast(this, "已复制");
            }
        });
        content.addView(copy);

        TextView foot = Kit.text(this, "Made with care in HBU", 10.5f, Kit.faint(this));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 20));
        content.addView(foot);
    }

    private String version() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private LinearLayout row(String left, String right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
        r.addView(Kit.text(this, left, 13.5f, Kit.ink(this)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Kit.text(this, right, 11.5f, Kit.faint(this)));
        return r;
    }

    /** 参考项目那一栏：可点，直接跳到对应仓库。 */
    private TextView refLink(String text, String url) {
        TextView t = note(text);
        t.setTextColor(Kit.brand(this));
        t.setClickable(true);
        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Kit.toast(this, "没有可用的浏览器");
            }
        });
        return t;
    }

    private TextView note(String s) {
        TextView t = Kit.text(this, s, 12f, Kit.sub(this));
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 10), Kit.dp(this, 14), Kit.dp(this, 10));
        t.setLineSpacing(Kit.dp(this, 3), 1f);
        return t;
    }

    /** 许可说明：把「能做什么 / 不能做什么 / 三方来源」一次讲清。 */
    private void showLicense() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Kit.dp(this, 20), 0, Kit.dp(this, 20), Kit.dp(this, 18));
        box.addView(Kit.bold(this, "开源许可", 16f, Kit.ink(this)));
        box.addView(Kit.gap(this, 10));

        LinearLayout card = Kit.card(this, 2);
        card.addView(noteLine("本应用代码", "AGPL-3.0-only"));
        card.addView(Kit.hr(this));
        card.addView(noteLine("河大课表", "Apache-2.0（仅参考设计，未复制代码）"));
        card.addView(Kit.hr(this));
        card.addView(noteLine("OpenWakeUp", "AGPL-3.0（仅参考设计，未复制代码）"));
        card.addView(Kit.hr(this));
        card.addView(noteLine("掌上唐师", "无许可证（未使用其代码）"));
        box.addView(card);

        TextView tip = Kit.text(this, "你可以自由使用、修改、二次发布本应用，"
                + "但发布时必须同样以 AGPL-3.0 开源，不能做成闭源商业版。"
                + "分发安装包时需提供对应源码。", 12f, Kit.sub(this));
        tip.setPadding(0, Kit.dp(this, 12), 0, Kit.dp(this, 4));
        tip.setLineSpacing(Kit.dp(this, 3), 1f);
        box.addView(tip);

        TextView open = Kit.text(this, "在浏览器中查看完整条款", 12.5f, Kit.brand(this));
        open.setGravity(Gravity.CENTER);
        open.setPadding(0, Kit.dp(this, 12), 0, Kit.dp(this, 4));
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(PROJECT_URL + "/blob/main/LICENSE")));
            } catch (Exception e) {
                Kit.toast(this, "没有可用的浏览器");
            }
        });
        box.addView(open);

        com.hbu.zhangshanghbu.ui.Sheet.show(this, box, Kit.dp(this, 390));
    }

    private android.view.View noteLine(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Kit.dp(this, 14), Kit.dp(this, 10), Kit.dp(this, 14), Kit.dp(this, 10));
        TextView l = Kit.text(this, left, 13f, Kit.ink(this));
        l.setPadding(0, 0, Kit.dp(this, 10), 0);
        row.addView(l);
        TextView r = Kit.text(this, right, 11.5f, Kit.sub(this));
        r.setGravity(Gravity.END);
        r.setSingleLine(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(r, lp);
        return row;
    }

    private void openProject() {
        if (PROJECT_URL.isEmpty()) {
            Kit.toast(this, "项目地址还没公开，先占个位");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)));
        } catch (Exception e) {
            Kit.toast(this, "没有可用的浏览器");
        }
    }
}
