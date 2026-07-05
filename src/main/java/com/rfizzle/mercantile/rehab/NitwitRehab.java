package com.rfizzle.mercantile.rehab;

import com.rfizzle.mercantile.api.ReputationTier;

/**
 * Pure decision logic for nitwit rehabilitation (issue #91): a Trusted+ player converts an adult
 * nitwit into an unemployed villager by using a golden apple on it and paying an emerald fee.
 * No game classes; unit-testable.
 */
public final class NitwitRehab {

    /** Minimum reputation standing required to rehabilitate a nitwit. */
    public static final ReputationTier REQUIRED_TIER = ReputationTier.TRUSTED;

    /** Delay between paying the cost and the conversion landing (3 seconds). */
    public static final int CONVERSION_DELAY_TICKS = 60;

    private NitwitRehab() {
    }

    /**
     * Whether the player's standing clears the {@link #REQUIRED_TIER} gate. When the reputation
     * system is disabled the gate is skipped entirely and only the item/emerald cost applies.
     */
    public static boolean meetsReputationRequirement(boolean reputationEnabled, int score) {
        return !reputationEnabled || score >= REQUIRED_TIER.minScore();
    }

    /** Whether the player can cover the emerald fee. Creative players always can. */
    public static boolean canAfford(boolean creative, int emeraldCount, int emeraldCost) {
        return creative || emeraldCount >= emeraldCost;
    }
}
