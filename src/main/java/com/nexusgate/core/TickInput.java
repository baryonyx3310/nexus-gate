package com.nexusgate.core;

/** Per-tick facts about a charging player. dx/dy/dz are the offset from the start position. */
public record TickInput(boolean playerPresent, boolean tookDamage, double dx, double dy, double dz,
                        boolean sourceExists, boolean targetExists) {
}
