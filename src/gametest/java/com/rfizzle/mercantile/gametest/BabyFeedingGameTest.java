package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.breeding.BabyFeeding;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BabyFeedingGameTest implements FabricGameTest {

    private static final int START_AGE = -BabyFeeding.FULL_GROWTH_TICKS;

    private static ServerPlayer feedingPlayer(GameTestHelper helper, Villager villager, ItemStack stack) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Mock server players default to creative, where instabuild skips the item shrink;
        // force survival so the food is actually consumed.
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.moveTo(villager.position().add(1, 0, 0));
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void feedingConsumesItemAndReducesGrowthTime(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(START_AGE);
        ServerPlayer player = feedingPlayer(helper, villager, new ItemStack(Items.BREAD, 5));

        villager.interact(player, InteractionHand.MAIN_HAND);

        int expected = BabyFeeding.computeReduction(BabyFeeding.FULL_GROWTH_TICKS, 4,
                MercantileConfig.get().babyFeedPercentPerFeed);
        helper.assertTrue(villager.getAge() == START_AGE + expected,
                "Age should advance by " + expected + " ticks, got " + (villager.getAge() - START_AGE));
        helper.assertTrue(player.getMainHandItem().getCount() == 4,
                "One bread should be consumed, got " + player.getMainHandItem().getCount());
        helper.assertTrue(villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA)
                        .getFedGrowthTicks() == expected,
                "Fed ticks should be recorded on the villager attachment");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void breadReducesMoreThanBeetroot(GameTestHelper helper) {
        Villager fedBread = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        Villager fedBeetroot = helper.spawn(EntityType.VILLAGER, 2, 1, 0);
        fedBread.setAge(START_AGE);
        fedBeetroot.setAge(START_AGE);

        ServerPlayer player = feedingPlayer(helper, fedBread, new ItemStack(Items.BREAD, 1));
        fedBread.interact(player, InteractionHand.MAIN_HAND);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEETROOT, 1));
        player.moveTo(fedBeetroot.position().add(1, 0, 0));
        fedBeetroot.interact(player, InteractionHand.MAIN_HAND);

        int breadGain = fedBread.getAge() - START_AGE;
        int beetrootGain = fedBeetroot.getAge() - START_AGE;
        helper.assertTrue(breadGain > 0 && beetrootGain > 0,
                "Both feeds should reduce growth time (bread " + breadGain + ", beetroot " + beetrootGain + ")");
        helper.assertTrue(breadGain > beetrootGain,
                "Bread (" + breadGain + ") should reduce more than beetroot (" + beetrootGain + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cappedBabyRefusesFoodWithoutConsuming(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(START_AGE);
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setFedGrowthTicks(BabyFeeding.maxTotalReductionTicks(
                MercantileConfig.get().babyFeedMaxReductionPercent));
        villager.setAttached(MercantileAttachments.VILLAGER_DATA, data);

        ServerPlayer player = feedingPlayer(helper, villager, new ItemStack(Items.BREAD, 5));
        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(!result.consumesAction(),
                "Interaction should not consume once the feed cap is reached (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 5,
                "No bread should be consumed at the cap, got " + player.getMainHandItem().getCount());
        helper.assertTrue(villager.getAge() == START_AGE,
                "Growth time should be unchanged at the cap");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void repeatedFeedsStopAtCap(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(START_AGE);
        ServerPlayer player = feedingPlayer(helper, villager, new ItemStack(Items.BREAD, 64));

        int cap = BabyFeeding.maxTotalReductionTicks(MercantileConfig.get().babyFeedMaxReductionPercent);
        for (int i = 0; i < 64; i++) {
            villager.interact(player, InteractionHand.MAIN_HAND);
        }

        int fed = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).getFedGrowthTicks();
        helper.assertTrue(fed == cap,
                "Cumulative fed ticks should stop exactly at the cap " + cap + ", got " + fed);
        helper.assertTrue(villager.getAge() == START_AGE + cap,
                "Total growth acceleration should equal the cap");
        helper.assertTrue(villager.isBaby(), "Villager should still be a baby at a 50% cap");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledConfigLeavesVanillaBehavior(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableBabyFeeding;
        config.enableBabyFeeding = false;
        try {
            Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            villager.setAge(START_AGE);
            ServerPlayer player = feedingPlayer(helper, villager, new ItemStack(Items.BREAD, 5));

            villager.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(villager.getAge() == START_AGE,
                    "Growth time should be unchanged when the feature is disabled");
            player.discard();
        } finally {
            config.enableBabyFeeding = saved;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nonFoodItemIsIgnored(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(START_AGE);
        ServerPlayer player = feedingPlayer(helper, villager, new ItemStack(Items.STONE, 5));

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(villager.getAge() == START_AGE,
                "Non-food items should not affect growth time");
        helper.assertTrue(player.getMainHandItem().getCount() == 5,
                "Non-food items should not be consumed");

        player.discard();
        helper.succeed();
    }
}
