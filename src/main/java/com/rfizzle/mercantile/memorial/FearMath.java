package com.rfizzle.mercantile.memorial;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure math for the fear markup: kill-window bookkeeping and the linear decay of an
 * active markup. No Minecraft imports so the whole surface is plain-JUnit testable.
 */
public final class FearMath {

    /** Real-time ticks per minute (20 t/s × 60 s). */
    public static final long TICKS_PER_MINUTE = 1_200L;
    /** Ticks per in-game day. */
    public static final long TICKS_PER_DAY = 24_000L;

    private FearMath() {
    }

    /**
     * Appends a kill at {@code now} to the window, dropping kills that fell out of it.
     * Kill times in the future (a rewound world clock) are dropped rather than kept alive
     * forever. Returns a new list, capped at {@code maxTracked} newest entries.
     */
    public static List<Long> recordKill(List<Long> killTimes, long now, long windowTicks, int maxTracked) {
        List<Long> pruned = new ArrayList<>();
        for (long time : killTimes) {
            if (time <= now && now - time < windowTicks) {
                pruned.add(time);
            }
        }
        pruned.add(now);
        while (pruned.size() > maxTracked) {
            pruned.removeFirst();
        }
        return pruned;
    }

    /** Whether the kills currently inside the window meet the activation threshold. */
    public static boolean thresholdReached(List<Long> killTimes, int threshold) {
        return killTimes.size() >= threshold;
    }

    /**
     * Remaining fear strength in {@code [0, 1]}: 1 at activation, decaying linearly to 0
     * over {@code durationTicks}. A start of {@code -1} means fear was never activated.
     * A clock that moved backwards past the start reads as freshly activated.
     */
    public static double fraction(long fearStartGameTime, long now, long durationTicks) {
        if (fearStartGameTime < 0 || durationTicks <= 0) return 0.0;
        long elapsed = now - fearStartGameTime;
        if (elapsed < 0) return 1.0;
        if (elapsed >= durationTicks) return 0.0;
        return 1.0 - (double) elapsed / durationTicks;
    }

    /**
     * Emerald markup for one offer at the given fear strength. At least 1 emerald whenever
     * fear is active at all, so a decaying markup never silently vanishes before expiry.
     */
    public static int markup(int basePrice, int markupPercent, double fraction) {
        if (fraction <= 0.0 || markupPercent <= 0 || basePrice <= 0) return 0;
        return Math.max(1, (int) Math.round(basePrice * (markupPercent / 100.0) * fraction));
    }

    /**
     * Caps a raw fear markup at the headroom vanilla's max-stack clamp leaves for it.
     * {@code MerchantOffer.getCostA} clamps the charged count to the cost item's max stack
     * size, so any markup past that headroom would be reported in the breakdown but never
     * actually charged — and would leak into the "Other" residual line.
     */
    public static int capToHeadroom(int rawMarkup, int maxStackSize, int basePrice,
                                    int demandAdjust, int otherModifiers) {
        if (rawMarkup <= 0) return 0;
        int headroom = maxStackSize - basePrice - demandAdjust - otherModifiers;
        if (headroom <= 0) return 0;
        return Math.min(rawMarkup, headroom);
    }
}
