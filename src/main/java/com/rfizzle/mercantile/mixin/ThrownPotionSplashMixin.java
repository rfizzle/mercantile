package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownPotion.class)
public abstract class ThrownPotionSplashMixin {

    @Inject(method = "applySplash", at = @At("HEAD"))
    private void mercantile$enterHealingContext(Iterable<MobEffectInstance> iterable, @Nullable Entity entity, CallbackInfo ci) {
        VillagerHealingContext.exit(); // reset any state leaked by a prior exception before RETURN
        VillagerHealingContext.enter();
    }

    @Inject(method = "applySplash", at = @At("RETURN"))
    private void mercantile$exitHealingContext(Iterable<MobEffectInstance> iterable, @Nullable Entity entity, CallbackInfo ci) {
        VillagerHealingContext.exit();
    }
}
