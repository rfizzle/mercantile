package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

/**
 * Target goal for sentry golems. Unlike vanilla's iron-golem targeting — which explicitly
 * excludes creepers — this reuses {@link SentryPylonScanner#isHostile} so the sentry attacks
 * the same set of mobs the pylon treats as threats (creepers included), while still ignoring
 * other golems and fellow sentries.
 *
 * <p>The sentry's aggro range matches the pylon's detection radius: {@link #getFollowDistance()}
 * returns {@code pylonDetectionRadius} so both the entity search box and the combat range scale
 * with the config, and the candidate filter rejects any hostile outside the defended sphere
 * (measured from the pylon, not the golem) so a sentry never acquires — and so never chases —
 * a threat beyond the zone it guards.
 *
 * <p>The sentry tag is applied after the golem spawns, so {@link #canUse()} re-checks it at
 * runtime: on a plain iron golem this goal never engages and vanilla targeting (registered at a
 * lower priority) takes over.
 */
public class SentryTargetHostilesGoal extends NearestAttackableTargetGoal<Mob> {
    private final IronGolem golem;

    public SentryTargetHostilesGoal(IronGolem golem) {
        super(golem, Mob.class, 5, false, false, (e) -> withinDefendedZone(golem, e));
        this.golem = golem;
    }

    /**
     * Aggro range tracks the pylon's detection radius rather than the vanilla iron-golem
     * {@code FOLLOW_RANGE}. The superclass freezes the combat-range condition from this value at
     * construction time, and {@link #getTargetSearchArea(double)} re-reads it each scan — so both
     * the targeting range and the search box follow the config.
     */
    @Override
    protected double getFollowDistance() {
        return MercantileConfig.get().pylonDetectionRadius;
    }

    /**
     * Vanilla {@code TargetGoal} caps the vertical search at ±4 blocks; widen it to the full radius
     * so a sentry spots threats above or below it (on walls, ledges, ravines) out to the same range
     * the pylon scans. The combat-range condition keeps the effective shape a sphere.
     */
    @Override
    protected AABB getTargetSearchArea(double distance) {
        return this.golem.getBoundingBox().inflate(distance, distance, distance);
    }

    private static boolean withinDefendedZone(IronGolem golem, LivingEntity candidate) {
        if (!SentryPylonScanner.isHostile(candidate)) {
            return false;
        }
        BlockPos pylon = SentryGolemTag.getPylonPos(golem);
        if (pylon == null) {
            // Unmarked golem (the goal is inert via canUse) or a sentry whose pylon link is
            // missing — fall back to the plain hostile check rather than blocking all targets.
            return true;
        }
        int radius = MercantileConfig.get().pylonDetectionRadius;
        double dx = candidate.getX() - (pylon.getX() + 0.5);
        double dy = candidate.getY() - (pylon.getY() + 0.5);
        double dz = candidate.getZ() - (pylon.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) radius * (double) radius;
    }

    @Override
    public boolean canUse() {
        return SentryGolemTag.isSentry(golem) && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return SentryGolemTag.isSentry(golem) && super.canContinueToUse();
    }
}
