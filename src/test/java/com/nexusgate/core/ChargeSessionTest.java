package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeSessionTest {
    private static TickInput still() { return new TickInput(true, false, 0, 0, 0, true, true); }

    @Test void session_completesAfterTotalTicks() {
        var s = new ChargeSession(100, 1.5);
        for (int i = 0; i < 99; i++) assertEquals(ChargeOutcome.CONTINUE, s.tick(still()));
        assertEquals(ChargeOutcome.COMPLETE, s.tick(still()));
        assertEquals(100, s.elapsed());
        assertEquals(100, s.total());
    }

    @Test void session_cancelsOnDamage() {
        var s = new ChargeSession(100, 1.5);
        assertEquals(ChargeOutcome.CANCEL_DAMAGE, s.tick(new TickInput(true, true, 0, 0, 0, true, true)));
    }

    @Test void session_cancelsWhenMoved() {
        var s = new ChargeSession(100, 1.5);
        assertEquals(ChargeOutcome.CANCEL_MOVED, s.tick(new TickInput(true, false, 1.6, 0, 0, true, true)));
    }

    @Test void session_cancelsWhenTargetGone() {
        var s = new ChargeSession(100, 1.5);
        assertEquals(ChargeOutcome.CANCEL_STATION, s.tick(new TickInput(true, false, 0, 0, 0, true, false)));
    }

    @Test void session_cancelsWhenSourceGone() {
        var s = new ChargeSession(100, 1.5);
        assertEquals(ChargeOutcome.CANCEL_STATION, s.tick(new TickInput(true, false, 0, 0, 0, false, true)));
    }

    @Test void session_cancelsWhenPlayerGone() {
        var s = new ChargeSession(100, 1.5);
        assertEquals(ChargeOutcome.CANCEL_GONE, s.tick(new TickInput(false, true, 5, 0, 0, false, false)));
    }
}
