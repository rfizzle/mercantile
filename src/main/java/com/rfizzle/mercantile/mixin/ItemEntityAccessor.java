package com.rfizzle.mercantile.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("target")
    UUID getTarget();

    @Accessor("target")
    void setTarget(UUID target);
}
