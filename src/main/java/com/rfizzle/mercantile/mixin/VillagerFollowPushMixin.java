package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.follow.FollowManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class VillagerFollowPushMixin {

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void mercantile$preventFollowingVillagerPush(Entity entity, CallbackInfo ci) {
        if (((Object) this) instanceof Villager villager
                && FollowManager.isFollowing(villager)
                && !(entity instanceof Player)) {
            ci.cancel();
            return;
        }

        if (entity instanceof Villager villager
                && FollowManager.isFollowing(villager)
                && !(((Object) this) instanceof Player)) {
            ci.cancel();
        }
    }
}
