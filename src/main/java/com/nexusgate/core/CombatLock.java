package com.nexusgate.core;

public final class CombatLock {
    /** Marker for "never been in combat". */
    public static final long NEVER = Long.MIN_VALUE;

    private CombatLock() {}

    public static int remainingTicks(long lastCombatTick, long nowTick, int lockSeconds) {
        if (lastCombatTick == NEVER) return 0;
        long remaining = lastCombatTick + lockSeconds * 20L - nowTick;
        return (int) Math.max(0, remaining);
    }
}
