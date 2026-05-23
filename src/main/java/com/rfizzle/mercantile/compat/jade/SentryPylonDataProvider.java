package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum SentryPylonDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final String KEY_FUEL = "mercantile:pylonFuel";
    static final String KEY_MAX_FUEL = "mercantile:pylonMaxFuel";
    static final String KEY_SENTRIES = "mercantile:pylonSentries";
    static final String KEY_MAX_SENTRIES = "mercantile:pylonMaxSentries";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SentryPylonBlockEntity pylon)) return;
        tag.putInt(KEY_FUEL, pylon.getFuel());
        tag.putInt(KEY_MAX_FUEL, pylon.getMaxFuel());
        tag.putInt(KEY_SENTRIES, pylon.getSentries().size());
        tag.putInt(KEY_MAX_SENTRIES, MercantileConfig.get().pylonMaxGolems);
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("sentry_pylon");
    }
}
