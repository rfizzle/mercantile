package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.mixin.ItemEntityAccessor;
import com.rfizzle.mercantile.mixin.VillagerAccessor;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GiftingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void giftingGainsReputation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);

        ItemStack gift = new ItemStack(Items.WHEAT, 1);
        ItemEntity itemEntity = new ItemEntity(helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(), gift);
        ((ItemEntityAccessor) itemEntity).setTarget(player.getUUID());

        ((VillagerAccessor) villager).invokePickUpItem(itemEntity);

        int expectedGain = MercantileConfig.get().reputationGiftGain;
        helper.assertTrue(data.getScore() == expectedGain,
                "Expected score " + expectedGain + " after gifting, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void giftingDailyCap(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);

        int cap = MercantileConfig.get().reputationDailyMaxGiftRep;
        int giftGain = MercantileConfig.get().reputationGiftGain;

        for (int i = 0; i < cap + 1; i++) {
            ItemStack gift = new ItemStack(Items.WHEAT, 1);
            ItemEntity itemEntity = new ItemEntity(helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(), gift);
            ((ItemEntityAccessor) itemEntity).setTarget(player.getUUID());
            ((VillagerAccessor) villager).invokePickUpItem(itemEntity);
        }

        int expectedScore = cap * giftGain;
        helper.assertTrue(data.getScore() == expectedScore,
                "Expected score " + expectedScore + " (hit cap), got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void giftingMismatchedProfession(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.ARMORER, 1));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);

        ItemStack gift = new ItemStack(Items.WHEAT, 1); // Farmer gift, not Armorer
        ItemEntity itemEntity = new ItemEntity(helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(), gift);
        ((ItemEntityAccessor) itemEntity).setTarget(player.getUUID());

        ((VillagerAccessor) villager).invokePickUpItem(itemEntity);

        helper.assertTrue(data.getScore() == 0,
                "Expected score 0 after mismatched gifting, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationDecay(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(-10);
        data.setReputationMigrated(true);

        long startDay = 100L;
        data.setLastDecayDay(startDay);
        data.resetDailyCounters(startDay);

        long nextDay = startDay + 1;
        ReputationManager.rolloverIfNewDay(player, data, nextDay);

        int decayPerDay = MercantileConfig.get().reputationNegativeDecayPerDay;
        int expectedScore = -10 + decayPerDay;
        helper.assertTrue(data.getScore() == expectedScore,
                "Expected score " + expectedScore + " after 1 day decay, got " + data.getScore());

        long manyDaysLater = nextDay + 100;
        ReputationManager.rolloverIfNewDay(player, data, manyDaysLater);
        helper.assertTrue(data.getScore() == 0,
                "Expected score 0 after long decay, got " + data.getScore());

        data.setScore(10);
        data.setLastDecayDay(manyDaysLater);
        ReputationManager.rolloverIfNewDay(player, data, manyDaysLater + 1);
        helper.assertTrue(data.getScore() == 10,
                "Positive reputation should not decay, got " + data.getScore());

        helper.succeed();
    }
}
