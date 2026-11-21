package com.nexusgate;

import com.mojang.logging.LogUtils;
import com.nexusgate.config.NexusConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(NexusGate.MOD_ID)
public final class NexusGate {
    public static final String MOD_ID = "nexusgate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NexusGate(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, NexusConfig.SPEC);
    }
}
