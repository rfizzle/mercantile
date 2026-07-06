package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure, viewer-agnostic filter predicates over the synced trade index. Lives in the
 * shared client layer so the EMI / REI / JEI adapters stay thin translations of these
 * predicates into their own native mechanisms (per issue #118's shared-data-layer rule).
 *
 * <p>Nothing here reads client state directly — callers pass the player's current
 * reputation score so the logic is deterministic and unit-testable.
 */
public final class TradeIndexFilter {

    private TradeIndexFilter() {
    }

    /**
     * A trade is unlocked when it carries no reputation gate (all vanilla trades and
     * ungated exclusives) or the player's score meets the gate.
     */
    public static boolean isUnlocked(TradeIndexEntry entry, int playerScore) {
        OptionalInt min = entry.minScore();
        return min.isEmpty() || playerScore >= min.getAsInt();
    }

    /**
     * The reputation tier that gates this trade, or empty for ungated trades. Derived
     * from the requirement score via {@link ReputationTier#fromScore(int)} — the same
     * mapping that powers the "Requires Trusted" badges.
     */
    public static Optional<ReputationTier> gatingTier(TradeIndexEntry entry) {
        OptionalInt min = entry.minScore();
        return min.isEmpty() ? Optional.empty() : Optional.of(ReputationTier.fromScore(min.getAsInt()));
    }

    /**
     * The distinct tiers that gate at least one trade in the snapshot, ordered from the
     * lowest requirement upward (Liked → Trusted → Honored) so the per-tier viewer
     * categories read as a progression.
     *
     * <p>Only tiers that represent a positive requirement are returned: a trade whose gate
     * falls in the Neutral band or below is unlocked for essentially everyone, so surfacing
     * a "Neutral/Distrusted/Reviled Trades" tab would advertise nothing. Such trades still
     * appear in the comprehensive and available views.
     */
    public static List<ReputationTier> gatingTiersPresent(List<TradeIndexEntry> entries) {
        EnumSet<ReputationTier> present = EnumSet.noneOf(ReputationTier.class);
        for (TradeIndexEntry entry : entries) {
            gatingTier(entry)
                    .filter(tier -> tier.minScore() > 0)
                    .ifPresent(present::add);
        }
        List<ReputationTier> ordered = new ArrayList<>(present);
        ordered.sort(Comparator.comparingInt(ReputationTier::minScore));
        return ordered;
    }
}
