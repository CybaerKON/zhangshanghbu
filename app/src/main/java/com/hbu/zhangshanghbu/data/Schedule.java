// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 作息：第 1 节几点开始、每节多长、普通课间多长、大课间在第几节之后、多长。
 *
 * 河大原来的作息是「上午 4 节 + 下午 4 节 + 晚上 4 节」，但各校区/学期会有微调，
 * 所以这里做成可配置：
 *
 *   第 n 节的开始 = 第 1 节开始 + (n-1)×节长 + 第 1..n-1 节之后的休息之和
 *
 * 大课间就是「某一节之后的休息换成更长的」，可以有多个（上午一个、下午一个）。
 * 课程块按节次画，跨越大课间的课（比如 3-4 节连着上、中间正好是大课间）不会被切断，
 * 只是它的结束时间会把大课间算进去 —— 也就是「课可以越过大课间」。
 */
public final class Schedule {

    public String firstStart = "08:00";
    public int period = 45;          // 每节课分钟数
    public int rest = 10;            // 普通课间分钟数
    public int sectionCount = 12;    // 每天节数

    public static final class Big {
        public int after = 2;        // 第几节之后
        public int minutes = 20;     // 休息多少分钟

        public Big() {}

        public Big(int after, int minutes) {
            this.after = after;
            this.minutes = minutes;
        }
    }

    public final List<Big> bigs = new ArrayList<>();

    public static Schedule defaults() {
        Schedule s = new Schedule();
        s.firstStart = "08:00";
        s.period = 45;
        s.rest = 10;
        s.sectionCount = 12;
        s.bigs.add(new Big(2, 20));   // 上午两节后的大课间
        s.bigs.add(new Big(6, 20));   // 下午两节后的大课间
        return s;
    }

    // ---------- 计算 ----------

    public int startMinutes(int section) {
        int n = Math.max(1, section);
        int m = mins(firstStart, 8 * 60);
        for (int i = 1; i < n; i++) {
            m += Math.max(1, period);
            m += restAfter(i);
        }
        return m;
    }

    public int endMinutes(int section) {
        return startMinutes(section) + Math.max(1, period);
    }

    /** 第 section 节之后休息多久：有大课间就是大课间，否则普通课间。 */
    public int restAfter(int section) {
        for (Big b : bigs) {
            if (b.after == section && b.minutes > 0) return b.minutes;
        }
        return Math.max(0, rest);
    }

    public boolean hasBigAfter(int section) {
        for (Big b : bigs) if (b.after == section) return true;
        return false;
    }

    public String startTime(int section) {
        return hm(startMinutes(section));
    }

    public String endTime(int section) {
        return hm(endMinutes(section));
    }

    /** 上午/下午/晚上：给课表左侧的时间轴分组用。 */
    public String bandOf(int section) {
        int t = startMinutes(section);
        if (t < 12 * 60) return "上午";
        if (t < 18 * 60) return "下午";
        return "晚上";
    }

    public static String hm(int minutes) {
        int m = Math.max(0, minutes);
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    private static int mins(String hm, int fallback) {
        int v = Table.Session.mins(hm);
        return v < 0 ? fallback : v;
    }

    /** 一句话描述，给设置页当副标题。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(firstStart).append(" 开始 · 每节 ").append(period).append(" 分钟 · 课间 ")
                .append(rest).append(" 分钟");
        if (!bigs.isEmpty()) {
            sb.append(" · 大课间 ");
            for (int i = 0; i < bigs.size(); i++) {
                if (i > 0) sb.append('/');
                sb.append("第").append(bigs.get(i).after).append("节后")
                        .append(bigs.get(i).minutes).append("分");
            }
        }
        return sb.toString();
    }

    // ---------- 存取 ----------

    public JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        o.put("first", firstStart);
        o.put("period", period);
        o.put("rest", rest);
        o.put("count", sectionCount);
        JSONArray a = new JSONArray();
        for (Big b : bigs) {
            JSONObject x = new JSONObject();
            x.put("after", b.after);
            x.put("minutes", b.minutes);
            a.put(x);
        }
        o.put("bigs", a);
        return o;
    }

    public static Schedule from(JSONObject o) {
        Schedule s = defaults();
        if (o == null) return s;
        s.firstStart = o.optString("first", s.firstStart);
        s.period = clamp(o.optInt("period", s.period), 20, 120);
        s.rest = clamp(o.optInt("rest", s.rest), 0, 60);
        s.sectionCount = clamp(o.optInt("count", s.sectionCount), 6, 16);
        s.bigs.clear();
        JSONArray a = o.optJSONArray("bigs");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) continue;
                s.bigs.add(new Big(clamp(x.optInt("after", 2), 1, 15),
                        clamp(x.optInt("minutes", 20), 0, 90)));
            }
        }
        return s;
    }

    public Schedule copy() {
        Schedule s = new Schedule();
        s.firstStart = firstStart;
        s.period = period;
        s.rest = rest;
        s.sectionCount = sectionCount;
        for (Big b : bigs) s.bigs.add(new Big(b.after, b.minutes));
        return s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
