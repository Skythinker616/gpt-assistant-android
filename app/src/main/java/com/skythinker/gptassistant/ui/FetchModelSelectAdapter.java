package com.skythinker.gptassistant.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skythinker.gptassistant.R;

import java.util.List;

public class FetchModelSelectAdapter extends RecyclerView.Adapter<FetchModelSelectAdapter.ViewHolder> {

    public static class FetchModelItem {
        // 远端返回的模型 ID。
        public final String modelId;
        // 是否已存在于本地配置中。
        public final boolean added;
        // 是否被用户勾选导入。
        public boolean checked;

        // 记录一条待导入模型项的状态。
        public FetchModelItem(String modelId, boolean added) {
            this.modelId = modelId;
            this.added = added;
        }
    }

    public interface IItemClickListener {
        // 响应远端模型项点击事件。
        void onItemClick(FetchModelItem item);
    }

    private final Context context;
    private final List<FetchModelItem> items;
    private final IItemClickListener itemClickListener;

    // 绑定远端模型列表与勾选回调。
    public FetchModelSelectAdapter(Context context,
                                   List<FetchModelItem> items,
                                   IItemClickListener itemClickListener) {
        this.context = context;
        this.items = items;
        this.itemClickListener = itemClickListener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout llOuter;
        final TextView tvTitle;
        final CheckBox checkBox;
        final TextView tvAdded;

        // 缓存远端模型列表项视图。
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            llOuter = itemView.findViewById(R.id.ll_fetch_model_item_outer);
            tvTitle = itemView.findViewById(R.id.tv_fetch_model_item_title);
            checkBox = itemView.findViewById(R.id.cb_fetch_model_item);
            tvAdded = itemView.findViewById(R.id.tv_fetch_model_item_added);
        }
    }

    @NonNull
    @Override
    // 创建远端模型列表项视图。
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fetch_model_dialog_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    // 绑定远端模型的选中与已添加状态。
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FetchModelItem item = items.get(position);
        holder.tvTitle.setText(item.modelId);
        holder.checkBox.setChecked(item.checked);
        holder.checkBox.setVisibility(item.added ? View.GONE : View.VISIBLE);
        holder.tvAdded.setVisibility(item.added ? View.VISIBLE : View.GONE);
        if(item.added) {
            holder.tvTitle.setTextColor(Color.parseColor("#8A8A8A"));
        } else {
            holder.tvTitle.setTextColor(Color.BLACK);
        }
        holder.llOuter.setOnClickListener(view -> {
            if(itemClickListener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                itemClickListener.onItemClick(item);
                notifyItemChanged(holder.getAdapterPosition());
            }
        });
    }

    @Override
    // 返回远端模型列表数量。
    public int getItemCount() {
        return items.size();
    }
}
