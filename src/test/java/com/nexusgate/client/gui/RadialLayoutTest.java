package com.nexusgate.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialLayoutTest {
    @Test
    void pagesHoldEightChambers() {
        assertEquals(1, RadialLayout.pages(0));
        assertEquals(1, RadialLayout.pages(8));
        assertEquals(2, RadialLayout.pages(9));
        assertEquals(3, RadialLayout.pages(17));
    }

    @Test
    void entryAtMapsPageAndSlot() {
        assertEquals(0, RadialLayout.entryAt(0, 0, 3));
        assertEquals(-1, RadialLayout.entryAt(0, 3, 3));
        assertEquals(9, RadialLayout.entryAt(1, 1, 10));
        assertEquals(-1, RadialLayout.entryAt(1, 2, 10));
        assertEquals(1, RadialLayout.pageOf(9));
        assertEquals(1, RadialLayout.slotOf(9));
    }

    @Test
    void alignRotationBringsSlotToTopTheShortWay() {
        for (int slot = 0; slot < RadialLayout.CHAMBERS; slot++) {
            float current = 13.7f;
            float r = RadialLayout.alignRotation(slot, current);
            float atTop = RadialLayout.wrap(RadialLayout.slotAngle(slot) + r);
            assertEquals(0f, atTop, 1e-4f, "slot " + slot);
            assertTrue(Math.abs(r - current) <= Math.PI + 1e-4, "no long spin for slot " + slot);
        }
    }

    @Test
    void chamberHitTestFollowsRotation() {
        float cx = 100, cy = 100, ring = 40, radius = 8;
        // Slot 0 is straight up without rotation.
        assertEquals(0, RadialLayout.chamberAt(100, 60, cx, cy, 0f, ring, radius));
        // Rotating by one step clockwise moves slot 0 to the 1:30 position; slot 7 comes to the top.
        float step = RadialLayout.STEP;
        assertEquals(7, RadialLayout.chamberAt(100, 60, cx, cy, step, ring, radius));
        assertEquals(-1, RadialLayout.chamberAt(100, 100, cx, cy, 0f, ring, radius));
    }
}
