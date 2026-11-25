package com.nexusgate.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * Base for every Nexus Gate particle: full-bright, additive, colour that cools from a hot start colour to an
 * end colour, and a smooth fade-in / fade-out.
 */
public abstract class GlowParticle extends TextureSheetParticle {
    /** Additive blending without depth writes, so overlapping glows build up light instead of sorting. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "NEXUSGATE_ADDITIVE";
        }
    };

    protected final SpriteSet sprites;
    protected int startColor = 0xFFFFFF;
    protected int endColor = 0x33F0FF;
    protected float maxAlpha = 1.0f;
    protected float baseSize;

    protected GlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 0.92f;
        pickSprite(sprites);
    }

    public GlowParticle colors(int start, int end) {
        this.startColor = start;
        this.endColor = end;
        applyColor(0);
        return this;
    }

    public GlowParticle life(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    public GlowParticle size(float s) {
        this.baseSize = s;
        this.quadSize = s;
        return this;
    }

    public GlowParticle alpha(float a) {
        this.maxAlpha = a;
        return this;
    }

    protected float lifeFraction(float partialTick) {
        return Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
    }

    protected void applyColor(float t) {
        float k = t * t;
        rCol = Mth.lerp(k, ((startColor >> 16) & 255) / 255f, ((endColor >> 16) & 255) / 255f);
        gCol = Mth.lerp(k, ((startColor >> 8) & 255) / 255f, ((endColor >> 8) & 255) / 255f);
        bCol = Mth.lerp(k, (startColor & 255) / 255f, (endColor & 255) / 255f);
    }

    @Override
    public void tick() {
        super.tick();
        float t = lifeFraction(0);
        applyColor(t);
        float fadeIn = Mth.clamp(t / 0.12f, 0f, 1f);
        float fadeOut = 1f - Mth.clamp((t - 0.55f) / 0.45f, 0f, 1f);
        alpha = maxAlpha * stepped(fadeIn * fadeOut, 3);
    }

    /** Quantises 0..1 into {@code n} visible levels (rounding up, so a live particle never vanishes). */
    protected static float stepped(float v, int n) {
        return Mth.clamp((float) Math.ceil(Mth.clamp(v, 0f, 1f) * n) / n, 0f, 1f);
    }

    /** Pixel-art scaling: the sprite grows and shrinks in quarter steps instead of continuously. */
    protected float steppedSize(float k) {
        return baseSize * Math.max(0.25f, Math.round(k * 4f) / 4f);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIVE;
    }
}
