package com.nexusgate.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** One station in the network. pos is the lower half. */
public record StationRecord(UUID id, String name, ResourceKey<Level> dimension, BlockPos pos, UUID owner, boolean isPublic) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("Pos", pos.asLong());
        tag.putUUID("Owner", owner);
        tag.putBoolean("Public", isPublic);
        return tag;
    }

    public static StationRecord load(CompoundTag tag) {
        return new StationRecord(
                tag.getUUID("Id"),
                tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                BlockPos.of(tag.getLong("Pos")),
                tag.getUUID("Owner"),
                tag.getBoolean("Public"));
    }

    public StationRecord withName(String newName) {
        return new StationRecord(id, newName, dimension, pos, owner, isPublic);
    }

    public StationRecord withPublic(boolean value) {
        return new StationRecord(id, name, dimension, pos, owner, value);
    }
}
