package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeArrivalTest {
    @Test void arrival_prefersSouth() { assertArrayEquals(new int[]{0, 1, 0}, SafeArrival.pick((x, z) -> true)); }
    @Test void arrival_skipsBlocked() { assertArrayEquals(new int[]{-1, 0, 0}, SafeArrival.pick((x, z) -> x == -1)); }
    @Test void arrival_fallsBackToTop() { assertArrayEquals(new int[]{0, 0, 2}, SafeArrival.pick((x, z) -> false)); }
}
