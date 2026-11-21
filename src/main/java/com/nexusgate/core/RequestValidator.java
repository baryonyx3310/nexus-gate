package com.nexusgate.core;

public final class RequestValidator {
    private RequestValidator() {}

    public static RequestResult validateStart(TeleportRequest r, ChargeSettings s) {
        if (!r.sourceExists() || r.distanceToSource() > s.stationUseRange()) return RequestResult.NOT_AT_STATION;
        if (!r.targetExists()) return RequestResult.TARGET_OFFLINE;
        if (!r.targetKnown()) return RequestResult.TARGET_UNKNOWN;
        if (r.sameStation()) return RequestResult.SAME_STATION;
        if (r.crossDimension() && !s.allowCrossDimension()) return RequestResult.CROSS_DIMENSION_DISABLED;
        if (r.combatRemainingTicks() > 0) return RequestResult.IN_COMBAT;
        return validateCompletion(r.playerXpLevels(), r.creative(), r.xpCost(), true);
    }

    public static RequestResult validateCompletion(int playerXpLevels, boolean creative, int xpCost, boolean targetBlockPresent) {
        if (!targetBlockPresent) return RequestResult.TARGET_OFFLINE;
        return creative || playerXpLevels >= xpCost ? RequestResult.OK : RequestResult.NOT_ENOUGH_XP;
    }
}
