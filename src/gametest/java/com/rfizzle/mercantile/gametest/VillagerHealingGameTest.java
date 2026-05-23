package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void regenDurationDoubledOnVillager(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        int baseDuration = 200;
        float multiplier = MercantileConfig.get().healingMultiplier;

        VillagerHealingContext.enter();
        try {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, baseDuration, 0));
        } finally {
            VillagerHealingContext.exit();
        }

        MobEffectInstance applied = villager.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(applied != null, "Regen effect should be present on villager");
        int expected = (int) (baseDuration * multiplier);
        helper.assertTrue(applied.getDuration() == expected,
                "Expected regen duration " + expected + ", got " + applied.getDuration());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void regenDurationNormalOnNonVillager(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 0, 1, 0);
        int baseDuration = 200;

        VillagerHealingContext.enter();
        try {
            cow.addEffect(new MobEffectInstance(MobEffects.REGENERATION, baseDuration, 0));
        } finally {
            VillagerHealingContext.exit();
        }

        MobEffectInstance applied = cow.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(applied != null, "Regen effect should be present on cow");
        helper.assertTrue(applied.getDuration() == baseDuration,
                "Cow regen duration should not change: expected " + baseDuration
                        + ", got " + applied.getDuration());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void regenDurationNormalWhenContextInactive(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        int baseDuration = 200;

        villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, baseDuration, 0));

        MobEffectInstance applied = villager.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(applied != null, "Regen effect should be present on villager");
        helper.assertTrue(applied.getDuration() == baseDuration,
                "Without context, regen duration should not change: expected " + baseDuration
                        + ", got " + applied.getDuration());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void infiniteRegenDurationNotMultiplied(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        VillagerHealingContext.enter();
        try {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    MobEffectInstance.INFINITE_DURATION, 0));
        } finally {
            VillagerHealingContext.exit();
        }

        MobEffectInstance applied = villager.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(applied != null, "Regen effect should be present on villager");
        helper.assertTrue(applied.isInfiniteDuration(),
                "Infinite duration must remain infinite, got duration " + applied.getDuration());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledHealingConfigSkipsBoost(GameTestHelper helper) {
        boolean saved = MercantileConfig.get().enableHealing;
        try {
            MercantileConfig.get().enableHealing = false;

            Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            villager.setHealth(10.0f);

            VillagerHealingContext.enter();
            try {
                villager.heal(4.0f);
            } finally {
                VillagerHealingContext.exit();
            }

            helper.assertTrue(Math.abs(villager.getHealth() - 14.0f) < 0.01f,
                    "With healing disabled, vanilla heal expected: 14.0, got " + villager.getHealth());
        } finally {
            MercantileConfig.get().enableHealing = saved;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledHealingConfigSkipsRegenDoubling(GameTestHelper helper) {
        boolean saved = MercantileConfig.get().enableHealing;
        try {
            MercantileConfig.get().enableHealing = false;

            Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            int baseDuration = 200;

            VillagerHealingContext.enter();
            try {
                villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, baseDuration, 0));
            } finally {
                VillagerHealingContext.exit();
            }

            MobEffectInstance applied = villager.getEffect(MobEffects.REGENERATION);
            helper.assertTrue(applied != null, "Regen effect should be present on villager");
            helper.assertTrue(applied.getDuration() == baseDuration,
                    "With healing disabled, regen duration should not change: expected "
                            + baseDuration + ", got " + applied.getDuration());
        } finally {
            MercantileConfig.get().enableHealing = saved;
        }
        helper.succeed();
    }
}
