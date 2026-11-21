package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NexusGate.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nexusgate.main"))
            .icon(() -> ModItems.TELEPORT_STATION.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(ModItems.TELEPORT_STATION.get());
                output.accept(ModItems.LOCATOR.get());
            })
            .build());

    private ModCreativeTab() {}
}
