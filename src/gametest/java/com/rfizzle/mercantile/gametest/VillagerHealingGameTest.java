package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;

public class VillagerHealingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void splashHealingMultipliedOnVillager(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setHealth(10.0f);

        VillagerHealingContext.enter();
        try {
            villager.heal(4.0f);
        } finally {
            VillagerHealingContext.exit();
        }

        float expected = 10.0f + 4.0f * MercantileConfig.get().healingMultiplier;
        helper.assertTrue(Math.abs(villager.getHealth() - expected) < 0.01f,
                "Expected health " + expected + ", got " + villager.getHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void splashHealingNormalOnNonVillager(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 0, 1, 0);
        cow.setHealth(1.0f);

        VillagerHealingContext.enter();
        try {
            cow.heal(4.0f);
        } finally {
            VillagerHealingContext.exit();
        }

        helper.assertTrue(Math.abs(cow.getHealth() - 5.0f) < 0.01f,
                "Non-villager should heal normally: expected 5.0, got " + cow.getHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tippedArrowHealingNotMultiplied(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setHealth(10.0f);

        villager.heal(4.0f);

        helper.assertTrue(Math.abs(villager.getHealth() - 14.0f) < 0.01f,
                "Arrow healing should not be boosted: expected 14.0, got " + villager.getHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void customMultiplierValue(GameTestHelper helper) {
        float original = MercantileConfig.get().healingMultiplier;
        try {
            MercantileConfig.get().healingMultiplier = 5.0f;

            Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            villager.setHealth(10.0f);

            VillagerHealingContext.enter();
            try {
                villager.heal(2.0f);
            } finally {
                VillagerHealingContext.exit();
            }

            helper.assertTrue(Math.abs(villager.getHealth() - 20.0f) < 0.01f,
                    "Expected health 20.0 (10 + 2*5.0), got " + villager.getHealth());
        } finally {
            MercantileConfig.get().healingMultiplier = original;
        }
        helper.succeed();
    }
}
