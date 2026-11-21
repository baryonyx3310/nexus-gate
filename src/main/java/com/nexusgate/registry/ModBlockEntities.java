package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import com.nexusgate.block.TeleportStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NexusGate.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TeleportStationBlockEntity>> TELEPORT_STATION = REGISTER.register("teleport_station",
            () -> BlockEntityType.Builder.of(TeleportStationBlockEntity::new, ModBlocks.TELEPORT_STATION.get()).build(null));

    private ModBlockEntities() {}
}
