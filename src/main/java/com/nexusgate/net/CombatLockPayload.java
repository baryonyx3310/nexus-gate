package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the local player's teleport lock now lasts {@code ticks} more ticks (purely visual on the client). */
public record CombatLockPayload(int ticks) implements CustomPacketPayload {
    public static final Type<CombatLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "combat_lock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatLockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatLockPayload::ticks,
            CombatLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
