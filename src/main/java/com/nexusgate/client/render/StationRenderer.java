package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nexusgate.NexusGate;
import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.block.TeleportStationBlockEntity;
import com.nexusgate.client.fx.TeleportFxController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * The moving heart of the station, built from voxels: a revolver drum (8 cartridge chambers) turning inside
 * a stepped stator ring, an 8-plate iris over the plasma core, and energy rings while charging. Drawn on the
 * lower block, spanning both.
 * <p>
 * Every round part is rasterised on the model-pixel grid (stepped "Minecraft circles") and parts placed
 * around the axis are rotated in whole 45-degree steps, like rotated elements of a vanilla model.
 * <p>
 * Idle: the drum indexes 45 degrees every few seconds and locks (mechanical). Charging: the drum spins up for
 * two full turns, the chamber lenses light in cascade, the iris opens in mechanical steps and the core
 * intensifies. After a departure the core flashes and the iris snaps shut.
 */
public class StationRenderer implements BlockEntityRenderer<TeleportStationBlockEntity> {
    private static final ResourceLocation METAL = tex("drum_metal");
    private static final ResourceLocation DARK = tex("drum_dark");
    private static final ResourceLocation FRAME = tex("frame_graphite");
    private static final ResourceLocation CERAMIC = tex("armor_ceramic");

    // Geometry in model pixels (drum axis at x=8, y=16; front of the machine faces -z).
    static final float AXIS_Y = 16f;
    private static final float STATOR_R0 = 6.0f, STATOR_R1 = 7.2f, STATOR_Z0 = 3.5f, STATOR_Z1 = 9.0f;
    private static final float ROTOR_R = 6.0f, ROTOR_Z0 = 4.2f, ROTOR_Z1 = 8.5f;
    private static final float HUB_R0 = 1.6f, HUB_R1 = 2.4f, HUB_Z = 3.7f, BORE_Z = 7.5f;
    private static final float CH_RING = 3.95f, CH_HALF = 1.5f, CH_DEPTH = 0.6f;
    private static final float ORB_Z = 5.4f, ORB_R = 1.45f;
    private static final float STEP = Mth.TWO_PI / 8f;
    /** Mechanical positions of the iris between closed and open. */
    private static final int IRIS_STEPS = 6;

    private static final int CYAN = 0x33F0FF;
    private static final int AMBER = 0xFFB347;

