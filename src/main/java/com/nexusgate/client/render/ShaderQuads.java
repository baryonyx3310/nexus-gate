package com.nexusgate.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Immediate-mode quads with a custom shader, for the GUI and HUD (where uniforms can be set per draw). */
public final class ShaderQuads {
    private ShaderQuads() {}

    public enum Blend { ALPHA, ADD }

    /** Draws one quad covering (x0,y0)-(x1,y1) with UV 0..1 and the given ARGB colour. */
    public static void quad(GuiGraphics g, ShaderInstance shader, Blend blend, float x0, float y0, float x1, float y1, int argb) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(() -> shader);
        // Our programs ignore textures; binding plain white keeps the vanilla fallback a clean tinted quad.
        RenderSystem.setShaderTexture(0, ModRenderTypes.WHITE);
        RenderSystem.enableBlend();
        if (blend == Blend.ADD) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableDepthTest();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, x0, y1, 0).setUv(0, 1).setColor(argb);
        b.addVertex(m, x1, y1, 0).setUv(1, 1).setColor(argb);
        b.addVertex(m, x1, y0, 0).setUv(1, 0).setColor(argb);
        b.addVertex(m, x0, y0, 0).setUv(0, 0).setColor(argb);
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    /** Centered square quad. */
    public static void square(GuiGraphics g, ShaderInstance shader, Blend blend, float cx, float cy, float half, int argb) {
        quad(g, shader, blend, cx - half, cy - half, cx + half, cy + half, argb);
    }

    public static int argb(float a, int rgb) {
        int ai = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    /** Resets the per-draw uniforms so batched world draws of the same programs see neutral values. */
    public static void resetUniforms() {
        ShaderInstance orb = ModShaders.orb();
        ModShaders.set(orb, "Open", 0f);
        ShaderInstance w = ModShaders.wormhole();
        ModShaders.set(w, "Depth", 0f);
        ModShaders.set(w, "Exit", 0f);
        ModShaders.set(w, "Radius", 0f);
        ModShaders.set(w, "CenterX", 0f);
        ModShaders.set(w, "CenterY", 0f);
        ModShaders.set(w, "Aspect", 1f);
    }
}
