package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.client.visualization.BellGlowTracker;
import com.rfizzle.mercantile.client.visualization.ContractGlowTracker;
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

    // Outlines the payee villager while the local player holds its delivery contract (issue #86).
    // The isClientSide gate runs before any tracker read — the tracker's state is render-thread
    // confined, and on an integrated server this method is also hit from the server thread.
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void mercantile$contractGlow(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof Villager villager)) return;
        if (!villager.level().isClientSide()) return;
        if (!ContractGlowTracker.isActive()) return;
        if (ContractGlowTracker.isTarget(villager, villager.level().getGameTime())) {
            cir.setReturnValue(true);
        }
    }
}
