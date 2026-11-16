// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Cal;
import com.hbu.zhangshanghbu.data.Palette;
import com.hbu.zhangshanghbu.data.Schedule;
import com.hbu.zhangshanghbu.data.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表网格（自绘）。
 *
 * 一屏要画 7×12 甚至 7×16 个格子，用 View 拼会叠出上百个测量/布局；
 * 这里一次 onDraw 画完，切周只 invalidate。
 *
 * 画法：
 *   · 顶行：星期 + 日期（今天高亮）
 *   · 左列：节次 + 该节开始时间（时间来自可配置的 Schedule，不是写死的）
 *   · 课程块：左侧色条 + 浅色底，课名在上、教室贴底
 *   · 冲突：同一格多门课横向平分，谁也不遮谁
 *   · 调课/停课：调走的格子画虚线框并标「调走」
 *
 * 缩放是**整页等比**的：格子宽、行高、字号、内边距全部乘 zoom，
 * 所以放大后和「把纸放大」一样，而不是只把行拉高。
 * 放大后由外层的 ScrollView + HorizontalScrollView 负责两个方向的滚动。
 */
public class Grid extends View {

    public interface OnCourse {
        void onCourse(Table.Course c, Table.Session s, int day, int start);
    }

    public interface OnEmpty {
        void onEmpty(int day, int section);
    }

    private static final String[] WD = {"一", "二", "三", "四", "五", "六", "日"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float d;

    private Table table;
    private Schedule schedule = Schedule.defaults();
    private int week = 1;
    private String[] dates = new String[7];
    private boolean night;

    /** 设计尺寸（dp × density），实际绘制时再乘 zoom */
    private final float headDp = 46f;
    private final float slotDp = 46f;

    private OnCourse onCourse;
    private OnEmpty onEmpty;
    private final List<Hit> hits = new ArrayList<>();
    private float downX, downY;

    /** 1 = 铺满一屏；>1 时整页等比放大 */
    private float zoom = 1f;
    private float viewportW;
    private float viewportH;
    private boolean zooming;
    private android.view.ScaleGestureDetector scaleDet;

    private static final class Hit {
        RectF box = new RectF();
        Table.Course course;
        Table.Session session;
        int day, start;
    }

    public Grid(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.DEFAULT);
        setWillNotDraw(false);
    }

    public void setOnCourse(OnCourse l) {
        onCourse = l;
    }

    public void setOnEmpty(OnEmpty l) {
        onEmpty = l;
    }

    public void setSchedule(Schedule s) {
        if (s != null) schedule = s;
        requestLayout();
        invalidate();
    }

    public void setWeekDates(String[] dates) {
        this.dates = dates == null ? new String[7] : dates;
        invalidate();
    }

    public void setData(Table table, int week) {
        this.table = table;
        this.week = week;
        this.night = Kit.night(getContext());
        invalidate();
    }

    // ---------- 尺寸 ----------

    private float slotW() {
        return slotDp * d * zoom;
    }

    private float headH() {
        return headDp * d * zoom;
    }

    private float cellW() {
        float w = getWidth() - slotW();
        return w > 0 ? w / 7f : 0f;
    }

    /** zoom = 1 时至少给每节 46dp，避免小屏 12 节挤成一团。 */
    private float cellH() {
        int n = Math.max(1, schedule.sectionCount);
        float h = (contentHeight() - headH()) / n;
        return Math.max(h, 46 * d * zoom);
    }

    private float contentWidth() {
        return Math.max(viewportW, 1f) * zoom;
    }

    private float contentHeight() {
        int n = Math.max(1, schedule.sectionCount);
        float fit = Math.max(viewportH, 1f) * zoom;
        float min = headH() + n * 46 * d * zoom;
        return Math.max(fit, min);
    }

