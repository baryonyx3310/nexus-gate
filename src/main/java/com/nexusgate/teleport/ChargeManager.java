package com.nexusgate.teleport;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Owns charging sessions. Completed by the charging task. */
public final class ChargeManager {
    private ChargeManager() {}

    public static void requestStart(ServerPlayer player, UUID sourceId, UUID targetId) {
    }
}
