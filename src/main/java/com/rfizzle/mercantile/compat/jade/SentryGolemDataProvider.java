package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.SentryGolemTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum SentryGolemDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        if (!(accessor.getEntity() instanceof IronGolem golem)) return;
        SentryGolemTooltipData.write(tag, golem);
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("sentry_golem");
    }
}
