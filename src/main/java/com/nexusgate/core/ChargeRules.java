package com.nexusgate.core;

public final class ChargeRules {
    private static final int CELLS = 10;

    private ChargeRules() {}

    public static int chargeTicks(int hostileCount, boolean crossDimension, ChargeSettings s) {
        int seconds = s.chargeSeconds() + Math.max(0, hostileCount) * s.perHostileSeconds()
                + (crossDimension ? s.crossDimensionExtraSeconds() : 0);
        return Math.min(seconds, s.maxChargeSeconds()) * 20;
    }

    public static boolean movedTooFar(double dx, double dy, double dz, double max) {
        return dx * dx + dy * dy + dz * dz > max * max;
    }

    public static String progressBar(int elapsedTicks, int totalTicks) {
        int percent = totalTicks <= 0 ? 100 : Math.max(0, Math.min(100, elapsedTicks * 100 / totalTicks));
        int filled = percent * CELLS / 100;
        return "[" + "■".repeat(filled) + "□".repeat(CELLS - filled) + "] " + percent + "%";
    }
}
