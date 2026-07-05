package com.rfizzle.mercantile.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rfizzle.mercantile.client.hud.ReputationDetailPanelRenderer;
import com.rfizzle.mercantile.client.hud.ReputationHudOverlay;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.client.network.ClientNetworkHandler;
import com.rfizzle.mercantile.client.particle.CycleGlintParticle;
import com.rfizzle.mercantile.client.particle.FollowTrailParticle;
import com.rfizzle.mercantile.client.particle.GolemShardParticle;
import com.rfizzle.mercantile.client.particle.GriefTearParticle;
import com.rfizzle.mercantile.client.particle.LinkMoteParticle;
import com.rfizzle.mercantile.client.particle.PickupSparkleParticle;
import com.rfizzle.mercantile.client.particle.PylonMoteParticle;
import com.rfizzle.mercantile.client.particle.PylonSparkParticle;
import com.rfizzle.mercantile.client.particle.WorkstationMarkerParticle;
import com.rfizzle.mercantile.client.visualization.BellGlowTracker;
import com.rfizzle.mercantile.client.visualization.BellRadiusRenderer;
import com.rfizzle.mercantile.client.visualization.ContractGlowTracker;
import com.rfizzle.mercantile.client.visualization.WorkstationLinkRenderer;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.lwjgl.glfw.GLFW;

public class MercantileClient implements ClientModInitializer {
    /**
     * Hold-to-peek keybind for the reputation detail panel. Unbound by default
     * ({@link GLFW#GLFW_KEY_UNKNOWN}) so it never collides with another mod's
     * binding — the player assigns it under Controls → Mercantile.
     */
    public static KeyMapping KEY_REPUTATION_DETAIL;

    @Override
    public void onInitializeClient() {
        KEY_REPUTATION_DETAIL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mercantile.reputation_detail",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.mercantile"));

        ClientNetworkHandler.init();
        ReputationHudOverlay.register();
        HudRenderCallback.EVENT.register(new ReputationDetailPanelRenderer());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMercantileData.clear();
            BellGlowTracker.clear();
            BellRadiusRenderer.clearPending();
            ContractGlowTracker.clear();
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
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.WORKSTATION_CLAIMED, WorkstationMarkerParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.WORKSTATION_UNCLAIMED, WorkstationMarkerParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.GRIEF_TEAR, GriefTearParticle.Provider::new);
        // The contract cue reuses the workstation-marker billboard (bob + pulse); only the sprite differs.
        ParticleFactoryRegistry.getInstance().register(MercantileParticles.CONTRACT_AVAILABLE, WorkstationMarkerParticle.Provider::new);
        ClientTickEvents.END_CLIENT_TICK.register(WorkstationLinkRenderer::tick);
        ClientTickEvents.END_CLIENT_TICK.register(BellRadiusRenderer::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ContractGlowTracker::tick);
    }
}
