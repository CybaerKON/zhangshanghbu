// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.campus.Engine;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 空教室（空闲教室查询）。
 *
 * 以前这里只是占位，因为教务页面本身只有查询表单。实际链路（在浏览器里
 * 点了一次「第七教学楼」抓下来的）是：
 *   1. POST /student/teachingResources/freeClassroom/today
 *        body: position=&lt;校区号&gt;_n&amp;xqm=&lt;校区名&gt;   ← 设定查询范围
 *   2. POST /student/teachingResources/freeClassroom/today/&lt;节次&gt;
 *        body: dayplus=0/1/2                                ← 0 今天 1 明天 2 后天
 *      返回 {"spareroomObjList":[{"acmcBuildingName":"第七教学楼",
 *              "claroom":[{"classroom":"316","classNumberOfSeats":"110"}]}]}
 *   3. 校区清单：POST /freeClassroom/queryCodeCampusList
 *
 * 第 1 步不能省：不先设范围，服务端会沿用上一次在会话里选的某栋楼，
 * 结果只剩那一栋，看起来像「查不到别的楼」。
 *
 * 所以这里是真原生：点「查询」现场发请求，拿到 JSON 直接排版，不打开网页。
 */
public class FreeRoomActivity extends PageActivity {

    private Store store;
    private Schedule schedule;

    /** 已选节次 */
    private boolean[] picked = new boolean[24];
    private int dayplus = 0;
    /** 校区号，空 = 由教务给默认（第一个正常校区） */
    private String campusNo = "";
    private final List<String[]> campuses = new ArrayList<>();   // {no, name}

    private LinearLayout chipRow;
    private LinearLayout resultBox;
    private TextView status;
    private TextView campusValue;
    private LinearLayout dayRow;
    private boolean loading;
    private JSONObject lastData;

    @Override
    protected String title() {
        return "空教室";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);
        schedule = store.schedule();
        picked = new boolean[Math.max(12, schedule.sectionCount) + 1];
        picked[1] = true;
        picked[2] = true;

        LinearLayout dayCard = card();
        dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        dayRow.setGravity(Gravity.CENTER_VERTICAL);
        dayRow.setPadding(Kit.dp(this, 12), Kit.dp(this, 10), Kit.dp(this, 12), Kit.dp(this, 10));
        dayCard.addView(dayRow);

        LinearLayout campusCard = card();
        LinearLayout cRow = new LinearLayout(this);
        cRow.setOrientation(LinearLayout.HORIZONTAL);
        cRow.setGravity(Gravity.CENTER_VERTICAL);
        cRow.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
        cRow.setClickable(true);
        cRow.setOnClickListener(v -> pickCampus());
        cRow.addView(Kit.text(this, "校区", 14.5f, Kit.ink(this)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        campusValue = Kit.text(this, "全部教学楼", 12.5f, Kit.sub(this));
        cRow.addView(campusValue);
        campusCard.addView(cRow);

        content.addView(dayCard);
        gap(10);
        content.addView(campusCard);

        gap(14);
        section("节次");
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        LinearLayout chipCard = card();
        chipCard.addView(hs);
        content.addView(chipCard);

        gap(14);
        content.addView(Kit.primary(this, "查询空闲教室", this::query));

        gap(14);
        status = Kit.text(this, "", 11.5f, Kit.faint(this));
        content.addView(status);
        gap(8);

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultBox);

        renderDays();
        renderChips();

        /* 进来先按默认条件查一次：点开就该看到东西，而不是先面对空白页 */
        if (!Session.ok(Portal.P_JW)) {
            showLogin();
        } else {
            query();
        }
    }

    // ---------- 顶部控件 ----------

