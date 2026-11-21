package com.nexusgate.registry;

import com.mojang.serialization.Codec;
import com.nexusgate.NexusGate;
import net.minecraft.core.UUIDUtil;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NexusGate.MOD_ID);

    private static final Codec<Set<UUID>> UUID_SET_CODEC =
            UUIDUtil.STRING_CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    /** Stations the player has activated. */
    public static final Supplier<AttachmentType<Set<UUID>>> ACTIVATED = REGISTER.register("activated_stations",
            () -> AttachmentType.<Set<UUID>>builder(() -> new HashSet<>()).serialize(UUID_SET_CODEC).copyOnDeath().build());

    private ModAttachments() {}
}
