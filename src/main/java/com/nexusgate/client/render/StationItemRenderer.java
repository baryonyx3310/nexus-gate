package com.nexusgate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Item form of the station: both halves of the housing plus the live drum, scaled to one block. */
public class StationItemRenderer extends BlockEntityWithoutLevelRenderer {
    public StationItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        BlockState lower = ModBlocks.TELEPORT_STATION.get().defaultBlockState()
                .setValue(TeleportStationBlock.FACING, Direction.NORTH)
                .setValue(TeleportStationBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = lower.setValue(TeleportStationBlock.HALF, DoubleBlockHalf.UPPER);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();

        pose.pushPose();
        pose.translate(0.25, 0, 0.25);
        pose.scale(0.5f, 0.5f, 0.5f);
        dispatcher.renderSingleBlock(lower, pose, buffers, light, overlay);
        pose.pushPose();
        pose.translate(0, 1, 0);
        dispatcher.renderSingleBlock(upper, pose, buffers, light, overlay);
        pose.popPose();
        float seconds = Minecraft.getInstance().level != null
                ? (Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) / 20f
                : 0f;
        StationRenderer.draw(pose, buffers, Direction.NORTH, seconds, -1f, -1f, light);
        StationRenderer.siren(pose, buffers, Direction.NORTH, false, seconds);
        pose.popPose();
    }
}
