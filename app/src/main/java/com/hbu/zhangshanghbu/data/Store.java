// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地存储：课表（可多份）、作息、外观、账号、登录态、圈子布局、上课通知、页面缓存。
 *
 * 原则：
 *   · 课表按 id 分开存（t_&lt;id&gt;），再加一个 tables 列表描述名字与顺序，
 *     这样「多个课表自由增删」不用改结构；
 *   · 只存 Cookie 快照与学号，不存密码；
 *   · 任何写操作都是 apply()，不阻塞 UI；读的时候坏数据一律当空处理，不崩。
 */
public final class Store {

    private static final String FILE = "zshbu";

    private static final String K_THEME = "theme";
    private static final String K_TABLES = "tables";
    private static final String K_ACTIVE = "active";
    private static final String K_TABLE = "t_";
    private static final String K_SCHEDULE = "schedule";
    private static final String K_USER = "user";
    private static final String K_SNAME = "sname";
    private static final String K_SNO = "sno";
    private static final String K_SDEPT = "sdept";
    private static final String K_SMAJOR = "smajor";
    private static final String K_SCLS = "scls";
    private static final String K_PROFILE_AT = "profile_at";
    private static final String K_COOKIE = "cookie";
    private static final String K_LOGIN_AT = "login_at";
    private static final String K_PLAT = "platform";
    private static final String K_CIRCLE = "circle";
    private static final String K_NOTICE = "notice";
    private static final String K_NOTICE_LEAD = "notice_lead";
    private static final String K_DESK_UA = "desk_ua";
    private static final String K_CACHE = "cache_";

    public static final int THEME_LIGHT = 0, THEME_DARK = 1, THEME_SYSTEM = 2;

    private final SharedPreferences sp;
    private final Context app;

