package com.rfizzle.mercantile.memorial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FearMathTest {

    private static final long WINDOW = 10 * FearMath.TICKS_PER_MINUTE;
    private static final long DURATION = 3 * FearMath.TICKS_PER_DAY;

    @Test
    void recordKillAppendsAndKeepsRecentKills() {
        List<Long> kills = FearMath.recordKill(List.of(100L, 200L), 300L, WINDOW, 32);
        assertEquals(List.of(100L, 200L, 300L), kills);
    }

    @Test
    void recordKillPrunesKillsOutsideTheWindow() {
        long now = WINDOW + 500L;
        // 100 is older than the window; 1_000 is inside it.
        List<Long> kills = FearMath.recordKill(List.of(100L, 1_000L), now, WINDOW, 32);
        assertEquals(List.of(1_000L, now), kills);
    }

    @Test
    void recordKillDropsFutureTimestampsAfterClockRewind() {
        // A rewound world clock must not leave immortal "future" kills in the window.
        List<Long> kills = FearMath.recordKill(List.of(50_000L), 1_000L, WINDOW, 32);
        assertEquals(List.of(1_000L), kills);
    }

    @Test
    void recordKillCapsTrackedKills() {
        List<Long> kills = List.of(1L, 2L, 3L);
        List<Long> result = FearMath.recordKill(kills, 4L, WINDOW, 3);
        assertEquals(3, result.size());
        assertEquals(List.of(2L, 3L, 4L), result, "oldest kill is evicted at the cap");
    }

    @Test
    void thresholdReachedAtExactCount() {
        assertFalse(FearMath.thresholdReached(List.of(1L, 2L), 3));
        assertTrue(FearMath.thresholdReached(List.of(1L, 2L, 3L), 3));
    }

    @Test
    void fractionIsZeroWhenNeverActivated() {
        assertEquals(0.0, FearMath.fraction(-1L, 5_000L, DURATION));
    }

    @Test
    void fractionDecaysLinearlyFromOneToZero() {
        long start = 10_000L;
        assertEquals(1.0, FearMath.fraction(start, start, DURATION));
        assertEquals(0.5, FearMath.fraction(start, start + DURATION / 2, DURATION), 1e-9);
        assertEquals(0.0, FearMath.fraction(start, start + DURATION, DURATION));
        assertEquals(0.0, FearMath.fraction(start, start + DURATION + 1, DURATION));
    }

    @Test
    void fractionTreatsRewoundClockAsFresh() {
        assertEquals(1.0, FearMath.fraction(10_000L, 5_000L, DURATION));
    }

    @Test
    void fractionIsZeroForNonPositiveDuration() {
        assertEquals(0.0, FearMath.fraction(10_000L, 10_000L, 0L));
    }

    @Test
    void markupScalesWithBasePriceAndFraction() {
        assertEquals(16, FearMath.markup(64, 25, 1.0));
        assertEquals(8, FearMath.markup(64, 25, 0.5));
    }

    @Test
    void markupIsAtLeastOneWhileFearIsActive() {
        // A decaying markup never rounds away to nothing before expiry.
        assertEquals(1, FearMath.markup(1, 25, 0.01));
    }

    @Test
    void markupIsZeroWhenInactiveOrDisabled() {
        assertEquals(0, FearMath.markup(64, 25, 0.0));
        assertEquals(0, FearMath.markup(64, 0, 1.0));
        assertEquals(0, FearMath.markup(0, 25, 1.0));
    }

    @Test
    void capToHeadroomLeavesSmallMarkupsAlone() {
        // Base 8, no other adjustments: 56 emeralds of headroom under the 64-stack clamp.
        assertEquals(2, FearMath.capToHeadroom(2, 64, 8, 0, 0));
    }

    @Test
    void capToHeadroomClampsAtTheStackLimit() {
        // Base 60: only 4 emeralds of headroom, so a raw markup of 15 charges 4.
        assertEquals(4, FearMath.capToHeadroom(15, 64, 60, 0, 0));
    }

    @Test
    void capToHeadroomIsZeroWithNoHeadroom() {
        assertEquals(0, FearMath.capToHeadroom(16, 64, 64, 0, 0));
        // Demand and other markups already past the clamp leave nothing for fear.
        assertEquals(0, FearMath.capToHeadroom(16, 64, 40, 20, 10));
    }

    @Test
    void capToHeadroomAccountsForDiscountsAndDemand() {
        // A reputation discount of -10 widens the headroom; demand of 6 narrows it.
        assertEquals(8, FearMath.capToHeadroom(8, 64, 60, 6, -10));
        assertEquals(8, FearMath.capToHeadroom(20, 64, 60, 6, -10));
    }

    @Test
    void capToHeadroomIsZeroForNonPositiveMarkup() {
        assertEquals(0, FearMath.capToHeadroom(0, 64, 8, 0, 0));
        assertEquals(0, FearMath.capToHeadroom(-3, 64, 8, 0, 0));
    }
}
