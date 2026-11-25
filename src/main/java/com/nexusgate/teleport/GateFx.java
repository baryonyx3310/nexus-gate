package com.nexusgate.teleport;

import com.nexusgate.net.GateFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/** Server-side emitters for the visual teleport events. Never changes game state. */
public final class GateFx {
    private static final double VIEW_RADIUS = 64.0;

    private GateFx() {}

    public static void start(ServerPlayer player, BlockPos station, int totalTicks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(GateFxPayload.PHASE_START, player,
                player.position(), station, totalTicks, null));
    }

    public static void cancel(ServerPlayer player, BlockPos station, Component reason) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(GateFxPayload.PHASE_CANCEL, player,
                player.position(), station, 0, reason));
    }

    /** Only the requesting player needs to know; nothing started in the world. */
    public static void reject(ServerPlayer player, Component reason) {
        PacketDistributor.sendToPlayer(player, payload(GateFxPayload.PHASE_REJECT, player, player.position(),
                player.blockPosition(), 0, reason));
    }

    /** Sent from the origin, before the player leaves, so watchers there see the departure. */
    public static void depart(ServerPlayer player, ServerLevel origin, Vec3 from, BlockPos station) {
        GateFxPayload p = payload(GateFxPayload.PHASE_DEPART, player, from, station, 0, null);
        PacketDistributor.sendToPlayer(player, p);
        PacketDistributor.sendToPlayersNear(origin, player, from.x, from.y, from.z, VIEW_RADIUS, p);
    }

    /** Sent after the move, to everyone around the destination (including the traveller). */
    public static void arrive(ServerPlayer player, ServerLevel destination, BlockPos station) {
        Vec3 at = player.position();
        PacketDistributor.sendToPlayersNear(destination, null, at.x, at.y, at.z, VIEW_RADIUS,
                payload(GateFxPayload.PHASE_ARRIVE, player, at, station, 0, null));
    }

    private static GateFxPayload payload(byte phase, ServerPlayer player, Vec3 pos, BlockPos station, int total,
                                         Component message) {
        return new GateFxPayload(phase, player.getId(), pos, station, total, Optional.ofNullable(message));
    }
}
