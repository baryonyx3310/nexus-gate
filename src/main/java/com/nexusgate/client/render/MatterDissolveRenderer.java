package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusgate.client.fx.TeleportFxController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Optional;

/**
 * Visual-only dematerialisation of a player being relocated. When a body has a dissolve above 0, its normal
 * render is cancelled and the very same renderer is called again through a buffer wrapper that sends every
 * textured layer (skin, armour, cape, held items) to the matter dissolve shader. Nothing about the entity
 * changes: no position, no hitbox, no extra entity.
 */
public final class MatterDissolveRenderer {
    /** Guards the re-entrant render call (it fires RenderPlayerEvent.Pre again). */
    private static boolean active;

    private MatterDissolveRenderer() {}

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (active) return;
        Player player = event.getEntity();
        float dissolve = TeleportFxController.dissolve(player.getId(), event.getPartialTick());
        if (dissolve <= 0f) return;
        if (!ModShaders.dissolveReady()) {
            // No dissolve program: just hide the body for the second half of the conversion.
            if (dissolve >= 0.5f) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (dissolve >= 1f || !(player instanceof AbstractClientPlayer client)) return;

        PoseStack pose = event.getPoseStack();
        // The pose is at the feet, translated relative to the camera: its y is the body's base in vertex space.
        float baseY = pose.last().pose().m31();
        float height = Math.max(0.5f, player.getBbHeight());
        int alpha = Mth.clamp(Math.round(dissolve * 255f), 1, 254);
        MultiBufferSource source = event.getMultiBufferSource();
        MultiBufferSource wrapped = type -> wrap(source, type, alpha, baseY, height, dissolve);

        active = true;
        try {
            event.getRenderer().render(client, player.getYRot(), event.getPartialTick(), pose, wrapped, event.getPackedLight());
        } finally {
            active = false;
        }
    }

    private static VertexConsumer wrap(MultiBufferSource source, RenderType type, int alpha, float baseY, float height,
                                       float dissolve) {
        Optional<ResourceLocation> texture = convertible(type) ? texture(type) : Optional.empty();
        if (texture.isEmpty()) {
            // Glints, text and untextured layers cannot carry the dissolve; they go out first.
            return dissolve > 0.1f ? NullConsumer.INSTANCE : source.getBuffer(type);
        }
        return new ParamConsumer(source.getBuffer(ModRenderTypes.matterDissolve(texture.get())), alpha, baseY, height);
    }

    private static boolean convertible(RenderType type) {
        String name = type.toString();
        return !name.contains("glint") && !name.contains("text") && !name.contains("eyes");
    }

    private static Optional<ResourceLocation> texture(RenderType type) {
        if (type instanceof RenderType.CompositeRenderType composite) {
            return composite.state().textureState.cutoutTexture();
        }
        return Optional.empty();
    }

    /**
     * Replaces the vertex colour with the dissolve parameters: red = height of the vertex on the body,
     * alpha = dissolve. Vertex positions arrive already transformed, so their y is comparable to baseY.
     */
    private static final class ParamConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final float baseY;
        private final float height;
        private float lastY;

        ParamConsumer(VertexConsumer delegate, int alpha, float baseY, float height) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.baseY = baseY;
            this.height = height;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            lastY = y;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            int h = Mth.clamp(Math.round((lastY - baseY) / height * 255f), 0, 255);
            delegate.setColor(h, 0, 0, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    /** Swallows a layer that has already been converted. */
    private static final class NullConsumer implements VertexConsumer {
        static final NullConsumer INSTANCE = new NullConsumer();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
