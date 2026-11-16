// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.campus.Engine;
import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.campus.WebActivity;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.ui.Picker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩查询（圈子里的入口）。
 *
 * 五个口径各自对应教务里的一个页面，**按需分别取**，不是取一份再在本地猜：
 *   全部及格 / 课程属性 / 方案成绩 / 不及格 / 本学期
 *   · 每个口径第一次点开时去教务取，取到就按口径缓存，下次直接出；
 *   · 「全部及格」这一页还会带回教务自己的页签链接（Portal.learnTabs），
 *     后面几个口径优先走这些真实地址，比猜 URL 稳；
 *   · 学期从页面文本与「学期」列两方面识别，能自动选中最新学期，也能手动换；
 *   · 课程名只认「课程名称」列，绝不会再拿课程号当名字（上一版的问题）。
 */
public class GradesActivity extends PageActivity {

    private Store store;
    private int tab = Parse.ALL_PASS;
    private String term = "";                 // "" = 自动/全部

    private final Map<Integer, List<Parse.Grade>> byTab = new HashMap<>();
    private final Map<Integer, String> semesterText = new HashMap<>();

    private LinearLayout listBox;
    private LinearLayout summaryBox;
    private TextView termLabel;
    private TextView status;
    private LinearLayout tabBar;
    private boolean needLogin;

    @Override
    protected String title() {
        return "成绩查询";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        termLabel = Kit.text(this, "学期：自动识别", 12.5f, Kit.brand(this));
        termLabel.setPadding(0, Kit.dp(this, 6), 0, Kit.dp(this, 6));
        termLabel.setOnClickListener(v -> pickTerm());
        top.addView(termLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = Kit.text(this, "刷新", 12.5f, Kit.brand(this));
        refresh.setPadding(Kit.dp(this, 12), Kit.dp(this, 6), Kit.dp(this, 6), Kit.dp(this, 6));
        refresh.setOnClickListener(v -> load(tab, true));
        top.addView(refresh);
        content.addView(top);

        status = Kit.text(this, "", 11f, Kit.faint(this));
        status.setOnClickListener(v -> openWeb());
        content.addView(status);
        gap(8);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Parse.GRADE_TABS.length; i++) {
            final int idx = i;
            TextView chip = Kit.text(this, Parse.GRADE_TABS[i], 12.5f, Kit.sub(this));
            chip.setPadding(Kit.dp(this, 14), Kit.dp(this, 7), Kit.dp(this, 14), Kit.dp(this, 7));
            chip.setBackground(Kit.round(this, Kit.surface(this), 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Kit.dp(this, 8);
            chip.setTag(idx);
            chip.setOnClickListener(v -> {
                tab = idx;
                term = "";
                paintTabs();
                List<Parse.Grade> cached = cached(idx);
                if (cached != null) {
                    render(cached);
                    status.setText(sourceOf(idx));
                } else {
                    load(idx, false);
                }
            });
            tabBar.addView(chip, lp);
        }
        hs.addView(tabBar);
        content.addView(hs);
        gap(10);

        summaryBox = new LinearLayout(this);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(summaryBox);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);

        paintTabs();
        List<Parse.Grade> cached = cached(tab);
        if (cached != null) render(cached);
        load(tab, false);
    }

    private void paintTabs() {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            TextView chip = (TextView) tabBar.getChildAt(i);
            boolean on = i == tab;
            chip.setTextColor(on ? 0xFFFFFFFF : Kit.sub(this));
            chip.setBackground(Kit.round(this, on ? Kit.brand(this) : Kit.surface(this), 14));
        }
    }

    // ---------- 取数 ----------

    private List<Parse.Grade> cached(int type) {
        List<Parse.Grade> list = byTab.get(type);
        if (list != null) return list;
        String c = store.cache(cacheKey(type));
        if (c.isEmpty()) return null;
        list = Parse.grades(c);
        if (list.isEmpty()) return null;
        byTab.put(type, list);
        try {
            semesterText.put(type, new JSONObject(c).optString("semesterText", ""));
        } catch (Exception ignored) {
        }
        return list;
    }

    private String cacheKey(int type) {
        return "grades_" + type;
    }

