package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client → server: mark a station public/private (permission checked on the server). */
public record SetPublicPayload(UUID station, boolean value) implements CustomPacketPayload {
    public static final Type<SetPublicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "set_public"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPublicPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetPublicPayload::station,
            ByteBufCodecs.BOOL, SetPublicPayload::value,
            SetPublicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
