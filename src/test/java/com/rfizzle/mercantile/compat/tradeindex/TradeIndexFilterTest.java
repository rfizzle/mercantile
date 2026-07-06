package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TradeIndexFilterTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TradeIndexEntry gated(int minScore) {
        return entry(OptionalInt.of(minScore), TradeIndexEntry.Source.EXCLUSIVE_PROFESSION);
    }

    private static TradeIndexEntry ungated() {
        return entry(OptionalInt.empty(), TradeIndexEntry.Source.VANILLA);
    }

    private static TradeIndexEntry entry(OptionalInt minScore, TradeIndexEntry.Source source) {
        return new TradeIndexEntry(
                ResourceLocation.withDefaultNamespace("farmer"),
                1,
                source,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                12,
                2,
                0.05f,
                minScore);
    }

    // --- isUnlocked ---

    @Test
    void ungatedTradeIsAlwaysUnlocked() {
        assertTrue(TradeIndexFilter.isUnlocked(ungated(), Integer.MIN_VALUE));
    }

    @Test
    void gatedTradeUnlocksAtAndAboveThreshold() {
        TradeIndexEntry trade = gated(300);
        assertFalse(TradeIndexFilter.isUnlocked(trade, 299), "just below the gate stays locked");
        assertTrue(TradeIndexFilter.isUnlocked(trade, 300), "exactly at the gate unlocks");
        assertTrue(TradeIndexFilter.isUnlocked(trade, 1000), "above the gate is unlocked");
    }

    // --- gatingTier ---

    @Test
    void ungatedTradeHasNoGatingTier() {
        assertTrue(TradeIndexFilter.gatingTier(ungated()).isEmpty());
    }

    @Test
    void gatingTierMapsScoreToTierBand() {
        assertEquals(ReputationTier.LIKED, TradeIndexFilter.gatingTier(gated(75)).orElseThrow());
        assertEquals(ReputationTier.TRUSTED, TradeIndexFilter.gatingTier(gated(300)).orElseThrow());
        assertEquals(ReputationTier.HONORED, TradeIndexFilter.gatingTier(gated(1000)).orElseThrow());
    }

    // --- gatingTiersPresent ---

    @Test
    void gatingTiersPresentDedupesAndSortsAscending() {
        List<ReputationTier> tiers = TradeIndexFilter.gatingTiersPresent(List.of(
                gated(1000), gated(300), gated(75), gated(320), ungated()));
        assertEquals(List.of(ReputationTier.LIKED, ReputationTier.TRUSTED, ReputationTier.HONORED), tiers);
    }

    @Test
    void gatingTiersPresentIsEmptyWithoutExclusives() {
        assertTrue(TradeIndexFilter.gatingTiersPresent(List.of(ungated(), ungated())).isEmpty());
    }

    @Test
    void gatingTiersPresentSkipsNeutralAndBelow() {
        // A gate that lands in Neutral (0) or a sub-threshold score is effectively ungated;
        // it must not spawn a "Neutral/Distrusted Trades" tab, only a real tier does.
        List<ReputationTier> tiers = TradeIndexFilter.gatingTiersPresent(List.of(
                gated(0), gated(50), gated(-40), gated(75)));
        assertEquals(List.of(ReputationTier.LIKED), tiers);
    }

    @Test
    void forSnapshotOmitsTabsForNonPositiveGates() {
        List<TradeIndexCategoryKey> keys =
                TradeIndexCategoryKey.forSnapshot(List.of(gated(0), gated(50)));
        assertEquals(List.of(TradeIndexCategoryKey.all(), TradeIndexCategoryKey.available()), keys);
    }

    // --- TradeIndexCategoryKey.accepts ---

    @Test
    void allCategoryAcceptsEverything() {
        TradeIndexCategoryKey all = TradeIndexCategoryKey.all();
        assertTrue(all.accepts(ungated(), 0));
        assertTrue(all.accepts(gated(1000), 0), "the comprehensive view keeps locked trades too");
    }

    @Test
    void availableCategoryTracksReputation() {
        TradeIndexCategoryKey available = TradeIndexCategoryKey.available();
        TradeIndexEntry trade = gated(300);
        assertFalse(available.accepts(trade, 100));
        assertTrue(available.accepts(trade, 300));
    }

    @Test
    void tierCategoryAcceptsOnlyItsOwnBand() {
        TradeIndexCategoryKey trusted = TradeIndexCategoryKey.tier(ReputationTier.TRUSTED);
        assertTrue(trusted.accepts(gated(300), 0), "gated at Trusted belongs in the Trusted tab");
        assertFalse(trusted.accepts(gated(1000), 0), "gated at Honored does not");
        assertFalse(trusted.accepts(ungated(), 0), "ungated trades never belong to a tier tab");
    }

    // --- forSnapshot ---

    @Test
    void forSnapshotAlwaysIncludesAllAndAvailable() {
        List<TradeIndexCategoryKey> keys = TradeIndexCategoryKey.forSnapshot(List.of(ungated()));
        assertEquals(List.of(TradeIndexCategoryKey.all(), TradeIndexCategoryKey.available()), keys);
    }

    @Test
    void forSnapshotAppendsPresentTiersInOrder() {
        List<TradeIndexCategoryKey> keys = TradeIndexCategoryKey.forSnapshot(List.of(
                gated(1000), gated(75), ungated()));
        assertEquals(List.of(
                TradeIndexCategoryKey.all(),
                TradeIndexCategoryKey.available(),
                TradeIndexCategoryKey.tier(ReputationTier.LIKED),
                TradeIndexCategoryKey.tier(ReputationTier.HONORED)), keys);
    }

    // --- category key invariants ---

    @Test
    void tierKeyRequiresTierAndOthersForbidIt() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeIndexCategoryKey(TradeIndexCategoryKey.Type.TIER, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TradeIndexCategoryKey(TradeIndexCategoryKey.Type.ALL, ReputationTier.TRUSTED));
    }

    @Test
    void categoryKeyPathsAreStableAndDistinct() {
        assertEquals("villager_trades", TradeIndexCategoryKey.all().path());
        assertEquals("villager_trades_available", TradeIndexCategoryKey.available().path());
        assertEquals("villager_trades_tier_trusted",
                TradeIndexCategoryKey.tier(ReputationTier.TRUSTED).path());
    }
}
