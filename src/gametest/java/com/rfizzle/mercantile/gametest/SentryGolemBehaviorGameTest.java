package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.ReturnToPylonGoal;
import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.block.SentryTargetHostilesGoal;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SentryGolemBehaviorGameTest implements FabricGameTest {

    private static final BlockPos PYLON = new BlockPos(1, 2, 1);
    private static final int TEST_RADIUS = 4;

    private static SentryPylonBlockEntity placePylonOnFloor(GameTestHelper helper) {
        MercantileConfig.get().pylonDetectionRadius = TEST_RADIUS;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(PYLON);
        if (be == null) helper.fail("pylon block entity missing");
        return be;
    }

    private static IronGolem spawnSentryAt(GameTestHelper helper, SentryPylonBlockEntity be, BlockPos rel) {
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, rel);
        SentryGolemTag.markAsSentry(golem, helper.absolutePos(PYLON));
        be.addSentryForTesting(golem.getUUID());
        return golem;
    }

    private static GoalSelector readGoalSelector(Mob mob) throws ReflectiveOperationException {
        Field field = Mob.class.getDeclaredField("goalSelector");
        field.setAccessible(true);
        return (GoalSelector) field.get(mob);
    }

    private static GoalSelector readTargetSelector(Mob mob) throws ReflectiveOperationException {
        Field field = Mob.class.getDeclaredField("targetSelector");
        field.setAccessible(true);
        return (GoalSelector) field.get(mob);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryDespawnAlone", timeoutTicks = 200)
    public void sentryDespawnsAfterCountdown(GameTestHelper helper) {
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                "sentry should be tracked");

        helper.runAfterDelay(60, () -> {
            try {
                List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
                helper.assertTrue(golems.isEmpty(),
                        "sentry should despawn after countdown (got " + golems.size() + ")");
                helper.assertTrue(be.getSentries().isEmpty(),
                        "tracked sentries should be cleared");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryResetAlone", timeoutTicks = 240)
    public void countdownResetsOnHostileReturn(GameTestHelper helper) {
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 2;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        helper.runAfterDelay(20, () -> {
            Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
            helper.assertTrue(husk.isAlive(), "husk should spawn");
        });

        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(golem.isAlive(),
                        "sentry should still be alive after hostile return resets countdown");
                helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                        "sentry should still be tracked");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void sentryDropsNothingOnKill(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        helper.assertTrue(SentryGolemTag.isSentry(golem), "sentry tag should be set");

        golem.kill();

        helper.runAfterDelay(20, () -> {
            List<ItemEntity> items = helper.getEntities(EntityType.ITEM);
            helper.assertTrue(items.isEmpty(),
                    "sentry should drop no items on kill (got " + items.size() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void ironGolemMixinAddsReturnToPylonGoal(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        try {
            GoalSelector selector = readGoalSelector(golem);
            boolean hasReturnGoal = selector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal)
                    .anyMatch(g -> g instanceof ReturnToPylonGoal);
            helper.assertTrue(hasReturnGoal,
                    "IronGolemMixin should have added ReturnToPylonGoal to goalSelector");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("reflection failed: " + e.getMessage());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryReturnGoalAlone", timeoutTicks = 80)
    public void returnGoalActivatesWhenOutsideRadius(GameTestHelper helper) {
        MercantileConfig.get().pylonDetectionRadius = TEST_RADIUS;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(PYLON, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(PYLON);
        helper.assertTrue(be != null, "pylon block entity missing");

        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        SentryGolemTag.markAsSentry(golem, helper.absolutePos(PYLON));
        be.addSentryForTesting(golem.getUUID());

        ReturnToPylonGoal goal = new ReturnToPylonGoal(golem);
        helper.assertFalse(goal.canUse(),
                "goal should NOT be usable when golem is inside radius");

        BlockPos farAbs = helper.absolutePos(new BlockPos(7, 2, 7));
        golem.teleportTo(farAbs.getX() + 0.5, farAbs.getY(), farAbs.getZ() + 0.5);

        helper.assertTrue(goal.canUse(),
                "goal SHOULD be usable when golem is outside radius");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void returnGoalSkipsWhenPylonMissing(GameTestHelper helper) {
        MercantileConfig.get().pylonDetectionRadius = TEST_RADIUS;
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        SentryGolemTag.markAsSentry(golem, helper.absolutePos(PYLON));

        ReturnToPylonGoal goal = new ReturnToPylonGoal(golem);
        helper.assertFalse(goal.canUse(),
                "goal should NOT be usable when pylon block is missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void sentryGoalAcquiresCreeper(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(4, 2, 2));

        // Freshly spawned entities only enter the level's entity lookup once the spawn queue is
        // drained next tick, so the goal's level scan can't see the creeper synchronously. Defer
        // the check a couple ticks, then poll: the goal's randomInterval makes a single canUse()
        // probabilistic, so attempt it enough times that acquisition is deterministic. The creeper
        // is the only hostile present, so a positive canUse() means it was the target found.
        helper.runAfterDelay(2, () -> {
            SentryTargetHostilesGoal goal = new SentryTargetHostilesGoal(golem);
            boolean acquired = false;
            for (int i = 0; i < 200 && !acquired; i++) {
                acquired = goal.canUse();
            }
            helper.assertTrue(acquired,
                    "sentry goal should acquire a nearby creeper (vanilla golems exclude creepers)");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void sentryGoalInertOnPlainGolem(GameTestHelper helper) {
        placePylonOnFloor(helper);
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(4, 2, 2));

        SentryTargetHostilesGoal goal = new SentryTargetHostilesGoal(golem);
        for (int i = 0; i < 200; i++) {
            helper.assertFalse(goal.canUse(),
                    "the sentry target goal must stay inert on a non-sentry iron golem");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void ironGolemMixinAddsSentryTargetGoal(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        try {
            GoalSelector selector = readTargetSelector(golem);
            boolean hasTargetGoal = selector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal)
                    .anyMatch(g -> g instanceof SentryTargetHostilesGoal);
            helper.assertTrue(hasTargetGoal,
                    "IronGolemMixin should have added SentryTargetHostilesGoal to targetSelector");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("reflection failed: " + e.getMessage());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void creeperDoesNotPrimeAgainstSentry(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(3, 2, 2));

        creeper.setTarget(golem);
        creeper.setSwellDir(1);
        helper.assertTrue(creeper.getSwellDir() < 0,
                "a creeper targeting a sentry should be forced to de-swell, not prime");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void creeperStillPrimesAgainstNonSentry(GameTestHelper helper) {
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(3, 2, 2));

        creeper.setTarget(golem);
        creeper.setSwellDir(1);
        helper.assertTrue(creeper.getSwellDir() > 0,
                "the no-detonate guard must be scoped to sentries; a plain golem still primes the creeper");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void villagerGolemCountExcludesSentries(GameTestHelper helper) {
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 1));
        IronGolem sentry = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        villager.getBrain().eraseMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY);

        List<LivingEntity> sentryOnly = new ArrayList<>();
        sentryOnly.add(sentry);
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, sentryOnly);

        GolemSensor.checkForNearbyGolem(villager);
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY),
                "sentry alone must not trigger GOLEM_DETECTED_RECENTLY");

        IronGolem regular = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(5, 2, 1));
        List<LivingEntity> mixed = new ArrayList<>();
        mixed.add(sentry);
        mixed.add(regular);
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, mixed);

        GolemSensor.checkForNearbyGolem(villager);
        helper.assertTrue(
                villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY),
                "regular golem should trigger GOLEM_DETECTED_RECENTLY");

        helper.succeed();
    }
}
