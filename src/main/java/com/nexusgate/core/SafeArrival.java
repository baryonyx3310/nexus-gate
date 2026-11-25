package com.nexusgate.core;

import org.jetbrains.annotations.Nullable;

/** Picks where a player lands next to a 2-tall station. */
public final class SafeArrival {
    @FunctionalInterface
    public interface IntBinaryPredicate {
        boolean test(int x, int z);
    }

    /** Sides (S, N, E, W), then diagonals, then the radius-2 ring sides. */
    private static final int[][] CANDIDATES = {
            {0, 1}, {0, -1}, {1, 0}, {-1, 0},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {0, 2}, {0, -2}, {2, 0}, {-2, 0}};

    private SafeArrival() {}

    /**
     * @param free    whether the column at (x, z) offset is safe to stand in
     * @param topFree whether standing on top of the station is safe
     * @return {offsetX, offsetZ, offsetY}, or null when no safe spot exists
     */
    @Nullable
    public static int[] pick(IntBinaryPredicate free, boolean topFree) {
        for (int[] c : CANDIDATES) {
            if (free.test(c[0], c[1])) return new int[]{c[0], c[1], 0};
        }
        return topFree ? new int[]{0, 0, 2} : null;
    }
}
