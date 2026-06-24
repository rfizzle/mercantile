package com.rfizzle.mercantile.block;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Target goal for sentry golems. Unlike vanilla's iron-golem targeting — which explicitly
 * excludes creepers — this reuses {@link SentryPylonScanner#isHostile} so the sentry attacks
 * the same set of mobs the pylon treats as threats (creepers included), while still ignoring
 * other golems and fellow sentries.
 *
 * <p>The sentry tag is applied after the golem spawns, so {@link #canUse()} re-checks it at
 * runtime: on a plain iron golem this goal never engages and vanilla targeting (registered at a
 * lower priority) takes over.
 */
public class SentryTargetHostilesGoal extends NearestAttackableTargetGoal<Mob> {
    private final IronGolem golem;

    public SentryTargetHostilesGoal(IronGolem golem) {
        super(golem, Mob.class, 5, false, false, SentryPylonScanner::isHostile);
        this.golem = golem;
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
