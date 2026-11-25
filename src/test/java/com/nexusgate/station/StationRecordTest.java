package com.nexusgate.station;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StationRecordTest {
    @Test void record_roundTripsThroughNbt() {
        var r = new StationRecord(UUID.randomUUID(), "Base Nether", Level.NETHER, new BlockPos(10, 64, -20), UUID.randomUUID(), true);
        assertEquals(r, StationRecord.load(r.save()));
    }

    @Test void record_iconRoundTripsAndDefaultsToPin() {
        var r = new StationRecord(UUID.randomUUID(), "Casa", Level.OVERWORLD, BlockPos.ZERO, UUID.randomUUID(), false).withIcon(3);
        assertEquals(3, StationRecord.load(r.save()).icon());
        var legacy = r.save();
        legacy.remove("Icon");
        assertEquals(0, StationRecord.load(legacy).icon());
        assertEquals(3, r.withName("B").icon());
        assertEquals(3, r.withPublic(true).icon());
    }

    @Test void record_withersReturnCopies() {
        var r = new StationRecord(UUID.randomUUID(), "A", Level.OVERWORLD, BlockPos.ZERO, UUID.randomUUID(), false);
        assertEquals("B", r.withName("B").name());
        assertTrue(r.withPublic(true).isPublic());
        assertFalse(r.isPublic());
    }
}
