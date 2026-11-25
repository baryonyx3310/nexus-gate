package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → client: open the rename screen. When isNew is true the client pre-fills a name
 * localized in its own language from x/z.
 */
public record OpenRenamePayload(UUID station, String currentName, boolean isNew, int x, int z) implements CustomPacketPayload {
    public static final Type<OpenRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "open_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRenamePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, OpenRenamePayload::station,
            ByteBufCodecs.STRING_UTF8, OpenRenamePayload::currentName,
            ByteBufCodecs.BOOL, OpenRenamePayload::isNew,
            ByteBufCodecs.VAR_INT, OpenRenamePayload::x,
            ByteBufCodecs.VAR_INT, OpenRenamePayload::z,
            OpenRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
