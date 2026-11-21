package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatLockTest {
    @Test void neverInCombatHasNoLock() { assertEquals(0, CombatLock.remainingTicks(CombatLock.NEVER, 1000, 10)); }
    @Test void recentCombatLocksForRemainder() { assertEquals(150, CombatLock.remainingTicks(950, 1000, 10)); }
    @Test void oldCombatHasNoLock() { assertEquals(0, CombatLock.remainingTicks(800, 1000, 10)); }
}
