package com.nexusgate.core;

/** One row of the selection list. distance is -1 when the station is in another dimension; icon indexes the station icons. */
public record StationEntry(String id, String name, String dimension, boolean isPublic, double distance,
                           int xpCost, boolean affordable, int icon) {

    public StationEntry(String id, String name, String dimension, boolean isPublic, double distance, int xpCost, boolean affordable) {
        this(id, name, dimension, isPublic, distance, xpCost, affordable, 0);
    }
}
