package com.skythinker.gptassistant.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.CustomModelProfile;
import com.skythinker.gptassistant.data.GlobalDataHolder;
import com.skythinker.gptassistant.data.ModelCatalog;
import com.skythinker.gptassistant.ui.FetchModelSelectAdapter.FetchModelItem;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.models.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public class CustomModelConfActivity extends Activity {

    private static final int REQUEST_EDIT_MODEL = 1000;

    private final List<CustomModelProfile> modelProfiles = new ArrayList<>();
    private CustomModelConfAdapter adapter;
    private boolean isFetchingModels = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_model_conf);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        for(CustomModelProfile profile : GlobalDataHolder.getCustomModelProfiles()) {
            modelProfiles.add(profile.copy());
        }

        RecyclerView rvModelList = findViewById(R.id.rv_custom_model_conf);
        rvModelList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomModelConfAdapter(this, modelProfiles, this::startEditModel);
        rvModelList.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                if(fromPosition < 0 || toPosition < 0) {
                    return false;
                }
                Collections.swap(modelProfiles, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                saveProfiles();
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if(position < 0 || position >= modelProfiles.size()) {
                    return;
                }
                modelProfiles.remove(position);
                adapter.notifyItemRemoved(position);
                saveProfiles();
            }
        }).attachToRecyclerView(rvModelList);

        findViewById(R.id.bt_custom_model_conf_back).setOnClickListener(view -> finish());
        findViewById(R.id.tv_custom_model_conf_fetch).setOnClickListener(view -> fetchRemoteModels());
        findViewById(R.id.tv_custom_model_conf_add).setOnClickListener(view -> startEditModel(-1));
    }

    private void startEditModel(int position) {
        Intent intent = new Intent(this, CustomModelDetailActivity.class);
        intent.putExtra("position", position);
        if(position >= 0 && position < modelProfiles.size()) {
            CustomModelProfile profile = modelProfiles.get(position);
            intent.putExtra("model_id", profile.id);
            intent.putExtra("model_name", profile.name);
            intent.putExtra("vision", profile.hasCapability(ModelCatalog.CAPABILITY_VISION));
            intent.putExtra("tool", profile.hasCapability(ModelCatalog.CAPABILITY_TOOL));
            intent.putExtra("thinking", profile.hasCapability(ModelCatalog.CAPABILITY_THINKING));
        }
        intent.putStringArrayListExtra("existing_model_ids", buildExistingModelIds(position));
        startActivityForResult(intent, REQUEST_EDIT_MODEL);
    }

    private ArrayList<String> buildExistingModelIds(int excludedPosition) {
        ArrayList<String> modelIds = new ArrayList<>();
        for(int i = 0; i < modelProfiles.size(); i++) {
            if(i != excludedPosition) {
                modelIds.add(modelProfiles.get(i).id);
            }
        }
        return modelIds;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_EDIT_MODEL || resultCode != RESULT_OK || data == null) {
            return;
        }

        CustomModelProfile profile = CustomModelDetailActivity.parseResult(data);
        int position = data.getIntExtra("position", -1);
        if(profile.id.isEmpty()) {
            Toast.makeText(this, R.string.custom_model_toast_id_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if(position >= 0 && position < modelProfiles.size()) {
            modelProfiles.set(position, profile);
            adapter.notifyItemChanged(position);
        } else {
            modelProfiles.add(profile);
            adapter.notifyItemInserted(modelProfiles.size() - 1);
        }
        saveProfiles();
    }

    private void saveProfiles() {
        // 只更新模型配置，其他设置沿用当前全局值。
        GlobalDataHolder.saveGptApiInfo(
                GlobalDataHolder.getGptApiHost(),
                GlobalDataHolder.getGptApiKey(),
                GlobalDataHolder.getGptModel(),
                modelProfiles
        );
        setResult(RESULT_OK);
    }

    private void fetchRemoteModels() {
        if(isFetchingModels) {
            return;
        }

        String host = GlobalDataHolder.getGptApiHost().trim();
        String apiKey = GlobalDataHolder.getGptApiKey().trim();
        if(host.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, R.string.custom_model_fetch_missing_config, Toast.LENGTH_SHORT).show();
            return;
        }

        isFetchingModels = true;
        Toast.makeText(this, R.string.custom_model_fetch_loading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> remoteModelIds = requestRemoteModelIds(host, apiKey);
                runOnUiThread(() -> {
                    isFetchingModels = false;
                    if(remoteModelIds.size() == 0) {
                        Toast.makeText(this, R.string.custom_model_fetch_empty, Toast.LENGTH_SHORT).show();
                    } else {
                        showFetchModelDialog(remoteModelIds);
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if(message == null || message.trim().isEmpty()) {
                    message = e.toString();
                }
                String finalMessage = message;
                runOnUiThread(() -> {
                    isFetchingModels = false;
                    Toast.makeText(
                            this,
                            getString(R.string.custom_model_fetch_failed, finalMessage),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    // 通过v1/models接口获取当前可用模型列表。
    private List<String> requestRemoteModelIds(String host, String apiKey) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.COMPATIBLE_TLS))
                .build();
        OpenAiClient client = OpenAiClient.builder()
                .apiKey(Arrays.asList(apiKey))
                .apiHost(host)
                .okHttpClient(httpClient)
                .build();

        TreeSet<String> modelIdSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<Model> models = client.models();
        if(models != null) {
            for(Model model : models) {
                if(model != null && model.getId() != null && !model.getId().trim().isEmpty()) {
                    modelIdSet.add(model.getId().trim());
                }
            }
        }
        return new ArrayList<>(modelIdSet);
    }

    private void showFetchModelDialog(List<String> remoteModelIds) {
        Set<String> existingModelIds = new HashSet<>();
        for(CustomModelProfile profile : modelProfiles) {
            existingModelIds.add(profile.id);
        }

        ArrayList<FetchModelItem> items = new ArrayList<>();
        for(String modelId : remoteModelIds) {
            items.add(new FetchModelItem(modelId, existingModelIds.contains(modelId)));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.fetch_model_dialog, null);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setContentView(dialogView);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_fetch_model_dialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new FetchModelSelectAdapter(this, items, item -> {
            if(item.added) {
                Toast.makeText(this, R.string.custom_model_fetch_exists_toast, Toast.LENGTH_SHORT).show();
            } else {
                item.checked = !item.checked;
            }
        }));

        dialogView.findViewById(R.id.cv_fetch_model_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
        dialogView.findViewById(R.id.cv_fetch_model_dialog_ok).setOnClickListener(view -> {
            int addedCount = 0;
            for(FetchModelItem item : items) {
                if(!item.checked || item.added) {
                    continue;
                }
                modelProfiles.add(ModelCatalog.createProfileWithKnownDefaults(item.modelId));
                addedCount++;
            }
            if(addedCount > 0) {
                adapter.notifyDataSetChanged();
                saveProfiles();
            }
            Toast.makeText(this, getString(R.string.custom_model_fetch_result, addedCount), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}
