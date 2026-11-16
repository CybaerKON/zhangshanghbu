// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.hbu.zhangshanghbu.R;
import com.hbu.zhangshanghbu.data.Store;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 无头取数引擎：打开一个页面 → 判断登没登上 → 注入 page.js 解析 → 把 JSON 交回原生。
 *
 * 和上一版最大的区别：**跳转由原生做，不再让脚本自己找路**。
 *   · 上一版 agent.js 用 sessionStorage 记「试到第几个候选地址」，可每次跳转都换一个
 *     WebView，sessionStorage 跟着丢，于是它在候选地址之间来回空跳，最后只报「找不到入口」；
 *   · 现在每个候选地址都是原生 sequential 地 loadUrl，失败就明确换下一个，
 *     全部失败才去首页按关键词找链接，再试一次 —— 每一步都知道自己停在哪。
 *
 * 另外两件事也在这里做：
 *   · 记录真实的 URP 根地址（校外是 https://v.hbu.cn/http/&lt;enc&gt;/），后续接口直接拼它，
 *     不再每次都从 zhjw 走一遍 302；
 *   · 页面里还有密码框 / 跳到 loginAction 时直接报「需要重新登录」，让上层弹窗口。
 */
public final class Engine {

    public interface DataCb {
        void onData(boolean ok, String json, String msg);
    }

    public interface ProbeCb {
        void onProbe(boolean loggedIn, String url, String why);
    }

    private Engine() {}

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final long TIMEOUT_FIRST = 12_000L;
    private static final long TIMEOUT_NEXT = 10_000L;
    private static final int MAX_DISCOVER = 1;

    // ---------- 对外 ----------

    /** 打开一个地址并解析。 */
    public static void fetch(Activity host, String url, String kind, DataCb cb) {
        fetch(host, url, kind, TIMEOUT_FIRST, cb);
    }

    /**
     * 依次尝试候选地址，返回第一个解析成功的结果。
     * 全部失败时再做一次「首页找链接」，还不行就把每一步的原因拼起来交回去。
     */
    public static void fetchFirst(Activity host, String[] urls, String kind, DataCb cb) {
        if (urls == null || urls.length == 0) {
            cb.onData(false, "", "没有可用的地址");
            return;
        }
        tryNext(host, urls, kind, 0, new StringBuilder(), cb);
    }

    private static void tryNext(Activity host, String[] urls, String kind, int i,
                                StringBuilder log, DataCb cb) {
        if (i >= urls.length) {
            discover(host, kind, log, cb);
            return;
        }
        String url = urls[i];
        fetch(host, url, kind, i == 0 ? TIMEOUT_FIRST : TIMEOUT_NEXT, (ok, json, msg) -> {
            if (ok) {
                cb.onData(true, json, "");
                return;
            }
            if (log.length() > 0) log.append("；");
            log.append(shortUrl(url)).append(" → ").append(msg.isEmpty() ? "没有数据" : msg);
            tryNext(host, urls, kind, i + 1, log, cb);
        });
    }

    /** 所有已知地址都失败：打开教务首页，按关键词找入口，再试一次。 */
    private static void discover(Activity host, String kind, StringBuilder log, DataCb cb) {
        String keyword = Portal.keywordOf(kind);
        if (keyword == null) {
            cb.onData(false, "", log.toString());
            return;
        }
        fetch(host, Portal.url(Portal.URP_INDEX), "links", TIMEOUT_NEXT, (ok, json, msg) -> {
            String next = "";
            try {
                JSONObject o = new JSONObject(json);
                org.json.JSONArray a = o.optJSONArray("tabs");
                next = Portal.pickLink(a, keyword);
            } catch (Exception ignored) {
            }
            final String nextHref = next;
            if (nextHref.isEmpty()) {
                if (log.length() > 0) log.append("；");
                log.append("首页也没找到「").append(keyword).append("」入口");
                cb.onData(false, "", log.toString());
                return;
            }
            fetch(host, nextHref, kind, TIMEOUT_NEXT, (ok2, json2, msg2) -> {
                if (ok2) cb.onData(true, json2, "");
                else {
                    if (log.length() > 0) log.append("；");
                    log.append("首页入口 ").append(shortUrl(nextHref)).append(" → ").append(msg2);
                    cb.onData(false, "", log.toString());
                }
            });
        });
    }

    /** 取某个地址的链接表（给「按名称找入口」用）。 */
    public static void links(Activity host, String url, DataCb cb) {
        fetch(host, url, "links", cb);
    }

    private static void fetch(Activity host, String url, String kind, long timeout, DataCb cb) {
        fetch(host, url, kind, timeout, R.raw.page, "{}", cb);
    }

