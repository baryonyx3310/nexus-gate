package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatLockTest {
    @Test void neverInCombatHasNoLock() { assertEquals(0, CombatLock.remainingTicks(CombatLock.NEVER, 1000)); }
    @Test void activeLockReturnsRemainder() { assertEquals(150, CombatLock.remainingTicks(1150, 1000)); }
    @Test void expiredLockHasNoLock() { assertEquals(0, CombatLock.remainingTicks(800, 1000)); }

    @Test void extendStartsFromNever() { assertEquals(1200, CombatLock.extend(CombatLock.NEVER, 1000, 10)); }
    @Test void extendNeverShortens() { assertEquals(1300, CombatLock.extend(1300, 1000, 5)); }
    @Test void extendLengthens() { assertEquals(1300, CombatLock.extend(1100, 1000, 15)); }

    @Test void hurtLockScalesWithDamage() {
        assertEquals(5, CombatLock.hurtLockSeconds(0.5f, 5, 15, 20f));
        assertEquals(6, CombatLock.hurtLockSeconds(2f, 5, 15, 20f));
        assertEquals(10, CombatLock.hurtLockSeconds(10f, 5, 15, 20f));
        assertEquals(15, CombatLock.hurtLockSeconds(20f, 5, 15, 20f));
        assertEquals(15, CombatLock.hurtLockSeconds(80f, 5, 15, 20f));
    }
}
