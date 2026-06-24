package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.block.ReturnToPylonGoal;
import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.block.SentryTargetHostilesGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class IronGolemMixin extends AbstractGolem {

    protected IronGolemMixin(EntityType<? extends AbstractGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void mercantile$addSentryGoals(CallbackInfo ci) {
        IronGolem self = (IronGolem) (Object) this;
        this.goalSelector.addGoal(2, new ReturnToPylonGoal(self));
        // Higher priority (lower number) than vanilla's creeper-excluding target goal at slot 3,
        // so sentries acquire creepers; the goal is inert on non-sentry golems.
        this.targetSelector.addGoal(2, new SentryTargetHostilesGoal(self));
    }

    /**
     * Vanilla {@code IronGolem.canAttackType} hardcodes creepers out, and that check sits inside
     * the combat targeting conditions — so without this even a creeper-inclusive target goal can
     * never acquire one. Re-allow creepers, but only for sentries; plain golems keep vanilla's
     * exclusion.
     */
    @Inject(method = "canAttackType", at = @At("HEAD"), cancellable = true)
    private void mercantile$allowCreeperForSentry(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (type == EntityType.CREEPER && SentryGolemTag.isSentry((IronGolem) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
