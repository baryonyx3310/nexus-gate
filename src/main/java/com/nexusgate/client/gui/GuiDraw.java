package com.nexusgate.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Vector drawing for the gate interface: anti-aliasing-free but sub-pixel geometry (rings, sectors, discs,
 * thick lines, polygons) with per-vertex colour, so metal can be lit with gradients.
 * Angles are in radians, 0 = 12 o'clock, growing clockwise.
 */
public final class GuiDraw {
    private GuiDraw() {}

    private static BufferBuilder begin(GuiGraphics g, boolean additive) {
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    public static float px(float cx, float a, float r) {
        return cx + Mth.sin(a) * r;
    }

    public static float py(float cy, float a, float r) {
        return cy - Mth.cos(a) * r;
    }

    /** Ring sector between radii r0..r1 and angles a0..a1, colour ci on the inner edge and co on the outer. */
    public static void ring(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int ci, int co, boolean additive) {
        if (r1 <= r0 || a1 <= a0) return;
        int seg = Math.max(2, (int) Math.ceil((a1 - a0) / (Mth.TWO_PI) * Math.max(24, r1 * 1.2f)));
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        for (int i = 0; i < seg; i++) {
            float t0 = a0 + (a1 - a0) * i / seg;
            float t1 = a0 + (a1 - a0) * (i + 1) / seg;
            float ix0 = px(cx, t0, r0), iy0 = py(cy, t0, r0), ox0 = px(cx, t0, r1), oy0 = py(cy, t0, r1);
            float ix1 = px(cx, t1, r0), iy1 = py(cy, t1, r0), ox1 = px(cx, t1, r1), oy1 = py(cy, t1, r1);
            b.addVertex(m, ix0, iy0, 0).setColor(ci);
            b.addVertex(m, ox0, oy0, 0).setColor(co);
            b.addVertex(m, ox1, oy1, 0).setColor(co);
            b.addVertex(m, ix0, iy0, 0).setColor(ci);
            b.addVertex(m, ox1, oy1, 0).setColor(co);
            b.addVertex(m, ix1, iy1, 0).setColor(ci);
        }
        end(b);
    }

    public static void ring(GuiGraphics g, float cx, float cy, float r0, float r1, int color) {
        ring(g, cx, cy, r0, r1, 0, Mth.TWO_PI, color, color, false);
    }

    /**
     * A full ring whose colour follows a directional light: brighter toward {@code lightAngle}.
     * Gives turned metal its bevel without textures.
     */
    public static void litRing(GuiGraphics g, float cx, float cy, float r0, float r1, int dark, int bright, float lightAngle, float sharp) {
        int seg = Math.max(32, (int) (r1 * 1.4f));
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, false);
        for (int i = 0; i < seg; i++) {
            float t0 = Mth.TWO_PI * i / seg;
            float t1 = Mth.TWO_PI * (i + 1) / seg;
            int c0 = lit(dark, bright, t0, lightAngle, sharp);
            int c1 = lit(dark, bright, t1, lightAngle, sharp);
            b.addVertex(m, px(cx, t0, r0), py(cy, t0, r0), 0).setColor(c0);
            b.addVertex(m, px(cx, t0, r1), py(cy, t0, r1), 0).setColor(c0);
            b.addVertex(m, px(cx, t1, r1), py(cy, t1, r1), 0).setColor(c1);
            b.addVertex(m, px(cx, t0, r0), py(cy, t0, r0), 0).setColor(c0);
            b.addVertex(m, px(cx, t1, r1), py(cy, t1, r1), 0).setColor(c1);
            b.addVertex(m, px(cx, t1, r0), py(cy, t1, r0), 0).setColor(c1);
        }
        end(b);
    }

    private static int lit(int dark, int bright, float a, float light, float sharp) {
        float k = 0.5f + 0.5f * Mth.cos(a - light);
        k = (float) Math.pow(k, sharp);
        return lerpColor(dark, bright, k);
    }

    /** Filled disc with a radial gradient (centre colour to edge colour). */
    public static void disc(GuiGraphics g, float cx, float cy, float r, int center, int edge, boolean additive) {
        int seg = Math.max(20, (int) (r * 1.3f));
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        for (int i = 0; i < seg; i++) {
            float t0 = Mth.TWO_PI * i / seg;
            float t1 = Mth.TWO_PI * (i + 1) / seg;
            b.addVertex(m, cx, cy, 0).setColor(center);
            b.addVertex(m, px(cx, t0, r), py(cy, t0, r), 0).setColor(edge);
            b.addVertex(m, px(cx, t1, r), py(cy, t1, r), 0).setColor(edge);
        }
        end(b);
    }

