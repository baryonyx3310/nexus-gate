package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client -> server: change a station's icon (owner or OP only, checked on the server). */
public record SetIconPayload(UUID station, int icon) implements CustomPacketPayload {
    public static final Type<SetIconPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "set_icon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetIconPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetIconPayload::station,
            ByteBufCodecs.VAR_INT, SetIconPayload::icon,
            SetIconPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
