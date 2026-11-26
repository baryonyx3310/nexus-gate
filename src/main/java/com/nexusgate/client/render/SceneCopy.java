package com.nexusgate.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusgate.NexusGate;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A copy of the frame rendered so far, so the space-time lens can refract the real background (it samples this
 * copy with displaced coordinates and draws the result back). Captured at most once per frame, and only while
 * a relocation is on screen.
 */
public final class SceneCopy {
    @Nullable private static TextureTarget target;
    private static boolean failed;

    private SceneCopy() {}

    /** Copies the main colour buffer. @return false if the copy is not available (then no lens is drawn). */
    public static boolean capture() {
        if (failed) return false;
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (target == null || target.width != main.width || target.height != main.height) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_NEAREST);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(false);
            return true;
        } catch (RuntimeException e) {
            failed = true;
            NexusGate.LOGGER.error("RMIR: scene copy unavailable, space-time lens disabled", e);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            return false;
        }
    }

    public static int textureId() {
        return target != null ? target.getColorTextureId() : 0;
    }
}
