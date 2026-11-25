package com.nexusgate.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusgate.client.render.ModRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * World-space teleport VFX drawn with the custom shaders: holographic rings around the traveller, the beam
 * from the station core, the light column, and the rift / collapse / re-forming bursts.
 */
public final class WorldFxRenderer {
    private static final int CYAN = TeleportFxController.CYAN;
    private static final int RED = TeleportFxController.RED;

    private WorldFxRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean any = TeleportFxController.charges().iterator().hasNext() || TeleportFxController.bursts().iterator().hasNext();
        if (!any) return;

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

        VertexConsumer field = buffers.getBuffer(ModRenderTypes.FIELD);
        for (TeleportFxController.Charge c : TeleportFxController.charges()) {
            boolean local = mc.player != null && mc.player.getId() == c.entityId && mc.options.getCameraType().isFirstPerson();
            float p = c.progress(partial);
            Vec3 at = c.position(partial);
            chargeRings(field, m, at, p, seconds, local);
            beam(field, m, cam, core(c.station), at.add(0, 1.0, 0), 0.08f + 0.12f * p, argb(0.35f + 0.6f * p, CYAN), seconds);
            float col = smooth(0.7f, 1f, p);
            if (col > 0 && !local) column(field, m, at, 0.45f + 0.15f * col, 2.6f, argb(col, CYAN), 10);
        }
        for (TeleportFxController.Burst b : TeleportFxController.bursts()) {
            float age = b.age(partial);
            if (age > 1f) continue;
            Vec3 at = b.pos();
            switch (b.kind()) {
                case DEPART -> {
                    float k = 1f - age;
                    column(field, m, at, 0.6f * k * k + 0.03f, 3.2f, argb(k, 0xCFFBFF), 12);
                    flatRing(field, m, at.add(0, 0.05, 0), 0.4f + age * 3f, 0.25f, argb(k * k, CYAN), seconds * 4f, 40);
                }
                case ARRIVE -> {
                    float k = 1f - age;
                    column(field, m, at, 0.15f + 0.5f * age, 2.8f * k + 0.2f, argb(k * 0.9f, CYAN), 12);
                    for (int i = 0; i < 3; i++) {
                        float r = 0.3f + age * (1.2f + i * 0.5f);
                        flatRing(field, m, at.add(0, 0.3 + i * 0.7, 0), r, 0.12f, argb(k * k, CYAN), seconds * (i % 2 == 0 ? 3 : -3), 32);
                    }
                }
                case CANCEL -> {
                    float k = 1f - age;
                    for (int i = 0; i < 3; i++) {
                        flatRing(field, m, at.add(0, 0.2 + i * 0.75 - age * 0.3, 0), 0.9f * k, 0.1f, argb(k, RED), seconds * 6f, 32);
                    }
                }
            }
        }
        buffers.endBatch(ModRenderTypes.FIELD);

        VertexConsumer rift = buffers.getBuffer(ModRenderTypes.RIFT);
        for (TeleportFxController.Burst b : TeleportFxController.bursts()) {
            float age = b.age(partial);
            if (age > 1f || b.kind() == TeleportFxController.BurstKind.CANCEL) continue;
            boolean local = mc.player != null && b.kind() == TeleportFxController.BurstKind.ARRIVE
                    && b.pos().distanceToSqr(mc.player.position()) < 1.0 && mc.options.getCameraType().isFirstPerson();
            if (local) continue;
            float size = b.kind() == TeleportFxController.BurstKind.DEPART
                    ? 1.4f * (1f - age) * (1f - age) + 0.1f
                    : 1.3f * Mth.sin(Mth.PI * Math.min(1f, age * 1.3f));
            billboard(rift, m, b.pos().add(0, 1.0, 0), right, up, size, argb(1f - age * age, CYAN));
        }
        buffers.endBatch(ModRenderTypes.RIFT);
        pose.popPose();
    }

    private static Vec3 core(BlockPos station) {
        return new Vec3(station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5);
    }

    /** Three segmented rings at feet / waist / head that counter-rotate and climb with progress. */
    private static void chargeRings(VertexConsumer vc, Matrix4f m, Vec3 at, float p, float seconds, boolean local) {
        for (int i = 0; i < 3; i++) {
            if (local && i > 0) continue; // keep the first-person view clear; only the floor ring
            float y = 0.08f + i * 0.85f + p * 0.25f;
            float r = (0.95f - i * 0.12f) * (1f - 0.35f * smooth(0.8f, 1f, p));
            float dir = i % 2 == 0 ? 1f : -1f;
            float spin = seconds * dir * (0.8f + p * 5f);
            int c = argb(0.25f + 0.65f * p, CYAN);
            for (int s = 0; s < 3; s++) {
                float a0 = spin + Mth.TWO_PI * s / 3f;
                arc(vc, m, at.add(0, y, 0), r, 0.07f + 0.05f * p, a0, a0 + 1.6f, c, 16);
            }
        }
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

    /** Vertical square column of light (open tube, axis-aligned like a beacon beam). */
    private static void column(VertexConsumer vc, Matrix4f m, Vec3 base, float r, float h, int argb, int seg) {
        seg = 4;
        for (int i = 0; i < seg; i++) {
            float t0 = Mth.TWO_PI * (i + 0.5f) / seg, t1 = Mth.TWO_PI * (i + 1.5f) / seg;
            float x0 = (float) base.x + Mth.cos(t0) * r, z0 = (float) base.z + Mth.sin(t0) * r;
            float x1 = (float) base.x + Mth.cos(t1) * r, z1 = (float) base.z + Mth.sin(t1) * r;
            float y0 = (float) base.y, y1 = y0 + h;
            // u runs up the column, v across the facet: the shader's flow then rises.
            vc.addVertex(m, x0, y0, z0).setUv(0, 0).setColor(argb);
            vc.addVertex(m, x1, y0, z1).setUv(0, 1).setColor(argb);
            vc.addVertex(m, x1, y1, z1).setUv(h, 1).setColor(argb);
            vc.addVertex(m, x0, y1, z0).setUv(h, 0).setColor(argb);
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
