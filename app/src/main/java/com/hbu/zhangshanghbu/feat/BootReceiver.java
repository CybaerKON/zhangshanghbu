// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机后重排上课提醒（闹钟在重启后会丢）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            NoticeCenter.reschedule(c.getApplicationContext());
        }
    }
}
