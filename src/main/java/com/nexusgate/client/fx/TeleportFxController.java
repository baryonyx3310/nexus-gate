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
        return !CHARGES.isEmpty() || !BURSTS.isEmpty() || !RECEPTIONS.isEmpty();
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
     * How dematerialised a body is right now: 0 = intact, 1 = fully converted. Used only to draw the body
     * (see {@code MatterDissolveRenderer}); the entity itself is never touched.
     */
    public static float dissolve(int entityId, float partial) {
        Charge c = CHARGES.get(entityId);
        if (c != null) return RelocationTimeline.dissolve(c.progress(partial));
        for (Reception r : RECEPTIONS) {
            if (r.entityId == entityId) return RelocationTimeline.reconstruct(r.age(partial));
        }
        Long departed = IN_TRANSIT.get(entityId);
        if (departed != null && ticks - departed < IN_TRANSIT_TICKS) return 1f;
        Rematerialize m = REMATERIALIZING.get(entityId);
        if (m != null) return RelocationTimeline.rematerialize(m.from, ticks - m.startTick + partial);
        return 0f;
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
            REMATERIALIZING.clear();
            IN_TRANSIT.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        BURSTS.removeIf(b -> ticks - b.startTick > Burst.LENGTH);
        RECEPTIONS.removeIf(r -> ticks - r.startTick > RelocationTimeline.RECEPTION_TICKS + 5);
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

    /** Point of the segment a..b closest to p. */
    private static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return a;
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return a.add(ab.scale(t));
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

        // 1. The core accumulates energy: motes drawn into it.
        if (t < RelocationTimeline.DISSOLVE_END && RANDOM.nextFloat() < 0.5f + t) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 from = core.add(Math.cos(a) * 0.7, (RANDOM.nextDouble() - 0.5) * 0.8, Math.sin(a) * 0.7);
            var m = NgParticles.moteTo(from, core, 0.02f, 0.02f);
            if (m != null) m.life(20);
        }

        // 2. Connection: energy pulses run along the beam as it reaches the traveller.
        float reach = RelocationTimeline.beamReach(t);
        if (reach > 0f && RelocationTimeline.beamIntensity(t) > 0.2f && RANDOM.nextFloat() < 0.35f) {
            Vec3 tip = core.lerp(chest, reach);
            var q = NgParticles.quantum(0.16f, 0f, core, tip);
            if (q != null) q.colors(0xFFFFFF, CYAN);
        }

        // 3. Dematerialisation: pixels leave the body at the dissolve front, join the beam, flow into the core.
        float d = RelocationTimeline.dissolve(t);
        if (d > 0f && d < 1f) {
            int count = 2 + RANDOM.nextInt(3);
            for (int i = 0; i < count; i++) {
                float front = d * 1.09f - 0.09f;
                float h = 1f - (front - 0.4f * RANDOM.nextFloat()) / 0.6f;
                Vec3 from = bodyPoint(at, h);
                Vec3 onBeam = closestOnSegment(core, chest, from);
                NgParticles.quantum(0.14f + RANDOM.nextFloat() * 0.1f, 0.03f, from, onBeam, core);
            }
        } else if (d >= 1f && t < 0.9f && RANDOM.nextFloat() < 0.5f) {
            // The last fluctuations are absorbed.
            NgParticles.quantum(0.2f, 0.02f, chest.add((RANDOM.nextDouble() - 0.5) * 0.3, 0, (RANDOM.nextDouble() - 0.5) * 0.3), core);
        }

        // 4. Uplink: packets of converted matter climb the orbital beam.
        float up = RelocationTimeline.uplink(t);
        if (up > 0f && RANDOM.nextFloat() < up) {
            Vec3 crown = crown(c.station);
            NgParticles.quantum(0.6f + RANDOM.nextFloat() * 0.4f, 0.04f, core, crown, crown.add(0, 24, 0));
        }
    }

    /** Departure: a last burst of packets shoots up the orbital beam. */
    private static void uplinkRelease(BlockPos station) {
        Vec3 core = core(station);
        Vec3 crown = crown(station);
        for (int i = 0; i < 40; i++) {
            float speed = 0.8f + RANDOM.nextFloat() * 0.8f;
            var q = NgParticles.quantum(speed, 0.05f, core, crown, crown.add(0, 20 + RANDOM.nextInt(20), 0));
            if (q != null) q.colors(0xFFFFFF, 0xCFFBFF);
        }
        NgParticles.ring(crown.x, crown.y, crown.z, 1.4f);
    }

    private static void receptionParticles(Reception r) {
        float a = r.age(0);
        Vec3 core = core(r.station);
        Vec3 crown = crown(r.station);

        // 1. The transmission comes down the sky beam into the crown.
        if (a < 12f) {
            for (int i = 0; i < 3; i++) {
                NgParticles.quantum(0.9f + RANDOM.nextFloat() * 0.5f, 0.04f, crown.add(0, 12 + RANDOM.nextInt(12), 0), crown, core);
            }
        }
        // 2. The core is energised.
        if (a >= 8f && a < 20f && RANDOM.nextFloat() < 0.7f) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            var m = NgParticles.moteTo(core.add(Math.cos(ang) * 0.7, (RANDOM.nextDouble() - 0.5) * 0.8, Math.sin(ang) * 0.7), core, 0.02f, 0.02f);
            if (m != null) m.life(20);
        }
        // 3. Fluctuations leave the core, ride the beam and rebuild the body bottom-up.
        if (RelocationTimeline.emitting(a)) {
            Vec3 feet = r.body(0);
            Vec3 chest = feet.add(0, 1.0, 0);
            float built = 1f - RelocationTimeline.reconstruct(a);
            int count = 2 + RANDOM.nextInt(3);
            for (int i = 0; i < count; i++) {
                Vec3 to = bodyPoint(feet, Math.min(1f, built + 0.15f) * RANDOM.nextFloat());
                Vec3 onBeam = closestOnSegment(core, chest, to);
                NgParticles.quantum(0.14f + RANDOM.nextFloat() * 0.1f, 0.03f, core, onBeam, to);
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
