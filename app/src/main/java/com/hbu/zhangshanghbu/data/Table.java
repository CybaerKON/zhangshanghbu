// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一份课表的数据模型（一份 = 一个学期，或者用户自己建的临时表）。
 *
 * 结构：Table 1 ── N Course 1 ── N Session；另挂一串 Adjust（临时调课 / 停课）。
 *
 * 为什么 Course 和 Session 分开：一门课一周上三次（周一 1-2、周三 3-4、周五 5-6）
 * 是常态，教室、周次、老师都可能每个时段不同。所以「聚合键」放在 Course，
 * 「具体哪天第几节、哪个教室、哪几周」放在 Session。
 */
public final class Table {

    public static final int SCHEMA = 2;

    public static final String SRC_CAMPUS = "campus";   // 从教务抓的
    public static final String SRC_CUSTOM = "custom";   // 手动加的 / 改过的
    public static final String SRC_DEMO = "demo";       // 示例

    // ---------- 学期 ----------

    public static final class Semester {
        public String id = "";        // 2026-2027-1
        public String name = "";      // 2026-2027 学年第一学期
        public String start = "";     // 第 1 周周一 yyyy-MM-dd
        public int weeks = 20;

        public JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("start", start);
            o.put("weeks", weeks);
            return o;
        }

