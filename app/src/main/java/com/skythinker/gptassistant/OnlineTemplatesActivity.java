package com.skythinker.gptassistant;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineTemplatesActivity extends Activity {

    private class OnlineTemplate {
        public String tag, content, title, description, pageUrl;
    }

    private class OnlineTemplateListAdapter extends RecyclerView.Adapter<OnlineTemplatesActivity.OnlineTemplateListAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.oline_temp_list_item, parent, false);
            return new OnlineTemplatesActivity.OnlineTemplateListAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnlineTemplate onlineTemplate = onlineTemplates.get(position);
            holder.tvTitle.setText(onlineTemplate.title);
            holder.tvDesc.setText(onlineTemplate.description.replaceAll("\n", " "));
        }

        @Override
        public int getItemCount() {
            return onlineTemplates.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private TextView tvTitle, tvDesc;
            private LinearLayout llOuter;
            public ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_online_temp_item_title);
                tvDesc = itemView.findViewById(R.id.tv_online_temp_item_desc);
                llOuter = itemView.findViewById(R.id.ll_online_temp_item_outer);
                llOuter.setOnClickListener((view) -> {
                    OnlineTemplate onlineTemplate = onlineTemplates.get(getAdapterPosition());
                    if(onlineTemplate.pageUrl != null) {
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse(onlineTemplate.pageUrl);
                        intent.setData(content_url);
                        startActivity(intent);
                    }
                });
            }
        }
    }

    List<OnlineTemplate> onlineTemplates = new ArrayList<>();
    RecyclerView rvTemplateList;
    OnlineTemplateListAdapter templateListAdapter;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_templates);

        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        rvTemplateList = findViewById(R.id.rv_online_temp_list);
        rvTemplateList.setLayoutManager(new LinearLayoutManager(this));
        templateListAdapter = new OnlineTemplateListAdapter();
        rvTemplateList.setAdapter(templateListAdapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) { // 右滑添加
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不支持上下拖动
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition(); // 获取滑动的item的position
                templateListAdapter.notifyDataSetChanged();
                Intent intent = new Intent();
                intent.putExtra("tag", onlineTemplates.get(position).tag);
                intent.putExtra("content", onlineTemplates.get(position).content);
                setResult(RESULT_OK, intent);
                finish();
            }
        }).attachToRecyclerView(rvTemplateList);

        (findViewById(R.id.bt_online_temp_back)).setOnClickListener((view) -> {
            finish();
        });

        (findViewById(R.id.bt_online_temp_share)).setOnClickListener((view) -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse(getString(R.string.new_discussion_url));
            intent.setData(content_url);
            startActivity(intent);
        });

        (findViewById(R.id.bt_online_temp_github)).setOnClickListener((view) -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse(getString(R.string.discussions_url));
            intent.setData(content_url);
            startActivity(intent);
        });

        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getString(R.string.shared_templates_url))
                    .build();
            try {
                Response response = client.newCall(request).execute();
                JSONArray jsonArray = new JSONArray(response.body().string());
                Log.d("OnlineTemplatesActivity", jsonArray.toString());
                onlineTemplates.clear();
                for(int i = 0; i < jsonArray.size(); i++){
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    OnlineTemplate onlineTemplate = new OnlineTemplate();
                    onlineTemplate.tag = jsonObject.getStr("tag");
                    onlineTemplate.content = jsonObject.getStr("content");
                    onlineTemplate.title = jsonObject.getStr("title");
                    onlineTemplate.description = jsonObject.getStr("description");
                    onlineTemplate.pageUrl = jsonObject.getStr("page");
                    onlineTemplates.add(onlineTemplate);
                }
                runOnUiThread(() -> {
                    findViewById(R.id.tv_online_temp_status).setVisibility(View.GONE);
                    templateListAdapter.notifyDataSetChanged();
                });
            } catch (IOException e) {
                ((TextView) findViewById(R.id.tv_online_temp_status)).setText(R.string.text_online_temp_error);
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}