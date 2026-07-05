package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.shared.SentryGolemTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import net.minecraft.world.entity.animal.IronGolem;

public enum SentryGolemWthitDataProvider implements IDataProvider<IronGolem> {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<IronGolem> accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        SentryGolemTooltipData.write(data.raw(), accessor.getTarget());
    }
}
