package com.rfizzle.mercantile.trade.index;

import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TradeIndexDataSourceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetExclusiveState() {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
    }

    @AfterEach
    void teardown() {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
    }

    @Test
    void vanillaRebuildProducesEntriesForKnownProfessions() {
        TradeIndexDataSource.rebuild();
        List<TradeIndexEntry> snapshot = TradeIndexDataSource.snapshot();
        assertFalse(snapshot.isEmpty(), "snapshot should not be empty");

        Set<ResourceLocation> professions = snapshot.stream()
                .filter(e -> e.source() == TradeIndexEntry.Source.VANILLA)
                .map(TradeIndexEntry::profession)
                .collect(Collectors.toSet());

        assertTrue(professions.contains(ResourceLocation.parse("minecraft:farmer")),
                "expected farmer entries, got: " + professions);
        assertTrue(professions.contains(ResourceLocation.parse("minecraft:librarian")),
                "expected librarian entries, got: " + professions);
        assertTrue(professions.contains(ResourceLocation.parse("minecraft:butcher")),
                "expected butcher entries, got: " + professions);
    }

    @Test
    void entriesExposeInputAndOutputItems() {
        TradeIndexDataSource.rebuild();
        for (TradeIndexEntry e : TradeIndexDataSource.snapshot()) {
            assertFalse(e.inputA().isEmpty(), "inputA must not be empty for " + e);
            assertFalse(e.output().isEmpty(), "output must not be empty for " + e);
        }
    }

    @Test
    void vanillaEntriesHaveAbsentMinScore() {
        TradeIndexDataSource.rebuild();
        for (TradeIndexEntry e : TradeIndexDataSource.snapshot()) {
            if (e.source() == TradeIndexEntry.Source.VANILLA) {
                assertTrue(e.minScore().isEmpty(),
                        "vanilla entry should have absent minScore: " + e);
            }
        }
    }

    @Test
    void exclusiveEntriesHaveMinScorePresent() {
        ExclusiveTradesManager.ExclusiveTrade profTrade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 10), null,
                new ItemStack(Items.DIAMOND, 1), 4, 5, 0.0f, 100);
        ExclusiveTradesManager.ExclusiveTrade crossTrade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 50), null,
                new ItemStack(Items.NETHERITE_INGOT, 1), 2, 10, 0.0f, 500);
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("farmer", List.of(profTrade)),
                List.of(crossTrade)
        );

        TradeIndexDataSource.rebuild();

        for (TradeIndexEntry e : TradeIndexDataSource.snapshot()) {
            if (e.source() != TradeIndexEntry.Source.VANILLA) {
                assertTrue(e.minScore().isPresent(),
                        "exclusive entry must carry minScore: " + e);
            }
        }
    }

    @Test
    void includesExclusiveProfessionTrades() {
        ExclusiveTradesManager.ExclusiveTrade trade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 10), null,
                new ItemStack(Items.DIAMOND, 1), 4, 5, 0.0f, 100);
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("farmer", List.of(trade)),
                List.of()
        );

        TradeIndexDataSource.rebuild();

        ResourceLocation farmerId = ResourceLocation.parse("minecraft:farmer");
        boolean found = TradeIndexDataSource.snapshot().stream().anyMatch(e ->
                e.source() == TradeIndexEntry.Source.EXCLUSIVE_PROFESSION
                        && e.profession().equals(farmerId)
                        && e.minScore().isPresent()
                        && e.minScore().getAsInt() == 100
                        && e.output().getItem() == Items.DIAMOND);
        assertTrue(found, "expected an exclusive farmer trade entry");
    }

    @Test
    void includesCrossProfessionTrades() {
        ExclusiveTradesManager.ExclusiveTrade trade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 50), null,
                new ItemStack(Items.NETHERITE_INGOT, 1), 2, 10, 0.0f, 500);
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of(),
                List.of(trade)
        );

        TradeIndexDataSource.rebuild();

        boolean found = TradeIndexDataSource.snapshot().stream().anyMatch(e ->
                e.source() == TradeIndexEntry.Source.EXCLUSIVE_CROSS_PROFESSION
                        && e.minScore().isPresent()
                        && e.minScore().getAsInt() == 500
                        && e.output().getItem() == Items.NETHERITE_INGOT);
        assertTrue(found, "expected a cross-profession exclusive trade entry");
    }

    @Test
    void rebuildIsIdempotent() {
        ExclusiveTradesManager.ExclusiveTrade trade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 10), null,
                new ItemStack(Items.DIAMOND, 1), 4, 5, 0.0f, 100);
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("farmer", List.of(trade)),
                List.of()
        );

        TradeIndexDataSource.rebuild();
        List<String> sigs1 = signatures(TradeIndexDataSource.snapshot());
        TradeIndexDataSource.rebuild();
        List<String> sigs2 = signatures(TradeIndexDataSource.snapshot());

        assertEquals(sigs1, sigs2, "two rebuilds with identical inputs should yield identical snapshots");
    }

    @Test
    void rebuildReplacesPriorSnapshot() {
        ExclusiveTradesManager.ExclusiveTrade trade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 10), null,
                new ItemStack(Items.DIAMOND, 1), 4, 5, 0.0f, 100);
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("farmer", List.of(trade)),
                List.of()
        );

        TradeIndexDataSource.rebuild();
        long exclusiveCount = TradeIndexDataSource.snapshot().stream()
                .filter(e -> e.source() != TradeIndexEntry.Source.VANILLA)
                .count();
        assertTrue(exclusiveCount > 0, "expected exclusive entries after seeding");

        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        TradeIndexDataSource.rebuild();
        long exclusiveAfter = TradeIndexDataSource.snapshot().stream()
                .filter(e -> e.source() != TradeIndexEntry.Source.VANILLA)
                .count();
        assertEquals(0, exclusiveAfter, "rebuild after clearing exclusives should drop those rows");
    }

    @Test
    void snapshotIsImmutable() {
        TradeIndexDataSource.rebuild();
        List<TradeIndexEntry> snap = TradeIndexDataSource.snapshot();
        assertThrows(UnsupportedOperationException.class,
                () -> snap.add(snap.get(0)),
                "snapshot must be immutable");
    }

    private static List<String> signatures(List<TradeIndexEntry> entries) {
        return entries.stream()
                .map(e -> e.profession() + "|" + e.level() + "|" + e.source() + "|"
                        + stackSig(e.inputA()) + "|"
                        + stackSig(e.inputB()) + "|"
                        + stackSig(e.output()) + "|"
                        + e.maxUses() + "|" + e.xpGain() + "|" + e.priceMultiplier() + "|"
                        + (e.minScore().isPresent() ? e.minScore().getAsInt() : "none"))
                .toList();
    }

    private static String stackSig(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }
}
