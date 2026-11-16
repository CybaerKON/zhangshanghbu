// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.feat;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hbu.zhangshanghbu.campus.Engine;
import com.hbu.zhangshanghbu.campus.Parse;
import com.hbu.zhangshanghbu.campus.Portal;
import com.hbu.zhangshanghbu.campus.Session;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;

import java.util.ArrayList;
import java.util.List;

/** 考试安排：从教务自动读取，缓存可离线看。 */
public class ExamsActivity extends PageActivity {

    private Store store;
    private final List<Parse.Exam> list = new ArrayList<>();
    private LinearLayout box;
    private TextView status;

    @Override
    protected String title() {
        return "考试安排";
    }

    @Override
    protected void build(LinearLayout content) {
        store = new Store(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        status = Kit.text(this, "", 11.5f, Kit.faint(this));
        top.addView(status, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = Kit.text(this, "刷新", 12.5f, Kit.brand(this));
        refresh.setPadding(Kit.dp(this, 12), Kit.dp(this, 6), Kit.dp(this, 6), Kit.dp(this, 6));
        refresh.setOnClickListener(v -> load());
        top.addView(refresh);
        content.addView(top);
        gap(10);

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);

        String cache = store.cache("exams");
        if (!cache.isEmpty()) {
            list.addAll(Parse.exams(cache));
            render();
        }
        load();
    }

    private void load() {
        if (!Session.ok(Portal.P_JW)) {
            status.setText("教务未登录");
            if (list.isEmpty()) hint("点这里登录后自动读取考试安排", true);
            return;
        }
        status.setText("正在读取…");
        /* 考试安排是服务端直出的两张表格（按课程 / 按日期），没有 JSON 接口，
           所以走 urp.js 的 exams 分支去读表格，而不是解析接口。 */
        Engine.fetchUrpByLabel(this, Portal.URP_EXAM, Portal.examLabel(), Portal.MENU_EXAM,
                "exams", (ok, json, msg) -> {
            if (!ok) {
                status.setText(msg + "（显示缓存）");
                if (list.isEmpty()) hint(msg, false);
                return;
            }
            store.cache("exams", json);
            list.clear();
            list.addAll(Parse.exams(json));
            status.setText(list.isEmpty() ? "本学期还没有排考" : "共 " + list.size() + " 场");
            render();
        });
    }

    private void hint(String text, boolean login) {
        box.removeAllViews();
        LinearLayout card = card();
        TextView t = Kit.text(this, text, 13f, Kit.sub(this));
        t.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
        card.addView(t);
        if (login) {
            TextView go = Kit.primary(this, "登录教务", () -> Session.ensure(this,
                    new String[]{Portal.P_JW}, this::load));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Kit.dp(this, 14), 0, Kit.dp(this, 14), Kit.dp(this, 14));
            card.addView(go, lp);
        }
        box.addView(card);
    }

    private void render() {
        box.removeAllViews();
        if (list.isEmpty()) {
            hint("还没有考试安排（可能还没排考）。", false);
            return;
        }
        LinearLayout card = card();
        for (int i = 0; i < list.size(); i++) {
            Parse.Exam e = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(Kit.dp(this, 14), Kit.dp(this, 12), Kit.dp(this, 14), Kit.dp(this, 12));
            row.addView(Kit.text(this, e.name, 14f, Kit.ink(this)));
            StringBuilder meta = new StringBuilder();
            if (!e.time.isEmpty()) meta.append(e.time);
            if (!e.room.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(e.room);
            if (!e.seat.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append("座位 ").append(e.seat);
            if (meta.length() > 0) {
                TextView m = Kit.text(this, meta.toString(), 11.5f, Kit.sub(this));
                m.setPadding(0, Kit.dp(this, 3), 0, 0);
                row.addView(m);
            }
            card.addView(row);
            if (i < list.size() - 1) card.addView(Kit.hr(this));
        }
        box.addView(card);
    }
}
