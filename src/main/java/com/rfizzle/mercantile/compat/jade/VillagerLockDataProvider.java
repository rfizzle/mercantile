package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum VillagerLockDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!MercantileConfig.get().enableProfessionLock) return;
        if (!(accessor.getEntity() instanceof Villager villager)) return;

        var data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        tag.putBoolean("mercantile:professionLocked", data.isProfessionLocked());
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("profession_lock");
    }
}
