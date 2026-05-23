package com.rfizzle.mercantile.particle;

import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MercantileParticles {

    public static final SimpleParticleType CYCLE_GLINT = FabricParticleTypes.simple();
    public static final SimpleParticleType PICKUP_SPARKLE = FabricParticleTypes.simple();
    public static final SimpleParticleType FOLLOW_TRAIL = FabricParticleTypes.simple();
    public static final SimpleParticleType PYLON_MOTE = FabricParticleTypes.simple();
    public static final SimpleParticleType PYLON_SPARK = FabricParticleTypes.simple();
    public static final SimpleParticleType GOLEM_SHARD = FabricParticleTypes.simple();

    public static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("cycle_glint"), CYCLE_GLINT);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pickup_sparkle"), PICKUP_SPARKLE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("follow_trail"), FOLLOW_TRAIL);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pylon_mote"), PYLON_MOTE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pylon_spark"), PYLON_SPARK);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("golem_shard"), GOLEM_SHARD);
    }

    private MercantileParticles() {
    }
}
