package com.nexusgate.core;

import java.util.Comparator;
import java.util.List;

public final class StationSorter {
    private StationSorter() {}

    public static List<StationEntry> sort(List<StationEntry> in, String currentDimension) {
        return in.stream()
                .sorted(Comparator.<StationEntry>comparingInt(e -> e.dimension().equals(currentDimension) ? 0 : 1)
                        .thenComparing(StationEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
