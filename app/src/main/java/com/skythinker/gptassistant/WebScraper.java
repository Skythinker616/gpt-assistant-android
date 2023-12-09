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

    // 针对特定网站的抓取规则
    private class WebsiteRule {
        public String urlPattern = ".*"; // URL正则匹配模板
        public String jsCode = "(function(){return document.body.innerText;})();"; // 加载完毕后抓取内容的JS代码
        public boolean desktopMode = false; // 是否使用桌面模式加载
        public int extraDelay = 500; // 加载完成后的抓取延迟时间（等待动态渲染）
        public int timeout = 15000; // 加载超时时间

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

    // 针对搜索网页的抓取JS模板
    String searchWebsiteJsTemplate = "(function(){var res='';" +
            "document.querySelectorAll('<selector>').forEach(function(box){" +
            "   res+=box.innerText.replace(/\\n/g,' ')+'\\n';" +
            "   var a=box.querySelectorAll('a')[<linkIndex>];" +
            "   if(a&&a.href) res+=a.href+'\\n';" +
            "   res+='---\\n';});" +
            "if(res=='')res=document.body.innerText;" +
            "return res;})();";

    // 各网站的抓取规则
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
        new WebsiteRule() // 用默认规则匹配其他所有网站
    );

    WebsiteRule websiteRule = null;

    public WebScraper(Context context, LinearLayout parentLayout) {
        handler = new Handler(context.getMainLooper());

        webViewClient = new WebViewClient() { // 初始化WebViewClient事件回调
            private void endLoading() {
                callback = null;
                isLoading = false;
                jumpCount = 0;
                loadingUrl = "";
                webView.stopLoading();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { // 页面产生了重定向
                String url = request.getUrl().toString();
                Log.d("WebView", "shouldOverrideUrlLoading " + url);
                if(url.startsWith("http://") || url.startsWith("https://") || !url.contains("://")) {
                    view.loadUrl(url);
                    jumpCount++; // 跳转深度+1
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
            public void onPageFinished(WebView view, String url) { // 页面加载完成（重定向后原有页面加载完成也会触发）
                Log.d("WebView", "onPageFinished " + url);
                if(jumpCount == 1 || url.equals(loadingUrl)) { // 判定为最终页面加载完成
                    handler.postDelayed(() -> {
                        if (callback != null){
                            // 执行JS代码抓取页面内容
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
                if(jumpCount > 0) // 跳转深度减一
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
            parentLayout.addView(webView, 0); // 将WebView插入父布局，并设置为不可见
            webView.setVisibility(View.INVISIBLE);
            webView.setLayoutParams(new LinearLayout.LayoutParams(500, 1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 加载一个URL
    public void load(String url, Callback callback) {
        if(webView == null)
            return;

        if(isLoading)
            stopLoading();

        isLoading = true;
        this.callback = callback;
        jumpCount = 1;
        loadingUrl = url;

        for(WebsiteRule rule : websiteRules) { // 进行规则匹配
            if(url.matches(rule.urlPattern)) {
                websiteRule = rule;
                break;
            }
        }
        if(websiteRule.desktopMode) // 若需要桌面模式则设置UA
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36");
        else
            webView.getSettings().setUserAgentString(null);

        new Thread(() -> { // 开启超时等待线程
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

    // 停止加载
    public void stopLoading(){
        if(webView == null)
            return;
        webView.stopLoading();
        isLoading = false;
        callback = null;
        jumpCount = 0;
        loadingUrl = "";
    }

    // 判断是否正在加载
    public boolean isLoading(){
        return isLoading;
    }

    // 销毁所有数据
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
