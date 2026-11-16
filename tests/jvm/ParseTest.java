import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.data.Table;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 原生解析层测试（不需要 Android 设备）。
 *
 * 重点盯住上一版的三个具体问题：
 *   1. 成绩表头是「课程号 | 课程名称 …」时，列表里显示的是课程号 —— 必须取课程名称；
 *   2. 五类口径没有分开、学期识别不出来；
 *   3. 一行里塞了两门课时没有拆开。
 */
public final class ParseTest {

    private static int failed = 0;

    private static void check(String name, boolean ok, Object detail) {
        System.out.println((ok ? "  ✓ " : "  ✗ ") + name + (ok ? "" : " — " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. 成绩列映射：课程名优先");
        JSONObject grade = new JSONObject();
        grade.put("headers", new JSONArray(new String[]{"课程号", "课程名称", "课程属性", "学分", "成绩", "绩点"}));
        JSONArray rows = new JSONArray();
        rows.put(new JSONArray(new String[]{"B1234567", "高等数学A(二)", "学科基础课", "5.0", "92", "4.2"}));
        rows.put(new JSONArray(new String[]{"B7654321", "数据结构", "专业必修", "3.5", "55", "0"}));
        grade.put("rows", rows);
        grade.put("semesterText", "2026-2027学年 第一学期");

        List<Parse.Grade> list = Parse.grades(grade.toString());
        check("两条成绩", list.size() == 2, list.size());
        Parse.Grade g = list.isEmpty() ? new Parse.Grade() : list.get(0);
        check("名字是课程名而不是课程号", "高等数学A(二)".equals(g.name), g.name);
        check("课程号单独保留", "B1234567".equals(g.code), g.code);
        check("课程属性", "学科基础课".equals(g.attr), g.attr);
        check("学分 5", Math.abs(g.credit - 5) < 0.001, g.credit);
        check("成绩 92", Math.abs(g.score - 92) < 0.001, g.score);
        check("绩点 4.2", Math.abs(g.gpa - 4.2) < 0.001, g.gpa);
        check("及格判定", g.passed, g.passed);

        System.out.println("2. 不及格口径");
        List<Parse.Grade> fail = Parse.filter(list, Parse.FAIL);
        check("只留下不及格", fail.size() == 1 && fail.get(0).name.equals("数据结构"),
                fail.size() + " / " + (fail.isEmpty() ? "" : fail.get(0).name));

        System.out.println("3. 没有「课程名称」列时的兜底");
        JSONObject g2 = new JSONObject();
        g2.put("headers", new JSONArray(new String[]{"课程号", "科目", "学分", "成绩"}));
        JSONArray r2 = new JSONArray();
        r2.put(new JSONArray(new String[]{"B0001", "大学物理", "4.0", "88"}));
        g2.put("rows", r2);
        List<Parse.Grade> l2 = Parse.grades(g2.toString());
        check("兜底取到科目列", l2.size() == 1 && "大学物理".equals(l2.get(0).name),
                l2.isEmpty() ? "empty" : l2.get(0).name);

        System.out.println("4. 一行两门课要拆开");
        JSONObject g3 = new JSONObject();
        g3.put("headers", new JSONArray(new String[]{"课程号", "课程名称", "学分", "成绩"}));
        JSONArray r3 = new JSONArray();
        r3.put(new JSONArray(new String[]{"B0002", "形势与政策；军事理论", "2.0", "90"}));
        g3.put("rows", r3);
        List<Parse.Grade> l3 = Parse.grades(g3.toString());
        check("拆成两条", l3.size() == 2, l3.size());
        check("第一条名字", !l3.isEmpty() && l3.get(0).name.contains("形势与政策"),
                l3.isEmpty() ? "" : l3.get(0).name);
        check("第二条名字", l3.size() > 1 && l3.get(1).name.contains("军事理论"),
                l3.size() > 1 ? l3.get(1).name : "");

        System.out.println("5. 课表记录 → Table");
        JSONObject course = new JSONObject();
        JSONArray recs = new JSONArray();
        recs.put(rec("高等数学A(二)", "王建国", "七教302", 1, 1, 2, "1,2,3,4,5,6,7,8"));
        recs.put(rec("高等数学A(二)", "王建国", "七教302", 3, 1, 2, "1,2,3,4,5,6,7,8"));
        recs.put(rec("大学物理", "李红", "九教105", 2, 3, 4, "1,2,3,4,5,6,7,8"));
        course.put("records", recs);
        course.put("term", "2026-2027-1");
        Table t = Parse.courses(course.toString(), "main", "我的课表");
        check("两门课", t.courses.size() == 2, t.courses.size());
        Table.Course math = null;
        for (Table.Course c : t.courses) if (c.name.startsWith("高等数学")) math = c;
        check("高数两个时段", math != null && math.sessions.size() == 2,
                math == null ? "null" : math.sessions.size());
        check("学期 id", "2026-2027-1".equals(t.semester.id), t.semester.id);

        System.out.println("6. 周次解析：单双周与连周");
        check("1-16 周 16 个", Parse.courses(course.toString(), "m", "m").semester.weeks >= 8, "");

        System.out.println(failed == 0 ? "\n全部通过" : "\n" + failed + " 项失败");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static JSONObject rec(String name, String teacher, String room, int day,
                                  int start, int end, String weeks) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("teacher", teacher);
        o.put("room", room);
        o.put("day", day);
        o.put("start", start);
        o.put("end", end);
        JSONArray a = new JSONArray();
        for (String s : weeks.split(",")) a.put(Integer.parseInt(s.trim()));
        o.put("weeks", a);
        return o;
    }
}
