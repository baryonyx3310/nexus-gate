package com.nexusgate.teleport;

import com.nexusgate.core.CombatLock;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the last game tick each player took or dealt damage, and damage taken while charging. */
public final class CombatEvents {
    private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
    private static final Set<UUID> DAMAGED_SINCE_TICK = ConcurrentHashMap.newKeySet();

    private CombatEvents() {}

    public static long lastCombat(UUID player) {
        return LAST_COMBAT.getOrDefault(player, CombatLock.NEVER);
    }

    /** @return true if the player took damage since the last call for that player. */
    static boolean consumeDamaged(UUID player) {
        return DAMAGED_SINCE_TICK.remove(player);
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            LAST_COMBAT.put(victim.getUUID(), victim.server.overworld().getGameTime());
            DAMAGED_SINCE_TICK.add(victim.getUUID());
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            LAST_COMBAT.put(attacker.getUUID(), attacker.server.overworld().getGameTime());
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_COMBAT.remove(id);
        DAMAGED_SINCE_TICK.remove(id);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_COMBAT.clear();
        DAMAGED_SINCE_TICK.clear();
    }
}
