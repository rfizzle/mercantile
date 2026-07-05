package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.ContractService;
import com.rfizzle.mercantile.contract.DeliveryContract;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Delivery-contract gametests (issue #86). Contracts are seeded directly onto the villager's
 * attachment (rather than waiting on the random sweep) so every test is deterministic; the
 * accept/deliver flow then drives the real {@code mobInteract} mixin end to end.
 */
public class ContractGameTest implements FabricGameTest {

    private static final BlockPos VILLAGER_POS = new BlockPos(1, 1, 1);

    /** Employed adult on a stone floor (EMPTY_STRUCTURE is pure air — see the work-order tests). */
    private static Villager spawnFarmer(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER_POS);
        villager.setOnGround(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        return villager;
    }

    /** Survival mock player standing next to the villager with the given main-hand stack. */
    private static ServerPlayer interactingPlayer(GameTestHelper helper, Villager villager, ItemStack held) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.moveTo(villager.position().add(1, 0, 0));
        return player;
    }

    /** Seeds a wheat offer (un-accepted) onto the villager; expiry well in the future. */
    private static DeliveryContract seedOffer(GameTestHelper helper, Villager villager, int count, int payment) {
        DeliveryContract offer = new DeliveryContract(UUID.randomUUID(),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT), count, payment,
                false, helper.getLevel().getGameTime() + 12_000L);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(offer);
        return offer;
    }

    /** Seeds an accepted contract on the villager and returns the matching written item. */
    private static ItemStack seedAcceptedContract(GameTestHelper helper, Villager villager,
                                                  int count, int payment, long expiry) {
        DeliveryContract accepted = new DeliveryContract(UUID.randomUUID(),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT), count, payment, true, expiry);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(accepted);
        return ContractService.createContractItem(villager, accepted);
    }

    private static DeliveryContract contractOf(Villager villager) {
        return villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).getContract();
    }

    private static int countOf(ServerPlayer player, Item item) {
        return ContractService.countItem(player, item);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void paperAcceptsOfferAndWritesContract(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        DeliveryContract offer = seedOffer(helper, villager, 8, 3);
        ServerPlayer player = interactingPlayer(helper, villager, new ItemStack(Items.PAPER, 2));
        player.setShiftKeyDown(true);

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "Accepting an offer should consume the interaction (got " + result + ")");
        helper.assertTrue(countOf(player, Items.PAPER) == 1,
                "Exactly one paper must be consumed, " + countOf(player, Items.PAPER) + " left");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 1,
                "The player should receive one written contract");

        DeliveryContract stored = contractOf(villager);
        helper.assertTrue(stored != null && stored.accepted(),
                "The villager's contract should now be accepted");
        helper.assertTrue(stored.id().equals(offer.id()),
                "Accepting must keep the offer's contract id");
        long expectedDeadline = helper.getLevel().getGameTime()
                + (long) MercantileConfig.get().contractDeadlineDays * 24_000L;
        helper.assertTrue(Math.abs(stored.expiryGameTime() - expectedDeadline) <= 2,
                "The deadline should be contractDeadlineDays out; got " + stored.expiryGameTime());

        // The written item must match by id, so delivery can verify it later.
        ItemStack written = findContractItem(player);
        helper.assertTrue(ContractService.readContractId(written).map(offer.id()::equals).orElse(false),
                "The written contract item must carry the contract id");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void paperWithoutOfferFallsThroughToVanilla(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        ServerPlayer player = interactingPlayer(helper, villager, new ItemStack(Items.PAPER, 2));
        player.setShiftKeyDown(true);

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(countOf(player, Items.PAPER) == 2,
                "No paper may be consumed when the villager has no offer");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 0,
                "No contract may be written when the villager has no offer");
        helper.assertTrue(contractOf(villager) == null,
                "The villager must not gain a contract from a plain paper interaction");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void paperWithoutSneakTradesInsteadOfSigning(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        seedOffer(helper, villager, 8, 3);
        ServerPlayer player = interactingPlayer(helper, villager, new ItemStack(Items.PAPER, 2));
        player.setShiftKeyDown(false);

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(countOf(player, Items.PAPER) == 2,
                "An un-sneaked paper click must not consume paper");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 0,
                "An un-sneaked paper click must not sign the contract");
        DeliveryContract stored = contractOf(villager);
        helper.assertTrue(stored != null && !stored.accepted(),
                "The offer must remain open for a later sneak-click");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deliveryPaysEmeraldsAndCapBypassingRep(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        Villager villager = spawnFarmer(helper);
        long future = helper.getLevel().getGameTime() + 24_000L;
        ItemStack written = seedAcceptedContract(helper, villager, 8, 3, future);

        ServerPlayer player = interactingPlayer(helper, villager, written);
        player.getInventory().add(new ItemStack(Items.WHEAT, 10));
        // Max out the daily cap first to prove the contract reward bypasses it.
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        long day = helper.getLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(day);
        while (data.getDailyReputationEarned() < config.reputationDailyCap) {
            data.addDailyGiftRep(1);
        }
        int scoreBefore = data.getScore();

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "A completed delivery should consume the interaction (got " + result + ")");
        helper.assertTrue(countOf(player, Items.WHEAT) == 2,
                "The requested 8 wheat must be consumed, " + countOf(player, Items.WHEAT) + " left");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 0,
                "The written contract must be consumed on completion");
        helper.assertTrue(countOf(player, Items.EMERALD) == 3,
                "The payment should land in the inventory, got " + countOf(player, Items.EMERALD));
        helper.assertTrue(data.getScore() == scoreBefore + config.contractRepGain,
                "Contract rep must bypass the maxed daily cap; got " + data.getScore());
        helper.assertTrue(contractOf(villager) == null,
                "The villager's contract slot should clear on completion");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deliveryRepStopsAtDailyAwardCount(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        Villager villager = spawnFarmer(helper);
        long future = helper.getLevel().getGameTime() + 24_000L;
        ItemStack written = seedAcceptedContract(helper, villager, 8, 3, future);

        ServerPlayer player = interactingPlayer(helper, villager, written);
        player.getInventory().add(new ItemStack(Items.WHEAT, 10));
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.resetDailyCounters(helper.getLevel().getGameTime() / 24_000L);
        // Exhaust the day's contract-rep awards so this delivery pays emeralds but no rep.
        for (int i = 0; i < config.contractRepPerDay; i++) {
            data.incrementDailyContractRepAwards();
        }
        int scoreBefore = data.getScore();

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "The delivery itself should still complete (got " + result + ")");
        helper.assertTrue(countOf(player, Items.EMERALD) == 3,
                "Emeralds are always paid, got " + countOf(player, Items.EMERALD));
        helper.assertTrue(data.getScore() == scoreBefore,
                "No rep may be granted past the daily contract award count; got " + data.getScore());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wrongVillagerRefusesDelivery(GameTestHelper helper) {
        Villager payee = spawnFarmer(helper);
        long future = helper.getLevel().getGameTime() + 24_000L;
        ItemStack written = seedAcceptedContract(helper, payee, 8, 3, future);

        Villager stranger = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        stranger.setOnGround(true);
        stranger.setVillagerData(stranger.getVillagerData().setProfession(VillagerProfession.FARMER));

        ServerPlayer player = interactingPlayer(helper, stranger, written);
        player.getInventory().add(new ItemStack(Items.WHEAT, 10));

        var result = stranger.interact(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction() && countOf(player, Items.EMERALD) > 0,
                "The wrong villager must not settle the contract");
        helper.assertTrue(countOf(player, Items.WHEAT) == 10,
                "No items may be taken by the wrong villager");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 1,
                "The contract item must be kept");
        helper.assertTrue(contractOf(payee) != null,
                "The payee's contract must remain outstanding");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void expiredContractRefusedAndForgotten(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        // Expired the moment it is checked (game time is always past tick 1).
        ItemStack written = seedAcceptedContract(helper, villager, 8, 3, 1L);

        ServerPlayer player = interactingPlayer(helper, villager, written);
        player.getInventory().add(new ItemStack(Items.WHEAT, 10));

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(countOf(player, Items.WHEAT) == 10,
                "No items may be taken for an expired contract");
        helper.assertTrue(countOf(player, Items.EMERALD) == 0,
                "No payment may be made for an expired contract");
        helper.assertTrue(contractOf(villager) == null,
                "The villager should forget a lapsed contract on interaction");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void missingItemsRefusedWithoutConsumingAnything(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        long future = helper.getLevel().getGameTime() + 24_000L;
        ItemStack written = seedAcceptedContract(helper, villager, 8, 3, future);

        ServerPlayer player = interactingPlayer(helper, villager, written);
        player.getInventory().add(new ItemStack(Items.WHEAT, 5)); // 3 short

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction(),
                "A short delivery must be refused (got " + result + ")");
        helper.assertTrue(countOf(player, Items.WHEAT) == 5,
                "A partial stock must not be taken");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 1,
                "The contract item must be kept");
        helper.assertTrue(contractOf(villager) != null && contractOf(villager).accepted(),
                "The contract must remain outstanding");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void contractSurvivesEntityNbtRoundTrip(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        DeliveryContract offer = seedOffer(helper, villager, 8, 3);

        // The pickup/place cycle transports the villager through saveWithoutId/load with the
        // UUID stripped (VillagerPickupHelper.createHeadItem) — the same round trip modeled here.
        CompoundTag nbt = new CompoundTag();
        villager.saveWithoutId(nbt);
        nbt.remove("UUID");
        Villager reloaded = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        reloaded.load(nbt);

        DeliveryContract restored = contractOf(reloaded);
        helper.assertTrue(restored != null, "The contract must survive the NBT round trip");
        helper.assertTrue(restored.id().equals(offer.id()),
                "The restored contract must keep its id (item↔villager matching depends on it)");
        helper.assertTrue(restored.count() == offer.count() && restored.payment() == offer.payment(),
                "The restored contract must keep its terms");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void featureInertWhenDisabled(GameTestHelper helper) {
        Villager villager = spawnFarmer(helper);
        seedOffer(helper, villager, 8, 3);
        ServerPlayer player = interactingPlayer(helper, villager, new ItemStack(Items.PAPER, 2));
        player.setShiftKeyDown(true);

        boolean saved = MercantileConfig.get().enableContracts;
        try {
            MercantileConfig.get().enableContracts = false;
            villager.interact(player, InteractionHand.MAIN_HAND);
        } finally {
            MercantileConfig.get().enableContracts = saved;
        }

        helper.assertTrue(countOf(player, Items.PAPER) == 2,
                "No paper may be consumed while contracts are disabled");
        helper.assertTrue(countOf(player, MercantileRegistry.DELIVERY_CONTRACT.asItem()) == 0,
                "No contract may be written while contracts are disabled");
        DeliveryContract stored = contractOf(villager);
        helper.assertTrue(stored != null && !stored.accepted(),
                "The seeded offer must stay un-accepted while contracts are disabled");

        player.discard();
        helper.succeed();
    }

    private static ItemStack findContractItem(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(MercantileRegistry.DELIVERY_CONTRACT)) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(MercantileRegistry.DELIVERY_CONTRACT)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
