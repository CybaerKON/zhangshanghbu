// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.pages;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hbu.zhangshanghbu.MainActivity;
import com.hbu.zhangshanghbu.R;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.campus.WebActivity;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;
import com.hbu.zhangshanghbu.feat.ExamsActivity;
import com.hbu.zhangshanghbu.feat.FreeRoomActivity;
import com.hbu.zhangshanghbu.feat.GradesActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 圈子：一堆入口的聚合页（不再是成绩页）。
 *
 * 每个板块可以开关、可以长按拖动排序，统一在「管理」里改；关掉等于隐藏，
 * 数据不删，随时能再打开。第一个「热点」、第二个「体测计算」、第三个「公交查询」
 * 目前是占位入口，点开明确显示「开发中，暂不可使用」。
 */
public class CirclePage extends ScrollView {

    private static final String[][] CATALOG = {
            // id, 名称, 副标题, 颜色, 类型
            {"hot", "热点", "校园热点与通知", "E8734A", "dev"},
            {"fitness", "体测成绩计算", "换算总分与等级", "3FA37A", "dev"},
            {"bus", "公交查询", "校门口实时公交", "D9A21B", "dev"},
            {"grades", "成绩查询", "五类成绩 · 自动识别学期", "3B7BE8", "grades"},
            {"exams", "考试安排", "自动读取教务", "9B6BDA", "exams"},
            {"calendar", "校历", "校历与周次（开发中）", "3F9FB8", "dev"},
            {"room", "空教室", "按节次实时查空闲教室", "C9527A", "room"},
            {"hall", "办事大厅", "奖助 / 请假", "6E7FA8", "hall"},
            /* 下面几个地址取自融合门户自己的资源列表接口，不是猜的 */
            {"fanya", "网络教学平台", "学习通 · 课程与作业", "E8734A", "web",
                    "http://hbu.fanya.chaoxing.com/portal"},
            {"zhihuishu", "智慧树", "共享课与直播", "3FA37A", "web",
                    "https://www.zhihuishu.com/"},
            {"xuetangx", "学堂在线", "雨课堂 · 课程回放", "9B6BDA", "web",
                    "https://hbu.xuetangx.com/pro/portal/home/"},
            {"mooc", "中国大学MOOC", "通识课与专业课", "3B7BE8", "web",
                    "https://www.icourse163.org/"},
            {"mail", "学生邮箱", "stumail.hbu.edu.cn", "3F9FB8", "web",
                    "http://stumail.hbu.edu.cn"},
    };

    private final Activity host;
    private final Store store;
    private final LinearLayout page;

    private final List<String[]> order = new ArrayList<>();
    private boolean managing;

