package com.nexusgate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Stepped-corner graphite/ceramic button matching the gate panel. Same behaviour as a vanilla button. */
public class TechButton extends Button {
    private final int accent;

    public TechButton(int x, int y, int w, int h, Component label, int accent, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused() && active;
        float x0 = getX(), y0 = getY(), x1 = getX() + width, y1 = getY() + height;
        int top = hot ? 0xF02A323C : 0xE01A1F26;
        int bottom = hot ? 0xF01A2028 : 0xE0101318;
        int rim = !active ? 0x60606870 : hot ? (0xFF000000 | accent) : 0xA0707A88;
        GuiDraw.steppedPanel(g, x0, y0, x1, y1, 4f, top, bottom, rim);
        // Accent latch on the left edge: two emissive blocks.
        int latch = active ? (hot ? 0xFF000000 | accent : 0x80000000 | accent) : 0x40606870;
        int mid = getY() + height / 2;
        g.fill(getX() + 4, getY() + 4, getX() + 6, mid - 1, latch);
        g.fill(getX() + 4, mid + 1, getX() + 6, getY() + height - 4, latch);
        Font font = Minecraft.getInstance().font;
        String text = getMessage().getString().toUpperCase(java.util.Locale.ROOT);
        int color = !active ? 0xFF6A7480 : hot ? 0xFFFFFFFF : 0xFFD9DCD6;
        g.drawString(font, text, getX() + (width - font.width(text)) / 2 + 2, getY() + (height - 8) / 2, color, false);
    }
}
