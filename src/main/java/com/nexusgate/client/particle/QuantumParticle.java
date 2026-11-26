package com.nexusgate.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A quantum fluctuation: one square pixel of converted matter riding a fixed path. Dematerialising, it leaves
 * the body, joins the connection beam and runs along it into the core; rebuilding, it takes the same path
 * backwards. Purely visual.
 */
public class QuantumParticle extends GlowParticle {
    private Vec3[] path = new Vec3[0];
    private float[] cumulative = new float[0];
    private float speed = 0.12f;
    private float travelled;
    private float jitter;
    private float phase;

    public QuantumParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites);
        this.friction = 1f;
        size(0.03f + random.nextFloat() * 0.025f);
        colors(0xFFFFFF, 0x33F0FF);
        alpha(0.95f);
        life(40);
        phase = random.nextFloat() * Mth.TWO_PI;
    }

    /** Follows the given points in order at {@code speed} blocks per tick; lifetime fits the path. */
    public QuantumParticle follow(float speed, float jitter, Vec3... points) {
        this.path = points;
        this.speed = Math.max(0.01f, speed);
        this.jitter = jitter;
        this.cumulative = new float[points.length];
        for (int i = 1; i < points.length; i++) {
            cumulative[i] = cumulative[i - 1] + (float) points[i].distanceTo(points[i - 1]);
        }
        float length = points.length > 1 ? cumulative[points.length - 1] : 0f;
        life(Math.max(4, Mth.ceil(length / this.speed) + 1));
        if (points.length > 0) {
            setPos(points[0].x, points[0].y, points[0].z);
            xo = x;
            yo = y;
            zo = z;
        }
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive() || path.length < 2) return;
        travelled += speed;
        float length = cumulative[path.length - 1];
        if (travelled >= length) {
            remove();
            return;
        }
        int seg = 1;
        while (seg < path.length - 1 && cumulative[seg] < travelled) seg++;
        float span = Math.max(1e-4f, cumulative[seg] - cumulative[seg - 1]);
        float k = (travelled - cumulative[seg - 1]) / span;
        Vec3 p = path[seg - 1].lerp(path[seg], k);
        // A little quantum uncertainty around the path, in whole sixteenths of a block.
        double w = Math.round(Mth.sin(age * 0.9f + phase) * jitter * 16f) / 16.0;
        double v = Math.round(Mth.cos(age * 1.3f + phase) * jitter * 16f) / 16.0;
        setPos(p.x + w, p.y + v * 0.5, p.z + v);
        // Fade only at the two ends of the path, so the pixel stays lit all the way into the core.
        float t = travelled / length;
        alpha = maxAlpha * stepped(Math.min(t / 0.1f, 1f) * Math.min((1f - t) / 0.1f, 1f), 3);
        quadSize = steppedSize(0.8f + 0.2f * Mth.sin(age * 0.7f + phase));
    }
}
