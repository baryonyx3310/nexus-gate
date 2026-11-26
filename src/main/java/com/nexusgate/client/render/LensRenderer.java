package com.nexusgate.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the space-time lenses of a frame and draws them in one batch: copy the frame, then redraw each lens
 * disc with the {@code spacetime_lens} program sampling that copy with bent coordinates. Big lenses (bodies,
 * cores) go first; small ones (beam, fragments) are only kept where they do not cut into a big one, since every
 * lens samples the same unbent copy.
 */
public final class LensRenderer {
    private record Lens(Vec3 center, float radius, float strength, float seed, float chroma) {}

    private static final List<Lens> BIG = new ArrayList<>();
    private static final List<Lens> SMALL = new ArrayList<>();

    private LensRenderer() {}

    /** A lens around a body or a core. */
    public static void big(Vec3 center, float radius, float strength, float seed) {
        if (strength > 0.01f) BIG.add(new Lens(center, radius, Mth.clamp(strength, 0f, 1f), seed, 1f));
    }

    /** A minor lens (along a beam, around a fragment). Dropped where it would overlap a big one. */
    public static void small(Vec3 center, float radius, float strength, float seed) {
        if (strength > 0.01f) SMALL.add(new Lens(center, radius, Mth.clamp(strength, 0f, 1f), seed, 0.6f));
    }

    public static void flush(Matrix4f m, Vector3f right, Vector3f up) {
        try {
            if (BIG.isEmpty() && SMALL.isEmpty()) return;
            if (!LensSupport.enabled() || !SceneCopy.capture()) return;
            ShaderInstance shader = ModShaders.lens();
            if (shader == null) return;
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, SceneCopy.textureId());
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Lens l : BIG) disc(b, m, right, up, l);
            for (Lens l : SMALL) {
                if (!overlapsBig(l)) disc(b, m, right, up, l);
            }
            MeshData mesh = b.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        } finally {
            BIG.clear();
            SMALL.clear();
        }
    }

    private static boolean overlapsBig(Lens s) {
        for (Lens b : BIG) {
            double reach = b.radius + s.radius;
            if (b.center.distanceToSqr(s.center) < reach * reach) return true;
        }
        return false;
    }

    /** Camera-facing disc; UV is the local position (-1..1) with x along the camera's right and y along its up. */
    private static void disc(BufferBuilder b, Matrix4f m, Vector3f right, Vector3f up, Lens l) {
        float x = (float) l.center.x, y = (float) l.center.y, z = (float) l.center.z, r = l.radius;
        float rx = right.x * r, ry = right.y * r, rz = right.z * r;
        float ux = up.x * r, uy = up.y * r, uz = up.z * r;
        int color = (Math.round(l.strength * 255f) << 24) | (Math.round((l.seed % 1f) * 255f) << 16)
                | (Math.round(l.chroma * 255f));
        b.addVertex(m, x - rx - ux, y - ry - uy, z - rz - uz).setUv(-1, -1).setColor(color);
        b.addVertex(m, x + rx - ux, y + ry - uy, z + rz - uz).setUv(1, -1).setColor(color);
        b.addVertex(m, x + rx + ux, y + ry + uy, z + rz + uz).setUv(1, 1).setColor(color);
        b.addVertex(m, x - rx + ux, y - ry + uy, z - rz + uz).setUv(-1, 1).setColor(color);
    }
}
