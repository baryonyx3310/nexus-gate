package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XpCostTest {
    private final XpSettings x = XpSettings.defaults();

    @Test void zeroDistanceCostsBase() { assertEquals(1, XpCost.levels(0, false, x)); }
    @Test void justBelowStepCostsBase() { assertEquals(1, XpCost.levels(499, false, x)); }
    @Test void eachFiveHundredBlocksAddsLevel() { assertEquals(2, XpCost.levels(500, false, x)); }
    @Test void floorsPartialSteps() { assertEquals(3, XpCost.levels(1250, false, x)); }
    @Test void sameDimensionCappedAtTen() { assertEquals(10, XpCost.levels(100_000, false, x)); }
    @Test void crossDimensionIsFlatFive() { assertEquals(5, XpCost.levels(10, true, x)); }
    @Test void disabledCostsNothing() { assertEquals(0, XpCost.levels(5000, false, new XpSettings(false, 1, 500, 10, 5))); }
}
