package com.nexusgate.core;

/** XP cost values. Pure data, no Minecraft types. */
public record XpSettings(boolean enabled, int baseCost, int blocksPerLevel, int maxSameDimension, int crossDimensionCost) {
    public static XpSettings defaults() {
        return new XpSettings(true, 1, 500, 10, 5);
    }
}
