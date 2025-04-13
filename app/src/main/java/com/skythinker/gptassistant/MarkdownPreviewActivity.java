package com.skythinker.gptassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MarkdownPreviewActivity extends Activity {

    TextView tvTitle, tvPreview;
    ImageButton btBrowser, btBack;
    MarkdownRenderer markdownRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_preview);

        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        tvTitle = findViewById(R.id.tv_markdown_preview_title);
        tvPreview = findViewById(R.id.tv_markdown_preview);
        btBrowser = findViewById(R.id.bt_markdown_preview_browser);
        btBack = findViewById(R.id.bt_markdown_preview_back);

        tvPreview.setMovementMethod(LinkMovementMethod.getInstance());

        markdownRenderer = new MarkdownRenderer(this);

        Intent recv_intent = getIntent();
        if(recv_intent.hasExtra("title")) {
            tvTitle.setText(recv_intent.getStringExtra("title"));
        } else {
            tvTitle.setText("");
        }
        if(recv_intent.hasExtra("markdown")) {
            markdownRenderer.render(tvPreview, recv_intent.getStringExtra("markdown"));
        } else if(recv_intent.hasExtra("url")) {
            tvPreview.setText(R.string.text_markdown_preview_loading);
            String url = recv_intent.getStringExtra("url");
            new Thread(() -> {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                try {
                    Response response = client.newCall(request).execute();
                    String markdown = response.body().string();
                    runOnUiThread(() -> {
                        markdownRenderer.render(tvPreview, markdown);
                    });
                } catch (IOException e) {
                    tvPreview.setText(R.string.text_markdown_preview_loading_failed);
                    e.printStackTrace();
                }
            }).start();
        } else {
            tvPreview.setText("");
        }
        if(recv_intent.hasExtra("browser_url")) {
            btBrowser.setVisibility(View.VISIBLE);
            btBrowser.setOnClickListener(view -> {
                GlobalUtils.browseURL(this, recv_intent.getStringExtra("browser_url"));
            });
        } else {
            btBrowser.setVisibility(View.GONE);
        }

        btBack.setOnClickListener(view -> {
            finish();
        });
    }
}