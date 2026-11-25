package com.nexusgate.client.fx;

import com.nexusgate.client.gui.GuiDraw;
import com.nexusgate.client.render.ModShaders;
import com.nexusgate.client.render.ShaderQuads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;

/**
 * The first-person "dive into the core" sequence, shared by the gate screen and the HUD so the hand-off
 * between them is seamless:
 * <ol>
 *   <li>dive (last second of the charge): the core shell splits into petals, a tunnel shows through the
 *       aperture and the view zooms into it until the tunnel fills the screen;</li>
 *   <li>tunnel: the server moves the player while the screen is inside the wormhole;</li>
 *   <li>exit: the tunnel slows and an aperture opens in its centre, revealing the destination.</li>
 * </ol>
 */
public final class DiveFx {
    /** Sphere radius inside the orb quad (must match R in core_orb.fsh). */
    public static final float ORB_R = 0.62f;
    private static final float MIN_TUNNEL_MS = 480f;
    private static final float EXIT_MS = 950f;
    private static final float ABORT_MS = 450f;
    private static final long FAILSAFE_NS = 5_000_000_000L;

    public enum Phase { NONE, TUNNEL, EXIT, ABORT }

    private static Phase phase = Phase.NONE;
    private static float phaseMs;
    private static boolean arrived;
    private static float abortFrom;
    private static long phaseStartNs;
    private static long lastFrameNs;

    private DiveFx() {}

    public static Phase phase() {
        return phase;
    }

    static void reset() {
        phase = Phase.NONE;
    }

    static void abort(float diveAtCancel) {
        if (diveAtCancel > 0.01f) {
            set(Phase.ABORT);
            abortFrom = diveAtCancel;
        } else {
            reset();
        }
    }

    static void enterTunnel() {
        set(Phase.TUNNEL);
        arrived = false;
    }

    static void arrived() {
        arrived = true;
    }

    private static void set(Phase p) {
        phase = p;
        phaseMs = 0;
        phaseStartNs = System.nanoTime();
    }

    /** Real frame delta in ms, clamped so loading hitches do not skip the animation. */
    private static float frameMs() {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 16f : (now - lastFrameNs) / 1_000_000f;
        lastFrameNs = now;
        return Mth.clamp(dt, 0f, 50f);
    }

    // ------------------------------------------------------------------ easing

    public static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Petal opening for a dive amount. */
    public static float openFor(float dive) {
        return smooth(0.0f, 0.55f, dive);
    }

