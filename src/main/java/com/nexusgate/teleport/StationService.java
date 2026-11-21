package com.nexusgate.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Server-side station lifecycle. */
public final class StationService {
    private StationService() {}

    public static void onPlaced(ServerLevel level, BlockPos lowerPos, ServerPlayer placer) {
    }

    public static InteractionResult onUse(ServerLevel level, BlockPos lowerPos, ServerPlayer player) {
        return InteractionResult.PASS;
    }

    public static void onRemoved(ServerLevel level, BlockPos lowerPos) {
    }
}
