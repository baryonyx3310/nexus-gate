package com.nexusgate.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapseFieldTest {
    private static final float EPS = 1e-4f;
    private final float[] out = new float[3];

    private float distanceFromFocus(float px, float py, float pz, float c) {
        CollapseField.collapse(px, py, pz, 0f, 1f, 0f, c, 2f, out);
        float dx = out[0], dy = out[1] - 1f, dz = out[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void intactAtZeroCompression() {
        CollapseField.collapse(0.3f, 1.7f, -0.2f, 0f, 1f, 0f, 0f, 2f, out);
        assertEquals(0.3f, out[0], EPS);
        assertEquals(1.7f, out[1], EPS);
        assertEquals(-0.2f, out[2], EPS);
    }

    @Test
    void everythingConvergesOnTheFocalPoint() {
        CollapseField.collapse(0.4f, 1.9f, 0.3f, 0.1f, 1f, -0.2f, 1f, 2f, out);
        assertEquals(0.1f, out[0], EPS);
        assertEquals(1f, out[1], EPS);
        assertEquals(-0.2f, out[2], EPS);
    }

    @Test
    void neverPushesAnythingOutward() {
        float prev = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float d = distanceFromFocus(0.35f, 1.9f, 0.1f, i / 100f);
            assertTrue(d <= prev + EPS, "distance grew at c=" + i / 100f);
            prev = d;
        }
    }

    @Test
    void extremitiesLeadTheCollapse() {
        float c = 0.4f;
        float near = distanceFromFocus(0.1f, 1.1f, 0f, c) / 0.1414f;
        // Same direction from the focus, six times farther out.
        float far = distanceFromFocus(0.6f, 1.6f, 0f, c) / 0.8485f;
        assertTrue(far < near);
    }

    @Test
    void silhouetteNarrowsBeforeItShortens() {
        CollapseField.collapse(0.3f, 1.9f, 0f, 0f, 1f, 0f, 0.15f, 0f, out);
        float widthKept = Math.abs(out[0]) / 0.3f;
        float heightKept = (out[1] - 1f) / 0.9f;
        assertTrue(widthKept < heightKept);
    }
}
