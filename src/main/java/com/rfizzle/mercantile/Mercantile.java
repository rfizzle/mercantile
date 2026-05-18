package com.rfizzle.mercantile;

import com.rfizzle.mercantile.config.MercantileConfig;
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
        LOGGER.info("Mercantile initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
