package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * Procedural mesh primitives in "drum space": origin at the drum axis, x/y on the drum face, +z going into
 * the machine (away from the viewer). Units are model pixels. Angles: 0 = up, clockwise as seen from the front.
 */
final class DrumMesh {
    private DrumMesh() {}

    /** Front-view clockwise position: +x in drum space appears on the viewer's left. */
    static float x(float a, float r) {
        return -Mth.sin(a) * r;
    }

    static float y(float a, float r) {
        return Mth.cos(a) * r;
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color,
                            TextureAtlasSprite s, float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(color)
                .setUv(s.getU(Mth.clamp(u, 0f, 1f)), s.getV(Mth.clamp(v, 0f, 1f)))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Flat annulus facing -z (front) or +z (back). */
    static void annulus(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite s, float r0, float r1, float z,
                        float a0, float a1, int segments, int color, int light, boolean front) {
        float nz = front ? -1 : 1;
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments, t1 = a0 + (a1 - a0) * (i + 1) / segments;
            float u0 = (float) i / segments, u1 = (float) (i + 1) / segments;
            vtx(vc, pose, x(t0, r0), y(t0, r0), z, color, s, u0, 1, light, 0, 0, nz);
            vtx(vc, pose, x(t0, r1), y(t0, r1), z, color, s, u0, 0, light, 0, 0, nz);
            vtx(vc, pose, x(t1, r1), y(t1, r1), z, color, s, u1, 0, light, 0, 0, nz);
            vtx(vc, pose, x(t1, r0), y(t1, r0), z, color, s, u1, 1, light, 0, 0, nz);
        }
    }

