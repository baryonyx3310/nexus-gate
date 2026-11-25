package com.nexusgate.client.fx;

import com.nexusgate.client.particle.NgParticles;
import com.nexusgate.client.particle.ShardParticle;
import com.nexusgate.client.particle.SparkParticle;
import com.nexusgate.net.GateFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client-side brain of every teleport visual. Receives {@link GateFxPayload}s, tracks the charges in view,
 * spawns particles, and exposes progress to the station renderer, world renderer, HUD and the gate screen.
 */
public final class TeleportFxController {
    /** Ticks before completion when the local "dive into the core" starts. */
    public static final int DIVE_TICKS = 20;

    public static final int CYAN = 0x33F0FF;
    public static final int RED = 0xFF4A4A;

    public static final class Charge {
        public final int entityId;
        public final Vec3 start;
        public final BlockPos station;
        public final int total;
        public final long startTick;

        Charge(int entityId, Vec3 start, BlockPos station, int total, long startTick) {
            this.entityId = entityId;
            this.start = start;
            this.station = station;
            this.total = Math.max(1, total);
            this.startTick = startTick;
        }

        public float elapsed(float partial) {
            return ticks - startTick + partial;
        }

        public float progress(float partial) {
            return Mth.clamp(elapsed(partial) / total, 0f, 1f);
        }

        /** 0 until the last {@link #DIVE_TICKS}, then rises to 1 at completion. */
        public float dive(float partial) {
            float begin = Math.max(0, total - DIVE_TICKS);
            return Mth.clamp((elapsed(partial) - begin) / Math.max(1, total - begin), 0f, 1f);
        }

        public Vec3 position(float partial) {
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
            return e != null ? e.getPosition(partial) : start;
        }
    }

    public enum BurstKind { DEPART, ARRIVE, CANCEL }

    public record Burst(BurstKind kind, Vec3 pos, BlockPos station, long startTick) {
        public static final int LENGTH = 30;

        public float age(float partial) {
            return (ticks - startTick + partial) / LENGTH;
        }
    }

    /** Receives events for the local player (implemented by the gate screen). */
    public interface LocalListener {
        void onChargeStart(Charge charge);

        void onChargeEnd(boolean departed, @Nullable Component reason);
    }

    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long ticks;
    @Nullable private static LocalListener listener;

    private TeleportFxController() {}

    public static void setListener(@Nullable LocalListener l) {
        listener = l;
    }

    public static long ticks() {
        return ticks;
    }

    public static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    public static Iterable<Charge> charges() {
        return CHARGES.values();
    }

    public static Iterable<Burst> bursts() {
        return BURSTS;
    }

