package com.nexusgate.client.particle;

import com.nexusgate.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Registers the providers and offers typed spawn helpers. The FX code builds particles directly (so it can
 * pass targets, colours and lifetimes) using the sprite sets captured at registration.
 */
public final class NgParticles {
    @Nullable private static SpriteSet spark, mote, shard, ring;

    private NgParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> {
            spark = sprites;
            return (type, level, x, y, z, vx, vy, vz) -> new SparkParticle(level, x, y, z, vx, vy, vz, sprites);
        });
        event.registerSpriteSet(ModParticles.MOTE.get(), sprites -> {
            mote = sprites;
            return (type, level, x, y, z, vx, vy, vz) -> new MoteParticle(level, x, y, z, vx, vy, vz, sprites);
        });
        event.registerSpriteSet(ModParticles.SHARD.get(), sprites -> {
            shard = sprites;
            return (type, level, x, y, z, vx, vy, vz) -> new ShardParticle(level, x, y, z, vx, vy, vz, sprites);
        });
        event.registerSpriteSet(ModParticles.SHOCK_RING.get(), sprites -> {
            ring = sprites;
            return (type, level, x, y, z, vx, vy, vz) -> new ShockRingParticle(level, x, y, z, vx, vy, vz, sprites);
        });
    }

    @Nullable
    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static <P extends Particle> P add(P p) {
        Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    @Nullable
    public static SparkParticle spark(double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = level();
        return level == null || spark == null ? null : add(new SparkParticle(level, x, y, z, vx, vy, vz, spark));
    }

    @Nullable
    public static MoteParticle mote(double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = level();
        return level == null || mote == null ? null : add(new MoteParticle(level, x, y, z, vx, vy, vz, mote));
    }

    @Nullable
    public static MoteParticle moteTo(Vec3 from, Vec3 target, float pull, float swirl) {
        MoteParticle p = mote(from.x, from.y, from.z, 0, 0, 0);
        return p == null ? null : p.attract(target, pull, swirl);
    }

    @Nullable
    public static ShardParticle shard(double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = level();
        return level == null || shard == null ? null : add(new ShardParticle(level, x, y, z, vx, vy, vz, shard));
    }

    @Nullable
    public static ShockRingParticle ring(double x, double y, double z, float radius) {
        ClientLevel level = level();
        return level == null || ring == null ? null : add(new ShockRingParticle(level, x, y, z, 0, 0, 0, ring).radius(radius));
    }
}
