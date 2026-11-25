package com.nexusgate.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Soft glowing mote. With a target it spirals inward (charging); without one it drifts and breathes. */
public class MoteParticle extends GlowParticle {
    @Nullable private Vec3 target;
    private float pull = 0.03f;
    private float swirl = 0.0f;

    public MoteParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites);
        this.friction = 0.9f;
        life(20 + random.nextInt(20));
        size(0.05f + random.nextFloat() * 0.04f);
        colors(0xCFFBFF, 0x1AA6C8);
        alpha(0.85f);
    }

    /** Accelerates toward the point, with a tangential swirl around the vertical axis. */
    public MoteParticle attract(Vec3 point, float pull, float swirl) {
        this.target = point;
        this.pull = pull;
        this.swirl = swirl;
        return this;
    }

    @Override
    public void tick() {
        if (target != null) {
            double dx = target.x - x, dy = target.y - y, dz = target.z - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.12) {
                remove();
                return;
            }
            xd += dx / dist * pull - dz / dist * swirl;
            yd += dy / dist * pull;
            zd += dz / dist * pull + dx / dist * swirl;
        }
        super.tick();
        float t = lifeFraction(0);
        quadSize = steppedSize((0.75f + 0.25f * Mth.sin(age * 0.6f)) * (1f - 0.4f * t));
    }
}
