package com.skythinker.gptassistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TabConfActivity extends Activity {

    private RecyclerView rvTabList;
    private TabConfListAdapter adapter;
    private BroadcastReceiver localReceiver;
    private Handler handler = new Handler();

    private interface CustomTextWatcher extends TextWatcher { // 去掉TextWatcher不需要的方法
        @Override default void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
        @Override default void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_conf);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        List<PromptTabData> tabDataList = GlobalDataHolder.getTabDataList();
        rvTabList = findViewById(R.id.rv_tab_conf_list);
        rvTabList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TabConfListAdapter(this);
        rvTabList.setAdapter(adapter);

        // 模板列表拖拽处理
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder dragged, RecyclerView.ViewHolder target) { // 拖拽排序
                int position_dragged = dragged.getAdapterPosition();
                int position_target = target.getAdapterPosition();
                PromptTabData tab = tabDataList.get(position_dragged);
                tabDataList.set(position_dragged, tabDataList.get(position_target));
                tabDataList.set(position_target, tab);
                adapter.notifyItemMoved(position_dragged, position_target);
                GlobalDataHolder.saveTabDataList();
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) { // 左滑删除
                tabDataList.remove(viewHolder.getAdapterPosition());
                adapter.notifyItemRemoved(viewHolder.getAdapterPosition());
                GlobalDataHolder.saveTabDataList();
            }
        }).attachToRecyclerView(rvTabList);

        // 接收模板编辑请求广播（来自TabConfListAdapter）
        localReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                startEditTab(intent.getStringExtra("title"), intent.getStringExtra("prompt"), intent.getIntExtra("position", 0));
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, new IntentFilter("com.skythinker.gptassistant.TAB_EDIT"));

        (findViewById(R.id.bt_add_tab)).setOnClickListener(view -> {
            startEditTab("", "", tabDataList.size());
        });

        ((EditText) findViewById(R.id.et_openai_host_conf)).setText(GlobalDataHolder.getGptApiHost());
        ((EditText) findViewById(R.id.et_openai_host_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                String host = editable.toString().trim();
                if(!host.isEmpty()) { // 自动补全URL
                    if(!host.startsWith("http://") && !host.startsWith("https://")) {
                        host = "https://" + host;
                    }
                    if(!host.endsWith("/")) {
                        host += "/";
                    }
                }
                GlobalDataHolder.saveGptApiInfo(host, GlobalDataHolder.getGptApiKey(), GlobalDataHolder.getGptModel(), GlobalDataHolder.getCustomModels());
            }
        });

        ((EditText) findViewById(R.id.et_openai_key_conf)).setText(GlobalDataHolder.getGptApiKey());
        ((EditText) findViewById(R.id.et_openai_key_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), editable.toString().trim(), GlobalDataHolder.getGptModel(), GlobalDataHolder.getCustomModels());
            }
        });

        List<String> models = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.models))); // 内置模型列表
        models.addAll(GlobalDataHolder.getCustomModels()); // 自定义模型列表
        ArrayAdapter<String> modelsAdapter = new ArrayAdapter<String>(this, R.layout.model_spinner_item, models) { // 设置Spinner样式和列表数据
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) { // 设置选中/未选中的选项样式
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                if(((Spinner) findViewById(R.id.sp_model_conf)).getSelectedItemPosition() == position) { // 选中项
                    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                } else { // 未选中项
                    tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                }
                return tv;
            }
        };
        modelsAdapter.setDropDownViewResource(R.layout.model_spinner_dropdown_item); // 设置下拉选项样式
        ((Spinner) findViewById(R.id.sp_model_conf)).setAdapter(modelsAdapter);
        ((Spinner) findViewById(R.id.sp_model_conf)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) { // 有选项被选中
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey(), adapterView.getItemAtPosition(i).toString(), GlobalDataHolder.getCustomModels());
                modelsAdapter.notifyDataSetChanged();
            }
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        for(int i = 0; i < modelsAdapter.getCount(); i++) { // 根据当前模型名查找选中的选项
            if(modelsAdapter.getItem(i).equals(GlobalDataHolder.getGptModel())) {
                ((Spinner) findViewById(R.id.sp_model_conf)).setSelection(i);
                break;
            }
            if(i == modelsAdapter.getCount() - 1) { // 没有找到当前模型名，默认选中第一项
                ((Spinner) findViewById(R.id.sp_model_conf)).setSelection(0);
            }
        }

        ((EditText) findViewById(R.id.et_custom_model_conf)).setText(String.join(";", GlobalDataHolder.getCustomModels()));
        ((EditText) findViewById(R.id.et_custom_model_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) { // 将输入的自定义模型转为列表存储
                List<String> modelList = new ArrayList<>(Arrays.asList(editable.toString().trim().split(";")));
                modelList.removeIf(String::isEmpty);
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey(), GlobalDataHolder.getGptModel(), modelList);
                models.clear();
                models.addAll(Arrays.asList(getResources().getStringArray(R.array.models)));
                models.addAll(modelList);
                modelsAdapter.notifyDataSetChanged();
            }
        });

        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setChecked(GlobalDataHolder.getAsrUseBaidu());
        setBaiduAsrItemHidden(!GlobalDataHolder.getAsrUseBaidu());
        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveAsrInfo(checked, GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            setBaiduAsrItemHidden(!checked);
        });

        ((EditText) findViewById(R.id.et_asr_app_id_conf)).setText(GlobalDataHolder.getAsrAppId());
        ((EditText) findViewById(R.id.et_asr_app_id_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), editable.toString().trim(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_api_key_conf)).setText(GlobalDataHolder.getAsrApiKey());
        ((EditText) findViewById(R.id.et_asr_api_key_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), GlobalDataHolder.getAsrAppId(), editable.toString().trim(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_secret_conf)).setText(GlobalDataHolder.getAsrSecretKey());
        ((EditText) findViewById(R.id.et_asr_secret_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), editable.toString().trim(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((Switch) findViewById(R.id.sw_asr_real_time_conf)).setChecked(GlobalDataHolder.getAsrUseRealTime());
        ((Switch) findViewById(R.id.sw_asr_real_time_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), checked);
        });

        ((Switch) findViewById(R.id.sw_check_access_conf)).setChecked(GlobalDataHolder.getCheckAccessOnStart());
        ((Switch) findViewById(R.id.sw_check_access_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveStartUpSetting(checked);
        });

        ((Switch) findViewById(R.id.sw_tts_enable_conf)).setChecked(GlobalDataHolder.getDefaultEnableTts());
        ((Switch) findViewById(R.id.sw_tts_enable_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveTtsSetting(checked);
        });

        ((Switch) findViewById(R.id.sw_def_enable_multi_chat_conf)).setChecked(GlobalDataHolder.getDefaultEnableMultiChat());
        ((Switch) findViewById(R.id.sw_def_enable_multi_chat_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveMultiChatSetting(checked);
        });

        ((Switch) findViewById(R.id.sw_remember_tab_conf)).setChecked(GlobalDataHolder.getSelectedTab() != -1);
        ((Switch) findViewById(R.id.sw_remember_tab_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked && GlobalDataHolder.getSelectedTab() == -1) {
                GlobalDataHolder.saveSelectedTab(0);
            } else if(!checked && GlobalDataHolder.getSelectedTab() != -1) {
                GlobalDataHolder.saveSelectedTab(-1);
            }
        });

        ((Switch) findViewById(R.id.sw_auto_save_history_conf)).setChecked(GlobalDataHolder.getAutoSaveHistory());
        ((Switch) findViewById(R.id.sw_auto_save_history_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveHistorySetting(checked);
        });

        ((Switch) findViewById(R.id.sw_limit_vision_size_conf)).setChecked(GlobalDataHolder.getLimitVisionSize());
        ((Switch) findViewById(R.id.sw_limit_vision_size_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveVisionSetting(checked);
        });

        (findViewById(R.id.tv_set_tts_conf)).setOnClickListener(view -> {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent); // 跳转到系统的TTS设置界面
        });

        ((Switch) findViewById(R.id.sw_enable_internet_conf)).setChecked(GlobalDataHolder.getEnableInternetAccess());
        setInternetItemHidden(!GlobalDataHolder.getEnableInternetAccess());
        ((Switch) findViewById(R.id.sw_enable_internet_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked)
                Toast.makeText(this, "提醒：访问网络时会大幅增加token用量，GPT4请谨慎使用！", Toast.LENGTH_LONG).show();
            GlobalDataHolder.saveFunctionSetting(checked, GlobalDataHolder.getWebMaxCharCount(), GlobalDataHolder.getOnlyLatestWebResult());
            setInternetItemHidden(!checked);
        });

        ((EditText) findViewById(R.id.et_web_max_char_conf)).setText(String.valueOf(GlobalDataHolder.getWebMaxCharCount()));
        ((EditText) findViewById(R.id.et_web_max_char_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                try {
                    int maxChars = 2000;
                    if (!editable.toString().isEmpty())
                        maxChars = Integer.parseInt(editable.toString());
                    GlobalDataHolder.saveFunctionSetting(GlobalDataHolder.getEnableInternetAccess(), maxChars, GlobalDataHolder.getOnlyLatestWebResult());
                } catch (NumberFormatException e) {
                    ((EditText) findViewById(R.id.et_web_max_char_conf)).setText(String.valueOf(GlobalDataHolder.getWebMaxCharCount()));
                }
            }
        });

        ((Switch) findViewById(R.id.sw_only_latest_web_conf)).setChecked(GlobalDataHolder.getOnlyLatestWebResult());
        ((Switch) findViewById(R.id.sw_only_latest_web_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveFunctionSetting(GlobalDataHolder.getEnableInternetAccess(), GlobalDataHolder.getWebMaxCharCount(), checked);
        });

        (findViewById(R.id.tv_set_access_conf)).setOnClickListener(view -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        (findViewById(R.id.tv_help_conf)).setOnClickListener(view -> { // 弹出帮助对话框
            new ConfirmDialog(this)
                    .setTitle("帮助")
                    .setContent(getResources().getString(R.string.help_msg))
                    .setContentAlignment(TextView.TEXT_ALIGNMENT_TEXT_START)
                    .setOkButtonVisibility(View.GONE)
                    .setCancelText("返回")
                    .show();
        });

        new Thread(() -> { // 通过Gitee检查更新
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://gitee.com/api/v5/repos/skythinker/gpt-assistant-android/releases/latest")
                    .build();
            try {
                Response response = client.newCall(request).execute();
                String json = response.body().string();
                JSONObject jsonObject = new JSONObject(json);
                String version = jsonObject.getString("tag_name").replace("v", "");
                if(version.equals(BuildConfig.VERSION_NAME)){
                    handler.post(() -> {
                        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format("当前版本：%s · 已是最新版本", version));
                    });
                } else {
                    handler.post(() -> {
                        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format("有可用更新：%s -> %s", BuildConfig.VERSION_NAME, version));
                    });
                }
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        }).start();

        ((LinearLayout) findViewById(R.id.tv_check_update_conf).getParent()).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse("https://gitee.com/skythinker/gpt-assistant-android/releases");
            intent.setData(content_url);
            startActivity(intent); // 用默认浏览器打开Releases页面
        });

        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format("当前版本：%s", BuildConfig.VERSION_NAME));

        ((LinearLayout) findViewById(R.id.tv_homepage_conf).getParent()).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse("https://gitee.com/skythinker/gpt-assistant-android");
            intent.setData(content_url);
            startActivity(intent); // 用默认浏览器打开Gitee主页
        });

        (findViewById(R.id.bt_back_conf)).setOnClickListener(view -> {
            finish();
        });
    }

    // 设置百度语音识别子配置项是否隐藏
    private void setBaiduAsrItemHidden(boolean hidden) {
        ((LinearLayout) findViewById(R.id.et_asr_app_id_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.et_asr_api_key_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.et_asr_secret_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.sw_asr_real_time_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    // 设置联网子配置项是否隐藏
    private void setInternetItemHidden(boolean hidden) {
        ((LinearLayout) findViewById(R.id.et_web_max_char_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.sw_only_latest_web_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    // 进入模板编辑页面
    public void startEditTab(String title, String prompt, int position) {
        Intent intent = new Intent(this, TabDetailConfActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("prompt", prompt);
        startActivityForResult(intent, position);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) { // 处理模板编辑页面返回的数据
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK && data.hasExtra("ok")) {
            if(data.getBooleanExtra("ok", false)) {
                String title = data.getStringExtra("title");
                String prompt = data.getStringExtra("prompt");
                if(requestCode == GlobalDataHolder.getTabDataList().size()) {
                    GlobalDataHolder.getTabDataList().add(new PromptTabData(title, prompt));
                    adapter.notifyItemInserted(GlobalDataHolder.getTabDataList().size() - 1);
                    GlobalDataHolder.saveTabDataList();
                } else {
                    GlobalDataHolder.getTabDataList().get(requestCode).setTitle(title);
                    GlobalDataHolder.getTabDataList().get(requestCode).setPrompt(prompt);
                    adapter.notifyItemChanged(requestCode);
                    GlobalDataHolder.saveTabDataList();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}