    private void renderDays() {
        dayRow.removeAllViews();
        String[] labels = {"今天", "明天", "后天"};
        for (int i = 0; i < labels.length; i++) {
            final int d = i;
            boolean on = dayplus == i;
            TextView t = Kit.chip(this, labels[i], on ? 0xFFFFFFFF : Kit.ink(this),
                    on ? Kit.brand(this) : (Kit.night(this) ? 0xFF1F2836 : 0xFFF1F3F8));
            t.setPadding(Kit.dp(this, 12), Kit.dp(this, 6), Kit.dp(this, 12), Kit.dp(this, 6));
            t.setOnClickListener(v -> {
                if (dayplus == d) return;
                dayplus = d;
                renderDays();
                query();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Kit.dp(this, 8);
            dayRow.addView(t, lp);
        }
    }

    private void renderChips() {
        chipRow.removeAllViews();
        int n = Math.min(20, Math.max(12, schedule.sectionCount));
        for (int i = 1; i <= n; i++) {
            final int sec = i;
            boolean on = picked[i];
            TextView t = Kit.chip(this, String.valueOf(i), on ? 0xFFFFFFFF : Kit.ink(this),
                    on ? Kit.brand(this) : (Kit.night(this) ? 0xFF1F2836 : 0xFFF1F3F8));
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Kit.dp(this, 34), Kit.dp(this, 34));
            lp.rightMargin = Kit.dp(this, 6);
            t.setOnClickListener(v -> {
                picked[sec] = !picked[sec];
                if (selectedSections().isEmpty()) picked[sec] = true;   // 至少留一节
                renderChips();
            });
            chipRow.addView(t, lp);
        }
    }

    private List<Integer> selectedSections() {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < picked.length; i++) if (picked[i]) out.add(i);
        return out;
    }

    private void pickCampus() {
        if (campuses.isEmpty()) {
            Kit.toast(this, "校区清单还没拿到，先点一次查询");
            return;
        }
        String[] labels = new String[campuses.size()];
        int cur = 0;
        for (int i = 0; i < campuses.size(); i++) {
            labels[i] = campuses.get(i)[1];
            if (campuses.get(i)[0].equals(campusNo)) cur = i;
        }
        com.hbu.zhangshanghbu.ui.Picker.show(this, "校区", labels, cur, i -> {
            campusNo = campuses.get(i)[0];
            campusValue.setText(campusName(campusNo));
            query();
        });
    }

    // ---------- 查询 ----------

    private void query() {
        if (loading) return;
        if (!Session.ok(Portal.P_JW)) {
            showLogin();
            return;
        }
        List<Integer> secs = selectedSections();
        if (secs.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(secs.get(i));
        }

        loading = true;
        status.setText("正在查询…");
        try {
            JSONObject extra = new JSONObject();
            extra.put("sections", sb.toString());
            extra.put("dayplus", dayplus);
            extra.put("campus", campusNo == null ? "" : campusNo);
            Engine.fetchUrp(this, Portal.url(Portal.URP_FREE_ROOM), Portal.freeRoomMenu(),
                    "free", extra, (ok, json, msg) -> {
                        loading = false;
                        if (!ok) {
                            status.setText(msg.isEmpty() ? "查询失败" : msg);
                            resultBox.removeAllViews();
                            if (msg.contains("登录")) showLogin();
                            return;
                        }
                        apply(json);
                    });
        } catch (Exception e) {
            loading = false;
            status.setText("查询失败：" + e.getMessage());
        }
    }

    private void apply(String json) {
        try {
            JSONObject env = new JSONObject(json);
            JSONObject d = env.optJSONObject("data");
            if (d == null) {
                status.setText("返回数据不完整");
                return;
            }
            JSONArray cs = d.optJSONArray("campusList");
            if (cs != null && cs.length() > 0) {
                campuses.clear();
                for (int i = 0; i < cs.length(); i++) {
                    JSONObject c = cs.optJSONObject(i);
                    if (c == null) continue;
                    String name = c.optString("name", "");
                    if (name.isEmpty() || name.contains("请选择")) continue;
                    campuses.add(new String[]{c.optString("n", ""), name});
                }
            }
            String cn = d.optString("campus", campusNo);
            if (cn != null && !cn.isEmpty()) campusNo = cn;
            campusValue.setText(campusName(campusNo));
            lastData = d;
            render();
        } catch (Exception e) {
            status.setText("数据解析失败：" + e.getMessage());
        }
    }

