package com.rfizzle.mercantile.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin {

    @WrapMethod(method = "tick")
    private void mercantile$wrapTick(Operation<Void> original) {
        if (!mercantile$carriesHealingOrRegen()) {
            original.call();
            return;
        }
        VillagerHealingContext.enter();
        try {
            original.call();
        } finally {
            VillagerHealingContext.exit();
        }
    }

    @Unique
    private boolean mercantile$carriesHealingOrRegen() {
        PotionContents contents = ((AreaEffectCloudAccessor) this).mercantile$getPotionContents();
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().is(MobEffects.HEAL) || effect.getEffect().is(MobEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }
}
