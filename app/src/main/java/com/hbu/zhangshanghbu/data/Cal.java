// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日期与教学周计算。
 *
 * 全工程只用「yyyy-MM-dd 字符串 + 周一为一周起点」这一套口径，
 * 免得 UI、通知、课表各算一套，出现「课表第 8 周、通知说第 7 周」这种事。
 */
public final class Cal {

    private Cal() {}

    private static final String[] DAY_FULL = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String nowHm() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    public static boolean valid(String ymd) {
        if (ymd == null || ymd.length() != 10) return false;
        try {
            Calendar c = parse(ymd + " 12:00");
            return c != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 1=周一 … 7=周日 */
    public static int dayOfWeek(String ymd) {
        Calendar c = parse(ymd + " 12:00");
        if (c == null) return 1;
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    public static int todayDow() {
        return dayOfWeek(today());
    }

    public static String dayName(int day) {
        return DAY_FULL[Math.max(1, Math.min(7, day)) - 1];
    }

    public static String addDays(String ymd, int n) {
        Calendar c = parse(ymd + " 12:00");
        if (c == null) return ymd;
        c.add(Calendar.DAY_OF_MONTH, n);
        return fmt(c);
    }

    public static String monday(String ymd) {
        if (!valid(ymd)) return "";
        return addDays(ymd, 1 - dayOfWeek(ymd));
    }

    /** 第 week 周的第 day 天（day=1 周一）。 */
    public static String dateOf(String start, int week, int day) {
        String mon = monday(start);
        if (mon.isEmpty()) return "";
        return addDays(mon, (week - 1) * 7 + (day - 1));
    }

    public static String[] weekDates(String start, int week) {
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) out[i] = dateOf(start, week, i + 1);
        return out;
    }

    /** 「11.09 - 11.15」 */
    public static String weekRange(String start, int week) {
        String a = dateOf(start, week, 1), b = dateOf(start, week, 7);
        if (a.isEmpty()) return "";
        return md(a) + " - " + md(b);
    }

    public static String md(String ymd) {
        if (!valid(ymd)) return ymd == null ? "" : ymd;
        return ymd.substring(5, 7) + "." + ymd.substring(8, 10);
    }

    public static String ymdCn(String ymd) {
        if (!valid(ymd)) return "";
        return ymd.substring(0, 4) + " 年 " + Integer.parseInt(ymd.substring(5, 7)) + " 月 "
                + Integer.parseInt(ymd.substring(8, 10)) + " 日";
    }

    /** 当前第几周（开学前算 1，超过总周数按总周数封顶）。 */
    public static int currentWeek(String start, int weeks) {
        String mon = monday(start);
        if (mon.isEmpty()) return 1;
        int w = weekOf(mon, today());
        return Math.max(1, Math.min(Math.max(1, weeks), w));
    }

    public static int weekOf(String start, String ymd) {
        String a = monday(start), b = monday(ymd);
        if (a.isEmpty() || b.isEmpty()) return 1;
        long days = (epoch(b) - epoch(a)) / 86400000L;
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    /** 距开学还有几天；已开学返回 0。 */
    public static int daysToStart(String start) {
        String mon = monday(start);
        if (mon.isEmpty()) return 0;
        long d = (epoch(mon) - epoch(monday(today()))) / 86400000L;
        return (int) Math.max(0, d);
    }

    private static long epoch(String ymd) {
        Calendar c = parse(ymd + " 12:00");
        return c == null ? 0 : c.getTimeInMillis();
    }

    public static Calendar parse(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)) - 1,
                    Integer.parseInt(s.substring(8, 10)),
                    s.length() >= 16 ? Integer.parseInt(s.substring(11, 13)) : 12,
                    s.length() >= 16 ? Integer.parseInt(s.substring(14, 16)) : 0, 0);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    /** 学期 id（2026-2027-1）→ 估算的第 1 周周一：秋季取 9 月第一个周一，春季取 2/20 后第一个周一。 */
    public static String guessStart(String semesterId) {
        if (semesterId == null || !semesterId.matches("\\d{4}-\\d{4}-[12]")) return "";
        String[] p = semesterId.split("-");
        int y = Integer.parseInt(p[0]);
        int term = Integer.parseInt(p[2]);
        if (term == 1) {
            String sep = y + "-09-01";
            int dow = dayOfWeek(sep);
            return dow == 1 ? sep : addDays(sep, 8 - dow);
        } else {
            String feb = (y + 1) + "-02-20";
            int dow = dayOfWeek(feb);
            return dow == 1 ? feb : addDays(feb, 8 - dow);
        }
    }

    public static String fmt(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    // ---------- 周次表达式 ----------

    /**
     * 支持：1-16 / 1-8,10-16 / 1,3,5 / 2-16周(双) / 1-15(单) /
     * 1-4,6-8,10-16周。解析不出来返回空列表（调用方按「每周」处理）。
     */
    public static List<Integer> parseWeeks(String expr) {
        List<Integer> out = new ArrayList<>();
        if (expr == null || expr.trim().isEmpty()) return out;
        String s = expr.replace('～', '-').replace('~', '-').replace('—', '-')
                .replace(" ", "")
                .replace('（', '(').replace('）', ')')
                .replace('，', ',');
        boolean odd = s.contains("单"), even = s.contains("双");
        s = s.replaceAll("[^0-9,\\-]", "");

        for (String part : s.split(",")) {
            if (part.isEmpty()) continue;
            String[] r = part.split("-");
            try {
                if (r.length == 2 && !r[0].isEmpty() && !r[1].isEmpty()) {
                    int a = Integer.parseInt(r[0]), b = Integer.parseInt(r[1]);
                    if (b < a) { int t = a; a = b; b = t; }
                    if (b > 40) continue;
                    for (int i = a; i <= b; i++) {
                        if (odd && i % 2 == 0) continue;
                        if (even && i % 2 == 1) continue;
                        if (!out.contains(i)) out.add(i);
                    }
                } else if (r.length == 1 && !r[0].isEmpty()) {
                    int v = Integer.parseInt(r[0]);
                    if (v >= 1 && v <= 40 && !out.contains(v)) out.add(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /** [1,2,3,5,6] → 「1-3,5-6」 */
    public static String compress(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) return "";
        List<Integer> w = new ArrayList<>(weeks);
        w.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < w.size()) {
            int a = w.get(i), b = a;
            while (i + 1 < w.size() && w.get(i + 1) == b + 1) {
                b = w.get(i + 1);
                i++;
            }
            if (sb.length() > 0) sb.append(',');
            sb.append(a);
            if (b != a) sb.append('-').append(b);
            i++;
        }
        return sb.toString();
    }
}