    /** 页面布局后告知可视区域，缩放以此为基准。 */
    public void setViewport(int w, int h) {
        boolean ch = false;
        if (w > 0 && Math.abs(w - viewportW) > 1) {
            viewportW = w;
            ch = true;
        }
        if (h > 0 && Math.abs(h - viewportH) > 1) {
            viewportH = h;
            ch = true;
        }
        if (ch) {
            requestLayout();
            invalidate();
        }
    }

    public void setViewportH(int h) {
        setViewport((int) viewportW, h);
    }

    /** 1 = 刚好一屏；上限 3，再大屏上一格就只剩几个字了。 */
    public void setZoom(float z) {
        float nz = Math.max(1f, Math.min(3f, z));
        if (Math.abs(nz - zoom) < 0.01f) return;
        zoom = nz;
        requestLayout();
        invalidate();
    }

    public float zoom() {
        return zoom;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int wWant = Math.round(contentWidth());
        int hWant = Math.round(contentHeight());
        int w = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(wSpec) : wWant;
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(hSpec) : hWant;
        setMeasuredDimension(w, h);
    }

    // ---------- 绘制 ----------

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        hits.clear();

        int bg = Kit.page(getContext());
        int surface = night ? 0xFF161D29 : 0xFFFFFFFF;
        int line = night ? 0x14FFFFFF : 0xFFEDF0F5;
        int ink = Kit.ink(getContext());
        int sub = Kit.faint(getContext());
        int brand = Kit.brand(getContext());
        int today = Cal.todayDow();

        canvas.drawColor(bg);
        float cw = cellW(), ch = cellH();
        float sw = slotW(), hh = headH();

        // 内容底
        r.set(sw, hh, w, h);
        p.setColor(surface);
        canvas.drawRoundRect(r, 14 * d * zoom, 14 * d * zoom, p);

