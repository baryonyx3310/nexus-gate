package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client → server: start charging from source to target. */
public record SelectStationPayload(UUID source, UUID target) implements CustomPacketPayload {
    public static final Type<SelectStationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "select_station"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectStationPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SelectStationPayload::source,
            UUIDUtil.STREAM_CODEC, SelectStationPayload::target,
            SelectStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
