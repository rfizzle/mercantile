package com.rfizzle.mercantile.breeding;

/**
 * Pure math for feeding baby villagers to accelerate growth. A feed removes a configured
 * percentage of the remaining growth time, scaled by the villager food value of the item
 * (bread, worth {@link #BREAD_FOOD_POINTS} points, gets the full percentage; a 1-point
 * beetroot gets a quarter of it). Cumulative acceleration per baby is capped at a
 * configured percentage of the full vanilla growth span.
 */
public final class BabyFeeding {

    /** Vanilla bred-baby starting growth time in ticks (age -24000). */
    public static final int FULL_GROWTH_TICKS = 24000;

    /** Food points of bread — the baseline item that earns the full configured percentage. */
    public static final int BREAD_FOOD_POINTS = 4;

    private BabyFeeding() {}

    /** Total ticks of acceleration a single baby may ever receive. */
    public static int maxTotalReductionTicks(int capPercent) {
        return (int) ((long) FULL_GROWTH_TICKS * Math.clamp(capPercent, 0, 100) / 100L);
    }

    /** Acceleration ticks still available given what this baby has already been fed. */
    public static int remainingBudget(int fedTicks, int capPercent) {
        return Math.max(0, maxTotalReductionTicks(capPercent) - Math.max(0, fedTicks));
    }

    /**
     * Growth-time reduction for one feed, before the cap budget is applied: a food-value-weighted
     * share of the remaining time, never more than the remaining time, and at least 1 tick so a
     * valid feed always visibly progresses growth.
     */
    public static int computeReduction(int remainingTicks, int foodPoints, int percentPerFeed) {
        if (remainingTicks <= 0 || foodPoints <= 0 || percentPerFeed <= 0) return 0;
        long reduction = (long) remainingTicks * percentPerFeed * foodPoints
                / (100L * BREAD_FOOD_POINTS);
        return (int) Math.min(Math.max(1L, reduction), remainingTicks);
    }
}
