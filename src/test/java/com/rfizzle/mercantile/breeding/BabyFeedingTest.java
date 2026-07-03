package com.rfizzle.mercantile.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BabyFeedingTest {

    @Test
    void breadTakesFullPercentOfRemaining() {
        // 10% of 24000 remaining at bread's 4 food points.
        assertEquals(2400, BabyFeeding.computeReduction(24000, 4, 10));
    }

    @Test
    void lowValueFoodScalesDown() {
        // Beetroot (1 point) earns a quarter of bread's reduction.
        assertEquals(600, BabyFeeding.computeReduction(24000, 1, 10));
        assertTrue(BabyFeeding.computeReduction(24000, 4, 10)
                > BabyFeeding.computeReduction(24000, 1, 10));
    }

    @Test
    void reductionShrinksAsRemainingShrinks() {
        assertEquals(240, BabyFeeding.computeReduction(2400, 4, 10));
    }

    @Test
    void reductionIsAtLeastOneTick() {
        assertEquals(1, BabyFeeding.computeReduction(5, 1, 10));
    }

    @Test
    void reductionNeverExceedsRemaining() {
        assertEquals(3, BabyFeeding.computeReduction(3, 4, 100));
    }

    @Test
    void zeroInputsProduceZeroReduction() {
        assertEquals(0, BabyFeeding.computeReduction(0, 4, 10));
        assertEquals(0, BabyFeeding.computeReduction(24000, 0, 10));
        assertEquals(0, BabyFeeding.computeReduction(24000, 4, 0));
    }

    @Test
    void maxTotalReductionScalesWithCap() {
        assertEquals(12000, BabyFeeding.maxTotalReductionTicks(50));
        assertEquals(0, BabyFeeding.maxTotalReductionTicks(0));
        assertEquals(BabyFeeding.FULL_GROWTH_TICKS, BabyFeeding.maxTotalReductionTicks(100));
    }

    @Test
    void maxTotalReductionClampsOutOfRangeCap() {
        assertEquals(0, BabyFeeding.maxTotalReductionTicks(-5));
        assertEquals(BabyFeeding.FULL_GROWTH_TICKS, BabyFeeding.maxTotalReductionTicks(150));
    }

    @Test
    void remainingBudgetShrinksAndBottomsOutAtZero() {
        assertEquals(12000, BabyFeeding.remainingBudget(0, 50));
        assertEquals(2000, BabyFeeding.remainingBudget(10000, 50));
        assertEquals(0, BabyFeeding.remainingBudget(12000, 50));
        assertEquals(0, BabyFeeding.remainingBudget(20000, 50));
    }

    @Test
    void negativeFedTicksTreatedAsZero() {
        assertEquals(12000, BabyFeeding.remainingBudget(-100, 50));
    }
}
