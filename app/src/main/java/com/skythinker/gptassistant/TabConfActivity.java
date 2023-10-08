package com.skythinker.gptassistant;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TabConfActivity extends Activity {

    private RecyclerView rvTabList;
    private TabConfListAdapter adapter;
    private BroadcastReceiver localReceiver;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_conf);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        List<PromptTabData> tabDataList = GlobalDataHolder.getTabDataList();
        rvTabList = findViewById(R.id.rv_tab_conf_list);
        rvTabList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TabConfListAdapter(this);
        rvTabList.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder dragged, RecyclerView.ViewHolder target) {
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
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                tabDataList.remove(viewHolder.getAdapterPosition());
                adapter.notifyItemRemoved(viewHolder.getAdapterPosition());
                GlobalDataHolder.saveTabDataList();
            }
        });
        helper.attachToRecyclerView(rvTabList);

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
        ((EditText) findViewById(R.id.et_openai_host_conf)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void afterTextChanged(Editable editable) {
                String host = editable.toString().trim();
                if(!host.isEmpty()) {
                    if(!host.startsWith("http://") && !host.startsWith("https://")) {
                        host = "https://" + host;
                    }
                    if(!host.endsWith("/")) {
                        host += "/";
                    }
                }
                GlobalDataHolder.saveGptApiInfo(host, GlobalDataHolder.getGptApiKey(), GlobalDataHolder.getGpt4Enable());
            }
        });

        ((EditText) findViewById(R.id.et_openai_key_conf)).setText(GlobalDataHolder.getGptApiKey());
        ((EditText) findViewById(R.id.et_openai_key_conf)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), editable.toString().trim(), GlobalDataHolder.getGpt4Enable());
            }
        });

        ((Switch) findViewById(R.id.sw_gpt4_enable_conf)).setChecked(GlobalDataHolder.getGpt4Enable());
        ((Switch) findViewById(R.id.sw_gpt4_enable_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey(), checked);
        });

        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setChecked(GlobalDataHolder.getAsrUseBaidu());
        setBaiduAsrItemHidden(!GlobalDataHolder.getAsrUseBaidu());
        ((Switch) findViewById(R.id.sw_asr_use_baidu_conf)).setOnCheckedChangeListener((compoundButton, checked) -> {
            GlobalDataHolder.saveAsrInfo(checked, GlobalDataHolder.getAsrAppId(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            setBaiduAsrItemHidden(!checked);
        });

        ((EditText) findViewById(R.id.et_asr_app_id_conf)).setText(GlobalDataHolder.getAsrAppId());
        ((EditText) findViewById(R.id.et_asr_app_id_conf)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), editable.toString().trim(), GlobalDataHolder.getAsrApiKey(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_api_key_conf)).setText(GlobalDataHolder.getAsrApiKey());
        ((EditText) findViewById(R.id.et_asr_api_key_conf)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void afterTextChanged(Editable editable) {
                GlobalDataHolder.saveAsrInfo(GlobalDataHolder.getAsrUseBaidu(), GlobalDataHolder.getAsrAppId(), editable.toString().trim(), GlobalDataHolder.getAsrSecretKey(), GlobalDataHolder.getAsrUseRealTime());
            }
        });

        ((EditText) findViewById(R.id.et_asr_secret_conf)).setText(GlobalDataHolder.getAsrSecretKey());
        ((EditText) findViewById(R.id.et_asr_secret_conf)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
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

        (findViewById(R.id.tv_set_tts_conf)).setOnClickListener(view -> {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        (findViewById(R.id.tv_set_access_conf)).setOnClickListener(view -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        (findViewById(R.id.tv_help_conf)).setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = LayoutInflater.from(this);
            View v = inflater.inflate(R.layout.help_dialog, null);
            Button btOk = v.findViewById(R.id.bt_help_dialog_ok);
            final Dialog dialog = builder.create();
            dialog.show();
            dialog.getWindow().setContentView(v);
            btOk.setOnClickListener(btView -> {
                dialog.dismiss();
            });
        });

        (findViewById(R.id.tv_check_update_conf)).setOnClickListener(view -> {
            new Thread(() -> {
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
                            Toast.makeText(this, String.format("已是最新版本 (v%s)", version), Toast.LENGTH_LONG).show();
                        });
                    } else {
                        handler.post(() -> {
                            Toast.makeText(this, String.format("发现新版本 v%s (当前版本 v%s)", version, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG).show();
                            Intent intent = new Intent();
                            intent.setAction("android.intent.action.VIEW");
                            Uri content_url = Uri.parse("https://gitee.com/skythinker/gpt-assistant-android/releases");
                            intent.setData(content_url);
                            startActivity(intent);
                        });
                    }
                } catch (JSONException | IOException e) {
                    handler.post(() -> {
                        Toast.makeText(this, "检查更新失败", Toast.LENGTH_SHORT).show();
                    });
                    e.printStackTrace();
                }
            }).start();
        });

        (findViewById(R.id.tv_homepage_conf)).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse("https://gitee.com/skythinker/gpt-assistant-android");
            intent.setData(content_url);
            startActivity(intent);
        });

        (findViewById(R.id.bt_back_conf)).setOnClickListener(view -> {
            finish();
        });
    }

    private void setBaiduAsrItemHidden(boolean hidden) {
        ((LinearLayout) findViewById(R.id.et_asr_app_id_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.et_asr_api_key_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.et_asr_secret_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
        ((LinearLayout) findViewById(R.id.sw_asr_real_time_conf).getParent()).setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    public void startEditTab(String title, String prompt, int position) {
        Intent intent = new Intent(this, TabDetailConfActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("prompt", prompt);
        startActivityForResult(intent, position);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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