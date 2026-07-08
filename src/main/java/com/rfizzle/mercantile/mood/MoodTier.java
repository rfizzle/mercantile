package com.rfizzle.mercantile.mood;

/**
 * Mood buckets for a villager's 0–100 mood score. Only the outer tiers carry
 * mechanical effects (price nudge, restock speed); the middle tiers are neutral.
 */
public enum MoodTier {
    MISERABLE("tooltip.mercantile.mood.miserable"),
    UNHAPPY("tooltip.mercantile.mood.unhappy"),
    CONTENT("tooltip.mercantile.mood.content"),
    HAPPY("tooltip.mercantile.mood.happy");

    private final String translationKey;

    MoodTier(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static MoodTier fromMood(int mood) {
        if (mood < 25) return MISERABLE;
        if (mood < 50) return UNHAPPY;
        if (mood < 80) return CONTENT;
        return HAPPY;
    }
}
