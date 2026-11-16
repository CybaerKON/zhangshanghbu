// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.ui.Dialog;
import com.hbu.zhangshanghbu.ui.Picker;

import java.util.List;

/**
 * 上课通知设置：开不开、提前多久、权限有没有、以及「接下来会提醒什么」。
 *
 * 权限这块不做静默失败：没开通知权限就明确写出来，并给一个能直接跳过去的按钮；
 * Android 13 起还要申请 POST_NOTIFICATIONS 运行时权限。
 */
public class NoticeActivity extends PageActivity {

    private Store store;

    @Override
    protected String title() {
        return "上课通知";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        content.removeAllViews();

        boolean permission = NoticeCenter.allowed(this);
        boolean on = store.noticeOn();

        section("开关");
        LinearLayout sw = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Kit.dp(this, 14), Kit.dp(this, 10), Kit.dp(this, 14), Kit.dp(this, 10));
        row.addView(Kit.text(this, "上课前提醒", 14.5f, Kit.ink(this)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(on);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            store.setNoticeOn(checked);
            if (checked) {
                NoticeCenter.reschedule(this);
                if (!NoticeCenter.allowed(this)) askPermission();
            } else {
                NoticeCenter.cancelAll(this);
            }
            build(content);
        });
        row.addView(toggle);
        sw.addView(row);
        sw.addView(Kit.hr(this));
        sw.addView(Kit.row(this, "提前多久", NoticeCenter.leadText(store.noticeLead()), this::pickLead));
        content.addView(sw);

        gap(16);
        section("权限");
        LinearLayout perm = card();
        String permText = permission ? "通知权限已开启" : "通知权限未开启 · 提醒不会出现";
        perm.addView(Kit.row(this, permText, permission ? "" : "去开启", permission ? null : this::askPermission));
        content.addView(perm);

        gap(16);
        section("接下来的提醒");
        List<String> list = NoticeCenter.preview(this, 4);
        LinearLayout pv = card();
        if (!on) {
            pv.addView(pad(Kit.text(this, "通知已关闭。打开后这里会列出接下来的上课提醒。", 12.5f, Kit.sub(this))));
        } else if (list.isEmpty()) {
            pv.addView(pad(Kit.text(this, "未来 7 天没有需要提醒的课（或者课表还是空的）。", 12.5f, Kit.sub(this))));
        } else {
            for (String s : list) {
                pv.addView(pad(Kit.text(this, s, 12.5f, Kit.ink(this))));
            }
        }
        content.addView(pv);

        gap(14);
        TextView re = Kit.text(this, "重新排程", 13.5f, Kit.brand(this));
        re.setGravity(Gravity.CENTER);
        re.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 10));
        re.setOnClickListener(v -> {
            NoticeCenter.reschedule(this);
            Kit.toast(this, "已按当前课表重排未来 7 天");
            build(content);
        });
        content.addView(re);

        gap(6);
        content.addView(Kit.text(this, "说明：只排未来 7 天，改课表或改作息后会自动重排；"
                + "Android 12 起精确闹钟需要系统授权，没授权时可能晚几分钟。", 11f, Kit.faint(this)));
    }

    private TextView pad(TextView t) {
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 10), Kit.dp(this, 14), Kit.dp(this, 10));
        return t;
    }

    private void pickLead() {
        final int[] vals = {0, 5, 10, 15, 20, 30};
        String[] labels = new String[vals.length];
        int cur = 3;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = NoticeCenter.leadText(vals[i]);
            if (vals[i] == store.noticeLead()) cur = i;
        }
        Picker.show(this, "提前多久提醒", labels, cur, i -> {
            store.setNoticeLead(vals[i]);
            NoticeCenter.reschedule(this);
            build(content);
        });
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            return;
        }
        Dialog.confirm(this, "通知权限", "系统里没有给掌上hbu通知权限，提醒不会显示。要现在去设置里打开吗？",
                "算了", "去设置", () -> {
                    Intent i = new Intent();
                    if (Build.VERSION.SDK_INT >= 26) {
                        i.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                    }
                    startActivity(i);
                });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] result) {
        super.onRequestPermissionsResult(code, perms, result);
        if (code == 1001) {
            boolean ok = result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) NoticeCenter.reschedule(this);
            build(content);
        }
    }
}
