package com.nexusgate.config;

import com.nexusgate.core.ChargeSettings;
import com.nexusgate.core.XpSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: nexusgate-server.toml. */
public final class NexusConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue CHARGE_SECONDS;
    private static final ModConfigSpec.IntValue PER_HOSTILE_SECONDS;
    private static final ModConfigSpec.IntValue HOSTILE_RADIUS;
    private static final ModConfigSpec.IntValue MAX_CHARGE_SECONDS;
    private static final ModConfigSpec.IntValue CROSS_DIMENSION_EXTRA_SECONDS;
    private static final ModConfigSpec.IntValue COMBAT_LOCK_SECONDS;
    private static final ModConfigSpec.DoubleValue MAX_MOVE_DISTANCE;
    private static final ModConfigSpec.DoubleValue STATION_USE_RANGE;
    private static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION;
    private static final ModConfigSpec.BooleanValue XP_COST_ENABLED;
    private static final ModConfigSpec.IntValue XP_BASE_COST;
    private static final ModConfigSpec.IntValue XP_BLOCKS_PER_LEVEL;
    private static final ModConfigSpec.IntValue XP_MAX_SAME_DIMENSION;
    private static final ModConfigSpec.IntValue XP_CROSS_DIMENSION_COST;
    private static final ModConfigSpec.IntValue PUBLIC_STATION_PERMISSION_LEVEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("charging");
        CHARGE_SECONDS = b.comment("Base charge time in seconds before a teleport completes.")
                .defineInRange("chargeSeconds", 5, 0, 60);
        PER_HOSTILE_SECONDS = b.comment("Extra seconds of charge per hostile mob within hostileRadius.")
                .defineInRange("perHostileSeconds", 1, 0, 60);
        HOSTILE_RADIUS = b.comment("Radius in blocks used to count nearby hostile mobs.")
                .defineInRange("hostileRadius", 8, 0, 32);
        MAX_CHARGE_SECONDS = b.comment("Maximum charge time in seconds.")
                .defineInRange("maxChargeSeconds", 12, 0, 60);
        CROSS_DIMENSION_EXTRA_SECONDS = b.comment("Extra seconds of charge when changing dimension.")
                .defineInRange("crossDimensionExtraSeconds", 3, 0, 60);
        COMBAT_LOCK_SECONDS = b.comment("Seconds after taking or dealing damage during which a teleport cannot start.")
                .defineInRange("combatLockSeconds", 10, 0, 60);
        MAX_MOVE_DISTANCE = b.comment("Moving further than this many blocks from the start position cancels the charge.")
                .defineInRange("maxMoveDistance", 1.5, 0.0, 16.0);
        STATION_USE_RANGE = b.comment("Maximum distance in blocks from the source station to start a teleport.")
                .defineInRange("stationUseRange", 4.0, 1.0, 16.0);
        ALLOW_CROSS_DIMENSION = b.comment("Allow teleporting between dimensions.")
                .define("allowCrossDimension", true);
        b.pop();
        b.push("xp");
        XP_COST_ENABLED = b.comment("Charge XP levels for teleports.").define("xpCostEnabled", true);
        XP_BASE_COST = b.comment("Base XP level cost within the same dimension.").defineInRange("xpBaseCost", 1, 0, 100);
        XP_BLOCKS_PER_LEVEL = b.comment("One extra level per this many blocks of distance.")
                .defineInRange("xpBlocksPerLevel", 500, 1, 1_000_000);
        XP_MAX_SAME_DIMENSION = b.comment("Maximum XP level cost within the same dimension.")
                .defineInRange("xpMaxSameDimension", 10, 0, 100);
        XP_CROSS_DIMENSION_COST = b.comment("Flat XP level cost when changing dimension.")
                .defineInRange("xpCrossDimensionCost", 5, 0, 100);
        b.pop();
        b.push("admin");
        PUBLIC_STATION_PERMISSION_LEVEL = b.comment("Permission level required to make a station public.")
                .defineInRange("publicStationPermissionLevel", 2, 0, 4);
        b.pop();
        SPEC = b.build();
    }

    private NexusConfig() {}

    public static ChargeSettings chargeSettings() {
        return new ChargeSettings(CHARGE_SECONDS.get(), PER_HOSTILE_SECONDS.get(), MAX_CHARGE_SECONDS.get(),
                CROSS_DIMENSION_EXTRA_SECONDS.get(), MAX_MOVE_DISTANCE.get(), COMBAT_LOCK_SECONDS.get(),
                STATION_USE_RANGE.get(), ALLOW_CROSS_DIMENSION.get());
    }

    public static XpSettings xpSettings() {
        return new XpSettings(XP_COST_ENABLED.get(), XP_BASE_COST.get(), XP_BLOCKS_PER_LEVEL.get(),
                XP_MAX_SAME_DIMENSION.get(), XP_CROSS_DIMENSION_COST.get());
    }

    public static int hostileRadius() { return HOSTILE_RADIUS.get(); }

    public static int publicPermissionLevel() { return PUBLIC_STATION_PERMISSION_LEVEL.get(); }
}
