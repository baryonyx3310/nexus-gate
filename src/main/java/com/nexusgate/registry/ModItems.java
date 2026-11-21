package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import com.nexusgate.item.LocatorItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(NexusGate.MOD_ID);

    public static final DeferredItem<DoubleHighBlockItem> TELEPORT_STATION = REGISTER.register("teleport_station",
            () -> new DoubleHighBlockItem(ModBlocks.TELEPORT_STATION.get(), new Item.Properties()));

    public static final DeferredItem<LocatorItem> LOCATOR = REGISTER.register("locator",
            () -> new LocatorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
