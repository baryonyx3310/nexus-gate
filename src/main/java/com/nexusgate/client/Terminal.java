package com.nexusgate.client;

import com.nexusgate.client.gui.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;

/** Shared panel look (graphite body, ceramic header strip, cyan latch) used by the smaller gate screens. */
final class Terminal {
    static final int CYAN = 0xFF33F0FF;
    static final int CYAN_BRIGHT = 0xFFCFFBFF;
    static final int GRAY = 0xFF7A8490;
    static final int WHITE = 0xFFD9DCD6;

    private Terminal() {}

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // Stepped drop shadow (two flat layers) instead of a soft radial vignette.
        g.fill(x - 6, y - 4, x + w + 6, y + h + 8, 0x30000000);
        g.fill(x - 2, y, x + w + 3, y + h + 4, 0x40000000);
        GuiDraw.steppedPanel(g, x, y, x + w, y + h, 8f, 0xF0161A20, 0xF00A0C10, 0xFF3A414A);
        // Ceramic header strip with a stepped right end, and the status latch.
        GuiDraw.steppedPanel(g, x + 3, y + 3, x + w - 44, y + 22, 4f, 0xFFD9DCD6, 0xFFB9BDB7, 0xFF8E928C);
        for (int i = 0; i < 3; i++) {
            int c = i == 0 ? CYAN : i == 1 ? 0xFF1E8FA0 : 0xFF145F6B;
            g.fill(x + w - 36 + i * 8, y + 9, x + w - 30 + i * 8, y + 13, c);
        }
        // Segmented underline fading out in steps.
        for (int i = 0; x + 10 + i * 6 < x + w * 0.45f; i++) {
            int a = Math.max(0, 0x80 - i * 0x0C);
            g.fill(x + 10 + i * 6, y + h - 7, x + 14 + i * 6, y + h - 5, (a << 24) | 0x33F0FF);
        }
        for (int i = 0; i < 4; i++) {
            int bx = i % 2 == 0 ? x + 5 : x + w - 7, by = i < 2 ? y + h / 2 - 9 : y + h / 2 + 7;
            g.fill(bx, by, bx + 2, by + 2, 0xFF2A2F37);
            g.fill(bx, by, bx + 1, by + 1, 0xFF6B7380);
        }
    }
}
