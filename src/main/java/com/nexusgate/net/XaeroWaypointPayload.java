package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client: a station was activated; add a minimap waypoint if Xaero is present. */
public record XaeroWaypointPayload(String name, int x, int y, int z, String dimension) implements CustomPacketPayload {
    public static final Type<XaeroWaypointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "xaero_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XaeroWaypointPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, XaeroWaypointPayload::name,
            ByteBufCodecs.VAR_INT, XaeroWaypointPayload::x,
            ByteBufCodecs.VAR_INT, XaeroWaypointPayload::y,
            ByteBufCodecs.VAR_INT, XaeroWaypointPayload::z,
            ByteBufCodecs.STRING_UTF8, XaeroWaypointPayload::dimension,
            XaeroWaypointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
