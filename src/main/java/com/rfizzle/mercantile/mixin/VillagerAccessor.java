package com.rfizzle.mercantile.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface VillagerAccessor {
    @Invoker("pickUpItem")
    void invokePickUpItem(ItemEntity itemEntity);
}
