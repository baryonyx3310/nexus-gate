package com.nexusgate.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.config.NexusConfig;
import com.nexusgate.teleport.StationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /nexusgate public <true|false> — marks the station the admin is looking at. */
public final class NexusCommand {
    private static final double REACH = 6.0;

    private NexusCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nexusgate")
                .then(Commands.literal("public")
                        .requires(src -> src.hasPermission(NexusConfig.publicPermissionLevel()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(NexusCommand::setPublic))));
    }

    private static int setPublic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean value = BoolArgumentType.getBool(ctx, "value");
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(blockHit.getBlockPos());
            if (state.getBlock() instanceof TeleportStationBlock) {
                BlockPos lower = TeleportStationBlock.lowerPos(state, blockHit.getBlockPos());
                var record = StationService.stationAt(level, lower);
                if (record.isPresent()) {
                    StationService.applyPublic(player, record.get(), value);
                    return 1;
                }
            }
        }
        ctx.getSource().sendFailure(Component.translatable("msg.nexusgate.no_station_in_sight"));
        return 0;
    }
}
