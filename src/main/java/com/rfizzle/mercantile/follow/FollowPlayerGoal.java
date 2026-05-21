package com.rfizzle.mercantile.follow;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class FollowPlayerGoal extends Goal {

    private static final double FOLLOW_DISTANCE_SQR = 6.0 * 6.0;
    private static final double TELEPORT_DISTANCE_SQR = 32.0 * 32.0;
    private static final double SPEED = 0.5;
    private static final int RECALC_INTERVAL = 10;

    private final Villager villager;
    private @Nullable Player target;
    private int recalcCooldown;

    public FollowPlayerGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        UUID targetUuid = FollowManager.getFollowTarget(villager);
        if (targetUuid == null) return false;

        Player player = villager.level().getPlayerByUUID(targetUuid);
        if (player == null || player.isSpectator() || player.isDeadOrDying()) {
            FollowManager.stopFollowing(villager);
            return false;
        }

        this.target = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (!FollowManager.isFollowing(villager)) {
            return false;
        }
        if (villager.distanceToSqr(target) > TELEPORT_DISTANCE_SQR) {
            FollowManager.stopFollowing(villager);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.recalcCooldown = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;

        this.villager.getLookControl().setLookAt(target, 10.0f, (float) villager.getMaxHeadXRot());

        if (--recalcCooldown > 0) return;
        recalcCooldown = RECALC_INTERVAL;

        double distSqr = villager.distanceToSqr(target);
        if (distSqr > FOLLOW_DISTANCE_SQR) {
            villager.getNavigation().moveTo(target, SPEED);
        } else {
            villager.getNavigation().stop();
        }
    }
}
