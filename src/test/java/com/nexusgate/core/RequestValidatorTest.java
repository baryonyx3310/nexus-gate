package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidatorTest {
    private final ChargeSettings d = ChargeSettings.defaults();

    /** sourceExists, dist, targetExists, known, same, cross, combat, xp, creative, cost */
    private static TeleportRequest req(double dist, boolean targetExists, boolean known, boolean same,
                                       boolean cross, int combat, int xp, boolean creative, int cost) {
        return new TeleportRequest(true, dist, targetExists, known, same, cross, combat, xp, creative, cost);
    }

    private static TeleportRequest ok() { return req(2, true, true, false, false, 0, 30, false, 3); }

    @Test void validator_okForValidRequest() { assertEquals(RequestResult.OK, RequestValidator.validateStart(ok(), d)); }

    @Test void validator_rejectsMissingSource() {
        var r = new TeleportRequest(false, 2, true, true, false, false, 0, 30, false, 3);
        assertEquals(RequestResult.NOT_AT_STATION, RequestValidator.validateStart(r, d));
    }

    @Test void validator_rejectsTooFar() {
        assertEquals(RequestResult.NOT_AT_STATION, RequestValidator.validateStart(req(4.5, true, true, false, false, 0, 30, false, 3), d));
        assertEquals(RequestResult.OK, RequestValidator.validateStart(req(4.0, true, true, false, false, 0, 30, false, 3), d));
    }

    @Test void validator_rejectsMissingTarget() {
        assertEquals(RequestResult.TARGET_OFFLINE, RequestValidator.validateStart(req(2, false, true, false, false, 0, 30, false, 3), d));
    }

    @Test void validator_rejectsUnknownPrivateTarget() {
        assertEquals(RequestResult.TARGET_UNKNOWN, RequestValidator.validateStart(req(2, true, false, false, false, 0, 30, false, 3), d));
    }

    @Test void validator_rejectsSameStation() {
        assertEquals(RequestResult.SAME_STATION, RequestValidator.validateStart(req(2, true, true, true, false, 0, 30, false, 3), d));
    }

    @Test void validator_rejectsCrossDimWhenDisabled() {
        var noCross = new ChargeSettings(5, 1, 12, 3, 1.5, 10, 4.0, false);
        var r = req(2, true, true, false, true, 0, 30, false, 5);
        assertEquals(RequestResult.CROSS_DIMENSION_DISABLED, RequestValidator.validateStart(r, noCross));
        assertEquals(RequestResult.OK, RequestValidator.validateStart(r, d));
    }

    @Test void validator_rejectsInCombat() {
        assertEquals(RequestResult.IN_COMBAT, RequestValidator.validateStart(req(2, true, true, false, false, 1, 30, false, 3), d));
    }

    @Test void validator_rejectsWithoutXp() {
        assertEquals(RequestResult.NOT_ENOUGH_XP, RequestValidator.validateStart(req(2, true, true, false, false, 0, 2, false, 3), d));
        assertEquals(RequestResult.OK, RequestValidator.validateStart(req(2, true, true, false, false, 0, 2, true, 3), d));
    }

    @Test void validator_completionRecheckFailsWithoutXp() {
        assertEquals(RequestResult.NOT_ENOUGH_XP, RequestValidator.validateCompletion(2, false, 3));
        assertEquals(RequestResult.OK, RequestValidator.validateCompletion(3, false, 3));
        assertEquals(RequestResult.OK, RequestValidator.validateCompletion(0, true, 3));
    }

    @Test void resultTranslationKeys() {
        assertEquals("msg.nexusgate.in_combat", RequestResult.IN_COMBAT.translationKey());
        assertEquals("msg.nexusgate.not_enough_xp", RequestResult.NOT_ENOUGH_XP.translationKey());
    }
}
