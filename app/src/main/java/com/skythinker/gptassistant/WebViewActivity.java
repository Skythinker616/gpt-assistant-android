package com.skythinker.gptassistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WebViewActivity extends Activity {

    WebView webView;
    WebViewClient webViewClient;
    TextView tvTitle;
    ImageButton btBack, btBrowser;
    String targetUrl = "", jsCode = "";
    boolean fixedTitle = false;

    static public void openUrl(Context context, String title, String url) {
        Intent intent = new Intent(context, WebViewActivity.class);
        if(title != null)
            intent.putExtra("title", title);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = findViewById(R.id.wv_preview);
        tvTitle = findViewById(R.id.tv_webview_title);
        btBack = findViewById(R.id.bt_webview_back);
        btBrowser = findViewById(R.id.bt_webview_browser);

        webViewClient = new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if(url.equals(targetUrl)) {
                    webView.evaluateJavascript(jsCode, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                        }
                    });
                }
                if(!fixedTitle) {
                    tvTitle.setText(webView.getTitle());
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if(!fixedTitle) {
                    tvTitle.setText(url);
                }
                super.onPageStarted(view, url, favicon);
            }

            //            @Override
//            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//                String url = request.getUrl().toString();
//                if(url.equals(targetUrl)) {
//                    return super.shouldOverrideUrlLoading(view, request);
//                } else {
//                    GlobalUtils.browseURL(WebViewActivity.this, url);
//                    return true;
//                }
//            }
        };

        try {

            WebView.setWebContentsDebuggingEnabled(true);
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webSettings.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            webView.setWebViewClient(webViewClient);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent recv_intent = getIntent();
        if(recv_intent.hasExtra("title")) {
            tvTitle.setText(recv_intent.getStringExtra("title"));
            fixedTitle = true;
        } else {
            tvTitle.setText("");
        }
        if(recv_intent.hasExtra("body_selector")) {
            jsCode += "document.body.replaceWith(document.querySelector('" + recv_intent.getStringExtra("body_selector") + "'));";
        }
        if(recv_intent.hasExtra("url")) {
            targetUrl = recv_intent.getStringExtra("url");
            webView.loadUrl(targetUrl);
            btBrowser.setOnClickListener(view -> {
                GlobalUtils.browseURL(this, webView.getUrl());
            });
        }

        btBack.setOnClickListener(view -> {
            finish();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }
}