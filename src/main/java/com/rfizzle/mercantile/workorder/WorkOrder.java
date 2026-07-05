package com.rfizzle.mercantile.workorder;

/**
 * Pure decision logic for work orders (issue #90): sneak + right-clicking an unemployed adult
 * villager with a workstation item sends it to claim the nearest unclaimed workstation of that
 * type for a small emerald fee. No game classes; unit-testable.
 */
public final class WorkOrder {

    /**
     * How far around the villager to search for an unclaimed workstation, in blocks — matches
     * vanilla {@code AcquirePoi}'s job-site scan range so an order can never reach a site the
     * villager couldn't have found on its own.
     */
    public static final int SEARCH_RADIUS = 48;

    /**
     * How many closest free sites to try a path to before denying the order — vanilla
     * {@code AcquirePoi}'s candidate cap.
     */
    public static final int MAX_CANDIDATES = 5;

    private WorkOrder() {
    }

    /**
     * Only unemployed adults take work orders. Nitwits hold the NITWIT profession (not NONE), so
     * the unemployed check excludes them as well as every employed villager.
     */
    public static boolean isEligibleTarget(boolean baby, boolean unemployed) {
        return !baby && unemployed;
    }

    /** Whether the player can cover the emerald fee. Creative players always can. */
    public static boolean canAfford(boolean creative, int emeraldCount, int emeraldCost) {
        return creative || emeraldCount >= emeraldCost;
    }
}
