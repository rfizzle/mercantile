package com.rfizzle.mercantile.mixin;

import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FenceGateBlock.class)
public interface FenceGateBlockAccessor {

    @Accessor("type")
    WoodType mercantile$getType();
}
