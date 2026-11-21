package com.nexusgate;

import com.mojang.logging.LogUtils;
import com.nexusgate.block.TeleportStationBlock;
import com.nexusgate.command.NexusCommand;
import com.nexusgate.config.NexusConfig;
import com.nexusgate.net.Payloads;
import com.nexusgate.registry.ModAttachments;
import com.nexusgate.registry.ModBlockEntities;
import com.nexusgate.registry.ModBlocks;
import com.nexusgate.registry.ModComponents;
import com.nexusgate.registry.ModCreativeTab;
import com.nexusgate.registry.ModItems;
import com.nexusgate.teleport.ChargeManager;
import com.nexusgate.teleport.CombatEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(NexusGate.MOD_ID)
public final class NexusGate {
    public static final String MOD_ID = "nexusgate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NexusGate(IEventBus modBus, ModContainer container) {
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModComponents.REGISTER.register(modBus);
        ModCreativeTab.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, NexusConfig.SPEC);
        modBus.addListener(Payloads::register);

        NeoForge.EVENT_BUS.addListener(ChargeManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(ChargeManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(TeleportStationBlock::onBreak);
        NeoForge.EVENT_BUS.addListener(NexusCommand::register);
    }
}
