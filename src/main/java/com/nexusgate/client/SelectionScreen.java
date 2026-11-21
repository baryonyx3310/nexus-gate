package com.nexusgate.client;

import com.nexusgate.core.StationEntry;
import com.nexusgate.net.OpenRenamePayload;
import com.nexusgate.net.OpenSelectionPayload;
import com.nexusgate.net.SelectStationPayload;
import com.nexusgate.net.SetPublicPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Terminal-styled list of reachable stations. All checks are repeated on the server. */
public class SelectionScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int ROW_H = 16;
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_TOP = 30;
    private static final int PANEL_H = LIST_TOP + VISIBLE_ROWS * ROW_H + 36;

    private final OpenSelectionPayload payload;
    private final List<StationEntry> entries;
    private int combatTicks;
    private int scroll;

    public SelectionScreen(OpenSelectionPayload payload) {
        super(Component.literal("Nexus Gate"));
        this.payload = payload;
        this.entries = payload.entries();
        this.combatTicks = payload.combatRemainingTicks();
    }

    private int left() { return (width - PANEL_W) / 2; }

    private int top() { return (height - PANEL_H) / 2; }

    @Override
    protected void init() {
        int buttonsY = top() + PANEL_H - 26;
        int x = left() + 10;
        if (payload.canRename()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.nexusgate.rename"), b -> openRename())
                    .bounds(x, buttonsY, 90, 18).build());
            x += 96;
        }
        if (payload.isOp()) {
            Component label = Component.translatable(payload.sourcePublic() ? "screen.nexusgate.make_private" : "screen.nexusgate.make_public");
            addRenderableWidget(Button.builder(label, b -> togglePublic()).bounds(x, buttonsY, 110, 18).build());
        }
    }

    private void openRename() {
        Minecraft.getInstance().setScreen(new RenameScreen(
                new OpenRenamePayload(UUID.fromString(payload.sourceId()), payload.sourceName(), false, 0, 0)));
    }

    private void togglePublic() {
        PacketDistributor.sendToServer(new SetPublicPayload(UUID.fromString(payload.sourceId()), !payload.sourcePublic()));
        onClose();
    }

    @Override
    public void tick() {
        if (combatTicks > 0) combatTicks--;
    }

    private boolean selectable(StationEntry e) {
        return combatTicks <= 0 && e.affordable();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            StationEntry e = entries.get(index);
            if (selectable(e)) {
                PacketDistributor.sendToServer(new SelectStationPayload(UUID.fromString(payload.sourceId()), UUID.fromString(e.id())));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, entries.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        int x0 = left() + 6;
        int y0 = top() + LIST_TOP;
        if (mouseX < x0 || mouseX > x0 + PANEL_W - 12 || mouseY < y0 || mouseY >= y0 + VISIBLE_ROWS * ROW_H) return -1;
        int index = scroll + (int) ((mouseY - y0) / ROW_H);
        return index < entries.size() ? index : -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Terminal.panel(g, left(), top(), PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        g.drawString(font, "> NEXUS GATE // " + payload.sourceName(), left + 10, top + 10, Terminal.CYAN, false);
        if (combatTicks > 0) {
            String warn = Component.translatable("screen.nexusgate.interference", (combatTicks + 19) / 20).getString();
            g.drawString(font, warn, left + PANEL_W - 10 - font.width(warn), top + 10, Terminal.RED, false);
        }

        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("screen.nexusgate.empty").getString(), left + 12, top + LIST_TOP + 6, Terminal.GRAY, false);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= entries.size()) break;
            StationEntry e = entries.get(index);
            int y = top + LIST_TOP + row * ROW_H;
            boolean enabled = selectable(e);
            if (index == hovered && enabled) g.fill(left + 6, y, left + PANEL_W - 6, y + ROW_H, Terminal.ROW_HOVER);

            int nameColor = !enabled ? Terminal.GRAY : e.isPublic() ? Terminal.GOLD : Terminal.WHITE;
            String name = e.name();
            if (e.isPublic()) name = name + " " + Component.translatable("screen.nexusgate.public_tag").getString();
            g.drawString(font, font.plainSubstrByWidth(name, 150), left + 12, y + 4, nameColor, false);

            g.drawString(font, dimensionLabel(e.dimension()), left + 168, y + 4, Terminal.GRAY, false);

            String dist = e.distance() < 0 ? Component.translatable("screen.nexusgate.other_dimension").getString()
                    : Component.translatable("screen.nexusgate.distance", Math.round(e.distance())).getString();
            g.drawString(font, dist, left + 232, y + 4, Terminal.CYAN_BRIGHT, false);

            String cost = e.xpCost() == 0 ? Component.translatable("screen.nexusgate.free").getString()
                    : Component.translatable("screen.nexusgate.xp", e.xpCost()).getString();
            int costColor = e.affordable() ? Terminal.CYAN : Terminal.RED;
            g.drawString(font, cost, left + PANEL_W - 12 - font.width(cost), y + 4, costColor, false);
        }

        if (entries.size() > VISIBLE_ROWS) {
            int trackTop = top + LIST_TOP;
            int trackH = VISIBLE_ROWS * ROW_H;
            int thumbH = Math.max(8, trackH * VISIBLE_ROWS / entries.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / (entries.size() - VISIBLE_ROWS);
            g.fill(left + PANEL_W - 4, thumbY, left + PANEL_W - 2, thumbY + thumbH, Terminal.CYAN);
        }
    }

    private static String dimensionLabel(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        String path = rl == null ? id : rl.getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path.replace('_', ' ').toUpperCase(Locale.ROOT);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
