package com.nexusgate.block;

import com.nexusgate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Lives on the lower half; links the block to its StationRecord. */
public class TeleportStationBlockEntity extends BlockEntity {
    @Nullable
    private UUID stationId;

    public TeleportStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORT_STATION.get(), pos, state);
    }

    @Nullable
    public UUID getStationId() {
        return stationId;
    }

    public void setStationId(UUID id) {
        this.stationId = id;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stationId != null) tag.putUUID("StationId", stationId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stationId = tag.hasUUID("StationId") ? tag.getUUID("StationId") : null;
    }
}
