package com.nexusgate.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A flat ring lying on the ground that expands and thins out: the pressure wave of a jump. */
public class ShockRingParticle extends GlowParticle {
    private float maxRadius = 2.5f;

    public ShockRingParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites);
        life(16);
        colors(0xFFFFFF, 0x1E9FD0);
    }

    public ShockRingParticle radius(float r) {
        this.maxRadius = r;
        return this;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = lifeFraction(0);
        applyColor(t);
        alpha = maxAlpha * (1f - t) * (1f - t);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (x - cam.x), py = (float) (y - cam.y) + 0.02f, pz = (float) (z - cam.z);
        float t = lifeFraction(partialTicks);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float r = Mth.lerp(ease, 0.2f, maxRadius);
        float a = maxAlpha * stepped((1f - t) * (1f - t), 3);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        // Up-facing and down-facing copies so the ring reads from both above and below.
        quad(buffer, px, py, pz, r, u0, u1, v0, v1, a, light, false);
        quad(buffer, px, py, pz, r, u0, u1, v0, v1, a, light, true);
    }

    private void quad(VertexConsumer b, float x, float y, float z, float r, float u0, float u1, float v0, float v1,
                      float a, int light, boolean flip) {
        if (!flip) {
            b.addVertex(x - r, y, z - r).setUv(u0, v0).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x - r, y, z + r).setUv(u0, v1).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x + r, y, z + r).setUv(u1, v1).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x + r, y, z - r).setUv(u1, v0).setColor(rCol, gCol, bCol, a).setLight(light);
        } else {
            b.addVertex(x - r, y, z - r).setUv(u0, v0).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x + r, y, z - r).setUv(u1, v0).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x + r, y, z + r).setUv(u1, v1).setColor(rCol, gCol, bCol, a).setLight(light);
            b.addVertex(x - r, y, z + r).setUv(u0, v1).setColor(rCol, gCol, bCol, a).setLight(light);
        }
    }
}