        // 表头日期
        t.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) {
            float cx = sw + cw * (i + 0.5f);
            boolean isToday = (i + 1) == today;
            t.setTextSize(9.5f * d * zoom);
            t.setFakeBoldText(isToday);
            t.setColor(isToday ? brand : sub);
            canvas.drawText("周" + WD[i], cx, hh - 24 * d * zoom, t);
            String md = (dates != null && i < dates.length && dates[i] != null) ? Cal.md(dates[i]) : "";
            t.setTextSize(9f * d * zoom);
            t.setColor(isToday ? brand : (night ? 0xFF6D7787 : 0xFF9AA2B0));
            t.setFakeBoldText(false);
            canvas.drawText(md, cx, hh - 8 * d * zoom, t);
        }

        // 网格线
        p.setColor(line);
        p.setStrokeWidth(Math.max(1f, d * zoom * 0.6f));
        for (int i = 0; i <= 7; i++) {
            float x = sw + cw * i;
            canvas.drawLine(x, hh + 8 * d * zoom, x, h, p);
        }
        for (int s = 0; s <= schedule.sectionCount; s++) {
            float y = hh + ch * s;
            canvas.drawLine(sw, y, w, y, p);
        }

        // 大课间分隔：画粗一点
        p.setColor(night ? 0x33FFFFFF : 0x140C4C8C);
        for (int s = 1; s < schedule.sectionCount; s++) {
            if (!schedule.hasBigAfter(s)) continue;
            float y = hh + ch * s;
            canvas.drawRect(sw, y - 1.5f * d * zoom, w, y + 1.5f * d * zoom, p);
        }

        // 左列节次与时间
        p.setColor(night ? 0x0DFFFFFF : 0xFFF7F8FB);
        r.set(0, hh, sw, h);
        canvas.drawRect(r, p);
        for (int s = 1; s <= schedule.sectionCount; s++) {
            float top = hh + ch * (s - 1);
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(ink);
            t.setTextSize(11f * d * zoom);
            canvas.drawText(String.valueOf(s), sw / 2f, top + ch * 0.42f, t);
            t.setColor(sub);
            t.setTextSize(8.5f * d * zoom);
            canvas.drawText(schedule.startTime(s), sw / 2f, top + ch * 0.68f, t);
        }

        // 课程块
        if (table == null) return;
        for (int day = 1; day <= 7; day++) {
            List<Table.Item> items = table.itemsOn(week, day);
            for (int i = 0; i < items.size(); i++) {
                Table.Item it = items.get(i);
                List<Table.Item> same = new ArrayList<>();
                for (Table.Item o : items) {
                    if (o.start == it.start && o.end == it.end) same.add(o);
                }
                int idx = same.indexOf(it);
                drawBlock(canvas, it, day, cw, ch, idx, same.size());
            }
            // 调走的格子：画个虚线提示
            for (Table.Course c : table.courses) {
                for (Table.Session s : c.sessions) {
                    if (s.day != day || !s.active(week)) continue;
                    Table.Adjust mv = table.moved(week, day, s.start);
                    if (mv == null) continue;
                    drawGhost(canvas, s, day, cw, ch,
                            "调至 周" + WD[Math.max(0, Math.min(6, mv.toDay - 1))]);
                }
            }
        }
    }

    /**
     * 画一门课。
     *
     * 排版是「上对齐课名 + 底对齐教室」：先算出教室要占几行、几号字，
     * 剩下的高度全给课名（1–3 行）。所有基线都由 ascend/descend 精确推出来，
     * 不再用「固定 13dp 行距」这种和字号无关的常数 —— 之前教室文字错位就是它造成的。
     */
    private void drawBlock(Canvas canvas, Table.Item it, int day, float cw, float ch,
                           int idx, int total) {
        float left = slotW() + cw * (day - 1);
        float top = headH() + ch * (it.start - 1);
        float wid = cw / Math.max(1, total);
        float inset = 1.5f * d * zoom;
        float l = left + wid * idx + inset;
        float rr = left + wid * (idx + 1) - inset;
        float b = headH() + ch * it.end - inset;

        int color = it.adjusted ? 0xFF9B6BDA : Palette.main(it.course.color);
        int soft = it.adjusted ? (night ? 0xFF2E1F3D : 0xFFF3EBFB)
                : Palette.soft(it.course.color, night);

        r.set(l, top + inset, rr, b);
        p.setColor(soft);
        canvas.drawRoundRect(r, 8 * d * zoom, 8 * d * zoom, p);

        // 左侧色条
        r.set(l, top + inset, l + 3 * d * zoom, b);
        p.setColor(color);
        canvas.drawRoundRect(r, 1.5f * d * zoom, 1.5f * d * zoom, p);

        float padL = 9 * d * zoom;
        float padR = 5 * d * zoom;
        float textLeft = l + padL;
        float textW = (rr - padR) - textLeft;
        if (textW < 10 * d) return;

        float innerTop = top + inset + 5 * d * zoom;
        float innerBot = b - 5 * d * zoom;
        if (innerBot - innerTop < 12 * d * zoom) return;

        t.setTextAlign(Paint.Align.LEFT);
        t.setFakeBoldText(true);
        t.setColor(night ? 0xFFF0F3F8 : 0xFF1A2029);

        float nameSize = 10.2f * d * zoom;
        float roomSize = 8.2f * d * zoom;
        boolean hasRoom = it.session.room != null && !it.session.room.isEmpty();
        String roomText = hasRoom ? it.session.room : "";

        /* 教室名排法（三种，按「能完整显示」优先）：
           1. 原字号一行放得下 → 就一行；
           2. 放不下 → 先缩字号，缩到 6.4sp 还能一行放下 → 缩着放；
           3. 再放不下 → 断成两行，且**断在中文和房间号之间**
              （「计算机楼401」→「计算机楼」/「401」），
              而不是按宽度硬切，那样会出现「计算机楼 40 / 1」这种把数字劈开的排版。
           两行都放不下才缩字号 + 省略号。 */
        String[] roomRows = null;
        float roomLineH;
        if (hasRoom) {
            t.setTextSize(roomSize);
            float need = t.measureText(roomText);
            if (need > textW) {
                float fit = roomSize * textW / need;
                if (fit >= 6.4f * d * zoom) {
                    roomSize = fit;
                    t.setTextSize(roomSize);
                } else {
                    String[] two = splitRoom(roomText);
                    boolean tall = (innerBot - innerTop) > 52 * d * zoom;
                    if (two != null && tall) {
                        roomRows = two;
                        t.setTextSize(roomSize);
                        float maxW = Math.max(t.measureText(two[0]), t.measureText(two[1]));
                        if (maxW > textW) {
                            roomSize = Math.max(5.8f * d * zoom, roomSize * textW / maxW);
                            t.setTextSize(roomSize);
                        }
                    } else {
                        roomSize = Math.max(5.8f * d * zoom, fit);
                        t.setTextSize(roomSize);
                    }
                }
            }
        }

        roomLineH = roomSize * 1.16f;
        float nameLineH = nameSize * 1.22f;
        float gap = 2 * d * zoom;

        int roomLines = hasRoom ? (roomRows != null ? 2 : 1) : 0;
        float roomH = roomLines * roomLineH;

        float nameAvail = (innerBot - innerTop) - (hasRoom ? roomH + gap : 0);
        int lines = Math.max(1, Math.min(3, (int) Math.floor(nameAvail / nameLineH)));

        // 课名：从顶部基线开始，行距 = 字号 × 1.22
        t.setTextSize(nameSize);
        float nameBase = innerTop - t.ascent();
        drawLines(canvas, Table.cleanName(it.course.name), textLeft, nameBase,
                nameLineH, textW, lines);

        // 教室：最后一行基线贴底，往上推
        if (hasRoom) {
            t.setFakeBoldText(false);
            t.setColor(night ? 0xFF98A2B2 : 0xFF6B7484);
            t.setTextSize(roomSize);
            float lastBase = innerBot - t.descent();
            float firstBase = lastBase - (roomLines - 1) * roomLineH;
            if (roomRows != null) {
                drawLines(canvas, roomRows[0], textLeft, firstBase, roomLineH, textW, 1);
                drawLines(canvas, roomRows[1], textLeft, firstBase + roomLineH, roomLineH, textW, 1);
            } else {
                drawLines(canvas, roomText, textLeft, firstBase, roomLineH, textW, 1);
            }
        }

        Hit hit = new Hit();
        hit.box.set(l, top, rr, b);
        hit.course = it.course;
        hit.session = it.session;
        hit.day = day;
        hit.start = it.start;
        hits.add(hit);
    }

    /**
     * 把教室名断成「楼名」+「房间号」两段。
     *
     * 「计算机楼401」→ {计算机楼, 401}；「七教302」→ {七教, 302}；
     * 「A-201」只有一段就不再拆。这样两行都完整可读，
     * 不会像按宽度硬切那样把 401 变成 40 / 1。
     */
    private static String[] splitRoom(String room) {
        if (room == null) return null;
        String r = room.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.*?[^0-9A-Za-z\\-])([0-9A-Za-z\\-]+)$").matcher(r);
        if (m.matches()) {
            String a = m.group(1).trim();
            String b = m.group(2).trim();
            if (!a.isEmpty() && !b.isEmpty() && a.length() <= 12 && b.length() <= 8) {
                return new String[]{a, b};
            }
        }
        return null;
    }

    private void drawGhost(Canvas canvas, Table.Session s, int day, float cw, float ch, String label) {
        float left = slotW() + cw * (day - 1);
        float top = headH() + ch * (s.start - 1);
        float in = 2 * d * zoom;
        r.set(left + in, top + in, left + cw - in, headH() + ch * s.end - in);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d * zoom);
        p.setColor(night ? 0x33FFFFFF : 0x220C4C8C);
        path.reset();
        path.addRoundRect(r, 8 * d * zoom, 8 * d * zoom, Path.Direction.CW);
        canvas.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        t.setColor(Kit.faint(getContext()));
        t.setTextSize(8.5f * d * zoom);
        t.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, left + cw / 2f, top + ch * 0.5f, t);
    }

    /**
     * 按给定基线画多行，超出就在最后一行加省略号。调用前需设好字号/颜色/对齐。
     *
     * 两个排版细节（都是之前截图里能看到的问题）：
     *   · 不让下一行以「)」「，」这类收尾标点开头 —— 断行时能塞进本行就塞；
     *   · 万一某一行只剩标点，就把它并回上一行，别让它单独占一行。
     *     「大学英语（四）」因此显示成两行，而不是「大学英 / 语（四 / ）」。
     */
    private void drawLines(Canvas canvas, String s, float x, float firstBase,
                           float lineH, float maxW, int maxLines) {
        if (s == null || s.isEmpty()) return;
        java.util.List<String> out = new java.util.ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < maxLines) {
            int n = t.breakText(rest, true, maxW, null);
            if (n <= 0) break;
            String tail = rest.substring(n);
            int extra = 0;
            while (extra < 2 && extra < tail.length()
                    && CLOSERS.indexOf(tail.charAt(extra)) >= 0) {
                if (t.measureText(rest.substring(0, n + extra + 1)) > maxW * 1.18f) break;
                extra++;
            }
            n += extra;
            out.add(rest.substring(0, n));
            rest = rest.substring(n);
        }
        if (out.isEmpty()) return;

        // 只剩标点的行并回上一行
        for (int i = out.size() - 1; i > 0; i--) {
            if (isPunctOnly(out.get(i))) {
                out.set(i - 1, out.get(i - 1) + out.get(i));
                out.remove(i);
            }
        }

        // 还有剩余：最后一行加省略号
        if (!rest.isEmpty()) {
            String last = out.get(out.size() - 1);
            while (last.length() > 1 && t.measureText(last + "…") > maxW) {
                last = last.substring(0, last.length() - 1);
            }
            out.set(out.size() - 1, last + "…");
        }

        for (int i = 0; i < out.size(); i++) {
            canvas.drawText(out.get(i), x, firstBase + lineH * i, t);
        }
    }

    private static final String CLOSERS = ")]}）】」』》〉，。、；：？！%,.";

    private static boolean isPunctOnly(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (CLOSERS.indexOf(s.charAt(i)) < 0) return false;
        }
        return true;
    }

    // ---------- 手势 ----------

    private void ensureDetectors() {
        if (scaleDet == null) {
            scaleDet = new android.view.ScaleGestureDetector(getContext(),
                    new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(android.view.ScaleGestureDetector det) {
                            setZoom(zoom * det.getScaleFactor());
                            return true;
                        }
                    });
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        ensureDetectors();
        /* 双指缩放：整页等比放大，同时挡住外层两个滚动容器，
           否则它们会把第二根手指当成滚动、缩放会一顿一顿 */
        if (e.getPointerCount() > 1 || zooming) {
            zooming = e.getPointerCount() > 1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(zooming);
            scaleDet.onTouchEvent(e);
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (Math.abs(e.getX() - downX) > 24 * d || Math.abs(e.getY() - downY) > 24 * d) {
                return true;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.box.contains(e.getX(), e.getY())) {
                    if (onCourse != null) onCourse.onCourse(h.course, h.session, h.day, h.start);
                    return true;
                }
            }
            if (e.getY() > headH() && e.getX() > slotW()) {
                int day = (int) ((e.getX() - slotW()) / cellW()) + 1;
                int sec = (int) ((e.getY() - headH()) / cellH()) + 1;
                if (day >= 1 && day <= 7 && sec >= 1 && sec <= schedule.sectionCount && onEmpty != null) {
                    onEmpty.onEmpty(day, sec);
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
