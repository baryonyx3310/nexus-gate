package com.nexusgate.client.fx;

import com.nexusgate.client.gui.GateScreen;
import com.nexusgate.client.gui.GuiDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/** HUD: radial charge meter around the crosshair, and the full-screen dive / tunnel / exit sequence. */
public final class HudFxLayer implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        TeleportFxController.Charge charge = TeleportFxController.localCharge();
        boolean screenDrives = mc.screen instanceof GateScreen;

        if (charge != null && !screenDrives && DiveFx.phase() == DiveFx.Phase.NONE) {
            chargeMeter(g, w * 0.5f, h * 0.5f, charge.progress(partial));
        }
        float hudDive = charge != null && !screenDrives ? charge.dive(partial) : -1f;
        DiveFx.renderHud(g, w, h, hudDive);
    }

    private static void chargeMeter(GuiGraphics g, float cx, float cy, float progress) {
        float r0 = 9f, r1 = 11f;
        GuiDraw.setUnit(1f);
        GuiDraw.pixelRing(g, cx, cy, r0, r1, 0, Mth.TWO_PI, 0x5512181E, 0x5512181E, false);
        // The sweep advances in 1/32-turn pixel notches.
        float sweep = Mth.TWO_PI * (float) Math.floor(progress * 32f) / 32f;
        if (sweep > 0f) GuiDraw.pixelRing(g, cx, cy, r0, r1, 0, sweep, 0xFF33F0FF, 0xFFCFFBFF, true);
        // Eight chamber ticks that light as the drum aligns.
        for (int i = 0; i < 8; i++) {
            float a = Mth.TWO_PI * i / 8f;
            boolean lit = progress >= (i + 1) / 8f;
            GuiDraw.pixelRing(g, cx, cy, r1 + 1.5f, r1 + 3.5f, a - 0.12f, a + 0.12f,
                    lit ? 0xFFFFB347 : 0x66FFFFFF, lit ? 0xFFFFB347 : 0x66FFFFFF, lit);
        }
        GuiDraw.blockGlow(g, cx, cy, 6f + progress * 4f, 0x33F0FF, 0.25f + progress * 0.35f);
    }
}
