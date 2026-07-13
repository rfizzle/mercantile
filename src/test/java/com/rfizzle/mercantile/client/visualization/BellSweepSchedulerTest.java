package com.rfizzle.mercantile.client.visualization;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage for {@link BellSweepScheduler} (issue #163). The scheduler is Minecraft-free, so
 * the budget/coverage/promotion logic that governs the client-side placed-bell sweep tests without a
 * client — the seam that keeps the discovery cadence honest even though the {@code getChunkNow} shell
 * around it cannot be gametested.
 */
class BellSweepSchedulerTest {

    /** Drive one full pass at the given radius/budget, returning every chunk index it visited. */
    private static Set<Integer> runFullPass(BellSweepScheduler scheduler, int radius, int budget) {
        Set<Integer> visited = new HashSet<>();
        int total = BellSweepScheduler.total(radius);
        // A pass spans ceil(total/budget) ticks; run a couple extra to be safe, dedup by set.
        int ticks = total / budget + 2;
        for (int t = 0; t < ticks && visited.size() < total; t++) {
            for (int index : scheduler.nextChunkIndices(radius, budget)) {
                visited.add(index);
            }
        }
        return visited;
    }

    @Test
    void offsetMapping_coversSquareCenteredOnPlayer() {
        int radius = 2; // 5x5 = 25 chunks
        Set<String> offsets = new HashSet<>();
        for (int index = 0; index < BellSweepScheduler.total(radius); index++) {
            int dx = BellSweepScheduler.offsetX(index, radius);
            int dz = BellSweepScheduler.offsetZ(index, radius);
            assertTrue(dx >= -radius && dx <= radius, "dx in range");
            assertTrue(dz >= -radius && dz <= radius, "dz in range");
            offsets.add(dx + "," + dz);
        }
        assertEquals(BellSweepScheduler.total(radius), offsets.size(),
                "every (dx,dz) in the square must be hit exactly once");
    }

    @Test
    void fullPass_visitsEveryChunkExactlyOnce() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int radius = 3; // 7x7 = 49
        Set<Integer> visited = runFullPass(scheduler, radius, 8);
        assertEquals(BellSweepScheduler.total(radius), visited.size(),
                "a full pass must visit all chunk indices");
    }

    @Test
    void batch_neverExceedsBudget() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int radius = 4;
        int budget = 10;
        for (int t = 0; t < 20; t++) {
            assertTrue(scheduler.nextChunkIndices(radius, budget).length <= budget,
                    "no tick may scan more than the chunk budget");
        }
    }

    @Test
    void publishedBells_emptyUntilFirstPassCompletes() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int radius = 2; // 25 chunks
        int budget = 8; // pass spans 4 ticks

        scheduler.nextChunkIndices(radius, budget); // tick 1
        scheduler.recordBell(100L);
        assertTrue(scheduler.publishedBells().isEmpty(), "nothing published mid first pass");
        scheduler.nextChunkIndices(radius, budget); // tick 2
        scheduler.nextChunkIndices(radius, budget); // tick 3
        scheduler.nextChunkIndices(radius, budget); // tick 4 — cursor wraps, pass complete
        assertTrue(scheduler.publishedBells().isEmpty(), "still empty until the next pass begins");

        scheduler.nextChunkIndices(radius, budget); // first tick of pass 2 — promotes pass 1
        assertEquals(Set.of(100L), scheduler.publishedBells(),
                "the completed pass's finds are promoted at the start of the next pass");
    }

    @Test
    void brokenBell_disappearsAfterNextPass() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int radius = 1; // 9 chunks
        int budget = 9; // one tick per pass

        // Pass 1 records two bells.
        scheduler.nextChunkIndices(radius, budget);
        scheduler.recordBell(1L);
        scheduler.recordBell(2L);
        // Pass 2 promotes pass 1, records only one bell (the other was "broken").
        scheduler.nextChunkIndices(radius, budget);
        assertEquals(Set.of(1L, 2L), scheduler.publishedBells());
        scheduler.recordBell(1L);
        // Pass 3 promotes pass 2 — the missing bell is gone.
        scheduler.nextChunkIndices(radius, budget);
        assertEquals(Set.of(1L), scheduler.publishedBells(),
                "a bell absent from the latest pass drops out of the published set");
    }

    @Test
    void radiusChange_restartsSweepCursor() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        // Partially advance a pass at radius 3.
        scheduler.nextChunkIndices(3, 8);
        // Switching radius restarts: the very next batch must begin at index 0.
        int[] batch = scheduler.nextChunkIndices(2, 8);
        assertFalse(batch.length == 0, "a fresh radius yields a batch");
        assertEquals(0, batch[0], "a radius change restarts the cursor at index 0");
    }

    @Test
    void radiusChange_retainsPublishedUntilNewPassCompletes() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int r1 = 1; // 9 chunks, one tick per pass at budget 9
        // Complete a pass at r1 that finds one bell, then promote it.
        scheduler.nextChunkIndices(r1, 9);
        scheduler.recordBell(7L);
        scheduler.nextChunkIndices(r1, 9); // promotes r1's find
        assertEquals(Set.of(7L), scheduler.publishedBells());

        // Switch radius: the previous published set must survive until r2 finishes a pass.
        int r2 = 2; // 25 chunks, pass spans multiple ticks at budget 9
        scheduler.nextChunkIndices(r2, 9); // first tick of r2's first pass — no promote yet
        assertEquals(Set.of(7L), scheduler.publishedBells(),
                "a radius change keeps the last published set until the new pass completes");
        scheduler.nextChunkIndices(r2, 9);
        scheduler.nextChunkIndices(r2, 9); // r2 pass completes here (25 chunks / 9 = 3 ticks)
        scheduler.recordBell(8L);
        scheduler.nextChunkIndices(r2, 9); // first tick of r2's second pass — promotes r2's finds
        assertEquals(Set.of(8L), scheduler.publishedBells(),
                "once the new-radius pass completes, its finds replace the retained set");
    }

    @Test
    void reset_clearsPublishedAndCursor() {
        BellSweepScheduler scheduler = new BellSweepScheduler();
        int radius = 1;
        int budget = 9;
        scheduler.nextChunkIndices(radius, budget);
        scheduler.recordBell(5L);
        scheduler.nextChunkIndices(radius, budget); // promote
        assertFalse(scheduler.publishedBells().isEmpty());

        scheduler.reset();
        assertTrue(scheduler.publishedBells().isEmpty(), "reset drops published bells");
        int[] batch = scheduler.nextChunkIndices(radius, budget);
        assertEquals(0, batch[0], "reset restarts the cursor");
    }
}