    /** Cylindrical wall between z0 and z1 with radial normals (outward, or inward for bores). */
    static void wall(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite s, float r, float z0, float z1,
                     float a0, float a1, int segments, int color, int light, boolean outward) {
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments, t1 = a0 + (a1 - a0) * (i + 1) / segments;
            float tm = (t0 + t1) * 0.5f;
            float nx = x(tm, 1), ny = y(tm, 1);
            if (!outward) {
                nx = -nx;
                ny = -ny;
            }
            float u0 = (float) i / segments, u1 = (float) (i + 1) / segments;
            vtx(vc, pose, x(t0, r), y(t0, r), z0, color, s, u0, 0, light, nx, ny, 0);
            vtx(vc, pose, x(t1, r), y(t1, r), z0, color, s, u1, 0, light, nx, ny, 0);
            vtx(vc, pose, x(t1, r), y(t1, r), z1, color, s, u1, 1, light, nx, ny, 0);
            vtx(vc, pose, x(t0, r), y(t0, r), z1, color, s, u0, 1, light, nx, ny, 0);
        }
    }

    /** Disc facing the front (fan built from thin quads so it stays in QUADS mode). */
    static void disc(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite s, float cx, float cy, float r, float z,
                     int segments, int color, int light) {
        for (int i = 0; i < segments; i++) {
            float t0 = Mth.TWO_PI * i / segments, t1 = Mth.TWO_PI * (i + 1) / segments;
            vtx(vc, pose, cx, cy, z, color, s, 0.5f, 0.5f, light, 0, 0, -1);
            vtx(vc, pose, cx + x(t0, r), cy + y(t0, r), z, color, s, 0.5f + 0.5f * Mth.sin(t0), 0.5f - 0.5f * Mth.cos(t0), light, 0, 0, -1);
            vtx(vc, pose, cx + x(t1, r), cy + y(t1, r), z, color, s, 0.5f + 0.5f * Mth.sin(t1), 0.5f - 0.5f * Mth.cos(t1), light, 0, 0, -1);
            vtx(vc, pose, cx, cy, z, color, s, 0.5f, 0.5f, light, 0, 0, -1);
        }
    }

    /** Small cylinder (boss) sticking out of the face toward the viewer, centred on (cx, cy). */
    static void boss(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite side, TextureAtlasSprite cap, float cx, float cy,
                     float r, float zFront, float zBack, int segments, int color, int light) {
        for (int i = 0; i < segments; i++) {
            float t0 = Mth.TWO_PI * i / segments, t1 = Mth.TWO_PI * (i + 1) / segments;
            float tm = (t0 + t1) * 0.5f;
            float nx = x(tm, 1), ny = y(tm, 1);
            vtx(vc, pose, cx + x(t0, r), cy + y(t0, r), zFront, color, side, 0, 0, light, nx, ny, 0);
            vtx(vc, pose, cx + x(t1, r), cy + y(t1, r), zFront, color, side, 1, 0, light, nx, ny, 0);
            vtx(vc, pose, cx + x(t1, r), cy + y(t1, r), zBack, color, side, 1, 1, light, nx, ny, 0);
            vtx(vc, pose, cx + x(t0, r), cy + y(t0, r), zBack, color, side, 0, 1, light, nx, ny, 0);
        }
        disc(vc, pose, cap, cx, cy, r, zFront, segments, color, light);
    }

    // ------------------------------------------------------------------ emissive (POSITION_COLOR)

    static void glowDisc(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float r, float z, int segments, int center, int edge) {
        for (int i = 0; i < segments; i++) {
            float t0 = Mth.TWO_PI * i / segments, t1 = Mth.TWO_PI * (i + 1) / segments;
            vc.addVertex(pose, cx, cy, z).setColor(center);
            vc.addVertex(pose, cx + x(t0, r), cy + y(t0, r), z).setColor(edge);
            vc.addVertex(pose, cx + x(t1, r), cy + y(t1, r), z).setColor(edge);
            vc.addVertex(pose, cx, cy, z).setColor(center);
        }
    }

    static void glowArc(VertexConsumer vc, PoseStack.Pose pose, float r0, float r1, float z, float a0, float a1, int segments, int inner, int outer) {
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments, t1 = a0 + (a1 - a0) * (i + 1) / segments;
            vc.addVertex(pose, x(t0, r0), y(t0, r0), z).setColor(inner);
            vc.addVertex(pose, x(t0, r1), y(t0, r1), z).setColor(outer);
            vc.addVertex(pose, x(t1, r1), y(t1, r1), z).setColor(outer);
            vc.addVertex(pose, x(t1, r0), y(t1, r0), z).setColor(inner);
        }
    }

    // ------------------------------------------------------------------ shader ribbons (POSITION_TEX_COLOR)

    /** Flat ring ribbon on the drum face for the energy_field shader (u along the ring, v across). */
    static void fieldRing(VertexConsumer vc, PoseStack.Pose pose, float r, float width, float z, float a0, float a1,
                          int segments, int argb) {
        // Straight octagonal segments: the ring reads as built from blocks, not a smooth torus.
        segments = Math.max(1, Math.round(8f * (a1 - a0) / Mth.TWO_PI));
        float r0 = r - width * 0.5f, r1 = r + width * 0.5f;
        float len = (a1 - a0) * r / 4f;
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments, t1 = a0 + (a1 - a0) * (i + 1) / segments;
            float u0 = len * i / segments, u1 = len * (i + 1) / segments;
            vc.addVertex(pose, x(t0, r0), y(t0, r0), z).setUv(u0, 0).setColor(argb);
            vc.addVertex(pose, x(t0, r1), y(t0, r1), z).setUv(u0, 1).setColor(argb);
            vc.addVertex(pose, x(t1, r1), y(t1, r1), z).setUv(u1, 1).setColor(argb);
            vc.addVertex(pose, x(t1, r0), y(t1, r0), z).setUv(u1, 0).setColor(argb);
        }
    }

    // ------------------------------------------------------------------ voxel primitives (Minecraft-style)

    /** Bit flags for {@link #box} faces. */
    static final int F_FRONT = 1, F_BACK = 2, F_LEFT = 4, F_RIGHT = 8, F_UP = 16, F_DOWN = 32, F_ALL = 63;

    /** UV coordinate (0..1) of a model-pixel coordinate inside its 16px texture tile, like vanilla elements. */
    private static float tile(float base, float c) {
        return Mth.clamp((c - base) / 16f, 0f, 1f);
    }

    private static float tileBase(float a) {
        return (float) Math.floor(a / 16f) * 16f;
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite s, int color, int light,
                             float[] p0, float[] p1, float[] p2, float[] p3,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vtx(vc, pose, p0[0], p0[1], p0[2], color, s, u0, v0, light, nx, ny, nz);
        vtx(vc, pose, p1[0], p1[1], p1[2], color, s, u1, v0, light, nx, ny, nz);
        vtx(vc, pose, p2[0], p2[1], p2[2], color, s, u1, v1, light, nx, ny, nz);
        vtx(vc, pose, p3[0], p3[1], p3[2], color, s, u0, v1, light, nx, ny, nz);
    }

    /**
     * Axis-aligned cuboid with texels mapped from model-pixel coordinates (texels keep their size whatever
     * the box size). Front is -z (toward the viewer). {@code sideColor} tints the four side faces.
     */
    static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite front, TextureAtlasSprite side,
                    float x0, float y0, float z0, float x1, float y1, float z1,
                    int frontColor, int sideColor, int light, int faces) {
        float bx = tileBase(Math.min(x0, x1)), by = tileBase(Math.min(y0, y1)), bz = tileBase(Math.min(z0, z1));
        float ux0 = tile(bx, x0), ux1 = tile(bx, x1);
        float vy0 = 1f - tile(by, y1), vy1 = 1f - tile(by, y0);
        float uz0 = tile(bz, z0), uz1 = tile(bz, z1);
        if ((faces & F_FRONT) != 0)
            face(vc, pose, front, frontColor, light, new float[]{x0, y1, z0}, new float[]{x1, y1, z0}, new float[]{x1, y0, z0},
                    new float[]{x0, y0, z0}, ux0, vy0, ux1, vy1, 0, 0, -1);
        if ((faces & F_BACK) != 0)
            face(vc, pose, side, sideColor, light, new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, new float[]{x0, y0, z1},
                    new float[]{x1, y0, z1}, ux0, vy0, ux1, vy1, 0, 0, 1);
        if ((faces & F_LEFT) != 0)
            face(vc, pose, side, sideColor, light, new float[]{x0, y1, z1}, new float[]{x0, y1, z0}, new float[]{x0, y0, z0},
                    new float[]{x0, y0, z1}, uz0, vy0, uz1, vy1, -1, 0, 0);
        if ((faces & F_RIGHT) != 0)
            face(vc, pose, side, sideColor, light, new float[]{x1, y1, z0}, new float[]{x1, y1, z1}, new float[]{x1, y0, z1},
                    new float[]{x1, y0, z0}, uz0, vy0, uz1, vy1, 1, 0, 0);
        if ((faces & F_UP) != 0)
            face(vc, pose, side, sideColor, light, new float[]{x0, y1, z1}, new float[]{x1, y1, z1}, new float[]{x1, y1, z0},
                    new float[]{x0, y1, z0}, ux0, uz0, ux1, uz1, 0, 1, 0);
        if ((faces & F_DOWN) != 0)
            face(vc, pose, side, sideColor, light, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1},
                    new float[]{x0, y0, z1}, ux0, uz0, ux1, uz1, 0, -1, 0);
    }

    private static boolean inRing(int i, int j, float cell, float r0, float r1) {
        float cx = (i + 0.5f) * cell, cy = (j + 0.5f) * cell;
        float d = Mth.sqrt(cx * cx + cy * cy);
        return d >= r0 && d < r1;
    }

    /**
     * Ring rasterised on a pixel grid and extruded as voxels: the stepped "Minecraft circle". Every cell
     * whose centre lies in [r0, r1) becomes a block from zFront to zBack; faces between neighbouring cells
     * are skipped, so only the stepped silhouette gets walls. r0 &lt; 0 gives a solid disc.
     */
    static void pixelRing(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite front, TextureAtlasSprite side,
                          float r0, float r1, float zFront, float zBack, float cell, int frontColor, int sideColor, int light) {
        int n = (int) Math.ceil(r1 / cell) + 1;
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                if (!inRing(i, j, cell, r0, r1)) continue;
                int faces = F_FRONT;
                if (!inRing(i - 1, j, cell, r0, r1)) faces |= F_LEFT;
                if (!inRing(i + 1, j, cell, r0, r1)) faces |= F_RIGHT;
                if (!inRing(i, j + 1, cell, r0, r1)) faces |= F_UP;
                if (!inRing(i, j - 1, cell, r0, r1)) faces |= F_DOWN;
                box(vc, pose, front, side, i * cell, j * cell, zFront, (i + 1) * cell, (j + 1) * cell, zBack,
                        frontColor, sideColor, light, faces);
            }
        }
    }

    /** Flat emissive rectangle facing the front (POSITION_COLOR). */
    static void glowRect(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, int color) {
        vc.addVertex(pose, x0, y0, z).setColor(color);
        vc.addVertex(pose, x0, y1, z).setColor(color);
        vc.addVertex(pose, x1, y1, z).setColor(color);
        vc.addVertex(pose, x1, y0, z).setColor(color);
    }

    /** Emissive axis-aligned cuboid: six flat POSITION_COLOR faces (the render type does not cull). */
    static void glowBox(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        glowRect(vc, pose, x0, y0, x1, y1, z0, color);
        glowRect(vc, pose, x0, y0, x1, y1, z1, color);
        float[][] sides = {
                {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}};
        for (float[] q : sides) {
            for (int i = 0; i < 12; i += 3) vc.addVertex(pose, q[i], q[i + 1], q[i + 2]).setColor(color);
        }
    }

    /** Emissive pixel ring (stepped), one flat cell per pixel. */
    static void glowPixelRing(VertexConsumer vc, PoseStack.Pose pose, float r0, float r1, float z, float cell, int color) {
        int n = (int) Math.ceil(r1 / cell) + 1;
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                if (inRing(i, j, cell, r0, r1)) glowRect(vc, pose, i * cell, j * cell, (i + 1) * cell, (j + 1) * cell, z, color);
            }
        }
    }

    /** Camera-independent quad with UV 0..1 (for the orb / rift shaders), in the drum face plane. */
    static void faceQuad(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float half, float z, int argb) {
        vc.addVertex(pose, cx - half, cy - half, z).setUv(1, 1).setColor(argb);
        vc.addVertex(pose, cx - half, cy + half, z).setUv(1, 0).setColor(argb);
        vc.addVertex(pose, cx + half, cy + half, z).setUv(0, 0).setColor(argb);
        vc.addVertex(pose, cx + half, cy - half, z).setUv(0, 1).setColor(argb);
    }
}
