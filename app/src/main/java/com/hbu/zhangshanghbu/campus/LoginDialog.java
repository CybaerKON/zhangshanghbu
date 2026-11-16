// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hbu.zhangshanghbu.R;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;

/**
 * 登录窗口：**只让用户登一次统一认证**，后面的平台能单点就单点。
 *
 * 之前的做法是「校园网 → 教务 → 办事大厅」三步走，每步都 loadUrl 平台首页。
 * 实际踩到的坑：
 *   · CAS 是 SPA，登录成功后先在站内跳一下，再 302 到 v.hbu.cn/login?cas_login=true&ticket=…；
 *     旧代码一看到「v.hbu.cn 下、URL 里没有 /login」就判定校园网已登录，立刻 loadUrl 下一步，
 *     把还在半路的票据跳转打断 —— WebVPN 收不到票，下一屏又是登录页，用户看到的就是
 *     「登上了又跳回登录界面」；
 *   · CAS 也是经 WebVPN 代理的（/https/&lt;cas 的编码&gt;/lyuapServer/login），
 *     旧代码把这个前缀记成教务根地址，之后请求全打到 CAS 上，同样被弹回登录。
 *
 * 现在的状态机：
 *   ENTRY  打开校园网 / 统一认证，只在**拿到 WebVPN 会话票**后才算过（不看 URL 猜）；
 *   CHECK  静默打开教务，看是不是已经单点进去了 —— 是就跳过，不打扰；
 *   LOGIN  只有教务确实要自己登一次时，才把登录页给用户；
 *   DONE   关窗。
 * 认证跳转中的页面（ticket / cas_login / loginAgain / CAS 页）一律不决策，等它跳完。
 */
public final class LoginDialog {

    private static final int PH_ENTRY = 0;
    private static final int PH_CHECK = 1;
    private static final int PH_LOGIN = 2;
    private static final int PH_DONE = 3;

    private final Activity host;
    private final Store store;
    /** 需要用户亲自登录的平台（不含校园网；校园网就是这个窗口本身） */
    private final String[] need;
    private final Runnable onSuccess;

    private final Dialog dialog;
    private WebView web;
    private ProgressBar bar;
    private TextView phaseText;
    private TextView status;
    private TextView hint;
    private TextView advanceBtn;

    private int phase = PH_ENTRY;
    private int needIdx = 0;
    private int casTries = 0;
    private int waits = 0;
    /** 上一屏的阶段：换阶段就重置计时，避免「填密码花了三分钟」把超时算进去 */
    private int lastStage = -1;
    /** 「重新载入」是用户主动重来：不再自动点统一认证，留出账号登录的选择 */
    private boolean autoCas = true;
    private long phaseAt = System.currentTimeMillis();
    private boolean closed = false;
    private String lastUrl = "";
    private String lastTitle = "";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LoginDialog(Activity host, String[] platforms, Runnable onSuccess) {
        this.host = host;
        this.store = new Store(host);
        this.need = onlyPlatforms(platforms);
        this.onSuccess = onSuccess;
        this.dialog = new Dialog(host, android.R.style.Theme_Translucent_NoTitleBar);
        build();
    }

