package com.nexusgate.teleport;

import com.nexusgate.core.CombatLock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the last game tick each player took or dealt damage. */
public final class CombatEvents {
    private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();

    private CombatEvents() {}

    public static long lastCombat(UUID player) {
        return LAST_COMBAT.getOrDefault(player, CombatLock.NEVER);
    }

    static void mark(UUID player, long gameTime) {
        LAST_COMBAT.put(player, gameTime);
    }

    static void forget(UUID player) {
        LAST_COMBAT.remove(player);
    }
}
