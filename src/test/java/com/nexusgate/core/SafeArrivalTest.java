package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeArrivalTest {
    @Test void arrival_prefersSouth() { assertArrayEquals(new int[]{0, 1, 0}, SafeArrival.pick((x, z) -> true, true)); }
    @Test void arrival_skipsBlocked() { assertArrayEquals(new int[]{-1, 0, 0}, SafeArrival.pick((x, z) -> x == -1 && z == 0, true)); }
    @Test void arrival_triesDiagonalsBeforeRing() { assertArrayEquals(new int[]{1, 1, 0}, SafeArrival.pick((x, z) -> x == 1 && z == 1 || z == 2, true)); }
    @Test void arrival_triesRingBeforeTop() { assertArrayEquals(new int[]{0, -2, 0}, SafeArrival.pick((x, z) -> x == 0 && z == -2, true)); }
    @Test void arrival_fallsBackToTop() { assertArrayEquals(new int[]{0, 0, 2}, SafeArrival.pick((x, z) -> false, true)); }
    @Test void arrival_returnsNullWhenNothingSafe() { assertNull(SafeArrival.pick((x, z) -> false, false)); }
}
