package com.skythinker.gptassistant.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.CustomModelProfile;
import com.skythinker.gptassistant.data.ModelCatalog;

import java.util.ArrayList;
import java.util.List;

public class CustomModelDetailActivity extends Activity {

    @Override
    // 初始化模型新增/编辑页面。
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_model_detail);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        int position = getIntent().getIntExtra("position", -1);
        ((android.widget.TextView) findViewById(R.id.tv_custom_model_detail_title))
                .setText(position >= 0 ? R.string.custom_model_detail_title_edit : R.string.custom_model_detail_title_add);

        ((EditText) findViewById(R.id.et_custom_model_detail_id)).setText(getIntent().getStringExtra("model_id"));
        ((EditText) findViewById(R.id.et_custom_model_detail_name)).setText(getIntent().getStringExtra("model_name"));
        ((Switch) findViewById(R.id.sw_custom_model_detail_vision)).setChecked(getIntent().getBooleanExtra("vision", false));
        ((Switch) findViewById(R.id.sw_custom_model_detail_tool)).setChecked(getIntent().getBooleanExtra("tool", false));
        ((Switch) findViewById(R.id.sw_custom_model_detail_thinking)).setChecked(getIntent().getBooleanExtra("thinking", false));

        findViewById(R.id.bt_custom_model_detail_back).setOnClickListener(view -> finish());
        findViewById(R.id.tv_custom_model_detail_save).setOnClickListener(view -> saveModelProfile());
    }

    // 校验输入并将模型配置作为结果返回。
    private void saveModelProfile() {
        String modelId = ((EditText) findViewById(R.id.et_custom_model_detail_id)).getText().toString().trim();
        String modelName = ((EditText) findViewById(R.id.et_custom_model_detail_name)).getText().toString().trim();
        if(modelId.isEmpty()) {
            Toast.makeText(this, R.string.custom_model_toast_id_required, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> existingModelIds = getIntent().getStringArrayListExtra("existing_model_ids");
        if(existingModelIds != null && existingModelIds.contains(modelId)) {
            Toast.makeText(this, R.string.custom_model_toast_duplicate_id, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("position", getIntent().getIntExtra("position", -1));
        resultIntent.putExtra("model_id", modelId);
        resultIntent.putExtra("model_name", modelName);
        resultIntent.putExtra("vision", ((Switch) findViewById(R.id.sw_custom_model_detail_vision)).isChecked());
        resultIntent.putExtra("tool", ((Switch) findViewById(R.id.sw_custom_model_detail_tool)).isChecked());
        resultIntent.putExtra("thinking", ((Switch) findViewById(R.id.sw_custom_model_detail_thinking)).isChecked());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // 将编辑页返回结果转换为模型配置对象。
    public static CustomModelProfile parseResult(Intent data) {
        List<String> capabilities = new ArrayList<>();
        if(data.getBooleanExtra("vision", false)) {
            capabilities.add(ModelCatalog.CAPABILITY_VISION);
        }
        if(data.getBooleanExtra("tool", false)) {
            capabilities.add(ModelCatalog.CAPABILITY_TOOL);
        }
        if(data.getBooleanExtra("thinking", false)) {
            capabilities.add(ModelCatalog.CAPABILITY_THINKING);
        }
        return new CustomModelProfile(
                data.getStringExtra("model_id"),
                data.getStringExtra("model_name"),
                capabilities
        );
    }

    @Override
    // 关闭页面并播放返回动画。
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}
