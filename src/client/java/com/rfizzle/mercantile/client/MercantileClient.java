package com.rfizzle.mercantile.client;

import com.rfizzle.mercantile.client.hud.ReputationHudOverlay;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.client.network.ClientNetworkHandler;
import com.rfizzle.mercantile.client.particle.CycleGlintParticle;
import com.rfizzle.mercantile.client.particle.FollowTrailParticle;
import com.rfizzle.mercantile.client.particle.GolemShardParticle;
import com.rfizzle.mercantile.client.particle.LinkMoteParticle;
import com.rfizzle.mercantile.client.particle.PickupSparkleParticle;
import com.rfizzle.mercantile.client.particle.PylonMoteParticle;
import com.rfizzle.mercantile.client.particle.PylonSparkParticle;
import com.rfizzle.mercantile.client.visualization.BellGlowTracker;
import com.rfizzle.mercantile.client.visualization.BellRadiusRenderer;
import com.rfizzle.mercantile.client.visualization.WorkstationLinkRenderer;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

public class MercantileClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.init();
        ReputationHudOverlay.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMercantileData.clear();
            BellGlowTracker.clear();
            BellRadiusRenderer.clearPending();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof MerchantScreen) {
                ScreenEvents.remove(screen).register(s -> ClientMercantileData.clearMerchantScreenData());
            }
        });
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.CYCLE_GLINT, CycleGlintParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.PICKUP_SPARKLE, PickupSparkleParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.FOLLOW_TRAIL, FollowTrailParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.PYLON_MOTE, PylonMoteParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.PYLON_SPARK, PylonSparkParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.GOLEM_SHARD, GolemShardParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.LINK_MOTE, LinkMoteParticle.Provider::new);
        ClientTickEvents.END_CLIENT_TICK.register(WorkstationLinkRenderer::tick);
        ClientTickEvents.END_CLIENT_TICK.register(BellRadiusRenderer::tick);
    }
}
