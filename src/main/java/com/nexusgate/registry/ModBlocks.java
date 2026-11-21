package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import com.nexusgate.block.TeleportStationBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(NexusGate.MOD_ID);

    public static final DeferredBlock<TeleportStationBlock> TELEPORT_STATION = REGISTER.register("teleport_station",
            () -> new TeleportStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(TeleportStationBlock.HALF) == DoubleBlockHalf.LOWER ? 7 : 4)));

    private ModBlocks() {}
}
