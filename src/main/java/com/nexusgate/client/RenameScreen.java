package com.nexusgate.client;

import com.nexusgate.net.OpenRenamePayload;
import com.nexusgate.net.RenameStationPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Gate-styled screen to name a station. */
public class RenameScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 90;

    private final OpenRenamePayload payload;
    private EditBox nameBox;
    private boolean sent;

    public RenameScreen(OpenRenamePayload payload) {
        super(Component.translatable("screen.nexusgate.rename_title"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        String initial = nameBox != null ? nameBox.getValue()
                : payload.isNew() ? Component.translatable("station.nexusgate.default_name", payload.x(), payload.z()).getString()
                : payload.currentName();
        nameBox = new EditBox(font, left + 12, top + 32, PANEL_W - 24, 18, title);
        nameBox.setMaxLength(32);
        nameBox.setValue(initial);
        nameBox.setTextColor(Terminal.CYAN_BRIGHT);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(new com.nexusgate.client.gui.TechButton(left + PANEL_W / 2 - 50, top + PANEL_H - 28, 100, 18,
                Component.translatable("screen.nexusgate.confirm"), 0x33F0FF, b -> confirm()));
    }

    private void confirm() {
        send();
        onClose();
    }

    private void send() {
        if (sent) return;
        sent = true;
        PacketDistributor.sendToServer(new RenameStationPayload(payload.station(), nameBox.getValue()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // A new station keeps the name shown in the box (localized on this client) even if closed with Esc.
        if (payload.isNew()) send();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        g.drawString(font, "NEXUS GATE // " + title.getString().toUpperCase(java.util.Locale.ROOT), left + 12, top + 9, 0xFF16191E, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        Terminal.panel(g, left, top, PANEL_W, PANEL_H);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
