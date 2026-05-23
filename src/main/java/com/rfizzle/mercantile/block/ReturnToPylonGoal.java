package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;

import java.util.EnumSet;

public class ReturnToPylonGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final int RECALC_INTERVAL = 20;

    private final IronGolem golem;
    private BlockPos pylonPos;
    private int recalcCooldown;

    public ReturnToPylonGoal(IronGolem golem) {
        this.golem = golem;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SentryGolemTag.isSentry(golem)) return false;
        BlockPos pos = SentryGolemTag.getPylonPos(golem);
        if (pos == null) return false;
        if (golem.level().getBlockState(pos).getBlock() != MercantileRegistry.SENTRY_PYLON) {
            return false;
        }
        int radius = MercantileConfig.get().pylonDetectionRadius;
        long maxSq = (long) radius * (long) radius;
        if (squaredDist(pos) <= maxSq) {
            return false;
        }
        this.pylonPos = pos;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (pylonPos == null) return false;
        if (!SentryGolemTag.isSentry(golem)) return false;
        if (golem.level().getBlockState(pylonPos).getBlock() != MercantileRegistry.SENTRY_PYLON) {
            return false;
        }
        int radius = MercantileConfig.get().pylonDetectionRadius;
        int hysteresis = Math.max(0, radius - 2);
        long hystSq = (long) hysteresis * (long) hysteresis;
        return squaredDist(pylonPos) > hystSq;
    }

    @Override
    public void start() {
        this.recalcCooldown = 0;
        this.golem.setTarget(null);
    }

    @Override
    public void stop() {
        this.pylonPos = null;
        this.golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (pylonPos == null) return;

        if (golem.getTarget() != null) {
            int radius = MercantileConfig.get().pylonDetectionRadius;
            long maxSq = (long) radius * (long) radius;
            double dx = golem.getTarget().getX() - (pylonPos.getX() + 0.5);
            double dy = golem.getTarget().getY() - (pylonPos.getY() + 0.5);
            double dz = golem.getTarget().getZ() - (pylonPos.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz > maxSq) {
                golem.setTarget(null);
            }
        }

        if (--recalcCooldown > 0) return;
        recalcCooldown = RECALC_INTERVAL;
        golem.getNavigation().moveTo(pylonPos.getX() + 0.5, pylonPos.getY(), pylonPos.getZ() + 0.5, SPEED);
    }

    private double squaredDist(BlockPos pos) {
        double dx = golem.getX() - (pos.getX() + 0.5);
        double dy = golem.getY() - (pos.getY() + 0.5);
        double dz = golem.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