    /** Soft additive glow: bright core fading to transparent. */
    public static void glow(GuiGraphics g, float cx, float cy, float r, int rgb, float alpha) {
        disc(g, cx, cy, r, argb(alpha, rgb), argb(0, rgb), true);
    }

    /** Thick line segment with square caps. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int c0, int c1, boolean additive) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(c0);
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(c0);
        b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(c1);
        b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(c0);
        b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(c1);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(c1);
        end(b);
    }

    /** Convex polygon (fan) from interleaved x,y coordinates, single colour. */
    public static void polygon(GuiGraphics g, float[] xy, int color, boolean additive) {
        if (xy.length < 6) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        for (int i = 2; i + 3 < xy.length; i += 2) {
            b.addVertex(m, xy[0], xy[1], 0).setColor(color);
            b.addVertex(m, xy[i], xy[i + 1], 0).setColor(color);
            b.addVertex(m, xy[i + 2], xy[i + 3], 0).setColor(color);
        }
        end(b);
    }

    /** Quadrilateral with one colour per corner (order: p0..p3 around the shape). */
    public static void quad(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                            int c0, int c1, int c2, int c3, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        b.addVertex(m, x0, y0, 0).setColor(c0);
        b.addVertex(m, x1, y1, 0).setColor(c1);
        b.addVertex(m, x2, y2, 0).setColor(c2);
        b.addVertex(m, x0, y0, 0).setColor(c0);
        b.addVertex(m, x2, y2, 0).setColor(c2);
        b.addVertex(m, x3, y3, 0).setColor(c3);
        end(b);
    }