    public StationRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "block/" + name);
    }

    private static TextureAtlasSprite sprite(ResourceLocation rl) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(rl);
    }

    @Override
    public void render(TeleportStationBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Direction facing = be.getBlockState().hasProperty(TeleportStationBlock.FACING)
                ? be.getBlockState().getValue(TeleportStationBlock.FACING) : Direction.NORTH;
        BlockPos pos = be.getBlockPos();
        TeleportFxController.Charge charge = TeleportFxController.chargeAt(pos);
        float progress = charge != null ? charge.progress(partialTick) : -1f;
        float since = TeleportFxController.sinceDeparture(pos, partialTick);
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
        float seconds = (gameTime + partialTick) / 20f + (Math.floorMod(pos.hashCode(), 997) / 997f) * 7f;
        // Light from the upper half, which is usually brighter than the half-buried lower block.
        int lit = be.getLevel() != null
                ? net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), pos.above())
                : light;
        draw(pose, buffers, facing, seconds, progress, since, lit);
        nameplate(pose, buffers, facing, pos, be.getDisplayName(), seconds);
        siren(pose, buffers, facing, be.isArrivalBlocked() || com.nexusgate.client.ClientCombatLock.isLocked(), seconds);
    }

    // Siren on the antenna fin tip, in model pixels (front of the machine faces -z).
    private static final float SIREN_X0 = 12f, SIREN_Y0 = 31.5f, SIREN_Z0 = 11f, SIREN_X1 = 13f, SIREN_Y1 = 32f, SIREN_Z1 = 12f;
    private static final int RED = 0xFF2030, RED_DIM = 0x5A1015;
    private static final ResourceLocation WHITE = tex("white");

    /**
     * The antenna siren: steady amber normally; flashing red (with a soft halo) when the station cannot receive
     * travellers or the local player is locked out of teleporting.
     */
    static void siren(PoseStack pose, MultiBufferSource buffers, Direction facing, boolean alarm, float seconds) {
        boolean on = alarm && ((int) Math.floor(seconds / 0.3f)) % 2 == 0;
        int rgb = !alarm ? AMBER : on ? RED : RED_DIM;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        TextureAtlasSprite white = sprite(WHITE);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        int c = 0xFF000000 | rgb;
        DrumMesh.box(solid, pose.last(), white, white, SIREN_X0, SIREN_Y0, SIREN_Z0, SIREN_X1, SIREN_Y1, SIREN_Z1, c, c,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, DrumMesh.F_ALL);
        if (on) {
            float g = 0.25f;
            DrumMesh.glowBox(buffers.getBuffer(ModRenderTypes.EMISSIVE), pose.last(), SIREN_X0 - g, SIREN_Y0 - g, SIREN_Z0 - g,
                    SIREN_X1 + g, SIREN_Y1 + g, SIREN_Z1 + g, argb(0.45f, RED));
        }
        pose.popPose();
    }

    // Crown screen (static model element at x 3.5..12.5, y 26..28, front face z 2.6), in model pixels.
    private static final float SCREEN_CX = 8f, SCREEN_CY = 27f, SCREEN_Z = 2.58f, SCREEN_W = 8f;
    /** Model pixels per font pixel: an 8px-tall font line becomes 1.5 model pixels. */
    private static final float TEXT_SCALE = 1.5f / 8f;
    private static final String TICKER_GAP = "   //   ";
    private static final double NAMEPLATE_RANGE_SQ = 16 * 16;

    /**
     * The station name on the small screen in the crown, always full-bright. Names that fit sit centred;
     * longer ones run as a ticker, advancing one character every quarter second.
     */
    private static void nameplate(PoseStack pose, MultiBufferSource buffers, Direction facing, BlockPos pos, String name,
                                  float seconds) {
        if (name == null || name.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5)
                > NAMEPLATE_RANGE_SQ) return;
        net.minecraft.client.gui.Font font = mc.font;
        int window = (int) (SCREEN_W / TEXT_SCALE);
        String shown;
        float x;
        if (font.width(name) <= window) {
            shown = name;
            x = -font.width(name) / 2f;
        } else {
            String loop = name + TICKER_GAP;
            int step = Math.floorMod((int) Math.floor(seconds * 4f), loop.length());
            shown = font.plainSubstrByWidth(loop.substring(step) + loop, window);
            x = -window / 2f;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(SCREEN_CX / 16f, SCREEN_CY / 16f, SCREEN_Z / 16f);
        // Face the front (-z) and flip Y for font space.
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        float s = TEXT_SCALE / 16f;
        pose.scale(s, -s, s);
        font.drawInBatch(shown, x, -3.5f, 0xFF7FF6FF, false, pose.last().pose(), buffers,
                net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Shared by the block entity and the item renderer. {@code progress} < 0 when idle. */
    static void draw(PoseStack pose, MultiBufferSource buffers, Direction facing, float seconds, float progress,
                     float sinceDepart, int light) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(8, AXIS_Y, 0);

        boolean charging = progress >= 0f;
        float p = Math.max(0f, progress);
        float flash = sinceDepart >= 0 ? (float) Math.exp(-sinceDepart / 6f) : 0f;

        // Drum rotation: mechanical indexing when idle, two indexed turns while charging.
        float period = 3.2f;
        float cycle = seconds / period;
        float idx = (float) Math.floor(cycle);
        float k = Mth.clamp((cycle - idx) * period / 0.55f, 0f, 1f);
        float settle = k < 1f ? k * k * (3f - 2f * k) + 0.08f * Mth.sin(k * Mth.PI) : 1f;
        float rotation = STEP * (idx + settle);
        if (charging) rotation += Mth.TWO_PI * 2f * smooth(0.02f, 0.8f, p);

        float iris = charging ? Mth.lerp(smooth(0f, 0.6f, p), 0.4f, 1f) : 0.22f + 0.04f * Mth.sin(seconds * 1.3f);
        if (flash > 0.01f) iris = Math.max(iris, flash);
        iris = stepped(iris, IRIS_STEPS);

        PoseStack.Pose last = pose.last();
        TextureAtlasSprite metal = sprite(METAL), dark = sprite(DARK), frame = sprite(FRAME), ceramic = sprite(CERAMIC);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        // ---- stator: stepped voxel ring integrated in the housing
        DrumMesh.pixelRing(solid, last, frame, dark, STATOR_R0, STATOR_R1, STATOR_Z0, STATOR_Z1, 1f,
                0xFF9AA0A8, 0xFF707680, light);
        // Ceramic clamps at the diagonals: 2x2 blocks standing proud of the ring.
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * (2 * i + 1)));
            DrumMesh.box(solid, pose.last(), ceramic, ceramic, -1f, 5.6f, STATOR_Z0 - 0.5f, 1f, 7.6f, STATOR_Z0 + 1.5f,
                    0xFFFFFFFF, 0xFFD0D0D0, light, DrumMesh.F_ALL & ~DrumMesh.F_BACK);
            pose.popPose();
        }

        // ---- rotor (the revolver cylinder), rotated as one rigid voxel part
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(-rotation));
        PoseStack.Pose rot = pose.last();
        DrumMesh.pixelRing(solid, rot, metal, metal, HUB_R1, ROTOR_R, ROTOR_Z0, ROTOR_Z1, 1f, 0xFFB8BEC6, 0xFF9098A0, light);
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * (i + 0.5f)));
            PoseStack.Pose f = pose.last();
            // Flute (dark notch) on the rim between chambers and a ceramic index tab.
            DrumMesh.box(solid, f, dark, dark, -0.5f, 5.0f, ROTOR_Z0 - 0.05f, 0.5f, 6.0f, ROTOR_Z0, 0xFF404448, 0xFF303438, light, DrumMesh.F_FRONT);
            DrumMesh.box(solid, f, ceramic, ceramic, -0.5f, 4.5f, ROTOR_Z0 - 0.3f, 0.5f, 5.0f, ROTOR_Z0, 0xFFFFFFFF, 0xFFD0D0D0, light,
                    DrumMesh.F_ALL & ~DrumMesh.F_BACK);
            pose.popPose();
        }
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * i));
            cartridge(solid, pose.last(), metal, dark, light);
            pose.popPose();
        }
        pose.popPose();

        // ---- hub, bore and iris housing (fixed): stepped collar around a dark bore
        DrumMesh.pixelRing(solid, last, frame, dark, HUB_R0, HUB_R1, HUB_Z, BORE_Z, 0.5f, 0xFFB0B6BE, 0xFF505660, light);
        DrumMesh.pixelRing(solid, last, dark, dark, -1f, HUB_R0, BORE_Z, BORE_Z + 0.5f, 0.5f, 0xFF202428, 0xFF202428, light);
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * (2 * i + 1)));
            DrumMesh.box(solid, pose.last(), ceramic, ceramic, -0.25f, 1.75f, HUB_Z - 0.3f, 0.25f, 2.25f, HUB_Z, 0xFFFFFFFF, 0xFFC0C0C0, light,
                    DrumMesh.F_ALL & ~DrumMesh.F_BACK);
            pose.popPose();
        }
        irisPlates(pose, solid, metal, iris, light);

        // ---- emissive: stator LEDs, chamber lenses
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.EMISSIVE);
        for (int i = 0; i < 8; i++) {
            int c;
            if (charging) c = p >= (i + 1) / 8f ? 0xFF000000 | CYAN : 0xFF0A3A44;
            else if (i == 0) c = 0xFF000000 | AMBER;
            else c = 0xFF0C3C46;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * i));
            DrumMesh.glowRect(glow, pose.last(), -1f, 6.25f, 1f, 6.95f, STATOR_Z0 - 0.03f, c);
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(-rotation));
        for (int i = 0; i < 8; i++) {
            float lit = charging ? smooth(i / 8f, (i + 1) / 8f, p) : 0.25f + 0.1f * Mth.sin(seconds * 2f + i);
            lit = stepped(lit, 4);
            int center = argb(1f, lerpColor(0x0B3440, 0xCFFBFF, lit));
            int edge = argb(1f, lerpColor(0x061A20, CYAN, lit));
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * i));
            PoseStack.Pose f = pose.last();
            // 2x2 lens: dim frame of light around a bright 1x1 core.
            DrumMesh.glowRect(glow, f, -1f, CH_RING - 1f, 1f, CH_RING + 1f, ROTOR_Z0 - 0.02f, edge);
            DrumMesh.glowRect(glow, f, -0.5f, CH_RING - 0.5f, 0.5f, CH_RING + 0.5f, ROTOR_Z0 - 0.04f, center);
            pose.popPose();
        }
        pose.popPose();
        // Core light leaking through the iris: two stepped halos.
        float coreI = charging ? 0.6f + 0.4f * p : 0.35f + 0.1f * Mth.sin(seconds * 2.4f);
        coreI = Math.max(coreI, flash);
        DrumMesh.glowPixelRing(glow, last, -1f, HUB_R0 * 1.6f, HUB_Z - 0.3f, 0.5f, argb(0.18f * coreI, CYAN));
        DrumMesh.glowPixelRing(glow, last, -1f, HUB_R0 * 1.15f, HUB_Z - 0.32f, 0.5f, argb(0.22f * coreI, CYAN));

        // ---- core orb (pixelated plasma shader). Faces the drum front; the shader shades it as a voxel sphere.
        VertexConsumer orb = buffers.getBuffer(ModRenderTypes.ORB);
        DrumMesh.faceQuad(orb, last, 0, 0, ORB_R / 0.62f, ORB_Z, argb(Mth.clamp(coreI, 0f, 1f), flash > 0.3f ? 0xCFFBFF : CYAN));

        // ---- energy rings while charging (energy_field shader, octagonal segments)
        if (charging || flash > 0.05f) {
            VertexConsumer field = buffers.getBuffer(ModRenderTypes.FIELD);
            float e = charging ? 0.35f + 0.65f * p : flash;
            float spin = seconds * (1.5f + p * 8f);
            DrumMesh.fieldRing(field, last, HUB_R1 + 0.5f, 0.5f, HUB_Z - 0.4f, spin, spin + Mth.TWO_PI * 0.75f, 24, argb(e, CYAN));
            DrumMesh.fieldRing(field, last, STATOR_R1 + 0.4f, 0.6f, STATOR_Z0 - 0.5f, -spin * 0.6f, -spin * 0.6f + Mth.TWO_PI, 48,
                    argb(e * smooth(0.3f, 1f, p + flash), CYAN));
        }
        pose.popPose();
    }

    /**
     * One chamber, in a frame rotated to its slot: a 3x3 cartridge standing proud of the rotor, built from four
     * rim blocks around a 2x2 dark cavity (the lens is drawn in the emissive pass).
     */
    private static void cartridge(VertexConsumer vc, PoseStack.Pose f, TextureAtlasSprite metal, TextureAtlasSprite dark, int light) {
        float x0 = -CH_HALF, x1 = CH_HALF, y0 = CH_RING - CH_HALF, y1 = CH_RING + CH_HALF;
        float zf = ROTOR_Z0 - CH_DEPTH, zb = ROTOR_Z0;
        int top = 0xFFD0D6DC, side = 0xFF8A929C;
        int faces = DrumMesh.F_ALL & ~DrumMesh.F_BACK;
        DrumMesh.box(vc, f, metal, metal, x0, y1 - 0.5f, zf, x1, y1, zb, top, side, light, faces);
        DrumMesh.box(vc, f, metal, metal, x0, y0, zf, x1, y0 + 0.5f, zb, top, side, light, faces);
        DrumMesh.box(vc, f, metal, metal, x0, y0 + 0.5f, zf, x0 + 0.5f, y1 - 0.5f, zb, top, side, light, faces);
        DrumMesh.box(vc, f, metal, metal, x1 - 0.5f, y0 + 0.5f, zf, x1, y1 - 0.5f, zb, top, side, light, faces);
        DrumMesh.box(vc, f, dark, dark, -1f, CH_RING - 1f, zb - 0.01f, 1f, CH_RING + 1f, zb, 0xFF30343A, 0xFF30343A, light, DrumMesh.F_FRONT);
    }

    /**
     * Eight rectangular plates sliding radially: their inner edges form an octagonal aperture that follows
     * {@code open}. Plates stay hidden under the hub collar when fully open.
     */
    private static void irisPlates(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, float open, int light) {
        float rOut = HUB_R0 + 0.35f;
        float rIn = HUB_R0 * (0.06f + 0.94f * Mth.clamp(open, 0f, 1f));
        float len = Math.max(0f, rOut - rIn);
        if (len <= 0.01f) return;
        float half = 0.75f;
        float base = open * STEP * 0.5f;
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(STEP * i + base));
            float z = HUB_Z + 0.05f + i * 0.012f;
            int c = i % 2 == 0 ? 0xFF8A929C : 0xFF747C86;
            DrumMesh.box(vc, pose.last(), s, s, -half, rIn, z, half, rIn + len, z + 0.01f, c, c, light, DrumMesh.F_FRONT);
            pose.popPose();
        }
    }

    /**
     * Quantises a 0..1 value into {@code steps} mechanical positions with a short eased move between them:
     * the part holds still, then snaps to the next notch.
     */
    static float stepped(float v, int steps) {
        float s = Mth.clamp(v, 0f, 1f) * steps;
        float k = (float) Math.floor(s);
        float f = smooth(0.65f, 1f, s - k);
        return Mth.clamp((k + f) / steps, 0f, 1f);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static int argb(float a, int rgb) {
        return (Math.round(Mth.clamp(a, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    static int lerpColor(int c0, int c1, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (c0 >> 16) & 255, (c1 >> 16) & 255);
        int g = (int) Mth.lerp(t, (c0 >> 8) & 255, (c1 >> 8) & 255);
        int b = (int) Mth.lerp(t, c0 & 255, c1 & 255);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean shouldRenderOffScreen(TeleportStationBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TeleportStationBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }
}
