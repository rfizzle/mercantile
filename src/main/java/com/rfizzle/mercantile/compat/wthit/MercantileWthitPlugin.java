package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

public class MercantileWthitPlugin implements IWailaPlugin {

    @Override
    public void register(IRegistrar registrar) {
        registrar.addComponent(StateIndicatorWthitProvider.INSTANCE, TooltipPosition.BODY, Villager.class);
        registrar.addEntityData(StateIndicatorWthitDataProvider.INSTANCE, Villager.class);

        registrar.addComponent(BreedingWthitProvider.INSTANCE, TooltipPosition.BODY, Villager.class);
        registrar.addEntityData(BreedingWthitDataProvider.INSTANCE, Villager.class);

        registrar.addComponent(MoodWthitProvider.INSTANCE, TooltipPosition.BODY, Villager.class);
        registrar.addEntityData(MoodWthitDataProvider.INSTANCE, Villager.class);

        registrar.addComponent(SentryPylonWthitProvider.INSTANCE, TooltipPosition.BODY, SentryPylonBlock.class);
        registrar.addBlockData(SentryPylonWthitDataProvider.INSTANCE, SentryPylonBlockEntity.class);

        registrar.addComponent(SentryGolemWthitProvider.INSTANCE, TooltipPosition.BODY, IronGolem.class);
        registrar.addEntityData(SentryGolemWthitDataProvider.INSTANCE, IronGolem.class);
    }
}
