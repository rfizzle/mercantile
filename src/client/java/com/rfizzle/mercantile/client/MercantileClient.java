package com.rfizzle.mercantile.client;

import com.rfizzle.mercantile.client.network.ClientNetworkHandler;
import com.rfizzle.mercantile.client.particle.CycleGlintParticle;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public class MercantileClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.init();
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.CYCLE_GLINT, CycleGlintParticle.Provider::new);
    }
}
