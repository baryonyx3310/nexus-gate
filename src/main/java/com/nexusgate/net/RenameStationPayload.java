package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client → server: rename a station (owner or OP only, checked on the server). */
public record RenameStationPayload(UUID station, String name) implements CustomPacketPayload {
    public static final Type<RenameStationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "rename_station"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameStationPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenameStationPayload::station,
            ByteBufCodecs.stringUtf8(64), RenameStationPayload::name,
            RenameStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