    /** Axis-aligned rectangle with a vertical gradient. */
    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int top, int bottom) {
        quad(g, x0, y0, x1, y0, x1, y1, x0, y1, top, top, bottom, bottom, false);
    }

    /** Chamfered panel (cut corners) with a vertical gradient and a 1px rim. */
    public static void chamferPanel(GuiGraphics g, float x0, float y0, float x1, float y1, float cut, int top, int bottom, int rim) {
        float[] xy = {x0 + cut, y0, x1 - cut, y0, x1, y0 + cut, x1, y1 - cut, x1 - cut, y1, x0 + cut, y1, x0, y1 - cut, x0, y0 + cut};
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, false);
        float h = y1 - y0;
        for (int i = 2; i + 3 < xy.length; i += 2) {
            b.addVertex(m, xy[0], xy[1], 0).setColor(lerpColor(top, bottom, (xy[1] - y0) / h));
            b.addVertex(m, xy[i], xy[i + 1], 0).setColor(lerpColor(top, bottom, (xy[i + 1] - y0) / h));
            b.addVertex(m, xy[i + 2], xy[i + 3], 0).setColor(lerpColor(top, bottom, (xy[i + 3] - y0) / h));
        }
        end(b);
        for (int i = 0; i < xy.length; i += 2) {
            int j = (i + 2) % xy.length;
            line(g, xy[i], xy[i + 1], xy[j], xy[j + 1], 1f, rim, rim, false);
        }
    }

    // ================================================================== pixel-art rasterisation
    //
    // The shapes below are rasterised on a grid of square cells ("pixels") of size {@link #unit()} GUI
    // pixels, so circles become stepped Minecraft circles and gradients become a few flat tones. The grid
    // is anchored at the origin of the current pose, so zooming the pose scales the pixels with it.

    private static float unit = 1f;

    /** Sets the pixel size (GUI pixels) used by the pixel-art methods. Reset to 1 when done. */
    public static void setUnit(float u) {
        unit = Math.max(1f, u);
    }

    public static float unit() {
        return unit;
    }

    @FunctionalInterface
    private interface CellShader {
        /** ARGB colour of the cell centred at (x, y), or 0 to leave it empty. */
        int at(float x, float y);
    }

    /** Fills every grid cell of size {@code u} in the box whose shader returns a colour, in one batch. */
    private static void cells(GuiGraphics g, float x0, float y0, float x1, float y1, float u, float ox, float oy,
                              boolean additive, CellShader shader) {
        int i0 = (int) Math.floor((x0 - ox) / u), i1 = (int) Math.ceil((x1 - ox) / u);
        int j0 = (int) Math.floor((y0 - oy) / u), j1 = (int) Math.ceil((y1 - oy) / u);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = null;
        for (int j = j0; j < j1; j++) {
            float py0 = oy + j * u, py1 = py0 + u;
            for (int i = i0; i < i1; i++) {
                float px0 = ox + i * u, px1 = px0 + u;
                int c = shader.at(px0 + u * 0.5f, py0 + u * 0.5f);
                if ((c >>> 24) == 0) continue;
                if (b == null) b = begin(g, additive);
                b.addVertex(m, px0, py0, 0).setColor(c);
                b.addVertex(m, px0, py1, 0).setColor(c);
                b.addVertex(m, px1, py1, 0).setColor(c);
                b.addVertex(m, px0, py0, 0).setColor(c);
                b.addVertex(m, px1, py1, 0).setColor(c);
                b.addVertex(m, px1, py0, 0).setColor(c);
            }
        }
        if (b != null) end(b);
    }

    /** Clockwise-from-top angle of (dx, dy), in [0, 2pi). */
    private static float angleOf(float dx, float dy) {
        float a = (float) Math.atan2(dx, -dy);
        return a < 0 ? a + Mth.TWO_PI : a;
    }

    private static boolean inArc(float a, float a0, float a1) {
        if (a1 - a0 >= Mth.TWO_PI - 1.0e-4f) return true;
        float rel = a - a0;
        rel -= Mth.TWO_PI * (float) Math.floor(rel / Mth.TWO_PI);
        return rel <= a1 - a0;
    }

    /** Posterises t (0..1) into {@code n} flat levels. */
    public static float levels(float t, int n) {
        return Mth.clamp((float) Math.floor(Mth.clamp(t, 0f, 0.9999f) * n) / (n - 1), 0f, 1f);
    }

    /** Pixel ring sector: same arguments as {@link #ring}, colours stepped from inner to outer edge. */
    public static void pixelRing(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int ci, int co, boolean additive) {
        if (r1 <= r0 || a1 <= a0) return;
        float u = unit;
        if (r1 - r0 < u) {
            float mid = (r0 + r1) * 0.5f;
            r0 = mid - u * 0.5f;
            r1 = mid + u * 0.5f;
        }
        final float rr0 = r0, rr1 = r1;
        cells(g, cx - rr1, cy - rr1, cx + rr1, cy + rr1, u, 0, 0, additive, (x, y) -> {
            float dx = x - cx, dy = y - cy;
            float d = Mth.sqrt(dx * dx + dy * dy);
            if (d < rr0 || d >= rr1 || !inArc(angleOf(dx, dy), a0, a1)) return 0;
            return ci == co ? ci : lerpColor(ci, co, levels((d - rr0) / (rr1 - rr0), 3));
        });
    }

    public static void pixelRing(GuiGraphics g, float cx, float cy, float r0, float r1, int color) {
        pixelRing(g, cx, cy, r0, r1, 0, Mth.TWO_PI, color, color, false);
    }

    /**
     * Pixel version of {@link #litRing}: directional light posterised into 4 tones, plus a 1-pixel lit rim on
     * the outer edge facing the light and a dark rim on the opposite side (block bevel).
     */
    public static void pixelLitRing(GuiGraphics g, float cx, float cy, float r0, float r1, int dark, int bright, float lightAngle, float sharp) {
        float u = unit;
        cells(g, cx - r1, cy - r1, cx + r1, cy + r1, u, 0, 0, false, (x, y) -> {
            float dx = x - cx, dy = y - cy;
            float d = Mth.sqrt(dx * dx + dy * dy);
            if (d < r0 || d >= r1) return 0;
            float a = angleOf(dx, dy);
            float k = (float) Math.pow(0.5f + 0.5f * Mth.cos(a - lightAngle), sharp);
            float t = levels(k, 4);
            if (d >= r1 - u) t = Math.min(1f, t + (k > 0.5f ? 0.34f : -0.34f));
            return lerpColor(dark, bright, Math.max(0f, t));
        });
    }

    /** Pixel disc with a stepped radial gradient (3 bands, centre colour to edge colour). */
    public static void pixelDisc(GuiGraphics g, float cx, float cy, float r, int center, int edge, boolean additive) {
        cells(g, cx - r, cy - r, cx + r, cy + r, unit, 0, 0, additive, (x, y) -> {
            float dx = x - cx, dy = y - cy;
            float d = Mth.sqrt(dx * dx + dy * dy);
            if (d >= r) return 0;
            return center == edge ? center : lerpColor(center, edge, levels(d / r, 3));
        });
    }

    /**
     * Emissive block glow: three stepped octagonal halos with decreasing alpha (replaces the soft radial
     * gradient of {@link #glow}).
     */
    public static void blockGlow(GuiGraphics g, float cx, float cy, float r, int rgb, float alpha) {
        if (alpha <= 0.01f || r <= 0f) return;
        int c0 = argb(alpha, rgb), c1 = argb(alpha * 0.55f, rgb), c2 = argb(alpha * 0.22f, rgb);
        cells(g, cx - r, cy - r, cx + r, cy + r, unit, 0, 0, true, (x, y) -> {
            float ax = Math.abs(x - cx), ay = Math.abs(y - cy);
            float d = Math.max(Math.max(ax, ay), (ax + ay) * 0.7071f);
            if (d >= r) return 0;
            return d < r * 0.38f ? c0 : d < r * 0.7f ? c1 : c2;
        });
    }

    /** Thick line drawn as grid cells (stepped like pixel art), colour stepped from c0 to c1. */
    public static void pixelLine(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int c0, int c1, boolean additive) {
        pixelLine(g, x0, y0, x1, y1, width, c0, c1, additive, unit, 0, 0);
    }

    /** As {@link #pixelLine} with an explicit cell size and grid origin. */
    public static void pixelLine(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int c0, int c1,
                                 boolean additive, float u, float ox, float oy) {
        float dx = x1 - x0, dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1.0e-6f) return;
        float half = Math.max(width, u) * 0.5f;
        float pad = half + u;
        cells(g, Math.min(x0, x1) - pad, Math.min(y0, y1) - pad, Math.max(x0, x1) + pad, Math.max(y0, y1) + pad, u, ox, oy, additive,
                (x, y) -> {
                    float t = Mth.clamp(((x - x0) * dx + (y - y0) * dy) / len2, 0f, 1f);
                    float px = x0 + dx * t - x, py = y0 + dy * t - y;
                    // Chebyshev distance keeps the stroke one full cell thick on diagonals.
                    if (Math.max(Math.abs(px), Math.abs(py)) > half) return 0;
                    return c0 == c1 ? c0 : lerpColor(c0, c1, levels(t, 3));
                });
    }

    /** Convex polygon rasterised on the grid, single colour. */
    public static void pixelPolygon(GuiGraphics g, float[] xy, int color, boolean additive) {
        pixelPolygon(g, xy, additive, (x, y) -> color);
    }

    /**
     * Convex quad rasterised on the grid, coloured by distance from (cx, cy): stepped from {@code inner}
     * (at rInner) to {@code outer} (at rOuter). Used by the iris plates.
     */
    public static void pixelQuad(GuiGraphics g, float[] xy, float cx, float cy, float rInner, float rOuter, int inner, int outer) {
        pixelPolygon(g, xy, false, (x, y) -> {
            float d = Mth.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
            return lerpColor(inner, outer, levels((d - rInner) / Math.max(1e-3f, rOuter - rInner), 3));
        });
    }

    private static void pixelPolygon(GuiGraphics g, float[] xy, boolean additive, CellShader color) {
        if (xy.length < 6) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float area = 0;
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xy[2 * i]);
            maxX = Math.max(maxX, xy[2 * i]);
            minY = Math.min(minY, xy[2 * i + 1]);
            maxY = Math.max(maxY, xy[2 * i + 1]);
            int j = (i + 1) % n;
            area += xy[2 * i] * xy[2 * j + 1] - xy[2 * j] * xy[2 * i + 1];
        }
        final float sign = Math.signum(area);
        cells(g, minX, minY, maxX, maxY, unit, 0, 0, additive, (x, y) -> {
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ex = xy[2 * j] - xy[2 * i], ey = xy[2 * j + 1] - xy[2 * i + 1];
                float cross = ex * (y - xy[2 * i + 1]) - ey * (x - xy[2 * i]);
                if (cross * sign < 0) return 0;
            }
            return color.at(x, y);
        });
    }

    /**
     * Panel with stepped (staircase) corners, a vertical gradient in 4 flat bands, a 1px rim and a vanilla
     * style inner bevel (light top/left, dark bottom/right). Replaces {@link #chamferPanel}.
     */
    public static void steppedPanel(GuiGraphics g, float x0, float y0, float x1, float y1, float cut, int top, int bottom, int rim) {
        x0 = Math.round(x0);
        y0 = Math.round(y0);
        x1 = Math.round(x1);
        y1 = Math.round(y1);
        int stepsN = Math.max(1, Math.round(cut / 2f));
        float s = 2f;
        // Rim: the full stepped silhouette; body: the same silhouette inset by 1px.
        steppedFill(g, x0, y0, x1, y1, stepsN, s, rim, rim);
        steppedFill(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, stepsN, s, top, bottom);
        float inset = stepsN * s;
        int hi = lerpColor(top, 0xFFFFFFFF, 0.12f), lo = lerpColor(bottom, 0xFF000000, 0.35f);
        rect(g, x0 + inset, y0 + 1, x1 - inset, y0 + 2, hi, hi);
        rect(g, x0 + 1, y0 + inset, x0 + 2, y1 - inset, hi, hi);
        rect(g, x0 + inset, y1 - 2, x1 - inset, y1 - 1, lo, lo);
        rect(g, x1 - 2, y0 + inset, x1 - 1, y1 - inset, lo, lo);
    }

    private static void steppedFill(GuiGraphics g, float x0, float y0, float x1, float y1, int n, float s, int top, int bottom) {
        float h = y1 - y0;
        if (h <= 0 || x1 <= x0) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, false);
        // Horizontal bands: staircase rows at the top and bottom, full width in between; colour in 4 tones.
        n = Math.max(0, Math.min(n, (int) (h / (2 * s))));
        float yy = y0;
        while (yy < y1 - 1.0e-3f) {
            float ft = yy - y0, fb = y1 - yy;
            float inset, next;
            if (ft < n * s) {
                int r = (int) (ft / s);
                inset = (n - r) * s;
                next = y0 + (r + 1) * s;
            } else if (fb <= n * s + 1.0e-3f) {
                int r = (int) Math.floor((fb - 1.0e-3f) / s);
                inset = (n - r) * s;
                next = y1 - r * s;
            } else {
                inset = 0;
                next = y1 - n * s;
            }
            // Colour bands change every quarter of the height.
            int band = Mth.clamp((int) Math.floor(ft / h * 4f), 0, 3);
            float bandEnd = y0 + h * (band + 1) / 4f;
            if (bandEnd > yy + 1.0e-3f) next = Math.min(next, bandEnd);
            int c = lerpColor(top, bottom, band / 3f);
            float ax0 = x0 + inset, ax1 = x1 - inset;
            if (ax1 > ax0) {
                b.addVertex(m, ax0, yy, 0).setColor(c);
                b.addVertex(m, ax0, next, 0).setColor(c);
                b.addVertex(m, ax1, next, 0).setColor(c);
                b.addVertex(m, ax0, yy, 0).setColor(c);
                b.addVertex(m, ax1, next, 0).setColor(c);
                b.addVertex(m, ax1, yy, 0).setColor(c);
            }
            yy = next;
        }
        end(b);
    }

    public static int argb(float a, int rgb) {
        int ai = Math.round(Mth.clamp(a, 0f, 1f) * 255f);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    /** Multiplies the alpha of an ARGB colour. */
    public static int fade(int argb, float k) {
        int a = (argb >>> 24) & 255;
        return (Math.round(a * Mth.clamp(k, 0f, 1f)) << 24) | (argb & 0xFFFFFF);
    }

    public static int lerpColor(int c0, int c1, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) Mth.lerp(t, (c0 >>> 24) & 255, (c1 >>> 24) & 255);
        int r = (int) Mth.lerp(t, (c0 >> 16) & 255, (c1 >> 16) & 255);
        int gg = (int) Mth.lerp(t, (c0 >> 8) & 255, (c1 >> 8) & 255);
        int bb = (int) Mth.lerp(t, c0 & 255, c1 & 255);
        return (a << 24) | (r << 16) | (gg << 8) | bb;
    }
}
