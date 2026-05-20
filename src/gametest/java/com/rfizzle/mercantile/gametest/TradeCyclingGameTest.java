package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerData;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import com.rfizzle.mercantile.trade.TradeCycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class TradeCyclingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void cyclePreservesLockedTrades(GameTestHelper helper) {
        MerchantOffer lockedOffer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer unlocked1 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD, 1), 16, 1, 0.0f);
        MerchantOffer unlocked2 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 3), new ItemStack(Items.CARROT, 1), 16, 1, 0.0f);

        Villager villager = spawnTraderWithOffers(helper, lockedOffer, unlocked1, unlocked2);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));

        villager.setTradingPlayer(player);
        villager.notifyTrade(lockedOffer);

        VillagerData vd = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        String lockedHash = OfferIdentityHash.compute(lockedOffer);
        helper.assertTrue(vd.isTradeLocked(lockedHash),
                "Traded offer should be locked");

        boolean cycled = TradeCycleManager.cycle(player, villager);
        helper.assertTrue(cycled, "Cycle should succeed");

        boolean foundLocked = false;
        for (MerchantOffer offer : villager.getOffers()) {
            if (OfferIdentityHash.compute(offer).equals(lockedHash)) {
                foundLocked = true;
                break;
            }
        }
        helper.assertTrue(foundLocked,
                "Locked trade should still be present after cycle");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cycleDeductsEmeralds(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTraderWithOffers(helper, offer);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        int cost = MercantileConfig.get().tradeCycleEmeraldCost;
        player.getInventory().add(new ItemStack(Items.EMERALD, cost + 5));
        villager.setTradingPlayer(player);

        boolean cycled = TradeCycleManager.cycle(player, villager);
        helper.assertTrue(cycled, "Cycle should succeed");

        int remaining = countEmeralds(player);
        helper.assertTrue(remaining == 5,
                "Expected 5 emeralds remaining, got " + remaining);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void canCycleFalseWhenAllLocked(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTraderWithOffers(helper, offer);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));

        VillagerData vd = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        vd.addLockedTrade(OfferIdentityHash.compute(offer));

        helper.assertFalse(TradeCycleManager.canCycle(player, villager),
                "canCycle should be false when all trades are locked");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void canCycleFalseWhenNoEmeralds(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTraderWithOffers(helper, offer);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.assertFalse(TradeCycleManager.canCycle(player, villager),
                "canCycle should be false when player has no emeralds");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void canCycleFalseWhenDisabled(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTraderWithOffers(helper, offer);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));

        MercantileConfig config = MercantileConfig.get();
        boolean original = config.enableTradeCycling;
        try {
            config.enableTradeCycling = false;
            helper.assertFalse(TradeCycleManager.canCycle(player, villager),
                    "canCycle should be false when feature disabled");
        } finally {
            config.enableTradeCycling = original;
        }
        helper.succeed();
    }

    private Villager spawnTraderWithOffers(GameTestHelper helper, MerchantOffer... offers) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new net.minecraft.world.entity.npc.VillagerData(
                VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers merchantOffers = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            merchantOffers.add(offer);
        }
        villager.overrideOffers(merchantOffers);
        return villager;
    }

    private int countEmeralds(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) count += stack.getCount();
        }
        return count;
    }
}
