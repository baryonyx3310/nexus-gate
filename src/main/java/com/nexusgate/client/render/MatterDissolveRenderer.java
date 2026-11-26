package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusgate.client.fx.CollapseField;
import com.nexusgate.client.fx.RelocationTimeline;
import com.nexusgate.client.fx.TeleportFxController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Visual-only convergent collapse of a player being relocated.
 * <p>
 * While a body is unstable or compressed, its normal render is cancelled and the very same renderer is called
 * again through a buffer wrapper. The wrapper moves every vertex of every layer (skin, armour, cape, held items,
 * glints) through {@link CollapseField}, so the geometry itself is squeezed toward the focal point at the end of
 * the connection beam, down to a single point that then rides the beam into the core. The body is drawn every
 * frame of the sequence; it is never hidden. Textured layers also go to the matter shader for the surface effects.
 * <p>
 * The deformed positions are sampled so the quantum fluctuations can leave from the regions being compressed.
 * Nothing about the entity changes: no position, no hitbox, no extra entity.
 */
public final class MatterDissolveRenderer {
    /** Guards the re-entrant render call (it fires RenderPlayerEvent.Pre again). */
    private static boolean active;
    private static final int MAX_SAMPLES = 24;
    private static final Map<Integer, List<Vec3>> SAMPLES = new HashMap<>();
    /** Original shadow radius of each player renderer we have shrunk (the renderer is shared by players). */
    private static final Map<Object, Float> SHADOWS = new java.util.IdentityHashMap<>();

    private MatterDissolveRenderer() {}

    /** World positions of a few deformed vertices from the last frame this body was drawn (may be empty). */
    public static List<Vec3> samples(int entityId) {
        List<Vec3> s = SAMPLES.get(entityId);
        return s != null ? s : List.of();
    }

    public static void forget(int entityId) {
        SAMPLES.remove(entityId);
    }

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (active) return;
        Player player = event.getEntity();
        float partial = event.getPartialTick();
        RelocationTimeline.Body body = TeleportFxController.body(player.getId(), partial);
        var renderer = event.getRenderer();
        Float shadow = SHADOWS.get(renderer);
        if (!body.active() || !(player instanceof AbstractClientPlayer client)) {
            if (shadow != null) renderer.shadowRadius = shadow;
            return;
        }
        event.setCanceled(true);
        // The shadow shrinks with the body (the dispatcher draws it right after this event, per entity).
        if (shadow == null) {
            shadow = renderer.shadowRadius;
            SHADOWS.put(renderer, shadow);
        }
        renderer.shadowRadius = shadow * CollapseField.remainingSize(body.conversion());

        PoseStack pose = event.getPoseStack();
        // The entity pose is translated to the feet, relative to the camera and without rotation (the camera
        // rotation lives in the model-view matrix), so vertex positions minus this origin are body coordinates.
        Matrix4f origin = pose.last().pose();
        float baseX = origin.m30(), baseY = origin.m31(), baseZ = origin.m32();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 focal = TeleportFxController.focal(player.getId(), partial);
        float fx = (float) (focal.x - cam.x) - baseX, fy = (float) (focal.y - cam.y) - baseY, fz = (float) (focal.z - cam.z) - baseZ;

        List<Vec3> samples = new ArrayList<>(MAX_SAMPLES);
        SAMPLES.put(player.getId(), samples);
        Collapse collapse = new Collapse(baseX, baseY, baseZ, fx, fy, fz, body.conversion(),
                (player.getId() & 1) == 0 ? 2.5f : -2.5f, cam, samples);
        int state = RelocationTimeline.encodeBody(body.instability(), body.conversion());
        MultiBufferSource source = event.getMultiBufferSource();
        MultiBufferSource wrapped = type -> wrap(source, type, state, collapse);

        active = true;
        try {
            renderer.render(client, player.getYRot(), partial, pose, wrapped, event.getPackedLight());
        } finally {
            active = false;
        }
    }

    private static VertexConsumer wrap(MultiBufferSource source, RenderType type, int state, Collapse collapse) {
        Optional<ResourceLocation> texture = ModShaders.dissolveReady() && convertible(type) ? texture(type) : Optional.empty();
        if (texture.isPresent()) {
            return new CollapseConsumer(source.getBuffer(ModRenderTypes.matterDissolve(texture.get())), collapse, state);
        }
        // Glints, text, untextured layers (or no matter shader): same render type, same colours, same collapse.
        return new CollapseConsumer(source.getBuffer(type), collapse, -1);
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

    /** The collapse of one body for one frame, shared by all its layers. */
    private static final class Collapse {
        final float baseX, baseY, baseZ, fx, fy, fz, compression, twist;
        final Vec3 cam;
        final List<Vec3> samples;
        final float[] out = new float[3];
        int counter;

        Collapse(float baseX, float baseY, float baseZ, float fx, float fy, float fz, float compression, float twist,
                 Vec3 cam, List<Vec3> samples) {
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.compression = compression;
            this.twist = twist;
            this.cam = cam;
            this.samples = samples;
        }

        /** Collapses a camera-relative vertex; the result is left in {@link #out} (camera-relative). */
        void apply(float x, float y, float z) {
            CollapseField.collapse(x - baseX, y - baseY, z - baseZ, fx, fy, fz, compression, twist, out);
            out[0] += baseX;
            out[1] += baseY;
            out[2] += baseZ;
            if (++counter % 13 == 0 && samples.size() < MAX_SAMPLES) {
                samples.add(new Vec3(cam.x + out[0], cam.y + out[1], cam.z + out[2]));
            }
        }
    }

    /**
     * Moves every vertex through the collapse. With {@code state >= 0} it also replaces the vertex colour with
     * the matter-shader parameters: rgb = the vertex's original position on the body (x and z over -0.75..0.75,
     * y over 0..2.25 blocks from the feet, so surface patterns stay glued to the body while it is compressed),
     * alpha = body state.
     */
    private static final class CollapseConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Collapse c;
        private final int state;
        private float lastX, lastY, lastZ;

        CollapseConsumer(VertexConsumer delegate, Collapse c, int state) {
            this.delegate = delegate;
            this.c = c;
            this.state = state;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            lastX = x;
            lastY = y;
            lastZ = z;
            c.apply(x, y, z);
            delegate.addVertex(c.out[0], c.out[1], c.out[2]);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (state < 0) {
                delegate.setColor(red, green, blue, alpha);
            } else {
                delegate.setColor(channel((lastX - c.baseX) / 1.5f + 0.5f), channel((lastY - c.baseY) / 2.25f),
                        channel((lastZ - c.baseZ) / 1.5f + 0.5f), state);
            }
            return this;
        }

        private static int channel(float v) {
            return Mth.clamp(Math.round(v * 255f), 0, 255);
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
}
