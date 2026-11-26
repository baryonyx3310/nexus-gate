package com.nexusgate.client.gui;

import com.nexusgate.client.RenameScreen;
import com.nexusgate.client.fx.DiveFx;
import com.nexusgate.client.fx.GateSounds;
import com.nexusgate.client.fx.RelocationTimeline;
import com.nexusgate.client.fx.TeleportFxController;
import com.nexusgate.client.render.ModShaders;
import com.nexusgate.client.render.ShaderQuads;
import com.nexusgate.config.NexusClientConfig;
import com.nexusgate.core.StationEntry;
import com.nexusgate.net.OpenRenamePayload;
import com.nexusgate.net.OpenSelectionPayload;
import com.nexusgate.net.SelectStationPayload;
import com.nexusgate.net.SetIconPayload;
import com.nexusgate.net.SetPublicPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The gate's control face: a revolver drum of destination chambers around a plasma core.
 * Select a chamber, press the core, watch the drum index and the iris open, then dive into the core.
 * Every rule is still enforced on the server; this screen only sends the same {@link SelectStationPayload}.
 */
public class GateScreen extends Screen implements TeleportFxController.LocalListener {
    // Palette (shared visual language with the block).
    static final int GRAPHITE_0 = 0xFF0C0F12;
    static final int GRAPHITE_1 = 0xFF16191E;
    static final int GRAPHITE_2 = 0xFF2A2F37;
    static final int STEEL = 0xFF4A525D;
    static final int EDGE = 0xFF6B7380;
    static final int CERAMIC = 0xFFD9DCD6;
    static final int CYAN = 0x33F0FF;
    static final int AMBER = 0xFFB347;
    static final int RED = 0xFF4A4A;
    static final int GOLD = 0xFFC94A;
    static final int TEXT = 0xFFD9DCD6;
    static final int TEXT_DIM = 0xFF7A8490;

    /** Light comes from the upper left (angle in the clockwise-from-top convention). */
    private static final float LIGHT = -0.8f;

    private enum State { IDLE, ENGAGING, CHARGING, FAULT }

    private final OpenSelectionPayload payload;
    private final List<StationEntry> entries;
    private int combatTicks;

    private long openedNs = -1;
    private long lastNs;
    private float time;

    private float drumAngle = -0.75f;
    private float drumVel;
    private float drumTarget;
    private float irisOpen = 0.05f;
    private float shake;
    private int page;
    private int selected = -1;
    private int hoverSlot = -1;
    private boolean hoverCore;
    private int mouseX0, mouseY0;
    /** Icon of the station this screen was opened from; changed through the logo picker. */
    private int sourceIcon;
    private boolean iconPickerOpen;
    @Nullable private IconButton logoButton;
    /** Close button of the "select a destination" hint, valid while the hint is drawn (x0 < 0 otherwise). */
    private float hintCloseX0 = -1, hintCloseY0, hintCloseX1, hintCloseY1;
    private int revealedSounds;
    private float readoutTime;

    private State state = State.IDLE;
    private float stateTime;
    @Nullable private TeleportFxController.Charge charge;
    @Nullable private Component fault;
    private boolean closing;

    // Layout (recomputed every frame).
    private float cx, cy, R;

    public GateScreen(OpenSelectionPayload payload) {
        super(Component.literal("Nexus Gate"));
        this.payload = payload;
        this.entries = payload.entries();
        this.combatTicks = payload.combatRemainingTicks();
        this.sourceIcon = StationIcons.clamp(payload.sourceIcon());
    }

    // ================================================================== lifecycle

    @Override
    protected void init() {
        layout();
        TeleportFxController.setListener(this);
        TeleportFxController.Charge running = TeleportFxController.localCharge();
        if (running != null) onChargeStart(running);

        int y = 8;
        int x = width - 8;
        if (payload.isOp()) {
            Component label = Component.translatable(payload.sourcePublic() ? "screen.nexusgate.make_private" : "screen.nexusgate.make_public");
            int w = font.width(label.getString()) + 20;
            x -= w;
            addRenderableWidget(new TechButton(x, y, w, 16, label, GOLD, b -> togglePublic()));
            x -= 6;
        }
        if (payload.canRename()) {
            Component label = Component.translatable("screen.nexusgate.rename");
            int w = font.width(label.getString()) + 20;
            x -= w;
            addRenderableWidget(new TechButton(x, y, w, 16, label, CYAN, b -> openRename()));
            x -= 6 + 20;
            logoButton = addRenderableWidget(new IconButton(x, y, 20, 16, Component.translatable("screen.nexusgate.logo"), CYAN,
                    () -> sourceIcon, b -> {
                        iconPickerOpen = !iconPickerOpen;
                        GateSounds.index();
                    }));
        }
        if (RadialLayout.pages(entries.size()) > 1) {
            int py = (int) (cy + R * 1.24f) + 9;
            int half = readoutWidth() / 2;
            addRenderableWidget(new TechButton((int) cx - half - 24, py, 18, 16, Component.literal("<"), CYAN, b -> changePage(-1)));
            addRenderableWidget(new TechButton((int) cx + half + 6, py, 18, 16, Component.literal(">"), CYAN, b -> changePage(1)));
        }
    }

    @Override
    public void removed() {
        TeleportFxController.setListener(null);
        ShaderQuads.resetUniforms();
        super.removed();
    }

    private void layout() {
        cx = width * 0.5f;
        cy = height * 0.5f;
        R = Math.min(110f, Math.min((height * 0.5f - 46f) / 1.24f, (width * 0.5f - 112f) / 1.25f));
        R = Math.max(R, 36f);
    }

