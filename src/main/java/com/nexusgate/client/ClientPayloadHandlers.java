package com.nexusgate.client;

import com.nexusgate.net.OpenRenamePayload;
import com.nexusgate.net.OpenSelectionPayload;
import com.nexusgate.net.XaeroWaypointPayload;
import net.minecraft.client.Minecraft;

/** Client-only handling of server → client payloads. Never referenced outside payload lambdas. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void openSelection(OpenSelectionPayload payload) {
        Minecraft.getInstance().setScreen(new SelectionScreen(payload));
    }

    public static void openRename(OpenRenamePayload payload) {
        Minecraft.getInstance().setScreen(new RenameScreen(payload));
    }

    public static void xaeroWaypoint(XaeroWaypointPayload payload) {
        XaeroCompat.onStationActivated(payload);
    }
}
