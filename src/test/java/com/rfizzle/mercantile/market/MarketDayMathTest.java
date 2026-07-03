package com.rfizzle.mercantile.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketDayMathTest {

    @Test
    void dawnOfAScheduledDayIsMarketDay() {
        assertTrue(MarketDayMath.isMarketDay(7 * 24_000L, 7));
        assertTrue(MarketDayMath.isMarketDay(14 * 24_000L + 11_999L, 7));
    }

    @Test
    void worldStartDayIsNotAMarketDay() {
        assertFalse(MarketDayMath.isMarketDay(0L, 7));
        assertFalse(MarketDayMath.isMarketDay(1_000L, 1));
    }

    @Test
    void endsAtDusk() {
        assertTrue(MarketDayMath.isMarketDay(7 * 24_000L + 11_999L, 7));
        assertFalse(MarketDayMath.isMarketDay(7 * 24_000L + 12_000L, 7));
        assertFalse(MarketDayMath.isMarketDay(7 * 24_000L + 23_999L, 7));
    }

    @Test
    void offScheduleDaysAreNotMarketDays() {
        for (int day = 1; day < 7; day++) {
            assertFalse(MarketDayMath.isMarketDay(day * 24_000L + 6_000L, 7),
                    "day " + day + " should not be a market day on a 7-day interval");
        }
    }

    @Test
    void intervalOfOneMakesEveryMorningMarketDay() {
        assertTrue(MarketDayMath.isMarketDay(3 * 24_000L + 100L, 1));
        assertFalse(MarketDayMath.isMarketDay(3 * 24_000L + 13_000L, 1));
    }

    @Test
    void nonPositiveIntervalDisablesSchedule() {
        assertFalse(MarketDayMath.isMarketDay(7 * 24_000L, 0));
        assertFalse(MarketDayMath.isMarketDay(7 * 24_000L, -5));
    }

    @Test
    void dayOfFloorsToCalendarDay() {
        assertEquals(0L, MarketDayMath.dayOf(23_999L));
        assertEquals(1L, MarketDayMath.dayOf(24_000L));
        assertEquals(7L, MarketDayMath.dayOf(7 * 24_000L + 12_345L));
    }

    @Test
    void daysUntilNextIsZeroWhileMarketDayIsActive() {
        assertEquals(0L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L, 7));
        assertEquals(0L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L + 11_999L, 7));
    }

    @Test
    void daysUntilNextCountsDownAcrossTheInterval() {
        assertEquals(7L, MarketDayMath.daysUntilNextMarketDay(0L, 7));
        assertEquals(6L, MarketDayMath.daysUntilNextMarketDay(1 * 24_000L + 500L, 7));
        assertEquals(1L, MarketDayMath.daysUntilNextMarketDay(6 * 24_000L + 500L, 7));
    }

    @Test
    void marketDayPastDuskIsAFullIntervalFromTheNext() {
        assertEquals(7L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L + 12_000L, 7));
        assertEquals(7L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L + 23_999L, 7));
    }

    @Test
    void daysUntilNextWithIntervalOfOne() {
        // Morning of any day (past day 0) is active; evening looks to tomorrow.
        assertEquals(0L, MarketDayMath.daysUntilNextMarketDay(3 * 24_000L + 100L, 1));
        assertEquals(1L, MarketDayMath.daysUntilNextMarketDay(3 * 24_000L + 13_000L, 1));
        assertEquals(1L, MarketDayMath.daysUntilNextMarketDay(500L, 1));
    }

    @Test
    void daysUntilNextWithNonPositiveIntervalIsDisabled() {
        assertEquals(-1L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L, 0));
        assertEquals(-1L, MarketDayMath.daysUntilNextMarketDay(7 * 24_000L, -3));
    }

    @Test
    void discountIsFlooredPercentOfBaseAndNeverAMarkup() {
        assertEquals(-3, MarketDayMath.discount(64, 5));
        assertEquals(-1, MarketDayMath.discount(20, 5));
        // Cheap trades floor to no discount rather than rounding up.
        assertEquals(0, MarketDayMath.discount(1, 5));
        assertEquals(0, MarketDayMath.discount(10, 0));
        assertEquals(-10, MarketDayMath.discount(10, 100));
    }
}
