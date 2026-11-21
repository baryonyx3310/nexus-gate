package com.nexusgate.teleport;

import com.nexusgate.core.CombatLock;
import com.nexusgate.core.CombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last game tick each player took or dealt damage, and who was in a fight since the last charge tick.
 * Uses the incoming-damage event so hits soaked by shields, armour or absorption hearts still count.
 */
public final class CombatEvents {
    private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
    private static final Set<UUID> FOUGHT_SINCE_TICK = ConcurrentHashMap.newKeySet();

    private CombatEvents() {}

    public static long lastCombat(UUID player) {
        return LAST_COMBAT.getOrDefault(player, CombatLock.NEVER);
    }

    /** @return true if the player took or dealt damage since the last call for that player. */
    static boolean consumeFought(UUID player) {
        return FOUGHT_SINCE_TICK.remove(player);
    }

    /** Registered at LOWEST priority, not receiving cancelled events, so only hits that really land count. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        UUID victim = event.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        UUID attacker = event.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        Set<UUID> involved = CombatRules.participants(victim, attacker, event.getAmount());
        if (involved.isEmpty()) return;
        long now = event.getEntity().getServer().overworld().getGameTime();
        for (UUID id : involved) {
            LAST_COMBAT.put(id, now);
            FOUGHT_SINCE_TICK.add(id);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_COMBAT.remove(id);
        FOUGHT_SINCE_TICK.remove(id);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_COMBAT.clear();
        FOUGHT_SINCE_TICK.clear();
    }
}
