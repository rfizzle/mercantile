package com.rfizzle.mercantile.compat.wthit;

import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.world.entity.npc.Villager;

public class MercantileWthitPlugin implements IWailaPlugin {

    @Override
    public void register(IRegistrar registrar) {
        registrar.addComponent(VillagerLockWthitProvider.INSTANCE, TooltipPosition.BODY, Villager.class);
        registrar.addDataContext(VillagerLockWthitProvider.INSTANCE, Villager.class);
    }
}
