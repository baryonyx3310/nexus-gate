package com.nexusgate.teleport;

import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.core.SafeArrival;
import com.nexusgate.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Moves a player next to a destination station. */
public final class TeleportExecutor {
    private TeleportExecutor() {}

    /** Loads the destination chunk and checks that the station block is really there. */
    public static boolean targetBlockPresent(MinecraftServer server, StationRecord target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null) return false;
        BlockPos base = target.pos();
        // Keep the destination chunk loaded through the teleport, like vanilla portals do.
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(base), 1, base);
        level.getChunk(base);
        return StationService.isStationBlock(level, base);
    }

    /** @return false when there is no safe spot around the station (nothing happens then). */
    public static boolean teleport(ServerPlayer player, StationRecord target) {
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) return false;
        BlockPos base = target.pos();
        int[] offset = SafeArrival.pick((x, z) -> isSafeColumn(level, base.offset(x, 0, z)), isSafeTop(level, base.above(2)));
        if (offset == null) return false;
        BlockPos dest = base.offset(offset[0], offset[2], offset[1]);
        float yaw = offset[2] == 0 ? (float) Math.toDegrees(Math.atan2(offset[0], -offset[1])) : player.getYRot();

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

    /** Feet and head passable and harmless, solid harmless floor below. */
    private static boolean isSafeColumn(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        return isSafeSpace(level, feet) && isSafeSpace(level, feet.above())
                && floor.isFaceSturdy(level, below, Direction.UP)
                && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(BlockTags.CAMPFIRES)
                && !(floor.getBlock() instanceof TeleportStationBlock);
    }

    /** Standing on the station top: only needs two safe blocks of headroom. */
    private static boolean isSafeTop(ServerLevel level, BlockPos feet) {
        return isSafeSpace(level, feet) && isSafeSpace(level, feet.above());
    }

    private static boolean isSafeSpace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.POWDER_SNOW)
                && !state.is(Blocks.CACTUS)
                && !state.is(Blocks.WITHER_ROSE)
                && !(state.getBlock() instanceof TeleportStationBlock && state.getValue(TeleportStationBlock.HALF) == DoubleBlockHalf.UPPER);
    }
}
