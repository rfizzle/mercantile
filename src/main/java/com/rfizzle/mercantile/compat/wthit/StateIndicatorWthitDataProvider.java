package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.StateIndicatorData;
import com.rfizzle.mercantile.config.MercantileConfig;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import net.minecraft.world.entity.npc.Villager;

public enum StateIndicatorWthitDataProvider implements IDataProvider<Villager> {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<Villager> accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableStateIndicators) return;
        StateIndicatorData.write(data.raw(), accessor.getTarget());
    }
}
