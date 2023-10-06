package com.skythinker.gptassistant;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

public class TabDetailConfActivity extends Activity {

    private EditText etTitle, etPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_detail_conf);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

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

        (findViewById(R.id.cv_tab_detail_cancel)).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.putExtra("ok", false);
            setResult(RESULT_OK, intent);
            finish();
        });

        (findViewById(R.id.cv_tab_detail_ok)).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.putExtra("ok", true);
            intent.putExtra("title", etTitle.getText().toString());
            intent.putExtra("prompt", etPrompt.getText().toString());
            setResult(RESULT_OK, intent);
            finish();
        });
    }
}