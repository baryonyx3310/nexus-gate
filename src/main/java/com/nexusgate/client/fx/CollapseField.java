package com.nexusgate.client.fx;

/**
 * Convergent collapse of a body toward a focal point (pure math, unit-tested). Applied to every vertex of the
 * player model while it is converted, so the geometry itself is compressed: it never fades or pops out.
 * <p>
 * With compression {@code c} going 0 → 1:
 * <ul>
 *   <li>A (≈0–0.25): the silhouette loses width; extremities slide toward the body axis first.</li>
 *   <li>B (≈0.25–0.6): the body narrows into a funnel and is pulled vertically toward the focal height, with a
 *       spiral twist that grows with distance from the focus.</li>
 *   <li>C (≈0.6–1): everything converges on the focal point; at c = 1 every vertex sits on it.</li>
 * </ul>
 * Farther parts are always pulled harder than nearer ones, so heads, hands and feet lead the collapse.
 * Coordinates are in blocks, relative to any common origin.
 */
public final class CollapseField {
    private CollapseField() {}

    /**
     * Writes the collapsed position of {@code p} into {@code out} (length 3).
     *
     * @param f     focal point
     * @param c     compression, 0 (intact) .. 1 (a single point)
     * @param twist spiral direction and strength (radians per block at full compression), may be 0
     */
    public static void collapse(float px, float py, float pz, float fx, float fy, float fz, float c, float twist,
                                float[] out) {
        c = clamp01(c);
        float hx = px - fx, hy = py - fy, hz = pz - fz;
        if (c <= 0f) {
            out[0] = px;
            out[1] = py;
            out[2] = pz;
            return;
        }
        float dist = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);

        // Width: down to 15 % of the original, extremities (far from the focus) faster.
        float width = 1f - 0.85f * smooth(0f, 0.6f, c);
        // Height: pulled toward the focal height in phase B.
        float height = 1f - 0.9f * smooth(0.2f, 0.85f, c);
        // Global convergence onto the point in phase C.
        float converge = 1f - smooth(0.55f, 1f, c);
        float lead = 1f + 0.8f * dist;
        float sxz = (float) Math.pow(width, lead) * converge;
        float sy = (float) Math.pow(height, lead) * converge;

        // Funnel twist around the vertical axis through the focus.
        float ang = twist * c * c * dist;
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        float rx = hx * cos - hz * sin, rz = hx * sin + hz * cos;

        out[0] = fx + rx * sxz;
        out[1] = fy + hy * sy;
        out[2] = fz + rz * sxz;
    }

    /** Fraction of its original size a part of the body keeps (for effects that scale with the collapse). */
    public static float remainingSize(float c) {
        c = clamp01(c);
        return (1f - 0.85f * smooth(0f, 0.6f, c)) * (1f - smooth(0.55f, 1f, c));
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    static float smooth(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
