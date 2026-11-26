package com.nexusgate.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelocationTimelineTest {
    private static final float EPS = 1e-4f;

    @Test
    void bannerOnlyDuringConnection() {
        assertTrue(RelocationTimeline.connecting(0f));
        assertTrue(RelocationTimeline.connecting(0.2f));
        assertFalse(RelocationTimeline.connecting(0.25f));
        assertFalse(RelocationTimeline.connecting(0.6f));
        assertEquals(1f, RelocationTimeline.bannerAlpha(0.1f), EPS);
        assertEquals(0f, RelocationTimeline.bannerAlpha(0.3f), EPS);
    }

    @Test
    void beamReachesTheBodyBeforeDematerialisation() {
        assertEquals(0f, RelocationTimeline.beamReach(0.05f), EPS);
        assertEquals(1f, RelocationTimeline.beamReach(RelocationTimeline.CONNECT_END), EPS);
        assertTrue(RelocationTimeline.dissolve(RelocationTimeline.CONNECT_END) < 0.15f);
    }

    @Test
    void bodyIsFullyConvertedBeforeTheServerTeleports() {
        assertEquals(1f, RelocationTimeline.dissolve(RelocationTimeline.DISSOLVE_END), EPS);
        assertEquals(1f, RelocationTimeline.dissolve(1f), EPS);
        assertTrue(RelocationTimeline.DISSOLVE_END < 1f);
    }

    @Test
    void dissolveIsMonotonic() {
        float prev = -1f;
        for (int i = 0; i <= 100; i++) {
            float d = RelocationTimeline.dissolve(i / 100f);
            assertTrue(d >= prev);
            prev = d;
        }
    }

    @Test
    void pointTravelsIntoTheCoreBeforeTheServerTeleports() {
        assertEquals(0f, RelocationTimeline.focalTravel(RelocationTimeline.DISSOLVE_END), EPS);
        assertEquals(1f, RelocationTimeline.focalTravel(RelocationTimeline.TRAVEL_END), EPS);
        assertTrue(RelocationTimeline.TRAVEL_END < 1f);
    }

    @Test
    void uplinkFiresAtTheEnd() {
        assertEquals(0f, RelocationTimeline.uplink(0.5f), EPS);
        assertEquals(1f, RelocationTimeline.uplink(1f), EPS);
        assertEquals(1f, RelocationTimeline.release(0f), EPS);
        assertEquals(0f, RelocationTimeline.release(RelocationTimeline.RELEASE_TICKS), EPS);
    }

    @Test
    void cancelBringsTheBodyBack() {
        assertEquals(0.6f, RelocationTimeline.rematerialize(0.6f, 0f), EPS);
        assertEquals(0f, RelocationTimeline.rematerialize(0.6f, RelocationTimeline.REMATERIALIZE_TICKS), EPS);
    }

    @Test
    void receptionRebuildsTheBodyAndSettles() {
        assertEquals(1f, RelocationTimeline.reconstruct(0f), EPS);
        assertEquals(1f, RelocationTimeline.reconstruct(20f), EPS);
        assertEquals(0f, RelocationTimeline.reconstruct(52f), EPS);
        assertEquals(1f, RelocationTimeline.arrivalTravel(10f), EPS);
        assertEquals(0f, RelocationTimeline.arrivalTravel(22f), EPS);
        assertEquals(0f, RelocationTimeline.receptionCore(RelocationTimeline.RECEPTION_TICKS), EPS);
        assertEquals(0f, RelocationTimeline.skyIntensity(RelocationTimeline.RECEPTION_TICKS), EPS);
        assertEquals(1f, RelocationTimeline.skyReach(10f), EPS);
        assertTrue(RelocationTimeline.receptionCore(25f) > 0.99f);
    }

    @Test
    void phaseAComesBeforeConversion() {
        assertTrue(RelocationTimeline.instability(0.2f) > 0f);
        assertTrue(RelocationTimeline.dissolve(0.2f) < 0.1f);
        assertTrue(RelocationTimeline.bodyLens(0.2f) > 0f);
        assertTrue(RelocationTimeline.bodyLens(0.6f) > RelocationTimeline.bodyLens(0.2f));
        assertEquals(0f, RelocationTimeline.bodyLens(1f), EPS);
        assertTrue(RelocationTimeline.coreLens(1f) > 0f);
    }

    @Test
    void arrivalDistortsBeforeMatterAppears() {
        assertTrue(RelocationTimeline.arrivalLens(18f) > 0f);
        assertEquals(1f, RelocationTimeline.reconstruct(18f), EPS);
        assertEquals(0f, RelocationTimeline.arrivalLens(RelocationTimeline.RECEPTION_TICKS), EPS);
        assertEquals(0f, RelocationTimeline.arrivalInstability(RelocationTimeline.RECEPTION_TICKS), EPS);
    }

    @Test
    void bodyEncodingSeparatesInstabilityFromConversion() {
        assertEquals(0, RelocationTimeline.encodeBody(0f, 0f));
        assertEquals(RelocationTimeline.CONVERSION_BASE - 1, RelocationTimeline.encodeBody(1f, 0f));
        assertEquals(RelocationTimeline.CONVERSION_BASE + 1, RelocationTimeline.encodeBody(1f, 0.004f), 1);
        assertEquals(255, RelocationTimeline.encodeBody(1f, 1f));
    }
}
