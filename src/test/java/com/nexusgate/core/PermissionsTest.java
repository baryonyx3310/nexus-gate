package com.nexusgate.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {
    @Test void canSetPublic_requiresLevel() {
        assertFalse(Permissions.canSetPublic(0, 2));
        assertTrue(Permissions.canSetPublic(2, 2));
        assertTrue(Permissions.canSetPublic(4, 2));
    }
}
