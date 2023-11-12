package com.skythinker.gptassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.List;

public class WebScraper {
    public interface Callback {
        void onLoadResult(String result);
        void onLoadFail(String message);
    }

    private class WebsiteRule {
        public String urlPattern = ".*";
        public String jsCode = "(function(){return document.body.innerText;})();";
        public boolean desktopMode = false;
        public int extraDelay = 500;
        public int timeout = 15000;

        public WebsiteRule url(String url) {
            this.urlPattern = url;
            return this;
        }
        public WebsiteRule js(String js) {
            this.jsCode = js;
            return this;
        }
        public WebsiteRule desktopMode(boolean desktopMode) {
            this.desktopMode = desktopMode;
            return this;
        }
        public WebsiteRule extraDelay(int extraDelay) {
            this.extraDelay = extraDelay;
            return this;
        }
        public WebsiteRule timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }
    }

    Handler handler = null;
    private WebView webView = null;
    private WebViewClient webViewClient = null;
    private String loadingUrl = "";
    private Callback callback = null;
    private boolean isLoading = false;
    private int jumpCount = 0;

    String searchWebsiteJsTemplate = "(function(){var res='';" +
            "document.querySelectorAll('<selector>').forEach(function(box){" +
            "   res+=box.innerText.replace(/\\n/g,' ')+'\\n';" +
            "   var a=box.querySelectorAll('a')[<linkIndex>];" +
            "   if(a&&a.href) res+=a.href+'\\n';" +
            "   res+='---\\n';});" +
            "if(res=='')res=document.body.innerText;" +
            "return res;})();";

    List<WebsiteRule> websiteRules = Arrays.asList(
        new WebsiteRule().url("^https://www.baidu.com/s\\?.*").desktopMode(true).js(
            searchWebsiteJsTemplate.replace("<selector>", ".result.c-container,.result-op.c-container")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://www.bing.com/search\\?.*|^https://cn.bing.com/search\\?.*").js(
            searchWebsiteJsTemplate.replace("<selector>", ".b_ans,.b_algo")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://www.google.com/search\\?.*").js(
            searchWebsiteJsTemplate.replace("<selector>", ".MjjYud,.TzHB6b")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://www.zhihu.com/search\\?.*").extraDelay(2000).js(
            searchWebsiteJsTemplate.replace("<selector>", ".SearchResult-Card")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://www.zhihu.com/hot").extraDelay(2000).js(
            searchWebsiteJsTemplate.replace("<selector>", ".HotItem")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://s.weibo.com/weibo/.*|^https://m.weibo.cn/search\\?.*").extraDelay(2000),
        new WebsiteRule().url("^https://search.bilibili.com/all\\?.*").desktopMode(true).js(
            searchWebsiteJsTemplate.replace("<selector>", ".bili-video-card")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://search.jd.com/Search\\?.*").js(
            searchWebsiteJsTemplate.replace("<selector>", ".gl-item")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://github.com/search\\?.*").js(
            searchWebsiteJsTemplate.replace("<selector>", ".jUbAHB")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://scholar.google.com/scholar\\?.*").js(
            searchWebsiteJsTemplate.replace("<selector>", ".gs_ri")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule().url("^https://kns.cnki.net/kns8s/defaultresult/index\\?.*").extraDelay(2000).js(
            searchWebsiteJsTemplate.replace("<selector>", ".result-table-list tr")
                .replace("<linkIndex>", "0")
        ),
        new WebsiteRule()
    );

    WebsiteRule websiteRule = null;

    public WebScraper(Context context, LinearLayout parentLayout) {
        handler = new Handler(context.getMainLooper());

        webViewClient = new WebViewClient() {
            private void endLoading() {
                callback = null;
                isLoading = false;
                jumpCount = 0;
                loadingUrl = "";
                webView.stopLoading();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d("WebView", "shouldOverrideUrlLoading " + url);
                if(url.startsWith("http://") || url.startsWith("https://") || !url.contains("://")) {
                    view.loadUrl(url);
                    jumpCount++;
                    loadingUrl = url;
                }
                return true;
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d("WebView", "onPageStarted ");
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("WebView", "onPageFinished " + url);
                if(jumpCount == 1 || url.equals(loadingUrl)) {
                    handler.postDelayed(() -> {
                        if (callback != null){
                            webView.evaluateJavascript(websiteRule.jsCode,
                                new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String responseText) {
                                        responseText = responseText.replaceAll("\\\\n", "\n")
                                                .replaceAll("\\u003C", "<")
                                                .replaceAll("\\\"", "\"");
                                        if (responseText.length() > GlobalDataHolder.getWebMaxCharCount())
                                            responseText = responseText.substring(0, GlobalDataHolder.getWebMaxCharCount());
                                        if (responseText.isEmpty())
                                            responseText = "The response is empty.";
                                        if(callback != null)
                                            callback.onLoadResult(responseText);
                                        else
                                            Log.e("WebView", "callback is null when finished");
                                        endLoading();
                                    }
                                });
                        }
                    }, websiteRule.extraDelay);
                    loadingUrl = "";
                }
                if(jumpCount > 0)
                    jumpCount--;
                super.onPageFinished(view, url);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e("WebView", "onReceivedError " + error.getErrorCode() + " " + error.getDescription());
                super.onReceivedError(view, request, error);
            }
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e("WebView", "onRenderProcessGone " + detail);
                if(callback != null)
                    callback.onLoadFail(detail.toString());
                endLoading();
                return true;
            }
        };

        try {
            WebView.setWebContentsDebuggingEnabled(true);
            webView = new WebView(context);
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.setWebViewClient(webViewClient);
            parentLayout.addView(webView, 0);
            webView.setVisibility(View.INVISIBLE);
            webView.setLayoutParams(new LinearLayout.LayoutParams(500, 1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void load(String url, Callback callback) {
        if(webView == null)
            return;

        if(isLoading)
            stopLoading();

        isLoading = true;
        this.callback = callback;
        jumpCount = 1;
        loadingUrl = url;

        for(WebsiteRule rule : websiteRules) {
            if(url.matches(rule.urlPattern)) {
                websiteRule = rule;
                break;
            }
        }
        if(websiteRule.desktopMode)
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36");
        else
            webView.getSettings().setUserAgentString(null);

        new Thread(() -> {
            int timeout = websiteRule.timeout;
            int waitTime = 0;
            while(waitTime < timeout && isLoading) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) { }
                waitTime += 100;
            }
            if(isLoading) {
                handler.post(() -> {
                    if(callback != null)
                        callback.onLoadFail("Timeout");
                    stopLoading();
                });
                Log.e("WebView", "Timeout");
            }
        }).start();

        webView.loadUrl(url);
    }

    public void stopLoading(){
        if(webView == null)
            return;
        webView.stopLoading();
        isLoading = false;
        callback = null;
        jumpCount = 0;
        loadingUrl = "";
    }

    public boolean isLoading(){
        return isLoading;
    }

    public void destroy(){
        if(webView == null)
            return;
        webView.destroy();
        webView = null;
        webViewClient = null;
        callback = null;
        isLoading = false;
        jumpCount = 0;
    }
}
