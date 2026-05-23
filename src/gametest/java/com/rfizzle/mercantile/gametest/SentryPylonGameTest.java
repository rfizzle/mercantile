package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SentryPylonGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void blockPlacesAndPersistsAsBlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        helper.assertBlockPresent(MercantileRegistry.SENTRY_PYLON, pos);

        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof SentryPylonBlockEntity, "block entity should be SentryPylonBlockEntity");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fuelRoundTripsThroughNbt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        be.setFuel(3);
        helper.assertTrue(be.getFuel() == 3, "fuel should be 3 after setFuel(3)");

        CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());

        SentryPylonBlockEntity reloaded = new SentryPylonBlockEntity(
                be.getBlockPos(), MercantileRegistry.SENTRY_PYLON.defaultBlockState());
        reloaded.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(reloaded.getFuel() == 3, "fuel should round-trip through NBT");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fuelClampsToMax(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        int max = be.getMaxFuel();
        be.setFuel(max + 100);
        helper.assertTrue(be.getFuel() == max,
                "fuel should clamp to maxFuel (got " + be.getFuel() + ", expected " + max + ")");

        be.setFuel(-5);
        helper.assertTrue(be.getFuel() == 0,
                "fuel should clamp to 0 (got " + be.getFuel() + ")");
        helper.succeed();
    }
}
