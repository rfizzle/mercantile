package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class MercantileJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(VillagerLockDataProvider.INSTANCE, Villager.class);
        registration.registerEntityDataProvider(StateIndicatorDataProvider.INSTANCE, Villager.class);
        registration.registerEntityDataProvider(BreedingDataProvider.INSTANCE, Villager.class);
        registration.registerBlockDataProvider(SentryPylonDataProvider.INSTANCE, SentryPylonBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(VillagerLockComponentProvider.INSTANCE, Villager.class);
        registration.registerEntityComponent(StateIndicatorComponentProvider.INSTANCE, Villager.class);
        registration.registerEntityComponent(BreedingComponentProvider.INSTANCE, Villager.class);
        registration.registerBlockComponent(SentryPylonComponentProvider.INSTANCE, SentryPylonBlock.class);
    }
}
