package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocatorArrowTest {
    @Test void arrow_straightAhead() { assertEquals("↑", LocatorArrow.arrow(0, 10, 0f)); }
    @Test void arrow_eastWhenFacingSouth() { assertEquals("←", LocatorArrow.arrow(10, 0, 0f)); }
    @Test void arrow_eastWhenFacingNorth() { assertEquals("→", LocatorArrow.arrow(10, 0, 180f)); }
    @Test void arrow_behind() { assertEquals("↓", LocatorArrow.arrow(0, -10, 0f)); }
    @Test void arrow_diagonal() { assertEquals("↗", LocatorArrow.arrow(10, -10, 180f)); }
    @Test void arrow_negativeYaw() { assertEquals(LocatorArrow.arrow(10, -10, 180f), LocatorArrow.arrow(10, -10, -180f)); }
    @Test void arrow_largeYawWraps() { assertEquals("↑", LocatorArrow.arrow(0, 10, 720f)); }
}
