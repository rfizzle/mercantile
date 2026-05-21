package com.rfizzle.mercantile.client;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.client.network.ClientNetworkHandler;
import com.rfizzle.mercantile.client.particle.CycleGlintParticle;
import com.rfizzle.mercantile.client.particle.FollowTrailParticle;
import com.rfizzle.mercantile.client.particle.PickupSparkleParticle;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public class MercantileClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.init();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientMercantileData.clear());
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.CYCLE_GLINT, CycleGlintParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.PICKUP_SPARKLE, PickupSparkleParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.FOLLOW_TRAIL, FollowTrailParticle.Provider::new);
    }
}
