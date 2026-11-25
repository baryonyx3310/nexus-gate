package com.nexusgate.block;

import com.nexusgate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import com.nexusgate.teleport.TeleportExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    /** Station name mirrored from the StationRecord, synced to clients for the crown screen. */
    private String displayName = "";
    /** True when no safe arrival spot exists around the station (siren turns red). Recomputed on the server. */
    private boolean arrivalBlocked;

    public boolean isArrivalBlocked() {
        return arrivalBlocked;
    }

    /** Server ticker: re-checks the arrival area every 2 seconds and syncs changes to clients. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleportStationBlockEntity be) {
        if (!(level instanceof ServerLevel server) || Math.floorMod(level.getGameTime() + pos.hashCode(), 40L) != 0) return;
        boolean blocked = !TeleportExecutor.hasSafeArrival(server, pos);
        if (blocked == be.arrivalBlocked) return;
        be.arrivalBlocked = blocked;
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        if (name == null || name.equals(displayName)) return;
        displayName = name;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", displayName);
        tag.putBoolean("ArrivalBlocked", arrivalBlocked);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        tag.putString("Name", displayName);
        tag.putBoolean("ArrivalBlocked", arrivalBlocked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("StationId")) stationId = tag.getUUID("StationId");
        displayName = tag.getString("Name");
        arrivalBlocked = tag.getBoolean("ArrivalBlocked");
    }
}
