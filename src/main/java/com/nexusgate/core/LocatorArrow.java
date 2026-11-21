package com.nexusgate.core;

public final class LocatorArrow {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private LocatorArrow() {}

    /** Arrow pointing at (dx, dz) relative to a Minecraft yaw (0 = +Z, 90 = -X). */
    public static String arrow(double dx, double dz, float yawDegrees) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = wrap(targetYaw - yawDegrees);
        int index = Math.floorMod((int) Math.round(relative / 45.0), 8);
        return ARROWS[index];
    }

    private static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