    private int readoutWidth() {
        return (int) Math.min(380, width - 70);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================== charge events

    @Override
    public void onChargeStart(TeleportFxController.Charge c) {
        charge = c;
        state = State.CHARGING;
        stateTime = 0;
        GateSounds.irisOpen();
    }

    @Override
    public void onChargeEnd(boolean departed, @Nullable Component reason) {
        charge = null;
        if (departed) {
            // The HUD continues the same tunnel from here; closing now is seamless.
            closing = true;
            Minecraft.getInstance().setScreen(null);
            return;
        }
        state = State.FAULT;
        stateTime = 0;
        fault = reason;
        shake = 1f;
        irisOpen = 0.04f;
    }

    // ================================================================== input

    private boolean selectable(StationEntry e) {
        return combatTicks <= 0 && e.affordable();
    }

    private boolean busy() {
        return state == State.ENGAGING || state == State.CHARGING;
    }

    private float chamberRing() { return R * 0.64f; }

    private float chamberRadius() { return R * 0.19f; }

    private float hubRadius() { return R * 0.34f; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (iconPickerOpen && button == 0) {
            int cell = pickerCellAt(mouseX, mouseY);
            if (cell >= 0) {
                chooseIcon(cell);
                return true;
            }
            // The logo button itself toggles the picker; any other click outside closes it.
            if (logoButton == null || !logoButton.isMouseOver(mouseX, mouseY)) {
                if (!pickerContains(mouseX, mouseY)) iconPickerOpen = false;
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || busy() || closing) return false;
        if (overHintClose(mouseX, mouseY)) {
            NexusClientConfig.hideSelectHint();
            hintCloseX0 = -1;
            GateSounds.index();
            return true;
        }
        int slot = RadialLayout.chamberAt(mouseX, mouseY, cx, cy, drumAngle, chamberRing(), chamberRadius());
        if (slot >= 0) {
            int e = RadialLayout.entryAt(page, slot, entries.size());
            if (e >= 0) {
                select(e);
                return true;
            }
        }
        if (RadialLayout.inCircle(mouseX, mouseY, cx, cy, hubRadius())) {
            engage();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && !busy()) changePage(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (iconPickerOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            iconPickerOpen = false;
            return true;
        }
        if (!busy()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                engage();
                return true;
            }
            if (!entries.isEmpty() && (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_LEFT)) {
                int dir = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
                int next = selected < 0 ? 0 : Math.floorMod(selected + dir, entries.size());
                if (RadialLayout.pageOf(next) != page) page = RadialLayout.pageOf(next);
                select(next);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void changePage(int dir) {
        int pages = RadialLayout.pages(entries.size());
        if (pages <= 1) return;
        page = Math.floorMod(page + dir, pages);
        // Cylinder swap: a full mechanical turn while the new chambers lock in.
        drumTarget += dir * RadialLayout.TWO_PI;
        if (selected >= 0 && RadialLayout.pageOf(selected) == page) {
            drumTarget = RadialLayout.alignRotation(RadialLayout.slotOf(selected), drumTarget);
        }
        GateSounds.index();
    }

    private void select(int entry) {
        if (entry == selected) return;
        selected = entry;
        drumTarget = RadialLayout.alignRotation(RadialLayout.slotOf(entry), drumTarget);
        readoutTime = 0;
        if (state == State.FAULT) state = State.IDLE;
        GateSounds.index();
        if (!selectable(entries.get(entry))) GateSounds.denied();
    }

    private void engage() {
        if (busy() || closing) return;
        if (selected < 0 || !selectable(entries.get(selected))) {
            shake = 1f;
            GateSounds.denied();
            return;
        }
        StationEntry e = entries.get(selected);
        PacketDistributor.sendToServer(new SelectStationPayload(UUID.fromString(payload.sourceId()), UUID.fromString(e.id())));
        state = State.ENGAGING;
        stateTime = 0;
        fault = null;
        GateSounds.engage();
    }

    // ================================================================== animation state

    private void update(float dt) {
        time += dt;
        stateTime += dt;
        readoutTime += dt;
        shake = Math.max(0f, shake - dt * 3.5f);

        // Critically-damped-ish spring with a little overshoot: reads as a heavy cylinder locking.
        float k = 95f, damping = 13f;
        drumVel += ((drumTarget - drumAngle) * k - drumVel * damping) * dt;
        drumAngle += drumVel * dt;

        if (state == State.ENGAGING && stateTime > 1.6f) state = State.IDLE;
        if (state == State.FAULT && stateTime > 3.0f) {
            state = State.IDLE;
            fault = null;
        }

        float target;
        float progress = charge != null ? charge.progress(TeleportFxController.partial()) : 0f;
        switch (state) {
            case CHARGING -> target = Mth.lerp(DiveFx.smooth(0f, 0.6f, progress), 0.55f, 1f);
            case ENGAGING -> target = 0.6f;
            case FAULT -> target = 0.05f;
            default -> target = (selected >= 0 ? 0.48f : 0.3f) + (hoverCore ? 0.1f : 0f) + 0.03f * Mth.sin(time * 1.7f);
        }
        float rate = state == State.FAULT ? 30f : 7f;
        irisOpen += (target - irisOpen) * Math.min(1f, dt * rate);

        // Reveal sounds, one per chamber.
        int shouldHave = 0;
        for (int i = 0; i < RadialLayout.CHAMBERS; i++) if (reveal(i) >= 1f) shouldHave = i + 1;
        while (revealedSounds < shouldHave) GateSounds.chamberReveal(revealedSounds++);
    }

    /** Boot: chamber i reveal progress. */
    private float reveal(int slot) {
        return Mth.clamp((time - 0.62f - slot * 0.055f) / 0.18f, 0f, 1f);
    }

    /** Extra rotation while charging: two indexed turns that lock back on the selected chamber. */
    private float chargeSpin(float progress) {
        float s = DiveFx.smooth(0.02f, 0.8f, progress);
        return RadialLayout.TWO_PI * 2f * s;
    }

    // ================================================================== rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Custom backdrop instead of the vanilla blur: the world stays faintly visible behind the panel.
        g.fillGradient(0, 0, width, height, 0xB805090C, 0xE0020304);
        GuiDraw.disc(g, cx, cy, Math.max(width, height) * 0.8f, 0x00000000, 0xA0000000, false);
        int grid = 14;
        for (int x = (int) (cx % grid); x < width; x += grid) {
            for (int y = (int) (cy % grid); y < height; y += grid) {
                float d = Mth.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (R * 2.2f);
                float a = 0.10f * Mth.clamp(1.2f - d, 0f, 1f);
                if (a > 0.01f) g.fill(x, y, x + 1, y + 1, GuiDraw.argb(a, CYAN));
            }
        }
        // Slow scan band.
        int band = (int) ((time * 40f) % (height + 60)) - 30;
        g.fillGradient(0, band, width, band + 30, 0x0033F0FF, 0x0C33F0FF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (openedNs < 0) {
            openedNs = now;
            lastNs = now;
            GateSounds.boot();
        }
        float dt = Mth.clamp((now - lastNs) / 1_000_000_000f, 0f, 0.05f);
        lastNs = now;
        layout();
        update(dt);

        float partial = TeleportFxController.partial();
        float progress = charge != null ? charge.progress(partial) : 0f;
        float dive = charge != null ? charge.dive(partial) : 0f;
        float spin = state == State.CHARGING ? chargeSpin(progress) : 0f;
        float rot = drumAngle + spin;

        hoverSlot = busy() ? -1 : RadialLayout.chamberAt(mouseX, mouseY, cx, cy, rot, chamberRing(), chamberRadius());
        boolean wasHoverCore = hoverCore;
        hoverCore = !busy() && RadialLayout.inCircle(mouseX, mouseY, cx, cy, hubRadius());
        mouseX0 = mouseX;
        mouseY0 = mouseY;
        if (hoverCore && !wasHoverCore) GateSounds.hover();

        for (var child : children()) {
            if (child instanceof TechButton b) b.visible = dive <= 0f;
        }
        super.render(g, mouseX, mouseY, partialTick);

        float ui = 1f - DiveFx.smooth(0.15f, 0.6f, dive);
        float zoom = DiveFx.zoomFor(dive);
        float sx = shake > 0 ? Math.round(Mth.sin(time * 70f) * shake * 1.5f) * 2f : 0f;

        // Pixel-art grid for the whole panel: ~2 GUI px per "pixel" at full size.
        GuiDraw.setUnit(pixel());
        g.pose().pushPose();
        g.pose().translate(cx + sx, cy, 0);
        g.pose().scale(zoom, zoom, 1f);
        g.pose().translate(-cx, -cy, 0);
        if (ui > 0.01f) telemetry(g, ui);
        bezel(g, progress, ui);
        stator(g, progress);
        drum(g, rot);
        chambers(g, rot, progress);
        hub(g, progress, dive);
        g.pose().popPose();

        if (dive > 0f) {
            DiveFx.drawCore(g, width, height, cx, cy, R * 0.27f, dive, 1f, CYAN);
        }
        if (ui > 0.05f) {
            labels(g, rot, ui * (1f - DiveFx.smooth(0f, 0.15f, progress)));
            header(g, ui);
            readout(g, progress, ui);
        }
        if (iconPickerOpen && (busy() || dive > 0f)) iconPickerOpen = false;
        if (iconPickerOpen && ui > 0.05f) iconPicker(g);
        if (state == State.FAULT) {
            float f = Math.max(0f, 1f - stateTime * 2.5f);
            if (f > 0) g.fill(0, 0, width, height, GuiDraw.argb(0.22f * GuiDraw.levels(f, 3), 0xFF2030));
        }
        GuiDraw.setUnit(1f);
        if (charge != null) connectingBanner(g, progress);
    }

    /**
     * "CONECTANDO COM A INTRA" while the core reaches out to the traveller (connection phase only):
     * black letters with a red outline, INTRA larger with a pulsing bright outline and a red glow behind it.
     */
    private void connectingBanner(GuiGraphics g, float progress) {
        float a = RelocationTimeline.bannerAlpha(progress);
        if (a <= 0.02f) return;
        String lead = Component.translatable("screen.nexusgate.connecting").getString() + " ";
        String hot = Component.translatable("screen.nexusgate.connecting_highlight").getString();
        float big = 1.5f;
        float wLead = font.width(lead);
        float wHot = font.width(hot) * big;
        float x = Math.round(cx - (wLead + wHot) / 2f);
        float y = Math.round(cy - R * 1.24f - 24);
        float pulse = 0.5f + 0.5f * Mth.sin(time * 6f);
        GuiDraw.blockGlow(g, x + wLead + wHot / 2f, y + 6f, wHot * 0.75f, 0xFF3030, 0.3f * a * (0.6f + 0.4f * pulse));
        outlined(g, lead, x, y + 3, 1f, GuiDraw.fade(0xFF000000, a), GuiDraw.fade(0xFFB01E1E, a));
        int hotOutline = GuiDraw.lerpColor(0xFFB01E1E, 0xFFFF3030, pulse);
        outlined(g, hot, x + wLead, y, big, GuiDraw.fade(0xFF000000, a), GuiDraw.fade(hotOutline, a));
    }

    private void outlined(GuiGraphics g, String s, float x, float y, float scale, int fill, int outline) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        font.drawInBatch8xOutline(Component.literal(s).getVisualOrderText(), 0, 0, fill, outline,
                g.pose().last().pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
        g.flush();
        g.pose().popPose();
    }

    /** Size of one art pixel in GUI pixels. */
    private float pixel() {
        return Math.max(1f, Math.round(R / 55f));
    }

    /** Quantises a 0..1 value into mechanical notches with a short eased move between them. */
    private static float notched(float v, int steps) {
        float s = Mth.clamp(v, 0f, 1f) * steps;
        float k = (float) Math.floor(s);
        float f = DiveFx.smooth(0.65f, 1f, s - k);
        return Mth.clamp((k + f) / steps, 0f, 1f);
    }

    /** 2x2-pixel rivet snapped to the grid: lit top-left, shadowed bottom-right. */
    private static void rivet(GuiGraphics g, float x, float y, int light, int dark) {
        float u = GuiDraw.unit();
        float x0 = (float) Math.floor(x / u - 1f) * u, y0 = (float) Math.floor(y / u - 1f) * u;
        GuiDraw.rect(g, x0, y0, x0 + 2 * u, y0 + 2 * u, dark, dark);
        GuiDraw.rect(g, x0, y0, x0 + u, y0 + u, light, light);
    }

    /** Square status LED (2x2 pixels) snapped to the grid. */
    private static void led(GuiGraphics g, float x, float y, int argb) {
        float u = GuiDraw.unit();
        float x0 = (float) Math.floor(x / u - 1f) * u, y0 = (float) Math.floor(y / u - 1f) * u;
        GuiDraw.rect(g, x0, y0, x0 + 2 * u, y0 + 2 * u, argb, argb);
        int hi = GuiDraw.lerpColor(argb, 0xFFFFFFFF, 0.6f);
        GuiDraw.rect(g, x0, y0, x0 + u, y0 + u, hi, hi);
    }

    // ---------------------------------------------------------------- layers

    private void telemetry(GuiGraphics g, float ui) {
        float boot = DiveFx.easeOutCubic(time / 0.6f);
        float r0 = R * 1.28f, r1 = R * 1.6f;
        for (int i = 0; i < 24; i++) {
            float a = RadialLayout.TWO_PI * i / 24f + time * 0.02f;
            float len = (i % 3 == 0 ? 1f : 0.45f) * boot;
            GuiDraw.pixelLine(g, GuiDraw.px(cx, a, r0), GuiDraw.py(cy, a, r0), GuiDraw.px(cx, a, r0 + (r1 - r0) * len),
                    GuiDraw.py(cy, a, r0 + (r1 - r0) * len), 1f, GuiDraw.argb(0.16f * ui, CYAN), GuiDraw.argb(0.03f * ui, CYAN), true);
        }
        // Crosshair rails with gaps, like a targeting reticle around the machine (dashed in pixel blocks).
        int rail = GuiDraw.argb(0.18f * ui * boot, CYAN);
        float e = R * 1.75f * boot;
        float u = GuiDraw.unit();
        for (float x = R * 1.3f; x < e; x += u * 4f) {
            float x1 = Math.min(e, x + u * 2f);
            GuiDraw.pixelLine(g, cx - x1, cy, cx - x, cy, 1f, rail, rail, true);
            GuiDraw.pixelLine(g, cx + x, cy, cx + x1, cy, 1f, rail, rail, true);
        }
    }

    private void bezel(GuiGraphics g, float progress, float ui) {
        float sweep = RadialLayout.TWO_PI * DiveFx.easeOutCubic(time / 0.38f);
        float spin = time * 0.06f + (charge != null ? progress * progress * 9f : 0f);
        GuiDraw.pixelRing(g, cx, cy, R * 1.10f, R * 1.135f, 0, sweep, GRAPHITE_1, GRAPHITE_2, false);
        GuiDraw.pixelRing(g, cx, cy, R * 1.135f, R * 1.15f, 0, sweep, 0xFF3A414A, 0xFF3A414A, false);
        // Ticks advance in whole-tick notches, like a mechanical counter.
        float tickStep = RadialLayout.TWO_PI / 72f;
        float notchedSpin = (float) Math.floor(spin / tickStep) * tickStep;
        for (int i = 0; i < 72; i++) {
            float a = tickStep * i + notchedSpin;
            if (i / 72f > sweep / RadialLayout.TWO_PI) continue;
            boolean major = i % 9 == 0;
            if (!major && i % 2 == 1) continue; // fewer, chunkier minor ticks at pixel resolution
            float outer = R * (major ? 1.21f : 1.175f);
            int c = major ? 0xC0A8B2BE : 0x50A8B2BE;
            GuiDraw.pixelLine(g, GuiDraw.px(cx, a, R * 1.155f), GuiDraw.py(cy, a, R * 1.155f), GuiDraw.px(cx, a, outer),
                    GuiDraw.py(cy, a, outer), 1f, GuiDraw.fade(c, ui), GuiDraw.fade(c, ui * 0.4f), false);
        }
        // Four ceramic clamps holding the drum (fixed).
        for (int i = 0; i < 4; i++) {
            float a = RadialLayout.TWO_PI * (i + 0.5f) / 4f;
            GuiDraw.pixelRing(g, cx, cy, R * 1.075f, R * 1.16f, a - 0.11f, a + 0.11f, 0xFFB9BDB7, CERAMIC, false);
            GuiDraw.pixelRing(g, cx, cy, R * 1.10f, R * 1.13f, a - 0.05f, a + 0.05f, GRAPHITE_0, GRAPHITE_0, false);
        }
        // Firing-position marker at 12 o'clock.
        boolean armed = selected >= 0;
        int marker = armed ? 0xFF000000 | AMBER : 0xFF59616C;
        float tip = cy - R * 1.085f, base = cy - R * 1.2f, w = R * 0.05f;
        GuiDraw.pixelPolygon(g, new float[]{cx, tip, cx + w, base, cx - w, base}, marker, false);
        if (armed) GuiDraw.blockGlow(g, cx, (tip + base) * 0.5f, R * 0.1f, AMBER, 0.35f + 0.15f * GuiDraw.levels(0.5f + 0.5f * Mth.sin(time * 4f), 3));
    }

    private void stator(GuiGraphics g, float progress) {
        GuiDraw.pixelLitRing(g, cx, cy, R * 0.965f, R * 1.08f, 0xFF101317, 0xFF434A55, LIGHT, 2f);
        GuiDraw.pixelRing(g, cx, cy, R * 0.955f, R * 0.968f, 0xFF59616C);
        GuiDraw.pixelRing(g, cx, cy, R * 1.078f, R * 1.09f, GRAPHITE_0);
        float boltFlash = time > 0.7f ? (float) Math.exp(-(time - 0.7f) * 7f) : 0f;
        for (int i = 0; i < 8; i++) {
            float a = RadialLayout.slotAngle(i);
            int color;
            boolean additive = false;
            if (state == State.FAULT) {
                color = 0xFF000000 | RED;
                additive = true;
            } else if (charge != null) {
                boolean lit = progress >= (i + 1) / 8f;
                color = lit ? 0xFF000000 | CYAN : 0x6033F0FF;
                additive = lit;
            } else if (i == 0 && selected >= 0) {
                color = 0xFF000000 | AMBER;
                additive = true;
            } else {
                color = 0x5033F0FF;
            }
            GuiDraw.pixelRing(g, cx, cy, R * 1.0f, R * 1.045f, a - 0.07f, a + 0.07f, color, color, additive);
            if (additive) GuiDraw.blockGlow(g, GuiDraw.px(cx, a, R * 1.022f), GuiDraw.py(cy, a, R * 1.022f), R * 0.07f, color & 0xFFFFFF, 0.5f);
            // Rivets between the indicators.
            float b = a + RadialLayout.STEP * 0.5f;
            float bx = GuiDraw.px(cx, b, R * 1.022f), by = GuiDraw.py(cy, b, R * 1.022f);
            rivet(g, bx, by, EDGE, GRAPHITE_2);
            if (boltFlash > 0.02f) GuiDraw.blockGlow(g, bx, by, R * 0.05f, 0xFFFFFF, boltFlash * 0.8f);
        }
    }

    private void drum(GuiGraphics g, float rot) {
        float boot = Mth.clamp((time - 0.22f) / 0.45f, 0f, 1f);
        float scale = 0.86f + 0.14f * easeOutBack(boot);
        float r = R * 0.95f * scale;
        float u = GuiDraw.unit();
        GuiDraw.pixelDisc(g, cx + u, cy + 2 * u, r * 1.01f, 0x90000000, 0x40000000, false);
        GuiDraw.pixelLitRing(g, cx, cy, R * 0.40f, r, 0xFF181C21, 0xFF4E5661, LIGHT, 1.6f);
        GuiDraw.pixelRing(g, cx, cy, R * 0.84f, R * 0.846f, 0x24FFFFFF);
        GuiDraw.pixelRing(g, cx, cy, R * 0.47f, R * 0.476f, 0x24FFFFFF);
        for (int i = 0; i < 8; i++) {
            float a = RadialLayout.slotAngle(i) + RadialLayout.STEP * 0.5f + rot;
            // Flutes on the cylinder edge and ceramic index tabs between chambers.
            GuiDraw.pixelRing(g, cx, cy, r * 0.93f, r, a - 0.13f, a + 0.13f, GRAPHITE_0, 0xFF07090B, false);
            GuiDraw.pixelRing(g, cx, cy, R * 0.80f, R * 0.865f, a - 0.055f, a + 0.055f, 0xFFB9BDB7, CERAMIC, false);
            GuiDraw.pixelRing(g, cx, cy, R * 0.55f, R * 0.58f, a - 0.03f, a + 0.03f, 0xFF59616C, 0xFF59616C, false);
        }
    }

    private void chambers(GuiGraphics g, float rot, float progress) {
        float ring = chamberRing();
        float cr = chamberRadius();
        for (int slot = 0; slot < RadialLayout.CHAMBERS; slot++) {
            float x = RadialLayout.chamberX(cx, slot, rot, ring);
            float y = RadialLayout.chamberY(cy, slot, rot, ring);
            float angle = RadialLayout.slotAngle(slot) + rot;
            int index = RadialLayout.entryAt(page, slot, entries.size());
            float rv = reveal(slot);

            float u = GuiDraw.unit();
            GuiDraw.pixelDisc(g, x + u, y + u, cr * 1.06f, 0x90000000, 0x50000000, false);
            GuiDraw.pixelLitRing(g, x, y, cr * 0.78f, cr, 0xFF22272E, 0xFF8E98A4, LIGHT, 3f);
            GuiDraw.pixelRing(g, x, y, cr * 0.71f, cr * 0.785f, 0xFF07090B);

            if (index < 0) {
                plug(g, x, y, cr, 1f);
                continue;
            }
            StationEntry e = entries.get(index);
            boolean available = selectable(e);
            boolean isSelected = index == selected;
            int dim = dimensionColor(e.dimension());

            GuiDraw.pixelDisc(g, x, y, cr * 0.71f, GuiDraw.lerpColor(0xFF0B1418, 0xFF000000 | dim, 0.14f), 0xFF040607, false);
            float glyphScale = 0.6f + 0.4f * rv;
            glyph(g, x, y, cr * 0.42f * glyphScale, e.id(), e.icon(), dim, available ? 1f : 0.3f, isSelected);
            if (e.isPublic()) GuiDraw.pixelRing(g, x, y, cr * 0.80f, cr * 0.85f, 0, Mth.TWO_PI, 0xFF000000 | GOLD, 0xFF000000 | GOLD, false);

            // Status LED on the outer side of the chamber.
            float lx = GuiDraw.px(x, angle, cr * 0.89f), ly = GuiDraw.py(y, angle, cr * 0.89f);
            int ledColor = !available ? (combatTicks > 0 ? RED : AMBER) : isSelected ? 0xFFFFFF : CYAN;
            if (charge != null && isSelected) ledColor = CYAN;
            led(g, lx, ly, 0xFF000000 | ledColor);
            GuiDraw.blockGlow(g, lx, ly, cr * 0.3f, ledColor, rv * 0.5f);

            if (isSelected) {
                float pulse = 0.6f + 0.4f * GuiDraw.levels(0.5f + 0.5f * Mth.sin(time * 5f), 3);
                float energy = charge != null ? 0.8f + progress * 0.6f : pulse;
                GuiDraw.pixelRing(g, x, y, cr * 1.0f, cr * 1.18f, 0, Mth.TWO_PI, GuiDraw.argb(0.7f * energy, CYAN), GuiDraw.argb(0.12f * energy, CYAN), true);
                feedLine(g, x, y, angle, cr, energy);
            } else if (slot == hoverSlot) {
                GuiDraw.pixelRing(g, x, y, cr, cr * 1.07f, 0, Mth.TWO_PI, 0x60FFFFFF, 0x20FFFFFF, true);
            }
            if (rv < 1f) plug(g, x, y, cr, 1f - rv);
        }
    }

    /** Energy conduit from the selected chamber into the hub, with a pulse running inward. */
    private void feedLine(GuiGraphics g, float x, float y, float angle, float cr, float energy) {
        float startR = chamberRing() - cr, endR = hubRadius() * 1.05f;
        float x0 = GuiDraw.px(cx, angle, startR), y0 = GuiDraw.py(cy, angle, startR);
        float x1 = GuiDraw.px(cx, angle, endR), y1 = GuiDraw.py(cy, angle, endR);
        float u = GuiDraw.unit();
        GuiDraw.pixelLine(g, x0, y0, x1, y1, 3f * u, GuiDraw.argb(0.25f * energy, CYAN), GuiDraw.argb(0.45f * energy, CYAN), true);
        GuiDraw.pixelLine(g, x0, y0, x1, y1, u, GuiDraw.argb(0.8f * energy, 0xCFFBFF), GuiDraw.argb(energy, 0xFFFFFF), true);
        // The pulse hops along the conduit in 6 discrete positions (block by block).
        float t = (float) Math.floor((time * 1.6f) % 1f * 6f) / 6f;
        GuiDraw.blockGlow(g, Mth.lerp(t, x0, x1), Mth.lerp(t, y0, y1), cr * 0.35f, 0xFFFFFF, 0.8f * energy);
    }

    /** Sealed chamber cap: no destination loaded. */
    private void plug(GuiGraphics g, float x, float y, float cr, float alpha) {
        int top = GuiDraw.fade(0xFF2E343C, alpha), edge = GuiDraw.fade(0xFF15181D, alpha);
        GuiDraw.pixelDisc(g, x, y, cr * 0.72f, top, edge, false);
        int slit = GuiDraw.fade(0xFF07090B, alpha);
        float s = cr * 0.45f;
        GuiDraw.pixelLine(g, x - s, y - s, x + s, y + s, 1f, slit, slit, false);
        GuiDraw.pixelLine(g, x - s, y + s, x + s, y - s, 1f, slit, slit, false);
        rivet(g, x, y, GuiDraw.fade(0xFF59616C, alpha), GuiDraw.fade(0xFF2A2F37, alpha));
    }

    private void glyph(GuiGraphics g, float x, float y, float s, String id, int icon, int color, float alpha, boolean hot) {
        GlyphFactory.Glyph glyph = GlyphFactory.of(id);
        int solid = GuiDraw.argb(alpha, hot ? GuiDraw.lerpColor(0xFF000000 | color, 0xFFFFFFFF, 0.5f) : color);
        int glow = GuiDraw.argb(alpha * (hot ? 0.16f : 0.09f), color);
        // Pixel-art station icon centred on the chamber; the rim notches stay as a per-station signature.
        StationIcons.draw(g, x, y, s * 2.3f, icon, solid, glow);
        for (int i = 0; i < 6; i++) {
            if ((glyph.notches() >> i & 1) == 0) continue;
            float a = RadialLayout.TWO_PI * i / 6f;
            GuiDraw.pixelRing(g, x, y, s * 1.35f, s * 1.55f, a - 0.18f, a + 0.18f, solid, solid, false);
        }
    }

    private void hub(GuiGraphics g, float progress, float dive) {
        float hubR = hubRadius();
        GuiDraw.pixelLitRing(g, cx, cy, hubR, R * 0.42f, 0xFF0F1215, 0xFF5C6470, LIGHT + Mth.PI, 2f);
        GuiDraw.pixelRing(g, cx, cy, R * 0.42f, R * 0.445f, 0, Mth.TWO_PI, 0xFFB9BDB7, CERAMIC, false);
        for (int i = 0; i < 8; i++) {
            float a = RadialLayout.TWO_PI * i / 8f;
            GuiDraw.pixelRing(g, cx, cy, R * 0.42f, R * 0.445f, a - 0.04f, a + 0.04f, GRAPHITE_1, GRAPHITE_1, false);
        }
        GuiDraw.pixelDisc(g, cx, cy, hubR, 0xFF020304, 0xFF07090B, false);

        float orbR = R * 0.27f;
        if (dive <= 0f) {
            float intensity = 0.3f;
            if (selected >= 0) intensity = 0.6f + 0.12f * Mth.sin(time * 3.2f);
            if (hoverCore) intensity += 0.2f;
            if (charge != null) intensity = 0.7f + 0.3f * progress;
            if (state == State.FAULT) intensity = 0.15f;
            intensity *= DiveFx.smooth(0.85f, 1.3f, time);
            int tint = state == State.FAULT ? RED : CYAN;
            if (selected >= 0 && state != State.FAULT) {
                tint = GuiDraw.lerpColor(0xFF000000 | CYAN, 0xFF000000 | dimensionColor(entries.get(selected).dimension()), 0.25f) & 0xFFFFFF;
            }
            GuiDraw.blockGlow(g, cx, cy, hubR * 1.05f, tint, 0.25f * intensity);
            if (ModShaders.ready()) {
                ShaderQuads.square(g, ModShaders.orb(), ShaderQuads.Blend.ALPHA, cx, cy, orbR / DiveFx.ORB_R,
                        ShaderQuads.argb(Mth.clamp(intensity, 0f, 1f), tint));
            } else {
                GuiDraw.pixelDisc(g, cx, cy, orbR, 0xFFFFFFFF, 0xFF000000 | tint, false);
            }
        }
        iris(g, hubR, notched(irisOpen, 6));
        // Energy arcs around the hub when armed or charging: they advance in 1/16-turn notches.
        if ((selected >= 0 || charge != null) && state != State.FAULT) {
            float speed = charge != null ? 1.5f + progress * 10f : 0.8f;
            float energy = charge != null ? 0.6f + 0.4f * progress : 0.35f;
            float notch = RadialLayout.TWO_PI / 32f;
            for (int i = 0; i < 3; i++) {
                float a = (float) Math.floor((time * speed + RadialLayout.TWO_PI * i / 3f) / notch) * notch;
                GuiDraw.pixelRing(g, cx, cy, R * 0.455f, R * 0.475f, a, a + 1.1f, GuiDraw.argb(energy, CYAN), GuiDraw.argb(energy * 0.2f, CYAN), true);
            }
        }
    }

    /** Eight overlapping blades; the aperture radius follows {@code open}. */
    private void iris(GuiGraphics g, float hubR, float open) {
        float rIn = hubR * (0.05f + 0.91f * Mth.clamp(open, 0f, 1f));
        float twist = 0.65f;
        float base = open * 0.6f;
        for (int i = 0; i < 8; i++) {
            float t = RadialLayout.STEP * i + base;
            float o0x = GuiDraw.px(cx, t, hubR), o0y = GuiDraw.py(cy, t, hubR);
            float o1x = GuiDraw.px(cx, t + RadialLayout.STEP * 1.7f, hubR), o1y = GuiDraw.py(cy, t + RadialLayout.STEP * 1.7f, hubR);
            float i1x = GuiDraw.px(cx, t + RadialLayout.STEP + twist, rIn), i1y = GuiDraw.py(cy, t + RadialLayout.STEP + twist, rIn);
            float i0x = GuiDraw.px(cx, t + twist, rIn), i0y = GuiDraw.py(cy, t + twist, rIn);
            int shade = i % 2 == 0 ? 0xFF2C323A : 0xFF262B32;
            GuiDraw.pixelQuad(g, new float[]{o0x, o0y, o1x, o1y, i1x, i1y, i0x, i0y}, cx, cy, rIn, hubR, 0xFF4A525D, shade);
            GuiDraw.pixelLine(g, i0x, i0y, o0x, o0y, 1f, 0xFF07090B, 0xFF07090B, false);
            GuiDraw.pixelLine(g, i0x, i0y, i1x, i1y, 1f, 0xFF7A8490, 0xFF5C6470, false);
        }
        GuiDraw.pixelRing(g, cx, cy, hubR * 0.985f, hubR * 1.02f, 0xFF07090B);
    }

    private void labels(GuiGraphics g, float rot, float alpha) {
        if (alpha < 0.05f) return;
        for (int slot = 0; slot < RadialLayout.CHAMBERS; slot++) {
            int index = RadialLayout.entryAt(page, slot, entries.size());
            if (index < 0) continue;
            float lt = Mth.clamp((time - 0.7f - slot * 0.055f) / 0.3f, 0f, 1f);
            if (lt <= 0f) continue;
            StationEntry e = entries.get(index);
            String name = e.name();
            if (name.length() > 16) name = name.substring(0, 15) + "…";
            name = name.substring(0, Math.max(0, Math.min(name.length(), Math.round(name.length() * lt))));
            float a = RadialLayout.slotAngle(slot) + rot;
            float s = Mth.sin(a), c = Mth.cos(a);
            float lx = cx + s * R * 1.27f, ly = cy - c * R * 1.25f - 4;
            int w = font.width(name);
            int x;
            if (Math.abs(s) < 0.35f) x = (int) (lx - w / 2f);
            else x = s > 0 ? (int) lx : (int) (lx - w);
            int color = index == selected ? 0xFFFFFFFF : !selectable(e) ? 0xFF5A6470 : e.isPublic() ? 0xFF000000 | GOLD : 0xFFC8D0D8;
            if (index == selected) textShadow(g, name, x, (int) ly, color, alpha);
            else text(g, name, x, (int) ly, color, alpha);
            // Tiny leader tick toward the chamber.
            int lead = GuiDraw.argb(0.4f * alpha, index == selected ? CYAN : 0xA8B2BE);
            GuiDraw.pixelLine(g, cx + s * R * 1.205f, cy - c * R * 1.205f, cx + s * R * 1.235f, cy - c * R * 1.235f, 1f, lead, lead, false);
        }
    }

    private void header(GuiGraphics g, float alpha) {
        String brand = "RMIR";
        textShadow(g, brand, 10, 9, 0xFF000000 | CYAN, alpha);
        String src = " // " + payload.sourceName().toUpperCase(Locale.ROOT);
        text(g, src, 10 + font.width(brand), 9, TEXT, alpha);
        float boot = DiveFx.easeOutCubic(time / 0.5f);
        // Segmented underline: blocks with gaps, fading in steps.
        float lineEnd = 10 + 150 * boot;
        for (int i = 0; 10 + i * 6 < lineEnd; i++) {
            float x0 = 10 + i * 6, x1 = Math.min(lineEnd, x0 + 4);
            float k = 1f - GuiDraw.levels(i / 25f, 4);
            GuiDraw.rect(g, x0, 20, x1, 22, GuiDraw.argb(0.7f * alpha * k, CYAN), GuiDraw.argb(0.7f * alpha * k, CYAN));
        }
        text(g, "DTIR.INTRA  v1.0  //  " + String.format(Locale.ROOT, "%02d", entries.size()) + " NODES", 10, 25, TEXT_DIM, alpha * 0.8f);
        if (combatTicks > 0 && ((int) (time * 3f)) % 2 == 0) {
            String warn = Component.translatable("screen.nexusgate.interference", (combatTicks + 19) / 20).getString();
            text(g, warn, (int) (cx - font.width(warn) / 2f), (int) (cy - R * 1.24f - 12), 0xFF000000 | RED, alpha);
        }
        int pages = RadialLayout.pages(entries.size());
        if (pages > 1) {
            String p = Component.translatable("screen.nexusgate.page", page + 1, pages).getString();
            text(g, p, (int) (cx - font.width(p) / 2f), (int) (cy + R * 1.24f) - 10, TEXT_DIM, alpha);
        }
    }

    // ---------------------------------------------------------------- logo picker

    private static final int PICK_COLS = 4, PICK_CELL = 20, PICK_PAD = 6, PICK_TITLE = 12;

    /** Picker panel box {x0, y0, x1, y1}: under the top buttons, right-aligned with the logo button. */
    private float[] pickerBox() {
        int rows = (StationIcons.COUNT + PICK_COLS - 1) / PICK_COLS;
        float w = PICK_COLS * PICK_CELL + PICK_PAD * 2, h = PICK_TITLE + rows * PICK_CELL + PICK_PAD * 2;
        float right = logoButton != null ? logoButton.getX() + logoButton.getWidth() : width - 8;
        float x0 = Math.max(4, right - w);
        float y0 = 28;
        return new float[]{x0, y0, x0 + w, y0 + h};
    }

    private boolean pickerContains(double mx, double my) {
        float[] b = pickerBox();
        return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    private int pickerCellAt(double mx, double my) {
        float[] b = pickerBox();
        float gx = b[0] + PICK_PAD, gy = b[1] + PICK_PAD + PICK_TITLE;
        if (mx < gx || my < gy) return -1;
        int c = (int) Math.floor((mx - gx) / PICK_CELL), r = (int) Math.floor((my - gy) / PICK_CELL);
        if (c >= PICK_COLS) return -1;
        int i = r * PICK_COLS + c;
        return i < StationIcons.COUNT ? i : -1;
    }

    private void chooseIcon(int icon) {
        iconPickerOpen = false;
        GateSounds.index();
        if (icon == sourceIcon) return;
        sourceIcon = icon;
        PacketDistributor.sendToServer(new SetIconPayload(UUID.fromString(payload.sourceId()), icon));
    }

    private void iconPicker(GuiGraphics g) {
        GuiDraw.setUnit(1f);
        float[] b = pickerBox();
        GuiDraw.steppedPanel(g, b[0], b[1], b[2], b[3], 5f, 0xF0141920, 0xF00B0E12, 0xC0000000 | CYAN);
        int tx = (int) b[0] + PICK_PAD + 2, ty = (int) b[1] + PICK_PAD;
        text(g, Component.translatable("screen.nexusgate.logo").getString().toUpperCase(Locale.ROOT), tx, ty, TEXT_DIM, 1f);
        int hover = pickerCellAt(mouseX0, mouseY0);
        if (hover >= 0) {
            String name = Component.translatable(StationIcons.key(hover)).getString();
            text(g, name, (int) b[2] - PICK_PAD - 2 - font.width(name), ty, 0xFF000000 | CYAN, 1f);
        }
        float gx = b[0] + PICK_PAD, gy = b[1] + PICK_PAD + PICK_TITLE;
        for (int i = 0; i < StationIcons.COUNT; i++) {
            int x0 = (int) (gx + (i % PICK_COLS) * PICK_CELL), y0 = (int) (gy + (i / PICK_COLS) * PICK_CELL);
            int x1 = x0 + PICK_CELL - 2, y1 = y0 + PICK_CELL - 2;
            boolean current = i == sourceIcon, hot = i == hover;
            g.fill(x0, y0, x1, y1, hot ? 0x5033F0FF : 0x60000000);
            if (current || hot) {
                int rim = current ? 0xFF000000 | AMBER : 0xFF000000 | CYAN;
                g.fill(x0, y0, x1, y0 + 1, rim);
                g.fill(x0, y1 - 1, x1, y1, rim);
                g.fill(x0, y0, x0 + 1, y1, rim);
                g.fill(x1 - 1, y0, x1, y1, rim);
            }
            int color = hot ? 0xFFFFFFFF : current ? 0xFF000000 | AMBER : 0xFFC8D0D8;
            StationIcons.draw(g, (x0 + x1) / 2f, (y0 + y1) / 2f, 9, i, color, 0);
        }
    }

    private boolean overHintClose(double mx, double my) {
        return hintCloseX0 >= 0 && mx >= hintCloseX0 && mx < hintCloseX1 && my >= hintCloseY0 && my < hintCloseY1;
    }

    private void readout(GuiGraphics g, float progress, float alpha) {
        hintCloseX0 = -1;
        boolean hint = selected < 0 && !entries.isEmpty();
        if (hint && !NexusClientConfig.showSelectHint()) return;
        int w = readoutWidth();
        float x0 = cx - w / 2f, y0 = cy + R * 1.24f, x1 = cx + w / 2f, y1 = y0 + 34;
        GuiDraw.steppedPanel(g, x0, y0, x1, y1, 6f, GuiDraw.fade(0xE0141920, alpha), GuiDraw.fade(0xE00B0E12, alpha),
                GuiDraw.argb(0.55f * alpha, state == State.FAULT ? RED : CYAN));
        // Status latch: a column of three emissive blocks.
        float lx = Math.round(x0) + 6, lh = (y1 - y0 - 8) / 3f;
        for (int i = 0; i < 3; i++) {
            float k = 1f - i * 0.35f;
            int c = GuiDraw.argb(alpha * k, CYAN);
            GuiDraw.rect(g, lx, Math.round(y0 + 4 + i * lh), lx + 3, Math.round(y0 + 4 + (i + 1) * lh) - 1, c, c);
        }
        int tx = (int) x0 + 14;
        int ty1 = (int) y0 + 7, ty2 = (int) y0 + 20;

        if (entries.isEmpty()) {
            text(g, Component.translatable("screen.nexusgate.empty").getString(), tx, ty1, TEXT_DIM, alpha);
            return;
        }
        if (selected < 0) {
            float pulse = 0.55f + 0.45f * Mth.sin(time * 3f);
            text(g, Component.translatable("screen.nexusgate.select_destination").getString(), tx, ty1, 0xFF000000 | CYAN, alpha * pulse);
            text(g, Component.translatable("screen.nexusgate.select_hint").getString(), tx, ty2, TEXT_DIM, alpha);
            // Close button: a small pixel "x" in the top-right corner of the panel.
            hintCloseX0 = Math.round(x1) - 15;
            hintCloseY0 = Math.round(y0) + 3;
            hintCloseX1 = hintCloseX0 + 11;
            hintCloseY1 = hintCloseY0 + 11;
            int xc = GuiDraw.argb(alpha, overHintClose(mouseX0, mouseY0) ? CYAN : 0x7A8490);
            for (int i = 0; i < 5; i++) {
                float px = hintCloseX0 + 3 + i, py = hintCloseY0 + 3 + i, qy = hintCloseY0 + 7 - i;
                GuiDraw.rect(g, px, py, px + 1, py + 1, xc, xc);
                GuiDraw.rect(g, px, qy, px + 1, qy + 1, xc, xc);
            }
            return;
        }
        StationEntry e = entries.get(selected);
        String title = String.format(Locale.ROOT, "CH-%02d // ", RadialLayout.slotOf(selected) + 1) + e.name().toUpperCase(Locale.ROOT);
        if (e.isPublic()) title += "  [" + Component.translatable("screen.nexusgate.public_short").getString() + "]";
        textShadow(g, typed(title), tx, ty1, e.isPublic() ? 0xFF000000 | GOLD : 0xFFFFFFFF, alpha);

        String dist = e.distance() < 0 ? Component.translatable("screen.nexusgate.other_dimension").getString()
                : Component.translatable("screen.nexusgate.distance", Math.round(e.distance())).getString();
        String cost = e.xpCost() == 0 ? Component.translatable("screen.nexusgate.free").getString()
                : Component.translatable("screen.nexusgate.xp", e.xpCost()).getString();
        int x = tx;
        x = field(g, x, ty2, Component.translatable("screen.nexusgate.dimension").getString(), dimensionLabel(e.dimension()), 0xFF000000 | dimensionColor(e.dimension()), alpha);
        x = field(g, x, ty2, Component.translatable("screen.nexusgate.distance_label").getString(), dist, 0xFF7FF6FF, alpha);
        field(g, x, ty2, Component.translatable("screen.nexusgate.cost_label").getString(), cost, e.affordable() ? 0xFF7FF6FF : 0xFF000000 | RED, alpha);

        // Status tag on the right.
        String status;
        int color;
        if (state == State.FAULT) {
            status = fault != null ? fault.getString() : Component.translatable("msg.nexusgate.interrupted").getString();
            color = 0xFF000000 | RED;
        } else if (state == State.CHARGING) {
            status = Component.translatable("screen.nexusgate.status.charging", Math.round(progress * 100)).getString();
            color = ((int) (time * 4)) % 2 == 0 ? 0xFF000000 | CYAN : 0xFFCFFBFF;
        } else if (state == State.ENGAGING) {
            status = Component.translatable("screen.nexusgate.status.awaiting").getString();
            color = 0xFF000000 | CYAN;
        } else if (combatTicks > 0) {
            status = Component.translatable("screen.nexusgate.status.interference").getString();
            color = 0xFF000000 | RED;
        } else if (!e.affordable()) {
            status = Component.translatable("screen.nexusgate.status.no_xp").getString();
            color = 0xFF000000 | AMBER;
        } else {
            status = Component.translatable(hoverCore ? "screen.nexusgate.engage_now" : "screen.nexusgate.engage").getString();
            float pulse = hoverCore ? 1f : 0.6f + 0.4f * Mth.sin(time * 4f);
            color = GuiDraw.lerpColor(0xFF1E8FA0, 0xFFCFFBFF, pulse);
        }
        status = font.plainSubstrByWidth(status, w / 2);
        text(g, status, (int) x1 - 10 - font.width(status), state == State.FAULT ? ty2 : ty1, color, alpha);
        if (state == State.CHARGING) {
            // Segmented charge bar: 15 blocks that light one by one (like the XP bar).
            int blocks = 15;
            float bx0 = Math.round(x1 - 10 - 90);
            int lit = (int) Math.floor(progress * blocks);
            for (int i = 0; i < blocks; i++) {
                float b0 = bx0 + i * 6, b1 = b0 + 5;
                int c = i < lit ? GuiDraw.argb(alpha, i == lit - 1 ? 0xCFFBFF : CYAN) : GuiDraw.argb(0.4f * alpha, 0x0B2A33);
                GuiDraw.rect(g, b0, ty2 + 2, b1, ty2 + 6, c, c);
            }
        }
    }

    private int field(GuiGraphics g, int x, int y, String label, String value, int color, float alpha) {
        text(g, label, x, y, TEXT_DIM, alpha);
        x += font.width(label) + 4;
        text(g, value, x, y, color, alpha);
        return x + font.width(value) + 12;
    }

    private String typed(String s) {
        int n = Mth.clamp((int) (readoutTime * 70f), 0, s.length());
        return n < s.length() ? s.substring(0, n) + "_" : s;
    }

    private void text(GuiGraphics g, String s, int x, int y, int argb, float alpha) {
        if (alpha < 0.05f || s.isEmpty()) return;
        g.drawString(font, s, x, y, GuiDraw.fade(argb, alpha), false);
    }

    /** Text with the vanilla drop shadow, for titles and the selected destination. */
    private void textShadow(GuiGraphics g, String s, int x, int y, int argb, float alpha) {
        if (alpha < 0.05f || s.isEmpty()) return;
        g.drawString(font, s, x, y, GuiDraw.fade(argb, alpha), true);
    }

    // ---------------------------------------------------------------- helpers

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    static int dimensionColor(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        String path = rl == null ? id : rl.getPath();
        return switch (path) {
            case "overworld" -> CYAN;
            case "the_nether" -> AMBER;
            case "the_end" -> 0xB58CFF;
            default -> 0x8CFFC8;
        };
    }

    static String dimensionLabel(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        String path = rl == null ? id : rl.getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path.replace('_', ' ').toUpperCase(Locale.ROOT);
        };
    }
}
