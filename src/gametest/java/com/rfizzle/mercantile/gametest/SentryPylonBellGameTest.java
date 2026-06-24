package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;

public class SentryPylonBellGameTest implements FabricGameTest {

    // EMPTY_STRUCTURE is all air; lay a floor so a placed bell has support (otherwise it pops off
    // and its POI — which the pylon's findNearestBell queries — never registers).
    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static SentryPylonBlockEntity fueledPylon(GameTestHelper helper, BlockPos pylonRel) {
        helper.setBlock(pylonRel, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity pylon = (SentryPylonBlockEntity) helper.getBlockEntity(pylonRel);
        pylon.setFuel(pylon.getMaxFuel());
        return pylon;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonRingsBellOnActivation(GameTestHelper helper) {
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        // Bell beside the pylon, not between it and the threat: detection is line-of-sight gated,
        // so a bell sitting on the pylon-to-zombie line would occlude the very threat it announces.
        BlockPos bellRel = new BlockPos(1, 2, 2);
        buildFloor(helper);
        SentryPylonBlockEntity pylon = fueledPylon(helper, pylonRel);
        helper.setBlock(bellRel, Blocks.BELL);
        helper.spawn(EntityType.ZOMBIE, 3, 2, 1);

        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        MercantileConfig.get().enablePylonBellAlarm = true;
        try {
            pylon.setScanCooldownForTesting(0);
            pylon.tickServerCommon();

            BellBlockEntity bell = (BellBlockEntity) helper.getBlockEntity(bellRel);
            helper.assertTrue(bell != null, "Bell block entity should exist");
            helper.assertTrue(bell.shaking,
                    "Bell should be ringing after the pylon activates near a threat");
            helper.succeed();
        } finally {
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonRespectsBellConfig(GameTestHelper helper) {
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        BlockPos bellRel = new BlockPos(1, 2, 2);
        buildFloor(helper);
        SentryPylonBlockEntity pylon = fueledPylon(helper, pylonRel);
        helper.setBlock(bellRel, Blocks.BELL);
        helper.spawn(EntityType.ZOMBIE, 3, 2, 1);

        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        MercantileConfig.get().enablePylonBellAlarm = false;
        try {
            pylon.setScanCooldownForTesting(0);
            pylon.tickServerCommon();

            BellBlockEntity bell = (BellBlockEntity) helper.getBlockEntity(bellRel);
            helper.assertTrue(bell != null, "Bell block entity should exist");
            helper.assertFalse(bell.shaking,
                    "Bell should NOT ring when enablePylonBellAlarm is false");
            helper.succeed();
        } finally {
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonDoesNotRingForSealedThreat(GameTestHelper helper) {
        // A hostile inside the detection radius but walled off from the pylon must NOT ring the
        // bell — otherwise a mob in a cave or a sealed room rings it endlessly. This is the core
        // of the line-of-sight detection fix.
        int savedRadius = MercantileConfig.get().pylonDetectionRadius;
        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        MercantileConfig.get().pylonDetectionRadius = 8;
        MercantileConfig.get().enablePylonBellAlarm = true;
        try {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                }
            }
            BlockPos pylonRel = new BlockPos(1, 2, 1);
            BlockPos bellRel = new BlockPos(1, 2, 2);
            SentryPylonBlockEntity pylon = fueledPylon(helper, pylonRel);
            helper.setBlock(bellRel, Blocks.BELL);

            // Solid wall at x=3 sealing the threat side (x>3) off from the pylon (x=1).
            for (int z = 0; z <= 2; z++) {
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                }
            }
            helper.spawn(EntityType.ZOMBIE, 5, 2, 1);

            pylon.setScanCooldownForTesting(0);
            pylon.tickServerCommon();

            BellBlockEntity bell = (BellBlockEntity) helper.getBlockEntity(bellRel);
            helper.assertTrue(bell != null, "Bell block entity should exist");
            helper.assertFalse(bell.shaking,
                    "Bell must NOT ring for a threat the pylon has no line of sight to");
            helper.succeed();
        } finally {
            MercantileConfig.get().pylonDetectionRadius = savedRadius;
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonHandlesNoBellCase(GameTestHelper helper) {
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        buildFloor(helper);
        SentryPylonBlockEntity pylon = fueledPylon(helper, pylonRel);
        helper.spawn(EntityType.ZOMBIE, 3, 2, 1);

        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        MercantileConfig.get().enablePylonBellAlarm = true;
        try {
            pylon.setScanCooldownForTesting(0);
            pylon.tickServerCommon();
            helper.succeed();
        } catch (Exception e) {
            helper.fail("Pylon should handle the no-bell case without errors: " + e.getMessage());
        } finally {
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }
}
