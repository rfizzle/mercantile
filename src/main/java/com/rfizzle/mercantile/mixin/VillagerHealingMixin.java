package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class VillagerHealingMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float mercantile$boostHeal(float amount) {
        if (!((Object) this instanceof Villager)) return amount;
        if (!MercantileConfig.get().enableHealing) return amount;
        if (!VillagerHealingContext.isActive()) return amount;
        return amount * MercantileConfig.get().healingMultiplier;
    }

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance mercantile$doubleRegenDuration(MobEffectInstance effect) {
        if (effect == null) return effect;
        if (!((Object) this instanceof Villager)) return effect;
        if (!MercantileConfig.get().enableHealing) return effect;
        if (!VillagerHealingContext.isActive()) return effect;
        if (!effect.getEffect().is(MobEffects.REGENERATION)) return effect;
        if (effect.isInfiniteDuration()) return effect;

        float multiplier = MercantileConfig.get().healingMultiplier;
        long scaled = (long) (effect.getDuration() * multiplier);
        int newDuration = scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
        MobEffectInstance hidden = ((MobEffectInstanceAccessor) (Object) effect).mercantile$getHiddenEffect();
        return new MobEffectInstance(
                effect.getEffect(),
                newDuration,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.isVisible(),
                effect.showIcon(),
                hidden
        );
    }
}
