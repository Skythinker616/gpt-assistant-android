package com.skythinker.gptassistant.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.GlobalDataHolder;
import com.skythinker.gptassistant.data.MainActionLayoutItem;
import com.skythinker.gptassistant.data.MainActionRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActionConfActivity extends Activity {

    // 当前页面上编辑的按钮布局副本。
    private final List<MainActionLayoutItem> actionItems = new ArrayList<>();
    private MainActionConfAdapter adapter;

    @Override
    // 初始化主界面按钮布局配置页。
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_action_conf);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        actionItems.addAll(MainActionRegistry.getResolvedLayout(GlobalDataHolder.getMainActionLayout()));

        RecyclerView rvActionList = findViewById(R.id.rv_main_action_conf);
        rvActionList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MainActionConfAdapter(this, actionItems, this::updatePlacement);
        rvActionList.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                if (fromPosition < 0 || toPosition < 0) {
                    return false;
                }
                MainActionLayoutItem draggedItem = actionItems.get(fromPosition);
                MainActionLayoutItem targetItem = actionItems.get(toPosition);
                if (draggedItem.placement != targetItem.placement) {
                    return false;
                }
                // 仅允许在同一分组内拖动，避免一级/二级混排时顺序语义变乱。
                Collections.swap(actionItems, fromPosition, toPosition);
                MainActionRegistry.rebuildPlacementOrdersFromCurrentSequence(actionItems);
                adapter.notifyItemMoved(fromPosition, toPosition);
                saveLayout();
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) { }
        }).attachToRecyclerView(rvActionList);

        findViewById(R.id.bt_main_action_conf_back).setOnClickListener(view -> finish());
        findViewById(R.id.tv_main_action_conf_reset).setOnClickListener(view -> {
            // 恢复默认时直接回到注册表定义，确保后续调默认策略时这里自动跟随。
            actionItems.clear();
            actionItems.addAll(MainActionRegistry.getDefaultLayout());
            adapter.notifyDataSetChanged();
            GlobalDataHolder.resetMainActionLayout();
        });
    }

    // 更新按钮所在分组，并同步重排布局。
    private boolean updatePlacement(MainActionLayoutItem item, int newPlacement) {
        if (item.placement == newPlacement) {
            return true;
        }
        if (newPlacement == MainActionRegistry.PLACEMENT_PRIMARY
                && item.placement != MainActionRegistry.PLACEMENT_PRIMARY
                && MainActionRegistry.getPrimaryActionCount(actionItems) >= MainActionRegistry.MAX_PRIMARY_ACTION_COUNT) {
            Toast.makeText(this, R.string.toast_main_action_primary_limit, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 改变分组后先放到目标分组末尾，再重新规整顺序，避免拖动结果被旧 order 覆盖。
        item.placement = newPlacement;
        item.order = MainActionRegistry.getNextOrder(actionItems, newPlacement);
        MainActionRegistry.normalizeLayout(actionItems);
        adapter.notifyDataSetChanged();
        saveLayout();
        return true;
    }

    // 将当前按钮布局写回全局配置。
    private void saveLayout() {
        GlobalDataHolder.saveMainActionLayout(MainActionRegistry.toJson(actionItems));
    }

    @Override
    // 关闭页面并播放返回动画。
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}
