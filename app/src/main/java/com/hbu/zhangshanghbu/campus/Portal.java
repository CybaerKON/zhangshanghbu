// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 河大校园网入口与 URP 教务的地址表。
 *
 * 实测（2026-11）：
 *   · 校外访问 zhjw.hbu.edu.cn 会 302 到 v.hbu.cn/http/&lt;enc&gt;/，即网瑞达 WebVPN，
 *     编码前缀随会话变化 —— 所以不能在原生层写死 URL。这里记住「这次实际落到哪里」，
 *     后续接口都拼在这个前缀下；没有前缀时才退回 zhjw，让它自己 302；
 *   · 本科教务是 URP 综合教务（清元优软，JSP + frameset，GB2312），
 *     登录 /loginAction.do，课表 /xskbcxAction.do?oper=xskbcx，
 *     成绩 /gradeLnAllAction.do（分口径）与 /bxqcjcxAction.do（本学期）；
 *   · 办事大厅 ehall.hbu.edu.cn、学生事务平台 xsfwpt.hbu.cn 是另外两个平台。
 */
public final class Portal {

    private Portal() {}

    public static final String HOST_VPN = "v.hbu.cn";
    public static final String HOST_JW = "zhjw.hbu.cn";
    public static final String HOST_EHALL = "ehall.hbu.edu.cn";
    public static final String HOST_XS = "xsfwpt.hbu.cn";
    public static final String HOST_JWC = "jwc.hbu.edu.cn";

    public static final String URL_VPN = "https://" + HOST_VPN + "/";
    public static final String URL_JW = "https://" + HOST_JW + "/";
    public static final String URL_EHALL = "https://" + HOST_EHALL + "/";
    public static final String URL_XS = "https://" + HOST_XS + "/";
    public static final String URL_JWC = "https://" + HOST_JWC + "/";

    /** URP 入口与常用动作（相对路径）。 */
    /* 河大教务是强智 URP（不是正方），页面路径全部以 /student/... 开头。
       首页是 /index，其余靠菜单进入；直接拼地址必须同时带上 selectionBar cookie，
       否则服务端返回 500「页面丢失」。 */
    public static final String URP_LOGIN = "/lyuapServer/login";
    public static final String URP_INDEX = "/index";
    public static final String URP_COURSE = "/student/courseSelect/thisSemesterCurriculum/index";
    public static final String URP_GRADE_ALL = "/student/integratedQuery/scoreQuery/allPassingScores/index";
    public static final String URP_EXAM = "/student/examinationManagement/examPlan/index";

    /** 空闲教室（也就是「空教室查询」）。 */
    public static final String URP_FREE_ROOM = "/student/teachingResources/freeClassroom/index";

    /** 学籍信息：学号、院系、专业、班级、头像都在这页。 */
    public static final String URP_PROFILE = "/student/rollManagement/rollInfo/index";

    /* 五个成绩口径的页面，顺序与菜单一致 */
    public static final String[] URP_GRADE_PATHS = {
            "/student/integratedQuery/scoreQuery/allPassingScores/index",
            "/student/integratedQuery/scoreQuery/coursePropertyScores/index",
            "/student/integratedQuery/scoreQuery/schemeScores/index",
            "/student/integratedQuery/scoreQuery/unpassedScores/index",
            "/student/integratedQuery/scoreQuery/thisTermScores/index"
    };

    /* 菜单 li 的 id（selectionBar 的值）。用抓到的实际值兜底；
       正常流程会先从教务首页的菜单里现取，取到就用页面上的。 */
    public static final String MENU_COURSE = "82021";
    public static final String MENU_EXAM = "1423379";
    public static final String MENU_FREE_ROOM = "12580354";
    public static final String MENU_PROFILE = "125803573";
    public static final String[] MENU_GRADE = {
            "125803589", "125803590", "125803591", "125803592", "125803593"
    };

    /** 平台标识 */
    public static final String P_VPN = "webvpn";
    public static final String P_JW = "jw";
    public static final String P_EHALL = "ehall";
    public static final String P_XS = "xsfwpt";

    public static final String[] PLATFORMS = {P_VPN, P_JW, P_EHALL, P_XS};

