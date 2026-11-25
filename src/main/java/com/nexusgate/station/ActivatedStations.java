package com.nexusgate.station;

import com.nexusgate.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Per-player set of activated stations (persistent, kept on death). */
public final class ActivatedStations {
    private ActivatedStations() {}

    public static Set<UUID> of(ServerPlayer player) {
        return Set.copyOf(player.getData(ModAttachments.ACTIVATED));
    }

    public static boolean isActivated(ServerPlayer player, UUID station) {
        return player.getData(ModAttachments.ACTIVATED).contains(station);
    }

    /** @return true if it was newly activated. */
    public static boolean activate(ServerPlayer player, UUID station) {
        Set<UUID> next = new HashSet<>(player.getData(ModAttachments.ACTIVATED));
        boolean added = next.add(station);
        if (added) player.setData(ModAttachments.ACTIVATED, next);
        return added;
    }
}
