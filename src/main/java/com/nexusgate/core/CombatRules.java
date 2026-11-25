package com.nexusgate.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CombatRules {
    private CombatRules() {}

    /**
     * Players involved in a hit, judged on the incoming amount (before shields, armour or absorption).
     * victim / attacker are null when that side is not a player.
     */
    public static Set<UUID> participants(UUID victim, UUID attacker, float incomingAmount) {
        Set<UUID> out = new HashSet<>();
        if (incomingAmount <= 0) return out;
        if (victim != null) out.add(victim);
        if (attacker != null) out.add(attacker);
        return out;
    }
}
