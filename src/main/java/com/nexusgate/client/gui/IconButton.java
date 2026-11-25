package com.nexusgate.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/** Small square TechButton that shows a station icon instead of a label (the "logo" button). */
public class IconButton extends TechButton {
    private final IntSupplier icon;
    private final int accent;

    public IconButton(int x, int y, int w, int h, Component narration, int accent, IntSupplier icon, OnPress onPress) {
        super(x, y, w, h, Component.empty(), accent, onPress);
        this.icon = icon;
        this.accent = accent;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(narration));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        boolean hot = isHoveredOrFocused() && active;
        int color = hot ? 0xFFFFFFFF : 0xFF000000 | accent;
        StationIcons.draw(g, getX() + width / 2f + 2, getY() + height / 2f, 9, icon.getAsInt(), color, 0);
    }
}
