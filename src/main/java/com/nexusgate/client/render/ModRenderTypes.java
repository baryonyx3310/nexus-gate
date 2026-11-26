package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * World render types for the custom shaders. All are depth-tested but do not write depth, so glows layer
 * over geometry behind them without punching holes. Per-instance parameters travel in the vertex colour.
 */
public final class ModRenderTypes {
    private ModRenderTypes() {}

    /** Plain white texture bound by the shader render types, so the vanilla fallback shader never samples
     *  whatever texture happens to be bound (font, block atlas) if one of our programs fails to compile. */
    public static final net.minecraft.resources.ResourceLocation WHITE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nexusgate", "textures/block/white.png");

    private static RenderStateShard.TextureStateShard white() {
        return new RenderStateShard.TextureStateShard(WHITE, false, false);
    }

    /** Plasma core sphere billboards (translucent). */
    public static final RenderType ORB = RenderType.create("nexusgate_orb", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 1024, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::orb))
                    .setTextureState(white())
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Energy ribbons, rings and columns (additive). */
    public static final RenderType FIELD = RenderType.create("nexusgate_field", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::field))
                    .setTextureState(white())
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Flat emissive surfaces (LEDs, lenses, filaments): additive vertex colour, unaffected by light. */
    public static final RenderType EMISSIVE = RenderType.create("nexusgate_emissive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 2048, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Spatial rift billboards (translucent, darkens its centre). */
    public static final RenderType RIFT = RenderType.create("nexusgate_rift", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 512, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::rift))
                    .setTextureState(white())
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> DISSOLVE = new HashMap<>();

    /**
     * A body texture redrawn by the matter dissolve shader: cutout, lit and depth-writing like the vanilla
     * entity types, with the dissolve parameters in the vertex colour. One cached type per texture.
     */
    public static RenderType matterDissolve(ResourceLocation texture) {
        return DISSOLVE.computeIfAbsent(texture, t -> RenderType.create("nexusgate_matter_dissolve",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::dissolve))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(true)));
    }
}
