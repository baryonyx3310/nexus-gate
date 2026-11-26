package com.nexusgate.client.render;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Whether the space-time lens can refract the real frame. It is switched off (everything else keeps working)
 * with "Fabulous" graphics, whose transparency pipeline splits the frame into several targets, and with an
 * active Iris/Oculus shader pack, which replaces the main target.
 */
public final class LensSupport {
    private static Boolean shaderModPresent;

    private LensSupport() {}

    public static boolean enabled() {
        return ModShaders.lensReady() && !Minecraft.useShaderTransparency() && !shaderPackInUse();
    }

    private static boolean shaderPackInUse() {
        if (shaderModPresent == null) {
            ModList mods = ModList.get();
            shaderModPresent = mods != null && (mods.isLoaded("iris") || mods.isLoaded("oculus"));
        }
        if (!shaderModPresent) return false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true; // cannot tell: assume a pack is on and keep the lens off
        }
    }
}
