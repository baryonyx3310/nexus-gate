package com.nexusgate.teleport;

import com.nexusgate.config.NexusConfig;
import com.nexusgate.core.ChargeOutcome;
import com.nexusgate.core.ChargeRules;
import com.nexusgate.core.ChargeSession;
import com.nexusgate.core.ChargeSettings;
import com.nexusgate.core.RequestResult;
import com.nexusgate.core.RequestValidator;
import com.nexusgate.core.TeleportRequest;
import com.nexusgate.core.TickInput;
import com.nexusgate.core.XpCost;
import com.nexusgate.station.StationNetwork;
import com.nexusgate.station.StationRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
    private record Active(UUID source, UUID target, ChargeSession session, Vec3 start, int xpCost, BlockPos station) {}

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
        int combat = CombatEvents.remainingTicks(player.getUUID(), server.overworld().getGameTime());
        int cost = targetExists ? XpCost.levels(cross ? 0 : StationService.distanceTo(player, target.get()), cross,
                NexusConfig.xpSettings()) : 0;

        RequestResult result = RequestValidator.validateStart(new TeleportRequest(sourceHere, distanceToSource, targetExists,
                known, sourceId.equals(targetId), cross, combat, player.experienceLevel, player.isCreative(), cost), settings);
        if (result != RequestResult.OK) {
            Component reason = message(result, combat, cost);
            player.displayClientMessage(reason, true);
            GateFx.reject(player, reason);
            return;
        }

        int radius = NexusConfig.hostileRadius();
        int hostiles = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob instanceof Enemy && mob.isAlive()).size();
        int ticks = ChargeRules.chargeTicks(hostiles, cross, settings);
        BlockPos stationPos = source.get().pos();
        SESSIONS.put(player.getUUID(), new Active(sourceId, targetId, new ChargeSession(ticks, settings.maxMoveDistance()),
                player.position(), cost, stationPos));
        GateFx.start(player, stationPos, ticks);
        CombatEvents.consumeFought(player.getUUID());
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
            boolean damaged = CombatEvents.consumeFought(entry.getKey());
            Vec3 delta = present ? player.position().subtract(active.start()) : Vec3.ZERO;
            Optional<StationRecord> target = network.get(active.target());
            ChargeOutcome outcome = active.session().tick(new TickInput(present, damaged, delta.x, delta.y, delta.z,
                    network.get(active.source()).isPresent(), target.isPresent()));

            switch (outcome) {
                case CONTINUE -> charging(player, active.session());
                case COMPLETE -> {
                    it.remove();
                    complete(player, target.orElseThrow(), active.xpCost(), active.station());
                }
                default -> {
                    it.remove();
                    if (present) cancelled(player, active.station());
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

    /** Visuals are animated client-side from the START event; the server only keeps the rising hum. */
    private static void charging(ServerPlayer player, ChargeSession session) {
        int elapsed = session.elapsed();
        float progress = (float) elapsed / session.total();
        if (elapsed % 20 == 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f + 1.5f * progress);
        }
    }

    private static void complete(ServerPlayer player, StationRecord target, int cost, BlockPos station) {
        boolean present = TeleportExecutor.targetBlockPresent(player.server, target);
        if (!present) StationNetwork.get(player.server).remove(target.id());
        RequestResult result = RequestValidator.validateCompletion(player.experienceLevel, player.isCreative(), cost, present);
        if (result != RequestResult.OK) {
            Component reason = message(result, 0, cost);
            player.displayClientMessage(reason, true);
            GateFx.cancel(player, station, reason);
            cancelSound(player);
            return;
        }
        ServerLevel origin = player.serverLevel();
        Vec3 from = player.position();
        if (!TeleportExecutor.teleport(player, target)) {
            Component reason = Component.translatable("msg.nexusgate.no_safe_spot").withStyle(ChatFormatting.RED);
            player.displayClientMessage(reason, true);
            GateFx.cancel(player, station, reason);
            cancelSound(player);
            return;
        }
        GateFx.depart(player, origin, from, station);
        GateFx.arrive(player, player.serverLevel(), target.pos());
        if (!player.isCreative() && cost > 0) player.giveExperienceLevels(-cost);
        player.displayClientMessage(Component.translatable("msg.nexusgate.arrived", target.name()).withStyle(ChatFormatting.AQUA), true);
    }

    private static void cancelled(ServerPlayer player, BlockPos station) {
        Component reason = Component.translatable("msg.nexusgate.interrupted").withStyle(ChatFormatting.RED);
        player.displayClientMessage(reason, true);
        GateFx.cancel(player, station, reason);
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
