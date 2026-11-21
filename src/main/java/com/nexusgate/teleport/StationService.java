package com.nexusgate.teleport;

import com.nexusgate.NexusGate;
import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.block.TeleportStationBlockEntity;
import com.nexusgate.core.Permissions;
import com.nexusgate.config.NexusConfig;
import com.nexusgate.core.CombatLock;
import com.nexusgate.core.StationEntry;
import com.nexusgate.core.StationSorter;
import com.nexusgate.core.XpCost;
import com.nexusgate.core.XpSettings;
import com.nexusgate.net.OpenRenamePayload;
import com.nexusgate.net.OpenSelectionPayload;
import com.nexusgate.net.XaeroWaypointPayload;
import com.nexusgate.station.ActivatedStations;
import com.nexusgate.station.StationNetwork;
import com.nexusgate.station.StationRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-side station lifecycle: placing, using, removing, renaming. */
public final class StationService {
    public static final int MAX_NAME_LENGTH = 32;

    private StationService() {}

    public static void onPlaced(ServerLevel level, BlockPos lowerPos, ServerPlayer placer) {
        StationRecord record = register(level, lowerPos, placer);
        ActivatedStations.activate(placer, record.id());
        PacketDistributor.sendToPlayer(placer,
                new OpenRenamePayload(record.id(), record.name(), true, lowerPos.getX(), lowerPos.getZ()));
    }

