package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.shared.StateIndicatorData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum StateIndicatorDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!MercantileConfig.get().enableStateIndicators) return;
        if (!(accessor.getEntity() instanceof Villager villager)) return;
        StateIndicatorData.write(tag, villager);
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("state_indicators");
    }
}
