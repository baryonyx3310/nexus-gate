package com.nexusgate.item;

import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.core.LocatorArrow;
import com.nexusgate.registry.ModComponents;
import com.nexusgate.station.StationNetwork;
import com.nexusgate.station.StationRecord;
import com.nexusgate.teleport.ChargeManager;
import com.nexusgate.teleport.StationService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Points at a linked station (direction + distance on the action bar). Never teleports. */
public class LocatorItem extends Item {
    private static final int UPDATE_INTERVAL = 10;

    public LocatorItem(Properties properties) {
        super(properties);
    }

    /** Sneak + use on a known station links the locator to it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof TeleportStationBlock) || context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
            BlockPos lower = TeleportStationBlock.lowerPos(state, context.getClickedPos());
            Optional<StationRecord> record = StationService.stationAt(serverLevel, lower);
            if (record.isPresent() && StationService.isKnown(player, record.get())) {
                context.getItemInHand().set(ModComponents.LINKED_STATION.get(), record.get().id());
                player.displayClientMessage(Component.translatable("msg.nexusgate.locator_linked", record.get().name())
                        .withStyle(ChatFormatting.AQUA), true);
            } else {
                player.displayClientMessage(Component.translatable("msg.nexusgate.target_unknown").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || level.getGameTime() % UPDATE_INTERVAL != 0) return;
        boolean held = selected || player.getOffhandItem() == stack;
        UUID linked = stack.get(ModComponents.LINKED_STATION.get());
        if (!held || linked == null || ChargeManager.isCharging(player.getUUID())) return;

        Optional<StationRecord> record = StationNetwork.get(player.server).get(linked);
        if (record.isEmpty()) {
            stack.remove(ModComponents.LINKED_STATION.get());
            player.displayClientMessage(Component.translatable("msg.nexusgate.signal_lost").withStyle(ChatFormatting.RED), true);
            return;
        }
        StationRecord r = record.get();
        if (!r.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("msg.nexusgate.locator_other_dimension", r.name())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        double dx = r.pos().getX() + 0.5 - player.getX();
        double dz = r.pos().getZ() + 0.5 - player.getZ();
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        String arrow = LocatorArrow.arrow(dx, dz, player.getYRot());
        player.displayClientMessage(Component.translatable("msg.nexusgate.locator_direction", r.name(), distance, arrow)
                .withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean linked = stack.has(ModComponents.LINKED_STATION.get());
        tooltip.add(Component.translatable(linked ? "tooltip.nexusgate.locator_linked" : "tooltip.nexusgate.locator_unlinked")
                .withStyle(linked ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.nexusgate.locator_info").withStyle(ChatFormatting.DARK_GRAY));
    }
}
