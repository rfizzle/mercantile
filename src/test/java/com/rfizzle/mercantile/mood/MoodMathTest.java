package com.rfizzle.mercantile.mood;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoodMathTest {

    @Test
    void allConditionsMetReachesMax() {
        assertEquals(100, MoodMath.computeTarget(true, true, true, true, false, false));
    }

    @Test
    void noConditionsMetIsZero() {
        assertEquals(0, MoodMath.computeTarget(false, false, false, false, true, true));
    }

    @Test
    void targetWeightsSumAsDocumented() {
        assertEquals(MoodMath.WEIGHT_BED, MoodMath.computeTarget(true, false, false, false, true, true));
        assertEquals(MoodMath.WEIGHT_WORKSTATION, MoodMath.computeTarget(false, true, false, false, true, true));
        assertEquals(MoodMath.WEIGHT_SLEPT_RECENTLY, MoodMath.computeTarget(false, false, true, false, true, true));
        assertEquals(MoodMath.WEIGHT_WELL_FED, MoodMath.computeTarget(false, false, false, true, true, true));
        assertEquals(MoodMath.WEIGHT_NOT_HURT, MoodMath.computeTarget(false, false, false, false, false, true));
        assertEquals(MoodMath.WEIGHT_NO_WITNESSED_DEATH, MoodMath.computeTarget(false, false, false, false, true, false));
    }

    @Test
    void tierBoundaries() {
        assertEquals(MoodTier.MISERABLE, MoodTier.fromMood(0));
        assertEquals(MoodTier.MISERABLE, MoodTier.fromMood(24));
        assertEquals(MoodTier.UNHAPPY, MoodTier.fromMood(25));
        assertEquals(MoodTier.UNHAPPY, MoodTier.fromMood(49));
        assertEquals(MoodTier.CONTENT, MoodTier.fromMood(50));
        assertEquals(MoodTier.CONTENT, MoodTier.fromMood(79));
        assertEquals(MoodTier.HAPPY, MoodTier.fromMood(80));
        assertEquals(MoodTier.HAPPY, MoodTier.fromMood(100));
    }

    @Test
    void driftMovesTowardTargetGradually() {
        // 5 intervals elapsed at 2 points per interval = 10 points of movement.
        assertEquals(60, MoodMath.drift(50, 100, 500, 100));
        assertEquals(40, MoodMath.drift(50, 0, 500, 100));
    }

    @Test
    void driftNeverOvershootsTarget() {
        assertEquals(100, MoodMath.drift(50, 100, 1_000_000, 100));
        assertEquals(0, MoodMath.drift(50, 0, 1_000_000, 100));
    }

    @Test
    void driftNoElapsedTimeIsNoOp() {
        assertEquals(50, MoodMath.drift(50, 100, 0, 100));
        assertEquals(50, MoodMath.drift(50, 100, 99, 100));
    }

    @Test
    void driftFullSwingConverges() {
        // Acceptance: full condition swing walks the score from one extreme to the other over time.
        int mood = 100;
        for (int i = 0; i < 100; i++) {
            mood = MoodMath.drift(mood, 0, 100, 100);
        }
        assertEquals(0, mood);
    }

    @Test
    void happyDiscountsAndMiserableMarksUp() {
        assertEquals(-3, MoodMath.priceModifier(MoodTier.HAPPY, 64, 5));
        assertEquals(3, MoodMath.priceModifier(MoodTier.MISERABLE, 64, 5));
        assertEquals(0, MoodMath.priceModifier(MoodTier.CONTENT, 64, 5));
        assertEquals(0, MoodMath.priceModifier(MoodTier.UNHAPPY, 64, 5));
    }

    @Test
    void priceModifierAtLeastOneEmeraldOnSmallTrades() {
        assertEquals(-1, MoodMath.priceModifier(MoodTier.HAPPY, 1, 5));
        assertEquals(1, MoodMath.priceModifier(MoodTier.MISERABLE, 1, 5));
    }

    @Test
    void priceModifierZeroPercentDisables() {
        assertEquals(0, MoodMath.priceModifier(MoodTier.HAPPY, 64, 0));
        assertEquals(0, MoodMath.priceModifier(MoodTier.MISERABLE, 64, 0));
    }

    @Test
    void restockIntervalScalesByTier() {
        assertEquals(1920, MoodMath.restockIntervalTicks(MoodTier.HAPPY, 2400, 20));
        assertEquals(2880, MoodMath.restockIntervalTicks(MoodTier.MISERABLE, 2400, 20));
        assertEquals(2400, MoodMath.restockIntervalTicks(MoodTier.CONTENT, 2400, 20));
        assertEquals(2400, MoodMath.restockIntervalTicks(MoodTier.UNHAPPY, 2400, 20));
    }

    @Test
    void restockIntervalZeroPercentDisables() {
        assertEquals(2400, MoodMath.restockIntervalTicks(MoodTier.HAPPY, 2400, 0));
    }
}
