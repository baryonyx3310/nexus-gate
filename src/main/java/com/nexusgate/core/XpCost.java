package com.nexusgate.core;

public final class XpCost {
    private XpCost() {}

    public static int levels(double distance, boolean crossDimension, XpSettings s) {
        if (!s.enabled()) return 0;
        if (crossDimension) return s.crossDimensionCost();
        int steps = s.blocksPerLevel() <= 0 ? 0 : (int) Math.floor(Math.max(0, distance) / s.blocksPerLevel());
        return Math.min(s.baseCost() + steps, s.maxSameDimension());
    }
}
