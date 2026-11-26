package com.nexusgate.client;

import com.nexusgate.NexusGate;
import com.nexusgate.client.fx.HudFxLayer;
import com.nexusgate.client.fx.TeleportFxController;
import com.nexusgate.client.fx.WorldFxRenderer;
import com.nexusgate.client.particle.NgParticles;
import com.nexusgate.client.render.ModShaders;
import com.nexusgate.client.render.StationItemRenderer;
import com.nexusgate.client.render.StationRenderer;
import com.nexusgate.registry.ModBlockEntities;
import com.nexusgate.registry.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring. Called from the mod constructor only when running on a client. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ModShaders::register);
        modBus.addListener(NgParticles::register);
        modBus.addListener(ClientSetup::renderers);
        modBus.addListener(ClientSetup::guiLayers);
        modBus.addListener(ClientSetup::extensions);
        NeoForge.EVENT_BUS.addListener(WorldFxRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.nexusgate.client.render.MatterDissolveRenderer::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> TeleportFxController.tick());
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TELEPORT_STATION.get(), StationRenderer::new);
    }

    private static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(NexusGate.MOD_ID, "gate_fx"), new HudFxLayer());
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new StationItemRenderer();
                return renderer;
            }
        }, ModItems.TELEPORT_STATION.get());
    }
}
