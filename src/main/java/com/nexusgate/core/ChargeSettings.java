package com.nexusgate.core;

/** Anti-escape rule values. Pure data, no Minecraft types. */
public record ChargeSettings(int chargeSeconds, int perHostileSeconds, int maxChargeSeconds,
                             int crossDimensionExtraSeconds, double maxMoveDistance, int combatLockSeconds,
                             double stationUseRange, boolean allowCrossDimension) {
    public static ChargeSettings defaults() {
        return new ChargeSettings(5, 1, 12, 3, 1.5, 10, 4.0, true);
    }
}