    private void load(int type, boolean force) {
        if (!Session.ok(Portal.P_JW)) {
            status.setText("教务未登录 · 点这里登录后自动读取");
            status.setTextColor(Kit.brand(this));
            if (cached(type) == null) showLoginHint();
            return;
        }
        if (!force && cached(type) != null) {
            render(cached(type));
            return;
        }
        status.setTextColor(Kit.faint(this));
        status.setText("正在打开教务「" + Parse.GRADE_TABS[type] + "」…");
        render(null);

        Engine.fetchUrpByLabel(this, Portal.gradePath(type), Portal.gradeLabel(type),
                Portal.gradeMenu(type), "grades", (ok, json, msg) -> {
            if (isFinishing()) return;
            if (!ok) {
                needLogin = msg.contains("登录");
                List<Parse.Grade> old = cached(type);
                status.setText(msg + (old != null ? "（显示的是上次缓存）" : "· 点这里打开网页版"));
                if (old != null) render(old);
                else showError(msg);
                return;
            }
            try {
                JSONObject o = new JSONObject(json);
                Portal.learnTabs(o.optJSONArray("tabs"));
                semesterText.put(type, o.optString("semesterText", ""));
            } catch (Exception ignored) {
            }
            List<Parse.Grade> list = Parse.urpGrades(json);
            if (list.isEmpty()) {
                status.setText("这一页没有解析出成绩 · 点这里打开网页版核对");
                showError("页面结构和预期不一样。可以点状态栏打开网页版，把页面结构告诉我。");
                return;
            }
            needLogin = false;
            byTab.put(type, list);
            store.cache(cacheKey(type), json);
            status.setText(sourceOf(type));
            if (tab == type) {
                render(list);
            }
        });
    }

    private String sourceOf(int type) {
        int n = cached(type) == null ? 0 : cached(type).size();
        String extra = semesterText.get(type) == null || semesterText.get(type).isEmpty()
                ? "" : " · " + semesterText.get(type);
        return "共 " + n + " 条" + extra + " · 数据只存本机";
    }

    private void openWeb() {
        Session.ensure(this, new String[]{Portal.P_JW}, () ->
                WebActivity.open(this, "成绩查询", Portal.url(Portal.URP_GRADE_ALL)));
    }

    // ---------- 渲染 ----------

