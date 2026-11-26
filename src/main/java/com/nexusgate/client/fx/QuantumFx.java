package com.nexusgate.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Quantum fluctuations: fragments of matter and information in an unstable physical state. This is the mod's own
 * system (not the vanilla particle engine), ticked by the FX controller and drawn by the world renderer.
 * <p>
 * Each fragment is a small procedural crystal (three crossed planes) that
 * <ul>
 *   <li>changes shape frame by frame (the shader picks among several pixel-art sigils),</li>
 *   <li>exists in superposition: faint copies flicker at nearby positions and it sometimes tunnels to one,</li>
 *   <li>splits into two halves that drift apart and recombine, and may break for good into smaller fragments,</li>
 *   <li>rides a curved, unstable trajectory (cubic Bezier plus noise), and may orbit its target before it is
 *       absorbed.</li>
 * </ul>
 * The larger ones also carry a tiny space-time lens (see {@link #forEachLens}).
 */
public final class QuantumFx {
    private static final int MAX = 360;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Fragment> FRAGMENTS = new ArrayList<>();
    private static final List<Fragment> PENDING = new ArrayList<>();

    private QuantumFx() {}

    public static final class Fragment {
        final Vec3 p0, p1, p2, p3;
        final int life;
        final float size;
        final float seed;
        final int tint;
        final boolean orbit;
        boolean splittable;
        float splitAt;
        int age;

        Fragment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int life, float size, int tint, boolean orbit, boolean splittable) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.life = Math.max(2, life);
            this.size = size;
            this.tint = tint;
            this.orbit = orbit;
            this.splittable = splittable;
            this.splitAt = 0.3f + RANDOM.nextFloat() * 0.4f;
            this.seed = RANDOM.nextFloat() * 1000f;
        }

        float t(float partial) {
            return Mth.clamp((age + partial) / life, 0f, 1f);
        }

        /** Position on the curve, with instability noise and an orbit around the target near the end. */
        public Vec3 position(float partial) {
            float t = t(partial);
            float u = t * t * (3f - 2f * t);
            Vec3 p = bezier(u);
            float a = age + partial;
            float noise = Mth.sin(Mth.PI * t) * 0.06f;
            double nx = Mth.sin(a * 0.9f + seed) * noise, ny = Mth.sin(a * 1.3f + seed * 1.7f) * noise * 0.6;
            double nz = Mth.cos(a * 1.1f + seed * 0.3f) * noise;
            if (orbit) {
                float k = smooth(0.6f, 0.85f, t) * (1f - smooth(0.88f, 1f, t));
                float ang = a * 0.55f + seed;
                double r = 0.32 * k;
                nx += Mth.cos(ang) * r;
                ny += Mth.sin(ang * 1.7f) * r * 0.4;
                nz += Mth.sin(ang) * r;
            }
            return new Vec3(snap(p.x + nx), snap(p.y + ny), snap(p.z + nz));
        }

        private Vec3 bezier(float u) {
            float v = 1f - u;
            double b0 = v * v * v, b1 = 3 * v * v * u, b2 = 3 * v * u * u, b3 = u * u * u;
            return new Vec3(p0.x * b0 + p1.x * b1 + p2.x * b2 + p3.x * b3,
                    p0.y * b0 + p1.y * b1 + p2.y * b2 + p3.y * b3,
                    p0.z * b0 + p1.z * b1 + p2.z * b2 + p3.z * b3);
        }

        public float size() {
            return size;
        }

        /** Fades in fast, out at the very end (absorbed). */
        float alpha(float partial) {
            float t = t(partial);
            return Math.min(1f, t / 0.08f) * Math.min(1f, (1f - t) / 0.12f);
        }
    }

    // ------------------------------------------------------------------ spawning

    public static boolean full() {
        return FRAGMENTS.size() + PENDING.size() >= MAX;
    }

    private static Fragment add(Fragment f) {
        if (!full()) PENDING.add(f);
        return f;
    }

    private static Vec3 randomUnit(double scale) {
        double a = RANDOM.nextDouble() * Math.PI * 2, y = RANDOM.nextDouble() * 2 - 1, r = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(a) * r * scale, y * scale, Math.sin(a) * r * scale);
    }

    private static int tint() {
        float r = RANDOM.nextFloat();
        return r < 0.55f ? TeleportFxController.CYAN : r < 0.88f ? 0xCFFBFF : 0x9E6BFF;
    }

    private static int lifeFor(Vec3 a, Vec3 b, float speed) {
        return Mth.ceil(a.distanceTo(b) / speed) + 4;
    }

    /** Matter leaving the body: curves toward the beam, then into the core. */
    public static void absorb(Vec3 from, Vec3 onBeam, Vec3 core) {
        Vec3 c1 = from.add(randomUnit(0.45)).add(0, 0.15, 0);
        Vec3 c2 = onBeam.add(randomUnit(0.25));
        float speed = 0.1f + RANDOM.nextFloat() * 0.08f;
        add(new Fragment(from, c1, c2, core, lifeFor(from, core, speed), 0.035f + RANDOM.nextFloat() * 0.05f, tint(),
                RANDOM.nextFloat() < 0.3f, RANDOM.nextFloat() < 0.25f));
    }

    /** Matter leaving the core to rebuild a body: rides the beam, then curves to its place and gathers there. */
    public static void emerge(Vec3 core, Vec3 onBeam, Vec3 to) {
        Vec3 c1 = onBeam.add(randomUnit(0.25));
        Vec3 c2 = to.add(randomUnit(0.5));
        float speed = 0.1f + RANDOM.nextFloat() * 0.08f;
        add(new Fragment(core, c1, c2, to, lifeFor(core, to, speed), 0.035f + RANDOM.nextFloat() * 0.05f, tint(),
                RANDOM.nextFloat() < 0.35f, RANDOM.nextFloat() < 0.15f));
    }

    /** Phase A: short-lived fluctuations popping in and out of existence around a point. */
    public static void flicker(Vec3 around, double radius) {
        Vec3 p = around.add(randomUnit(radius));
        Vec3 q = p.add(randomUnit(0.12));
        add(new Fragment(p, p.add(randomUnit(0.08)), q.add(randomUnit(0.08)), q, 4 + RANDOM.nextInt(8),
                0.025f + RANDOM.nextFloat() * 0.035f, tint(), false, false));
    }

    /** Converted matter streaming along a straight channel (beam pulses, orbital uplink, sky downlink). */
    public static void stream(Vec3 from, Vec3 to, float speed, float size) {
        Vec3 d = to.subtract(from);
        Vec3 c1 = from.add(d.scale(0.33)).add(randomUnit(0.12));
        Vec3 c2 = from.add(d.scale(0.66)).add(randomUnit(0.12));
        add(new Fragment(from, c1, c2, to, lifeFor(from, to, speed), size * (0.7f + RANDOM.nextFloat() * 0.6f), tint(),
                false, false));
    }

    // ------------------------------------------------------------------ ticking

    public static void tick() {
        FRAGMENTS.addAll(PENDING);
        PENDING.clear();
        Iterator<Fragment> it = FRAGMENTS.iterator();
        List<Fragment> born = new ArrayList<>();
        while (it.hasNext()) {
            Fragment f = it.next();
            f.age++;
            if (f.age >= f.life) {
                it.remove();
                continue;
            }
            // Breaks for good into two or three smaller fragments that finish the trip on their own.
            if (f.splittable && f.t(0) >= f.splitAt && f.size > 0.04f) {
                f.splittable = false;
                Vec3 here = f.position(0);
                int remaining = f.life - f.age;
                int n = 2 + RANDOM.nextInt(2);
                for (int i = 0; i < n; i++) {
                    born.add(new Fragment(here, here.add(randomUnit(0.3)), f.p2.add(randomUnit(0.2)), f.p3,
                            remaining + RANDOM.nextInt(4), f.size * 0.55f, f.tint, f.orbit, false));
                }
                it.remove();
            }
        }
        for (Fragment b : born) if (FRAGMENTS.size() < MAX) FRAGMENTS.add(b);
    }

    public static void clear() {
        FRAGMENTS.clear();
        PENDING.clear();
    }

    public static boolean any() {
        return !FRAGMENTS.isEmpty();
    }

    /** The larger fragments bend the light around them too (at most {@code max}). */
    public static void forEachLens(int max, float partial, Consumer<Fragment> sink) {
        int n = 0;
        for (Fragment f : FRAGMENTS) {
            if (f.size < 0.065f) continue;
            sink.accept(f);
            if (++n >= max) return;
        }
    }

    // ------------------------------------------------------------------ drawing

    private static final Vector3f[] AXES = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};

    /** Draws every fragment into the FRAGMENT render type (POSITION_TEX_COLOR, additive). */
    public static void render(VertexConsumer vc, Matrix4f m, float partial) {
        long frame = TeleportFxController.ticks();
        for (Fragment f : FRAGMENTS) {
            float alpha = f.alpha(partial);
            if (alpha <= 0.01f) continue;
            Vec3 pos = f.position(partial);
            float a = f.age + partial;
            int phase = (int) ((frame + (long) f.seed) / 3);

            // Superposition: up to two ghost positions that change every few ticks; sometimes the fragment
            // itself tunnels to one of them.
            Vec3 g1 = ghost(f, phase, 1), g2 = ghost(f, phase, 2);
            boolean tunnel = hash(f.seed, phase, 9) < 0.18f;
            Vec3 main = tunnel ? g1 : pos;
            Vec3 other = tunnel ? pos : g1;

            // Split into two halves that drift apart along an axis and recombine.
            float split = (float) Math.pow(Math.max(0f, Mth.sin(a * 0.28f + f.seed)), 3) * 1.4f;
            Quaternionf spin = new Quaternionf().rotateXYZ(a * 0.21f + f.seed, a * 0.17f + f.seed * 0.5f, a * 0.13f);
            int shapeA = (int) (hash(f.seed, phase, 3) * 5f), shapeB = (int) (hash(f.seed, phase, 4) * 5f);
            int color = argb(alpha * 0.95f, f.tint);

            if (split > 0.05f) {
                Vector3f axis = new Vector3f(AXES[(int) (f.seed) % 3]).rotate(spin).mul(split * f.size);
                crystal(vc, m, main.add(axis.x, axis.y, axis.z), f.size * 0.7f, spin, shapeA, shapeB, color);
                crystal(vc, m, main.add(-axis.x, -axis.y, -axis.z), f.size * 0.7f, spin, shapeB, shapeA, color);
            } else {
                crystal(vc, m, main, f.size, spin, shapeA, shapeB, color);
            }
            float ghostAlpha = alpha * (0.25f + 0.25f * hash(f.seed, phase, 5));
            crystal(vc, m, other, f.size * 0.8f, spin, shapeB, shapeA, argb(ghostAlpha, f.tint));
            if (hash(f.seed, phase, 6) < 0.5f) {
                crystal(vc, m, g2, f.size * 0.6f, spin, shapeA, shapeA, argb(ghostAlpha * 0.6f, 0x9E6BFF));
            }
        }
    }

    private static Vec3 ghost(Fragment f, int phase, int k) {
        Vec3 base = f.position(0);
        double r = 0.12 + 0.18 * hash(f.seed, phase, 10 + k);
        double ang = hash(f.seed, phase, 20 + k) * Math.PI * 2;
        double y = (hash(f.seed, phase, 30 + k) - 0.5) * 0.3;
        return new Vec3(snap(base.x + Math.cos(ang) * r), snap(base.y + y), snap(base.z + Math.sin(ang) * r));
    }

    /** Three crossed planes, each a quad whose UV carries the two shapes the shader alternates between. */
    private static void crystal(VertexConsumer vc, Matrix4f m, Vec3 c, float s, Quaternionf spin, int shapeA, int shapeB,
                                int color) {
        for (int i = 0; i < 3; i++) {
            Vector3f a = new Vector3f(AXES[(i + 1) % 3]).rotate(spin).mul(s);
            Vector3f b = new Vector3f(AXES[(i + 2) % 3]).rotate(spin).mul(s);
            float x = (float) c.x, y = (float) c.y, z = (float) c.z;
            vc.addVertex(m, x - a.x - b.x, y - a.y - b.y, z - a.z - b.z).setUv(shapeA, shapeB).setColor(color);
            vc.addVertex(m, x + a.x - b.x, y + a.y - b.y, z + a.z - b.z).setUv(shapeA + 0.999f, shapeB).setColor(color);
            vc.addVertex(m, x + a.x + b.x, y + a.y + b.y, z + a.z + b.z).setUv(shapeA + 0.999f, shapeB + 0.999f).setColor(color);
            vc.addVertex(m, x - a.x + b.x, y - a.y + b.y, z - a.z + b.z).setUv(shapeA, shapeB + 0.999f).setColor(color);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static float hash(float seed, int phase, int salt) {
        long h = Float.floatToIntBits(seed) * 0x9E3779B97F4A7C15L + phase * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static double snap(double v) {
        return Math.round(v * 32.0) / 32.0;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int argb(float a, int rgb) {
        return (Math.round(Mth.clamp(a, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }
}
