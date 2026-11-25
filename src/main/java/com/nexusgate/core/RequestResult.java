package com.nexusgate.core;

import java.util.Locale;

/** Validation outcome. Declaration order is the order checks run in. */
public enum RequestResult {
    OK, NOT_AT_STATION, TARGET_OFFLINE, TARGET_UNKNOWN, SAME_STATION, CROSS_DIMENSION_DISABLED, IN_COMBAT, NOT_ENOUGH_XP;

    public String translationKey() {
        return "msg.nexusgate." + name().toLowerCase(Locale.ROOT);
    }
}
