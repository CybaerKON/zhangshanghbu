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
 * 顺序登录窗口：先校园网（WebVPN），再本科教务（URP），最后办事大厅。
 *
 * 设计要点（都是被现实逼出来的）：
 *   1. **自动判定 + 手动推进**并存。自动判定只看「有没有可见密码框 + 地址是不是登录页 +
 *      是不是这个平台的特征页」，判不准时不会卡死 —— 每步都有「这步已完成」；
 *   2. 每一步都先 loadUrl 平台首页：校外会 302 到 WebVPN 的编码地址，登录后
 *      自动回到目标页。App 不拼地址、不碰密码；
 *   3. 完成一步立刻快照 Cookie 并标记该平台在线；中途关掉也不丢已完成的步骤；
 *   4. 全部完成后才关窗（需求要求「弹出窗口直到登录完成关闭」）。
 */
public final class LoginDialog {

    private final Activity host;
    private final Store store;
    private final String[] platforms;
    private final Runnable onSuccess;

    private final Dialog dialog;
    private WebView web;
    private ProgressBar bar;
    private TextView stepText;
    private TextView status;
    private TextView hint;
    private LinearLayout chips;
    private TextView advanceBtn;

    private int index = 0;
    private boolean closed = false;
    private boolean advancing = false;
    private boolean probedOnce = false;
    /** 只在入口页自动点一次「统一认证」，避免来回跳 */
    private boolean casClicked = false;
    private String lastUrl = "";
    private String lastTitle = "";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LoginDialog(Activity host, String[] platforms, Runnable onSuccess) {
        this.host = host;
        this.store = new Store(host);
        this.platforms = platforms == null || platforms.length == 0
                ? Portal.loginOrder(false) : platforms;
        this.onSuccess = onSuccess;
        this.dialog = new Dialog(host, android.R.style.Theme_Translucent_NoTitleBar);
        build();
    }

