package com.nexusgate.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusgate.client.render.LensRenderer;
import com.nexusgate.client.render.ModRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * World-space relocation VFX drawn with the custom shaders: the space-time lenses (real refraction of the frame),
 * the connection beam from the core to the traveller, the orbital uplink from the crown, the sky beam coming down
 * at the destination, the reconstruction beam, the spatial rifts at the cores and the quantum fluctuations.
 * Timing comes from {@link RelocationTimeline}.
 */
public final class WorldFxRenderer {
    private static final int CYAN = TeleportFxController.CYAN;
    private static final int RED = TeleportFxController.RED;

    private WorldFxRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!TeleportFxController.anyActive()) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float seconds = (TeleportFxController.ticks() + partial) / 20f;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera.rotation());

        // Space-time distortion first: it refracts the finished frame, then the energy is drawn on top.
        buffers.endBatch();
        collectLenses(mc, cam, partial);
        LensRenderer.flush(m, right, up);

        VertexConsumer field = buffers.getBuffer(ModRenderTypes.FIELD);
        // Origin: core → traveller connection, then the crown fires the orbital uplink.
        for (TeleportFxController.Charge c : TeleportFxController.charges()) {
            float t = c.progress(partial);
            Vec3 core = TeleportFxController.core(c.station);
            // The beam always ends on the focal point, which slides back along it into the core in phase D.
            Vec3 focal = TeleportFxController.focal(c.entityId, partial);
            float reach = RelocationTimeline.beamReach(t);
            float glow = RelocationTimeline.beamIntensity(t);
            if (reach > 0f && glow > 0f) {
                float d = RelocationTimeline.dissolve(t);
                beam(field, m, cam, core, core.lerp(focal, reach), 0.06f + 0.08f * d, argb(glow * (0.55f + 0.45f * d), CYAN), seconds);
            }
            float u = RelocationTimeline.uplink(t);
            if (u > 0f) uplink(field, m, TeleportFxController.crown(c.station), u, seconds, CYAN);
        }
        for (TeleportFxController.Burst b : TeleportFxController.bursts()) {
            float age = b.age(partial);
            if (age > 1f) continue;
            switch (b.kind()) {
                case DEPART -> {
                    // The transmission leaves: the orbital beam flares and dies away.
                    float k = RelocationTimeline.release(b.ticksSince(partial));
                    Vec3 crown = TeleportFxController.crown(b.station());
                    columnFade(field, m, crown, 0.08f + 0.3f * k, TeleportFxController.SKY_HEIGHT, argb(k, 0xCFFBFF), argb(0f, CYAN));
                    flatRing(field, m, crown, 0.3f + age * 2.5f, 0.18f, argb(k, CYAN), seconds * 4f, 32);
                }
                case CANCEL -> {
                    float k = 1f - age;
                    for (int i = 0; i < 3; i++) {
                        flatRing(field, m, b.pos().add(0, 0.2 + i * 0.75 - age * 0.3, 0), 0.9f * k, 0.1f, argb(k, RED), seconds * 6f, 32);
                    }
                }
            }
        }
        // Destination: the sky beam comes down, the core wakes, the core reaches the arrival spot.
        for (TeleportFxController.Reception r : TeleportFxController.receptions()) {
            float a = r.age(partial);
            Vec3 crown = TeleportFxController.crown(r.station());
            float reach = RelocationTimeline.skyReach(a);
            float sky = RelocationTimeline.skyIntensity(a);
            if (sky > 0f && reach > 0f) {
                float h = TeleportFxController.SKY_HEIGHT * reach;
                Vec3 front = crown.add(0, TeleportFxController.SKY_HEIGHT - h, 0);
                columnFade(field, m, front, 0.1f + 0.1f * sky, h, argb(sky, 0xCFFBFF), argb(sky * 0.15f, CYAN));
                if (reach >= 1f) uplink(field, m, crown, sky * 0.8f, seconds, CYAN);
            }
            float rebuild = RelocationTimeline.rebuildIntensity(a);
            float rr = RelocationTimeline.rebuildReach(a);
            if (rebuild > 0f && rr > 0f) {
                Vec3 core = TeleportFxController.core(r.station());
                Vec3 focal = TeleportFxController.focal(r.entityId(), partial);
                float built = 1f - RelocationTimeline.reconstruct(a);
                beam(field, m, cam, core, core.lerp(focal, rr), 0.14f - 0.08f * built, argb(rebuild, CYAN), seconds);
            }
        }
        buffers.endBatch(ModRenderTypes.FIELD);

        // Spatial distortion: the core folds space as matter is sent off, and again as it is received.
        VertexConsumer rift = buffers.getBuffer(ModRenderTypes.RIFT);
        for (TeleportFxController.Burst b : TeleportFxController.bursts()) {
            float age = b.age(partial);
            if (age > 1f || b.kind() != TeleportFxController.BurstKind.DEPART) continue;
            float size = 0.9f * (1f - age) * (1f - age) + 0.05f;
            billboard(rift, m, TeleportFxController.core(b.station()), right, up, size, argb(1f - age * age, CYAN));
        }
        for (TeleportFxController.Reception r : TeleportFxController.receptions()) {
            float a = r.age(partial);
            float k = smooth(6f, 12f, a) * (1f - smooth(16f, 26f, a));
            if (k > 0f) billboard(rift, m, TeleportFxController.core(r.station()), right, up, 0.8f * k, argb(k, CYAN));
        }
        buffers.endBatch(ModRenderTypes.RIFT);

        // Quantum fluctuations, over everything else.
        if (QuantumFx.any()) {
            QuantumFx.render(buffers.getBuffer(ModRenderTypes.FRAGMENT), m, partial);
            buffers.endBatch(ModRenderTypes.FRAGMENT);
        }
        pose.popPose();
    }

    /**
     * Where space bends this frame: around each body (growing with its instability and conversion), in the cores
     * while they absorb or emit matter, along the beams, and around the larger fragments.
     */
    private static void collectLenses(Minecraft mc, Vec3 cam, float partial) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        int self = mc.player != null ? mc.player.getId() : -1;
        for (TeleportFxController.Charge c : TeleportFxController.charges()) {
            float t = c.progress(partial);
            Vec3 core = TeleportFxController.core(c.station);
            Vec3 focal = TeleportFxController.focal(c.entityId, partial);
            float seed = (c.entityId * 0.618f) % 1f;
            // Your own body lens would fill the whole first-person view.
            if (!(firstPerson && c.entityId == self)) {
                focalLens(focal, cam, RelocationTimeline.dissolve(t), RelocationTimeline.bodyLens(t), seed);
            }
            LensRenderer.big(core, 0.9f, RelocationTimeline.coreLens(t), seed + 0.3f);
            beamLenses(core, core.lerp(focal, RelocationTimeline.beamReach(t)), RelocationTimeline.beamIntensity(t), seed);
        }
        for (TeleportFxController.Burst b : TeleportFxController.bursts()) {
            if (b.kind() != TeleportFxController.BurstKind.DEPART) continue;
            LensRenderer.big(TeleportFxController.core(b.station()), 0.9f,
                    RelocationTimeline.coreLensAfterDepart(b.ticksSince(partial)), 0.7f);
        }
        for (TeleportFxController.Reception r : TeleportFxController.receptions()) {
            float a = r.age(partial);
            Vec3 core = TeleportFxController.core(r.station());
            Vec3 focal = TeleportFxController.focal(r.entityId(), partial);
            float seed = (r.entityId() * 0.618f) % 1f;
            if (!(firstPerson && r.entityId() == self)) {
                focalLens(focal, cam, RelocationTimeline.reconstruct(a), RelocationTimeline.arrivalLens(a), seed);
            }
            LensRenderer.big(core, 0.9f, RelocationTimeline.receptionCoreLens(a), seed + 0.3f);
            beamLenses(core, core.lerp(focal, RelocationTimeline.rebuildReach(a)), RelocationTimeline.rebuildIntensity(a), seed);
        }
        QuantumFx.forEachLens(48, partial, f -> LensRenderer.small(f.position(partial), f.size() * 3.5f, 0.45f, (f.size() * 37f) % 1f));
    }

    /**
     * The convergent lens on the focal point. It tightens with the body: the more compressed the matter, the
     * smaller the region space is pulled into (radius 1.35 → 0.55 blocks), while its strength keeps rising.
     */
    private static void focalLens(Vec3 focal, Vec3 cam, float compression, float strength, float seed) {
        float radius = 0.55f + 0.8f * CollapseField.remainingSize(compression * 0.85f);
        LensRenderer.big(towardCamera(focal, cam, 0.35 * radius), radius, strength, seed);
    }

    /** Weak lenses spaced along a beam: the energy being transferred bends the light around it. */
    private static void beamLenses(Vec3 from, Vec3 to, float intensity, float seed) {
        if (intensity <= 0.05f || from.distanceToSqr(to) < 0.25) return;
        for (int i = 0; i < 5; i++) {
            float k = 0.25f + i * 0.13f;
            LensRenderer.small(from.lerp(to, k), 0.32f, 0.35f * intensity, (seed + i * 0.17f) % 1f);
        }
    }

    /** Moves a lens centre toward the camera so it sits in front of the body it bends. */
    private static Vec3 towardCamera(Vec3 p, Vec3 cam, double by) {
        Vec3 d = cam.subtract(p);
        double len = d.length();
        return len < 1e-3 ? p : p.add(d.scale(Math.min(by, len * 0.5) / len));
    }

    /** The crown lights up and the orbital beam climbs from it toward the sky. */
    private static void uplink(VertexConsumer vc, Matrix4f m, Vec3 crown, float k, float seconds, int rgb) {
        for (int i = 0; i < 2; i++) {
            float dir = i == 0 ? 1f : -1f;
            flatRing(vc, m, crown.add(0, 0.05 + i * 0.25, 0), 0.42f - i * 0.12f, 0.08f, argb(k, rgb), seconds * dir * (2f + 4f * k), 24);
        }
        columnFade(vc, m, crown, 0.06f + 0.1f * k, TeleportFxController.SKY_HEIGHT * smooth(0f, 0.6f, k), argb(k, rgb), argb(0f, rgb));
    }

    /** Horizontal ribbon arc (u along, v across). */
    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float w, float a0, float a1, int argb, int seg) {
        // Straight segments, eight per full turn: octagonal rings built like blocks.
        seg = Math.max(1, Math.round(8f * (a1 - a0) / Mth.TWO_PI));
        float r0 = r - w * 0.5f, r1 = r + w * 0.5f;
        float len = (a1 - a0) * r * 2f;
        for (int i = 0; i < seg; i++) {
            float t0 = a0 + (a1 - a0) * i / seg, t1 = a0 + (a1 - a0) * (i + 1) / seg;
            float u0 = len * i / seg, u1 = len * (i + 1) / seg;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            vc.addVertex(m, (float) c.x + c0 * r0, (float) c.y, (float) c.z + s0 * r0).setUv(u0, 0).setColor(argb);
            vc.addVertex(m, (float) c.x + c0 * r1, (float) c.y, (float) c.z + s0 * r1).setUv(u0, 1).setColor(argb);
            vc.addVertex(m, (float) c.x + c1 * r1, (float) c.y, (float) c.z + s1 * r1).setUv(u1, 1).setColor(argb);
            vc.addVertex(m, (float) c.x + c1 * r0, (float) c.y, (float) c.z + s1 * r0).setUv(u1, 0).setColor(argb);
        }
    }

    private static void flatRing(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float w, int argb, float spin, int seg) {
        arc(vc, m, c, r, w, spin, spin + Mth.TWO_PI, argb, seg);
    }

    /** Tall square column whose colour fades from the base to the top (the orbital beam). */
    private static void columnFade(VertexConsumer vc, Matrix4f m, Vec3 base, float r, float h, int bottom, int top) {
        if (h <= 0.01f) return;
        for (int i = 0; i < 4; i++) {
            float t0 = Mth.TWO_PI * (i + 0.5f) / 4, t1 = Mth.TWO_PI * (i + 1.5f) / 4;
            float x0 = (float) base.x + Mth.cos(t0) * r, z0 = (float) base.z + Mth.sin(t0) * r;
            float x1 = (float) base.x + Mth.cos(t1) * r, z1 = (float) base.z + Mth.sin(t1) * r;
            float y0 = (float) base.y, y1 = y0 + h;
            // u runs up the column (one unit per block), so the shader's flow rises toward the sky.
            vc.addVertex(m, x0, y0, z0).setUv(0, 0).setColor(bottom);
            vc.addVertex(m, x1, y0, z1).setUv(0, 1).setColor(bottom);
            vc.addVertex(m, x1, y1, z1).setUv(h, 1).setColor(top);
            vc.addVertex(m, x0, y1, z0).setUv(h, 0).setColor(top);
        }
    }

    /** Camera-facing ribbon between two points with a slight live wobble. */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, int argb, float seconds) {
        int seg = 10;
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0e-3) return;
        Vec3 n = dir.normalize();
        Vec3 prev = a;
        Vec3 prevSide = side(cam, a, n, width);
        for (int i = 1; i <= seg; i++) {
            float t = (float) i / seg;
            float wob = Mth.sin(seconds * 9f + t * 7f) * 0.06f * Mth.sin(t * Mth.PI);
            wob = Math.round(wob * 16f) / 16f; // wobble in whole block-pixels
            Vec3 p = a.add(dir.scale(t)).add(0, wob, 0);
            Vec3 s = side(cam, p, n, width);
            float u0 = (float) (len * (i - 1) / seg) * 2f, u1 = (float) (len * i / seg) * 2f;
            vc.addVertex(m, (float) (prev.x - prevSide.x), (float) (prev.y - prevSide.y), (float) (prev.z - prevSide.z)).setUv(u0, 0).setColor(argb);
            vc.addVertex(m, (float) (prev.x + prevSide.x), (float) (prev.y + prevSide.y), (float) (prev.z + prevSide.z)).setUv(u0, 1).setColor(argb);
            vc.addVertex(m, (float) (p.x + s.x), (float) (p.y + s.y), (float) (p.z + s.z)).setUv(u1, 1).setColor(argb);
            vc.addVertex(m, (float) (p.x - s.x), (float) (p.y - s.y), (float) (p.z - s.z)).setUv(u1, 0).setColor(argb);
            prev = p;
            prevSide = s;
        }
    }

    private static Vec3 side(Vec3 cam, Vec3 p, Vec3 n, float width) {
        Vec3 toCam = cam.subtract(p);
        Vec3 s = n.cross(toCam);
        double l = s.length();
        return l < 1.0e-5 ? new Vec3(width * 0.5, 0, 0) : s.scale(width * 0.5 / l);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f right, Vector3f up, float size, int argb) {
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        vc.addVertex(m, x - rx - ux, y - ry - uy, z - rz - uz).setUv(0, 1).setColor(argb);
        vc.addVertex(m, x + rx - ux, y + ry - uy, z + rz - uz).setUv(1, 1).setColor(argb);
        vc.addVertex(m, x + rx + ux, y + ry + uy, z + rz + uz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x - rx + ux, y - ry + uy, z - rz + uz).setUv(0, 0).setColor(argb);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int argb(float a, int rgb) {
        return (Math.round(Mth.clamp(a, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }
}