    public Store(Context c) {
        app = c.getApplicationContext();
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------- 多课表 ----------

    public static final class Meta {
        public String id = "";
        public String name = "";
        public long created;

        public Meta() {}

        public Meta(String id, String name, long created) {
            this.id = id;
            this.name = name;
            this.created = created;
        }
    }

    public List<Meta> tables() {
        List<Meta> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(K_TABLES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Meta m = new Meta();
                m.id = o.optString("id");
                m.name = o.optString("name");
                m.created = o.optLong("created");
                if (!m.id.isEmpty()) out.add(m);
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            Meta m = new Meta("main", "我的课表", System.currentTimeMillis());
            out.add(m);
            saveMeta(out);
            setActive("main");
        }
        return out;
    }

    private void saveMeta(List<Meta> list) {
        JSONArray a = new JSONArray();
        for (Meta m : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", m.id);
                o.put("name", m.name);
                o.put("created", m.created);
                a.put(o);
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString(K_TABLES, a.toString()).apply();
    }

    public String activeId() {
        String id = sp.getString(K_ACTIVE, "");
        if (!id.isEmpty() && sp.contains(K_TABLE + id)) return id;
        List<Meta> all = tables();
        return all.isEmpty() ? "main" : all.get(0).id;
    }

    public void setActive(String id) {
        if (id == null || id.isEmpty()) return;
        sp.edit().putString(K_ACTIVE, id).apply();
    }

    /** 新建一份空课表，返回它的 id。 */
    public String addTable(String name) {
        List<Meta> all = tables();
        String id = "t" + System.currentTimeMillis();
        all.add(0, new Meta(id, name == null || name.trim().isEmpty() ? ("课表 " + (all.size() + 1)) : name.trim(),
                System.currentTimeMillis()));
        saveMeta(all);
        setActive(id);
        Table t = new Table();
        t.id = id;
        t.name = all.get(0).name;
        t.semester.name = t.name;
        t.semester.id = id;
        saveTable(t);
        return id;
    }

    public void renameTable(String id, String name) {
        List<Meta> all = tables();
        for (Meta m : all) {
            if (m.id.equals(id)) m.name = name;
        }
        saveMeta(all);
        Table t = loadTable(id);
        if (t != null) {
            t.name = name;
            saveTable(t);
        }
    }

    public void deleteTable(String id) {
        sp.edit().remove(K_TABLE + id).apply();
        List<Meta> all = tables();
        all.removeIf(m -> m.id.equals(id));
        if (all.isEmpty()) all.add(new Meta("main", "我的课表", System.currentTimeMillis()));
        saveMeta(all);
        if (id.equals(sp.getString(K_ACTIVE, ""))) setActive(all.get(0).id);
    }

    public Table loadTable(String id) {
        String json = sp.getString(K_TABLE + id, null);
        if (json == null) {
            Table t = new Table();
            t.id = id;
            return t;
        }
        try {
            Table t = Table.from(id, new JSONObject(json));
            if (t.name.isEmpty() || t.name.equals("我的课表")) {
                for (Meta m : tables()) if (m.id.equals(id)) t.name = m.name;
            }
            return t;
        } catch (Exception e) {
            Table t = new Table();
            t.id = id;
            return t;
        }
    }

    public Table activeTable() {
        return loadTable(activeId());
    }

    public void saveTable(Table t) {
        if (t == null || t.id == null || t.id.isEmpty()) return;
        try {
            sp.edit().putString(K_TABLE + t.id, t.json().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public String tableName(String id) {
        for (Meta m : tables()) if (m.id.equals(id)) return m.name;
        return "课表";
    }

    // ---------- 作息 ----------

    public Schedule schedule() {
        try {
            String s = sp.getString(K_SCHEDULE, "");
            if (s.isEmpty()) return Schedule.defaults();
            return Schedule.from(new JSONObject(s));
        } catch (Exception e) {
            return Schedule.defaults();
        }
    }

    public void saveSchedule(Schedule s) {
        if (s == null) return;
        try {
            sp.edit().putString(K_SCHEDULE, s.json().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------- 外观 ----------

    public int themeMode() {
        return sp.getInt(K_THEME, THEME_SYSTEM);
    }

    public void setThemeMode(int m) {
        sp.edit().putInt(K_THEME, m == THEME_LIGHT || m == THEME_DARK ? m : THEME_SYSTEM).apply();
    }

    public boolean darkNow() {
        int m = themeMode();
        if (m == THEME_DARK) return true;
        if (m == THEME_LIGHT) return false;
        int sys = app.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return sys == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public boolean desktopUa() {
        return sp.getBoolean(K_DESK_UA, true);
    }

    public void setDesktopUa(boolean on) {
        sp.edit().putBoolean(K_DESK_UA, on).apply();
    }

    // ---------- 账号与登录态 ----------

    public String user() {
        return sp.getString(K_USER, "");
    }

    public void setUser(String u) {
        sp.edit().putString(K_USER, u == null ? "" : u.trim()).apply();
    }

    public String studentName() {
        return sp.getString(K_SNAME, "");
    }

    public void setStudentName(String n) {
        sp.edit().putString(K_SNAME, n == null ? "" : n.trim()).apply();
    }

    // ---------- 学生本人信息（登录后自动从学籍页读回来） ----------

    public String studentNo() {
        return sp.getString(K_SNO, "");
    }

    public String dept() {
        return sp.getString(K_SDEPT, "");
    }

    public String major() {
        return sp.getString(K_SMAJOR, "");
    }

    public String clazz() {
        return sp.getString(K_SCLS, "");
    }

    /** 上一次成功读取本人信息的时间，用来决定要不要刷新。 */
    public long profileAt() {
        return sp.getLong(K_PROFILE_AT, 0L);
    }

    /**
     * 保存从学籍页读到的人信息。头像单独存文件（几十 KB 的 base64 塞进
     * SharedPreferences 会让整个 XML 每次都被重写，不划算）。
     */
    public void setProfile(String name, String no, String dept, String major, String cls) {
        sp.edit()
                .putString(K_SNAME, clean(name))
                .putString(K_SNO, clean(no))
                .putString(K_SDEPT, clean(dept))
                .putString(K_SMAJOR, clean(major))
                .putString(K_SCLS, clean(cls))
                .putLong(K_PROFILE_AT, System.currentTimeMillis())
                .apply();
        // 学号读到了就顺手当显示用账号，省得用户自己填
        if (no != null && !no.trim().isEmpty()) setUser(no.trim());
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    public java.io.File avatarFile() {
        return new java.io.File(app.getFilesDir(), "avatar.img");
    }

    public boolean hasAvatar() {
        java.io.File f = avatarFile();
        return f.exists() && f.length() > 200;
    }

    /** 把 base64 头像落盘。失败就当没有，不影响其它功能。 */
    public boolean saveAvatar(String base64) {
        if (base64 == null || base64.length() < 100) return false;
        try {
            byte[] raw = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            if (raw.length < 200) return false;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(avatarFile())) {
                out.write(raw);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearProfile() {
        sp.edit().putString(K_SNO, "").putString(K_SDEPT, "")
                .putString(K_SMAJOR, "").putString(K_SCLS, "")
                .putLong(K_PROFILE_AT, 0L).apply();
        try {
            avatarFile().delete();
        } catch (Exception ignored) {
        }
    }

    public String cookie() {
        return sp.getString(K_COOKIE, "{}");
    }

    public void setCookie(String json) {
        sp.edit().putString(K_COOKIE, json == null ? "{}" : json).apply();
    }

    public long loginAt() {
        return sp.getLong(K_LOGIN_AT, 0L);
    }

    public void setLoginAt(long t) {
        sp.edit().putLong(K_LOGIN_AT, t).apply();
    }

    /** 每个平台最近一次确认在线的时间（毫秒），0 表示没确认过。 */
    public long platformAt(String key) {
        try {
            JSONObject o = new JSONObject(sp.getString(K_PLAT, "{}"));
            return o.optLong(key, 0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    public void setPlatformAt(String key, long t) {
        try {
            JSONObject o = new JSONObject(sp.getString(K_PLAT, "{}"));
            o.put(key, t);
            sp.edit().putString(K_PLAT, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void clearLogin() {
        sp.edit().remove(K_COOKIE).remove(K_LOGIN_AT).remove(K_PLAT)
                .remove(K_SNAME).apply();
    }

    // ---------- 圈子 ----------

    /** 返回 id → 是否显示；没记录过的算显示。 */
    public void saveCircle(String json) {
        sp.edit().putString(K_CIRCLE, json).apply();
    }

    public String circle() {
        return sp.getString(K_CIRCLE, "");
    }

    // ---------- 上课通知 ----------

    public boolean noticeOn() {
        return sp.getBoolean(K_NOTICE, true);
    }

    public void setNoticeOn(boolean on) {
        sp.edit().putBoolean(K_NOTICE, on).apply();
    }

    public int noticeLead() {
        return sp.getInt(K_NOTICE_LEAD, 15);
    }

    public void setNoticeLead(int min) {
        sp.edit().putInt(K_NOTICE_LEAD, Math.max(0, Math.min(120, min))).apply();
    }

    // ---------- 缓存 ----------

    public void cache(String key, String value) {
        sp.edit().putString(K_CACHE + key, value).apply();
    }

    public String cache(String key) {
        return sp.getString(K_CACHE + key, "");
    }

    public void clearCache() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) {
            if (k.startsWith(K_CACHE)) e.remove(k);
        }
        e.apply();
    }
}
