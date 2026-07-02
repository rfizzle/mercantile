package com.rfizzle.mercantile.compat.tribulation;

import com.rfizzle.mercantile.config.MercantileConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TribulationCompatScalingTest {

    private static MercantileConfig defaults() {
        return new MercantileConfig();
    }

    @Test
    void tierZeroReturnsBaseConfigValues() {
        MercantileConfig config = defaults();
        TribulationCompat.EffectivePylonLimits limits = TribulationCompat.scaledLimits(0, config);
        assertEquals(config.pylonMaxGolems, limits.maxGolems());
        assertEquals(config.pylonDetectionRadius, limits.detectionRadius());
    }

    @Test
    void eachTierAddsConfiguredBonuses() {
        MercantileConfig config = defaults();
        TribulationCompat.EffectivePylonLimits limits = TribulationCompat.scaledLimits(2, config);
        assertEquals(config.pylonMaxGolems + 2 * config.pylonTribulationGolemBonusPerTier, limits.maxGolems());
        assertEquals(config.pylonDetectionRadius + 2 * config.pylonTribulationRadiusBonusPerTier, limits.detectionRadius());
    }

    @Test
    void golemCountClampsToTribulationCap() {
        MercantileConfig config = defaults();
        config.pylonTribulationGolemBonusPerTier = 10;
        TribulationCompat.EffectivePylonLimits limits = TribulationCompat.scaledLimits(5, config);
        assertEquals(config.pylonTribulationMaxGolems, limits.maxGolems());
    }

    @Test
    void capBelowBaseNeverShrinksTheGolemFloor() {
        MercantileConfig config = defaults();
        config.pylonMaxGolems = 8;
        config.pylonTribulationMaxGolems = 6; // clamp() would heal this; scaling must too
        TribulationCompat.EffectivePylonLimits limits = TribulationCompat.scaledLimits(5, config);
        assertEquals(8, limits.maxGolems());
    }

    @Test
    void radiusClampsToHardCeiling() {
        MercantileConfig config = defaults();
        config.pylonTribulationRadiusBonusPerTier = 1000;
        TribulationCompat.EffectivePylonLimits limits = TribulationCompat.scaledLimits(5, config);
        assertEquals(TribulationCompat.MAX_DETECTION_RADIUS, limits.detectionRadius());
    }

    @Test
    void tierMappingIsInclusiveAtThresholds() {
        int[] thresholds = {50, 100, 150, 200, 250};
        assertEquals(0, TribulationCompat.tierFor(0, thresholds));
        assertEquals(0, TribulationCompat.tierFor(49, thresholds));
        assertEquals(1, TribulationCompat.tierFor(50, thresholds));
        assertEquals(2, TribulationCompat.tierFor(149, thresholds));
        assertEquals(3, TribulationCompat.tierFor(150, thresholds));
        assertEquals(5, TribulationCompat.tierFor(250, thresholds));
        assertEquals(5, TribulationCompat.tierFor(9999, thresholds));
    }
}
