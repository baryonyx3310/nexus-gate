package com.nexusgate.client;

import com.nexusgate.NexusGate;
import com.nexusgate.net.XaeroWaypointPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Optional Xaero's Minimap integration, done by reflection so there is no compile dependency.
 * Any failure (missing mod, changed API) disables the feature with a single log warning; it never crashes the game.
 */
public final class XaeroCompat {
    private static final String MOD_ID = "xaerominimap";
    /** Xaero colour index used for station waypoints (cyan family). */
    private static final int COLOR = 11;

    private static boolean disabled;

    private XaeroCompat() {}

    public static void onStationActivated(XaeroWaypointPayload p) {
        if (disabled || !ModList.get().isLoaded(MOD_ID)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().toString().equals(p.dimension())) return;
        try {
            addWaypoint(mc, p.name(), p.x(), p.y(), p.z());
        } catch (Throwable t) {
            disabled = true;
            NexusGate.LOGGER.warn("Xaero's Minimap waypoint integration disabled: {}", t.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void addWaypoint(Minecraft mc, String name, int x, int y, int z) throws Exception {
        Object connection = mc.getConnection();
        Object session = call(connection, "getXaero_minimapSession");
        Object manager = call(session, "getWaypointsManager");
        Object set = call(manager, "getWaypoints");
        List<Object> list = (List<Object>) call(set, "getList");
        for (Object wp : list) {
            if ((int) call(wp, "getX") == x && (int) call(wp, "getY") == y && (int) call(wp, "getZ") == z) return;
        }
        Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
        Constructor<?> ctor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class,
                int.class, int.class, boolean.class);
        String symbol = name.isEmpty() ? "N" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        list.add(ctor.newInstance(x, y, z, name, symbol, COLOR, 0, false));

        Class<?> minimapClass = Class.forName("xaero.minimap.XaeroMinimap");
        Object minimap = minimapClass.getField("instance").get(null);
        Object settings = call(minimap, "getSettings");
        Object world = call(manager, "getCurrentWorld");
        for (Method m : settings.getClass().getMethods()) {
            if (m.getName().equals("saveWaypoints") && m.getParameterCount() == 1) {
                m.invoke(settings, world);
                return;
            }
        }
        throw new NoSuchMethodException("saveWaypoints");
    }

    private static Object call(Object target, String method) throws Exception {
        if (target == null) throw new IllegalStateException("null target for " + method);
        return target.getClass().getMethod(method).invoke(target);
    }
}
