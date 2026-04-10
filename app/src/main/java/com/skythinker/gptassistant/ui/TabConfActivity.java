package com.skythinker.gptassistant.ui;

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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.skythinker.gptassistant.BuildConfig;
import com.skythinker.gptassistant.data.GlobalDataHolder;
import com.skythinker.gptassistant.data.ModelCatalog;
import com.skythinker.gptassistant.tool.GlobalUtils;
import com.skythinker.gptassistant.data.PromptTabData;
import com.skythinker.gptassistant.R;

import org.json.JSONArray;
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

    private static final int REQUEST_DATA_TRANSFER = 1000;
    private static final int REQUEST_CUSTOM_MODEL_CONF = 1001;

    private RecyclerView rvTabList;
    private TabConfListAdapter adapter;
    private BroadcastReceiver localReceiver;
    private Handler handler = new Handler();
    private ArrayAdapter<ModelCatalog.ModelOption> modelsAdapter;

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
                GlobalDataHolder.saveGptApiInfo(host, GlobalDataHolder.getGptApiKey(), GlobalDataHolder.getGptModel(), GlobalDataHolder.getCustomModelProfiles());
            }
        });

        ((EditText) findViewById(R.id.et_openai_key_conf)).setText(GlobalDataHolder.getGptApiKey());
        ((EditText) findViewById(R.id.et_openai_key_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), editable.toString().trim(), GlobalDataHolder.getGptModel(), GlobalDataHolder.getCustomModelProfiles());
            }
        });

        updateModelSpinner();
        updateCustomModelSummary();

        findViewById(R.id.ll_custom_model_conf_entry).setOnClickListener(view -> {
            startActivityForResult(new Intent(TabConfActivity.this, CustomModelConfActivity.class), REQUEST_CUSTOM_MODEL_CONF);
        });

        ((EditText) findViewById(R.id.et_temperature_conf)).setText(String.valueOf(GlobalDataHolder.getGptTemperature()));
        ((EditText) findViewById(R.id.et_temperature_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                try {
                    if (!editable.toString().isEmpty()) {
                        float temperature = Float.parseFloat(editable.toString().trim());
                        if (temperature >= 0 && temperature <= 2)
                            GlobalDataHolder.saveModelParams(temperature, GlobalDataHolder.getGptMaxContextNum());
                    }
                } catch (NumberFormatException e) {
                    ((EditText) findViewById(R.id.et_temperature_conf)).setText(String.valueOf(GlobalDataHolder.getGptTemperature()));
                }
            }
        });

        ((EditText) findViewById(R.id.et_context_num_conf)).setText(String.valueOf(GlobalDataHolder.getGptMaxContextNum()));
        ((EditText) findViewById(R.id.et_context_num_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                try {
                    if (!editable.toString().isEmpty()) {
                        int contextNum = Integer.parseInt(editable.toString().trim());
                        GlobalDataHolder.saveModelParams(GlobalDataHolder.getGptTemperature(), contextNum);
                    }
                } catch (NumberFormatException e) {
                    ((EditText) findViewById(R.id.et_context_num_conf)).setText(String.valueOf(GlobalDataHolder.getGptMaxContextNum()));
                }
            }
        });

        ((LinearLayout) findViewById(R.id.bt_asr_help).getParent()).setOnClickListener(view -> {
            new ConfirmDialog(this)
                    .setTitle(getString(R.string.dialog_asr_select_help_title))
                    .setContent(getString(R.string.dialog_asr_select_help))
                    .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                    .setOkButtonVisibility(View.GONE)
                    .show();
        });

        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setChecked(GlobalDataHolder.getAsrUseBaidu());
        setBaiduAsrItemHidden(!GlobalDataHolder.getAsrUseBaidu());
        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked) {
                ((Switch) findViewById(R.id.sw_asr_use_google_conf)).setChecked(false);
                ((Switch) findViewById(R.id.sw_asr_use_whisper_conf)).setChecked(false);
            }
            GlobalDataHolder.saveAsrSelection(GlobalDataHolder.getAsrUseWhisper(), checked, GlobalDataHolder.getAsrUseGoogle());
            setBaiduAsrItemHidden(!checked);
        });

        ((EditText) findViewById(R.id.et_asr_app_id_conf)).setText(GlobalDataHolder.getAsrAppId());
        ((EditText) findViewById(R.id.et_asr_app_id_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveBaiduAsrInfo(editable.toString().trim(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_api_key_conf)).setText(GlobalDataHolder.getAsrApiKey());
        ((EditText) findViewById(R.id.et_asr_api_key_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveBaiduAsrInfo(GlobalDataHolder.getAsrAppId(), editable.toString().trim(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_secret_conf)).setText(GlobalDataHolder.getAsrSecretKey());
        ((EditText) findViewById(R.id.et_asr_secret_conf)).addTextChangedListener(new CustomTextWatcher() {
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveBaiduAsrInfo(GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), editable.toString().trim(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((Switch) findViewById(R.id.sw_asr_real_time_conf)).setChecked(GlobalDataHolder.getAsrUseRealTime());
        ((Switch) findViewById(R.id.sw_asr_real_time_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveBaiduAsrInfo(GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), checked);
        });

        ((Switch) findViewById(R.id.sw_asr_use_whisper_conf)).setChecked(GlobalDataHolder.getAsrUseWhisper());
        ((Switch) findViewById(R.id.sw_asr_use_whisper_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked) {
                ((Switch) findViewById(R.id.sw_asr_use_google_conf)).setChecked(false);
                ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setChecked(false);
            }
            GlobalDataHolder.saveAsrSelection(checked, GlobalDataHolder.getAsrUseBaidu(), GlobalDataHolder.getAsrUseGoogle());
        });

        ((Switch) findViewById(R.id.sw_asr_use_google_conf)).setChecked(GlobalDataHolder.getAsrUseGoogle());
        ((Switch) findViewById(R.id.sw_asr_use_google_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked) {
                try { // 检查是否安装了 Google 搜索
                    getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", PackageManager.GET_META_DATA);
                    ((Switch) findViewById(R.id.sw_asr_use_whisper_conf)).setChecked(false);
                    ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setChecked(false);
                } catch (PackageManager.NameNotFoundException e) { // 未安装 Google 搜索，提示用户安装
                    new ConfirmDialog(this)
                            .setContent(getString(R.string.dialog_download_google))
                            .setOnConfirmListener(() -> {
                                try{
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.googlequicksearchbox")));
                                } catch (Exception e1) {
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")));
                                }
                            }).show();
                    ((Switch) findViewById(R.id.sw_asr_use_google_conf)).setChecked(false);
                    return;
                }
            }
            GlobalDataHolder.saveAsrSelection(GlobalDataHolder.getAsrUseWhisper(), GlobalDataHolder.getAsrUseBaidu(), checked);
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

        ((Switch) findViewById(R.id.sw_use_gitee_conf)).setChecked(GlobalDataHolder.getUseGitee());
        ((Switch) findViewById(R.id.sw_use_gitee_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveOnlineResourceSetting(checked);
        });

        (findViewById(R.id.tv_set_tts_conf)).setOnClickListener(view -> {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent); // 跳转到系统的TTS设置界面
        });

        // 主界面按钮管理独立成子页面，避免把当前设置页继续堆得更复杂。
        ((View) findViewById(R.id.tv_main_action_conf).getParent()).setOnClickListener(view -> {
            startActivity(new Intent(TabConfActivity.this, MainActionConfActivity.class));
        });

        ((View) findViewById(R.id.tv_data_transfer_conf).getParent()).setOnClickListener(view -> {
            startActivityForResult(new Intent(TabConfActivity.this, DataTransferActivity.class), REQUEST_DATA_TRANSFER);
        });

        ((Switch) findViewById(R.id.sw_enable_internet_conf)).setChecked(GlobalDataHolder.getEnableInternetAccess());
//        setInternetItemHidden(!GlobalDataHolder.getEnableInternetAccess());
        ((Switch) findViewById(R.id.sw_enable_internet_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            if(checked)
                Toast.makeText(this, R.string.toast_enable_network, Toast.LENGTH_LONG).show();
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
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        (findViewById(R.id.tv_help_conf)).setOnClickListener(view -> { // 弹出帮助对话框
            new ConfirmDialog(this)
                    .setTitle(getString(R.string.dialog_help_title))
                    .setContentAlignment(TextView.TEXT_ALIGNMENT_TEXT_START)
                    .setMarkdownContent(getString(R.string.help_msg))
                    .setCancelText(getString(R.string.dialog_help_cancel))
                    .setOkText(getString(R.string.dialog_help_homepage))
                    .setOnConfirmListener(() -> {
                        WebViewActivity.openUrl(this, getString(R.string.text_homepage_title), getString(GlobalDataHolder.getUseGitee() ? R.string.homepage_url_gitee : R.string.homepage_url_github));
                    })
                    .show();
        });

        new Thread(() -> { // 通过Gitee/GitHub检查更新
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getString(GlobalDataHolder.getUseGitee() ? R.string.check_update_url_gitee : R.string.check_update_url_github))
                    .build();
            try {
                Response response = client.newCall(request).execute();
                String json = response.body().string();
                JSONObject jsonObject = new JSONObject(json);
                String version = jsonObject.getString("tag_name").replace("v", "");
                if(version.equals(BuildConfig.VERSION_NAME)){
                    handler.post(() -> {
                        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format(getString(R.string.format_version_latest), version));
                    });
                } else {
                    handler.post(() -> {
                        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format(getString(R.string.format_version_available), BuildConfig.VERSION_NAME, version));
                    });
                }
                GlobalDataHolder.saveUpdateSetting(version);
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        }).start();

        ((LinearLayout) findViewById(R.id.tv_check_update_conf).getParent()).setOnClickListener(view -> {
            new Thread(() -> { // 通过Gitee/GitHub检查更新
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(getString(GlobalDataHolder.getUseGitee() ? R.string.releases_raw_url_gitee : R.string.releases_raw_url_github))
                        .build();
                try {
                    Response response = client.newCall(request).execute();
                    String json = response.body().string();
                    JSONArray jsonArray = new JSONArray(json);
                    String releaseMarkdown = "";
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject object = jsonArray.getJSONObject(i);
                        String tagName = object.getString("tag_name");
                        String description = object.getString("body");
                        String apkName = object.getJSONArray("assets").getJSONObject(0).getString("name");
                        String downloadUrl = object.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
                        if(i == 0 && !tagName.equals("v" + BuildConfig.VERSION_NAME)) { // update available
                            releaseMarkdown += String.format(getString(R.string.text_download_latest), downloadUrl) + "\n\n---\n\n";
                        }
                        releaseMarkdown += "### " + tagName + "\n\n" + description + "\n\n[" + apkName + "](" + downloadUrl + ")\n\n---\n\n";
                    }
                    Intent intent = new Intent(TabConfActivity.this, MarkdownPreviewActivity.class);
                    intent.putExtra("title", getString(R.string.text_releases_title));
                    intent.putExtra("markdown", releaseMarkdown);
                    intent.putExtra("browser_url", getString(GlobalDataHolder.getUseGitee() ? R.string.release_url_gitee : R.string.release_url_github));
                    startActivity(intent);
                } catch (JSONException | IOException e) {
                    handler.post(() -> GlobalUtils.showToast(TabConfActivity.this, getString(R.string.toast_get_releases_failed), true));
                    e.printStackTrace();
                }
            }).start();
        });

        ((TextView) findViewById(R.id.tv_version_conf)).setText(String.format(getString(R.string.format_version_normal), BuildConfig.VERSION_NAME));

        ((LinearLayout) findViewById(R.id.tv_homepage_conf).getParent()).setOnClickListener(view -> {
            WebViewActivity.openUrl(this, getString(R.string.text_homepage_title), getString(GlobalDataHolder.getUseGitee() ? R.string.homepage_url_gitee : R.string.homepage_url_github));
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
        if(requestCode == REQUEST_DATA_TRANSFER) {
            if(resultCode == RESULT_OK) {
                restartSelf();
            }
            return;
        }
        if(requestCode == REQUEST_CUSTOM_MODEL_CONF) {
            if(resultCode == RESULT_OK) {
                restartSelf();
            }
            return;
        }

        if(resultCode == RESULT_OK && data != null && data.hasExtra("ok")) {
            if(data.getBooleanExtra("ok", false)) {
                String title = data.getStringExtra("title");
                String prompt = data.getStringExtra("prompt");
                boolean fromOnline = data.getBooleanExtra("fromOnline", false);
                if(requestCode == GlobalDataHolder.getTabDataList().size() || fromOnline) {
                    GlobalDataHolder.getTabDataList().add(new PromptTabData(title, prompt));
                    adapter.notifyItemInserted(GlobalDataHolder.getTabDataList().size() - 1);
                } else {
                    GlobalDataHolder.getTabDataList().get(requestCode).setTitle(title);
                    GlobalDataHolder.getTabDataList().get(requestCode).setPrompt(prompt);
                    adapter.notifyItemChanged(requestCode);
                }
                GlobalDataHolder.saveTabDataList();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
    }

    // 无动画重启当前设置页，便于切换语言等场景立即刷新。
    private void restartSelf() {
        Intent intent = new Intent(getIntent());
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }

    // 刷新设置页的模型下拉框并同步当前选择。
    private void updateModelSpinner() {
        Spinner spinner = findViewById(R.id.sp_model_conf);
        List<ModelCatalog.ModelOption> modelOptions = ModelCatalog.buildModelOptions(this, GlobalDataHolder.getGptModel());
        modelsAdapter = new ArrayAdapter<ModelCatalog.ModelOption>(this, R.layout.model_spinner_item, modelOptions) {
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                if(spinner.getSelectedItemPosition() == position) {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                } else {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                }
                return tv;
            }
        };
        modelsAdapter.setDropDownViewResource(R.layout.model_spinner_dropdown_item);
        spinner.setAdapter(modelsAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            // 保存设置页当前选中的默认模型。
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                ModelCatalog.ModelOption option = modelsAdapter.getItem(position);
                if(option == null) {
                    return;
                }
                GlobalDataHolder.saveGptApiInfo(
                        GlobalDataHolder.getGptApiHost(),
                        GlobalDataHolder.getGptApiKey(),
                        option.modelId,
                        GlobalDataHolder.getCustomModelProfiles()
                );
                modelsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        for(int i = 0; i < modelsAdapter.getCount(); i++) {
            ModelCatalog.ModelOption option = modelsAdapter.getItem(i);
            if(option != null && option.modelId.equals(GlobalDataHolder.getGptModel())) {
                spinner.setSelection(i);
                return;
            }
        }
        if(modelsAdapter.getCount() > 0) {
            spinner.setSelection(0);
        }
    }

    // 更新自定义模型配置入口的摘要文案。
    private void updateCustomModelSummary() {
        int customModelCount = GlobalDataHolder.getCustomModelProfiles().size();
        ((TextView) findViewById(R.id.tv_custom_model_conf_summary))
                .setText(getString(R.string.conf_custom_model_summary, customModelCount));
    }
}
