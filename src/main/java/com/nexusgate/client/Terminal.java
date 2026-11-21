package com.nexusgate.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared terminal look: colours and the bordered panel. */
final class Terminal {
    static final int BACKGROUND = 0xF00A0E12;
    static final int ROW_HOVER = 0xFF12303A;
    static final int CYAN = 0xFF00E5FF;
    static final int CYAN_BRIGHT = 0xFF7FF6FF;
    static final int GRAY = 0xFF8A94A6;
    static final int GOLD = 0xFFFFC94A;
    static final int RED = 0xFFFF5555;
    static final int WHITE = 0xFFE6F0F5;

    private Terminal() {}

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BACKGROUND);
        g.fill(x, y, x + w, y + 1, CYAN);
        g.fill(x, y + h - 1, x + w, y + h, CYAN);
        g.fill(x, y, x + 1, y + h, CYAN);
        g.fill(x + w - 1, y, x + w, y + h, CYAN);
        // scanline accent under the header
        g.fill(x + 6, y + 22, x + w - 6, y + 23, 0x8000E5FF);
    }
}
