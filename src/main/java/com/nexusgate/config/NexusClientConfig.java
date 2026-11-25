package com.nexusgate.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: nexusgate-client.toml. */
public final class NexusClientConfig {
    public static final ModConfigSpec SPEC;

    /** Whether the gate screen shows the "select a destination" hint panel. Dismissed with its close button. */
    public static final ModConfigSpec.BooleanValue SHOW_SELECT_HINT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SHOW_SELECT_HINT = b.comment("Show the 'select a destination' hint on the gate screen. Set back to true to restore it after closing it.")
                .define("showSelectHint", true);
        SPEC = b.build();
    }

    private NexusClientConfig() {}

    public static boolean showSelectHint() {
        return !SPEC.isLoaded() || SHOW_SELECT_HINT.get();
    }

    public static void hideSelectHint() {
        if (!SPEC.isLoaded()) return;
        SHOW_SELECT_HINT.set(false);
        SPEC.save();
    }
}
