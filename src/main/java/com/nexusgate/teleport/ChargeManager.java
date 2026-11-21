package com.nexusgate.teleport;

import com.nexusgate.config.NexusConfig;
import com.nexusgate.core.ChargeOutcome;
import com.nexusgate.core.ChargeRules;
import com.nexusgate.core.ChargeSession;
import com.nexusgate.core.ChargeSettings;
import com.nexusgate.core.CombatLock;
import com.nexusgate.core.RequestResult;
import com.nexusgate.core.RequestValidator;
import com.nexusgate.core.TeleportRequest;
import com.nexusgate.core.TickInput;
import com.nexusgate.core.XpCost;
import com.nexusgate.station.StationNetwork;
import com.nexusgate.station.StationRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns charging sessions: starts them after validation, ticks them, completes or cancels them. */
public final class ChargeManager {
    private record Active(UUID source, UUID target, ChargeSession session, Vec3 start, int xpCost) {}

    private static final Map<UUID, Active> SESSIONS = new ConcurrentHashMap<>();

    private ChargeManager() {}

    public static void requestStart(ServerPlayer player, UUID sourceId, UUID targetId) {
        MinecraftServer server = player.server;
        StationNetwork network = StationNetwork.get(server);
        ChargeSettings settings = NexusConfig.chargeSettings();
        Optional<StationRecord> source = network.get(sourceId);
        Optional<StationRecord> target = network.get(targetId);

        boolean sourceHere = source.isPresent() && source.get().dimension().equals(player.level().dimension());
        double distanceToSource = sourceHere ? StationService.distanceTo(player, source.get()) : Double.MAX_VALUE;
        boolean targetExists = target.isPresent() && server.getLevel(target.get().dimension()) != null;
        boolean known = target.isPresent() && StationService.isKnown(player, target.get());
        boolean cross = targetExists && !target.get().dimension().equals(player.level().dimension());
        int combat = CombatLock.remainingTicks(CombatEvents.lastCombat(player.getUUID()), server.overworld().getGameTime(),
                settings.combatLockSeconds());
        int cost = targetExists ? XpCost.levels(cross ? 0 : StationService.distanceTo(player, target.get()), cross,
                NexusConfig.xpSettings()) : 0;

        RequestResult result = RequestValidator.validateStart(new TeleportRequest(sourceHere, distanceToSource, targetExists,
                known, sourceId.equals(targetId), cross, combat, player.experienceLevel, player.isCreative(), cost), settings);
        if (result != RequestResult.OK) {
            player.displayClientMessage(message(result, combat, cost), true);
            return;
        }

        int radius = NexusConfig.hostileRadius();
        int hostiles = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob instanceof Enemy && mob.isAlive()).size();
        int ticks = ChargeRules.chargeTicks(hostiles, cross, settings);
        SESSIONS.put(player.getUUID(), new Active(sourceId, targetId, new ChargeSession(ticks, settings.maxMoveDistance()),
                player.position(), cost));
        CombatEvents.consumeDamaged(player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        StationNetwork network = StationNetwork.get(server);
        Iterator<Map.Entry<UUID, Active>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> entry = it.next();
            Active active = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean present = player != null && player.isAlive() && !player.hasDisconnected();
            boolean damaged = CombatEvents.consumeDamaged(entry.getKey());
            Vec3 delta = present ? player.position().subtract(active.start()) : Vec3.ZERO;
            Optional<StationRecord> target = network.get(active.target());
            ChargeOutcome outcome = active.session().tick(new TickInput(present, damaged, delta.x, delta.y, delta.z,
                    network.get(active.source()).isPresent(), target.isPresent()));

            switch (outcome) {
                case CONTINUE -> charging(player, active.session());
                case COMPLETE -> {
                    it.remove();
                    complete(player, target.orElseThrow(), active.xpCost());
                }
                default -> {
                    it.remove();
                    if (present) cancelled(player);
                }
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    public static boolean isCharging(UUID player) {
        return SESSIONS.containsKey(player);
    }

    private static void charging(ServerPlayer player, ChargeSession session) {
        ServerLevel level = player.serverLevel();
        int elapsed = session.elapsed();
        float progress = (float) elapsed / session.total();
        double angle = elapsed * 0.45;
        double height = (elapsed % 30) / 30.0 * 2.0;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + arm * Math.PI;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(a) * 0.8, player.getY() + height,
                    player.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
        }
        if (elapsed % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.3, 0, 0.3, 0.02);
        }
        if (elapsed % 20 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f + 1.5f * progress);
        }
        if (elapsed % 2 == 0) {
            player.displayClientMessage(Component.literal(ChargeRules.progressBar(elapsed, session.total())).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void complete(ServerPlayer player, StationRecord target, int cost) {
        RequestResult result = RequestValidator.validateCompletion(player.experienceLevel, player.isCreative(), cost);
        if (result != RequestResult.OK) {
            player.displayClientMessage(message(result, 0, cost), true);
            cancelSound(player);
            return;
        }
        if (!TeleportExecutor.teleport(player, target)) {
            player.displayClientMessage(Component.translatable(RequestResult.TARGET_OFFLINE.translationKey()).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.isCreative() && cost > 0) player.giveExperienceLevels(-cost);
        player.displayClientMessage(Component.translatable("msg.nexusgate.arrived", target.name()).withStyle(ChatFormatting.AQUA), true);
    }

    private static void cancelled(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("msg.nexusgate.interrupted").withStyle(ChatFormatting.RED), true);
        cancelSound(player);
    }

    private static void cancelSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static Component message(RequestResult result, int combatTicks, int cost) {
        Component text = switch (result) {
            case IN_COMBAT -> Component.translatable(result.translationKey(), (combatTicks + 19) / 20);
            case NOT_ENOUGH_XP -> Component.translatable(result.translationKey(), cost);
            default -> Component.translatable(result.translationKey());
        };
        return text.copy().withStyle(ChatFormatting.RED);
    }
}
