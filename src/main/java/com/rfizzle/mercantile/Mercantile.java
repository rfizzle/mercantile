package com.rfizzle.mercantile;

import com.rfizzle.mercantile.command.MercantileCommands;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.GiftMappingManager;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.data.VillagerNameManager;
import com.rfizzle.mercantile.data.VillagerPlacementHandler;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.particle.MercantileParticles;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mercantile implements ModInitializer {
    public static final String MOD_ID = "mercantile";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MercantileConfig.get();
        MercantileRegistry.register();
        MercantileRegistry.registerApiLookups();
        MercantileAttachments.init();
        ReputationManager.init();
        MoodManager.init();
        ExclusiveTradesManager.init();
        TradeIndexDataSource.init();
        GiftMappingManager.init();
        VillagerHeadTextures.init();
        VillagerNameManager.init();
        FollowManager.init();
        MercantileParticles.init();
        MercantileNetworking.init();
        MercantileCommands.init();
        VillagerPlacementHandler.init();
        LOGGER.info("Mercantile initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
