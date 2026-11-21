package com.nexusgate.core;

/** Picks where a player lands next to a 2-tall station. */
public final class SafeArrival {
    @FunctionalInterface
    public interface IntBinaryPredicate {
        boolean test(int x, int z);
    }

    /** South, north, east, west. */
    private static final int[][] SIDES = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

    private SafeArrival() {}

    /** @return {offsetX, offsetZ, offsetY}; {0, 0, 2} (on top) when no side is free. */
    public static int[] pick(IntBinaryPredicate free) {
        for (int[] side : SIDES) {
            if (free.test(side[0], side[1])) return new int[]{side[0], side[1], 0};
        }
        return new int[]{0, 0, 2};
    }
}
