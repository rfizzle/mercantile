package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.client.visualization.BellGlowTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityIsCurrentlyGlowingMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void mercantile$bellRingGlow(CallbackInfoReturnable<Boolean> cir) {
        if (BellGlowTracker.size() == 0) return;
        Object self = this;
        if (!(self instanceof Villager villager)) return;
        if (!villager.level().isClientSide()) return;
        if (BellGlowTracker.isGlowing(villager.getUUID(), villager.level().getGameTime())) {
            cir.setReturnValue(true);
        }
    }
}
