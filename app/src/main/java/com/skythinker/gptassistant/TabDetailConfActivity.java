package com.skythinker.gptassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class TabDetailConfActivity extends Activity {

    private EditText etTitle, etPrompt;
    private boolean isFromOnlineTemplates = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_detail_conf);

        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        etTitle = findViewById(R.id.et_tab_detail_title);
        etPrompt = findViewById(R.id.et_tab_detail_prompt);

        Intent recv_intent = getIntent();
        if(recv_intent.hasExtra("title")) {
            etTitle.setText(recv_intent.getStringExtra("title"));
        } else {
            etTitle.setText("");
        }
        if(recv_intent.hasExtra("prompt")) {
            etPrompt.setText(recv_intent.getStringExtra("prompt"));
        } else {
            etPrompt.setText("");
        }

        (findViewById(R.id.cv_tab_detail_cancel)).setOnClickListener(view -> { // 点击取消按钮
            Intent intent = new Intent();
            intent.putExtra("ok", false);
            setResult(RESULT_OK, intent);
            finish();
        });

        (findViewById(R.id.cv_tab_detail_ok)).setOnClickListener(view -> { // 点击确定按钮
            Intent intent = new Intent();
            intent.putExtra("ok", true);
            intent.putExtra("title", etTitle.getText().toString());
            intent.putExtra("prompt", etPrompt.getText().toString().replaceAll("\\r", ""));
            intent.putExtra("fromOnline", isFromOnlineTemplates);
            setResult(RESULT_OK, intent);
            finish();
        });

        (findViewById(R.id.bt_tab_detail_help)).setOnClickListener(view -> { // 打开模板说明页面
            Intent intent = new Intent(TabDetailConfActivity.this, MarkdownPreviewActivity.class);
            intent.putExtra("title", getString(R.string.text_template_help_title));
            if(GlobalUtils.languageIsChinese()) {
                intent.putExtra("url", getString(GlobalDataHolder.getUseGitee() ? R.string.template_help_raw_url_gitee_zh : R.string.template_help_raw_url_github_zh));
                intent.putExtra("browser_url", getString(GlobalDataHolder.getUseGitee() ? R.string.template_help_url_gitee_zh : R.string.template_help_url_github_zh));
            } else {
                intent.putExtra("url", getString(GlobalDataHolder.getUseGitee() ? R.string.template_help_raw_url_gitee_en : R.string.template_help_raw_url_github_en));
                intent.putExtra("browser_url", getString(GlobalDataHolder.getUseGitee() ? R.string.template_help_url_gitee_en : R.string.template_help_url_github_en));
            }
            startActivity(intent);
        });

        (findViewById(R.id.bt_share_template)).setOnClickListener((view) -> {
            GlobalUtils.copyToClipboard(this, etPrompt.getText().toString().replaceAll("\\r", ""));
            GlobalUtils.browseURL(this, getString(R.string.new_discussion_url));
            GlobalUtils.showToast(this, getString(R.string.toast_share_template), true);
        });

        (findViewById(R.id.bt_online_templates)).setOnClickListener(view -> {
            startActivityForResult(new Intent(TabDetailConfActivity.this, OnlineTemplatesActivity.class), 0);
        });

        (findViewById(R.id.bt_tab_detail_back)).setOnClickListener(view -> {
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0 && resultCode==RESULT_OK) {
            etTitle.setText(data.getStringExtra("tag"));
            etPrompt.setText(data.getStringExtra("content"));
            isFromOnlineTemplates = true;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}