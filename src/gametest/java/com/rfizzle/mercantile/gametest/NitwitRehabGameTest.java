package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.rehab.NitwitRehabManager;
import com.rfizzle.mercantile.trade.EmeraldPayment;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NitwitRehabGameTest implements FabricGameTest {

    // Comfortably past the 60-tick conversion delay.
    private static final int CONVERSION_TIMEOUT_TICKS = 200;

    private static Villager spawnNitwit(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
        return villager;
    }

    private static ServerPlayer rehabPlayer(GameTestHelper helper, Villager villager, int emeralds) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Mock server players default to creative, where instabuild skips the cost;
        // force survival so the apple and emeralds are actually consumed.
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        if (emeralds > 0) {
            player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
        }
        player.moveTo(villager.position().add(1, 0, 0));
        return player;
    }

    private static void setScore(ServerPlayer player, int score) {
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(score);
        player.setAttached(MercantileAttachments.PLAYER_DATA, data);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = CONVERSION_TIMEOUT_TICKS)
    public void trustedPlayerConvertsAdultNitwit(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player = rehabPlayer(helper, villager, cost + 4);
        setScore(player, ReputationTier.TRUSTED.minScore());

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "A Trusted player's rehab attempt should consume the interaction (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 1,
                "One golden apple should be consumed, got " + player.getMainHandItem().getCount());
        helper.assertTrue(EmeraldPayment.count(player) == 4,
                "The emerald fee should be deducted, " + EmeraldPayment.count(player) + " emeralds left");
        helper.assertTrue(NitwitRehabManager.isPending(villager.getUUID()),
                "The conversion should be pending after payment");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                    "The nitwit should convert to an unemployed villager after the delay");
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = CONVERSION_TIMEOUT_TICKS)
    public void secondAppleWhilePendingIsNotConsumed(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player = rehabPlayer(helper, villager, cost * 2);
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);
        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getMainHandItem().getCount() == 1,
                "Only one golden apple should be consumed while a conversion is pending");
        helper.assertTrue(EmeraldPayment.count(player) == cost,
                "Only one emerald fee should be deducted while a conversion is pending");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                    "The pending conversion should still land");
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = CONVERSION_TIMEOUT_TICKS)
    public void creativePlayerPaysNothing(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player = rehabPlayer(helper, villager, cost);
        player.getAbilities().instabuild = true;
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getMainHandItem().getCount() == 2,
                "Creative players should keep the golden apple");
        helper.assertTrue(EmeraldPayment.count(player) == cost,
                "Creative players should keep their emeralds");
        helper.assertTrue(NitwitRehabManager.isPending(villager.getUUID()),
                "The conversion should still be scheduled for creative players");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                    "The conversion should land for creative players");
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void emeraldsSplitAcrossInventoryAndOffhandAreCounted(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        // Split the fee so neither slot alone can cover it: cost-1 in the inventory, 2 offhand.
        ServerPlayer player = rehabPlayer(helper, villager, cost - 1);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 2));
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(NitwitRehabManager.isPending(villager.getUUID()),
                "Emeralds split across inventory and offhand should cover the fee");
        helper.assertTrue(EmeraldPayment.count(player) == 1,
                "Exactly the fee should be deducted across both slots, "
                        + EmeraldPayment.count(player) + " emeralds left");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void zeroCostRequiresOnlyTheApple(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        int saved = config.nitwitRehabEmeraldCost;
        config.nitwitRehabEmeraldCost = 0;
        try {
            Villager villager = spawnNitwit(helper);
            ServerPlayer player = rehabPlayer(helper, villager, 0);
            setScore(player, ReputationTier.TRUSTED.minScore());

            villager.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(player.getMainHandItem().getCount() == 1,
                    "The golden apple should be consumed at zero emerald cost");
            helper.assertTrue(NitwitRehabManager.isPending(villager.getUUID()),
                    "A zero emerald cost should schedule the conversion with no emeralds held");
            player.discard();
        } finally {
            config.nitwitRehabEmeraldCost = saved;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void belowTrustedPlayerIsDeniedWithoutCost(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player = rehabPlayer(helper, villager, cost);
        setScore(player, ReputationTier.TRUSTED.minScore() - 1);

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(!result.consumesAction(),
                "A below-Trusted attempt should not consume the interaction (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 2,
                "No golden apple should be consumed on denial");
        helper.assertTrue(EmeraldPayment.count(player) == cost,
                "No emeralds should be deducted on denial");
        helper.assertTrue(!NitwitRehabManager.isPending(villager.getUUID()),
                "No conversion should be scheduled on denial");
        helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                "The villager should remain a nitwit");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void babyNitwitIsDeniedWithoutCost(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        villager.setAge(-24_000);
        ServerPlayer player = rehabPlayer(helper, villager, cost);
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getMainHandItem().getCount() == 2,
                "No golden apple should be consumed for a baby nitwit");
        helper.assertTrue(EmeraldPayment.count(player) == cost,
                "No emeralds should be deducted for a baby nitwit");
        helper.assertTrue(!NitwitRehabManager.isPending(villager.getUUID()),
                "No conversion should be scheduled for a baby nitwit");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void insufficientEmeraldsAreDeniedWithoutCost(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player = rehabPlayer(helper, villager, cost - 1);
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getMainHandItem().getCount() == 2,
                "No golden apple should be consumed when the fee cannot be covered");
        helper.assertTrue(EmeraldPayment.count(player) == cost - 1,
                "No emeralds should be deducted when the fee cannot be covered");
        helper.assertTrue(!NitwitRehabManager.isPending(villager.getUUID()),
                "No conversion should be scheduled when the fee cannot be covered");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = CONVERSION_TIMEOUT_TICKS)
    public void reputationDisabledSkipsTierGate(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableReputation;
        config.enableReputation = false;
        Villager villager = spawnNitwit(helper);
        ServerPlayer player;
        try {
            int cost = config.nitwitRehabEmeraldCost;
            player = rehabPlayer(helper, villager, cost);
            setScore(player, 0);

            villager.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(player.getMainHandItem().getCount() == 1,
                    "With reputation disabled the tier gate is skipped and the apple is consumed");
            helper.assertTrue(NitwitRehabManager.isPending(villager.getUUID()),
                    "With reputation disabled the conversion should be scheduled");
        } finally {
            config.enableReputation = saved;
        }

        ServerPlayer finalPlayer = player;
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                    "The conversion should land even though reputation is disabled");
            finalPlayer.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledFeatureLeavesVanillaBehavior(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableNitwitRehab;
        config.enableNitwitRehab = false;
        try {
            Villager villager = spawnNitwit(helper);
            ServerPlayer player = rehabPlayer(helper, villager, config.nitwitRehabEmeraldCost);
            setScore(player, ReputationTier.TRUSTED.minScore());

            villager.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(player.getMainHandItem().getCount() == 2,
                    "No golden apple should be consumed when the feature is disabled");
            helper.assertTrue(!NitwitRehabManager.isPending(villager.getUUID()),
                    "No conversion should be scheduled when the feature is disabled");
            player.discard();
        } finally {
            config.enableNitwitRehab = saved;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = CONVERSION_TIMEOUT_TICKS)
    public void customNameSurvivesConversion(GameTestHelper helper) {
        int cost = MercantileConfig.get().nitwitRehabEmeraldCost;
        Villager villager = spawnNitwit(helper);
        villager.setCustomName(Component.literal("Bertram"));
        ServerPlayer player = rehabPlayer(helper, villager, cost);
        setScore(player, ReputationTier.TRUSTED.minScore());

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                    "The nitwit should convert after the delay");
            helper.assertTrue(villager.getCustomName() != null
                            && "Bertram".equals(villager.getCustomName().getString()),
                    "The custom name should survive the conversion");
            player.discard();
        });
    }
}
