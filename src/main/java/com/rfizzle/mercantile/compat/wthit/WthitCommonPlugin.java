package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

public final class WthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.entityData(StateIndicatorWthitDataProvider.INSTANCE, Villager.class);
        registrar.entityData(BreedingWthitDataProvider.INSTANCE, Villager.class);
        registrar.entityData(MoodWthitDataProvider.INSTANCE, Villager.class);
        registrar.blockData(SentryPylonWthitDataProvider.INSTANCE, SentryPylonBlockEntity.class);
        registrar.entityData(SentryGolemWthitDataProvider.INSTANCE, IronGolem.class);
    }
}
