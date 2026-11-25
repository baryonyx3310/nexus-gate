package com.nexusgate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Client mirror of the local player's teleport lock, used only for visuals (the station siren). Game time is shared
 * by every dimension of a world, so the lock survives dimension changes; it is dropped on a new connection.
 */
public final class ClientCombatLock {
    private static long untilGameTime = Long.MIN_VALUE;
    private static ClientPacketListener connection;

    private ClientCombatLock() {}

    public static void set(int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        connection = Minecraft.getInstance().getConnection();
        untilGameTime = level.getGameTime() + ticks;
    }

    public static boolean isLocked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() != connection) return false;
        return mc.level.getGameTime() < untilGameTime;
    }
}