    /** 本次会话里 URP 的真实根地址，形如 https://v.hbu.cn/http/7772…/ 或 https://zhjw.hbu.edu.cn/ */
    private static volatile String base = "";
    private static volatile long lastLoginPageAt = 0;

    /** 从成绩页上发现的分类链接：分类名 → 真实 href。 */
    private static final Map<String, String> tabs = new LinkedHashMap<>();

    public static String name(String key) {
        switch (key) {
            case P_VPN: return "校园网（WebVPN）";
            case P_JW: return "本科教务（URP）";
            case P_EHALL: return "办事大厅";
            case P_XS: return "学生事务平台";
            default: return key;
        }
    }

    public static String shortName(String key) {
        switch (key) {
            case P_VPN: return "校园网";
            case P_JW: return "教务";
            case P_EHALL: return "办事大厅";
            case P_XS: return "学生事务";
            default: return key;
        }
    }

    public static String home(String key) {
        switch (key) {
            case P_VPN: return URL_VPN;
            case P_JW: return URL_JW;
            case P_EHALL: return URL_EHALL;
            case P_XS: return URL_XS;
            default: return URL_JW;
        }
    }

    /** 登录顺序：先打通校园网隧道，再登依赖它的业务平台。 */
    public static String[] loginOrder(boolean withHall) {
        if (withHall) return new String[]{P_VPN, P_JW, P_EHALL};
        return new String[]{P_VPN, P_JW};
    }

    public static String host(String key) {
        switch (key) {
            case P_VPN: return HOST_VPN;
            case P_JW: return HOST_JW;
            case P_EHALL: return HOST_EHALL;
            case P_XS: return HOST_XS;
            default: return HOST_JW;
        }
    }

    public static String[] hosts() {
        return new String[]{HOST_VPN, HOST_JW, HOST_EHALL, HOST_XS};
    }

    // ---------- 真实根地址 ----------

