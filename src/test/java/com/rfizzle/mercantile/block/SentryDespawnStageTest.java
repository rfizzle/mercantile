package com.rfizzle.mercantile.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SentryDespawnStageTest {

    private static final int TELEGRAPH = 60;

    // --- No cracks until the countdown enters its telegraph window ---

    @Test
    void stageIsZeroEarlyInCountdown() {
        int threshold = 600; // 30s default
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(0, threshold, TELEGRAPH),
                "a fresh countdown shows no cracks");
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(threshold - TELEGRAPH - 1, threshold, TELEGRAPH),
                "still no cracks one tick before the window opens");
    }

    @Test
    void stageIsZeroExactlyAtWindowEdge() {
        int threshold = 600;
        // remaining == window -> not yet inside the window
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(threshold - TELEGRAPH, threshold, TELEGRAPH),
                "the tick where remaining == window is still crack-free");
        assertEquals(1, SentryPylonBlockEntity.despawnStageFor(threshold - TELEGRAPH + 1, threshold, TELEGRAPH),
                "the first tick inside the window shows the lightest cracks");
    }

    // --- Escalates through the window and maxes out at expiry ---

    @Test
    void stageEscalatesThroughThirds() {
        int threshold = 600;
        // window 60: thirds at remaining 40 (stage 1->2 boundary) and 20 (stage 2->3 boundary).
        assertEquals(1, SentryPylonBlockEntity.despawnStageFor(threshold - 60 + 1, threshold, TELEGRAPH));
        assertEquals(1, SentryPylonBlockEntity.despawnStageFor(threshold - 41, threshold, TELEGRAPH));
        assertEquals(2, SentryPylonBlockEntity.despawnStageFor(threshold - 40, threshold, TELEGRAPH));
        assertEquals(2, SentryPylonBlockEntity.despawnStageFor(threshold - 21, threshold, TELEGRAPH));
        assertEquals(3, SentryPylonBlockEntity.despawnStageFor(threshold - 20, threshold, TELEGRAPH));
    }

    @Test
    void stageIsThreeAtAndAfterExpiry() {
        int threshold = 600;
        assertEquals(3, SentryPylonBlockEntity.despawnStageFor(threshold, threshold, TELEGRAPH),
                "the countdown expiring shows full cracks");
        assertEquals(3, SentryPylonBlockEntity.despawnStageFor(threshold + 5, threshold, TELEGRAPH),
                "past expiry stays fully cracked");
    }

    @Test
    void stageIsMonotonicNonDecreasing() {
        int threshold = 600;
        int prev = SentryPylonBlockEntity.despawnStageFor(0, threshold, TELEGRAPH);
        for (int idle = 1; idle <= threshold; idle++) {
            int current = SentryPylonBlockEntity.despawnStageFor(idle, threshold, TELEGRAPH);
            assertTrue(current >= prev,
                    "crack stage must never regress as the countdown runs (idle=" + idle + ")");
            assertTrue(current >= 0 && current <= 3, "stage stays in [0,3] (idle=" + idle + ")");
            prev = current;
        }
    }

    // --- A short countdown caps the window at the countdown itself, still escalating fully ---

    @Test
    void windowCapsAtShortCountdown() {
        int threshold = 30; // 1.5s — shorter than the 60-tick telegraph
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(0, threshold, TELEGRAPH),
                "even a short countdown starts crack-free");
        // window == threshold == 30; thirds at remaining 20 and 10.
        assertEquals(1, SentryPylonBlockEntity.despawnStageFor(1, threshold, TELEGRAPH));
        assertEquals(2, SentryPylonBlockEntity.despawnStageFor(threshold - 20, threshold, TELEGRAPH));
        assertEquals(3, SentryPylonBlockEntity.despawnStageFor(threshold - 10, threshold, TELEGRAPH));
        assertEquals(3, SentryPylonBlockEntity.despawnStageFor(threshold, threshold, TELEGRAPH));
    }

    // --- Degenerate inputs stay safe ---

    @Test
    void nonPositiveThresholdIsZero() {
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(5, 0, TELEGRAPH));
        assertEquals(0, SentryPylonBlockEntity.despawnStageFor(5, -10, TELEGRAPH));
    }
}
