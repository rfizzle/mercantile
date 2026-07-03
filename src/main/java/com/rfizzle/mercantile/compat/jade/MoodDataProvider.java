package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.MoodTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum MoodDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!MercantileConfig.get().enableMood) return;
        if (!(accessor.getEntity() instanceof Villager villager)) return;
        MoodTooltipData.write(tag, villager);
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("mood");
    }
}
