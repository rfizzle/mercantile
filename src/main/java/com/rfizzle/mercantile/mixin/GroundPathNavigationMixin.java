package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin extends PathNavigation {

    protected GroundPathNavigationMixin(Mob mob, Level level) {
        super(mob, level);
    }

    @Inject(method = "canUpdatePath", at = @At("RETURN"), cancellable = true)
    private void mercantile$allowPathUpdateWhileClimbing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (!(this.mob instanceof Villager)) return;
        MercantileConfig config = MercantileConfig.get();
        if (!config.enablePathfindingFixes) return;
        if (!config.enablePathfindingLadders) return;
        if (this.mob.onClimbable()) {
            cir.setReturnValue(true);
        }
    }
}