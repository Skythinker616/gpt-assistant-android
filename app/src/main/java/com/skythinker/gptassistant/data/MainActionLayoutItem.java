package com.skythinker.gptassistant.data;

public class MainActionLayoutItem {
    // 按钮动作的唯一标识。
    public final String actionId;
    // 当前所在分组。
    public int placement;
    // 在所属分组中的显示顺序。
    public int order;

    // 创建一条主界面按钮布局记录。
    public MainActionLayoutItem(String actionId, int placement, int order) {
        this.actionId = actionId;
        this.placement = placement;
        this.order = order;
    }

    // 复制布局项，便于在内存中独立修改。
    public MainActionLayoutItem copy() {
        return new MainActionLayoutItem(actionId, placement, order);
    }
}