        public static Semester from(JSONObject o) {
            Semester s = new Semester();
            if (o == null) return s;
            s.id = o.optString("id", "");
            s.name = o.optString("name", "");
            s.start = o.optString("start", "");
            s.weeks = o.optInt("weeks", 20);
            return s;
        }
    }

    // ---------- 时段 ----------

    public static final class Session {
        public int day = 1;              // 1=周一 … 7=周日
        public int start = 1;            // 起始节
        public int end = 1;              // 结束节（含）
        public List<Integer> weeks = new ArrayList<>();
        public String raw = "";          // 原始周次描述，例如「1-16周(单)」
        public String room = "";
        public String campus = "";
        public String teacher = "";      // 该时段的老师（可为空）
        public String timeStart = "";    // 特殊时间 HH:mm（考试/临时课）
        public String timeEnd = "";

        public boolean active(int week) {
            return weeks.isEmpty() || weeks.contains(week);
        }

        public boolean hasClock() {
            int a = mins(timeStart), b = mins(timeEnd);
            return a >= 0 && b > a;
        }

        public Session copy() {
            Session s = new Session();
            s.day = day;
            s.start = start;
            s.end = end;
            s.weeks.addAll(weeks);
            s.raw = raw;
            s.room = room;
            s.campus = campus;
            s.teacher = teacher;
            s.timeStart = timeStart;
            s.timeEnd = timeEnd;
            return s;
        }

        public JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("day", day);
            o.put("start", start);
            o.put("end", end);
            JSONArray w = new JSONArray();
            for (int i : weeks) w.put(i);
            o.put("weeks", w);
            if (!raw.isEmpty()) o.put("raw", raw);
            if (!room.isEmpty()) o.put("room", room);
            if (!campus.isEmpty()) o.put("campus", campus);
            if (!teacher.isEmpty()) o.put("teacher", teacher);
            if (!timeStart.isEmpty()) o.put("t1", timeStart);
            if (!timeEnd.isEmpty()) o.put("t2", timeEnd);
            return o;
        }

        public static Session from(JSONObject o) {
            Session s = new Session();
            if (o == null) return s;
            s.day = clamp(o.optInt("day", 1), 1, 7);
            s.start = Math.max(1, o.optInt("start", 1));
            s.end = Math.max(s.start, o.optInt("end", s.start));
            JSONArray w = o.optJSONArray("weeks");
            if (w != null) for (int i = 0; i < w.length(); i++) s.weeks.add(w.optInt(i));
            s.raw = o.optString("raw", "");
            s.room = o.optString("room", "");
            s.campus = o.optString("campus", "");
            s.teacher = o.optString("teacher", "");
            s.timeStart = o.optString("t1", "");
            s.timeEnd = o.optString("t2", "");
            if (s.weeks.isEmpty() && !s.raw.isEmpty()) s.weeks.addAll(Cal.parseWeeks(s.raw));
            Collections.sort(s.weeks);
            return s;
        }

        public static int mins(String hm) {
            if (hm == null) return -1;
            String s = hm.trim();
            int c = s.indexOf(':');
            if (c <= 0) return -1;
            try {
                int h = Integer.parseInt(s.substring(0, c));
                int m = Integer.parseInt(s.substring(c + 1));
                if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
                return h * 60 + m;
            } catch (Exception e) {
                return -1;
            }
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    // ---------- 课程 ----------

    public static final class Course {
        public String key = "";
        public String name = "";
        public String teacher = "";
        public double credit;
        public String attr = "";       // 必修 / 选修 / 通识…
        public int color;
        public String source = SRC_CAMPUS;
        public String note = "";
        public List<Session> sessions = new ArrayList<>();

        public Session sessionOn(int day, int section) {
            for (Session s : sessions) {
                if (s.day == day && section >= s.start && section <= s.end) return s;
            }
            return null;
        }

        public JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("name", name);
            o.put("teacher", teacher);
            o.put("credit", credit);
            o.put("attr", attr);
            o.put("color", color);
            o.put("source", source);
            if (!note.isEmpty()) o.put("note", note);
            JSONArray a = new JSONArray();
            for (Session s : sessions) a.put(s.json());
            o.put("sessions", a);
            return o;
        }

        public static Course from(JSONObject o) {
            Course c = new Course();
            c.key = o.optString("key", "");
            c.name = o.optString("name", "");
            c.teacher = o.optString("teacher", "");
            c.credit = o.optDouble("credit", 0);
            c.attr = o.optString("attr", "");
            c.color = o.optInt("color", 0);
            c.source = o.optString("source", SRC_CAMPUS);
            c.note = o.optString("note", "");
            JSONArray a = o.optJSONArray("sessions");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject so = a.optJSONObject(i);
                    if (so != null) c.sessions.add(Session.from(so));
                }
            }
            return c;
        }
    }

    // ---------- 临时调课 / 停课 ----------

    public static final class Adjust {
        public static final String MOVE = "move";
        public static final String CANCEL = "cancel";

        public String id = "";
        public String type = MOVE;
        public String course = "";     // 课程名
        public int week;               // 原周次
        public int day, start, end;    // 原位置
        public int toWeek, toDay, toStart;  // 调到哪里（cancel 时无效）
        public String note = "";

        public JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            o.put("course", course);
            o.put("week", week);
            o.put("day", day);
            o.put("start", start);
            o.put("end", end);
            if (MOVE.equals(type)) {
                o.put("toWeek", toWeek);
                o.put("toDay", toDay);
                o.put("toStart", toStart);
            }
            if (!note.isEmpty()) o.put("note", note);
            return o;
        }

        public static Adjust from(JSONObject o) {
            Adjust a = new Adjust();
            a.id = o.optString("id", "");
            a.type = o.optString("type", MOVE);
            a.course = o.optString("course", "");
            a.week = o.optInt("week");
            a.day = o.optInt("day");
            a.start = o.optInt("start");
            a.end = o.optInt("end", a.start);
            a.toWeek = o.optInt("toWeek");
            a.toDay = o.optInt("toDay");
            a.toStart = o.optInt("toStart");
            a.note = o.optString("note", "");
            return a;
        }

        public boolean hits(int w, int d, int s) {
            return week == w && day == d && start == s;
        }
    }

    // ---------- 表 ----------

    public String id = "";
    public String name = "我的课表";
    public Semester semester = new Semester();
    public List<Course> courses = new ArrayList<>();
    public List<Adjust> adjusts = new ArrayList<>();

    public JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", SCHEMA);
        o.put("name", name);
        o.put("semester", semester.json());
        JSONArray a = new JSONArray();
        for (Course c : courses) a.put(c.json());
        o.put("courses", a);
        JSONArray b = new JSONArray();
        for (Adjust x : adjusts) b.put(x.json());
        o.put("adjusts", b);
        return o;
    }

    public static Table from(String id, JSONObject o) {
        Table t = new Table();
        t.id = id;
        if (o == null) return t;
        t.name = o.optString("name", "我的课表");
        t.semester = Semester.from(o.optJSONObject("semester"));
        JSONArray a = o.optJSONArray("courses");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject co = a.optJSONObject(i);
                if (co != null) t.courses.add(Course.from(co));
            }
        }
        JSONArray b = o.optJSONArray("adjusts");
        if (b != null) {
            for (int i = 0; i < b.length(); i++) {
                JSONObject ao = b.optJSONObject(i);
                if (ao != null) t.adjusts.add(Adjust.from(ao));
            }
        }
        return t;
    }

    public boolean empty() {
        return courses.isEmpty();
    }

    public int maxWeek() {
        int max = 1;
        for (Course c : courses) {
            for (Session s : c.sessions) {
                if (!s.weeks.isEmpty()) max = Math.max(max, s.weeks.get(s.weeks.size() - 1));
            }
        }
        for (Adjust a : adjusts) {
            max = Math.max(max, a.week);
            if (Adjust.MOVE.equals(a.type)) max = Math.max(max, a.toWeek);
        }
        return max;
    }

    public Course find(String key) {
        for (Course c : courses) if (c.key.equals(key)) return c;
        return null;
    }

    /** 某周某天要上的课（已应用停课/调课）。 */
    public List<Item> itemsOn(int week, int day) {
        List<Item> out = new ArrayList<>();
        for (Course c : courses) {
            for (Session s : c.sessions) {
                if (s.day != day || !s.active(week)) continue;
                if (cancelled(week, day, s.start)) continue;
                Item it = new Item();
                it.course = c;
                it.session = s;
                it.day = day;
                it.start = s.start;
                it.end = s.end;
                it.label = "";
                out.add(it);
            }
        }
        // 调进来的课
        for (Adjust a : adjusts) {
            if (!Adjust.MOVE.equals(a.type) || a.toWeek != week || a.toDay != day) continue;
            for (Course c : courses) {
                if (!c.name.equals(a.course)) continue;
                for (Session s : c.sessions) {
                    if (s.start != a.start || s.day != a.day) continue;
                    Item it = new Item();
                    it.course = c;
                    it.session = s;
                    it.day = day;
                    it.start = a.toStart;
                    it.end = a.toStart + (s.end - s.start);
                    it.label = "调课";
                    it.adjusted = true;
                    out.add(it);
                }
            }
        }
        Collections.sort(out, (x, y) -> Integer.compare(x.start, y.start));
        return out;
    }

    public boolean cancelled(int week, int day, int start) {
        for (Adjust a : adjusts) {
            if (Adjust.CANCEL.equals(a.type) && a.hits(week, day, start)) return true;
            if (Adjust.MOVE.equals(a.type) && a.hits(week, day, start)) return true;
        }
        return false;
    }

    /** 调走标记（格子显示「调走」）。 */
    public Adjust moved(int week, int day, int start) {
        for (Adjust a : adjusts) {
            if (Adjust.MOVE.equals(a.type) && a.hits(week, day, start)) return a;
        }
        return null;
    }

    public static final class Item {
        public Course course;
        public Session session;
        public int day, start, end;
        public String label = "";
        public boolean adjusted = false;
    }

    /** 教务页面上的课程名常带「_02939」这种课序号，展示时去掉。 */
    public static String cleanName(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\u00a0', ' ').trim();
        s = s.replaceAll("[_＿]\\s*\\d{3,6}\\s*$", "");
        s = s.replaceAll("\\s*\\(\\s*\\d{4}-\\d{4}-\\d\\s*\\)\\s*[-－]?\\s*[A-Za-z0-9]*\\s*$", "");
        return s.trim();
    }
}
