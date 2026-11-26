package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
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
    @Nullable private static ShaderInstance dissolve;

    private ModShaders() {}

    public static void register(RegisterShadersEvent event) {
        load(event, "core_orb", DefaultVertexFormat.POSITION_TEX_COLOR, s -> orb = s);
        load(event, "energy_field", DefaultVertexFormat.POSITION_TEX_COLOR, s -> field = s);
        load(event, "wormhole", DefaultVertexFormat.POSITION_TEX_COLOR, s -> wormhole = s);
        load(event, "rift", DefaultVertexFormat.POSITION_TEX_COLOR, s -> rift = s);
        load(event, "matter_dissolve", DefaultVertexFormat.NEW_ENTITY, s -> dissolve = s);
    }

    private static void load(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, name), format), sink);
        } catch (Exception e) {
            NexusGate.LOGGER.error("Nexus Gate shader '{}' failed to load; using fallback", name, e);
        }
    }

    public static ShaderInstance orb() { return orFallback(orb); }

    public static ShaderInstance field() { return orFallback(field); }

    public static ShaderInstance wormhole() { return orFallback(wormhole); }

    public static ShaderInstance rift() { return orFallback(rift); }

    /** Entity-format dissolve program; falls back to the vanilla entity shader (callers check {@link #dissolveReady}). */
    public static ShaderInstance dissolve() {
        return dissolve != null ? dissolve : GameRenderer.getRendertypeEntityCutoutNoCullShader();
    }

    /** The dissolve parameters live in the vertex colour, so only the real program may draw with them. */
    public static boolean dissolveReady() {
        return dissolve != null;
    }

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
