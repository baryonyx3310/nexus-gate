package com.nexusgate.client.fx;

/**
 * Pure timing of the relocation sequence (no Minecraft types, unit-tested).
 * <p>
 * Charge side, as a fraction {@code t} of the server charge (0..1, from the START event):
 * connection (core reaches the traveller) → dematerialisation (body converted into quantum fluctuations that
 * flow into the core) → uplink (the crown fires the orbital beam). The server teleports at t = 1 (DEPART).
 * <p>
 * Destination side, in ticks since ARRIVE: the beam comes down from the sky, the core is energised, the core
 * reaches the arrival spot and the body is rebuilt from Suspended Matter.
 * <p>
 * Every value here is only used for rendering: it never feeds back into game state.
 */
public final class RelocationTimeline {
    public static final float BEAM_START = 0.10f;
    public static final float CONNECT_END = 0.25f;
    /** The body is compressed to a point by here; then the point travels down the beam into the core. */
    public static final float DISSOLVE_END = 0.86f;
    public static final float TRAVEL_END = 0.97f;
    public static final float UPLINK_START = 0.78f;

    /** Ticks the orbital beam keeps flaring after the departure. */
    public static final int RELEASE_TICKS = 30;
    /** Length of the reception + reconstruction sequence at the destination. */
    public static final int RECEPTION_TICKS = 60;
    /** Ticks to put a body back together after a cancelled charge. */
    public static final int REMATERIALIZE_TICKS = 10;

    private RelocationTimeline() {}

    // ------------------------------------------------------------------ charge (t = 0..1)

    /** True while the "CONECTANDO COM A INTRA" banner belongs on screen. */
    public static boolean connecting(float t) {
        return t >= 0f && t < CONNECT_END;
    }

    /** Banner opacity: short fade in at the start and out just before dematerialisation. */
    public static float bannerAlpha(float t) {
        if (!connecting(t)) return 0f;
        return Math.min(ramp(0f, 0.03f, t), 1f - ramp(CONNECT_END - 0.03f, CONNECT_END, t));
    }

    /** How far the connection beam has travelled from the core toward the traveller (0..1). */
    public static float beamReach(float t) {
        return smooth(BEAM_START, CONNECT_END, t);
    }

    /** Connection beam brightness: on once it leaves the core, fading once the body is gone. */
    public static float beamIntensity(float t) {
        return ramp(BEAM_START, BEAM_START + 0.04f, t) * (1f - 0.85f * smooth(DISSOLVE_END, 1f, t));
    }

    /** Phase A: space around the body destabilises before any matter is converted (0..1, then held). */
    public static float instability(float t) {
        return smooth(0.08f, CONNECT_END, t);
    }

    /**
     * Convergent space-time lens on the focal point: grows with the instability, then with the compression, and
     * follows the point into the core, where it is gone.
     */
    public static float bodyLens(float t) {
        return (0.3f * instability(t) + 0.7f * dissolve(t)) * (1f - smooth(0.93f, 1f, t));
    }

    /** Lens folded into the core while it absorbs the last of the matter and fires the uplink. */
    public static float coreLens(float t) {
        return 0.5f * smooth(0.74f, 0.9f, t);
    }

    /**
     * Body compression (see {@link CollapseField}): 0 = intact, 1 = a single point. It starts gently with the
     * phase-A instability and reaches the point at {@link #DISSOLVE_END}.
     */
    public static float dissolve(float t) {
        return smooth(0.08f, DISSOLVE_END, t);
    }

    /** Phase D: the point travels along the connection beam from the body into the core (0 → 1). */
    public static float focalTravel(float t) {
        return smooth(DISSOLVE_END, TRAVEL_END, t);
    }

    /** Orbital uplink (crown rings and sky beam) brightness during the charge. */
    public static float uplink(float t) {
        return smooth(UPLINK_START, 0.95f, t);
    }

    // ------------------------------------------------------------------ after departure (ticks)

    /** Orbital beam after DEPART: a flare that decays to nothing. */
    public static float release(float ticksSinceDepart) {
        float k = 1f - clamp01(ticksSinceDepart / RELEASE_TICKS);
        return k * k;
    }

