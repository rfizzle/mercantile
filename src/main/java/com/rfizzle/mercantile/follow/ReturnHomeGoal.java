package com.rfizzle.mercantile.follow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.EnumSet;
import java.util.Optional;

public class ReturnHomeGoal extends Goal {
    private static final double SPEED = 0.5;
    private final Villager villager;
    private final FollowableVillager followable;

    public ReturnHomeGoal(Villager villager) {
        this.villager = villager;
        this.followable = (FollowableVillager) villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return followable.mercantile$isReturningHomeSync();
    }

    @Override
    public boolean canContinueToUse() {
        return followable.mercantile$isReturningHomeSync() && !villager.getNavigation().isDone();
    }

    @Override
    public void start() {
        Optional<GlobalPos> homePos = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (homePos.isPresent() && homePos.get().dimension() == villager.level().dimension()) {
            villager.getNavigation().moveTo(homePos.get().pos().getX(), homePos.get().pos().getY(), homePos.get().pos().getZ(), SPEED);
            return;
        }

        Optional<GlobalPos> jobPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobPos.isPresent() && jobPos.get().dimension() == villager.level().dimension()) {
            villager.getNavigation().moveTo(jobPos.get().pos().getX(), jobPos.get().pos().getY(), jobPos.get().pos().getZ(), SPEED);
            return;
        }

        // If neither exists, stop immediately
        followable.mercantile$setReturningHomeSync(false);
    }

    @Override
    public void tick() {
        BlockPos target = villager.getNavigation().getTargetPos();
        if (target != null && villager.distanceToSqr(target.getX(), target.getY(), target.getZ()) < 4.0) {
            followable.mercantile$setReturningHomeSync(false);
            villager.getNavigation().stop();
        }

        if (villager.getNavigation().isStuck() || villager.getNavigation().isDone()) {
            followable.mercantile$setReturningHomeSync(false);
        }
    }

    @Override
    public void stop() {
        followable.mercantile$setReturningHomeSync(false);
        villager.getNavigation().stop();
    }
}