    /** 去掉校园网（它是入口，不是一步），也去掉重复项。 */
    private static String[] onlyPlatforms(String[] platforms) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (platforms != null) {
            for (String p : platforms) {
                if (p == null || p.isEmpty() || Portal.P_VPN.equals(p)) continue;
                set.add(p);
            }
        }
        if (set.isEmpty()) set.add(Portal.P_JW);
        return set.toArray(new String[0]);
    }

    public static LoginDialog show(Activity host, String[] platforms, Runnable onSuccess) {
        LoginDialog d = new LoginDialog(host, platforms, onSuccess);
        d.dialog.show();
        d.loadEntry();
        return d;
    }

    // ---------- 界面 ----------

    private void build() {
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            win.setDimAmount(0.5f);
            win.setStatusBarColor(0x66000000);
            win.setNavigationBarColor(0x66000000);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        FrameLayout root = new FrameLayout(host);
        root.setPadding(dp(10), dp(26), dp(10), dp(22));

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(store.darkNow() ? 0xFF161D29 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), store.darkNow() ? 0xFF2A3444 : 0xFFE4E8F0);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(10));
        root.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(store.darkNow() ? 0xFF161D29 : 0xFFFFFFFF);
        head.setPadding(dp(16), dp(12), dp(10), dp(6));

        View dot = new View(host);
        dot.setBackground(Kit.round(host, Kit.brand(host), 8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        head.addView(dot, dlp);

        head.addView(Kit.bold(host, "登录", 15.5f, Kit.ink(host)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView ua = Kit.text(host, store.desktopUa() ? "电脑版" : "手机版", 12.5f, Kit.brand(host));
        ua.setPadding(dp(8), dp(6), dp(8), dp(6));
        ua.setOnClickListener(v -> {
            store.setDesktopUa(!store.desktopUa());
            ua.setText(store.desktopUa() ? "电脑版" : "手机版");
            web.getSettings().setUserAgentString(store.desktopUa()
                    ? Portal.desktopUa() : Portal.mobileUa());
            web.reload();
        });
        head.addView(ua);

        TextView close = Kit.text(host, "✕", 16f, Kit.sub(host));
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(10), dp(4), dp(10), dp(4));
        close.setOnClickListener(v -> close());
        head.addView(close);
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        phaseText = Kit.text(host, "", 11.5f, Kit.faint(host));
        phaseText.setPadding(dp(16), 0, dp(16), dp(4));
        card.addView(phaseText);

        bar = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        card.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        FrameLayout box = new FrameLayout(host);
        box.setBackgroundColor(0xFFF7F8FA);
        web = new WebView(host);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(store.desktopUa() ? Portal.desktopUa() : Portal.mobileUa());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(0xFFFFFFFF);
        web.addJavascriptInterface(new Bridge(), "HBU_P");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handle(r.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handle(url);
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                lastUrl = url;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                lastUrl = url;
                bar.setProgress(100);
                /* 每落一页就把 cookie 落盘：CAS 回调后 WebVPN 的票只在这一跳里下发，
                   不 flush 的话进程被杀就白登了 */
                CookieManager.getInstance().flush();
                Session.snapshot(host);
                if (closed) return;
                ui.postDelayed(LoginDialog.this::probe, 380);
            }
        });
        box.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(host);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(store.darkNow() ? 0xFF161D29 : 0xFFFFFFFF);
        foot.setPadding(dp(16), dp(10), dp(16), dp(10));

        status = Kit.text(host, "正在打开统一认证…", 12.5f, Kit.ink(host));
        foot.addView(status);
        hint = Kit.text(host, "登录一次即可，教务会自动进入，不需要再登一遍", 11f, Kit.faint(host));
        hint.setPadding(0, dp(4), 0, 0);
        foot.addView(hint);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);
        actions.addView(action("重新载入", () -> {
            /* 重来一次时不再替用户点统一认证：万一 CAS 那边有问题，
               还可以改走「账号登录」 */
            autoCas = false;
            web.loadUrl(Portal.URL_VPN);
        }));

        advanceBtn = Kit.text(host, "这步已完成", 12.5f, 0xFFFFFFFF);
        advanceBtn.setGravity(Gravity.CENTER);
        advanceBtn.setTypeface(Typeface.DEFAULT_BOLD);
        advanceBtn.setBackground(Kit.round(host, Kit.brand(host), 10));
        advanceBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        advanceBtn.setOnClickListener(v -> manualAdvance());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(12);
        actions.addView(advanceBtn, alp);

        TextView later = action("稍后再说", this::close);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dp(10);
        actions.addView(later, llp);
        foot.addView(actions);

        card.addView(foot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, key, e) -> {
            if (key == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                if (web.canGoBack()) web.goBack();
                else close();
                return true;
            }
            return false;
        });
    }

    private TextView action(String text, Runnable r) {
        TextView t = Kit.text(host, text, 12.5f, Kit.brand(host));
        t.setPadding(0, dp(8), dp(8), dp(8));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private int dp(float v) {
        return Kit.dp(host, v);
    }

    // ---------- 流程 ----------

    private void loadEntry() {
        phase = PH_ENTRY;
        phaseAt = System.currentTimeMillis();
        waits = 0;
        casTries = 0;
        lastStage = -1;
        autoCas = true;
        Portal.clearBase();
        setPhaseText("统一认证");
        status.setText("正在打开统一认证…");
        hint.setText("登录一次即可，教务会自动进入，不需要再登一遍");
        advanceBtn.setText("校园网已登录");
        web.loadUrl(Portal.URL_VPN);
    }

    /** 校园网会话已经建立（拿到票），进入静默检查。 */
    private void entryOk() {
        if (phase != PH_ENTRY) return;
        Session.markOk(host, Portal.P_VPN);
        Session.snapshot(host);
        setPhaseText("校园网已连接");
        status.setText("统一认证已完成");
        hint.setText("正在自动进入教务，不需要再登一遍");
        ui.postDelayed(this::nextPlatform, 320);
    }

    private void nextPlatform() {
        if (closed) return;
        if (needIdx >= need.length) {
            finish();
            return;
        }
        String p = need[needIdx];
        phase = PH_CHECK;
        phaseAt = System.currentTimeMillis();
        waits = 0;
        lastStage = -1;
        setPhaseText("正在检查" + Portal.shortName(p));
        status.setText("正在自动进入" + Portal.name(p) + "…");
        hint.setText("统一认证已经通过；能单点进入就不会再打扰你");
        advanceBtn.setText("跳过检查");
        web.loadUrl(Portal.home(p));
    }

    /** 教务确实要自己登一次 —— 只有这时候才把登录页留给用户。 */
    private void enterLogin(String p, String why) {
        if (phase == PH_LOGIN) return;
        phase = PH_LOGIN;
        phaseAt = System.currentTimeMillis();
        lastStage = -1;
        setPhaseText(Portal.shortName(p) + "需要登录");
        status.setText(why == null || why.isEmpty()
                ? "请在窗口里登录" + Portal.name(p) : why);
        hint.setText("统一认证已经完成；这里是" + Portal.name(p)
                + "自己的登录页，登完自动继续");
        advanceBtn.setText(needIdx >= need.length - 1 ? "完成" : "登录好了，继续");
    }

    private void setPhaseText(String text) {
        phaseText.setText(text);
    }

    private void probe() {
        try {
            web.evaluateJavascript(Engine.raw(host, R.raw.probe), null);
        } catch (Exception ignored) {
        }
    }

    private boolean handle(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        if (low.startsWith("http://") || low.startsWith("https://")) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(i);
        } catch (Exception ignored) {
        }
        return true;
    }

    private void onProbe(String json) {
        if (closed) return;
        int pwd = 0;
        boolean casBtn = false;
        String url = lastUrl;
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            pwd = o.optInt("pwd", 0);
            casBtn = o.optInt("cas", 0) == 1;
            url = o.optString("url", lastUrl);
            lastTitle = o.optString("title", "");
            String name = o.optString("name", "");
            if (name.length() >= 2 && name.length() <= 4 && !name.matches(".*\\d.*")) {
                store.setStudentName(name);
            }
        } catch (Exception ignored) {
        }
        lastUrl = url;
        if (!Portal.authBusy(url)) Portal.noteUrl(url);

        int stage = Portal.stage(url);
        if (stage != lastStage) {
            lastStage = stage;
            phaseAt = System.currentTimeMillis();
            waits = 0;
        }
        String p = phase == PH_ENTRY ? Portal.P_VPN : need[Math.min(needIdx, need.length - 1)];

        switch (phase) {
            case PH_ENTRY:
                entryState(url, stage, pwd, casBtn);
                break;
            case PH_CHECK:
                checkState(p, url, stage, pwd);
                break;
            case PH_LOGIN:
                loginState(p, url, stage, pwd);
                break;
            default:
                break;
        }
    }

    /**
     * 校园网阶段：**只认票**。
     * CAS 登录成功的时候票还没回来（要等 302 到 cas_login=true&ticket=… 由服务端换），
     * 这时候如果按 URL 猜，就会像旧版一样提前 loadUrl，把票据跳转打掉。
     */
    private void entryState(String url, int stage, int pwd, boolean casBtn) {
        if (stage == Portal.ST_SECOND) {
            status.setText("需要二次验证");
            hint.setText(Portal.stageHint(stage));
            return;
        }
        if (stage == Portal.ST_LOGIN_CAS || stage == Portal.ST_LOGIN_VPN) {
            if (stage == Portal.ST_LOGIN_VPN && casBtn && autoCas && casTries < 2) {
                clickCas();
                return;
            }
            status.setText("请在窗口里完成统一认证");
            hint.setText(Portal.stageHint(stage));
            return;
        }
        if (stage == Portal.ST_CALLBACK) {
            /* 票已经在 Cookie 里了就不用再等 URL —— 有的情况下回调页会停在原地，
               但会话其实已经建立。票才是唯一可信的信号。 */
            if (Portal.hasVpnTicket()) {
                entryOk();
                return;
            }
            status.setText("统一认证已通过，正在建立校园网会话…");
            hint.setText(Portal.stageHint(stage));
            /* 票一直没回来：多半是回调被打断或服务端没认，让用户重来一次，
               不要像旧版那样卡在「登录页 / 空白页」上没有任何提示 */
            if (System.currentTimeMillis() - phaseAt > 15_000L) {
                hint.setText("没有等到校园网会话，点底部「重新载入」再登一次");
                return;
            }
            waitAndProbe(700);
            return;
        }
        if (Portal.hasVpnTicket()) {
            entryOk();
            return;
        }
        status.setText("正在登录校园网…");
        hint.setText("如果一直停在这里，点底部「重新载入」重来一次");
        if (System.currentTimeMillis() - phaseAt > 15_000L) {
            hint.setText("校园网没有建立会话，点底部「重新载入」再登一次");
            return;
        }
        /* 票没回来但页面已经不是登录页（CAS 站内跳转中）：等它，不打断 */
        if (stage == Portal.ST_INSIDE || stage == 0) waitAndProbe(900);
    }

    private void clickCas() {
        casTries++;
        status.setText("正在转到统一身份认证…");
        hint.setText("用学号密码登录，有二次验证就按页面提示走");
        web.evaluateJavascript(
                "(function(){var a=document.getElementById('cas-login')||"
                        + "document.querySelector('a[href*=cas_login]');if(a)a.click();})();", null);
    }

    /** 检查阶段：能进就跳过，进不去才把登录页给用户。 */
    private void checkState(String p, String url, int stage, int pwd) {
        if (ready(p, url, pwd)) {
            Session.markOk(host, p);
            Session.snapshot(host);
            status.setText(Portal.shortName(p) + " 已自动进入");
            needIdx++;
            ui.postDelayed(this::nextPlatform, 260);
            return;
        }
        /* CAS 单点跳转中（教务 → CAS → 教务），等它跳完，绝不插一脚 */
        if (Portal.authBusy(url) && pwd == 0) {
            status.setText("正在通过统一认证进入" + Portal.shortName(p) + "…");
            hint.setText("不需要操作，正在自动完成单点登录");
            waitAndProbe(700);
            return;
        }
        if (pwd > 0 || Portal.loginPage(url)) {
            enterLogin(p, Portal.shortName(p) + "需要再登录一次");
            return;
        }
        long spent = System.currentTimeMillis() - phaseAt;
        if (spent > 12_000L) {
            enterLogin(p, Portal.shortName(p) + "没有自动进入，请手动登录");
            return;
        }
        status.setText("正在进入" + Portal.name(p) + "…");
        hint.setText("第一次打开会慢一点，正在等页面加载");
        waitAndProbe(900);
    }

    private void loginState(String p, String url, int stage, int pwd) {
        if (ready(p, url, pwd)) {
            Session.markOk(host, p);
            Session.snapshot(host);
            status.setText(Portal.shortName(p) + " 已登录");
            needIdx++;
            ui.postDelayed(this::nextPlatform, 260);
            return;
        }
        if (pwd > 0) {
            status.setText("请在窗口里登录" + Portal.name(p));
        } else if (stage == Portal.ST_SECOND) {
            status.setText("需要二次验证");
            hint.setText(Portal.stageHint(stage));
        } else if (Portal.authBusy(url)) {
            status.setText("正在跳转…");
            waitAndProbe(900);
        } else {
            status.setText("等待" + Portal.shortName(p) + "页面加载…");
        }
    }

    private void waitAndProbe(long delay) {
        if (closed) return;
        if (waits++ > 18) return;
        ui.postDelayed(this::probe, delay);
    }

    /**
     * 平台是否真的可用。只认「已经进到系统里的页面」，
     * 登录页、CAS 跳转、密码框一律不算。
     */
    private boolean ready(String platform, String url, int pwd) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return false;
        if (pwd > 0) return false;
        if (Portal.authBusy(url)) return false;
        if (Portal.loginPage(url)) return false;
        if (Portal.stage(url) != Portal.ST_INSIDE) return false;
        String u = url.toLowerCase();
        if (Portal.P_JW.equals(platform)) {
            boolean path = u.contains("/student/") || u.endsWith("/index");
            boolean title = lastTitle.contains("URP") || lastTitle.contains("教务");
            return path || title;
        }
        if (Portal.P_EHALL.equals(platform)) {
            return (u.contains(Portal.HOST_EHALL) || u.contains(Portal.HOST_XS))
                    && !u.contains("/login");
        }
        return false;
    }

    private void manualAdvance() {
        if (closed) return;
        if (phase == PH_ENTRY) {
            if (Portal.hasVpnTicket()) entryOk();
            else {
                status.setText("还没拿到校园网会话，请先完成窗口里的登录");
            }
            return;
        }
        if (phase == PH_CHECK) {
            /* 用户说已经好了：记为在线，继续下一个 */
            Session.markOk(host, need[needIdx]);
            needIdx++;
            nextPlatform();
            return;
        }
        if (phase == PH_LOGIN) {
            Session.markOk(host, need[needIdx]);
            Session.snapshot(host);
            needIdx++;
            nextPlatform();
        }
    }

    private void finish() {
        if (phase == PH_DONE) return;
        phase = PH_DONE;
        CookieManager.getInstance().flush();
        Session.snapshot(host);
        if (!Session.ok(Portal.P_VPN)) Session.markOk(host, Portal.P_VPN);
        store.setLoginAt(System.currentTimeMillis());
        setPhaseText("登录完成");
        status.setText("登录完成");
        hint.setText("教务已就绪，课表、成绩、考试可以直接用了");
        if (onSuccess != null) onSuccess.run();
        ui.postDelayed(this::close, 420);
    }

    private void close() {
        if (closed) return;
        closed = true;
        try {
            web.stopLoading();
            web.removeJavascriptInterface("HBU_P");
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
        } catch (Exception ignored) {
        }
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    public final class Bridge {
        @JavascriptInterface
        public void onProbe(String json) {
            ui.post(() -> LoginDialog.this.onProbe(json));
        }
    }
}
