package com.rfizzle.mercantile.mood;

/**
 * Pure mood arithmetic — no Minecraft or Fabric types, so every rule here is
 * unit-testable. {@link MoodManager} supplies the live villager conditions.
 */
public final class MoodMath {

    public static final int MIN_MOOD = 0;
    public static final int MAX_MOOD = 100;
    /** Starting mood for a villager that has never been evaluated. */
    public static final int DEFAULT_MOOD = 50;

    // Condition weights; all satisfied = 100.
    public static final int WEIGHT_BED = 20;
    public static final int WEIGHT_WORKSTATION = 20;
    public static final int WEIGHT_SLEPT_RECENTLY = 20;
    public static final int WEIGHT_WELL_FED = 20;
    public static final int WEIGHT_NOT_HURT = 10;
    public static final int WEIGHT_NO_WITNESSED_DEATH = 10;

    /** Points the stored mood moves toward its target per recalc interval. */
    public static final int DRIFT_PER_RECALC = 2;

    /** Vanilla gap between a villager's restocks within a day (Villager#allowedToRestock). */
    public static final long BASE_RESTOCK_INTERVAL_TICKS = 2400L;

    private MoodMath() {
    }

    /** The mood score the villager's current living conditions pull toward. */
    public static int computeTarget(boolean hasBed, boolean hasWorkstation, boolean sleptRecently,
                                    boolean wellFed, boolean recentlyHurt, boolean witnessedDeath) {
        int target = 0;
        if (hasBed) target += WEIGHT_BED;
        if (hasWorkstation) target += WEIGHT_WORKSTATION;
        if (sleptRecently) target += WEIGHT_SLEPT_RECENTLY;
        if (wellFed) target += WEIGHT_WELL_FED;
        if (!recentlyHurt) target += WEIGHT_NOT_HURT;
        if (!witnessedDeath) target += WEIGHT_NO_WITNESSED_DEATH;
        return target;
    }

    /**
     * Moves {@code current} toward {@code target} by {@link #DRIFT_PER_RECALC} per elapsed
     * recalc interval, never overshooting. Mood changes are gradual by design: removing a
     * villager's bed sours it over minutes, not instantly.
     */
    public static int drift(int current, int target, long elapsedTicks, int recalcIntervalTicks) {
        current = clamp(current);
        target = clamp(target);
        if (elapsedTicks <= 0 || recalcIntervalTicks <= 0 || current == target) return current;
        long steps = Math.min(elapsedTicks / recalcIntervalTicks, MAX_MOOD);
        long delta = Math.min(steps * DRIFT_PER_RECALC, Math.abs(target - current));
        return current + (int) (target > current ? delta : -delta);
    }

    /**
     * Emerald price nudge for the tier: Happy villagers discount, Miserable ones mark up,
     * everyone in between trades at the normal price. Magnitude is {@code percent} of the
     * base price, at least 1 emerald so a small trade still feels the effect.
     */
    public static int priceModifier(MoodTier tier, int basePrice, int percent) {
        if (percent <= 0 || basePrice <= 0) return 0;
        int magnitude = Math.max(1, basePrice * percent / 100);
        return switch (tier) {
            case HAPPY -> -magnitude;
            case MISERABLE -> magnitude;
            case UNHAPPY, CONTENT -> 0;
        };
    }

    /** Restock gap for the tier: Happy restocks sooner, Miserable later. */
    public static long restockIntervalTicks(MoodTier tier, long baseInterval, int speedPercent) {
        if (speedPercent <= 0) return baseInterval;
        return switch (tier) {
            case HAPPY -> Math.max(1L, baseInterval * (100 - speedPercent) / 100);
            case MISERABLE -> baseInterval * (100 + speedPercent) / 100;
            case UNHAPPY, CONTENT -> baseInterval;
        };
    }

    public static int clamp(int mood) {
        return Math.clamp(mood, MIN_MOOD, MAX_MOOD);
    }
}
