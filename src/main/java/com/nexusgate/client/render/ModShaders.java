package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.nexusgate.NexusGate;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Nexus Gate's own GLSL programs (assets/nexusgate/shaders/core). Every getter falls back to the vanilla
 * position-tex-colour shader so a driver that rejects a program never crashes the game.
 */
public final class ModShaders {
    @Nullable private static ShaderInstance orb;
    @Nullable private static ShaderInstance field;
    @Nullable private static ShaderInstance wormhole;
    @Nullable private static ShaderInstance rift;

    private ModShaders() {}

    public static void register(RegisterShadersEvent event) {
        load(event, "core_orb", s -> orb = s);
        load(event, "energy_field", s -> field = s);
        load(event, "wormhole", s -> wormhole = s);
        load(event, "rift", s -> rift = s);
    }

    private static void load(RegisterShadersEvent event, String name, Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, name), DefaultVertexFormat.POSITION_TEX_COLOR), sink);
        } catch (Exception e) {
            NexusGate.LOGGER.error("Nexus Gate shader '{}' failed to load; using fallback", name, e);
        }
    }

    public static ShaderInstance orb() { return orFallback(orb); }

    public static ShaderInstance field() { return orFallback(field); }

    public static ShaderInstance wormhole() { return orFallback(wormhole); }

    public static ShaderInstance rift() { return orFallback(rift); }

    /** True when the real program is available (fallback draws only a tinted quad). */
    public static boolean ready() {
        return orb != null && field != null && wormhole != null && rift != null;
    }

    private static ShaderInstance orFallback(@Nullable ShaderInstance s) {
        return s != null ? s : GameRenderer.getPositionTexColorShader();
    }

    /** Sets a float uniform if the program declares it (fallback shaders silently ignore it). */
    public static void set(ShaderInstance shader, String uniform, float value) {
        shader.safeGetUniform(uniform).set(value);
    }
}