    /** Core lens after DEPART: collapses with the release flare. */
    public static float coreLensAfterDepart(float ticksSinceDepart) {
        return 0.5f * release(ticksSinceDepart);
    }

    /** Compression coming back to 0 after a cancel, starting from the value it had. */
    public static float rematerialize(float startDissolve, float ticksSinceCancel) {
        return clamp01(startDissolve) * (1f - clamp01(ticksSinceCancel / REMATERIALIZE_TICKS));
    }

    // ------------------------------------------------------------------ destination (ticks since ARRIVE)

    /** How far the sky beam has come down toward the destination crown (0..1). */
    public static float skyReach(float a) {
        return smooth(0f, 10f, a);
    }

    /** Sky beam brightness: full while it delivers, fading once the body is rebuilt. */
    public static float skyIntensity(float a) {
        return ramp(0f, 2f, a) * (1f - smooth(30f, RECEPTION_TICKS, a));
    }

    /** Destination core energy, fed to the station renderer as a charge progress (0..1..0). */
    public static float receptionCore(float a) {
        return smooth(8f, 18f, a) * (1f - smooth(40f, RECEPTION_TICKS, a));
    }

    /** How far the reconstruction beam has travelled from the core to the arrival spot (0..1). */
    public static float rebuildReach(float a) {
        return smooth(15f, 22f, a);
    }

    /** Reconstruction beam brightness. */
    public static float rebuildIntensity(float a) {
        return ramp(15f, 17f, a) * (1f - smooth(46f, 56f, a));
    }

    /** The point coming out of the core along the beam to the arrival spot (1 = in the core, 0 = arrived). */
    public static float arrivalTravel(float a) {
        return 1f - smooth(12f, 22f, a);
    }

    /** Body compression while being rebuilt: a point until the matter has gathered, then it expands (1 → 0). */
    public static float reconstruct(float a) {
        return 1f - smooth(22f, 52f, a);
    }

    /** Silhouette instability while the rebuilt body settles. */
    public static float arrivalInstability(float a) {
        return 1f - smooth(48f, 58f, a);
    }

    /** Space distorts at the arrival spot before any matter appears, peaks while it is organised, then fades. */
    public static float arrivalLens(float a) {
        return smooth(8f, 16f, a) * (0.55f + 0.45f * reconstruct(a)) * (1f - smooth(42f, 58f, a));
    }

    /** Lens in the destination core while it is energised and emits. */
    public static float receptionCoreLens(float a) {
        return 0.5f * smooth(6f, 12f, a) * (1f - smooth(30f, 45f, a));
    }

    /** True while fluctuations should stream out of the destination core. */
    public static boolean emitting(float a) {
        return a >= 14f && a < 48f;
    }

    // ------------------------------------------------------------------ body encoding

    /**
     * What a body looks like right now: phase-A instability, compression toward the focal point, and how far the
     * focal point has travelled along the beam (0 = on the body, 1 = in the core). All 0..1.
     */
    public record Body(float instability, float conversion, float travel) {
        public static final Body NONE = new Body(0f, 0f, 0f);

        /** True when the body must be drawn through the conversion shader. */
        public boolean active() {
            return instability > 0f || conversion > 0f;
        }
    }

    /** Alpha values below this byte mean "instability only"; above it, conversion. */
    public static final int CONVERSION_BASE = 38;

    /**
     * Packs the body state into the vertex alpha read by the matter dissolve shader:
     * 0..37 = phase-A instability (body intact), 38..255 = conversion 0..1.
     */
    public static int encodeBody(float instability, float conversion) {
        if (conversion > 0f) return CONVERSION_BASE + Math.round(clamp01(conversion) * (255 - CONVERSION_BASE));
        return Math.round(clamp01(instability) * (CONVERSION_BASE - 1));
    }

    // ------------------------------------------------------------------ helpers

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** Linear 0..1 between e0 and e1. */
    static float ramp(float e0, float e1, float x) {
        return clamp01((x - e0) / (e1 - e0));
    }

    /** Smoothstep between e0 and e1. */
    static float smooth(float e0, float e1, float x) {
        float t = ramp(e0, e1, x);
        return t * t * (3f - 2f * t);
    }
}