    @Nullable
    public static Charge localCharge() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : CHARGES.get(mc.player.getId());
    }

    @Nullable
    public static Charge chargeAt(BlockPos station) {
        for (Charge c : CHARGES.values()) if (c.station.equals(station)) return c;
        return null;
    }

    /** Ticks since the station last fired (for its recoil flash), or -1. */
    public static float sinceDeparture(BlockPos station, float partial) {
        for (Burst b : BURSTS) {
            if (b.kind == BurstKind.DEPART && b.station.equals(station)) return ticks - b.startTick + partial;
        }
        return -1;
    }

    private static boolean isLocal(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entityId;
    }

    public static void handle(GateFxPayload p) {
        boolean local = isLocal(p.entityId());
        Component reason = p.message().orElse(null);
        switch (p.phase()) {
            case GateFxPayload.PHASE_START -> {
                Charge c = new Charge(p.entityId(), p.pos(), p.station(), p.totalTicks(), ticks);
                CHARGES.put(p.entityId(), c);
                if (local) {
                    DiveFx.reset();
                    if (listener != null) listener.onChargeStart(c);
                }
            }
            case GateFxPayload.PHASE_CANCEL -> {
                Charge c = CHARGES.remove(p.entityId());
                BURSTS.add(new Burst(BurstKind.CANCEL, c != null ? c.position(0) : p.pos(), p.station(), ticks));
                cancelSparks(p.pos());
                if (local) {
                    DiveFx.abort(c != null ? c.dive(partial()) : 0f);
                    if (listener != null) listener.onChargeEnd(false, reason);
                    GateSounds.abort();
                }
            }
            case GateFxPayload.PHASE_REJECT -> {
                if (local && listener != null) listener.onChargeEnd(false, reason);
                if (local) GateSounds.denied();
            }
            case GateFxPayload.PHASE_DEPART -> {
                CHARGES.remove(p.entityId());
                BURSTS.add(new Burst(BurstKind.DEPART, p.pos(), p.station(), ticks));
                if (local) {
                    DiveFx.enterTunnel();
                    if (listener != null) listener.onChargeEnd(true, null);
                    GateSounds.dive();
                } else {
                    departParticles(p.pos());
                }
            }
            case GateFxPayload.PHASE_ARRIVE -> {
                BURSTS.add(new Burst(BurstKind.ARRIVE, p.pos(), p.station(), ticks));
                arriveParticles(p.pos());
                if (local) DiveFx.arrived();
            }
            default -> { }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CHARGES.clear();
            BURSTS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        BURSTS.removeIf(b -> ticks - b.startTick > Burst.LENGTH);
        Iterator<Charge> it = CHARGES.values().iterator();
        while (it.hasNext()) {
            Charge c = it.next();
            if (ticks - c.startTick > c.total + 60) {
                it.remove();
                continue;
            }
            chargingParticles(c);
        }
    }

    // ---------------------------------------------------------------- particles

    private static Vec3 core(BlockPos station) {
        return new Vec3(station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5);
    }

    private static void chargingParticles(Charge c) {
        float progress = c.progress(0);
        Vec3 at = c.position(0);
        Vec3 chest = at.add(0, 1.0, 0);
        int count = 1 + (int) (progress * 3);
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 1.2 + RANDOM.nextDouble() * 0.6;
            Vec3 from = at.add(Math.cos(a) * r, RANDOM.nextDouble() * 2.2, Math.sin(a) * r);
            var m = NgParticles.moteTo(from, chest, 0.012f + progress * 0.02f, 0.03f);
            if (m != null) m.life(30);
        }
        // Discharge sparks from the station core toward the traveller.
        if (RANDOM.nextFloat() < 0.25f + progress * 0.5f) {
            Vec3 core = core(c.station);
            Vec3 dir = chest.subtract(core).normalize().scale(0.25 + RANDOM.nextDouble() * 0.2);
            SparkParticle s = NgParticles.spark(core.x, core.y, core.z,
                    dir.x + RANDOM.nextGaussian() * 0.05, dir.y + RANDOM.nextGaussian() * 0.05, dir.z + RANDOM.nextGaussian() * 0.05);
            if (s != null) s.stretch(2.0f);
        }
        if (progress > 0.75f && RANDOM.nextFloat() < 0.5f) {
            NgParticles.spark(at.x + RANDOM.nextGaussian() * 0.3, at.y + 0.1, at.z + RANDOM.nextGaussian() * 0.3,
                    0, 0.25 + RANDOM.nextDouble() * 0.2, 0);
        }
    }

    private static void departParticles(Vec3 pos) {
        for (int i = 0; i < 70; i++) {
            double y = RANDOM.nextDouble() * 1.9;
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 0.25 + RANDOM.nextDouble() * 0.15;
            ShardParticle s = NgParticles.shard(pos.x + Math.cos(a) * r, pos.y + y, pos.z + Math.sin(a) * r,
                    Math.cos(a) * 0.05, 0.08 + RANDOM.nextDouble() * 0.12, Math.sin(a) * 0.05);
            if (s != null) s.life(16 + RANDOM.nextInt(16));
        }
        for (int i = 0; i < 24; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            NgParticles.spark(pos.x, pos.y + 1.0, pos.z, Math.cos(a) * 0.4, (RANDOM.nextDouble() - 0.3) * 0.4, Math.sin(a) * 0.4);
        }
        NgParticles.ring(pos.x, pos.y, pos.z, 3.0f);
    }

    private static void arriveParticles(Vec3 pos) {
        Vec3 sink = pos.add(0, 1.0, 0);
        for (int i = 0; i < 60; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 1.6 + RANDOM.nextDouble() * 1.2;
            ShardParticle s = NgParticles.shard(pos.x + Math.cos(a) * r, pos.y + RANDOM.nextDouble() * 2.4, pos.z + Math.sin(a) * r, 0, 0, 0);
            if (s != null) s.converge(sink, 8 + RANDOM.nextInt(8));
        }
        NgParticles.ring(pos.x, pos.y, pos.z, 2.6f);
        var second = NgParticles.ring(pos.x, pos.y, pos.z, 1.6f);
        if (second != null) second.life(22);
    }

    private static void cancelSparks(Vec3 pos) {
        for (int i = 0; i < 20; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            SparkParticle s = NgParticles.spark(pos.x, pos.y + 1.0, pos.z, Math.cos(a) * 0.25, RANDOM.nextDouble() * 0.2, Math.sin(a) * 0.25);
            if (s != null) s.colors(0xFFFFFF, RED);
        }
    }

    /** Idle shimmer rising from the station vents (called from the block's animateTick). */
    public static void idleStation(BlockPos lower, RandomSource random) {
        double x = lower.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double z = lower.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        var m = NgParticles.mote(x, lower.getY() + 0.2, z, 0, 0.012 + random.nextDouble() * 0.01, 0);
        if (m != null) m.life(40).alpha(0.45f);
    }
}
