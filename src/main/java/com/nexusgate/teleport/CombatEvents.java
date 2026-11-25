package com.nexusgate.teleport;

import com.nexusgate.config.NexusConfig;
import com.nexusgate.core.CombatLock;
import com.nexusgate.net.CombatLockPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks until which game tick each player is locked out of teleporting, and who was in a fight since the last
 * charge tick. Taking damage locks for 5..15 s depending on the hit; dealing damage locks for combatLockSeconds.
 * Uses the incoming-damage event so hits soaked by shields, armour or absorption hearts still count.
 */
public final class CombatEvents {
    private static final Map<UUID, Long> LOCK_UNTIL = new ConcurrentHashMap<>();
    private static final Set<UUID> FOUGHT_SINCE_TICK = ConcurrentHashMap.newKeySet();

    private CombatEvents() {}

    /** Ticks the player must still wait before starting a teleport. */
    public static int remainingTicks(UUID player, long nowTick) {
        return CombatLock.remainingTicks(LOCK_UNTIL.getOrDefault(player, CombatLock.NEVER), nowTick);
    }

    /** @return true if the player took or dealt damage since the last call for that player. */
    static boolean consumeFought(UUID player) {
        return FOUGHT_SINCE_TICK.remove(player);
    }

    /** Registered at LOWEST priority, not receiving cancelled events, so only hits that really land count. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0) return;
        long now = event.getEntity().getServer().overworld().getGameTime();
        if (event.getEntity() instanceof ServerPlayer victim) {
            int seconds = CombatLock.hurtLockSeconds(event.getAmount(), NexusConfig.hurtLockMinSeconds(),
                    NexusConfig.hurtLockMaxSeconds(), NexusConfig.hurtLockFullDamage());
            lock(victim, now, seconds);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            lock(attacker, now, NexusConfig.chargeSettings().combatLockSeconds());
        }
    }

    private static void lock(ServerPlayer player, long now, int seconds) {
        UUID id = player.getUUID();
        long until = CombatLock.extend(LOCK_UNTIL.getOrDefault(id, CombatLock.NEVER), now, seconds);
        LOCK_UNTIL.put(id, until);
        FOUGHT_SINCE_TICK.add(id);
        PacketDistributor.sendToPlayer(player, new CombatLockPayload(CombatLock.remainingTicks(until, now)));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LOCK_UNTIL.remove(id);
        FOUGHT_SINCE_TICK.remove(id);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LOCK_UNTIL.clear();
        FOUGHT_SINCE_TICK.clear();
    }
}
