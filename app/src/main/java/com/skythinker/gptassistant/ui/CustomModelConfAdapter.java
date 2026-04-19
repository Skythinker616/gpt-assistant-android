package com.skythinker.gptassistant.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.CustomModelProfile;
import com.skythinker.gptassistant.data.ModelCatalog;

import java.util.ArrayList;
import java.util.List;

public class CustomModelConfAdapter extends RecyclerView.Adapter<CustomModelConfAdapter.ViewHolder> {

    public interface IItemClickListener {
        // 响应模型项点击事件。
        void onItemClick(int position);
    }

    private final Context context;
    private final List<CustomModelProfile> modelProfiles;
    private final IItemClickListener itemClickListener;

    // 绑定模型列表与点击回调。
    public CustomModelConfAdapter(Context context,
                                  List<CustomModelProfile> modelProfiles,
                                  IItemClickListener itemClickListener) {
        this.context = context;
        this.modelProfiles = modelProfiles;
        this.itemClickListener = itemClickListener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout llOuter;
        final TextView tvTitle;
        final TextView tvId;
        final TextView tvCaps;

        // 缓存模型项视图引用。
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            llOuter = itemView.findViewById(R.id.ll_custom_model_conf_outer);
            tvTitle = itemView.findViewById(R.id.tv_custom_model_conf_title);
            tvId = itemView.findViewById(R.id.tv_custom_model_conf_id);
            tvCaps = itemView.findViewById(R.id.tv_custom_model_conf_caps);
        }
    }

    @NonNull
    @Override
    // 创建模型配置列表项视图。
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_model_conf_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    // 绑定单个模型配置的展示内容。
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomModelProfile profile = modelProfiles.get(position);
        holder.tvTitle.setText(profile.id);
        holder.tvId.setVisibility(View.GONE);
        holder.tvCaps.setText(buildCapabilitySummary(profile));
        holder.llOuter.setOnClickListener(view -> {
            if(itemClickListener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                itemClickListener.onItemClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    // 返回当前模型配置数量。
    public int getItemCount() {
        return modelProfiles.size();
    }

    // 生成模型能力摘要文案。
    private String buildCapabilitySummary(CustomModelProfile profile) {
        ArrayList<String> capabilityTexts = new ArrayList<>();
        if(profile.hasCapability(ModelCatalog.CAPABILITY_VISION)) {
            capabilityTexts.add(context.getString(R.string.custom_model_cap_vision));
        }
        // if(profile.hasCapability(ModelCatalog.CAPABILITY_THINKING)) {
        //     capabilityTexts.add(context.getString(R.string.custom_model_cap_thinking));
        // }
        if(profile.hasCapability(ModelCatalog.CAPABILITY_TOOL)) {
            capabilityTexts.add(context.getString(R.string.custom_model_cap_tool));
        }
        if(capabilityTexts.size() == 0) {
            return context.getString(R.string.custom_model_caps_text_only);
        }
        return context.getString(R.string.custom_model_caps_prefix) + " " + android.text.TextUtils.join(" / ", capabilityTexts);
    }
}
