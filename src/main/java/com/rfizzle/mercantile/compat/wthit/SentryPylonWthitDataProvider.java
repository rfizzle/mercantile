package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;

public enum SentryPylonWthitDataProvider implements IDataProvider<SentryPylonBlockEntity> {
    INSTANCE;

    static final String KEY_FUEL = "mercantile:pylonFuel";
    static final String KEY_MAX_FUEL = "mercantile:pylonMaxFuel";
    static final String KEY_SENTRIES = "mercantile:pylonSentries";
    static final String KEY_MAX_SENTRIES = "mercantile:pylonMaxSentries";

    @Override
    public void appendData(IDataWriter data, IServerAccessor<SentryPylonBlockEntity> accessor, IPluginConfig config) {
        SentryPylonBlockEntity pylon = accessor.getTarget();
        data.raw().putInt(KEY_FUEL, pylon.getFuel());
        data.raw().putInt(KEY_MAX_FUEL, pylon.getMaxFuel());
        data.raw().putInt(KEY_SENTRIES, pylon.getSentries().size());
        data.raw().putInt(KEY_MAX_SENTRIES, MercantileConfig.get().pylonMaxGolems);
    }
}
