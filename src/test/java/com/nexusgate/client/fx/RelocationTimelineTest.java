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
        assertEquals(0f, RelocationTimeline.dissolve(RelocationTimeline.CONNECT_END), EPS);
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
        assertEquals(1f, RelocationTimeline.reconstruct(15f), EPS);
        assertEquals(0f, RelocationTimeline.reconstruct(48f), EPS);
        assertEquals(0f, RelocationTimeline.receptionCore(RelocationTimeline.RECEPTION_TICKS), EPS);
        assertEquals(0f, RelocationTimeline.skyIntensity(RelocationTimeline.RECEPTION_TICKS), EPS);
        assertEquals(1f, RelocationTimeline.skyReach(10f), EPS);
        assertTrue(RelocationTimeline.receptionCore(25f) > 0.99f);
    }
}
