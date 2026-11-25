package com.nexusgate.registry;

import com.nexusgate.NexusGate;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Nexus Gate's own particle family (textures in assets/nexusgate/textures/particle). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, NexusGate.MOD_ID);

    /** Velocity-stretched energy streak. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = REGISTER.register("spark", () -> new SimpleParticleType(false));
    /** Soft glowing mote that drifts and fades. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = REGISTER.register("mote", () -> new SimpleParticleType(false));
    /** Spinning hexagonal fragment used for (de)materialisation. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHARD = REGISTER.register("shard", () -> new SimpleParticleType(true));
    /** Flat expanding ground ring. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCK_RING = REGISTER.register("shock_ring", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
