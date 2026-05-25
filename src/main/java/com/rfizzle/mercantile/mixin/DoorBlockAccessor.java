package com.rfizzle.mercantile.mixin;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoorBlock.class)
public interface DoorBlockAccessor {

    @Accessor("type")
    BlockSetType mercantile$getType();
}
