package com.skythinker.gptassistant.data;

public class MainActionSpec {
    // 动作唯一标识。
    public final String id;
    // 标题资源。
    public final int titleRes;
    // 默认图标资源。
    public final int normalIconRes;
    // 状态切换后的备用图标资源。
    public final int alternateIconRes;
    // 默认所在分组。
    public final int defaultPlacement;
    // 默认显示顺序。
    public final int defaultOrder;
    // 是否允许用户自定义位置。
    public final boolean userCustomizable;
    // 是否使用浅色底图标。
    public final boolean whiteBackground;
    // 允许出现的分组范围。
    public final int[] allowedPlacements;

    // 定义一个主界面按钮的静态属性。
    public MainActionSpec(String id,
                          int titleRes,
                          int normalIconRes,
                          int alternateIconRes,
                          int defaultPlacement,
                          int defaultOrder,
                          boolean userCustomizable,
                          boolean whiteBackground,
                          int[] allowedPlacements) {
        this.id = id;
        this.titleRes = titleRes;
        this.normalIconRes = normalIconRes;
        this.alternateIconRes = alternateIconRes;
        this.defaultPlacement = defaultPlacement;
        this.defaultOrder = defaultOrder;
        this.userCustomizable = userCustomizable;
        this.whiteBackground = whiteBackground;
        this.allowedPlacements = allowedPlacements;
    }

    // 判断按钮是否支持放到指定分组。
    public boolean supportsPlacement(int placement) {
        for (int allowedPlacement : allowedPlacements) {
            if (allowedPlacement == placement) {
                return true;
            }
        }
        return false;
    }
}