    /** Exponential zoom into the core: 1x .. 60x. */
    public static float zoomFor(float dive) {
        return (float) Math.exp(dive * dive * Math.log(60.0));
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the core at (cx,cy) with sphere radius {@code orbPx}, opened and zoomed by {@code dive}.
     * The tunnel is drawn full-screen and masked to the opened aperture, so once the aperture covers the
     * screen it simply becomes the full-screen tunnel (no pop).
     *
     * @return true once the tunnel covers the whole screen
     */
    public static boolean drawCore(GuiGraphics g, int w, int h, float cx, float cy, float orbPx, float dive,
                                   float intensity, int tint) {
        float open = openFor(dive);
        float zoom = zoomFor(dive);
        float focus = smooth(0.15f, 0.9f, dive);
        float x = Mth.lerp(focus, cx, w * 0.5f);
        float y = Mth.lerp(focus, cy, h * 0.5f);
        float r = orbPx * zoom;
        float hole = r * (0.15f + 0.85f * open) * open;
        float halfDiag = Mth.sqrt(w * w + h * h) * 0.5f + Math.abs(x - w * 0.5f) + Math.abs(y - h * 0.5f);
        boolean covered = hole >= halfDiag;

        if (open > 0.001f) {
            tunnel(g, w, h, x, y, covered ? 0f : hole, 0.3f + 0.7f * dive, 0f, tint, 1f);
        }
        if (!covered && open < 0.995f) {
            ShaderInstance orb = ModShaders.orb();
            ModShaders.set(orb, "Open", open);
            ShaderQuads.square(g, orb, ShaderQuads.Blend.ALPHA, x, y, r / ORB_R, ShaderQuads.argb(intensity, tint));
            ModShaders.set(orb, "Open", 0f);
        }
        if (dive > 0.6f) {
            // Speed flash right before crossing in.
            float k = smooth(0.6f, 1f, dive);
            g.fill(0, 0, w, h, GuiDraw.argb(k * 0.25f, 0xE8FFFF));
        }
        return covered;
    }

    /** Full-screen wormhole. {@code maskPx} 0 = no mask; {@code exit} opens the far side. */
    public static void tunnel(GuiGraphics g, int w, int h, float cx, float cy, float maskPx, float depth, float exit,
                              int tint, float alpha) {
        ShaderInstance s = ModShaders.wormhole();
        float half = h * 0.5f;
        ModShaders.set(s, "Aspect", (float) w / h);
        ModShaders.set(s, "Radius", maskPx <= 0 ? 0f : maskPx / half);
        ModShaders.set(s, "CenterX", (cx - w * 0.5f) / half);
        ModShaders.set(s, "CenterY", (cy - h * 0.5f) / half);
        ModShaders.set(s, "Depth", depth);
        ModShaders.set(s, "Exit", exit);
        ShaderQuads.quad(g, s, ShaderQuads.Blend.ALPHA, 0, 0, w, h, ShaderQuads.argb(alpha, tint));
        ShaderQuads.resetUniforms();
    }

    /**
     * HUD part. {@code screenDive} is the dive amount when the gate screen is NOT drawing it itself
     * (gate screen closed while charging), otherwise negative.
     */
    public static void renderHud(GuiGraphics g, int w, int h, float screenDive) {
        float dt = frameMs();
        if (phase != Phase.NONE && System.nanoTime() - phaseStartNs > FAILSAFE_NS) reset();
        switch (phase) {
            case NONE -> {
                if (screenDive > 0f) {
                    float appear = smooth(0f, 0.3f, screenDive);
                    drawCore(g, w, h, w * 0.5f, h * 0.5f, h * 0.07f * appear, screenDive, 0.9f, TeleportFxController.CYAN);
                }
            }
            case TUNNEL -> {
                phaseMs += dt;
                tunnel(g, w, h, w * 0.5f, h * 0.5f, 0, 1f, 0f, TeleportFxController.CYAN, 1f);
                if (arrived && phaseMs >= MIN_TUNNEL_MS) {
                    set(Phase.EXIT);
                    GateSounds.emerge();
                }
            }
            case EXIT -> {
                phaseMs += dt;
                float e = Mth.clamp(phaseMs / EXIT_MS, 0f, 1f);
                float open = easeOutCubic(e);
                tunnel(g, w, h, w * 0.5f, h * 0.5f, 0, 1f - e, open, TeleportFxController.CYAN, 1f - smooth(0.85f, 1f, e));
                // Residual flash and scanlines as the world re-forms.
                float flash = (1f - e) * (1f - e);
                g.fill(0, 0, w, h, GuiDraw.argb(flash * 0.35f, 0xCFFBFF));
                int lines = GuiDraw.argb(0.12f * (1f - e), 0x33F0FF);
                for (int yy = (int) (e * 6) % 3; yy < h; yy += 3) g.fill(0, yy, w, yy + 1, lines);
                if (e >= 1f) reset();
            }
            case ABORT -> {
                phaseMs += dt;
                float a = Mth.clamp(phaseMs / ABORT_MS, 0f, 1f);
                float dive = abortFrom * (1f - easeOutCubic(a));
                drawCore(g, w, h, w * 0.5f, h * 0.5f, h * 0.07f, dive, 0.9f, TeleportFxController.RED);
                g.fill(0, 0, w, h, GuiDraw.argb(0.3f * (1f - a), 0xFF2030));
                if (a >= 1f) reset();
            }
        }
    }
}
