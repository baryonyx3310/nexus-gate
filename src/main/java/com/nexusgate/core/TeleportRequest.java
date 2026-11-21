package com.nexusgate.core;

/** Facts the server gathered about a teleport request, checked by {@link RequestValidator}. */
public record TeleportRequest(boolean sourceExists, double distanceToSource, boolean targetExists,
                              boolean targetKnown, boolean sameStation, boolean crossDimension,
                              int combatRemainingTicks, int playerXpLevels, boolean creative, int xpCost) {
}