    /**
     * 读学生本人信息：学号、姓名、院系、专业、班级，外加头像（以 base64 回来）。
     *
     * 走学籍信息页而不是首页，因为首页只有名字；学籍页四样都有，头像也在这页上。
     * 头像要下载，所以超时给得比别的功能长。
     */
    public static void fetchProfile(Activity host, DataCb cb) {
        ensureMenu(host, () -> {
            String menu = Portal.menuId(Portal.profileLabel(), Portal.MENU_PROFILE);
            try {
                CookieManager.getInstance().setCookie("https://" + Portal.HOST_VPN + "/",
                        "selectionBar=" + menu + "; path=/");
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {
            }
            fetch(host, Portal.url(Portal.URP_PROFILE), "profile", 25_000L,
                    R.raw.urp, "{}", cb);
        });
    }

    private static void fetch(Activity host, String url, String kind, long timeout, int rawId,
                              DataCb cb) {
        fetch(host, url, kind, timeout, rawId, "{}", cb);
    }

    /**
     * 读一次教务菜单表（菜单名 -> 当前会话的 li id）。
     * 换账号或会话重建后 id 都会变，只有这份现读的表可信。
     */
    public static void fetchMenu(Activity host, DataCb cb) {
        fetch(host, Portal.url(Portal.URP_INDEX), "menu", TIMEOUT_FIRST, R.raw.menu, cb);
    }

    /** 菜单表就绪后再干活；已有缓存就不再打一次首页。 */
    public static void ensureMenu(Activity host, Runnable then) {
        if (Portal.hasMenu()) {
            then.run();
            return;
        }
        fetchMenu(host, (ok, json, msg) -> {
            if (ok) Portal.fillMenu(json);
            then.run();
        });
    }

    /**
     * 按「菜单名」打开功能页取数。
     *
     * 这是保账号无关的关键：地址固定，菜单 id 每次现读现用，
     * 换个人登录、换个学期都不会因为 id 对不上而 500。
     */
    public static void fetchUrpByLabel(Activity host, String path, String label, String fallback,
                                       String kind, DataCb cb) {
        ensureMenu(host, () -> fetchUrp(host, Portal.url(path),
                Portal.menuId(label, fallback), kind, cb));
    }

    /**
     * 打开一个 URP 菜单页，并把页面自己用的数据接口 JSON 取回来。
     *
     * 两个必须做的事：
     *   1. 先种 cookie {@code selectionBar=<菜单 li 的 id>}，否则直接拼地址会 500「页面丢失」，
     *      这个 cookie 是强智用来标识「当前选的是哪个菜单」的；
     *   2. 注入 urp.js 而不是 page.js —— 前者读页面里的接口令牌并同源请求，
     *      比解析服务端渲染的表格稳得多（课程名、周次、连堂都以字段形式给出）。
     */
    public static void fetchUrp(Activity host, String pageUrl, String menuId, String kind, DataCb cb) {
        fetchUrp(host, pageUrl, menuId, kind, null, cb);
    }

    /**
     * 同上，额外带一份参数给页面脚本（空闲教室要传节次 / 第几天）。
     *
     * @param extra 会被序列化成 JSON 交给 urp.js 的 EXTRA，null 表示不需要
     */
    public static void fetchUrp(Activity host, String pageUrl, String menuId, String kind,
                                JSONObject extra, DataCb cb) {
        if (menuId != null && !menuId.isEmpty()) {
            try {
                CookieManager.getInstance().setCookie("https://" + Portal.HOST_VPN + "/",
                        "selectionBar=" + menuId + "; path=/");
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {
            }
        }
        fetch(host, pageUrl, kind, TIMEOUT_FIRST, R.raw.urp,
                extra == null ? "{}" : extra.toString(), cb);
    }

    private static void fetch(Activity host, String url, String kind, long timeout, int rawId,
                              String extra, DataCb cb) {
        if (host == null || host.isFinishing()) {
            cb.onData(false, "", "界面已关闭");
            return;
        }
        final WebView web = create(host);
        final boolean[] done = new boolean[]{false};
        final Runnable[] timeoutTask = new Runnable[1];

        timeoutTask[0] = () -> {
            if (done[0]) return;
            done[0] = true;
            destroy(web);
            cb.onData(false, "", "打开超时");
        };
        UI.postDelayed(timeoutTask[0], timeout);

        web.addJavascriptInterface(new Bridge(json -> UI.post(() -> {
            if (done[0]) return;
            done[0] = true;
            UI.removeCallbacks(timeoutTask[0]);
            boolean ok = false;
            String msg = "";
            try {
                JSONObject o = new JSONObject(json);
                Portal.noteUrl(o.optString("url", url));
                if (o.optBoolean("needLogin", false) || "login".equals(o.optString("need", ""))) {
                    done[0] = true;
                    destroy(web);
                    Portal.markLoginPage();
                    /* 让上层知道是「登录掉了」而不是「没找到页面」，
                       我的页和功能页据此提示并弹登录窗口 */
                    Session.markExpired(Portal.P_JW, "登录已过期");
                    cb.onData(false, "", "需要重新登录");
                    return;
                }
                ok = o.optBoolean("ok", false);
                msg = o.optString("msg", "");
            } catch (Exception e) {
                msg = "返回数据解析失败";
            }
            destroy(web);
            cb.onData(ok, json, msg);
        })), "HBU");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                if (done[0]) return;
                Portal.noteUrl(u);
                UI.postDelayed(() -> {
                    if (done[0]) return;
                    try {
                        String js = raw(host, rawId)
                                .replace("\"__KIND__\"", "\"" + kind + "\"")
                                .replace("__EXTRA__", extra == null || extra.isEmpty() ? "{}" : extra);
                        v.evaluateJavascript(js, null);
                    } catch (Exception e) {
                        if (!done[0]) {
                            done[0] = true;
                            destroy(web);
                            cb.onData(false, "", "注入解析脚本失败");
                        }
                    }
                }, 450);
            }
        });
        web.loadUrl(url);
    }

    /** 探测登录态：加载页面，看还有没有密码框。 */
    public static void probe(Activity host, String url, ProbeCb cb) {
        if (host == null || host.isFinishing()) {
            cb.onProbe(false, url, "界面已关闭");
            return;
        }
        final WebView web = create(host);
        final boolean[] done = new boolean[]{false};
        final Runnable[] timeout = new Runnable[1];
        timeout[0] = () -> {
            if (done[0]) return;
            done[0] = true;
            destroy(web);
            cb.onProbe(false, url, "连接超时");
        };
        UI.postDelayed(timeout[0], 10_000);

        web.addJavascriptInterface(new ProbeBridge(json -> UI.post(() -> {
            if (done[0]) return;
            done[0] = true;
            UI.removeCallbacks(timeout[0]);
            boolean logged = false;
            String real = url, why = "";
            try {
                JSONObject o = new JSONObject(json);
                real = o.optString("url", url);
                Portal.noteUrl(real);
                int pwd = o.optInt("pwd", 0);
                logged = pwd == 0 && !Portal.loginPage(real) && !Portal.authBusy(real);
                why = logged ? "" : "页面上还有登录表单";
            } catch (Exception e) {
                why = "页面解析失败";
            }
            destroy(web);
            cb.onProbe(logged, real, why);
        })), "HBU_P");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                if (done[0]) return;
                Portal.noteUrl(u);
                /* CAS 纯跳转页（ticket / cas_login / loginAgain）上先别注入：
                   这时候页面上既没有密码框也没有菜单，注进去只会得出「没登录」的错误结论。
                   登录页本身还是要探（多等一会儿，给单点跳转留时间）。 */
                if (Portal.authBusy(u) && !Portal.loginPage(u)) return;
                long delay = Portal.authBusy(u) ? 1200L : 400L;
                UI.postDelayed(() -> {
                    if (!done[0]) v.evaluateJavascript(raw(host, R.raw.probe), null);
                }, delay);
            }
        });
        web.loadUrl(url);
    }

    // ---------- 内部 ----------

    private static WebView create(Activity host) {
        WebView w = new WebView(host);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setBlockNetworkImage(true);
        s.setLoadsImagesAutomatically(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(new Store(host).desktopUa() ? Portal.desktopUa() : Portal.mobileUa());
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);

        FrameLayout holder = new FrameLayout(host);
        holder.addView(w, new FrameLayout.LayoutParams(1, 1));
        holder.setAlpha(0f);
        ViewGroup decor = (ViewGroup) host.getWindow().getDecorView();
        decor.addView(holder, new ViewGroup.LayoutParams(1, 1));
        w.setTag(holder);
        return w;
    }

    private static void destroy(WebView w) {
        try {
            Object tag = w.getTag();
            w.stopLoading();
            w.removeJavascriptInterface("HBU");
            w.removeJavascriptInterface("HBU_P");
            w.loadUrl("about:blank");
            w.destroy();
            if (tag instanceof View) {
                ViewGroup p = (ViewGroup) ((View) tag).getParent();
                if (p != null) p.removeView((View) tag);
            }
        } catch (Exception ignored) {
        }
    }

    static String raw(Activity host, int id) {
        try (InputStream in = host.getResources().openRawResource(id)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            return bos.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String shortUrl(String url) {
        if (url == null) return "";
        String s = url.replaceFirst("^https?://", "");
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        return s.length() > 42 ? s.substring(s.length() - 42) : s;
    }

    public static final class Bridge {
        private final java.util.function.Consumer<String> cb;

        Bridge(java.util.function.Consumer<String> cb) {
            this.cb = cb;
        }

        @JavascriptInterface
        public void onData(String json) {
            cb.accept(json);
        }
    }

    public static final class ProbeBridge {
        private final java.util.function.Consumer<String> cb;

        ProbeBridge(java.util.function.Consumer<String> cb) {
            this.cb = cb;
        }

        @JavascriptInterface
        public void onProbe(String json) {
            cb.accept(json);
        }
    }
}
