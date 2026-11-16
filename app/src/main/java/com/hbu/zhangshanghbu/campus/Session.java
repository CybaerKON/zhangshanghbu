// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import android.app.Activity;
import android.content.Context;
import android.webkit.CookieManager;

import com.hbu.zhangshanghbu.data.Store;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局登录态。一处登录、处处可用；重启后先检查，掉了就说清哪个掉了。
 *
 * 状态机很简单：每个平台三种状态 —— 没确认过 / 在线 / 已失效。
 *   · 「在线」只在真正探测成功后才算（不是「上次登录过」就算）；
 *   · 探测失败就把该平台标成失效，并记住原因，首页/圈子上会挂一条提示；
 *   · 用户点任何依赖该平台的功能时，{@link #ensure} 会弹登录窗口，而不是让页面白屏。
 *
 * Cookie 由系统 WebView 的 CookieManager 持有（它自己会落盘），
 * 我们额外存一份 JSON 快照，用于 WebView 数据被清掉后恢复。
 */
public final class Session {

    public static final int UNKNOWN = 0, OK = 1, EXPIRED = 2;
    public static final long TRUST_MS = 12 * 3600_000L;

    private static final Map<String, Integer> state = new HashMap<>();
    private static final Map<String, String> reason = new HashMap<>();
    private static final Map<String, Long> checkedAt = new HashMap<>();

    private Session() {}

    public static void bootstrap(Context c) {
        Store s = new Store(c);
        long at = s.loginAt();
        for (String p : Portal.PLATFORMS) {
            long t = s.platformAt(p);
            if (t <= 0) {
                state.put(p, UNKNOWN);
            } else if (System.currentTimeMillis() - t < TRUST_MS) {
                state.put(p, OK);
            } else {
                state.put(p, UNKNOWN);
            }
        }
        if (state.get(Portal.P_JW) == OK && at <= 0) state.put(Portal.P_JW, UNKNOWN);
    }

    public static int state(String platform) {
        Integer v = state.get(platform);
        return v == null ? UNKNOWN : v;
    }

    public static String reason(String platform) {
        return reason.getOrDefault(platform, "");
    }

    public static long checkedAt(String platform) {
        return checkedAt.getOrDefault(platform, 0L);
    }

    public static boolean ok(String platform) {
        return state(platform) == OK;
    }

    public static void markOk(Context c, String platform) {
        state.put(platform, OK);
        reason.remove(platform);
        checkedAt.put(platform, System.currentTimeMillis());
        new Store(c).setPlatformAt(platform, System.currentTimeMillis());
    }

    public static void markExpired(String platform, String why) {
        state.put(platform, EXPIRED);
        reason.put(platform, why == null ? "" : why);
        checkedAt.put(platform, System.currentTimeMillis());
    }

    /** 有没有平台掉线（给首页提示条用）。 */
    public static List<String> expired() {
        List<String> out = new ArrayList<>();
        for (String p : Portal.PLATFORMS) if (state(p) == EXPIRED) out.add(p);
        return out;
    }

    public static String expiredText() {
        List<String> list = expired();
        if (list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : list) {
            if (sb.length() > 0) sb.append("、");
            sb.append(Portal.name(p));
        }
        return sb + " 登录已失效";
    }

    // ---------- Cookie ----------

    public static void restore(Context c) {
        String json = new Store(c).cookie();
        if (json == null || json.isEmpty() || "{}".equals(json)) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            JSONObject o = new JSONObject(json);
            for (String host : Portal.hosts()) {
                String ck = o.optString(host, "");
                if (ck.isEmpty()) continue;
                for (String scheme : new String[]{"https://", "http://"}) {
                    cm.setCookie(scheme + host + "/", ck);
                }
            }
            cm.flush();
        } catch (Exception ignored) {
        }
    }

    public static void snapshot(Context c) {
        try {
            CookieManager cm = CookieManager.getInstance();
            JSONObject o = new JSONObject();
            for (String host : Portal.hosts()) {
                String ck = cm.getCookie("https://" + host + "/");
                if (ck != null && !ck.isEmpty()) o.put(host, ck);
            }
            if (o.length() > 0) new Store(c).setCookie(o.toString());
        } catch (Exception ignored) {
        }
    }

    public static void logout(Context c) {
        try {
            CookieManager cm = CookieManager.getInstance();
            for (String host : Portal.hosts()) {
                String ck = cm.getCookie("https://" + host + "/");
                if (ck == null) continue;
                for (String part : ck.split(";")) {
                    String name = part.split("=")[0].trim();
                    if (name.isEmpty()) continue;
                    cm.setCookie("https://" + host + "/", name + "=; Max-Age=0; path=/");
                }
            }
            cm.flush();
        } catch (Exception ignored) {
        }
        new Store(c).clearLogin();
        Portal.clearBase();
        state.clear();
        reason.clear();
        checkedAt.clear();
    }

    // ---------- 检查与按需登录 ----------

    public interface Done {
        void done(boolean allOk);
    }

    /**
     * 逐个平台探一遍。单个平台失败不影响其它平台，结果写进状态并回调。
     * 只做「页面还认不认我」这一件事，不拉业务数据。
     */
    public static void check(Activity host, Done cb) {
        /* 先看校园网：隧道断了，后面两个平台一定也用不了，
           这时直接标失效并说清原因，不用再白跑两次超时 */
        Engine.probe(host, Portal.home(Portal.P_VPN), (loggedIn, url, why) -> {
            if (!loggedIn) {
                markExpired(Portal.P_VPN, why.isEmpty() ? "需要重新登录" : why);
                markExpired(Portal.P_JW, "校园网未登录");
                markExpired(Portal.P_EHALL, "校园网未登录");
                snapshot(host);
                if (cb != null) cb.done(false);
                return;
            }
            markOk(host, Portal.P_VPN);
            checkNext(host, new String[]{Portal.P_JW, Portal.P_EHALL}, 0, cb);
        });
    }

    private static void checkNext(Activity host, String[] list, int i, Done cb) {
        if (i >= list.length) {
            Session.snapshot(host);
            if (cb != null) cb.done(expired().isEmpty());
            return;
        }
        String platform = list[i];
        Engine.probe(host, Portal.home(platform), new Engine.ProbeCb() {
            @Override
            public void onProbe(boolean loggedIn, String url, String why) {
                if (loggedIn) markOk(host, platform);
                else markExpired(platform, why.isEmpty() ? "需要重新登录" : why);
                checkNext(host, list, i + 1, cb);
            }
        });
    }

    /**
     * 功能入口统一走这里：所有平台都在线就直接执行，否则弹登录窗口，登录成功后继续。
     * 课表不调用它 —— 课表读本地，没登录也能看。
     */
    public static void ensure(Activity host, String[] need, Runnable then) {
        boolean all = true;
        for (String p : need) if (!ok(p)) all = false;
        if (all) {
            then.run();
            return;
        }
        /* 任何业务平台都在 WebVPN 后面：登录时永远先过校园网，再登需要的平台。
           这样「一处登录，全部可用」才成立，而不是各功能各弹一次。
           注意：登录窗口只把**真正验证过**的平台标为在线，这里不再无条件 markOk ——
           否则单点没通过也会被当成已登录，下一次取数又弹登录页。 */
        java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
        order.add(Portal.P_VPN);
        for (String p : need) order.add(p);
        LoginDialog.show(host, order.toArray(new String[0]), then);
    }
}
