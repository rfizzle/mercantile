package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

@SuppressWarnings("UnstableApiUsage")
public class SentryPylonHopperGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void pipeInsertViaTransferApi(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        BlockPos abs = helper.absolutePos(pos);
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), abs, Direction.UP);
        helper.assertTrue(storage != null, "ItemStorage.SIDED should be non-null for SENTRY_PYLON_BE");

        // Insert 5 iron blocks — fuel should reflect the count
        try (Transaction txn = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(Items.IRON_BLOCK), 5, txn);
            txn.commit();
            helper.assertTrue(inserted == 5,
                    "should accept 5 iron blocks (got " + inserted + ")");
        }
        helper.assertTrue(be.getFuel() == 5,
                "fuel should be 5 after inserting 5 iron blocks (got " + be.getFuel() + ")");

        // Non-iron-block items are rejected
        try (Transaction txn = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(Items.STONE), 3, txn);
            txn.commit();
            helper.assertTrue(inserted == 0,
                    "stone should be rejected (got " + inserted + ")");
        }
        helper.assertTrue(be.getFuel() == 5,
                "fuel should still be 5 after rejected stone (got " + be.getFuel() + ")");

        // Fill to max-1, then attempt bulk insert — only 1 is accepted, rest left over
        int max = be.getMaxFuel();
        be.setFuel(max - 1);
        try (Transaction txn = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(Items.IRON_BLOCK), 5, txn);
            txn.commit();
            helper.assertTrue(inserted == 1,
                    "only 1 iron block should fit when 1 under cap (got " + inserted + ")");
        }
        helper.assertTrue(be.getFuel() == max,
                "fuel should be at cap after partial insert (got " + be.getFuel() + ")");

        // Insert attempt at cap returns 0
        try (Transaction txn = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(Items.IRON_BLOCK), 1, txn);
            txn.commit();
            helper.assertTrue(inserted == 0,
                    "overflow insert at cap should return 0 (got " + inserted + ")");
        }
        helper.assertTrue(be.getFuel() == max, "fuel should remain at cap");

        // No extraction from any face
        try (Transaction txn = Transaction.openOuter()) {
            long extracted = storage.extract(ItemVariant.of(Items.IRON_BLOCK), 1, txn);
            txn.commit();
            helper.assertTrue(extracted == 0,
                    "extraction should always return 0 (got " + extracted + ")");
        }
        helper.assertTrue(be.getFuel() == max,
                "fuel should be unchanged after extract attempt (got " + be.getFuel() + ")");

        // enableSentryPylon=false disables the input pathway entirely
        boolean savedEnabled = MercantileConfig.get().enableSentryPylon;
        try {
            MercantileConfig.get().enableSentryPylon = false;
            be.setFuel(0);
            try (Transaction txn = Transaction.openOuter()) {
                long inserted = storage.insert(ItemVariant.of(Items.IRON_BLOCK), 1, txn);
                txn.commit();
                helper.assertTrue(inserted == 0,
                        "insert should return 0 when enableSentryPylon=false (got " + inserted + ")");
            }
            helper.assertTrue(be.getFuel() == 0,
                    "fuel should remain 0 when feature is disabled (got " + be.getFuel() + ")");
        } finally {
            MercantileConfig.get().enableSentryPylon = savedEnabled;
        }

        helper.succeed();
    }

    // Covers AC (a): hopper above transfers iron blocks one at a time until pylonMaxFuel is reached
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void hopperAboveFuelsToCap(GameTestHelper helper) {
        BlockPos pylonPos = new BlockPos(1, 2, 1);
        BlockPos hopperPos = new BlockPos(1, 3, 1);
        helper.setBlock(pylonPos, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(hopperPos, Blocks.HOPPER);

        SentryPylonBlockEntity pylonBe = (SentryPylonBlockEntity) helper.getBlockEntity(pylonPos);
        HopperBlockEntity hopperBe = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        helper.assertTrue(pylonBe != null, "pylon block entity should exist");
        helper.assertTrue(hopperBe != null, "hopper block entity should exist");

        int max = pylonBe.getMaxFuel();
        int n = 64; // iron block stack max; spec guarantees pylonMaxFuel < 64
        hopperBe.setItem(0, new ItemStack(Items.IRON_BLOCK, n));

        helper.succeedWhen(() -> {
            helper.assertTrue(pylonBe.getFuel() == max,
                    "fuel should reach cap (got " + pylonBe.getFuel() + ", expected " + max + ")");
            helper.assertTrue(hopperBe.getItem(0).getCount() == n - max,
                    "hopper slot 0 should have " + (n - max) + " iron blocks remaining (got " + hopperBe.getItem(0).getCount() + ")");
        });
    }

    // Covers AC (b): non-iron items are skipped; only iron blocks are consumed
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void hopperAboveMixedInventoryOnlyConsumesIron(GameTestHelper helper) {
        BlockPos pylonPos = new BlockPos(1, 2, 1);
        BlockPos hopperPos = new BlockPos(1, 3, 1);
        helper.setBlock(pylonPos, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(hopperPos, Blocks.HOPPER);

        SentryPylonBlockEntity pylonBe = (SentryPylonBlockEntity) helper.getBlockEntity(pylonPos);
        HopperBlockEntity hopperBe = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        helper.assertTrue(pylonBe != null, "pylon block entity should exist");
        helper.assertTrue(hopperBe != null, "hopper block entity should exist");

        int max = pylonBe.getMaxFuel();
        pylonBe.setFuel(max - 5); // leave room for all 3 iron blocks
        hopperBe.setItem(0, new ItemStack(Items.STONE, 16));
        hopperBe.setItem(1, new ItemStack(Items.IRON_BLOCK, 3));
        hopperBe.setItem(2, new ItemStack(Items.DIRT, 8));

        helper.succeedWhen(() -> {
            helper.assertTrue(pylonBe.getFuel() == max - 2,
                    "fuel should be max-2 (got " + pylonBe.getFuel() + ", expected " + (max - 2) + ")");
            helper.assertTrue(hopperBe.getItem(0).getCount() == 16,
                    "stone slot should be unchanged (got " + hopperBe.getItem(0).getCount() + ")");
            helper.assertTrue(hopperBe.getItem(1).isEmpty(),
                    "iron block slot should be empty after transfer (count: " + hopperBe.getItem(1).getCount() + ")");
            helper.assertTrue(hopperBe.getItem(2).getCount() == 8,
                    "dirt slot should be unchanged (got " + hopperBe.getItem(2).getCount() + ")");
        });
    }

    // Covers AC (c): hopper above does nothing when pylon is already at cap
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hopperAboveAtCapStopsConsumption(GameTestHelper helper) {
        BlockPos pylonPos = new BlockPos(1, 2, 1);
        BlockPos hopperPos = new BlockPos(1, 3, 1);
        helper.setBlock(pylonPos, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(hopperPos, Blocks.HOPPER);

        SentryPylonBlockEntity pylonBe = (SentryPylonBlockEntity) helper.getBlockEntity(pylonPos);
        HopperBlockEntity hopperBe = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        helper.assertTrue(pylonBe != null, "pylon block entity should exist");
        helper.assertTrue(hopperBe != null, "hopper block entity should exist");

        int max = pylonBe.getMaxFuel();
        pylonBe.setFuel(max);
        hopperBe.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));

        // runAfterDelay proves nothing happens — succeedWhen would short-circuit at tick 0
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pylonBe.getFuel() == max,
                    "fuel should remain at cap (got " + pylonBe.getFuel() + ", expected " + max + ")");
            helper.assertTrue(hopperBe.getItem(0).getCount() == 4,
                    "hopper slot 0 should still have 4 iron blocks (got " + hopperBe.getItem(0).getCount() + ")");
            helper.succeed();
        });
    }

    // Covers AC (d): hopper below cannot extract fuel — canTakeItemThroughFace always returns false
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hopperBelowCannotExtract(GameTestHelper helper) {
        BlockPos pylonPos = new BlockPos(1, 3, 1);
        BlockPos hopperPos = new BlockPos(1, 2, 1); // hopper below, FACING=DOWN, pulls from block above
        helper.setBlock(pylonPos, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(hopperPos, Blocks.HOPPER);

        SentryPylonBlockEntity pylonBe = (SentryPylonBlockEntity) helper.getBlockEntity(pylonPos);
        HopperBlockEntity hopperBe = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        helper.assertTrue(pylonBe != null, "pylon block entity should exist");
        helper.assertTrue(hopperBe != null, "hopper block entity should exist");

        pylonBe.setFuel(5);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pylonBe.getFuel() == 5,
                    "pylon fuel should be unchanged (got " + pylonBe.getFuel() + ", expected 5)");
            helper.assertTrue(hopperBe.isEmpty(),
                    "hopper should remain empty — no extraction from pylon");
            helper.succeed();
        });
    }
}
