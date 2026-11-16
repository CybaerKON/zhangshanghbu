// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.R;

/** 上课提醒的接收端：弹一条通知，点开进主界面。 */
public class NoticeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        NoticeCenter.ensureChannel(c);
        if (!NoticeCenter.allowed(c)) return;

        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        int code = intent.getIntExtra("code", 1);

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, "class_reminder");
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title == null ? "要上课了" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(code, b.build());
    }
}
