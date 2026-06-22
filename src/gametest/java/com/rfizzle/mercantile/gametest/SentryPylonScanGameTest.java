package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.block.SentryPylonScanner;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

public class SentryPylonScanGameTest implements FabricGameTest {

    private static final BlockPos PYLON = new BlockPos(1, 2, 1);
    private static final int TEST_RADIUS = 4;
    private static final String BATCH = "sentryScan";

    private static SentryPylonBlockEntity placePylonOnFloor(GameTestHelper helper, int fuel) {
        MercantileConfig.get().pylonDetectionRadius = TEST_RADIUS;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(PYLON);
        if (be == null) helper.fail("pylon block entity missing");
        be.setFuel(fuel);
        return be;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void spawnsGolemOnHostileWithFuel(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 4);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        helper.assertTrue(husk.isAlive(), "husk should be alive");

        helper.succeedWhen(() -> {
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(!golems.isEmpty(), "expected at least one iron golem");
            IronGolem golem = golems.get(0);
            helper.assertTrue(SentryGolemTag.isSentry(golem),
                    "spawned golem should carry the sentry tag");
            helper.assertTrue(be.getFuel() == 3,
                    "fuel should be 3 after one spawn (got " + be.getFuel() + ")");
            helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                    "pylon should track spawned golem UUID");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void noSpawnWithoutFuelTriggersAlert(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 0);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(be.getOutOfFuelCooldown() > 0,
                    "out-of-fuel alert should fire when hostile present and no fuel");
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(golems.isEmpty(),
                    "no golem should spawn when out of fuel (got " + golems.size() + ")");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void noSpawnAtMaxSentries(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 4);
        BlockPos pylonAbs = helper.absolutePos(PYLON);

        for (int i = 0; i < 3; i++) {
            IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM,
                    new BlockPos(5, 2, 1 + i));
            SentryGolemTag.markAsSentry(golem, pylonAbs);
            be.addSentryForTesting(golem.getUUID());
        }
        helper.assertTrue(be.getSentries().size() == 3, "should pre-populate 3 sentries");

        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));

        helper.runAfterDelay(60, () -> {
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(golems.size() == 3,
                    "no new golem should spawn at max sentries (got " + golems.size() + ")");
            helper.assertTrue(be.getFuel() == 4,
                    "fuel should not be consumed at max sentries (got " + be.getFuel() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void noSpawnWhenPowered(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 4);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(helper.getBlockState(PYLON).getValue(SentryPylonBlock.POWERED),
                    "pylon should be powered");
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(golems.isEmpty(),
                    "no golem should spawn while powered (got " + golems.size() + ")");
            helper.assertTrue(be.getFuel() == 4,
                    "fuel should not be consumed while powered (got " + be.getFuel() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void ignoresPassiveMobs(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 4);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 2, 3));
        helper.assertTrue(cow.isAlive(), "cow should spawn");

        helper.runAfterDelay(60, () -> {
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(golems.isEmpty(),
                    "no golem should spawn for passive mob (got " + golems.size() + ")");
            helper.assertTrue(be.getFuel() == 4,
                    "fuel should not be consumed for passive mob (got " + be.getFuel() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void prunesDeadSentry(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 0);
        UUID dead = UUID.randomUUID();
        be.addSentryForTesting(dead);
        helper.assertTrue(be.getSentries().contains(dead), "fake UUID should be tracked");

        helper.runAfterDelay(50, () -> {
            helper.assertFalse(be.getSentries().contains(dead),
                    "dead UUID should be pruned after scan cycle");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
    public void nbtRoundTripsSentriesAndCooldown(GameTestHelper helper) {
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(PYLON);
        helper.assertTrue(be != null, "block entity should exist");

        UUID id1 = new UUID(0x1234L, 0x5678L);
        UUID id2 = new UUID(0xCAFEL, 0xBABEL);
        be.addSentryForTesting(id1);
        be.addSentryForTesting(id2);
        be.setFuel(2);

        CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());

        SentryPylonBlockEntity reloaded = new SentryPylonBlockEntity(
                be.getBlockPos(), MercantileRegistry.SENTRY_PYLON.defaultBlockState());
        reloaded.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(reloaded.getFuel() == 2, "fuel should round-trip");
        helper.assertTrue(reloaded.getSentries().size() == 2,
                "sentries set size should round-trip (got " + reloaded.getSentries().size() + ")");
        helper.assertTrue(reloaded.getSentries().contains(id1), "sentry id1 should round-trip");
        helper.assertTrue(reloaded.getSentries().contains(id2), "sentry id2 should round-trip");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void doesNotSpawnBehindWalls(GameTestHelper helper) {
        MercantileConfig.get().pylonDetectionRadius = 8;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        // Solid wall splitting the pylon (x<4) from the threat side (x>4).
        for (int z = 0; z <= 7; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }

        BlockPos pylonAbs = helper.absolutePos(PYLON);
        BlockPos clear = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 6));

        helper.assertTrue(SentryPylonScanner.hasLineOfSight(helper.getLevel(), pylonAbs, clear),
                "open position on the pylon side should have line of sight");
        helper.assertFalse(SentryPylonScanner.hasLineOfSight(helper.getLevel(), pylonAbs, behind),
                "position behind a wall should not have line of sight");

        // A threat across the wall must never yield a spawn position the pylon cannot see —
        // this is what keeps sentries from materializing underground / behind walls.
        BlockPos nearAbs = helper.absolutePos(new BlockPos(6, 2, 6));
        for (int i = 0; i < 40; i++) {
            BlockPos spawn = SentryPylonScanner.findSpawnPos(helper.getLevel(), nearAbs, pylonAbs, 8);
            if (spawn != null) {
                helper.assertTrue(SentryPylonScanner.hasLineOfSight(helper.getLevel(), pylonAbs, spawn),
                        "findSpawnPos must only return positions with line of sight to the pylon");
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 120)
    public void spawnedGolemOnSolidGround(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper, 4);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));

        helper.succeedWhen(() -> {
            List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
            helper.assertTrue(!golems.isEmpty(), "expected a sentry golem to spawn");
            IronGolem golem = golems.get(0);
            BlockPos feet = golem.blockPosition();
            BlockPos below = feet.below();
            helper.assertTrue(helper.getLevel().getBlockState(below).isFaceSturdy(
                    helper.getLevel(), below, net.minecraft.core.Direction.UP),
                    "golem should spawn on a sturdy face");
            helper.assertTrue(be.getFuel() == 3, "one fuel should have been consumed");
        });
    }
}
