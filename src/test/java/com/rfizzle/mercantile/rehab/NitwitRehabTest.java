package com.rfizzle.mercantile.rehab;

import com.rfizzle.mercantile.api.ReputationTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NitwitRehabTest {

    private static final int TRUSTED_MIN = ReputationTier.TRUSTED.minScore();

    @Test
    void reputationGateRequiresTrustedOrAbove() {
        assertFalse(NitwitRehab.meetsReputationRequirement(true, TRUSTED_MIN - 1),
                "one point below Trusted must be denied");
        assertTrue(NitwitRehab.meetsReputationRequirement(true, TRUSTED_MIN),
                "exactly Trusted must pass");
        assertTrue(NitwitRehab.meetsReputationRequirement(true, ReputationTier.HONORED.minScore()),
                "Honored (above Trusted) must pass");
    }

    @Test
    void reputationGateSkippedWhenReputationDisabled() {
        assertTrue(NitwitRehab.meetsReputationRequirement(false, ReputationTier.REVILED.minScore()),
                "with reputation disabled the tier gate is skipped entirely");
    }

    @Test
    void affordabilityIsExactAtTheCost() {
        assertTrue(NitwitRehab.canAfford(false, 16, 16), "exactly the cost must pass");
        assertFalse(NitwitRehab.canAfford(false, 15, 16), "one emerald short must be denied");
        assertTrue(NitwitRehab.canAfford(false, 0, 0), "a zero cost needs no emeralds");
    }

    @Test
    void creativePlayersAlwaysAfford() {
        assertTrue(NitwitRehab.canAfford(true, 0, 16));
    }
}
