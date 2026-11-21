package com.nexusgate.core;

public final class Permissions {
    private Permissions() {}

    public static boolean canSetPublic(int playerPermissionLevel, int required) {
        return playerPermissionLevel >= required;
    }
}
