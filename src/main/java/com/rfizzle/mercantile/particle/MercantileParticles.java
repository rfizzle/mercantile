package com.rfizzle.mercantile.particle;

import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MercantileParticles {

    public static final SimpleParticleType CYCLE_GLINT = FabricParticleTypes.simple();
    public static final SimpleParticleType PICKUP_SPARKLE = FabricParticleTypes.simple();
    public static final SimpleParticleType FOLLOW_TRAIL = FabricParticleTypes.simple();
    public static final SimpleParticleType PYLON_MOTE = FabricParticleTypes.simple();
    public static final SimpleParticleType PYLON_SPARK = FabricParticleTypes.simple();
    public static final SimpleParticleType GOLEM_SHARD = FabricParticleTypes.simple();
    // Colour-carrying type for the workstation link visualization (profession-tinted).
    public static final ParticleType<LinkMoteParticleOptions> LINK_MOTE =
            FabricParticleTypes.complex(LinkMoteParticleOptions.CODEC, LinkMoteParticleOptions.STREAM_CODEC);
    // Workstation status markers (bell-held overlay): green check / white question mark.
    public static final SimpleParticleType WORKSTATION_CLAIMED = FabricParticleTypes.simple();
    public static final SimpleParticleType WORKSTATION_UNCLAIMED = FabricParticleTypes.simple();

    public static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("cycle_glint"), CYCLE_GLINT);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pickup_sparkle"), PICKUP_SPARKLE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("follow_trail"), FOLLOW_TRAIL);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pylon_mote"), PYLON_MOTE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("pylon_spark"), PYLON_SPARK);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("golem_shard"), GOLEM_SHARD);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("link_mote"), LINK_MOTE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("workstation_claimed"), WORKSTATION_CLAIMED);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mercantile.id("workstation_unclaimed"), WORKSTATION_UNCLAIMED);
    }

    private MercantileParticles() {
    }
}
