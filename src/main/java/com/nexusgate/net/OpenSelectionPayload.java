package com.nexusgate.net;

import com.nexusgate.NexusGate;
import com.nexusgate.core.StationEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server → client: open the station selection terminal. */
public record OpenSelectionPayload(String sourceId, String sourceName, boolean canRename, boolean isOp,
                                   boolean sourcePublic, int sourceIcon, int combatRemainingTicks,
                                   List<StationEntry> entries)
        implements CustomPacketPayload {

    public static final Type<OpenSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "open_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSelectionPayload> STREAM_CODEC =
            StreamCodec.of(OpenSelectionPayload::write, OpenSelectionPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenSelectionPayload p) {
        buf.writeUtf(p.sourceId);
        buf.writeUtf(p.sourceName);
        buf.writeBoolean(p.canRename);
        buf.writeBoolean(p.isOp);
        buf.writeBoolean(p.sourcePublic);
        buf.writeVarInt(p.sourceIcon);
        buf.writeVarInt(p.combatRemainingTicks);
        buf.writeVarInt(p.entries.size());
        for (StationEntry e : p.entries) writeEntry(buf, e);
    }

    private static OpenSelectionPayload read(RegistryFriendlyByteBuf buf) {
        String sourceId = buf.readUtf();
        String sourceName = buf.readUtf();
        boolean canRename = buf.readBoolean();
        boolean isOp = buf.readBoolean();
        boolean sourcePublic = buf.readBoolean();
        int sourceIcon = buf.readVarInt();
        int combat = buf.readVarInt();
        int size = buf.readVarInt();
        List<StationEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(readEntry(buf));
        return new OpenSelectionPayload(sourceId, sourceName, canRename, isOp, sourcePublic, sourceIcon, combat, entries);
    }

    private static void writeEntry(FriendlyByteBuf buf, StationEntry e) {
        buf.writeUtf(e.id());
        buf.writeUtf(e.name());
        buf.writeUtf(e.dimension());
        buf.writeBoolean(e.isPublic());
        buf.writeDouble(e.distance());
        buf.writeVarInt(e.xpCost());
        buf.writeBoolean(e.affordable());
        buf.writeVarInt(e.icon());
    }

    private static StationEntry readEntry(FriendlyByteBuf buf) {
        return new StationEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readDouble(),
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
