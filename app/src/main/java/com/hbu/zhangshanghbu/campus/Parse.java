// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Palette;
import com.hbu.zhangshanghbu.data.Table;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 agent.js 交回来的原始数据变成模型。
 *
 * 成绩这一块刻意「按表头找列」而不是按列号取值：URP 各校的表头顺序不一样
 * （有的把课程号放第一列、有的放课程属性），硬编码列号就会出现「拿课程号当课名」。
 */
public final class Parse {

    private Parse() {}

    // ---------- 课表 ----------

    public static Table courses(String json, String tableId, String tableName) {
        Table t = new Table();
        t.id = tableId;
        t.name = tableName;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray recs = o.optJSONArray("records");
            if (recs == null) return t;

            String term = o.optString("term", "");
            t.semester = semesterOf(term);

            Map<String, Table.Course> map = new LinkedHashMap<>();
            for (int i = 0; i < recs.length(); i++) {
                JSONObject r = recs.optJSONObject(i);
                if (r == null) continue;
                String name = Table.cleanName(r.optString("name", ""));
                if (name.isEmpty()) continue;
                String teacher = r.optString("teacher", "");
                String key = "c_" + name + "_" + teacher;

                Table.Course c = map.get(key);
                if (c == null) {
                    c = new Table.Course();
                    c.key = key;
                    c.name = name;
                    c.teacher = teacher;
                    c.source = Table.SRC_CAMPUS;
                    c.color = map.size() % Palette.SIZE;
                    map.put(key, c);
                }
                Table.Session s = new Table.Session();
                s.day = Math.max(1, Math.min(7, r.optInt("day", 1)));
                s.start = Math.max(1, r.optInt("start", 1));
                s.end = Math.max(s.start, r.optInt("end", s.start));
                s.room = r.optString("room", "");
                s.raw = r.optString("raw", "");
                JSONArray w = r.optJSONArray("weeks");
                if (w != null) {
                    for (int k = 0; k < w.length(); k++) s.weeks.add(w.optInt(k));
                }
                if (s.weeks.isEmpty()) s.weeks.addAll(Cal.parseWeeks(s.raw));
                if (!dup(c, s)) c.sessions.add(s);
            }
            t.courses.addAll(map.values());
            int max = t.maxWeek();
            if (max >= 10) t.semester.weeks = Math.min(30, Math.max(t.semester.weeks, max));
        } catch (Exception ignored) {
        }
        return t;
    }

    private static boolean dup(Table.Course c, Table.Session s) {
        for (Table.Session o : c.sessions) {
            if (o.day == s.day && o.start == s.start && o.end == s.end && o.weeks.equals(s.weeks)) {
                return true;
            }
        }
        return false;
    }

    public static Table.Semester semesterOf(String term) {
        Table.Semester s = new Table.Semester();
        if (term != null && term.matches("\\d{4}-\\d{4}-[12]")) {
            String[] p = term.split("-");
            s.id = term;
            s.name = p[0] + "-" + p[1] + " 学年" + (p[2].equals("1") ? "第一学期" : "第二学期");
            return s;
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        int y = c.get(java.util.Calendar.YEAR);
        int m = c.get(java.util.Calendar.MONTH) + 1;
        int first = m >= 9 ? y : (m <= 1 ? y - 1 : y);
        int term2 = (m >= 9 || m == 1) ? 1 : 2;
        s.id = first + "-" + (first + 1) + "-" + term2;
        s.name = first + "-" + (first + 1) + " 学年" + (term2 == 1 ? "第一学期" : "第二学期");
        return s;
    }

    // ---------- 成绩 ----------

    public static final int ALL_PASS = 0, BY_ATTR = 1, PLAN = 2, FAIL = 3, THIS_TERM = 4;
    public static final String[] GRADE_TABS = {"全部及格", "课程属性", "方案成绩", "不及格", "本学期"};

    public static final class Grade {
        public String name = "";
        public String code = "";
        public String attr = "";
        public String term = "";
        public String raw = "";
        public double credit;
        public double score;
        public double gpa;
        public boolean passed = true;

        public String scoreText() {
            return raw.isEmpty() ? (score > 0 ? fmt(score) : "—") : raw;
        }
    }

    public static List<Grade> grades(String json) {
        List<Grade> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray headers = o.optJSONArray("headers");
            JSONArray rows = o.optJSONArray("rows");
            String pageTerm = o.optString("semesterText", "");
            if (rows == null) return out;

            int cName = nameCol(headers);
            int cCode = col(headers, "课程号", "课程代码", "课程编号");
            int cAttr = col(headers, "课程属性", "课程性质", "课程类别", "属性", "类别");
            int cCredit = col(headers, "学分");
            int cScore = col(headers, "成绩", "总评", "分数", "考核成绩");
            int cGpa = col(headers, "绩点");
            int cTerm = col(headers, "学期", "学年");

            for (int r = 0; r < rows.length(); r++) {
                JSONArray row = rows.optJSONArray(r);
                if (row == null || row.length() < 2) continue;

                Grade g = new Grade();
                g.code = cell(row, cCode);
                g.name = Table.cleanName(cell(row, cName));
                if (g.name.isEmpty()) {
                    // 表头不可靠时：取最长的一段中文当课名，绝不拿纯数字（课程号）顶替
                    for (int c = 0; c < row.length(); c++) {
                        String s = row.optString(c).trim();
                        if (s.length() > g.name.length() && !s.matches("[\\d.\\-]+")) g.name = Table.cleanName(s);
                    }
                }
                if (g.name.isEmpty() || g.name.matches("[\\d.\\-]+")) continue;

                g.attr = cell(row, cAttr);
                g.term = cell(row, cTerm, pageTerm);
                g.credit = num(cell(row, cCredit));
                g.raw = cell(row, cScore);
                g.score = num(g.raw);
                if (g.score <= 0) g.score = scoreOf(g.raw);
                g.gpa = num(cell(row, cGpa));
                g.passed = isPass(g.raw, g.score);

                /* 有些页面把两门课塞进同一行（换行或分号分隔），拆开 */
                if (g.name.contains("\n") || g.name.contains("；") || g.name.contains(";")) {
                    String[] parts = g.name.split("[\n；;]");
                    for (String part : parts) {
                        String nm = Table.cleanName(part);
                        if (nm.isEmpty()) continue;
                        Grade one = new Grade();
                        one.code = g.code;
                        one.attr = g.attr;
                        one.term = g.term;
                        one.credit = g.credit;
                        one.raw = g.raw;
                        one.score = g.score;
                        one.gpa = g.gpa;
                        one.passed = g.passed;
                        one.name = nm;
                        out.add(one);
                    }
                } else {
                    out.add(g);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<Grade> filter(List<Grade> all, int type) {
        List<Grade> out = new ArrayList<>();
        switch (type) {
            case FAIL:
                for (Grade g : all) if (!g.passed) out.add(g);
                break;
            case THIS_TERM: {
                String newest = newestTerm(all);
                for (Grade g : all) {
                    if (g.term.isEmpty() || newest.isEmpty() || g.term.contains(newest)
                            || newest.contains(g.term)) out.add(g);
                }
                break;
            }
            case BY_ATTR:
            case PLAN:
            case ALL_PASS:
            default:
                for (Grade g : all) if (g.passed) out.add(g);
                break;
        }
        return out;
    }

    /** 从成绩里找最近的学期文本（如「2026-2027学年秋」）。 */
    public static String newestTerm(List<Grade> all) {
        String best = "";
        for (Grade g : all) {
            String t = g.term == null ? "" : g.term;
            if (t.length() > best.length()) best = t;
        }
        return best;
    }

    public static double avgScore(List<Grade> list) {
        double sum = 0, credit = 0;
        for (Grade g : list) {
            if (g.score <= 0) continue;
            sum += g.score * Math.max(0.5, g.credit);
            credit += Math.max(0.5, g.credit);
        }
        return credit > 0 ? sum / credit : 0;
    }

    public static double avgGpa(List<Grade> list) {
        double sum = 0;
        int n = 0;
        for (Grade g : list) {
            if (g.gpa > 0) {
                sum += g.gpa;
                n++;
            }
        }
        return n > 0 ? sum / n : 0;
    }

    // ---------- 考试 ----------

    public static final class Exam {
        public String name = "";
        public String time = "";
        public String room = "";
        public String seat = "";
        public String note = "";
    }

    public static List<Exam> exams(String json) {
        List<Exam> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray headers = o.optJSONArray("headers");
            JSONArray rows = o.optJSONArray("rows");
            if (rows == null) return out;
            int cName = col(headers, "课程", "科目", "考试名称");
            int cTime = col(headers, "时间", "考试时间", "日期");
            int cRoom = col(headers, "地点", "考场", "教室");
            int cSeat = col(headers, "座位", "座号");
            for (int r = 0; r < rows.length(); r++) {
                JSONArray row = rows.optJSONArray(r);
                if (row == null || row.length() < 2) continue;
                Exam e = new Exam();
                e.name = Table.cleanName(cell(row, cName));
                if (e.name.isEmpty()) {
                    for (int c = 0; c < row.length(); c++) {
                        String s = row.optString(c).trim();
                        if (s.length() > e.name.length() && !s.matches("[\\d.\\-:：]+")) e.name = s;
                    }
                }
                if (e.name.isEmpty()) continue;
                e.time = cell(row, cTime);
                e.room = cell(row, cRoom);
                e.seat = cell(row, cSeat);
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------- 工具 ----------

    /**
     * 找「课程名称」列。
     *
     * URP 的表头常见是「课程号 | 课程名称 | 课程属性 | 学分 | 成绩 | 绩点」，
     * 如果按「包含课程」去找，第一个命中的会是「课程号」——这就是之前成绩列表
     * 全是编号的原因。这里按优先级找，并明确排除号/代码/属性一类的列。
     */
    private static int nameCol(JSONArray headers) {
        if (headers == null) return -1;
        String[] strong = {"课程名称", "课程名", "科目名称", "科目名"};
        for (String key : strong) {
            for (int i = 0; i < headers.length(); i++) {
                if (headers.optString(i).contains(key)) return i;
            }
        }
        for (int i = 0; i < headers.length(); i++) {
            String h = headers.optString(i).trim();
            if (!h.contains("课程") && !h.contains("科目")) continue;
            if (h.contains("号") || h.contains("代码") || h.contains("编号")
                    || h.contains("属性") || h.contains("性质") || h.contains("类别")
                    || h.contains("学分") || h.contains("成绩") || h.contains("绩点")) continue;
            return i;
        }
        return -1;
    }

    private static int col(JSONArray headers, String... keys) {
        if (headers == null) return -1;
        for (int i = 0; i < headers.length(); i++) {
            String h = headers.optString(i);
            for (String k : keys) if (h.contains(k)) return i;
        }
        return -1;
    }

    private static String cell(JSONArray row, int i) {
        return cell(row, i, "");
    }

    private static String cell(JSONArray row, int i, String fallback) {
        if (i < 0 || i >= row.length()) return fallback;
        String s = row.optString(i).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static double num(String s) {
        if (s == null) return 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') sb.append(c);
        }
        if (sb.length() == 0) return 0;
        try {
            return Double.parseDouble(sb.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 等级制成绩换算成分数，仅用于算平均分。 */
    private static double scoreOf(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.contains("优秀") || s.equals("优")) return 95;
        if (s.contains("良好") || s.equals("良")) return 85;
        if (s.contains("中等") || s.equals("中")) return 75;
        if (s.contains("及格") && !s.contains("不及格")) return 65;
        if (s.contains("不及格")) return 50;
        if (s.contains("通过") || s.equals("P")) return 65;
        return 0;
    }

    private static boolean isPass(String raw, double score) {
        if (raw != null) {
            if (raw.contains("不及格") || raw.contains("不合格") || raw.contains("未通过")) return false;
            if (raw.contains("缓考") || raw.contains("缺考") || raw.contains("作弊")) return false;
            if (raw.contains("及格") || raw.contains("通过")) return true;
        }
        return score <= 0 || score >= 60;
    }

    // ---------- 河大 URP（强智）接口 ----------
    //
    // 河大教务不是正方，没有 kbList / gradeLnAllAction 这些动作。
    // 页面上的数据来自两个 JSON 接口，字段比表格清楚得多：
    //   · 课表：{"xkxx":[{"<课程键>":{courseName, attendClassTeacher, unit,
    //           timeAndPlaceList:[{classDay, classSessions, continuingSession,
    //           classWeek, campusName, teachingBuildingName, classroomName}]}}]}
    //   · 成绩：{"lnList":[{"cjList":[{courseName, courseScore, gradePointScore,
    //           credit, courseAttributeName, gradeName, id:{...}}]}]}
    // classWeek 是 24 位的 0/1 掩码（第 n 位 = 第 n 周），比「1-16周(单)」这种
    // 字符串可靠，不用再猜单双周。

    /** 解析课表接口。 */
    public static Table urpCourses(String json, String tableId, String tableName) {
        Table t = new Table();
        t.id = tableId;
        t.name = tableName;
        try {
            JSONObject env = new JSONObject(json);
            /* urp.js 会把接口原文放在 data 里（信封还带 kind/sections），
               直接喂原文也认，两种都处理。 */
            JSONObject o = env.has("data") ? new JSONObject(env.optString("data", "{}")) : env;
            if (!o.optString("errorMessage", "").isEmpty()) return t;
            JSONArray xk = o.optJSONArray("xkxx");
            if (xk == null) return t;

            Map<String, Table.Course> map = new LinkedHashMap<>();
            StringBuilder sem = new StringBuilder();

            for (int i = 0; i < xk.length(); i++) {
                JSONObject group = xk.optJSONObject(i);
                if (group == null) continue;
                /* 正常形状是「一个大对象，键是课程标识」；
                   个别学期会直接给课程对象，两种都认。 */
                if (group.has("courseName")) {
                    urpCourse(group, map, sem);
                    continue;
                }
                JSONArray names = group.names();
                if (names == null) continue;
                for (int k = 0; k < names.length(); k++) {
                    JSONObject c = group.optJSONObject(names.optString(k));
                    if (c != null) urpCourse(c, map, sem);
                }
            }

            t.courses.addAll(map.values());
            if (sem.length() > 0) t.semester = urpSemester(sem.toString());
        } catch (Exception ignored) {
        }
        return t;
    }

    private static void urpCourse(JSONObject c, Map<String, Table.Course> map, StringBuilder sem) {
        String name = Table.cleanName(c.optString("courseName", ""));
        if (name.isEmpty()) return;

        JSONObject id = c.optJSONObject("id");
        String cno = id == null ? "" : id.optString("coureNumber", "");
        String seq = id == null ? "" : id.optString("coureSequenceNumber", "");
        String sid = id == null ? "" : id.optString("executiveEducationPlanNumber", "");
        if (sem.length() == 0 && !sid.isEmpty()) sem.append(sid);

        String key = "u_" + cno + "_" + seq;
        Table.Course course = map.get(key);
        if (course == null) {
            course = new Table.Course();
            course.key = key;
            course.name = name;
            /* 教务给的是「刘梦天* 」，星号是标记不是名字 */
            course.teacher = c.optString("attendClassTeacher", "").replace("*", "").trim();
            course.credit = c.optDouble("unit", 0);
            course.attr = !c.optString("coursePropertiesName", "").isEmpty()
                    ? c.optString("coursePropertiesName", "")
                    : c.optString("courseCategoryName", "");
            course.source = Table.SRC_CAMPUS;
            course.color = map.size() % Palette.SIZE;
            map.put(key, course);
        }

        JSONArray taps = c.optJSONArray("timeAndPlaceList");
        if (taps == null) return;
        for (int i = 0; i < taps.length(); i++) {
            JSONObject tp = taps.optJSONObject(i);
            if (tp == null) continue;
            Table.Session s = new Table.Session();
            s.day = Math.max(1, Math.min(7, tp.optInt("classDay", 1)));
            s.start = Math.max(1, tp.optInt("classSessions", 1));
            /* continuingSession 是这次连着上几节，2 就是两节连堂 */
            int span = Math.max(1, tp.optInt("continuingSession", 1));
            s.end = s.start + span - 1;
            s.campus = tp.optString("campusName", "");
            s.room = (tp.optString("teachingBuildingName", "") + tp.optString("classroomName", "")).trim();
            s.raw = tp.optString("weekDescription", "");
            maskWeeks(s.weeks, tp.optString("classWeek", ""));
            if (s.weeks.isEmpty()) s.weeks.addAll(Cal.parseWeeks(s.raw));
            if (!dup(course, s)) course.sessions.add(s);
        }
    }

    /** "111100000000000000000000" -> [1,2,3,4] */
    private static void maskWeeks(List<Integer> weeks, String mask) {
        if (mask == null) return;
        for (int i = 0; i < mask.length() && i < 60; i++) {
            if (mask.charAt(i) == '1') weeks.add(i + 1);
        }
    }

    /** 2026-2027-1-2 -> 显示成「2026-2027 学年秋季」 */
    private static Table.Semester urpSemester(String id) {
        Table.Semester sem = new Table.Semester();
        sem.id = id;
        String[] p = id.split("-");
        if (p.length >= 3) {
            sem.name = p[0] + "-" + p[1] + " 学年" + ("1".equals(p[2]) ? "秋季" : "春季");
        } else {
            sem.name = id;
        }
        return sem;
    }

    /** 解析成绩接口，按学期分组。 */
    public static List<Grade> urpGrades(String json) {
        List<Grade> out = new ArrayList<>();
        try {
            JSONObject env = new JSONObject(json);
            JSONObject o = env.has("data") ? new JSONObject(env.optString("data", "{}")) : env;
            JSONArray ln = o.optJSONArray("lnList");
            if (ln == null) return out;
            for (int i = 0; i < ln.length(); i++) {
                JSONObject term = ln.optJSONObject(i);
                if (term == null) continue;
                String termName = term.optString("cjlx", "");
                if (termName.isEmpty()) termName = term.optString("cjbh", "");
                JSONArray list = term.optJSONArray("cjList");
                if (list == null) continue;
                for (int k = 0; k < list.length(); k++) {
                    JSONObject r = list.optJSONObject(k);
                    if (r == null) continue;
                    Grade g = new Grade();
                    g.name = Table.cleanName(r.optString("courseName", ""));
                    if (g.name.isEmpty()) continue;
                    JSONObject id = r.optJSONObject("id");
                    g.code = id == null ? "" : id.optString("courseNumber", "");
                    g.attr = r.optString("courseAttributeName", "");
                    g.credit = parseDouble(r.optString("credit", "0"));
                    /* courseScore 是百分制；没有的话退回 zscj / cj */
                    g.score = r.optDouble("courseScore", 0);
                    if (g.score <= 0) g.score = parseDouble(r.optString("zscj", "0"));
                    g.gpa = r.optDouble("gradePointScore", 0);
                    g.raw = r.optString("zscj", "");
                    if (g.raw.isEmpty() && g.score > 0) g.raw = fmt(g.score);
                    g.term = !r.optString("academicYearCode", "").isEmpty()
                            ? r.optString("academicYearCode", "") + " 学年"
                            + r.optString("termName", "") + "(" + r.optString("termTypeName", "") + ")"
                            : termName;
                    g.passed = g.score >= 60 || r.optString("gradeName", "").contains("合格");
                    out.add(g);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s == null ? "" : s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static String fmt(double d) {
        return Math.abs(d - Math.rint(d)) < 0.001
                ? String.valueOf((int) Math.rint(d))
                : String.format(java.util.Locale.US, "%.1f", d);
    }
}
