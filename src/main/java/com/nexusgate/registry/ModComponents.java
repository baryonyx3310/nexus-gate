package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

public final class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, NexusGate.MOD_ID);

    /** Station a Locator is linked to. */
    public static final Supplier<DataComponentType<UUID>> LINKED_STATION = REGISTER.registerComponentType("linked_station",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    private ModComponents() {}
}
