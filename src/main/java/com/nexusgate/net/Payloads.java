package com.nexusgate.net;

import com.nexusgate.teleport.ChargeManager;
import com.nexusgate.teleport.StationService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload. Client-bound handlers are lambdas whose bodies call into
 * {@code com.nexusgate.client.ClientPayloadHandlers}, so that class is only loaded on a client.
 */
public final class Payloads {
    private Payloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");

        r.playToClient(OpenSelectionPayload.TYPE, OpenSelectionPayload.STREAM_CODEC,
                (p, ctx) -> com.nexusgate.client.ClientPayloadHandlers.openSelection(p));
        r.playToClient(OpenRenamePayload.TYPE, OpenRenamePayload.STREAM_CODEC,
                (p, ctx) -> com.nexusgate.client.ClientPayloadHandlers.openRename(p));
        r.playToClient(XaeroWaypointPayload.TYPE, XaeroWaypointPayload.STREAM_CODEC,
                (p, ctx) -> com.nexusgate.client.ClientPayloadHandlers.xaeroWaypoint(p));

        r.playToServer(SelectStationPayload.TYPE, SelectStationPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) ChargeManager.requestStart(player, p.source(), p.target());
        });
        r.playToServer(RenameStationPayload.TYPE, RenameStationPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) StationService.rename(player, p.station(), p.name());
        });
        r.playToServer(SetPublicPayload.TYPE, SetPublicPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) StationService.setPublic(player, p.station(), p.value());
        });
    }
}
