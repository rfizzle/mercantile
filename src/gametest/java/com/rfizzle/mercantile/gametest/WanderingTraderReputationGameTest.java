package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.rfizzle.mercantile.data.VillagerPickupHelper;

import java.util.List;

public class WanderingTraderReputationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void neutralPlayerSeesNoExtraOffer(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Ensure reputation is NEUTRAL (0)
        ReputationManager.setScore(player, 0);

        int vanillaOfferCount = trader.getOffers().size();

        // Interact to trigger trade injection
        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(trader.getOffers().size() == vanillaOfferCount,
                "NEUTRAL player should see only vanilla offers (expected " + vanillaOfferCount + ", got " + trader.getOffers().size() + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void trustedPlayerSeesExactlyOneExtraOffer(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Set reputation to TRUSTED (300)
        ReputationManager.setScore(player, 300);

        int vanillaOfferCount = trader.getOffers().size();

        // Interact to trigger trade injection
        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(trader.getOffers().size() == vanillaOfferCount + 1,
                "TRUSTED player should see exactly one extra offer (expected " + (vanillaOfferCount + 1) + ", got " + trader.getOffers().size() + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void offerStabilityAcrossScreenOpens(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ReputationManager.setScore(player, 300);

        // First open
        trader.interact(player, InteractionHand.MAIN_HAND);
        MerchantOffers offers1 = trader.getOffers();
        MerchantOffer extraOffer1 = offers1.get(offers1.size() - 1);

        // Second open
        trader.interact(player, InteractionHand.MAIN_HAND);
        MerchantOffers offers2 = trader.getOffers();
        MerchantOffer extraOffer2 = offers2.get(offers2.size() - 1);

        helper.assertTrue(offers1.size() == offers2.size(), "Offer count should be stable");
        helper.assertTrue(ItemStack.matches(extraOffer1.getResult(), extraOffer2.getResult()),
                "Extra offer should be identical across repeated opens");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void offerStabilityAcrossPickup(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ReputationManager.setScore(player, 300);

        // Roll the offer
        trader.interact(player, InteractionHand.MAIN_HAND);
        MerchantOffers offersBefore = trader.getOffers();
        MerchantOffer extraOfferBefore = offersBefore.get(offersBefore.size() - 1);

        // Pick up
        ItemStack headItem = VillagerPickupHelper.createHeadItem(trader);
        trader.discard();

        // Place
        player.setItemInHand(InteractionHand.MAIN_HAND, headItem);
        BlockPos target = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
        player.gameMode.useItemOn(player, helper.getLevel(), headItem, InteractionHand.MAIN_HAND, hit);

        List<WanderingTrader> found = helper.getLevel().getEntitiesOfClass(WanderingTrader.class, new net.minecraft.world.phys.AABB(target.above()).inflate(2.0));
        if (found.isEmpty()) {
            helper.fail("No WanderingTrader found after re-placement");
        }
        WanderingTrader placed = found.get(0);

        // Check offer on placed trader
        placed.interact(player, InteractionHand.MAIN_HAND);
        MerchantOffers offersAfter = placed.getOffers();
        MerchantOffer extraOfferAfter = offersAfter.get(offersAfter.size() - 1);

        helper.assertTrue(ItemStack.matches(extraOfferBefore.getResult(), extraOfferAfter.getResult()),
                "Extra offer should be preserved across pickup and re-placement");

        placed.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledByConfig(GameTestHelper helper) {
        boolean savedEnabled = MercantileConfig.get().enableWanderingTraderRep;
        try {
            MercantileConfig.get().enableWanderingTraderRep = false;

            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            ReputationManager.setScore(player, 300);

            int vanillaOfferCount = trader.getOffers().size();

            trader.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(trader.getOffers().size() == vanillaOfferCount,
                    "No extra offer should be seen when feature is disabled in config");

            player.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().enableWanderingTraderRep = savedEnabled;
        }
    }
}
