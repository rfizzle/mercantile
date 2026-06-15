package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.reputation.ReputationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public abstract class RaidMixin {

    @Inject(method = "addHeroOfTheVillage", at = @At("HEAD"))
    private void mercantile$onRaidWin(Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            ReputationManager.gainRaidWinRep(player);
        }
    }
}