    public static InteractionResult onUse(ServerLevel level, BlockPos lowerPos, ServerPlayer player) {
        StationRecord record = stationAt(level, lowerPos).orElseGet(() -> register(level, lowerPos, player));
        boolean known = record.isPublic() || ActivatedStations.isActivated(player, record.id());
        if (!known) {
            ActivatedStations.activate(player, record.id());
            level.playSound(null, lowerPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
            player.displayClientMessage(Component.translatable("msg.nexusgate.synced", record.name()).withStyle(ChatFormatting.AQUA), true);
            PacketDistributor.sendToPlayer(player, new XaeroWaypointPayload(record.name(), lowerPos.getX(), lowerPos.getY(),
                    lowerPos.getZ(), level.dimension().location().toString()));
            return InteractionResult.SUCCESS;
        }
        openSelection(player, record);
        return InteractionResult.SUCCESS;
    }

    public static void onRemoved(ServerLevel level, BlockPos lowerPos) {
        stationAt(level, lowerPos).ifPresent(r -> StationNetwork.get(level.getServer()).remove(r.id()));
    }

    public static void rename(ServerPlayer player, UUID stationId, String rawName) {
        StationNetwork network = StationNetwork.get(player.server);
        Optional<StationRecord> found = network.get(stationId);
        if (found.isEmpty()) return;
        StationRecord record = found.get();
        if (!canRename(player, record)) {
            player.displayClientMessage(Component.translatable("msg.nexusgate.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }
        String name = sanitizeName(rawName);
        if (name == null) {
            player.displayClientMessage(Component.translatable("msg.nexusgate.invalid_name").withStyle(ChatFormatting.RED), true);
            return;
        }
        network.update(record.withName(name));
        player.displayClientMessage(Component.translatable("msg.nexusgate.renamed", name).withStyle(ChatFormatting.AQUA), true);
    }

    /** Handles the OP-only toggle from the terminal; permission is re-checked here. */
    public static void setPublic(ServerPlayer player, UUID stationId, boolean value) {
        Optional<StationRecord> found = StationNetwork.get(player.server).get(stationId);
        if (found.isEmpty()) return;
        if (!canManagePublic(player)) {
            NexusGate.LOGGER.warn("Player {} tried to change public state of station {} without permission",
                    player.getGameProfile().getName(), stationId);
            player.displayClientMessage(Component.translatable("msg.nexusgate.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }
        applyPublic(player, found.get(), value);
    }

    public static void applyPublic(ServerPlayer player, StationRecord record, boolean value) {
        StationNetwork.get(player.server).update(record.withPublic(value));
        String key = value ? "msg.nexusgate.now_public" : "msg.nexusgate.now_private";
        player.displayClientMessage(Component.translatable(key, record.name()).withStyle(ChatFormatting.GOLD), true);
    }

    public static boolean canManagePublic(ServerPlayer player) {
        return Permissions.canSetPublic(permissionLevel(player), NexusConfig.publicPermissionLevel());
    }

    private static int permissionLevel(ServerPlayer player) {
        for (int level = 4; level > 0; level--) {
            if (player.hasPermissions(level)) return level;
        }
        return 0;
    }

    /** Public stations can only be broken by players allowed to manage them. */
    public static boolean mayBreak(ServerLevel level, BlockPos lowerPos, ServerPlayer player) {
        return stationAt(level, lowerPos).map(r -> !r.isPublic() || canManagePublic(player)).orElse(true);
    }

    public static boolean isPublicAt(ServerLevel level, BlockPos lowerPos) {
        return stationAt(level, lowerPos).map(StationRecord::isPublic).orElse(false);
    }

    /** @return the trimmed name without formatting codes, or null if empty or too long. */
    public static String sanitizeName(String raw) {
        String stripped = ChatFormatting.stripFormatting(raw);
        String name = stripped == null ? "" : stripped.replace('§', ' ').trim();
        return name.isEmpty() || name.length() > MAX_NAME_LENGTH ? null : name;
    }

    public static boolean canRename(ServerPlayer player, StationRecord record) {
        return record.owner().equals(player.getUUID()) || canManagePublic(player);
    }

    public static boolean isKnown(ServerPlayer player, StationRecord record) {
        return record.isPublic() || ActivatedStations.isActivated(player, record.id());
    }

    public static Optional<StationRecord> stationAt(ServerLevel level, BlockPos lowerPos) {
        if (level.getBlockEntity(lowerPos) instanceof TeleportStationBlockEntity be && be.getStationId() != null) {
            Optional<StationRecord> byId = StationNetwork.get(level.getServer()).get(be.getStationId());
            if (byId.isPresent()) return byId;
        }
        return StationNetwork.get(level.getServer()).at(level.dimension(), lowerPos);
    }

    /** True if a lower-half station block is at pos. */
    public static boolean isStationBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TeleportStationBlock && state.getValue(TeleportStationBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** Distance from the player to the centre of a station lower half. */
    public static double distanceTo(ServerPlayer player, StationRecord record) {
        BlockPos p = record.pos();
        return Math.sqrt(player.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
    }

    private static StationRecord register(ServerLevel level, BlockPos lowerPos, ServerPlayer owner) {
        String defaultName = Component.translatable("station.nexusgate.default_name", lowerPos.getX(), lowerPos.getZ()).getString();
        StationRecord record = new StationRecord(UUID.randomUUID(), defaultName, level.dimension(), lowerPos.immutable(),
                owner.getUUID(), false);
        StationNetwork.get(level.getServer()).add(record);
        if (level.getBlockEntity(lowerPos) instanceof TeleportStationBlockEntity be) be.setStationId(record.id());
        return record;
    }

    private static void openSelection(ServerPlayer player, StationRecord source) {
        StationNetwork network = StationNetwork.get(player.server);
        XpSettings xp = NexusConfig.xpSettings();
        String currentDim = player.level().dimension().location().toString();
        List<StationEntry> entries = new ArrayList<>();
        for (StationRecord r : List.copyOf(network.all())) {
            if (r.id().equals(source.id()) || !isKnown(player, r)) continue;
            ServerLevel targetLevel = player.server.getLevel(r.dimension());
            if (targetLevel == null) continue;
            // Prune records whose block is gone, when we can check without loading chunks.
            if (targetLevel.isLoaded(r.pos()) && !isStationBlock(targetLevel, r.pos())) {
                network.remove(r.id());
                continue;
            }
            boolean cross = !r.dimension().equals(player.level().dimension());
            double distance = cross ? -1 : distanceTo(player, r);
            int cost = XpCost.levels(Math.max(0, distance), cross, xp);
            boolean affordable = player.isCreative() || player.experienceLevel >= cost;
            entries.add(new StationEntry(r.id().toString(), r.name(), r.dimension().location().toString(), r.isPublic(),
                    distance, cost, affordable));
        }
        int combat = CombatLock.remainingTicks(CombatEvents.lastCombat(player.getUUID()), player.server.overworld().getGameTime(),
                NexusConfig.chargeSettings().combatLockSeconds());
        PacketDistributor.sendToPlayer(player, new OpenSelectionPayload(source.id().toString(), source.name(),
                canRename(player, source), canManagePublic(player), source.isPublic(),
                combat, StationSorter.sort(entries, currentDim)));
    }
}
