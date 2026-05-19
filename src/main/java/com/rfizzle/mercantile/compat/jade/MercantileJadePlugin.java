package com.rfizzle.mercantile.compat.jade;

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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(VillagerLockComponentProvider.INSTANCE, Villager.class);
    }
}
