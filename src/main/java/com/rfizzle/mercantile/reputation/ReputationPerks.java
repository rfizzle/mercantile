package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.api.ReputationTier;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The reputation perks a player's current standing grants, expressed as
 * display lines. This is the single source of truth the reputation detail
 * panel reads, so what the panel claims can never drift from what the economy
 * actually does: the price figure is computed from the same {@link
 * ReputationTier#priceModifierForScore} the server applies at trade time, and
 * the exclusive-trade thresholds are read from {@link ReputationTier} rather
 * than hardcoded.
 *
 * <p>Pure logic with no client or Fabric dependency, so it lives in {@code
 * main} and is unit-tested directly.
 */
public final class ReputationPerks {

    /** Base price the discount/markup percentage is derived against (a modifier on 100 reads as a percent). */
    private static final int PERCENT_BASE = 100;

    private ReputationPerks() {
    }

    /**
     * The active perk lines for {@code score}, top to bottom: the price effect
     * (discount, markup, refusal, or none) followed by exclusive-trade access
     * (unlocked tiers, or the standing needed to unlock the first).
     */
    public static List<Component> activePerks(int score) {
        List<Component> perks = new ArrayList<>();
        ReputationTier tier = ReputationTier.fromScore(score);

        if (tier == ReputationTier.REVILED) {
            perks.add(Component.translatable("hud.mercantile.rep_detail.perk.refused"));
        } else {
            int pct = priceModifierPercent(score);
            if (pct < 0) {
                perks.add(Component.translatable("hud.mercantile.rep_detail.perk.discount", -pct));
            } else if (pct > 0) {
                perks.add(Component.translatable("hud.mercantile.rep_detail.perk.markup", pct));
            } else {
                perks.add(Component.translatable("hud.mercantile.rep_detail.perk.no_modifier"));
            }
        }

        if (score >= ReputationTier.TRUSTED.minScore()) {
            perks.add(Component.translatable("hud.mercantile.rep_detail.perk.exclusive_profession"));
        }
        if (score >= ReputationTier.HONORED.minScore()) {
            perks.add(Component.translatable("hud.mercantile.rep_detail.perk.exclusive_cross"));
        }
        if (tier != ReputationTier.REVILED && score < ReputationTier.TRUSTED.minScore()) {
            perks.add(Component.translatable("hud.mercantile.rep_detail.perk.exclusive_locked",
                    ReputationTier.TRUSTED.minScore()));
        }
        return perks;
    }

    /**
     * The price modifier at {@code score} as a signed percentage: negative is a
     * discount, positive a markup, zero no change. Derived from the live pricing
     * function against a base of 100, so it tracks tuning changes automatically.
     */
    public static int priceModifierPercent(int score) {
        return ReputationTier.priceModifierForScore(score, PERCENT_BASE);
    }

    /** Whether the standing at {@code score} has reached the floor where exclusive trades begin. */
    public static boolean exclusiveTradesUnlocked(int score) {
        return score >= ReputationTier.TRUSTED.minScore();
    }

    /** The standing score at which exclusive trades first unlock. */
    public static int exclusiveUnlockScore() {
        return ReputationTier.TRUSTED.minScore();
    }
}
