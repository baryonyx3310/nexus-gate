package com.nexusgate.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All stations of the server, stored on the overworld. */
public final class StationNetwork extends SavedData {
    private static final String FILE_ID = "nexusgate_stations";
    private final Map<UUID, StationRecord> stations = new LinkedHashMap<>();

    public static StationNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(StationNetwork::new, StationNetwork::load, null), FILE_ID);
    }

    private static StationNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        StationNetwork network = new StationNetwork();
        ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StationRecord record = StationRecord.load(list.getCompound(i));
            network.stations.put(record.id(), record);
        }
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stations.values().forEach(r -> list.add(r.save()));
        tag.put("Stations", list);
        return tag;
    }

    public void add(StationRecord record) {
        stations.put(record.id(), record);
        setDirty();
    }

    public void update(StationRecord record) {
        if (stations.containsKey(record.id())) {
            stations.put(record.id(), record);
            setDirty();
        }
    }

    public void remove(UUID id) {
        if (stations.remove(id) != null) setDirty();
    }

    public Optional<StationRecord> get(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    public Optional<StationRecord> at(ResourceKey<Level> dimension, BlockPos pos) {
        return stations.values().stream().filter(r -> r.dimension().equals(dimension) && r.pos().equals(pos)).findFirst();
    }

    public Collection<StationRecord> all() {
        return Collections.unmodifiableCollection(stations.values());
    }
}
