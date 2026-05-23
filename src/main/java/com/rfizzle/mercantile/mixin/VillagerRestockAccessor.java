package com.rfizzle.mercantile.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Villager.class)
public interface VillagerRestockAccessor {
    @Accessor("lastRestockGameTime")
    long mercantile$getLastRestockGameTime();

    @Accessor("numberOfRestocksToday")
    int mercantile$getNumberOfRestocksToday();
}
