package com.nexusgate.core;

/** Teleport lock after combat, stored as the game tick until which a player stays locked. */
public final class CombatLock {
    /** Marker for "never locked". */
    public static final long NEVER = Long.MIN_VALUE;

    private CombatLock() {}

    public static int remainingTicks(long lockUntilTick, long nowTick) {
        if (lockUntilTick == NEVER) return 0;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, lockUntilTick - nowTick));
    }

    /** Extends a lock to at least {@code seconds} from now; a shorter new lock never shortens a running one. */
    public static long extend(long currentUntil, long nowTick, int seconds) {
        long until = nowTick + Math.max(0, seconds) * 20L;
        return currentUntil == NEVER ? until : Math.max(currentUntil, until);
    }

    /**
     * Lock length after taking {@code damage}: scales linearly from {@code min} seconds (tiny hits) to {@code max}
     * seconds (at {@code fullDamage} or more).
     */
    public static int hurtLockSeconds(float damage, int min, int max, float fullDamage) {
        if (max < min) max = min;
        float t = fullDamage <= 0f ? 1f : Math.max(0f, Math.min(1f, damage / fullDamage));
        return Math.round(min + (max - min) * t);
    }
}
