package com.rfizzle.mercantile.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests the pure weighted-pick math in {@link GratitudeGiftTables}. Table loading and stack
 * rolling touch the item registry and are covered by the gametests.
 */
class GratitudeGiftTablesTest {

    @Test
    void totalWeightSums() {
        assertEquals(9, GratitudeGiftTables.totalWeight(new int[]{4, 3, 2}));
        assertEquals(1, GratitudeGiftTables.totalWeight(new int[]{1}));
    }

    @Test
    void pickIndexMapsRollToCumulativeBracket() {
        int[] weights = {4, 3, 2}; // brackets: [0,3] -> 0, [4,6] -> 1, [7,8] -> 2
        assertEquals(0, GratitudeGiftTables.pickIndex(weights, 0));
        assertEquals(0, GratitudeGiftTables.pickIndex(weights, 3));
        assertEquals(1, GratitudeGiftTables.pickIndex(weights, 4));
        assertEquals(1, GratitudeGiftTables.pickIndex(weights, 6));
        assertEquals(2, GratitudeGiftTables.pickIndex(weights, 7));
        assertEquals(2, GratitudeGiftTables.pickIndex(weights, 8));
    }

    @Test
    void pickIndexCoversEveryRollExactlyOnce() {
        int[] weights = {2, 5, 1, 3};
        int[] hits = new int[weights.length];
        for (int roll = 0; roll < GratitudeGiftTables.totalWeight(weights); roll++) {
            hits[GratitudeGiftTables.pickIndex(weights, roll)]++;
        }
        assertArrayEquals(weights, hits, "each entry must win exactly its weight in rolls");
    }

    @Test
    void singleEntryAlwaysWins() {
        assertEquals(0, GratitudeGiftTables.pickIndex(new int[]{7}, 0));
        assertEquals(0, GratitudeGiftTables.pickIndex(new int[]{7}, 6));
    }
}