    private String campusName(String no) {
        for (String[] c : campuses) {
            if (c[0].equals(no)) return c[1] + " · 全部教学楼";
        }
        return "全部教学楼";
    }

    private void render() {
        resultBox.removeAllViews();
        if (lastData == null) return;
        JSONArray list = lastData.optJSONArray("list");
        if (list == null || list.length() == 0) {
            status.setText("这个时间段没有空闲教室");
            return;
        }

        int rooms = 0;
        int buildings = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject b = list.optJSONObject(i);
            if (b == null) continue;
            String building = b.optString("building", "");
            JSONArray rs = b.optJSONArray("rooms");
            if (rs == null || rs.length() == 0) continue;

            LinearLayout card = card();
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Kit.dp(this, 14), Kit.dp(this, 10), Kit.dp(this, 14), Kit.dp(this, 6));
            head.addView(Kit.bold(this, building, 14f, Kit.ink(this)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Kit.text(this, rs.length() + " 间", 11f, Kit.faint(this)));
            card.addView(head);

            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(Kit.dp(this, 10), 0, Kit.dp(this, 10), Kit.dp(this, 10));
            LinearLayout row = null;
            int col = 0;
            for (int k = 0; k < rs.length(); k++) {
                JSONObject r = rs.optJSONObject(k);
                if (r == null) continue;
                String room = r.optString("room", "");
                String seats = r.optString("seats", "");
                if (room.isEmpty()) continue;
                if (col == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(row);
                }

                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(Kit.round(this, Kit.night(this) ? 0xFF1C2430 : 0xFFF5F7FB, 10));
                cell.setPadding(Kit.dp(this, 3), Kit.dp(this, 8), Kit.dp(this, 3), Kit.dp(this, 7));

                TextView name = Kit.text(this, room, 12.5f, Kit.ink(this));
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(name);

                TextView s2 = Kit.text(this, seats.isEmpty() ? " " : seats + "座", 9.5f, Kit.faint(this));
                s2.setGravity(Gravity.CENTER);
                cell.addView(s2);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(Kit.dp(this, 3), Kit.dp(this, 3), Kit.dp(this, 3), Kit.dp(this, 3));
                row.addView(cell, lp);
                rooms++;
                col = (col + 1) % 4;
            }
            // 补满最后一行的占位，保证每列等宽
            if (row != null && col != 0) {
                for (int k = col; k < 4; k++) {
                    View sp = new View(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                    lp.setMargins(Kit.dp(this, 3), 0, Kit.dp(this, 3), 0);
                    row.addView(sp, lp);
                }
            }
            resultBox.addView(card);
            resultBox.addView(Kit.gap(this, 10));
            buildings++;
        }

        String[] days = {"今天", "明天", "后天"};
        String dname = dayplus >= 0 && dayplus < 3 ? days[dayplus] : "今天";
        if (rooms == 0) {
            status.setText(dname + "这几节没有空闲教室");
        } else {
            status.setText(dname + " · " + buildings + " 栋楼 · " + rooms + " 间空闲教室");
        }
    }

    private void showLogin() {
        resultBox.removeAllViews();
        status.setText("教务未登录");
        LinearLayout card = card();
        TextView t = Kit.text(this, "空教室要实时查教务，先登录一次校园网 / 教务。", 13f, Kit.sub(this));
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 8));
        card.addView(t);
        TextView go = Kit.primary(this, "登录", () -> Session.ensure(this,
                new String[]{Portal.P_JW}, this::query));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Kit.dp(this, 14), 0, Kit.dp(this, 14), Kit.dp(this, 14));
        card.addView(go, lp);
        resultBox.addView(card);
    }
}
