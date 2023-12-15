package com.skythinker.gptassistant;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.skythinker.gptassistant.ChatManager.Conversation;
import com.skythinker.gptassistant.ChatManager.ChatMessage;

public class HistoryActivity extends Activity {

    static private class HistoryListAdapter extends RecyclerView.Adapter<HistoryListAdapter.ViewHolder> {
        HistoryActivity historyActivity;

        public HistoryListAdapter(HistoryActivity historyActivity) {
            this.historyActivity = historyActivity;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_list_item, parent, false);
            return new HistoryListAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Conversation conversation = historyActivity.chatManager.getConversationAtPosition(position + 1);
            holder.tvTitle.setText(conversation.title);
            holder.tvDetail.setText("");
            for(ChatMessage message : conversation.messages) {
                if (message.role == ChatMessage.ChatRole.ASSISTANT && message.functionName == null) {
                    holder.tvDetail.setText(message.contentText.replaceAll("\n", " "));
                    break;
                }
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.getYear() == conversation.time.getYear() && now.getMonthValue() == conversation.time.getMonthValue() && now.getDayOfMonth() == conversation.time.getDayOfMonth())
                holder.tvTime.setText(conversation.time.format(DateTimeFormatter.ofPattern("HH:mm")));
            else
                holder.tvTime.setText(conversation.time.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));
        }

        @Override
        public int getItemCount() {
            return (int) historyActivity.chatManager.getConversationCount() - 1;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private TextView tvTitle, tvDetail, tvTime;
            private LinearLayout llOuter;
            public ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_history_item_title);
                tvDetail = itemView.findViewById(R.id.tv_history_item_detail);
                tvTime = itemView.findViewById(R.id.tv_history_item_time);
                llOuter = itemView.findViewById(R.id.ll_history_item_outer);
                llOuter.setOnClickListener((view) -> {
                    Intent intent = new Intent();
                    intent.putExtra("id", historyActivity.chatManager.getConversationAtPosition(getAdapterPosition() + 1).id);
                    historyActivity.setResult(RESULT_OK, intent);
                    historyActivity.finish();
                });
            }
        }
    }

    private ChatManager chatManager;
    private RecyclerView rvHistoryList;
    private HistoryListAdapter historyListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out); // 进入动画

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        chatManager = new ChatManager(this);
        chatManager.removeEmptyConversations(false);

        rvHistoryList = findViewById(R.id.rv_history_list);
        rvHistoryList.setLayoutManager(new LinearLayoutManager(this));
        historyListAdapter = new HistoryListAdapter(this);
        rvHistoryList.setAdapter(historyListAdapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) { // 左滑删除
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不支持上下拖动
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition(); // 获取滑动的item的position
                chatManager.removeConversation(chatManager.getConversationAtPosition(position + 1).id);
                historyListAdapter.notifyItemRemoved(position);
            }
        }).attachToRecyclerView(rvHistoryList);

        (findViewById(R.id.bt_history_back)).setOnClickListener((view) -> {
            finish();
        });

        (findViewById(R.id.bt_history_clear_all)).setOnClickListener((view) -> {
            new ConfirmDialog(this)
                    .setContent("确定要清空所有历史记录吗？\n（左滑可删除单条记录）")
                    .setOnConfirmListener(() -> {
                        chatManager.removeAllConversations(false);
                        historyListAdapter.notifyDataSetChanged();
                    }).show();
        });
    }

    @Override
    protected void onDestroy() {
        chatManager.destroy();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}