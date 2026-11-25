package com.nexusgate.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A thin energy streak stretched along its velocity, like a discharge arc. */
public class SparkParticle extends GlowParticle {
    private float stretch = 3.0f;

    public SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites);
        this.friction = 0.86f;
        this.gravity = 0.15f;
        life(8 + random.nextInt(8));
        size(0.035f);
        colors(0xFFFFFF, 0x33F0FF);
    }

    public SparkParticle stretch(float s) {
        this.stretch = s;
        return this;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        Vector3f p = new Vector3f((float) (Mth.lerp(partialTicks, xo, x) - cam.x),
                (float) (Mth.lerp(partialTicks, yo, y) - cam.y), (float) (Mth.lerp(partialTicks, zo, z) - cam.z));
        Vector3f v = new Vector3f((float) xd, (float) yd, (float) zd).mul(stretch);
        if (v.lengthSquared() < 1.0e-6f) v.set(0, 0.01f, 0);
        Vector3f side = new Vector3f(v).cross(p);
        if (side.lengthSquared() < 1.0e-8f) side.set(1, 0, 0);
        side.normalize().mul(quadSize);
        Vector3f a = new Vector3f(p).sub(v.x * 0.5f, v.y * 0.5f, v.z * 0.5f);
        Vector3f b = new Vector3f(p).add(v.x * 0.5f, v.y * 0.5f, v.z * 0.5f);
        int light = getLightColor(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        vertex(buffer, a.x - side.x, a.y - side.y, a.z - side.z, u0, v1, light);
        vertex(buffer, a.x + side.x, a.y + side.y, a.z + side.z, u0, v0, light);
        vertex(buffer, b.x + side.x, b.y + side.y, b.z + side.z, u1, v0, light);
        vertex(buffer, b.x - side.x, b.y - side.y, b.z - side.z, u1, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }
}
