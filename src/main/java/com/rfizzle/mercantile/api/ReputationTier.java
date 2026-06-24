package com.rfizzle.mercantile.api;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The six reputation standings a player can hold with the villager economy,
 * ordered from best to worst. A player's tier is derived from their scalar
 * reputation score via {@link #fromScore(int)}; the score itself is available
 * through {@link MercantileAPI#getReputation}.
 *
 * <p>Part of Mercantile's stable API surface (Concord API Standard v1):
 * existing constants, their relative ordering, and the public methods on this
 * enum are stable across minor and patch releases. Exact score thresholds are
 * gameplay tuning and may shift in minor releases — compare tiers, don't
 * hardcode scores.
 */
@Stable
public enum ReputationTier {
    // Declared in descending minScore order so fromScore() can walk values() once.
    // minScore = lowest score in the tier's range (range extends up to the next tier's minScore - 1).
    HONORED   (1000, "mercantile.tier.honored",   -0.15f),
    TRUSTED   ( 300, "mercantile.tier.trusted",   -0.10f),
    LIKED     (  75, "mercantile.tier.liked",     -0.05f),
    NEUTRAL   (   0, "mercantile.tier.neutral",    0.00f),
    // DISTRUSTED has no flat multiplier — linear markup is computed in priceModifierForScore
    DISTRUSTED(-149, "mercantile.tier.distrusted", 0.00f),
    REVILED   (-200, "mercantile.tier.reviled",    0.00f);

    private final int minScore;
    private final String translationKey;
    private final float priceMultiplier;

    ReputationTier(int minScore, String translationKey, float priceMultiplier) {
        this.minScore = minScore;
        this.translationKey = translationKey;
        this.priceMultiplier = priceMultiplier;
    }

    public int minScore() {
        return minScore;
    }

    public String translationKey() {
        return translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    // Flat modifier only. DISTRUSTED uses linear scaling — call priceModifierForScore() for all tiers.
    int priceModifier(int basePrice) {
        if (priceMultiplier == 0.0f) return 0;
        // Discount tiers have negative multiplier; mirror original: -Math.round(base * |mult|)
        return -Math.round(basePrice * -priceMultiplier);
    }

    public static ReputationTier fromScore(int score) {
        for (ReputationTier tier : values()) {
            if (score >= tier.minScore) return tier;
        }
        return REVILED;
    }

    // Unknown names default to TRUSTED (minScore 50). Prior behavior defaulted to NEUTRAL (0) —
    // datapacks with a typo in min_tier / min_tier_override will gate trades more strictly than before.
    public static ReputationTier fromName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (ReputationTier tier : values()) {
            if (tier.name().toLowerCase(Locale.ROOT).equals(lower)) return tier;
        }
        Mercantile.LOGGER.warn("Unknown reputation tier '{}', defaulting to TRUSTED", name);
        return TRUSTED;
    }

    public static int priceModifierForScore(int score, int basePrice) {
        ReputationTier tier = fromScore(score);
        if (tier == DISTRUSTED) {
            // DISTRUSTED span -1..-149 (148 steps): ramps from ~10% markup at -1 to ~25% at -149.
            float markupPercent = (10f + 15f * (-score - 1) / 148f) / 100f;
            return Math.round(basePrice * markupPercent);
        }
        return tier.priceModifier(basePrice);
    }
}
