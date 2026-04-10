package com.skythinker.gptassistant.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.MainActionLayoutItem;
import com.skythinker.gptassistant.data.MainActionRegistry;
import com.skythinker.gptassistant.data.MainActionSpec;

import java.util.ArrayList;
import java.util.List;

public class MainActionConfAdapter extends RecyclerView.Adapter<MainActionConfAdapter.ViewHolder> {

    public interface IPlacementChangeListener {
        // 响应按钮分组变更请求。
        boolean onPlacementChange(MainActionLayoutItem item, int newPlacement);
    }

    private final Context context;
    private final List<MainActionLayoutItem> actionItems;
    private final IPlacementChangeListener placementChangeListener;

    // 绑定按钮布局列表与分组变更回调。
    public MainActionConfAdapter(Context context,
                                 List<MainActionLayoutItem> actionItems,
                                 IPlacementChangeListener placementChangeListener) {
        this.context = context;
        this.actionItems = actionItems;
        this.placementChangeListener = placementChangeListener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvTip;
        final Spinner spPlacement;
        boolean ignoreNextSelection = false;

        // 缓存按钮布局项视图。
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_main_action_conf_icon);
            tvTitle = itemView.findViewById(R.id.tv_main_action_conf_title);
            tvTip = itemView.findViewById(R.id.tv_main_action_conf_tip);
            spPlacement = itemView.findViewById(R.id.sp_main_action_conf_placement);
        }
    }

    @NonNull
    @Override
    // 创建按钮布局配置列表项视图。
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.main_action_conf_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    // 绑定按钮布局项的图标、标题和分组选项。
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MainActionLayoutItem item = actionItems.get(position);
        MainActionSpec spec = MainActionRegistry.findSpec(item.actionId);
        if (spec == null) {
            return;
        }

        holder.ivIcon.setImageResource(spec.normalIconRes);
        holder.tvTitle.setText(spec.titleRes);
        holder.tvTip.setText(buildAllowedPlacementTip(spec));

        List<Integer> placementValues = new ArrayList<>();
        List<String> placementLabels = new ArrayList<>();
        for (int allowedPlacement : spec.allowedPlacements) {
            placementValues.add(allowedPlacement);
            placementLabels.add(getPlacementLabel(allowedPlacement));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.param_spinner_item, placementLabels);
        adapter.setDropDownViewResource(R.layout.param_spinner_dropdown_item);
        holder.spPlacement.setAdapter(adapter);

        int selectedIndex = placementValues.indexOf(item.placement);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        holder.ignoreNextSelection = true;
        holder.spPlacement.setSelection(selectedIndex, false);
        holder.spPlacement.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int selectedPosition, long id) {
                if (holder.ignoreNextSelection) {
                    holder.ignoreNextSelection = false;
                    return;
                }
                int newPlacement = placementValues.get(selectedPosition);
                if (newPlacement == item.placement) {
                    return;
                }
                if (placementChangeListener != null && !placementChangeListener.onPlacementChange(item, newPlacement)) {
                    int rollbackIndex = placementValues.indexOf(item.placement);
                    holder.ignoreNextSelection = true;
                    holder.spPlacement.setSelection(Math.max(rollbackIndex, 0), false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    @Override
    // 返回按钮布局项数量。
    public int getItemCount() {
        return actionItems.size();
    }

    // 生成当前按钮可放置分组的说明文案。
    private String buildAllowedPlacementTip(MainActionSpec spec) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < spec.allowedPlacements.length; i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(getPlacementLabel(spec.allowedPlacements[i]));
        }
        return builder.toString();
    }

    // 获取分组对应的展示文案。
    private String getPlacementLabel(int placement) {
        if (placement == MainActionRegistry.PLACEMENT_PRIMARY) {
            return context.getString(R.string.main_action_primary);
        }
        if (placement == MainActionRegistry.PLACEMENT_SECONDARY) {
            return context.getString(R.string.main_action_secondary);
        }
        return context.getString(R.string.main_action_hidden);
    }
}
