package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    private final UUID victim = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();

    /** Incoming damage counts even if absorption hearts or a shield later reduce it to zero. */
    @Test void absorbedHitStillFlagsVictim() {
        assertEquals(Set.of(victim), CombatRules.participants(victim, null, 4.0f));
    }

    /** Dealing damage during a charge must also cancel it (no kiting while charging). */
    @Test void attackerIsFlagged() {
        assertEquals(Set.of(victim, attacker), CombatRules.participants(victim, attacker, 2.0f));
    }

    @Test void playerHittingMobFlagsOnlyAttacker() {
        assertEquals(Set.of(attacker), CombatRules.participants(null, attacker, 2.0f));
    }

    @Test void zeroIncomingIsNotCombat() {
        assertTrue(CombatRules.participants(victim, attacker, 0f).isEmpty());
    }

    @Test void selfDamageFlagsOnce() {
        assertEquals(Set.of(victim), CombatRules.participants(victim, victim, 1.0f));
    }
}
