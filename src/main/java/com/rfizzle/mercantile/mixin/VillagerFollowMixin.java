package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.follow.FollowPlayerGoal;
import com.rfizzle.mercantile.follow.FollowableVillager;
import com.rfizzle.mercantile.follow.ReturnHomeGoal;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/**
 * Intercepts {@code Villager#mobInteract} (HEAD, cancellable, priority 1100) to implement the
 * emerald-toggle follow mode. Runs after {@link VillagerPickupMixin} (priority 900). The
 * item-hand guards are mutually exclusive: this mixin requires an emerald in the main hand;
 * {@link VillagerPickupMixin} requires an empty hand, {@link VillagerBabyFeedMixin} a villager
 * breeding food on a baby, {@link VillagerNitwitRehabMixin} a golden apple on a nitwit, and
 * {@link VillagerWorkOrderMixin} a workstation block item. Future authors must preserve this
 * invariant so no two injections can fire for the same interaction.
 */
@Mixin(Villager.class)
public abstract class VillagerFollowMixin extends AbstractVillager implements FollowableVillager {

    @Unique
    private static final EntityDataAccessor<Boolean> mercantile$DATA_FOLLOWING =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.BOOLEAN);

    @Unique
    private static final EntityDataAccessor<Boolean> mercantile$DATA_RETURNING_HOME =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.BOOLEAN);

    protected VillagerFollowMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void mercantile$defineFollowData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(mercantile$DATA_FOLLOWING, false);
        builder.define(mercantile$DATA_RETURNING_HOME, false);
    }

    @Override
    public void mercantile$setFollowingSync(boolean following) {
        this.entityData.set(mercantile$DATA_FOLLOWING, following);
    }

    @Override
    public boolean mercantile$isFollowingSync() {
        return this.entityData.get(mercantile$DATA_FOLLOWING);
    }

    @Override
    public void mercantile$setReturningHomeSync(boolean returning) {
        this.entityData.set(mercantile$DATA_RETURNING_HOME, returning);
    }

    @Override
    public boolean mercantile$isReturningHomeSync() {
        return this.entityData.get(mercantile$DATA_RETURNING_HOME);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/npc/VillagerType;)V",
            at = @At("TAIL"))
    private void mercantile$addFollowGoal(CallbackInfo ci) {
        if (MercantileConfig.get().enableFollowMode) {
            this.goalSelector.addGoal(1, new FollowPlayerGoal((Villager) (Object) this));
            this.goalSelector.addGoal(1, new ReturnHomeGoal((Villager) (Object) this));
        }
    }

    @Unique
    private static final Set<Activity> mercantile$SURVIVAL_ACTIVITIES = Set.of(
            Activity.PANIC, Activity.HIDE, Activity.PRE_RAID, Activity.RAID);

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void mercantile$followClearScheduleTargets(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!FollowManager.isFollowing(self)) return;
        Brain<?> brain = self.getBrain();
        Optional<Activity> active = brain.getActiveNonCoreActivity();
        boolean inSurvival = active.isPresent() && mercantile$SURVIVAL_ACTIVITIES.contains(active.get());
        if (!inSurvival) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
    }


    @Inject(method = "tick", at = @At("TAIL"))
    private void mercantile$followTick(CallbackInfo ci) {
        if (!this.level().isClientSide) return;

        boolean following = this.entityData.get(mercantile$DATA_FOLLOWING);
        boolean returning = this.entityData.get(mercantile$DATA_RETURNING_HOME);
        if (!following && !returning) return;

        if (this.tickCount % 5 == 0) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * 0.6;
            double y = this.getY() + 0.1;
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * 0.6;
            this.level().addParticle(MercantileParticles.FOLLOW_TRAIL, x, y, z, 0, 0.02, 0);
        }
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryToggleFollow(Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableFollowMode) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown()) return;
        if (!player.getMainHandItem().is(Items.EMERALD)) return;

        Villager self = (Villager) (Object) this;

        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) self.level();

        if (FollowManager.isFollowing(self)) {
            java.util.UUID currentTarget = FollowManager.getFollowTarget(self);
            if (currentTarget != null && !currentTarget.equals(player.getUUID())) {
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.follow.denied.other_player")
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            FollowManager.stopFollowing(self);
            self.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, self.getVoicePitch());
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.follow.stop")
                            .withStyle(ChatFormatting.YELLOW), true);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        int followerCount = FollowManager.getFollowerCount(player.getUUID());
        if (followerCount >= MercantileConfig.get().maxFollowingVillagers) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.follow.denied.max",
                            MercantileConfig.get().maxFollowingVillagers)
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (self.isBaby()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.follow.denied.baby")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        boolean started = FollowManager.startFollowing(self, serverPlayer);
        if (!started) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.follow.denied.unavailable")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (!player.getAbilities().instabuild) {
            player.getMainHandItem().shrink(1);
        }

        self.playSound(SoundEvents.VILLAGER_YES, 1.0f, self.getVoicePitch());
        double py = self.getY() + self.getBbHeight() * 0.5;
        serverLevel.sendParticles(MercantileParticles.FOLLOW_TRAIL,
                self.getX(), py, self.getZ(), 10, 0.3, 0.4, 0.3, 0.02);

        serverPlayer.displayClientMessage(
                Component.translatable("mercantile.follow.start")
                        .withStyle(ChatFormatting.GREEN), true);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
