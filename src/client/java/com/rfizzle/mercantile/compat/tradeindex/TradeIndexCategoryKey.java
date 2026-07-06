package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A viewer-agnostic descriptor of one trade-index listing category. The three recipe
 * viewers each render the identical trade layout; they differ only in <em>which</em>
 * trades a category holds. This record is the single shared definition of that split —
 * each adapter turns a key into its native category type (EMI {@code EmiRecipeCategory},
 * REI {@code CategoryIdentifier}, JEI {@code RecipeType}) and asks {@link #accepts} which
 * synced entries belong in it.
 *
 * <ul>
 *   <li>{@link Type#ALL} — the comprehensive listing (unchanged existing behaviour).</li>
 *   <li>{@link Type#AVAILABLE} — the "unlocked-only" view: trades the player can use now.</li>
 *   <li>{@link Type#TIER} — everything a single reputation tier unlocks.</li>
 * </ul>
 */
public record TradeIndexCategoryKey(Type type, @Nullable ReputationTier tier) {

    public enum Type {
        ALL,
        AVAILABLE,
        TIER
    }

    public TradeIndexCategoryKey {
        if ((type == Type.TIER) != (tier != null)) {
            throw new IllegalArgumentException("tier must be set iff type == TIER");
        }
    }

    public static TradeIndexCategoryKey all() {
        return new TradeIndexCategoryKey(Type.ALL, null);
    }

    public static TradeIndexCategoryKey available() {
        return new TradeIndexCategoryKey(Type.AVAILABLE, null);
    }

    public static TradeIndexCategoryKey tier(ReputationTier tier) {
        return new TradeIndexCategoryKey(Type.TIER, Objects.requireNonNull(tier, "tier"));
    }

    /** The resource-path suffix that keeps each category's id and translation key stable. */
    public String path() {
        return switch (type) {
            case ALL -> "villager_trades";
            case AVAILABLE -> "villager_trades_available";
            case TIER -> "villager_trades_tier_" + tier.name().toLowerCase(Locale.ROOT);
        };
    }

    public ResourceLocation id() {
        return Mercantile.id(path());
    }

    public Component title() {
        return switch (type) {
            case ALL -> Component.translatable("category.mercantile.villager_trades");
            case AVAILABLE -> Component.translatable("category.mercantile.villager_trades.available");
            case TIER -> Component.translatable("category.mercantile.villager_trades.tier", tier.displayName());
        };
    }

    /**
     * Whether {@code entry} belongs in this category given the player's current reputation.
     * {@link Type#TIER} membership is reputation-independent; {@link Type#AVAILABLE} tracks
     * the live score so the answer changes as the player earns standing.
     */
    public boolean accepts(TradeIndexEntry entry, int playerScore) {
        return switch (type) {
            case ALL -> true;
            case AVAILABLE -> TradeIndexFilter.isUnlocked(entry, playerScore);
            case TIER -> TradeIndexFilter.gatingTier(entry).map(t -> t == tier).orElse(false);
        };
    }

    /**
     * The categories to register for a given snapshot: always the comprehensive and
     * available views, plus one per tier that actually gates a trade. Tier categories are
     * omitted when no exclusive trades exist so empty tabs never appear.
     */
    public static List<TradeIndexCategoryKey> forSnapshot(List<TradeIndexEntry> entries) {
        List<TradeIndexCategoryKey> keys = new ArrayList<>();
        keys.add(all());
        keys.add(available());
        for (ReputationTier tier : TradeIndexFilter.gatingTiersPresent(entries)) {
            keys.add(tier(tier));
        }
        return keys;
    }
}
