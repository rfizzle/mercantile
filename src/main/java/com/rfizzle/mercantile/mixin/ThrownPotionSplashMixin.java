package com.rfizzle.mercantile.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownPotion.class)
public abstract class ThrownPotionSplashMixin {

    @WrapMethod(method = "applySplash")
    private void mercantile$wrapApplySplash(Iterable<MobEffectInstance> effects, @Nullable Entity entity, Operation<Void> original) {
        if (!mercantile$containsHealingOrRegen(effects)) {
            original.call(effects, entity);
            return;
        }
        VillagerHealingContext.enter();
        try {
            original.call(effects, entity);
        } finally {
            VillagerHealingContext.exit();
        }
    }

    @Unique
    private static boolean mercantile$containsHealingOrRegen(Iterable<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().is(MobEffects.HEAL) || effect.getEffect().is(MobEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }
}
