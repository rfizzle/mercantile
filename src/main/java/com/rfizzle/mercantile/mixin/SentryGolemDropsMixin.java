package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.block.SentryGolemTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SentryGolemDropsMixin {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void mercantile$suppressSentryDrops(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        if (SentryGolemTag.isSentry((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldDropExperience", at = @At("HEAD"), cancellable = true)
    private void mercantile$suppressSentryExperience(CallbackInfoReturnable<Boolean> cir) {
        if (SentryGolemTag.isSentry((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
