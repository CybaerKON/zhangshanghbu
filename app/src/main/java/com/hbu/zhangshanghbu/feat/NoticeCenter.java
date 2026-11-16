// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.hbu.zhangshanghbu.R;
import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 上课通知：按当前课表 + 当前作息，排未来 7 天的闹钟。
 *
 * 只排 7 天，是因为课表会变（调课、停课、换表），排太远反而容易和实际对不上；
 * App 每次启动、每次改课表/改作息都会重排一次（先撤旧的再排新的）。
 * Android 12 起精确闹钟需要 SCHEDULE_EXACT_ALARM，没有授权就退化成普通闹钟，
 * 可能晚几分钟，但不至于不响。
 */
public final class NoticeCenter {

    private NoticeCenter() {}

    private static final String CHANNEL = "class_reminder";
    private static final String KEY_CODES = "notice_codes";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "上课提醒",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("上课前提醒下一节要上什么、在哪上");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    public static boolean allowed(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    /** 重排未来 7 天的提醒。任何课表/作息变更后调用。 */
    public static void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancelAll(c);

        Store store = new Store(c);
        if (!store.noticeOn() || !allowed(c)) return;

        Table t = store.activeTable();
        if (t.empty()) return;
        Schedule sch = store.schedule();
        int lead = store.noticeLead();

        String start = t.semester.start;
        if (start.isEmpty()) start = Cal.guessStart(t.semester.id);
        if (start.isEmpty()) return;

        JSONArray codes = new JSONArray();
        long now = System.currentTimeMillis();
        int scheduled = 0;

        for (int i = 0; i < 7 && scheduled < 40; i++) {
            String date = Cal.addDays(Cal.today(), i);
            int dow = Cal.dayOfWeek(date);
            int week = Cal.weekOf(start, date);
            for (Table.Item it : t.itemsOn(week, dow)) {
                Calendar when = Cal.parse(date);
                if (when == null) continue;
                int minutes = sch.startMinutes(it.start) - lead;
                when.set(Calendar.HOUR_OF_DAY, Math.max(0, minutes / 60));
                when.set(Calendar.MINUTE, Math.max(0, minutes % 60));
                when.set(Calendar.SECOND, 0);
                long at = when.getTimeInMillis();
                if (at <= now + 30_000) continue;

                String title = Table.cleanName(it.course.name) + " · 第 " + it.start + " 节";
                String body = sch.startTime(it.start) + " 开始"
                        + (it.session.room.isEmpty() ? "" : " · " + it.session.room)
                        + (it.session.teacher.isEmpty() ? "" : " · " + it.session.teacher);
                int code = (int) (at / 60000 % 100000);
                Intent intent = new Intent(c, NoticeReceiver.class);
                intent.putExtra("title", title);
                intent.putExtra("body", body);
                intent.putExtra("code", code);
                PendingIntent pi = PendingIntent.getBroadcast(c, code, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= 31) {
                    boolean exact = am.canScheduleExactAlarms();
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                }
                codes.put(code);
                scheduled++;
            }
        }
        new Store(c).cache(KEY_CODES, codes.toString());
    }

    public static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            JSONArray codes = new JSONArray(new Store(c).cache(KEY_CODES));
            for (int i = 0; i < codes.length(); i++) {
                Intent intent = new Intent(c, NoticeReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(c, codes.optInt(i), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception ignored) {
        }
        new Store(c).cache(KEY_CODES, "[]");
    }

    /** 给设置页看的预告：接下来会提醒什么。 */
    public static List<String> preview(Context c, int max) {
        List<String> out = new ArrayList<>();
        Store store = new Store(c);
        Table t = store.activeTable();
        if (t.empty()) return out;
        Schedule sch = store.schedule();
        int lead = store.noticeLead();
        String start = t.semester.start.isEmpty() ? Cal.guessStart(t.semester.id) : t.semester.start;
        if (start.isEmpty()) return out;

        for (int i = 0; i < 7 && out.size() < max; i++) {
            String date = Cal.addDays(Cal.today(), i);
            int week = Cal.weekOf(start, date);
            for (Table.Item it : t.itemsOn(week, Cal.dayOfWeek(date))) {
                Calendar when = Cal.parse(date);
                if (when == null) continue;
                int minutes = sch.startMinutes(it.start) - lead;
                when.set(Calendar.HOUR_OF_DAY, Math.max(0, minutes / 60));
                when.set(Calendar.MINUTE, Math.max(0, minutes % 60));
                if (when.getTimeInMillis() <= System.currentTimeMillis()) continue;
                out.add(Cal.md(date) + " " + Schedule.hm(minutes) + " · "
                        + Table.cleanName(it.course.name) + "（" + sch.startTime(it.start) + "）");
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    public static String leadText(int min) {
        if (min <= 0) return "上课时提醒";
        return "提前 " + min + " 分钟";
    }
}
