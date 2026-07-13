package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.HoldNearPylonGoal;
import com.rfizzle.mercantile.block.ReturnToPylonGoal;
import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.block.SentryTargetHostilesGoal;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
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

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentrySealedAlone", timeoutTicks = 200)
    public void countdownNotResetForSealedThreat(GameTestHelper helper) {
        // A hostile inside the radius but walled off from the pylon must not keep a sentry alive —
        // otherwise the golem stands around indefinitely, unable to reach a threat it can't see.
        // The despawn countdown should run to completion exactly as if no threat were present.
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        // Wall at x=3 seals the threat side (x>3) off from the pylon at (1,2,1).
        for (int z = 0; z <= 7; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        helper.assertTrue(husk.isAlive(), "sealed husk should spawn");

        helper.runAfterDelay(60, () -> {
            try {
                List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
                helper.assertTrue(golems.isEmpty(),
                        "sentry should despawn — a walled-off threat must not reset the countdown "
                                + "(got " + golems.size() + ")");
                helper.assertTrue(be.getSentries().isEmpty(),
                        "tracked sentries should be cleared");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryEngagedAlone", timeoutTicks = 200)
    public void countdownHeldForEngagedSentry(GameTestHelper helper) {
        // A sentry fighting a threat the pylon can't see (around a corner / behind cover) must not be
        // despawned mid-combat. The pylon has no line of sight to the husk, so its LoS reset never
        // fires; only the golem's own engagement holds the countdown open (issue #164).
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);

        // Wall at x=3 seals the threat side (x>3) off from the pylon at (1,2,1) — the pylon can't see
        // the husk. The sentry stands on the husk's side, so it (unlike the pylon) has a clear line.
        for (int z = 0; z <= 7; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(4, 2, 3));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        helper.assertTrue(husk.isAlive(), "husk should spawn");
        // spawnWithNoFreeWill strips the golem's goals, so this manually-set target stands in for the
        // combat the golem's own targeting would drive in a live game — the pylon still can't see it.
        golem.setTarget(husk);

        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(golem.isAlive(),
                        "sentry engaged with an out-of-sight threat must not despawn mid-combat");
                helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                        "engaged sentry should still be tracked");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryHurtAlone", timeoutTicks = 200)
    public void countdownHeldForRecentlyHurtSentry(GameTestHelper helper) {
        // A sentry taking fire from an in-zone hostile it has no current target on — vanilla
        // HurtByTargetGoal reacts to hits with no zone/LoS filter — must not despawn while under
        // attack, even when the pylon has no line of sight to the attacker (issue #164).
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);

        for (int z = 0; z <= 7; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(4, 2, 3));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        // Simulate a just-landed hit from the in-zone husk without applying damage: setLastHurtByMob
        // sets both the attacker and the hurt timestamp to the golem's current tick.
        golem.setLastHurtByMob(husk);
        helper.assertTrue(golem.getTarget() == null,
                "no target — the hold must come from recent damage, not targeting");

        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(golem.isAlive(),
                        "sentry under recent fire from an in-zone threat must not despawn");
                helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                        "recently-hurt sentry should still be tracked");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryPoweredAlone", timeoutTicks = 200)
    public void poweredPylonWindsDownSentries(GameTestHelper helper) {
        // A redstone-disabled pylon stops caring: it winds its summons down on the normal countdown
        // even with a threat present that would otherwise hold them (issue #165). The husk sits in the
        // open — visible to the pylon (would fire the LoS recheck reset) — and is set as the golem's
        // target (would fire the engaged hold); powering the pylon must bypass both.
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        be.setFuel(4);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        helper.assertTrue(husk.isAlive(), "husk should spawn");
        golem.setTarget(husk);

        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(helper.getBlockState(PYLON).getValue(SentryPylonBlock.POWERED),
                        "pylon should be powered");
                List<IronGolem> golems = helper.getEntities(EntityType.IRON_GOLEM);
                helper.assertTrue(golems.isEmpty(),
                        "a powered pylon must wind its sentries down despite a visible, engaged threat "
                                + "(got " + golems.size() + ")");
                helper.assertTrue(be.getSentries().isEmpty(),
                        "tracked sentries should be cleared");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void breakingPylonDismissesSentries(GameTestHelper helper) {
        // Removing the pylon must dismiss its sentries — they are temporary summons, not persistent
        // entities (spec §18, issue #166). Without the onRemove hook the golems outlive the block
        // forever, since the only despawn path is the block-entity tick that dies with the block.
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        helper.assertTrue(be.getSentries().contains(golem.getUUID()), "sentry should be tracked");
        helper.assertTrue(golem.isAlive(), "sentry should be alive before the pylon is broken");

        helper.destroyBlock(PYLON);

        // onRemove discards the golem synchronously within destroyBlock's setBlockState call.
        helper.assertFalse(golem.isAlive(), "breaking the pylon must dismiss its sentry");
        helper.assertTrue(be.getSentries().isEmpty(), "tracked sentries should be cleared on removal");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void pylonStateFlipKeepsSentries(GameTestHelper helper) {
        // The removal hook keys off a genuine block change (!state.is(newState.getBlock())). A POWERED
        // flip is the same block, so it routes through onRemove but must NOT dismiss — otherwise every
        // redstone toggle would instantly vaporize the sentries instead of winding them down (issue #166).
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        helper.setBlock(PYLON, helper.getBlockState(PYLON).setValue(SentryPylonBlock.POWERED, true));

        helper.assertTrue(golem.isAlive(),
                "a POWERED property flip must not dismiss sentries — only real removal does");
        helper.assertTrue(be.getSentries().contains(golem.getUUID()),
                "sentry should still be tracked after a state flip");
        helper.succeed();
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

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryHoldGoalAlone", timeoutTicks = 80)
    public void holdNearPylonGoalGatesOnSentryAndTarget(GameTestHelper helper) {
        // The idle-hold goal is what keeps an unengaged sentry near its pylon instead of village-
        // strolling. It must engage for an in-radius sentry with no target, yield the moment a target
        // appears (so melee takes over), and stay inert on a plain golem.
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));

        HoldNearPylonGoal goal = new HoldNearPylonGoal(golem);
        helper.assertTrue(goal.canUse(),
                "hold goal SHOULD engage for an idle sentry inside its radius");

        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 2));
        golem.setTarget(husk);
        helper.assertFalse(goal.canUse(),
                "hold goal must yield once the sentry has a combat target");

        golem.setTarget(null);
        helper.assertTrue(goal.canUse(),
                "hold goal re-engages after the target clears");

        IronGolem plain = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(5, 2, 5));
        HoldNearPylonGoal plainGoal = new HoldNearPylonGoal(plain);
        helper.assertFalse(plainGoal.canUse(),
                "hold goal must stay inert on a non-sentry iron golem");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void ironGolemMixinAddsHoldNearPylonGoal(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        try {
            GoalSelector selector = readGoalSelector(golem);
            boolean hasHoldGoal = selector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal)
                    .anyMatch(g -> g instanceof HoldNearPylonGoal);
            helper.assertTrue(hasHoldGoal,
                    "IronGolemMixin should have added HoldNearPylonGoal to goalSelector");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("reflection failed: " + e.getMessage());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void holdNearPylonGoalOutranksWandering(GameTestHelper helper) {
        // The hold goal only kills the boundary ping-pong / village-strolling if it outranks the
        // vanilla ambient movement goals it needs to suppress.
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        try {
            GoalSelector selector = readGoalSelector(golem);
            WrappedGoal holdGoal = null;
            WrappedGoal strollGoal = null;
            WrappedGoal offerGoal = null;
            WrappedGoal moveBackGoal = null;
            for (WrappedGoal w : selector.getAvailableGoals()) {
                if (w.getGoal() instanceof HoldNearPylonGoal) {
                    holdGoal = w;
                }
                if (w.getGoal() instanceof GolemRandomStrollInVillageGoal
                        && (strollGoal == null || w.getPriority() < strollGoal.getPriority())) {
                    strollGoal = w;
                }
                if (w.getGoal() instanceof OfferFlowerGoal
                        && (offerGoal == null || w.getPriority() < offerGoal.getPriority())) {
                    offerGoal = w;
                }
                if (w.getGoal() instanceof MoveBackToVillageGoal
                        && (moveBackGoal == null || w.getPriority() < moveBackGoal.getPriority())) {
                    moveBackGoal = w;
                }
            }
            helper.assertTrue(holdGoal != null, "HoldNearPylonGoal not registered");
            helper.assertTrue(strollGoal != null, "vanilla stroll goal not found on iron golem");
            helper.assertTrue(offerGoal != null, "vanilla offer-flower goal not found on iron golem");
            helper.assertTrue(moveBackGoal != null, "vanilla move-back-to-village goal not found on iron golem");
            helper.assertTrue(holdGoal.getPriority() < strollGoal.getPriority(),
                    "HoldNearPylonGoal (" + holdGoal.getPriority() + ") must outrank the village "
                            + "stroll goal (" + strollGoal.getPriority() + ")");
            helper.assertTrue(holdGoal.getPriority() < offerGoal.getPriority(),
                    "HoldNearPylonGoal (" + holdGoal.getPriority() + ") must outrank the offer-flower "
                            + "goal (" + offerGoal.getPriority() + ")");
            helper.assertTrue(holdGoal.getPriority() < moveBackGoal.getPriority(),
                    "HoldNearPylonGoal (" + holdGoal.getPriority() + ") must outrank the "
                            + "move-back-to-village goal (" + moveBackGoal.getPriority() + ")");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("reflection failed: " + e.getMessage());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryTelegraphAlone", timeoutTicks = 160)
    public void despawnStageEscalatesBeforeDespawn(GameTestHelper helper) {
        // The pylon must drive the golem's despawn-telegraph stage up over the countdown's final
        // seconds (spec §18), then discard it. With a 3-second countdown (60 ticks) the whole span is
        // the telegraph window, so the stage climbs from light cracks early to full cracks near expiry.
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 3;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        // Sample the stage at two points, then assert once at the end — so no assertion can throw
        // before the config is restored, keeping sentryDespawnSeconds from leaking into later tests.
        int[] earlyStage = {-1};
        int[] lateStage = {-1};

        helper.runAfterDelay(15, () -> earlyStage[0] = stageOf(golem));
        helper.runAfterDelay(50, () -> lateStage[0] = stageOf(golem));

        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(earlyStage[0] >= 1,
                        "an idle sentry should be showing cracks partway into the countdown (got "
                                + earlyStage[0] + ")");
                helper.assertTrue(lateStage[0] == 3,
                        "the sentry should be fully cracked just before despawn (got " + lateStage[0] + ")");
                helper.assertTrue(lateStage[0] > earlyStage[0],
                        "the crack stage must escalate across the countdown (" + earlyStage[0]
                                + " -> " + lateStage[0] + ")");
                helper.assertTrue(helper.getEntities(EntityType.IRON_GOLEM).isEmpty(),
                        "the sentry should have despawned after the countdown");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
    }

    private static int stageOf(IronGolem golem) {
        Integer stage = golem.getAttached(MercantileAttachments.SENTRY_DESPAWN_STAGE);
        return stage == null ? 0 : stage;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sentryBlockedDespawnAlone", timeoutTicks = 200)
    public void blockedSentryStillDespawnsOnSchedule(GameTestHelper helper) {
        // A sentry that can't path home (walled off from its pylon) must still despawn when the
        // countdown expires — despawn is driven by the pylon's timer, not by the golem reaching home.
        final int savedDespawn = MercantileConfig.get().sentryDespawnSeconds;
        MercantileConfig.get().sentryDespawnSeconds = 1;
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(6, 2, 6));

        // Seal the golem's corner off from the pylon so no path home exists.
        for (int z = 0; z <= 7; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }

        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(helper.getEntities(EntityType.IRON_GOLEM).isEmpty(),
                        "a walled-off sentry must still despawn on the countdown");
                helper.assertTrue(be.getSentries().isEmpty(), "tracked sentries should be cleared");
                helper.succeed();
            } finally {
                MercantileConfig.get().sentryDespawnSeconds = savedDespawn;
            }
        });
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
    public void sentryGoalIgnoresThreatOutsidePylonZone(GameTestHelper helper) {
        // Aggro is pinned to the pylon's radius measured from the PYLON, not the golem. The creeper
        // sits ~4.1 blocks from the pylon (outside the radius-4 zone) but only 3 from the golem
        // (inside its search reach), so only the zone filter — not the search box — can reject it.
        SentryPylonBlockEntity be = placePylonOnFloor(helper);
        IronGolem golem = spawnSentryAt(helper, be, new BlockPos(2, 2, 2));
        helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(5, 2, 2));

        helper.runAfterDelay(2, () -> {
            SentryTargetHostilesGoal goal = new SentryTargetHostilesGoal(golem);
            for (int i = 0; i < 200; i++) {
                helper.assertFalse(goal.canUse(),
                        "a sentry must not acquire a hostile beyond its pylon's radius");
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void returnToPylonGoalOutranksMelee(GameTestHelper helper) {
        // A sentry led past its radius must abandon the chase and walk home, so the return goal has
        // to outrank vanilla's MeleeAttackGoal in the goal selector.
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        try {
            GoalSelector selector = readGoalSelector(golem);
            WrappedGoal returnGoal = null;
            WrappedGoal meleeGoal = null;
            for (WrappedGoal w : selector.getAvailableGoals()) {
                if (w.getGoal() instanceof ReturnToPylonGoal) {
                    returnGoal = w;
                }
                if (w.getGoal() instanceof MeleeAttackGoal
                        && (meleeGoal == null || w.getPriority() < meleeGoal.getPriority())) {
                    meleeGoal = w;
                }
            }
            helper.assertTrue(returnGoal != null, "ReturnToPylonGoal not registered");
            helper.assertTrue(meleeGoal != null, "vanilla MeleeAttackGoal not found on iron golem");
            helper.assertTrue(returnGoal.getPriority() < meleeGoal.getPriority(),
                    "ReturnToPylonGoal (" + returnGoal.getPriority() + ") must outrank vanilla "
                            + "MeleeAttackGoal (" + meleeGoal.getPriority() + ")");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("reflection failed: " + e.getMessage());
        }
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