        /** 点掉入口页的「统一认证（CAS登录业务系统）」按钮。 */
    private static final String CAS_CLICK_JS =
            "(function(){var a=document.getElementById(\"cas-login\");"
                    + "if(!a){a=document.querySelector(\"a[href*=cas_login]\");}"
                    + "if(a){a.click();}})();";

public static LoginDialog show(Activity host, String[] platforms, Runnable onSuccess) {
        LoginDialog d = new LoginDialog(host, platforms, onSuccess);
        d.dialog.show();
        d.loadStep();
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

        // 标题
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

        head.addView(Kit.bold(host, "校园网登录", 15.5f, Kit.ink(host)),
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

        // 步骤条
        chips = new LinearLayout(host);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(16), 0, dp(16), dp(8));
        card.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stepText = Kit.text(host, "", 11.5f, Kit.faint(host));
        stepText.setPadding(dp(16), 0, dp(16), dp(4));
        card.addView(stepText);

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
                CookieManager.getInstance().flush();
                Session.snapshot(host);
                if (closed || advancing) return;
                ui.postDelayed(LoginDialog.this::probe, 420);
            }
        });
        box.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部
        LinearLayout foot = new LinearLayout(host);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(store.darkNow() ? 0xFF161D29 : 0xFFFFFFFF);
        foot.setPadding(dp(16), dp(10), dp(16), dp(10));

        status = Kit.text(host, "正在打开…", 12.5f, Kit.ink(host));
        foot.addView(status);
        hint = Kit.text(host, "按顺序登录，全部完成后窗口自动关闭", 11f, Kit.faint(host));
        hint.setPadding(0, dp(4), 0, 0);
        foot.addView(hint);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);
        actions.addView(action("重新载入", () -> web.loadUrl(Portal.home(platforms[index]))));

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

    private void loadStep() {
        if (index >= platforms.length) {
            finish();
            return;
        }
        advancing = false;
        probedOnce = false;
        String p = platforms[index];
        paintChips();
        stepText.setText("第 " + (index + 1) + " / " + platforms.length + " 步");
        status.setText("正在打开" + Portal.name(p) + "…");
        advanceBtn.setText(index == platforms.length - 1 ? "全部完成" : "这步已完成");
        hint.setText(hintFor(p));
        web.loadUrl(Portal.home(p));
    }

    private String hintFor(String p) {
        if (Portal.P_VPN.equals(p)) {
            return "先打通校园网隧道，账号就是统一认证那套（可能还有验证码 / 二次验证）";
        }
        if (Portal.P_JW.equals(p)) {
            return "教务若再要一次登录页，照原样登；如果已通过统一认证，会直接进主页";
        }
        return "办事大厅同样走统一认证；不想登可以点「稍后再说」，不影响课表和成绩";
    }

    private void paintChips() {
        chips.removeAllViews();
        for (int i = 0; i < platforms.length; i++) {
            boolean done = i < index;
            boolean now = i == index;
            String label = (done ? "✓ " : (i + 1) + " ") + Portal.shortName(platforms[i]);
            TextView chip = Kit.text(host, label, 11f,
                    now ? 0xFFFFFFFF : (done ? Kit.brand(host) : Kit.faint(host)));
            int bg = now ? Kit.brand(host) : (done ? 0x140063C6 : (store.darkNow() ? 0xFF1F2836 : 0xFFF1F3F7));
            chip.setPadding(dp(9), dp(4), dp(9), dp(4));
            chip.setBackground(Kit.round(host, bg, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chips.addView(chip, lp);
        }
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
        if (closed || advancing) return;
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
        probedOnce = true;
        Portal.noteUrl(url);

        String p = platforms[index];
        int stage = Portal.stage(url);

        /* 入口页上有「统一认证」按钮就替用户点掉：CAS 一次登录之后，
           校园网和教务都是通的，用户没必要先登一遍 VPN 再登一遍 CAS。 */
        if (stage == Portal.ST_LOGIN_VPN && casBtn && !casClicked) {
            casClicked = true;
            status.setText("正在转到统一身份认证…");
            web.evaluateJavascript(
                    "(function(){var a=document.getElementById('cas-login')||"
                            + "document.querySelector('a[href*=cas_login]');if(a)a.click();})();", null);
            return;
        }

        /* 二次验证页没有密码框，绝不能用「没有密码框」当已登录 —— 这正是之前
           窗口提前跳到下一步、实际还卡在验证码上的原因。 */
        if (stage == Portal.ST_SECOND) {
            status.setText("需要二次验证 · " + Portal.shortName(p));
            hint.setText(Portal.stageHint(stage));
            return;
        }

        if (stage == Portal.ST_LOGIN_CAS || stage == Portal.ST_LOGIN_VPN) {
            status.setText("请在窗口里完成" + Portal.name(p) + "登录");
            hint.setText(Portal.stageHint(stage));
            return;
        }

        if (stage == Portal.ST_INSIDE && isLogged(p, url, pwd)) {
            casClicked = false;
            status.setText(Portal.shortName(p) + " 已登录 · 正在进入下一步…");
            advance();
            return;
        }

        status.setText("请在窗口里完成" + Portal.name(p) + "登录");
    }

    /**
     * 每个平台单独判定。只看「没有密码框」不够 —— WebVPN 登录后会停在资源门户，
     * 那时教务还没登，但地址已经不像登录页了。
     */
    private boolean isLogged(String platform, String url, int pwd) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return false;
        if (pwd > 0) return false;
        /* 阶段判定放在前面：只要还停在登录 / 验证码页，一律不算登录，
           不管页面上有没有密码框。 */
        int stage = Portal.stage(url);
        if (stage != Portal.ST_INSIDE) return false;
        String u = url.toLowerCase();
        if (Portal.P_VPN.equals(platform)) {
            /* 进了隧道或资源站，且不在登录页 */
            return u.contains(Portal.HOST_VPN) && !u.contains("/login");
        }
        if (Portal.P_JW.equals(platform)) {
            if (Portal.loginPage(u)) return false;
            /* 河大教务的真实路径是 /student/... 或 /index，标题叫「URP综合教务系统首页」。
               CAS 打通后由服务端票继承，所以进到这两者之一就算通了。 */
            boolean path = u.contains("/student/") || u.endsWith("/index");
            boolean title = lastTitle.contains("URP") || lastTitle.contains("教务");
            return path || title;
        }
        /* 办事大厅 / 学生事务 */
        if (Portal.loginPage(u)) return false;
        if (u.contains(Portal.HOST_EHALL) || u.contains(Portal.HOST_XS)) return !u.contains("/login");
        return false;
    }

    private void manualAdvance() {
        if (advancing || closed) return;
        if (index >= platforms.length - 1) {
            /* 最后一步：标完直接结束 */
            Session.markOk(host, platforms[index]);
            Session.snapshot(host);
            finish();
            return;
        }
        status.setText(Portal.shortName(platforms[index]) + " 记为已完成");
        advance();
    }

    private void advance() {
        if (advancing || closed) return;
        advancing = true;
        String p = platforms[index];
        Portal.clearMenu();   // 换了账号或会话，菜单 id 全变了
        Session.markOk(host, p);
        Session.snapshot(host);
        store.setLoginAt(System.currentTimeMillis());
        paintChips();
        index++;
        ui.postDelayed(this::loadStep, 420);
    }

    private void finish() {
        CookieManager.getInstance().flush();
        Session.snapshot(host);
        store.setLoginAt(System.currentTimeMillis());
        status.setText("登录完成");
        if (onSuccess != null) onSuccess.run();
        ui.postDelayed(this::close, 380);
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
