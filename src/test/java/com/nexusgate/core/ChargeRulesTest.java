package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeRulesTest {
    private final ChargeSettings d = ChargeSettings.defaults();

    @Test void baseChargeIsFiveSeconds() { assertEquals(100, ChargeRules.chargeTicks(0, false, d)); }
    @Test void eachHostileAddsOneSecond() { assertEquals(160, ChargeRules.chargeTicks(3, false, d)); }
    @Test void chargeIsCappedAtTwelveSeconds() { assertEquals(240, ChargeRules.chargeTicks(10, false, d)); }
    @Test void crossDimensionAddsThreeSeconds() { assertEquals(160, ChargeRules.chargeTicks(0, true, d)); }
    @Test void crossDimensionStillCapped() { assertEquals(240, ChargeRules.chargeTicks(5, true, d)); }

    @Test void movingExactlyMaxIsAllowed() { assertFalse(ChargeRules.movedTooFar(1.5, 0, 0, 1.5)); }
    @Test void movingBeyondMaxIsTooFar() { assertTrue(ChargeRules.movedTooFar(1.2, 0, 1.0, 1.5)); }

    @Test void progressBarHalf() { assertEquals("[■■■■■□□□□□] 50%", ChargeRules.progressBar(50, 100)); }
    @Test void progressBarEmpty() { assertEquals("[□□□□□□□□□□] 0%", ChargeRules.progressBar(0, 100)); }
    @Test void progressBarClampedFull() { assertEquals("[■■■■■■■■■■] 100%", ChargeRules.progressBar(150, 100)); }
}
