package com.nexusgate.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a station's sigil deterministically from its id: a few strokes between the 7 nodes of a hexagon
 * (centre + 6 vertices), an outer notch pattern and a dot code. Same id, same sigil, on every client.
 */
public final class GlyphFactory {
    /** Node coordinates in a unit circle (index 0 = centre, 1..6 = vertices from the top, clockwise). */
    public static final float[][] NODES = new float[7][2];

    static {
        NODES[0] = new float[]{0f, 0f};
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            NODES[i + 1] = new float[]{(float) Math.sin(a), (float) -Math.cos(a)};
        }
    }

    /** All 15 candidate strokes: 6 spokes, 6 rim edges, 3 long diagonals. */
    private static final int[][] EDGES = buildEdges();

    /**
     * @param strokes pairs of node indices
     * @param notches bitmask of the 6 rim notches drawn on the chamber ring
     * @param dots    number of code dots (1..3) under the sigil
     */
    public record Glyph(List<int[]> strokes, int notches, int dots) {}

    private GlyphFactory() {}

    private static int[][] buildEdges() {
        List<int[]> edges = new ArrayList<>();
        for (int i = 1; i <= 6; i++) edges.add(new int[]{0, i});
        for (int i = 1; i <= 6; i++) edges.add(new int[]{i, i % 6 + 1});
        for (int i = 1; i <= 3; i++) edges.add(new int[]{i, i + 3});
        return edges.toArray(new int[0][]);
    }

    public static Glyph of(String id) {
        long h = mix(fnv(id));
        int count = 3 + (int) ((h >>> 3) & 1) + (int) ((h >>> 9) & 1);
        List<int[]> strokes = new ArrayList<>();
        boolean[] used = new boolean[EDGES.length];
        long state = h;
        while (strokes.size() < count) {
            state = mix(state + 0x9E3779B97F4A7C15L);
            int pick = (int) Long.remainderUnsigned(state, EDGES.length);
            if (used[pick]) continue;
            used[pick] = true;
            strokes.add(EDGES[pick]);
        }
        int notches = (int) ((h >>> 20) & 0x3F);
        if (notches == 0) notches = 0b100001;
        int dots = 1 + (int) Long.remainderUnsigned(h >>> 32, 3);
        return new Glyph(List.copyOf(strokes), notches, dots);
    }

    private static long fnv(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }

    /** SplitMix64 finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
