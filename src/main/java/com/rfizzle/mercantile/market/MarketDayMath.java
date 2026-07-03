package com.rfizzle.mercantile.market;

/** Pure market-day schedule and discount math, unit-testable without a server. */
public final class MarketDayMath {

    public static final long TICKS_PER_DAY = 24_000L;
    /** Market day runs from dawn (day time 0) until dusk. */
    public static final long DUSK_DAY_TIME = 12_000L;
    /** Vanilla allows two restocks per day; market day grants one more. */
    public static final int VANILLA_MAX_RESTOCKS_PER_DAY = 2;
    public static final int MARKET_DAY_MAX_RESTOCKS_PER_DAY = 3;

    private MarketDayMath() {
    }

    /** The calendar day a level day-time value falls on. */
    public static long dayOf(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /**
     * Whether market day is in effect at the given level day time: the calendar day lands on
     * the configured interval and the time of day is between dawn and dusk. Day 0 (world
     * start) is excluded — the first market day falls on day {@code intervalDays}.
     */
    public static boolean isMarketDay(long dayTime, int intervalDays) {
        if (intervalDays <= 0) return false;
        long day = dayOf(dayTime);
        long timeOfDay = Math.floorMod(dayTime, TICKS_PER_DAY);
        return day > 0 && day % intervalDays == 0 && timeOfDay < DUSK_DAY_TIME;
    }

    /**
     * Market-day price adjustment for one offer: a flat discount of {@code discountPercent}
     * of the base price, floored. Zero or negative — never a markup.
     */
    public static int discount(int basePrice, int discountPercent) {
        return -Math.max(0, basePrice * discountPercent / 100);
    }
}