    /**
     * 记下「这次实际落在哪个根地址」。
     * 校外是 https://v.hbu.cn/http/&lt;enc&gt;/，校内可能是 https://zhjw.hbu.edu.cn/。
     */
    public static void noteUrl(String url) {
        if (url == null) return;
        try {
            java.net.URI u = java.net.URI.create(url);
            String host = u.getHost();
            if (host == null) return;
            String path = u.getPath() == null ? "/" : u.getPath();
            if (HOST_VPN.equalsIgnoreCase(host)) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^(/http/[0-9a-fA-F]+/)").matcher(path);
                if (m.find()) {
                    String b = "https://" + host + m.group(1);
                    if (!b.equals(base)) base = b;
                }
            } else if (HOST_JW.equalsIgnoreCase(host)) {
                if (!("https://" + HOST_JW + "/").equals(base)) base = "https://" + HOST_JW + "/";
            }
        } catch (Exception ignored) {
        }
    }

    public static String base() {
        return base;
    }

    /** 把 URP 相对路径拼成可访问地址：优先用真实根地址，否则退回 zhjw 让它自己 302。 */
    public static String url(String path) {
        String p = path == null ? "" : path.replaceFirst("^/", "");
        if (!base.isEmpty()) return base + p;
        return URL_JW + p;
    }

    public static void markLoginPage() {
        lastLoginPageAt = System.currentTimeMillis();
    }

    public static long loginPageAt() {
        return lastLoginPageAt;
    }

    // ---------- 各功能的地址 ----------

    /** 课表：只有一个真实地址，菜单 id 单独给。 */
    public static String[] courseUrls() {
        return new String[]{url(URP_COURSE)};
    }

    public static String courseMenu() {
        return MENU_COURSE;
    }

    public static String[] examUrls() {
        return new String[]{url(URP_EXAM)};
    }

    public static String examMenu() {
        return MENU_EXAM;
    }

    public static String freeRoomUrl() {
        return url(URP_FREE_ROOM);
    }

    public static String freeRoomMenu() {
        return MENU_FREE_ROOM;
    }

    /** 五个口径各自的页面地址。 */
    public static String gradeUrl(int type) {
        return url(URP_GRADE_PATHS[gradeIndex(type)]);
    }

    public static String gradeMenu(int type) {
        return MENU_GRADE[gradeIndex(type)];
    }

    /**
     * Parse 里的口径常量顺序（本学期 / 不及格 / 属性 / 方案 / 全部）
     * 和菜单顺序（全部 / 属性 / 方案 / 不及格 / 本学期）不是一回事，这里做映射。
     */
    private static int gradeIndex(int type) {
        switch (type) {
            case Parse.THIS_TERM: return 4;
            case Parse.FAIL: return 3;
            case Parse.BY_ATTR: return 1;
            case Parse.PLAN: return 2;
            case Parse.ALL_PASS:
            default: return 0;
        }
    }

    /** 校历：URP 里没有这一页，只有教务处网站。 */
    public static String[] calendarUrls() {
        return new String[]{URL_JWC};
    }

    // ---------- 菜单表（保账号无关） ----------
    //
    // 强智每个功能页都要求 cookie selectionBar=<菜单 li 的 id>，而这个 id 是
    // 数据库里的菜单行 id：换账号、甚至同一个人重新登录都可能变。写死必然在
    // 别人账号上 500「页面丢失」，所以每次会话都从 /index 现读一遍，按菜单名匹配。
    // 下面 MENU_* 常量只在读不到页面时兜底。

    private static final Map<String, String> MENU_IDS = new LinkedHashMap<>();

    public static void clearMenu() {
        MENU_IDS.clear();
    }

    public static boolean hasMenu() {
        return !MENU_IDS.isEmpty();
    }

    /** 把 menu.js 交回来的菜单表存起来，返回条数。 */
    public static int fillMenu(String json) {
        MENU_IDS.clear();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("menu");
            if (arr == null) return 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                String label = m.optString("label", "").trim();
                String id = m.optString("id", "").trim();
                if (!label.isEmpty() && !id.isEmpty()) MENU_IDS.put(label, id);
            }
        } catch (Exception ignored) {
        }
        return MENU_IDS.size();
    }

    /**
     * 按菜单名找 id：先精确，再包含（菜单名偶尔多空格或带前缀），
     * 实在没有才用兜底常量。
     */
    public static String menuId(String label, String fallback) {
        if (label != null && !label.isEmpty()) {
            String id = MENU_IDS.get(label);
            if (id != null && !id.isEmpty()) return id;
            for (Map.Entry<String, String> e : MENU_IDS.entrySet()) {
                if (e.getKey().contains(label)) return e.getValue();
            }
        }
        return fallback == null ? "" : fallback;
    }

    public static String courseLabel() {
        return "本学期课表";
    }

    public static String examLabel() {
        return "考试安排";
    }

    public static String freeRoomLabel() {
        return "空闲教室查询";
    }

    public static String profileLabel() {
        return "学生学籍信息";
    }

    public static String gradeLabel(int type) {
        switch (type) {
            case Parse.THIS_TERM: return "本学期成绩";
            case Parse.FAIL: return "不及格成绩";
            case Parse.BY_ATTR: return "课程属性成绩";
            case Parse.PLAN: return "方案成绩";
            case Parse.ALL_PASS:
            default: return "全部及格成绩";
        }
    }

    /** 成绩口径对应的页面路径（菜单顺序与 Parse 常量顺序不同，需要映射）。 */
    public static String gradePath(int type) {
        return URP_GRADE_PATHS[gradeIndex(type)];
    }

    // ---------- 登录阶段 ----------
    //
    // 之前是靠「页面上还有没有密码框」判断登没登，这里有个坑：
    // CAS 的二次验证页（/loginAgain）本来就没有密码框，会被误判成「已登录」，
    // 于是窗口提前跳到下一步，实际还卡在验证码上。
    // 改成按页面位置判阶段，每个阶段对应一句明确的操作提示。

    public static final int ST_LOGIN_VPN = 1;   // v.hbu.cn/login：CAS 或账号登录入口
    public static final int ST_LOGIN_CAS = 2;   // lyuapServer/login：账号 + 密码 + 算术验证码
    public static final int ST_SECOND = 3;      // loginAgain：短信验证码 / 微信扫码
    public static final int ST_INSIDE = 4;      // 已经进了某个系统

    public static int stage(String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return 0;
        String u = url.toLowerCase();
        if (u.contains("/loginagain")) return ST_SECOND;
        if (u.contains("/lyuapserver/login")) return ST_LOGIN_CAS;
        if (u.contains("/login")) return ST_LOGIN_VPN;
        if (u.contains("/https/") || u.contains("/http/")) return ST_INSIDE;
        if (u.contains(HOST_VPN)) return ST_INSIDE;
        return 0;
    }

    /** 这个阶段该怎么提示用户。 */
    public static String stageHint(int stage) {
        switch (stage) {
            case ST_LOGIN_VPN: return "先点「统一认证（CAS登录业务系统）」，用学号密码登录";
            case ST_LOGIN_CAS: return "填学号、密码，算出图片里的算术题填进验证码";
            case ST_SECOND: return "输入手机收到的短信验证码；也可以切「微信/企业微信扫验证」扫码";
            case ST_INSIDE: return "已进入系统，正在检查登录状态…";
            default: return "等待页面加载…";
        }
    }

    public static String keywordOf(String kind) {
        switch (kind) {
            case "courses": return "课表";
            case "grades": return "成绩";
            case "exams": return "考试";
            case "calendar": return "校历";
            default: return null;
        }
    }

    /** 从链接表里挑一个最像目标的地址。 */
    public static String pickLink(JSONArray links, String keyword) {
        if (links == null || keyword == null) return "";
        String best = "";
        int bestScore = 0;
        for (int i = 0; i < links.length(); i++) {
            JSONObject o = links.optJSONObject(i);
            if (o == null) continue;
            String t = o.optString("t", "");
            String h = o.optString("h", "");
            if (h.isEmpty() || h.contains("logout") || h.contains("exit")) continue;
            int score = 0;
            if (t.equals(keyword)) score += 10;
            else if (t.contains(keyword)) score += 6;
            if (h.contains("kbcx") && "课表".equals(keyword)) score += 4;
            if (h.contains("grade") && "成绩".equals(keyword)) score += 4;
            if (h.contains("ks") && "考试".equals(keyword)) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = h;
            }
        }
        return bestScore >= 4 ? best : "";
    }

    /** 成绩页返回的页签链接记下来，下一次切分类直接命中。 */
    public static void learnTabs(JSONArray list) {
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String t = o.optString("t", "").replaceAll("\\s+", "");
            String h = o.optString("h", "");
            if (t.isEmpty() || h.isEmpty()) continue;
            for (String tab : Parse.GRADE_TABS) {
                if (t.contains(tab)) tabs.put(tab, h);
            }
            if (t.contains("本学期") || t.contains("当前学期")) tabs.put(Parse.GRADE_TABS[Parse.THIS_TERM], h);
            if (t.contains("不及格") || t.contains("未通过")) tabs.put(Parse.GRADE_TABS[Parse.FAIL], h);
            if (t.contains("方案")) tabs.put(Parse.GRADE_TABS[Parse.PLAN], h);
            if (t.contains("属性")) tabs.put(Parse.GRADE_TABS[Parse.BY_ATTR], h);
            if (t.contains("全部及格") || (t.contains("全部") && t.contains("及格"))) {
                tabs.put(Parse.GRADE_TABS[Parse.ALL_PASS], h);
            }
        }
    }

    public static String tabOf(String name) {
        return tabs.getOrDefault(name, "");
    }

    // ---------- 判定 ----------

    public static String desktopUa() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    }

    public static String mobileUa() {
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
    }

    public static boolean loginPage(String url) {
        if (url == null) return true;
        String u = url.toLowerCase();
        return u.contains("loginaction") || u.contains("login_slogin")
                || u.contains("authserver") || u.contains("oauth")
                || u.contains("cas/login") || u.endsWith("/login")
                || u.contains("/login?") || u.contains("/login/");
    }

    public static boolean campus(String url) {
        return url != null && (url.contains("hbu.edu.cn") || url.contains("hbu.cn"));
    }

    public static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    /** 教务页面里的「学年学期」文本 → 2026-2027-1 这种 id。 */
    public static String semesterId(String yearName, String termName) {
        if (yearName == null) return "";
        String y = yearName.replaceAll("[^0-9\\-]", "");
        if (y.length() < 9) return "";
        String[] p = y.split("-");
        if (p.length < 2) return "";
        int term = 1;
        if (termName != null && (termName.contains("2") || termName.contains("二")
                || termName.contains("春") || termName.contains("下"))) term = 2;
        return p[0] + "-" + p[1] + "-" + term;
    }
}
