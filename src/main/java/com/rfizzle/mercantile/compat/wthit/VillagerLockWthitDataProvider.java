package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import net.minecraft.world.entity.npc.Villager;

public enum VillagerLockWthitDataProvider implements IDataProvider<Villager> {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<Villager> accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableProfessionLock) return;

        Villager villager = accessor.getTarget();
        var modData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.raw().putBoolean(VillagerLockWthitProvider.KEY, modData.isProfessionLocked());
    }
}
