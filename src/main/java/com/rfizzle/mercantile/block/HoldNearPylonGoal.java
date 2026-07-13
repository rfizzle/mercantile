package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;

import java.util.EnumSet;

/**
 * Keeps an idle sentry golem home. While a sentry has no combat target it walks back toward its
 * pylon and holds within a few blocks of it, so the golem visibly waits out the pylon's despawn
 * countdown at its source instead of running vanilla village goals (strolling wherever the village
 * takes it, offering poppies to villagers) or ping-ponging against the radius edge.
 *
 * <p>The goal claims only {@link Flag#MOVE}, so — registered at priority 1 — it outranks and
 * suppresses vanilla's movement goals ({@code MoveBackToVillageGoal} at 2,
 * {@code GolemRandomStrollInVillageGoal} at 4, {@code OfferFlowerGoal} at 5) while idle, and leaves
 * the golem's look goals untouched. It shares priority 1 with vanilla's {@code MeleeAttackGoal} but
 * never contends with it: this goal is inert whenever the golem has a target, and melee is inert
 * whenever it does not, so the two are mutually exclusive by their engagement conditions.
 *
 * <p>Distinct from {@link ReturnToPylonGoal} (priority 0): that goal is the combat leash that hauls a
 * sentry pushed <em>outside</em> the radius back in, regardless of target. This goal is the idle hold
 * <em>inside</em> the radius, only while unengaged. When a sentry is dragged out mid-fight the leash
 * (priority 0) runs first; once back inside and disengaged, this goal takes over the hold.
 *
 * <p>Despawn itself is driven by the pylon's countdown, not by this goal, so a sentry that cannot
 * path home (walled off, stuck) still expires on schedule wherever it stands — it simply never
 * reaches the hold radius.
 */
public class HoldNearPylonGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final int RECALC_INTERVAL = 20;
    /** How close (blocks) the golem settles to its pylon before it stops pathing and just waits. */
    private static final double HOLD_RADIUS = 4.0;
    private static final double HOLD_RADIUS_SQ = HOLD_RADIUS * HOLD_RADIUS;

    private final IronGolem golem;
    private BlockPos pylonPos;
    private int recalcCooldown;

    public HoldNearPylonGoal(IronGolem golem) {
        this.golem = golem;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SentryGolemTag.isSentry(golem)) return false;
        // A golem with a target is fighting (or about to) — yield to the target/melee goals.
        if (golem.getTarget() != null) return false;
        BlockPos pos = SentryGolemTag.getPylonPos(golem);
        if (pos == null) return false;
        if (golem.level().getBlockState(pos).getBlock() != MercantileRegistry.SENTRY_PYLON) {
            return false;
        }
        this.pylonPos = pos;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (pylonPos == null) return false;
        if (!SentryGolemTag.isSentry(golem)) return false;
        if (golem.getTarget() != null) return false;
        return golem.level().getBlockState(pylonPos).getBlock() == MercantileRegistry.SENTRY_PYLON;
    }

    @Override
    public void start() {
        this.recalcCooldown = 0;
    }

    @Override
    public void stop() {
        this.pylonPos = null;
        this.golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (pylonPos == null) return;
        if (--recalcCooldown > 0) return;
        recalcCooldown = RECALC_INTERVAL;

        if (squaredDist(pylonPos) > HOLD_RADIUS_SQ) {
            golem.getNavigation().moveTo(pylonPos.getX() + 0.5, pylonPos.getY(), pylonPos.getZ() + 0.5, SPEED);
        } else {
            golem.getNavigation().stop();
        }
    }

    private double squaredDist(BlockPos pos) {
        double dx = golem.getX() - (pos.getX() + 0.5);
        double dy = golem.getY() - (pos.getY() + 0.5);
        double dz = golem.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
