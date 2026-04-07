package com.skythinker.gptassistant.data;

public class MainActionSpec {
    public final String id;
    public final int titleRes;
    public final int normalIconRes;
    public final int alternateIconRes;
    public final int defaultPlacement;
    public final int defaultOrder;
    public final boolean userCustomizable;
    public final boolean whiteBackground;
    public final int[] allowedPlacements;

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

    public boolean supportsPlacement(int placement) {
        for (int allowedPlacement : allowedPlacements) {
            if (allowedPlacement == placement) {
                return true;
            }
        }
        return false;
    }
}
