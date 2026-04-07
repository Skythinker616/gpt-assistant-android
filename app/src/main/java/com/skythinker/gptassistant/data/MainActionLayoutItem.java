package com.skythinker.gptassistant.data;

public class MainActionLayoutItem {
    public final String actionId;
    public int placement;
    public int order;

    public MainActionLayoutItem(String actionId, int placement, int order) {
        this.actionId = actionId;
        this.placement = placement;
        this.order = order;
    }

    public MainActionLayoutItem copy() {
        return new MainActionLayoutItem(actionId, placement, order);
    }
}
