package com.nexusgate.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tumbling cube-shaped data fragment (pixel sprite, rotates in 45-degree notches). Outward = disintegration (body breaking into light);
 * with {@link #converge} it flies into a point and grows brighter = materialisation.
 */
public class ShardParticle extends GlowParticle {
    private final float spin;
    @Nullable private Vec3 sink;
    private int sinkArrival;

    public ShardParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites);
        this.friction = 0.9f;
        // Tumbles in 45-degree notches (see tick), like a tiny rotated block.
        this.spin = (random.nextBoolean() ? 1f : -1f) * Mth.HALF_PI * 0.5f;
        this.roll = random.nextInt(8) * Mth.HALF_PI * 0.5f;
        life(18 + random.nextInt(14));
        size(0.06f + random.nextFloat() * 0.06f);
        colors(0xFFFFFF, 0x22C8F0);
    }

    /** Travel to the point in exactly {@code ticks} ticks (lifetime is set to match). */
    public ShardParticle converge(Vec3 point, int ticks) {
        this.sink = point;
        this.sinkArrival = Math.max(2, ticks);
        life(sinkArrival + 2);
        colors(0x22C8F0, 0xFFFFFF);
        return this;
    }

    @Override
    public void tick() {
        oRoll = roll;
        if (age % 3 == 0) {
            roll += spin;
            oRoll = roll; // snap, no interpolated in-between angle
        }
        if (sink != null) {
            int left = Math.max(1, sinkArrival - age);
            xd = (sink.x - x) / left;
            yd = (sink.y - y) / left;
            zd = (sink.z - z) / left;
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            move(xd, yd, zd);
            float t = lifeFraction(0);
            applyColor(t);
            alpha = maxAlpha * stepped(t * 4f, 3);
            quadSize = steppedSize(1.2f - 0.6f * t);
            return;
        }
        super.tick();
        quadSize = steppedSize(1f - 0.5f * lifeFraction(0));
    }
}
