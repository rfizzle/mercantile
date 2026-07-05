package com.rfizzle.mercantile.workorder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderTest {

    @Test
    void onlyUnemployedAdultsAreEligible() {
        assertTrue(WorkOrder.isEligibleTarget(false, true), "an unemployed adult must be eligible");
        assertFalse(WorkOrder.isEligibleTarget(true, true), "a baby must never take a work order");
        assertFalse(WorkOrder.isEligibleTarget(false, false),
                "an employed villager (or nitwit — profession NITWIT, not NONE) must be excluded");
        assertFalse(WorkOrder.isEligibleTarget(true, false), "a baby with a profession is doubly excluded");
    }

    @Test
    void affordabilityIsExactAtTheCost() {
        assertTrue(WorkOrder.canAfford(false, 1, 1), "exactly the cost must pass");
        assertFalse(WorkOrder.canAfford(false, 0, 1), "one emerald short must be denied");
        assertTrue(WorkOrder.canAfford(false, 0, 0), "a zero cost needs no emeralds");
    }

    @Test
    void creativePlayersAlwaysAfford() {
        assertTrue(WorkOrder.canAfford(true, 0, 64));
    }

    @Test
    void searchRadiusMatchesVanillaJobSiteScanRange() {
        // AcquirePoi scans 48 blocks; a work order must never out-range what the villager could
        // have found on its own.
        assertEquals(48, WorkOrder.SEARCH_RADIUS);
    }
}
