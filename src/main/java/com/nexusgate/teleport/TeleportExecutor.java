package com.nexusgate.teleport;

import com.nexusgate.core.SafeArrival;
import com.nexusgate.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

/** Moves a player next to a destination station. */
public final class TeleportExecutor {
    private TeleportExecutor() {}

    public static boolean teleport(ServerPlayer player, StationRecord target) {
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) return false;
        BlockPos base = target.pos();
        // Keep the destination chunk loaded through the teleport, like vanilla portals do.
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(base), 1, base);
        level.getChunk(base);

        int[] offset = SafeArrival.pick((x, z) -> isFree(level, base.offset(x, 0, z)));
        BlockPos dest = base.offset(offset[0], offset[2], offset[1]);
        float yaw = offset[2] == 0 ? Direction.fromDelta(-offset[0], 0, -offset[1]).toYRot() : player.getYRot();

        player.stopRiding();
        player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, yaw, player.getXRot());
        player.resetFallDistance();

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5,
                40, 0.4, 0.8, 0.4, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5,
                20, 0.5, 1.0, 0.5, 0.1);
        level.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    /** Feet and head blocks passable, solid floor below. */
    private static boolean isFree(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }
}
