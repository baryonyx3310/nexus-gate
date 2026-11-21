package com.nexusgate.block;

import com.mojang.serialization.MapCodec;
import com.nexusgate.teleport.StationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Two-block-tall teleport station. The lower half owns the block entity. */
public class TeleportStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TeleportStationBlock> CODEC = simpleCodec(TeleportStationBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(3, 3, 3, 13, 16, 13));
    private static final VoxelShape UPPER_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 12, 14),
            Block.box(7, 12, 7, 9, 16, 9));

    public TeleportStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            StationService.onPlaced(serverLevel, pos, player);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = half == DoubleBlockHalf.LOWER ? direction == Direction.UP : direction == Direction.DOWN;
        if (towardsOtherHalf && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative, breaking the upper half must not make the lower half drop its item.
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !newState.is(this) && level instanceof ServerLevel serverLevel) {
            StationService.onRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            return StationService.onUse(serverLevel, lowerPos(state, pos), serverPlayer);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
        double y = pos.getY() + 0.4 + random.nextDouble() * 1.4;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.02, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TeleportStationBlockEntity(pos, state) : null;
    }
}
