package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;

import java.util.Optional;

public class BulkTradingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void bulkExecutionWithSufficientItems(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);
        player.getInventory().add(new ItemStack(Items.EMERALD, 10));

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.tryMoveItems(0);
        menu.quickMoveStack(player, 2);

        helper.assertTrue(offer.getUses() == 10,
                "Expected 10 trades, got " + offer.getUses());
        helper.assertTrue(countItems(player, Items.APPLE) == 10,
                "Expected 10 apples, got " + countItems(player, Items.APPLE));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stockLimitStops(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 5, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);
        player.getInventory().add(new ItemStack(Items.EMERALD, 20));

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.tryMoveItems(0);
        menu.quickMoveStack(player, 2);

        helper.assertTrue(offer.getUses() == 5,
                "Expected 5 trades (stock limit), got " + offer.getUses());
        helper.assertTrue(offer.isOutOfStock(), "Offer should be out of stock");
        helper.assertTrue(countItems(player, Items.APPLE) == 5,
                "Expected 5 apples, got " + countItems(player, Items.APPLE));

        int remaining = countItems(player, Items.EMERALD) + countSlotItem(menu, 0, Items.EMERALD);
        helper.assertTrue(remaining == 15,
                "Expected 15 emeralds remaining, got " + remaining);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sixtyFourTradeCap(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 100, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));
        player.getInventory().add(new ItemStack(Items.EMERALD, 36));

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.tryMoveItems(0);
        menu.quickMoveStack(player, 2);

        helper.assertTrue(offer.getUses() == 64,
                "Expected 64 trades (cap), got " + offer.getUses());
        helper.assertTrue(countItems(player, Items.APPLE) == 64,
                "Expected 64 apples, got " + countItems(player, Items.APPLE));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void demandCounterAccuracy(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 12, 1, 0.05f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);
        player.getInventory().add(new ItemStack(Items.EMERALD, 10));

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.tryMoveItems(0);
        menu.quickMoveStack(player, 2);

        helper.assertTrue(offer.getUses() == 10,
                "Expected 10 uses, got " + offer.getUses());

        offer.updateDemand();
        // demand = initial(0) + uses(10) - (maxUses(12) - uses(10)) = 8
        helper.assertTrue(offer.getDemand() == 8,
                "Expected demand 8 after restock, got " + offer.getDemand());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void inventoryFullStopsWithNoItemLoss(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.DIAMOND_PICKAXE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);

        for (int i = 0; i < 33; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        player.getInventory().setItem(33, new ItemStack(Items.EMERALD, 10));
        // Slots 34-35 empty; tryMoveItems frees slot 33 → 3 free slots total

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.tryMoveItems(0);
        menu.quickMoveStack(player, 2);

        int pickaxes = countItems(player, Items.DIAMOND_PICKAXE);
        helper.assertTrue(pickaxes == 3,
                "Expected 3 pickaxes (inventory full), got " + pickaxes);

        int emeraldsInPayment = countSlotItem(menu, 0, Items.EMERALD);
        int emeraldsInInventory = countItems(player, Items.EMERALD);
        int total = pickaxes + emeraldsInPayment + emeraldsInInventory;
        helper.assertTrue(total == 10,
                "No item loss: expected 10 accounted items, got " + total);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dualCostBulkExecution(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 2), Optional.of(new ItemCost(Items.PAPER, 1)),
                new ItemStack(Items.BOOK, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);
        // Enough emeralds for many trades but paper only for 8 — paper runs out first.
        // Seed the payment slots with exactly the cost and keep the rest in the inventory:
        // vanilla tryMoveItems would dump full stacks into the payment slots, leaving a large
        // legitimate residue (returned to the inventory on close) that masks the B-047 refill-cap
        // invariant this test checks.
        player.getInventory().add(new ItemStack(Items.EMERALD, 62));
        player.getInventory().add(new ItemStack(Items.PAPER, 7));

        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.getSlot(0).set(new ItemStack(Items.EMERALD, 2));
        menu.getSlot(1).set(new ItemStack(Items.PAPER, 1));
        menu.quickMoveStack(player, 2);

        helper.assertTrue(offer.getUses() == 8,
                "Expected 8 trades (paper-limited), got " + offer.getUses());
        helper.assertTrue(countItems(player, Items.BOOK) == 8,
                "Expected 8 books, got " + countItems(player, Items.BOOK));

        int emeraldsInPayment = countSlotItem(menu, 0, Items.EMERALD);
        int emeraldsInInventory = countItems(player, Items.EMERALD);
        int emeraldsTotal = emeraldsInPayment + emeraldsInInventory;
        helper.assertTrue(emeraldsTotal == 64 - (8 * 2),
                "Expected " + (64 - 16) + " emeralds remaining, got " + emeraldsTotal);

        int paperInPayment = countSlotItem(menu, 1, Items.PAPER);
        int paperInInventory = countItems(player, Items.PAPER);
        helper.assertTrue(paperInPayment + paperInInventory == 0,
                "Expected 0 paper remaining, got " + (paperInPayment + paperInInventory));

        // B-047: payment slots must hold at most cost.count() residue after the loop ends.
        helper.assertTrue(emeraldsInPayment <= 2,
                "Payment slot 0 over-filled (B-047): " + emeraldsInPayment + " > 2");
        helper.assertTrue(paperInPayment <= 1,
                "Payment slot 1 over-filled (B-047): " + paperInPayment + " > 1");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bulkReputationHonorsTradeCaps(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedRep = config.enableReputation;
        boolean savedBulk = config.enableBulkTrading;
        try {
            config.enableReputation = true;
            config.enableBulkTrading = true;

            MerchantOffer offer = new MerchantOffer(
                    new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
            Villager villager = spawnTrader(helper, offer);

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(0);

            villager.setTradingPlayer(player);
            player.getInventory().add(new ItemStack(Items.EMERALD, 10));

            MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
            menu.setSelectionHint(0);
            menu.tryMoveItems(0);
            menu.quickMoveStack(player, 2);

            helper.assertTrue(offer.getUses() == 10,
                    "Expected 10 trades, got " + offer.getUses());

            // Bulk trading accrues reputation through the same per-trade path as
            // single trades: one gain pulse every reputationTradesPerGain trades,
            // bounded by the per-source sub-cap and the daily total cap. It must not
            // award a flat gain per operation or otherwise bypass the daily caps.
            int pulses = 10 / config.reputationTradesPerGain;
            int expected = Math.min(pulses * config.reputationTradeGain,
                    Math.min(config.reputationDailyMaxTradeRep, config.reputationDailyCap));
            helper.assertTrue(data.getScore() == expected,
                    "Bulk rep should honor the trade caps (" + expected + "), got " + data.getScore());

            // Counter is per-trade and not capped by BulkTradeContext.
            helper.assertTrue(data.getTradesWithVillager(villager.getUUID()) == 10,
                    "Trade counter should record all 10 trades, got "
                            + data.getTradesWithVillager(villager.getUUID()));

            player.discard();
            helper.succeed();
        } finally {
            config.enableReputation = savedRep;
            config.enableBulkTrading = savedBulk;
        }
    }

    private Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        // Villager.setOffers persists; AbstractVillager.overrideOffers is a vanilla no-op.
        villager.setOffers(offers);
        return villager;
    }

    private int countItems(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private int countSlotItem(MerchantMenu menu, int slotIndex, Item item) {
        ItemStack stack = menu.getSlot(slotIndex).getItem();
        return stack.is(item) ? stack.getCount() : 0;
    }
}