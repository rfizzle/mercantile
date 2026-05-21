package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void mercantile$enterHealingContext(CallbackInfo ci) {
        VillagerHealingContext.exit(); // reset any state leaked by a prior exception before RETURN
        VillagerHealingContext.enter();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mercantile$exitHealingContext(CallbackInfo ci) {
        VillagerHealingContext.exit();
    }
}
