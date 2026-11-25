package com.nexusgate.net;

import com.nexusgate.NexusGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Server → client: purely visual teleport events. Carries no authority; the client only animates with it.
 *
 * @param phase      one of the {@code PHASE_*} constants
 * @param entityId   the teleporting player (network id in the receiving level)
 * @param pos        where the effect happens (player position at start/depart, arrival spot at arrive)
 * @param station    lower block of the source station (or the destination station on arrive)
 * @param totalTicks charge duration, so clients can animate progress without further packets
 * @param message    reason shown on reject/cancel
 */
public record GateFxPayload(byte phase, int entityId, Vec3 pos, BlockPos station, int totalTicks,
                            Optional<Component> message) implements CustomPacketPayload {
    public static final byte PHASE_START = 0;
    public static final byte PHASE_CANCEL = 1;
    public static final byte PHASE_REJECT = 2;
    public static final byte PHASE_DEPART = 3;
    public static final byte PHASE_ARRIVE = 4;

    public static final Type<GateFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "gate_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateFxPayload> STREAM_CODEC =
            StreamCodec.of(GateFxPayload::write, GateFxPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, GateFxPayload p) {
        buf.writeByte(p.phase);
        buf.writeVarInt(p.entityId);
        buf.writeDouble(p.pos.x);
        buf.writeDouble(p.pos.y);
        buf.writeDouble(p.pos.z);
        buf.writeBlockPos(p.station);
        buf.writeVarInt(p.totalTicks);
        ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, p.message);
    }

    private static GateFxPayload read(RegistryFriendlyByteBuf buf) {
        byte phase = buf.readByte();
        int entity = buf.readVarInt();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        BlockPos station = buf.readBlockPos();
        int total = buf.readVarInt();
        Optional<Component> message = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf);
        return new GateFxPayload(phase, entity, pos, station, total, message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