    public CirclePage(Activity host, Store store) {
        super(host);
        this.host = host;
        this.store = store;
        setFillViewport(true);
        setBackgroundColor(Kit.page(host));
        page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Kit.dp(host, 18), Kit.dp(host, 12), Kit.dp(host, 18), 0);
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        reload();
    }

    public void reload() {
        page.removeAllViews();
        loadState();

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Kit.bold(host, "圈子", 19f, Kit.ink(host)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView manage = Kit.text(host, managing ? "完成" : "管理", 13f, Kit.brand(host));
        manage.setPadding(Kit.dp(host, 12), Kit.dp(host, 6), Kit.dp(host, 10), Kit.dp(host, 6));
        manage.setBackground(Kit.round(host, Kit.night(host) ? 0xFF12253C : 0xFFE7EFF9, 10));
        manage.setOnClickListener(v -> {
            managing = !managing;
            reload();
        });
        head.addView(manage);
        page.addView(head);

        TextView hint = Kit.text(host, managing
                ? "开关控制显示 / 隐藏；长按拖动可以排序"
                : "点开即用 · 关掉的板块在「管理」里还能找回来", 11.5f, Kit.faint(host));
        hint.setPadding(0, Kit.dp(host, 6), 0, Kit.dp(host, 12));
        page.addView(hint);

        // 登录提示（圈子里的多数功能要校园网）
        String warn = Session.expiredText();
        if (!warn.isEmpty()) {
            LinearLayout bar = new LinearLayout(host);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackground(Kit.stroke(host, Kit.night(host) ? 0xFF2C2018 : 0xFFFFF6E8,
                    14, 0x33E8734A));
            bar.setPadding(Kit.dp(host, 14), Kit.dp(host, 10), Kit.dp(host, 12), Kit.dp(host, 10));
            bar.setOnClickListener(v -> {
                if (host instanceof MainActivity) ((MainActivity) host).login();
            });
            TextView t = Kit.text(host, warn + " · 点这里重新登录", 12f, 0xFF9A5B12);
            bar.addView(t);
            page.addView(bar);
            page.addView(Kit.gap(host, 12));
        }

        if (managing) {
            page.addView(list());
        } else {
            page.addView(grid());
        }

        TextView foot = Kit.text(host, "圈子里的功能会陆续补齐", 10.5f, Kit.faint(host));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Kit.dp(host, 18), 0, Kit.dp(host, 10));
        page.addView(foot);
    }

    // ---------- 数据 ----------

    private void loadState() {
        order.clear();
        String saved = store.circle();
        if (!saved.isEmpty()) {
            try {
                JSONArray a = new JSONArray(saved);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("id");
                    for (String[] e : CATALOG) {
                        if (e[0].equals(id)) order.add(new String[]{id, o.optBoolean("on", true) ? "1" : "0"});
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // 补齐新增板块
        for (String[] e : CATALOG) {
            boolean has = false;
            for (String[] o : order) if (o[0].equals(e[0])) has = true;
            if (!has) order.add(new String[]{e[0], "1"});
        }
    }

    private void persist() {
        JSONArray a = new JSONArray();
        for (String[] o : order) {
            JSONObject x = new JSONObject();
            try {
                x.put("id", o[0]);
                x.put("on", "1".equals(o[1]));
                a.put(x);
            } catch (Exception ignored) {
            }
        }
        store.saveCircle(a.toString());
    }

    private String[] entry(String id) {
        for (String[] e : CATALOG) if (e[0].equals(id)) return e;
        return null;
    }

    // ---------- 正常模式：宫格 ----------

    private View grid() {
        LinearLayout wrap = new LinearLayout(host);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int shown = 0;
        for (String[] o : order) {
            if (!"1".equals(o[1])) continue;
            String[] e = entry(o[0]);
            if (e == null) continue;
            if (shown % 2 == 0) {
                row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Kit.dp(host, 10);
                wrap.addView(row, lp);
            }
            /* 固定高度是这里的关键：卡片用 wrap_content 时，副标题长一点就会换行，
               同一行两块高度就不一样，网格看着参差不齐。 */
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Kit.dp(host, 112), 1f);
            tp.rightMargin = (shown % 2 == 0) ? Kit.dp(host, 10) : 0;
            row.addView(tile(e), tp);
            shown++;
        }
        if (shown % 2 == 1) {
            // 占位块也撑同样的高度，否则最后一行会比上面矮
            View spacer = new View(host);
            row.addView(spacer, new LinearLayout.LayoutParams(0, Kit.dp(host, 112), 1f));
        }
        return wrap;
    }

    private View tile(String[] e) {
        LinearLayout card = Kit.card(host, 14);
        card.setClickable(true);
        card.setOnClickListener(v -> open(e));
        card.setBackground(Kit.stroke(host, Kit.surface(host), 16, Kit.line(host)));

        TextView icon = Kit.bold(host, e[1].substring(0, 1), 15f, 0xFFFFFFFF);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Kit.round(host, color(e[3]), 11));
        card.addView(icon, new LinearLayout.LayoutParams(Kit.dp(host, 34), Kit.dp(host, 34)));

        TextView name = Kit.bold(host, e[1], 14.5f, Kit.ink(host));
        name.setPadding(0, Kit.dp(host, 10), 0, 0);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        TextView sub = Kit.text(host, e[2], 10.5f, Kit.faint(host));
        sub.setPadding(0, Kit.dp(host, 3), 0, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(sub);
        return card;
    }

    private void open(String[] e) {
        String type = e[4];
        if ("dev".equals(type)) {
            com.hbu.zhangshanghbu.ui.Dialog.alert(host, e[1], "开发中，暂不可使用");
            return;
        }
        if ("grades".equals(type)) {
            Session.ensure(host, new String[]{Portal.P_JW}, () ->
                    host.startActivity(new android.content.Intent(host, GradesActivity.class)));
            return;
        }
        if ("exams".equals(type)) {
            Session.ensure(host, new String[]{Portal.P_JW}, () ->
                    host.startActivity(new android.content.Intent(host, ExamsActivity.class)));
            return;
        }
        if ("room".equals(type)) {
            /* 空教室是原生页：查询是现场 POST 教务接口，不打开网页。
               这里不先弹登录：没登录时页面里会说明「要登录才能查」，
               让用户先看看有什么功能，比一进来就被窗口拦住舒服。 */
            host.startActivity(new android.content.Intent(host, FreeRoomActivity.class));
            return;
        }
        if ("web".equals(type)) {
            String url = e.length > 5 ? e[5] : "";
            if (url.isEmpty()) return;
            /* 校内地址要先有校园网隧道；chaoxing / icourse163 这些公网地址直接开 */
            boolean campus = url.contains("hbu.edu.cn") || url.contains(".hbu.cn");
            if (campus) {
                Session.ensure(host, new String[]{Portal.P_VPN},
                        () -> WebActivity.open(host, url, e[1]));
            } else {
                WebActivity.open(host, url, e[1]);
            }
            return;
        }
        if ("hall".equals(type)) {
            Session.ensure(host, new String[]{Portal.P_VPN, Portal.P_EHALL}, () ->
                    WebActivity.open(host, Portal.URL_EHALL, "办事大厅"));
        }
    }

    // ---------- 管理模式：列表 + 开关 + 拖动 ----------

    private View list() {
        LinearLayout wrap = new LinearLayout(host);
        wrap.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < order.size(); i++) {
            String[] o = order.get(i);
            String[] e = entry(o[0]);
            if (e == null) continue;

            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Kit.dp(host, 12), Kit.dp(host, 10), Kit.dp(host, 8), Kit.dp(host, 10));
            row.setBackground(Kit.stroke(host, Kit.surface(host), 14, Kit.line(host)));

            TextView icon = Kit.bold(host, e[1].substring(0, 1), 13f, 0xFFFFFFFF);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(Kit.round(host, color(e[3]), 9));
            row.addView(icon, new LinearLayout.LayoutParams(Kit.dp(host, 28), Kit.dp(host, 28)));

            LinearLayout info = new LinearLayout(host);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ilp.leftMargin = Kit.dp(host, 10);
            info.addView(Kit.text(host, e[1], 14f, Kit.ink(host)));
            info.addView(Kit.text(host, e[2], 10.5f, Kit.faint(host)));
            row.addView(info, ilp);

            TextView sw = Kit.text(host, "1".equals(o[1]) ? "已显示" : "已隐藏", 11.5f,
                    "1".equals(o[1]) ? Kit.brand(host) : Kit.faint(host));
            sw.setPadding(Kit.dp(host, 8), Kit.dp(host, 5), Kit.dp(host, 8), Kit.dp(host, 5));
            sw.setBackground(Kit.round(host, "1".equals(o[1])
                    ? (Kit.night(host) ? 0xFF12253C : 0xFFE7EFF9)
                    : (Kit.night(host) ? 0xFF1F2836 : 0xFFF2F4F8), 8));
            final int idx = i;
            sw.setOnClickListener(v -> {
                order.get(idx)[1] = "1".equals(order.get(idx)[1]) ? "0" : "1";
                persist();
                reload();
            });
            row.addView(sw);

            TextView handle = Kit.text(host, "≡", 18f, Kit.faint(host));
            handle.setPadding(Kit.dp(host, 10), 0, Kit.dp(host, 4), Kit.dp(host, 4));
            row.addView(handle);

            installDrag(row, handle, i);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Kit.dp(host, 8);
            wrap.addView(row, lp);
        }

        TextView tip = Kit.text(host, "长按 ≡ 拖动排序；关闭只是隐藏，随时能再打开。", 11f, Kit.faint(host));
        tip.setPadding(Kit.dp(host, 4), Kit.dp(host, 6), 0, 0);
        wrap.addView(tip);
        return wrap;
    }

    /** 长按手柄进入拖动：移动时只记录落点，松手再重排并刷新。 */
    private void installDrag(LinearLayout row, TextView handle, int index) {
        final int[] from = {-1};
        final int[] target = {index};
        final float[] startY = {0};

        handle.setOnLongClickListener(v -> {
            from[0] = index;
            startY[0] = v.getRootView().getY();
            row.setElevation(Kit.dp(host, 10));
            row.setAlpha(0.92f);
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            return true;
        });

        handle.setOnTouchListener((v, e) -> {
            if (from[0] < 0) return false;
            if (e.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                View parent = (View) row.getParent();
                float y = e.getRawY();
                int count = (parent instanceof ViewGroup) ? ((ViewGroup) parent).getChildCount() : 0;
                for (int i = 0; i < count; i++) {
                    View child = ((ViewGroup) parent).getChildAt(i);
                    int[] loc = new int[2];
                    child.getLocationOnScreen(loc);
                    if (y >= loc[1] && y <= loc[1] + child.getHeight()) {
                        target[0] = i;
                        break;
                    }
                }
                return true;
            }
            if (e.getAction() == android.view.MotionEvent.ACTION_UP
                    || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                int a = from[0], b = Math.max(0, Math.min(order.size() - 1, target[0]));
                if (a != b && a >= 0 && a < order.size()) {
                    String[] item = order.remove(a);
                    order.add(b, item);
                    persist();
                }
                from[0] = -1;
                reload();
                return true;
            }
            return false;
        });
    }

    private int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0xFF3B7BE8;
        }
    }
}
