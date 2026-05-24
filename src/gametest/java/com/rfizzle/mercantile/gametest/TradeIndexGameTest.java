package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

public class TradeIndexGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void snapshotPopulatedAfterRebuild(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            TradeIndexDataSource.rebuild();
            List<TradeIndexEntry> snap = TradeIndexDataSource.snapshot();
            helper.assertTrue(!snap.isEmpty(),
                    "Trade index snapshot should be non-empty after rebuild, got size=" + snap.size());
            helper.assertTrue(TradeIndexDataSource.size() == snap.size(),
                    "size() and snapshot().size() must agree: size()=" + TradeIndexDataSource.size()
                            + " snapshot=" + snap.size());
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void snapshotContainsVanillaProfessionEntries(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            TradeIndexDataSource.rebuild();
            List<TradeIndexEntry> snap = TradeIndexDataSource.snapshot();

            helper.assertTrue(hasProfession(snap, "minecraft:farmer"),
                    "expected farmer entries in snapshot");
            helper.assertTrue(hasProfession(snap, "minecraft:librarian"),
                    "expected librarian entries in snapshot");
            helper.assertTrue(hasProfession(snap, "minecraft:armorer"),
                    "expected armorer entries in snapshot");
            helper.assertTrue(hasProfession(snap, "minecraft:cleric"),
                    "expected cleric entries in snapshot");
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void vanillaEntriesCarryWorkstationStacks(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            TradeIndexDataSource.rebuild();
            List<TradeIndexEntry> snap = TradeIndexDataSource.snapshot();

            assertWorkstationItem(helper, snap, "minecraft:farmer", Items.COMPOSTER);
            assertWorkstationItem(helper, snap, "minecraft:librarian", Items.LECTERN);
            assertWorkstationItem(helper, snap, "minecraft:armorer", Items.BLAST_FURNACE);
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void professionWorkstationsResolvesAllThirteenInServerContext(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            Map<ResourceLocation, Block> mappings = ProfessionWorkstations.snapshot();
            helper.assertTrue(mappings.size() >= 13,
                    "expected at least 13 vanilla profession→workstation mappings in server context, got: "
                            + mappings.size() + " keys=" + mappings.keySet());

            String[] required = {
                    "armorer", "butcher", "cartographer", "cleric", "farmer",
                    "fisherman", "fletcher", "leatherworker", "librarian",
                    "mason", "shepherd", "toolsmith", "weaponsmith"
            };
            for (String name : required) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", name);
                helper.assertTrue(mappings.containsKey(id),
                        "expected mapping for minecraft:" + name + " but missing; have: " + mappings.keySet());
            }
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void leatherworkerWorkstationHandlesCauldronVariants(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            TradeIndexDataSource.rebuild();
            ResourceLocation leatherworkerId = ResourceLocation.fromNamespaceAndPath("minecraft", "leatherworker");
            boolean sawAny = false;
            for (TradeIndexEntry e : TradeIndexDataSource.snapshot()) {
                if (e.source() != TradeIndexEntry.Source.VANILLA) continue;
                if (!e.profession().equals(leatherworkerId)) continue;
                sawAny = true;
                ItemStack ws = e.workstation();
                helper.assertTrue(ws.isEmpty() || ws.getItem() == Items.CAULDRON,
                        "leatherworker workstation must be empty or CAULDRON (filled-cauldron"
                                + " variants have no BlockItem), got: " + ws);
            }
            helper.assertTrue(sawAny, "expected at least one vanilla leatherworker entry in snapshot");
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rebuildIsStableAcrossInvocations(GameTestHelper helper) {
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        ProfessionWorkstations.invalidateForTesting();
        try {
            TradeIndexDataSource.rebuild();
            int firstCount = TradeIndexDataSource.snapshot().size();
            TradeIndexDataSource.rebuild();
            int secondCount = TradeIndexDataSource.snapshot().size();
            helper.assertTrue(firstCount == secondCount,
                    "two rebuilds with identical state should produce identical sizes (deterministic seed): "
                            + "first=" + firstCount + " second=" + secondCount);
            helper.assertTrue(firstCount > 0,
                    "rebuild should produce a non-empty snapshot in server context, got " + firstCount);
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            ProfessionWorkstations.invalidateForTesting();
        }
    }

    private static boolean hasProfession(List<TradeIndexEntry> snap, String id) {
        ResourceLocation target = ResourceLocation.parse(id);
        return snap.stream()
                .filter(e -> e.source() == TradeIndexEntry.Source.VANILLA)
                .anyMatch(e -> e.profession().equals(target));
    }

    private static void assertWorkstationItem(GameTestHelper helper, List<TradeIndexEntry> snap,
                                              String professionId,
                                              net.minecraft.world.item.Item expectedItem) {
        ResourceLocation target = ResourceLocation.parse(professionId);
        TradeIndexEntry found = snap.stream()
                .filter(e -> e.source() == TradeIndexEntry.Source.VANILLA)
                .filter(e -> e.profession().equals(target))
                .findFirst()
                .orElse(null);
        helper.assertTrue(found != null, "expected at least one vanilla entry for " + professionId);
        helper.assertTrue(!found.workstation().isEmpty(),
                "workstation stack must be non-empty for " + professionId + ", got: " + found.workstation());
        helper.assertTrue(found.workstation().getItem() == expectedItem,
                "workstation item mismatch for " + professionId + ": expected=" + expectedItem
                        + " got=" + found.workstation());
    }
}
