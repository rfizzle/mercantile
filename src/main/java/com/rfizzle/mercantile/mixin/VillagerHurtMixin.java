package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.follow.FollowableVillager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class VillagerHurtMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void mercantile$cancelReturnHomeOnHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Villager villager) {
            ((FollowableVillager) villager).mercantile$setReturningHomeSync(false);
        }
    }
}
