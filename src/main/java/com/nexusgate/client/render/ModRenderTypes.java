package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

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
}