    private void showLoginHint() {
        listBox.removeAllViews();
        LinearLayout card = card();
        card.addView(pad(Kit.text(this, "还没有登录教务。登录之后，五种口径会各自去教务取一次，之后离线也能看。",
                13f, Kit.sub(this))));
        TextView go = Kit.primary(this, "登录并读取", () -> Session.ensure(this,
                new String[]{Portal.P_JW}, () -> load(tab, true)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Kit.dp(this, 14), 0, Kit.dp(this, 14), Kit.dp(this, 14));
        card.addView(go, lp);
        listBox.addView(card);
    }

    private void showError(String msg) {
        listBox.removeAllViews();
        LinearLayout card = card();
        card.addView(pad(Kit.text(this, msg, 13f, Kit.sub(this))));
        TextView go = Kit.primary(this, "打开网页版", this::openWeb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Kit.dp(this, 14), 0, Kit.dp(this, 14), Kit.dp(this, 14));
        card.addView(go, lp);
        listBox.addView(card);
    }

    private void render(List<Parse.Grade> source) {
        if (summaryBox == null) return;
        List<Parse.Grade> list = source == null ? new ArrayList<>() : filtered(source);
        summary(list);

        termLabel.setText(term.isEmpty()
                ? ("学期：自动识别" + (autoTerm(source) == null ? "" : "（" + autoTerm(source) + "）"))
                : "学期：" + term);

        listBox.removeAllViews();
        if (list.isEmpty()) {
            LinearLayout card = card();
            card.addView(pad(Kit.text(this, source == null || source.isEmpty()
                    ? "正在读取…" : "这个口径下没有记录。", 13f, Kit.sub(this))));
            listBox.addView(card);
            return;
        }

        if (tab == Parse.BY_ATTR) {
            Map<String, List<Parse.Grade>> groups = new LinkedHashMap<>();
            for (Parse.Grade g : list) {
                String k = g.attr.isEmpty() ? "未标注属性" : g.attr;
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(g);
            }
            for (Map.Entry<String, List<Parse.Grade>> e : groups.entrySet()) {
                TextView h = Kit.section(this, e.getKey() + " · " + e.getValue().size() + " 门");
                h.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 4));
                listBox.addView(h);
                listBox.addView(cards(e.getValue()));
            }
        } else if (tab == Parse.THIS_TERM) {
            /* 本学期页本身就分学期，按学期分段显示，一眼能看出是哪一学期 */
            Map<String, List<Parse.Grade>> groups = new LinkedHashMap<>();
            for (Parse.Grade g : list) {
                String k = g.term.isEmpty() ? "本学期" : g.term;
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(g);
            }
            for (Map.Entry<String, List<Parse.Grade>> e : groups.entrySet()) {
                if (groups.size() > 1) {
                    TextView h = Kit.section(this, e.getKey() + " · " + e.getValue().size() + " 门");
                    h.setPadding(0, Kit.dp(this, 10), 0, Kit.dp(this, 4));
                    listBox.addView(h);
                }
                listBox.addView(cards(e.getValue()));
            }
        } else {
            listBox.addView(cards(list));
        }
    }

    private View cards(List<Parse.Grade> list) {
        LinearLayout card = card();
        for (int i = 0; i < list.size(); i++) {
            Parse.Grade g = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Kit.dp(this, 14), Kit.dp(this, 11), Kit.dp(this, 14), Kit.dp(this, 11));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(Kit.text(this, g.name, 14f, Kit.ink(this)));

            StringBuilder meta = new StringBuilder();
            if (!g.code.isEmpty()) meta.append(g.code);
            if (g.credit > 0) meta.append(meta.length() > 0 ? " · " : "").append(Parse.fmt(g.credit)).append(" 学分");
            if (!g.attr.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(g.attr);
            if (!g.term.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(g.term);
            if (g.gpa > 0) meta.append(meta.length() > 0 ? " · " : "").append("绩点 ").append(Parse.fmt(g.gpa));
            if (meta.length() > 0) {
                TextView m = Kit.text(this, meta.toString(), 10.5f, Kit.faint(this));
                m.setPadding(0, Kit.dp(this, 2), 0, 0);
                info.addView(m);
            }
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView score = Kit.bold(this, g.scoreText(), 16f,
                    g.passed ? Kit.ink(this) : 0xFFC0392B);
            row.addView(score);

            card.addView(row);
            if (i < list.size() - 1) card.addView(Kit.hr(this));
        }
        return card;
    }

    private void summary(List<Parse.Grade> list) {
        summaryBox.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Kit.dp(this, 18), Kit.dp(this, 14), Kit.dp(this, 18), Kit.dp(this, 14));
        card.setBackground(Kit.round(this, 0xFF0C4C8C, 16));

        card.addView(stat(list.size() + "", "门"));
        card.addView(bar());
        double avg = Parse.avgScore(list);
        card.addView(stat(avg > 0 ? Parse.fmt(avg) : "—", "加权平均"));
        card.addView(bar());
        double gpa = Parse.avgGpa(list);
        card.addView(stat(gpa > 0 ? String.format(java.util.Locale.US, "%.2f", gpa) : "—", "平均绩点"));
        summaryBox.addView(card);
    }

    private LinearLayout stat(String v, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Kit.bold(this, v, 19f, 0xFFFFFFFF));
        col.addView(Kit.text(this, label, 10.5f, 0xFFBBD6F2));
        return col;
    }

    private android.view.View bar() {
        android.view.View v = new android.view.View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Kit.dp(this, 1), Kit.dp(this, 28));
        lp.leftMargin = Kit.dp(this, 16);
        lp.rightMargin = Kit.dp(this, 16);
        v.setLayoutParams(lp);
        v.setBackgroundColor(0x33FFFFFF);
        return v;
    }

    // ---------- 过滤与学期 ----------

    private List<Parse.Grade> filtered(List<Parse.Grade> src) {
        List<Parse.Grade> list = new ArrayList<>();
        if (tab == Parse.THIS_TERM) {
            return new ArrayList<>(src);
        }
        list = Parse.filter(src, tab);
        if (!term.isEmpty()) {
            List<Parse.Grade> byTerm = new ArrayList<>();
            for (Parse.Grade g : list) {
                if (g.term.contains(term) || term.contains(g.term)) byTerm.add(g);
            }
            if (!byTerm.isEmpty()) return byTerm;
        }
        return list;
    }

    private String autoTerm(List<Parse.Grade> list) {
        String txt = semesterText.get(tab);
        if (txt != null && !txt.isEmpty()) return txt;
        String best = "";
        if (list != null) {
            for (Parse.Grade g : list) {
                if (g.term.length() > best.length()) best = g.term;
            }
        }
        return best;
    }

    private void pickTerm() {
        List<String> terms = new ArrayList<>();
        terms.add("自动（最新学期）");
        List<Parse.Grade> src = cached(tab);
        if (src != null) {
            for (Parse.Grade g : src) {
                if (!g.term.isEmpty() && !terms.contains(g.term)) terms.add(g.term);
            }
        }
        int cur = 0;
        if (!term.isEmpty()) {
            for (int i = 0; i < terms.size(); i++) if (terms.get(i).equals(term)) cur = i;
        }
        Picker.show(this, "学期", terms.toArray(new String[0]), cur, i -> {
            term = i == 0 ? "" : terms.get(i);
            render(cached(tab));
        });
    }

    private TextView pad(TextView t) {
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
        return t;
    }
}
