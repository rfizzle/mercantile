package com.rfizzle.mercantile;

import com.rfizzle.mercantile.advancement.MercantileCriteria;
import com.rfizzle.mercantile.command.MercantileCommands;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.ContractManager;
import com.rfizzle.mercantile.contract.ContractPools;
import com.rfizzle.mercantile.data.GiftMappingManager;
import com.rfizzle.mercantile.data.GratitudeGiftTables;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.data.VillagerNameManager;
import com.rfizzle.mercantile.data.VillagerPlacementHandler;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.market.MarketDayManager;
import com.rfizzle.mercantile.memorial.MemorialManager;
import com.rfizzle.mercantile.memorial.MourningManager;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.particle.MercantileParticles;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import com.rfizzle.mercantile.rehab.NitwitRehabManager;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.TradePinManager;
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
        MercantileCriteria.init();
        MercantileRegistry.registerApiLookups();
        MercantileAttachments.init();
        ReputationManager.init();
        MoodManager.init();
        MarketDayManager.init();
        ExclusiveTradesManager.init();
        TradeIndexDataSource.init();
        GiftMappingManager.init();
        GratitudeGiftTables.init();
        ContractPools.init();
        ContractManager.init();
        VillagerHeadTextures.init();
        VillagerNameManager.init();
        FollowManager.init();
        NitwitRehabManager.init();
        MemorialManager.init();
        MourningManager.init();
        TradePinManager.init();
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
