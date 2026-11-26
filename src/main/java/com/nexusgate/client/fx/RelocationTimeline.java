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
    public static final float DISSOLVE_END = 0.80f;
    public static final float UPLINK_START = 0.78f;

    /** Ticks the orbital beam keeps flaring after the departure. */
    public static final int RELEASE_TICKS = 30;
    /** Length of the reception + reconstruction sequence at the destination. */
    public static final int RECEPTION_TICKS = 50;
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

    /** Body dematerialisation: 0 = intact, 1 = fully converted. */
    public static float dissolve(float t) {
        return ramp(CONNECT_END, DISSOLVE_END, t);
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

    /** Body opacity coming back after a cancel, starting from the dissolve it had. */
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
        return ramp(15f, 17f, a) * (1f - smooth(44f, RECEPTION_TICKS, a));
    }

    /** Body dissolve while being rebuilt: hidden until the beam lands, then 1 → 0. */
    public static float reconstruct(float a) {
        return 1f - ramp(20f, 48f, a);
    }

    /** True while fluctuations should stream out of the destination core. */
    public static boolean emitting(float a) {
        return a >= 15f && a < 46f;
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
