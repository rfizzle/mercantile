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

/**
 * Cancel pushing during follow mode so following villagers don't get knocked off course
 * by other entities (and so a following villager doesn't push another non-player entity).
 * {@code doPush} is bidirectional — {@code villager.doPush(other)} AND {@code other.doPush(villager)}
 * each apply force, so both directions must be intercepted. Mixin 0.8 cannot resolve {@code doPush}
 * on a subclass target (the method is declared on {@link LivingEntity}); the {@code instanceof}
 * guards are the first instructions so non-relevant call sites return immediately.
 */
@Mixin(LivingEntity.class)
public abstract class VillagerFollowPushMixin {

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void mercantile$preventFollowingVillagerPush(Entity entity, CallbackInfo ci) {
        if (((Object) this) instanceof Villager self
                && FollowManager.isFollowing(self)
                && !(entity instanceof Player)) {
            ci.cancel();
            return;
        }
        if (entity instanceof Villager other
                && FollowManager.isFollowing(other)
                && !(((Object) this) instanceof Player)) {
            ci.cancel();
        }
    }
}
