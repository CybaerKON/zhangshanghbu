// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.R;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.data.Table;

import java.util.List;

/**
 * 今日课程小组件（系统原生 RemoteViews，不是自绘）。
 *
 * 一份 provider 覆盖所有尺寸：行数由「卡片当前实际高度」算出来，
 * 用户长按拖边框改大小后立刻按新高度重排，不用装好几个不同尺寸的卡片。
 * 高度只够一行时退化成摘要「今天 3 门 · 下一节 08:00 高等数学」。
 */
public class TodayWidget extends AppWidgetProvider {

    /** 每行大约占的高度（dp），含上下留白 */
    private static final int ROW_DP = 26;
    /** 标题栏高度（dp） */
    private static final int HEAD_DP = 30;

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            Bundle opt = mgr.getAppWidgetOptions(id);
            int hDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            mgr.updateAppWidget(id, build(c, hDp));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle newOptions) {
        int hDp = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        mgr.updateAppWidget(id, build(c, hDp));
    }

    /** 按可用高度生成视图。 */
    private RemoteViews build(Context c, int hDp) {
        Store store = new Store(c);
        Table t = store.activeTable();
        Schedule sch = store.schedule();

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_today);
        String[] cn = {"一", "二", "三", "四", "五", "六", "日"};
        v.setTextViewText(R.id.w_title, "掌上hbu");
        v.setTextViewText(R.id.w_day, "周" + cn[Cal.todayDow() - 1] + " · " + Cal.md(Cal.today()));

        v.removeAllViews(R.id.w_rows);

        int rows = Math.max(1, (hDp - HEAD_DP) / ROW_DP);

        if (t.empty()) {
            v.setViewVisibility(R.id.w_empty, View.VISIBLE);
            v.setTextViewText(R.id.w_empty, c.getString(R.string.widget_empty));
            return withClick(c, v);
        }

        String start = t.semester.start.isEmpty()
                ? Cal.guessStart(t.semester.id) : t.semester.start;
        int week = start.isEmpty() ? 1 : Cal.currentWeek(start, Math.max(18, t.maxWeek()));
        List<Table.Item> items = t.itemsOn(week, Cal.todayDow());

        if (items.isEmpty()) {
            v.setViewVisibility(R.id.w_empty, View.VISIBLE);
            v.setTextViewText(R.id.w_empty, c.getString(R.string.widget_none));
            return withClick(c, v);
        }

        /* 太矮就只留一行摘要：硬塞两行会把文字压扁，反而不如不显示 */
        if (rows <= 1) {
            Table.Item it = items.get(0);
            v.setViewVisibility(R.id.w_empty, View.VISIBLE);
            v.setTextViewText(R.id.w_empty, "今天 " + items.size() + " 门 · 下一节 "
                    + sch.startTime(it.start) + " " + Table.cleanName(it.course.name));
            return withClick(c, v);
        }

        v.setViewVisibility(R.id.w_empty, View.GONE);
        int show = Math.min(rows, items.size());
        for (int i = 0; i < show; i++) {
            Table.Item it = items.get(i);
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_row);
            row.setTextViewText(R.id.wr_time, sch.startTime(it.start));
            row.setTextViewText(R.id.wr_name, Table.cleanName(it.course.name));
            row.setTextViewText(R.id.wr_room, it.session.room);
            v.addView(R.id.w_rows, row);
        }
        if (items.size() > show) {
            RemoteViews more = new RemoteViews(c.getPackageName(), R.layout.widget_row);
            more.setTextViewText(R.id.wr_time, "");
            more.setTextViewText(R.id.wr_name, "还有 " + (items.size() - show) + " 门…");
            more.setTextViewText(R.id.wr_room, "");
            v.addView(R.id.w_rows, more);
        }
        return withClick(c, v);
    }

    private RemoteViews withClick(Context c, RemoteViews v) {
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_title, pi);
        v.setOnClickPendingIntent(R.id.w_empty, pi);
        v.setOnClickPendingIntent(R.id.w_rows, pi);
        return v;
    }
}
