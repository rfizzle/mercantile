package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class ReputationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeGainsReputation(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        villager.setTradingPlayer(player);
        for (int i = 0; i < 5; i++) {
            villager.notifyTrade(offer);
        }

        int expectedGain = 5 * MercantileConfig.get().reputationTradeGain;
        helper.assertTrue(data.getScore() == expectedGain,
                "Expected score " + expectedGain + " after 5 trades, got " + data.getScore());

        helper.assertTrue(data.getTradesWithVillager(villager.getUUID()) == 5,
                "Expected 5 trade stats, got " + data.getTradesWithVillager(villager.getUUID()));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void attackLosesReputation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1.0f);

        int expected = -MercantileConfig.get().reputationAttackLoss;
        helper.assertTrue(data.getScore() == expected,
                "Expected score " + expected + " after attack, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void killLosesReputation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1000.0f);

        int expected = -MercantileConfig.get().reputationKillLoss;
        helper.assertTrue(data.getScore() == expected,
                "Expected score " + expected + " after kill, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void priceModifierCalculation(GameTestHelper helper) {
        int base = 20;
        int honored = ReputationManager.getPriceModifier(100, base);
        int trusted = ReputationManager.getPriceModifier(50, base);
        int liked = ReputationManager.getPriceModifier(1, base);
        int neutral = ReputationManager.getPriceModifier(0, base);
        int distrusted = ReputationManager.getPriceModifier(-25, base);
        int reviled = ReputationManager.getPriceModifier(-100, base);

        helper.assertTrue(honored < 0, "Honored should discount, got " + honored);
        helper.assertTrue(trusted < 0, "Trusted should discount, got " + trusted);
        helper.assertTrue(liked < 0, "Liked should discount, got " + liked);
        helper.assertTrue(neutral == 0, "Neutral should be 0, got " + neutral);
        helper.assertTrue(distrusted > 0, "Distrusted should markup, got " + distrusted);
        helper.assertTrue(reviled == 0, "Reviled returns 0 (trades refused), got " + reviled);

        helper.assertTrue(honored < trusted, "Honored discount > trusted discount");
        helper.assertTrue(trusted < liked, "Trusted discount > liked discount");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reviledIsDetected(GameTestHelper helper) {
        helper.assertTrue(ReputationManager.isReviled(-50), "-50 should be reviled");
        helper.assertTrue(ReputationManager.isReviled(-100), "-100 should be reviled");
        helper.assertFalse(ReputationManager.isReviled(-49), "-49 should not be reviled");
        helper.assertFalse(ReputationManager.isReviled(0), "0 should not be reviled");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveTradesInjectedByScore(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        int baseCount = villager.getOffers().size();

        ExclusiveTradesManager.injectOffers(villager, 100);
        int honoredCount = villager.getOffers().size();

        ExclusiveTradesManager.stripInjectedOffers(villager);
        int afterStripCount = villager.getOffers().size();

        helper.assertTrue(honoredCount >= baseCount,
                "Honored tier should have >= base offers: base=" + baseCount + " honored=" + honoredCount);
        helper.assertTrue(afterStripCount == baseCount,
                "After strip should equal base: base=" + baseCount + " after=" + afterStripCount);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveTradesDoNotAccumulate(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ExclusiveTradesManager.injectOffers(villager, 100);
        int firstCount = villager.getOffers().size();

        ExclusiveTradesManager.stripInjectedOffers(villager);
        ExclusiveTradesManager.injectOffers(villager, 100);
        int secondCount = villager.getOffers().size();

        helper.assertTrue(firstCount == secondCount,
                "Exclusive trades should not accumulate: first=" + firstCount + " second=" + secondCount);
        helper.succeed();
    }

    private Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.overrideOffers(offers);
        return villager;
    }
}
