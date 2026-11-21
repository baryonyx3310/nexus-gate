package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StationSorterTest {
    private static StationEntry e(String name, String dim) {
        return new StationEntry(name, name, dim, false, 0, 1, true);
    }

    @Test void sorter_currentDimFirstThenAlpha() {
        var in = List.of(e("Zeta", "minecraft:the_nether"), e("beta", "minecraft:overworld"), e("Alpha", "minecraft:overworld"));
        var out = StationSorter.sort(in, "minecraft:overworld").stream().map(StationEntry::name).toList();
        assertEquals(List.of("Alpha", "beta", "Zeta"), out);
    }

    @Test void sorter_otherDimsAlphabetical() {
        var in = List.of(e("b", "minecraft:the_end"), e("a", "minecraft:the_nether"), e("c", "minecraft:overworld"));
        var out = StationSorter.sort(in, "minecraft:overworld").stream().map(StationEntry::name).toList();
        assertEquals(List.of("c", "a", "b"), out);
    }
}
