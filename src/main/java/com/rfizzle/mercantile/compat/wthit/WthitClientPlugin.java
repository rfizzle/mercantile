package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.block.SentryPylonBlock;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

public final class WthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(StateIndicatorWthitProvider.INSTANCE, Villager.class);
        registrar.body(BreedingWthitProvider.INSTANCE, Villager.class);
        registrar.body(MoodWthitProvider.INSTANCE, Villager.class);
        registrar.body(SentryPylonWthitProvider.INSTANCE, SentryPylonBlock.class);
        registrar.body(SentryGolemWthitProvider.INSTANCE, IronGolem.class);
    }
}
