// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hbu.zhangshanghbu.BaseActivity;
import com.hbu.zhangshanghbu.core.Kit;
import com.hbu.zhangshanghbu.data.Store;

/**
 * 内置浏览器：办事大厅、教务长尾页面、空教室这些没做原生界面的功能从这里进。
 *
 * 和登录窗口共用同一个 Cookie 仓库，所以只要登录态还在，打开就是已登录状态。
 * 会话过期会跳回登录页 —— 就在这个窗口里再登一次，登完继续。
 */
public class WebActivity extends BaseActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";

    private WebView web;
    private Store store;
    private ProgressBar bar;

    public static void open(Activity host, String url, String title) {
        Intent i = new Intent(host, WebActivity.class);
        i.putExtra(EXTRA_URL, url);
        i.putExtra(EXTRA_TITLE, title);
        host.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = store();
        String url = getIntent() == null ? Portal.URL_JW
                : getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent() == null ? "教务" : getIntent().getStringExtra(EXTRA_TITLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pageColor());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(8), dp(12), dp(6));

        TextView close = Kit.text(this, "‹", 24f, Kit.ink(this));
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setPadding(dp(6), 0, dp(12), dp(4));
        close.setOnClickListener(v -> finish());
        head.addView(close);

        TextView t = Kit.bold(this, title == null ? "教务" : title, 15f, Kit.ink(this));
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reload = Kit.text(this, "刷新", 12.5f, Kit.brand(this));
        reload.setPadding(dp(10), dp(6), dp(6), dp(6));
        reload.setOnClickListener(v -> {
            if (web != null) web.reload();
        });
        head.addView(reload);

        TextView ua = Kit.text(this, "电脑版", 12.5f, Kit.brand(this));
        ua.setPadding(dp(8), dp(6), dp(4), dp(6));
        ua.setOnClickListener(v -> {
            store.setDesktopUa(!store.desktopUa());
            if (web != null) {
                web.getSettings().setUserAgentString(store.desktopUa()
                        ? Portal.desktopUa() : Portal.mobileUa());
                web.reload();
            }
        });
        head.addView(ua);
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(store.desktopUa() ? Portal.desktopUa() : Portal.mobileUa());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(Color.WHITE);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handle(r.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return handle(u);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                CookieManager.getInstance().flush();
                Session.snapshot(WebActivity.this);
                if (u != null && !Portal.loginPage(u)) {
                    Session.markOk(WebActivity.this, Portal.P_JW);
                }
            }
        });
        col.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, insets.getInsets(WindowInsets.Type.systemBars()).top, 0,
                        insets.getInsets(WindowInsets.Type.systemBars()).bottom);
                return insets;
            });
        }
        web.loadUrl(url == null ? Portal.URL_JW : url);
    }

    private boolean handle(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        if (low.startsWith("http://") || low.startsWith("https://")) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }
}
