package com.nexusgate.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The station icons shown in the drum chambers: 9x9 pixel-art bitmaps ('#' = lit pixel). Index 0 (pin) is the
 * default. The order is persisted per station, so new icons must be appended, never inserted.
 */
public final class StationIcons {
    private static final String[] NAMES = {
            "pin", "house", "base", "skull", "mine", "farm", "portal", "tree", "chest", "sword", "star", "anchor"
    };

    private static final String[][] ART = {
            { // pin
                    "..#####..",
                    ".##...##.",
                    ".#..#..#.",
                    ".#.###.#.",
                    ".#..#..#.",
                    ".##...##.",
                    "..##.##..",
                    "...###...",
                    "....#...."},
            { // house
                    "....#....",
                    "...###...",
                    "..##.##..",
                    ".##...##.",
                    "#########",
                    ".#.....#.",
                    ".#.##..#.",
                    ".#.##..#.",
                    ".#######."},
            { // base (fort)
                    "#.#.#.#.#",
                    "#########",
                    ".#.....#.",
                    ".#.#.#.#.",
                    ".#.....#.",
                    ".#..#..#.",
                    ".#.###.#.",
                    ".#.###.#.",
                    ".#######."},
            { // skull
                    "..#####..",
                    ".#######.",
                    "##..#..##",
                    "##..#..##",
                    "#########",
                    ".###.###.",
                    "..#####..",
                    "..#.#.#..",
                    "..#####.."},
            { // mine (pickaxe)
                    "..#####..",
                    ".#..#..#.",
                    "#...#...#",
                    "....#....",
                    "....#....",
                    "....#....",
                    "....#....",
                    "....#....",
                    "...###..."},
            { // farm (wheat)
                    "....#....",
                    "...#.#...",
                    "..#.#.#..",
                    "...###...",
                    "..#.#.#..",
                    "...###...",
                    "....#....",
                    "....#....",
                    "...###..."},
            { // portal
                    ".#######.",
                    ".#.....#.",
                    ".#.###.#.",
                    ".#.#...#.",
                    ".#.#.#.#.",
                    ".#...#.#.",
                    ".#.###.#.",
                    ".#.....#.",
                    ".#######."},
            { // tree
                    "...###...",
                    "..#####..",
                    ".#######.",
                    "#########",
                    ".#######.",
                    "..#####..",
                    "....#....",
                    "....#....",
                    "..#####.."},
            { // chest (shop / storage)
                    ".........",
                    ".#######.",
                    "#.......#",
                    "#.......#",
                    "###.#.###",
                    "#...#...#",
                    "#.......#",
                    "#########",
                    "........."},
            { // sword (arena)
                    ".......##",
                    "......###",
                    ".....###.",
                    "....###..",
                    ".#.###...",
                    "..###....",
                    "..##.....",
                    ".#..#....",
                    "#........"},
            { // star
                    "....#....",
                    "....#....",
                    "...###...",
                    "#########",
                    ".#######.",
                    "..#####..",
                    "..##.##..",
                    ".##...##.",
                    ".#.....#."},
            { // anchor (port)
                    "...###...",
                    "...#.#...",
                    "...###...",
                    ".#######.",
                    "....#....",
                    "#...#...#",
                    "##..#..##",
                    ".##.#.##.",
                    "..#####.."},
    };

    public static final int COUNT = ART.length;
    private static final int N = 9;

    private StationIcons() {}

    public static int clamp(int icon) {
        return icon < 0 || icon >= COUNT ? 0 : icon;
    }

    /** Lang key of the icon's display name. */
    public static String key(int icon) {
        return "icon.nexusgate." + NAMES[clamp(icon)];
    }

    /**
     * Draws the icon centred on (x, y), about {@code size} GUI pixels wide, snapped to the pixel-art grid.
     * {@code glow} (may be 0) is drawn first as a soft one-cell halo around every lit pixel.
     */
    public static void draw(GuiGraphics g, float x, float y, float size, int icon, int solid, int glow) {
        String[] rows = ART[clamp(icon)];
        float cell = Math.max(GuiDraw.unit(), Math.round(size / N));
        float x0 = Math.round(x - cell * N * 0.5f), y0 = Math.round(y - cell * N * 0.5f);
        if ((glow >>> 24) != 0) {
            for (int j = 0; j < N; j++) {
                for (int i = 0; i < N; i++) {
                    if (rows[j].charAt(i) != '#') continue;
                    float px = x0 + i * cell, py = y0 + j * cell;
                    GuiDraw.rect(g, px - cell * 0.5f, py - cell * 0.5f, px + cell * 1.5f, py + cell * 1.5f, glow, glow);
                }
            }
        }
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                if (rows[j].charAt(i) != '#') continue;
                float px = x0 + i * cell, py = y0 + j * cell;
                GuiDraw.rect(g, px, py, px + cell, py + cell, solid, solid);
            }
        }
    }
}
