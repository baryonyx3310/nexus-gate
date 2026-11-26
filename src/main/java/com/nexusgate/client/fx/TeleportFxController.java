package com.nexusgate.client.fx;

import com.nexusgate.client.particle.NgParticles;
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
 * Client-side brain of every relocation visual. Receives {@link GateFxPayload}s (the same events the server
 * always sent), tracks the charges and receptions in view, spawns the quantum fluctuations, and exposes the
 * timeline to the station renderer, the world renderer, the body dissolve, the HUD and the gate screen.
 * <p>
 * Nothing here changes game state: the server still decides and performs the teleport; these values only
 * decide what is drawn. See {@link RelocationTimeline} for the sequence.
 */
public final class TeleportFxController {
    /** Ticks before completion when the local "dive into the core" starts. */
    public static final int DIVE_TICKS = 20;

    public static final int CYAN = 0x33F0FF;
    public static final int RED = 0xFF4A4A;

    /** Height of the orbital beam above the crown, in blocks. */
    public static final float SKY_HEIGHT = 96f;
    /** Ticks a departed body stays hidden while its ARRIVE is on the way. */
    private static final int IN_TRANSIT_TICKS = 40;

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
            return entityPosition(entityId, start, partial);
        }
    }

    public enum BurstKind { DEPART, CANCEL }

    public record Burst(BurstKind kind, Vec3 pos, BlockPos station, long startTick) {
        public static final int LENGTH = RelocationTimeline.RELEASE_TICKS;

        public float age(float partial) {
            return (ticks - startTick + partial) / LENGTH;
        }

        public float ticksSince(float partial) {
            return ticks - startTick + partial;
        }
    }

    /** A transmission arriving at a destination relocator: sky beam, core, reconstruction of the body. */
    public record Reception(int entityId, BlockPos station, Vec3 pos, long startTick) {
        public float age(float partial) {
            return ticks - startTick + partial;
        }

        public Vec3 body(float partial) {
            return entityPosition(entityId, pos, partial);
        }
    }

    private record Rematerialize(float from, long startTick) {}

    /** Receives events for the local player (implemented by the gate screen). */
    public interface LocalListener {
        void onChargeStart(Charge charge);

        void onChargeEnd(boolean departed, @Nullable Component reason);
    }

    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Reception> RECEPTIONS = new ArrayList<>();
    private static final Map<Integer, Rematerialize> REMATERIALIZING = new HashMap<>();
    private static final Map<Integer, Long> IN_TRANSIT = new HashMap<>();
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

    public static Iterable<Reception> receptions() {
        return RECEPTIONS;
    }

    public static boolean anyActive() {
        return !CHARGES.isEmpty() || !BURSTS.isEmpty() || !RECEPTIONS.isEmpty() || QuantumFx.any();
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

    /** Destination core energy while it receives a transmission (0..1), or -1 when it is not receiving. */
    public static float receptionAt(BlockPos station, float partial) {
        float best = -1f;
        for (Reception r : RECEPTIONS) {
            if (r.station.equals(station)) best = Math.max(best, RelocationTimeline.receptionCore(r.age(partial)));
        }
        return best;
    }

    /**
     * How a body looks right now: phase-A instability, compression toward the focal point and how far that point
     * has travelled along the beam. Used only to draw the body (see {@code MatterDissolveRenderer}); the entity
     * itself is never touched.
     */
    public static RelocationTimeline.Body body(int entityId, float partial) {
        Charge c = CHARGES.get(entityId);
        if (c != null) {
            float t = c.progress(partial);
            return new RelocationTimeline.Body(RelocationTimeline.instability(t), RelocationTimeline.dissolve(t),
                    RelocationTimeline.focalTravel(t));
        }
        Reception r = receptionOf(entityId);
        if (r != null) {
            float a = r.age(partial);
            return new RelocationTimeline.Body(RelocationTimeline.arrivalInstability(a), RelocationTimeline.reconstruct(a),
                    RelocationTimeline.arrivalTravel(a));
        }
        Long departed = IN_TRANSIT.get(entityId);
        if (departed != null && ticks - departed < IN_TRANSIT_TICKS) return new RelocationTimeline.Body(1f, 1f, 1f);
        Rematerialize m = REMATERIALIZING.get(entityId);
        if (m != null) {
            float k = ticks - m.startTick + partial;
            float conv = RelocationTimeline.rematerialize(m.from, k);
            return new RelocationTimeline.Body(1f - Mth.clamp(k / RelocationTimeline.REMATERIALIZE_TICKS, 0f, 1f), conv, 0f);
        }
        return RelocationTimeline.Body.NONE;
    }

    /**
     * The focal point the body collapses into: where the connection beam meets the body (chest height), moving
     * along the beam into the core in phase D (and out of it, at the destination).
     */
    public static Vec3 focal(int entityId, float partial) {
        Charge c = CHARGES.get(entityId);
        if (c != null) {
            Vec3 chest = c.position(partial).add(0, 1.0, 0);
            return chest.lerp(core(c.station), RelocationTimeline.focalTravel(c.progress(partial)));
        }
        Reception r = receptionOf(entityId);
        if (r != null) {
            Vec3 chest = r.body(partial).add(0, 1.0, 0);
            return chest.lerp(core(r.station), RelocationTimeline.arrivalTravel(r.age(partial)));
        }
        return entityPosition(entityId, Vec3.ZERO, partial).add(0, 1.0, 0);
    }

    @Nullable
    private static Reception receptionOf(int entityId) {
        for (Reception r : RECEPTIONS) if (r.entityId == entityId) return r;
        return null;
    }

    private static boolean isLocal(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entityId;
    }

    private static Vec3 entityPosition(int entityId, Vec3 fallback, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        return e != null ? e.getPosition(partial) : fallback;
    }

    public static void handle(GateFxPayload p) {
        boolean local = isLocal(p.entityId());
        Component reason = p.message().orElse(null);
        switch (p.phase()) {
            case GateFxPayload.PHASE_START -> {
                Charge c = new Charge(p.entityId(), p.pos(), p.station(), p.totalTicks(), ticks);
                CHARGES.put(p.entityId(), c);
                REMATERIALIZING.remove(p.entityId());
                IN_TRANSIT.remove(p.entityId());
                com.nexusgate.client.render.MatterDissolveRenderer.forget(p.entityId());
                RECEPTIONS.removeIf(r -> r.entityId == p.entityId());
                if (local) {
                    DiveFx.reset();
                    if (listener != null) listener.onChargeStart(c);
                }
            }
            case GateFxPayload.PHASE_CANCEL -> {
                Charge c = CHARGES.remove(p.entityId());
                BURSTS.add(new Burst(BurstKind.CANCEL, c != null ? c.position(0) : p.pos(), p.station(), ticks));
                float was = c != null ? RelocationTimeline.dissolve(c.progress(0)) : 0f;
                com.nexusgate.client.render.MatterDissolveRenderer.forget(p.entityId());
                if (was > 0f) REMATERIALIZING.put(p.entityId(), new Rematerialize(was, ticks));
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
                IN_TRANSIT.put(p.entityId(), ticks);
                com.nexusgate.client.render.MatterDissolveRenderer.forget(p.entityId());
                BURSTS.add(new Burst(BurstKind.DEPART, p.pos(), p.station(), ticks));
                uplinkRelease(p.station());
                if (local) {
                    DiveFx.enterTunnel();
                    if (listener != null) listener.onChargeEnd(true, null);
                    GateSounds.dive();
                }
            }
            case GateFxPayload.PHASE_ARRIVE -> {
                IN_TRANSIT.remove(p.entityId());
                RECEPTIONS.removeIf(r -> r.entityId == p.entityId());
                RECEPTIONS.add(new Reception(p.entityId(), p.station(), p.pos(), ticks));
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
            RECEPTIONS.clear();
            QuantumFx.clear();
            REMATERIALIZING.clear();
            IN_TRANSIT.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        BURSTS.removeIf(b -> ticks - b.startTick > Burst.LENGTH);
        RECEPTIONS.removeIf(r -> ticks - r.startTick > RelocationTimeline.RECEPTION_TICKS);
        REMATERIALIZING.values().removeIf(m -> ticks - m.startTick > RelocationTimeline.REMATERIALIZE_TICKS);
        IN_TRANSIT.values().removeIf(t -> ticks - t > IN_TRANSIT_TICKS);
        Iterator<Charge> it = CHARGES.values().iterator();
        while (it.hasNext()) {
            Charge c = it.next();
            if (ticks - c.startTick > c.total + 60) {
                it.remove();
                continue;
            }
            chargingParticles(c);
        }
        for (Reception r : RECEPTIONS) receptionParticles(r);
        QuantumFx.tick();
    }

    // ---------------------------------------------------------------- geometry

    /** Centre of the plasma core (where the drum sits, straddling both halves). */
    public static Vec3 core(BlockPos station) {
        return new Vec3(station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5);
    }

    /** Top of the crown, where the orbital beam leaves and lands. */
    public static Vec3 crown(BlockPos station) {
        return new Vec3(station.getX() + 0.5, station.getY() + 2.05, station.getZ() + 0.5);
    }

    /**
     * A point of the body as it is drawn right now: a vertex of the deformed geometry when the renderer has
     * sampled one, else a point of the body shape collapsed by the same field.
     */
    private static Vec3 fromBody(int entityId, Vec3 feet, Vec3 focal, float compression) {
        var samples = com.nexusgate.client.render.MatterDissolveRenderer.samples(entityId);
        if (!samples.isEmpty()) return samples.get(RANDOM.nextInt(samples.size()));
        Vec3 p = bodyPoint(feet, RANDOM.nextFloat());
        float[] out = new float[3];
        CollapseField.collapse((float) p.x, (float) p.y, (float) p.z, (float) focal.x, (float) focal.y, (float) focal.z,
                compression, 2.5f, out);
        return new Vec3(out[0], out[1], out[2]);
    }

    /** A random point on the body surface around {@code feet}, at the given fraction of its height. */
    private static Vec3 bodyPoint(Vec3 feet, float height) {
        double a = RANDOM.nextDouble() * Math.PI * 2;
        double r = 0.18 + RANDOM.nextDouble() * 0.14;
        return feet.add(Math.cos(a) * r, Mth.clamp(height, 0f, 1f) * 1.8, Math.sin(a) * r);
    }

    // ---------------------------------------------------------------- particles

    private static void chargingParticles(Charge c) {
        float t = c.progress(0);
        Vec3 at = c.position(0);
        Vec3 chest = at.add(0, 1.0, 0);
        Vec3 core = core(c.station);

        // The core accumulates energy: fluctuations condense into it from the space around it.
        if (t < RelocationTimeline.DISSOLVE_END && RANDOM.nextFloat() < 0.4f + t) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 from = core.add(Math.cos(a) * 0.8, (RANDOM.nextDouble() - 0.5) * 0.9, Math.sin(a) * 0.8);
            QuantumFx.stream(from, core, 0.06f, 0.03f);
        }

        // Phase A: the space around the body starts producing fluctuations that pop in and out.
        float inst = RelocationTimeline.instability(t);
        if (inst > 0f && t < RelocationTimeline.DISSOLVE_END && RANDOM.nextFloat() < inst * 0.8f) {
            QuantumFx.flicker(chest, 0.7);
        }

        // Connection: energy runs down the beam as it reaches the traveller.
        float reach = RelocationTimeline.beamReach(t);
        if (reach > 0f && RelocationTimeline.beamIntensity(t) > 0.2f && RANDOM.nextFloat() < 0.3f) {
            QuantumFx.stream(core, core.lerp(chest, reach), 0.3f, 0.03f);
        }

        // Matter breaks away from the regions being compressed (sampled from the deformed geometry itself),
        // is drawn into the focal point and carried down the beam into the core.
        float d = RelocationTimeline.dissolve(t);
        Vec3 focal = focal(c.entityId, 0);
        if (d > 0.05f && d < 1f) {
            int count = 1 + Math.round(d * 4f) + RANDOM.nextInt(2);
            for (int i = 0; i < count; i++) {
                QuantumFx.absorb(fromBody(c.entityId, at, focal, d), focal, core);
            }
        }
        // Phase D: the point rides the beam into the core, shedding the last fluctuations.
        float travel = RelocationTimeline.focalTravel(t);
        if (travel > 0f && travel < 1f) {
            for (int i = 0; i < 2; i++) QuantumFx.stream(focal, core, 0.25f + RANDOM.nextFloat() * 0.15f, 0.04f);
        }

        // Uplink: converted matter climbs the orbital beam.
        float up = RelocationTimeline.uplink(t);
        if (up > 0f && RANDOM.nextFloat() < up) {
            Vec3 crown = crown(c.station);
            QuantumFx.stream(crown, crown.add(0, 16 + RANDOM.nextInt(10), 0), 0.9f + RANDOM.nextFloat() * 0.5f, 0.06f);
        }
    }

    /** Departure: a last burst of matter shoots up the orbital beam. */
    private static void uplinkRelease(BlockPos station) {
        Vec3 crown = crown(station);
        for (int i = 0; i < 36; i++) {
            QuantumFx.stream(crown, crown.add(0, 18 + RANDOM.nextInt(22), 0), 1.0f + RANDOM.nextFloat() * 0.9f, 0.07f);
        }
        NgParticles.ring(crown.x, crown.y, crown.z, 1.4f);
    }

    private static void receptionParticles(Reception r) {
        float a = r.age(0);
        Vec3 core = core(r.station);
        Vec3 crown = crown(r.station);
        Vec3 feet = r.body(0);
        Vec3 chest = feet.add(0, 1.0, 0);

        // The transmission comes down the sky beam into the crown and the core.
        if (a < 12f) {
            for (int i = 0; i < 3; i++) {
                QuantumFx.stream(crown.add(0, 12 + RANDOM.nextInt(12), 0), core, 1.0f + RANDOM.nextFloat() * 0.5f, 0.06f);
            }
        }
        Vec3 focal = focal(r.entityId, 0);
        // The space at the arrival spot destabilises before any matter appears.
        if (a >= 8f && a < 46f && RANDOM.nextFloat() < 0.6f) QuantumFx.flicker(chest, 0.5);
        // The point comes out of the core along the beam...
        float travel = RelocationTimeline.arrivalTravel(a);
        if (travel > 0f && travel < 1f) {
            for (int i = 0; i < 2; i++) QuantumFx.stream(core, focal, 0.25f + RANDOM.nextFloat() * 0.15f, 0.04f);
        }
        // ...then matter keeps arriving through it and gathers where the body is expanding.
        if (RelocationTimeline.emitting(a)) {
            float c = RelocationTimeline.reconstruct(a);
            int count = 1 + Math.round((1f - c) * 3f) + RANDOM.nextInt(2);
            for (int i = 0; i < count; i++) {
                QuantumFx.emerge(core, focal, fromBody(r.entityId, feet, focal, c));
            }
        }
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
