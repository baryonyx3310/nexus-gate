package com.nexusgate.client.gui;

/**
 * Pure geometry of the revolver drum: which entry sits in which chamber, chamber angles, hit tests and the
 * rotation that brings a chamber to the firing position (12 o'clock). Angles in radians, 0 = up, clockwise.
 */
public final class RadialLayout {
    public static final int CHAMBERS = 8;
    public static final float TWO_PI = (float) (Math.PI * 2);
    public static final float STEP = TWO_PI / CHAMBERS;

    private RadialLayout() {}

    public static int pages(int entries) {
        return Math.max(1, (entries + CHAMBERS - 1) / CHAMBERS);
    }

    /** Entry index shown in a chamber, or -1 when that chamber is empty on this page. */
    public static int entryAt(int page, int slot, int entries) {
        if (slot < 0 || slot >= CHAMBERS || page < 0) return -1;
        int index = page * CHAMBERS + slot;
        return index < entries ? index : -1;
    }

    public static int pageOf(int entry) {
        return entry < 0 ? 0 : entry / CHAMBERS;
    }

    public static int slotOf(int entry) {
        return entry < 0 ? -1 : entry % CHAMBERS;
    }

    public static float slotAngle(int slot) {
        return STEP * slot;
    }

    /** Drum rotation that puts {@code slot} at the top, chosen closest to {@code current} (no long spins). */
    public static float alignRotation(int slot, float current) {
        float target = -slotAngle(slot);
        return current + wrap(target - current);
    }

    /** Wraps an angle difference into (-PI, PI]. */
    public static float wrap(float a) {
        float x = a % TWO_PI;
        if (x <= -Math.PI) x += TWO_PI;
        if (x > Math.PI) x -= TWO_PI;
        return x;
    }

    /** Screen position of a chamber centre (GUI y grows downward). */
    public static float chamberX(float cx, int slot, float rotation, float radius) {
        return cx + (float) Math.sin(slotAngle(slot) + rotation) * radius;
    }

    public static float chamberY(float cy, int slot, float rotation, float radius) {
        return cy - (float) Math.cos(slotAngle(slot) + rotation) * radius;
    }

    /** Chamber slot under the point, or -1. */
    public static int chamberAt(double mx, double my, float cx, float cy, float rotation, float ringRadius, float chamberRadius) {
        for (int slot = 0; slot < CHAMBERS; slot++) {
            double dx = mx - chamberX(cx, slot, rotation, ringRadius);
            double dy = my - chamberY(cy, slot, rotation, ringRadius);
            if (dx * dx + dy * dy <= chamberRadius * chamberRadius) return slot;
        }
        return -1;
    }

    public static boolean inCircle(double mx, double my, float cx, float cy, float radius) {
        double dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}
