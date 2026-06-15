package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.follow.FollowableVillager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class FollowModeGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void followStart(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
        player.moveTo(villager.position().add(1, 0, 0));

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should be following after interaction");
        helper.assertTrue(player.getMainHandItem().getCount() == 4,
                "One emerald should be consumed, got " + player.getMainHandItem().getCount());
        helper.assertTrue(((FollowableVillager) villager).mercantile$isFollowingSync(),
                "Synced entity data should report following");

        FollowManager.stopFollowing(villager);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void maxCapRejection(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MercantileConfig config = MercantileConfig.get();
        int max = config.maxFollowingVillagers;

        Villager[] villagers = new Villager[max + 1];
        for (int i = 0; i <= max; i++) {
            villagers[i] = helper.spawn(EntityType.VILLAGER, i % 3, 1, i / 3);
        }

        for (int i = 0; i < max; i++) {
            boolean started = FollowManager.startFollowing(villagers[i], player);
            helper.assertTrue(started,
                    "Villager " + i + " should start following (under cap)");
        }

        helper.assertTrue(FollowManager.getFollowerCount(player.getUUID()) == max,
                "Should have " + max + " followers");

        boolean rejected = FollowManager.startFollowing(villagers[max], player);
        helper.assertFalse(rejected,
                "Villager beyond cap should be rejected");
        helper.assertFalse(FollowManager.isFollowing(villagers[max]),
                "Rejected villager should not be in follow state");

        for (int i = 0; i < max; i++) {
            FollowManager.stopFollowing(villagers[i]);
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void distanceRelease(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(villager.position().add(1, 0, 0));

        FollowManager.startFollowing(villager, player);
        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should be following");

        player.moveTo(villager.position().add(50, 0, 0));

        helper.assertTrue(player.distanceToSqr(villager) > 32.0 * 32.0,
                "Player should be beyond release distance");

        villager.tick();

        helper.assertFalse(FollowManager.isFollowing(villager),
                "Villager should be released after distance exceeded");
        helper.assertFalse(((FollowableVillager) villager).mercantile$isFollowingSync(),
                "Synced data should be cleared after release");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void saveReloadClear(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        FollowManager.startFollowing(villager, player);
        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should be following before unload");

        FollowManager.stopFollowing(villager.getUUID());

        helper.assertFalse(FollowManager.isFollowing(villager),
                "Follow state should be cleared after entity unload");
        helper.assertTrue(FollowManager.getFollowerCount(player.getUUID()) == 0,
                "Player should have 0 followers after unload");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveFollow(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer playerA = helper.makeMockServerPlayerInLevel();
        ServerPlayer playerB = helper.makeMockServerPlayerInLevel();

        boolean startedA = FollowManager.startFollowing(villager, playerA);
        helper.assertTrue(startedA, "Player A should start following");

        boolean startedB = FollowManager.startFollowing(villager, playerB);
        helper.assertFalse(startedB,
                "Player B should be denied — villager already follows Player A");
        helper.assertTrue(FollowManager.getFollowTarget(villager).equals(playerA.getUUID()),
                "Follow target should remain Player A");

        FollowManager.stopFollowing(villager);
        playerA.discard();
        playerB.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void scheduleSuppression(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(villager.position().add(1, 0, 0));

        FollowManager.startFollowing(villager, player);

        Vec3 farTarget = villager.position().add(10, 0, 10);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(farTarget, 0.5f, 1));
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "Walk target should be set before tick");

        for (int i = 0; i < 5; i++) {
            villager.tick();
        }

        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "Walk target should be erased after follow tick (non-survival activity)");
        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should still be following after AI step");

        FollowManager.stopFollowing(villager);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void emeraldNotConsumedOnRejection(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer playerA = helper.makeMockServerPlayerInLevel();
        ServerPlayer playerB = helper.makeMockServerPlayerInLevel();
        playerB.setShiftKeyDown(true);
        playerB.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
        playerB.moveTo(villager.position().add(1, 0, 0));

        boolean startedA = FollowManager.startFollowing(villager, playerA);
        helper.assertTrue(startedA, "Player A should start following");

        net.minecraft.world.InteractionResult result =
                villager.interact(playerB, InteractionHand.MAIN_HAND);

        helper.assertTrue(result == net.minecraft.world.InteractionResult.FAIL,
                "Interaction should FAIL when villager already follows another player");
        helper.assertTrue(playerB.getMainHandItem().getCount() == 5,
                "Player B's emerald count should remain 5, got " + playerB.getMainHandItem().getCount());
        helper.assertTrue(FollowManager.getFollowTarget(villager).equals(playerA.getUUID()),
                "Villager should still follow Player A");

        FollowManager.stopFollowing(villager);
        playerA.discard();
        playerB.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stopFollowingClearsSyncedData(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        FollowManager.startFollowing(villager, player);
        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should be following before stop");
        helper.assertTrue(((FollowableVillager) villager).mercantile$isFollowingSync(),
                "Synced data should be true before stop");

        FollowManager.stopFollowing(villager);

        helper.assertFalse(FollowManager.isFollowing(villager),
                "Follow state should be cleared after stopFollowing");
        helper.assertFalse(((FollowableVillager) villager).mercantile$isFollowingSync(),
                "Synced data should be cleared after stopFollowing");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void returnHomeToBed(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos bedPos = helper.absolutePos(new BlockPos(5, 1, 5));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), bedPos));

        FollowManager.startFollowing(villager, player);
        FollowManager.stopFollowing(villager);

        helper.assertTrue(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Villager should be in returning home state");

        helper.onEachTick(() -> {
            if (villager.position().distanceToSqr(bedPos.getBottomCenter()) < 4.0) {
                helper.assertFalse(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                        "Returning home state should be cleared upon arrival");
                player.discard();
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void returnHomeToWorkstationFallback(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos jobPos = helper.absolutePos(new BlockPos(5, 1, 0));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(villager.level().dimension(), jobPos));

        FollowManager.startFollowing(villager, player);
        FollowManager.stopFollowing(villager);

        helper.assertTrue(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Villager should be in returning home state (workstation fallback)");

        helper.onEachTick(() -> {
            if (villager.position().distanceToSqr(jobPos.getBottomCenter()) < 4.0) {
                helper.assertFalse(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                        "Returning home state should be cleared upon arrival at workstation");
                player.discard();
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void noClaimNoReturn(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        FollowManager.startFollowing(villager, player);
        FollowManager.stopFollowing(villager);

        helper.assertFalse(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Villager should NOT be in returning home state if no bed/workstation");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cancelReturnOnHurt(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos bedPos = helper.absolutePos(new BlockPos(5, 1, 5));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), bedPos));

        FollowManager.startFollowing(villager, player);
        FollowManager.stopFollowing(villager);

        helper.assertTrue(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Villager should be in returning home state");

        villager.hurt(villager.damageSources().generic(), 1.0f);

        helper.assertFalse(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Returning home state should be cancelled when hurt");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cancelReturnOnReEngage(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos bedPos = helper.absolutePos(new BlockPos(5, 1, 5));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), bedPos));

        FollowManager.startFollowing(villager, player);
        FollowManager.stopFollowing(villager);

        helper.assertTrue(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Villager should be in returning home state");

        FollowManager.startFollowing(villager, player);

        helper.assertFalse(((FollowableVillager) villager).mercantile$isReturningHomeSync(),
                "Returning home state should be cancelled when follow is re-engaged");
        helper.assertTrue(FollowManager.isFollowing(villager),
                "Villager should be following again");

        FollowManager.stopFollowing(villager);
        player.discard();
        helper.succeed();
    }
}
