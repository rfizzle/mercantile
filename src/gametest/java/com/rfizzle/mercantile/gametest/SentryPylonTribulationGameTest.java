package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.compat.tribulation.TribulationCompat;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class SentryPylonTribulationGameTest implements FabricGameTest {

    private static final BlockPos PYLON = new BlockPos(1, 2, 1);
    private static final String BATCH = "sentryTribulation";

    /**
     * Tribulation is absent from the gametest classpath, so this exercises the fallback path in a
     * live world: the effective limits must be exactly the configured defaults and nothing throws.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
    public void fallsBackToConfigDefaultsWithoutTribulation(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(PYLON);
        if (be == null) helper.fail("pylon block entity missing");
        be.setFuel(2);

        MercantileConfig config = MercantileConfig.get();
        TribulationCompat.EffectivePylonLimits limits =
                TribulationCompat.effectiveLimits(helper.getLevel(), helper.absolutePos(PYLON), config);

        helper.assertTrue(limits.maxGolems() == config.pylonMaxGolems,
                "without Tribulation, max golems must be the configured default (got " + limits.maxGolems() + ")");
        helper.assertTrue(limits.detectionRadius() == config.pylonDetectionRadius,
                "without Tribulation, detection radius must be the configured default (got " + limits.detectionRadius() + ")");
        helper.succeed();
    }
}
