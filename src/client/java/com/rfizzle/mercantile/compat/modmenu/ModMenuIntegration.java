package com.rfizzle.mercantile.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ModMenu entry point (the {@code modmenu} entrypoint). Only ModMenu loads this class; the
 * Cloth-built screen lives in {@link MercantileConfigScreen} behind an {@code isModLoaded}
 * guard, so ModMenu-without-Cloth degrades to "no config screen" instead of a
 * {@code NoClassDefFoundError} (mc-config).
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return MercantileConfigScreen::create;
    }
}
