package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class VillagerHealingMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float mercantile$boostHeal(float amount) {
        if (!((Object) this instanceof Villager villager)) return amount;
        if (VillagerHealingContext.isActive()) {
            return amount * MercantileConfig.get().healingMultiplier;
        }
        var data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        if (data.isHealBoosted()) {
            return amount * MercantileConfig.get().healingMultiplier;
        }
        return amount;
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"))
    private void mercantile$trackBoostedRegen(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Villager villager)) return;
        if (!effect.getEffect().is(MobEffects.REGENERATION)) return;

        var data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        if (VillagerHealingContext.isActive()) {
            data.setHealBoosted(true);
        } else {
            data.setHealBoosted(false);
        }
    }

    @Inject(method = "onEffectRemoved", at = @At("HEAD"))
    protected void mercantile$clearHealBoost(MobEffectInstance effect, CallbackInfo ci) {
        if (!((Object) this instanceof Villager villager)) return;
        if (!effect.getEffect().is(MobEffects.REGENERATION)) return;

        var data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setHealBoosted(false);
    }
}
