package com.skythinker.gptassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class TabDetailConfActivity extends Activity {

    private EditText etTitle, etPrompt;

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
            intent.putExtra("prompt", etPrompt.getText().toString());
            setResult(RESULT_OK, intent);
            finish();
        });

        (findViewById(R.id.bt_tab_detail_back)).setOnClickListener(view -> {
            finish();
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}