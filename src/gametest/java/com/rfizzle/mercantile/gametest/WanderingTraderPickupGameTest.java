package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class WanderingTraderPickupGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void leashedLlamaDropsLeashOnPickup(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        TraderLlama llama = helper.spawn(EntityType.TRADER_LLAMA, 1, 1, 0);

        llama.setLeashedTo(trader, true);
        helper.assertTrue(llama.getLeashHolder() == trader,
                "Llama should be leashed to trader before pickup");

        int leadsBefore = mercantile$countLeadItems(helper);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(trader.position().add(1, 0, 0));

        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(trader.isRemoved(),
                "Trader should be removed after pickup");
        helper.assertTrue(player.getMainHandItem().is(Items.PLAYER_HEAD),
                "Player should hold trader head");
        helper.assertFalse(llama.isRemoved(),
                "Llama should remain in the world");
        helper.assertTrue(llama.getLeashHolder() == null,
                "Llama leash should be dropped after trader pickup");

        int leadsAfter = mercantile$countLeadItems(helper);
        helper.assertTrue(leadsAfter > leadsBefore,
                "At least one lead item should be dropped (was " + leadsBefore + ", now " + leadsAfter + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void multipleLeashedLlamasAllDropLeashes(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        TraderLlama llama1 = helper.spawn(EntityType.TRADER_LLAMA, 1, 1, 0);
        TraderLlama llama2 = helper.spawn(EntityType.TRADER_LLAMA, 0, 1, 1);
        TraderLlama llama3 = helper.spawn(EntityType.TRADER_LLAMA, 1, 1, 1);

        llama1.setLeashedTo(trader, true);
        llama2.setLeashedTo(trader, true);
        llama3.setLeashedTo(trader, true);

        int leadsBefore = mercantile$countLeadItems(helper);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(trader.position().add(2, 0, 0));

        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(trader.isRemoved(), "Trader should be removed");
        helper.assertFalse(llama1.isRemoved(), "Llama1 should remain");
        helper.assertFalse(llama2.isRemoved(), "Llama2 should remain");
        helper.assertFalse(llama3.isRemoved(), "Llama3 should remain");
        helper.assertTrue(llama1.getLeashHolder() == null, "Llama1 should be unleashed");
        helper.assertTrue(llama2.getLeashHolder() == null, "Llama2 should be unleashed");
        helper.assertTrue(llama3.getLeashHolder() == null, "Llama3 should be unleashed");

        int leadsAfter = mercantile$countLeadItems(helper);
        helper.assertTrue(leadsAfter - leadsBefore >= 3,
                "Expected at least 3 leads dropped; got " + (leadsAfter - leadsBefore));

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nonLeashedLlamaIgnored(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        TraderLlama llama = helper.spawn(EntityType.TRADER_LLAMA, 1, 1, 0);

        helper.assertTrue(llama.getLeashHolder() == null,
                "Precondition: llama starts unleashed");

        int leadsBefore = mercantile$countLeadItems(helper);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(trader.position().add(1, 0, 0));

        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(trader.isRemoved(), "Trader should be removed");
        helper.assertFalse(llama.isRemoved(), "Unleashed llama should remain");
        helper.assertTrue(llama.getLeashHolder() == null, "Unleashed llama should stay unleashed");

        int leadsAfter = mercantile$countLeadItems(helper);
        helper.assertTrue(leadsAfter == leadsBefore,
                "No leads should be dropped for an unleashed llama (delta=" + (leadsAfter - leadsBefore) + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void llamaLeashedToAnotherEntityIsIgnored(GameTestHelper helper) {
        WanderingTrader pickedUpTrader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        WanderingTrader otherTrader = helper.spawn(EntityType.WANDERING_TRADER, 3, 1, 0);
        TraderLlama llama = helper.spawn(EntityType.TRADER_LLAMA, 2, 1, 0);

        llama.setLeashedTo(otherTrader, true);
        helper.assertTrue(llama.getLeashHolder() == otherTrader,
                "Precondition: llama leashed to the other trader");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(pickedUpTrader.position().add(1, 0, 0));

        pickedUpTrader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(pickedUpTrader.isRemoved(),
                "Picked-up trader should be removed");
        helper.assertFalse(otherTrader.isRemoved(),
                "Other trader should remain in the world");
        helper.assertFalse(llama.isRemoved(),
                "Llama should remain");
        helper.assertTrue(llama.getLeashHolder() == otherTrader,
                "Llama leash to the other trader must be preserved");

        player.discard();
        otherTrader.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nbtRoundTripPreservesOffersUsesDespawnAndName(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        trader.setCustomName(Component.literal("Testname"));
        trader.setDespawnDelay(7777);

        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 3),
                new ItemStack(Items.WHEAT, 8),
                16, 1, 0.05f);
        offer.increaseUses();
        offer.increaseUses();
        trader.getOffers().add(offer);

        int snapshotDespawn = trader.getDespawnDelay();
        int origOfferCount = trader.getOffers().size();

        ItemStack headItem = VillagerPickupHelper.createHeadItem(trader);

        helper.assertTrue(headItem.is(Items.PLAYER_HEAD), "Item should be a player head");
        helper.assertTrue(headItem.has(DataComponents.PROFILE), "Item should have a profile");
        helper.assertTrue(headItem.has(DataComponents.CUSTOM_DATA), "Item should have custom data");
        helper.assertTrue(headItem.has(DataComponents.CUSTOM_NAME), "Item should have a display name");
        helper.assertTrue(headItem.has(DataComponents.LORE), "Item should have lore");

        CustomData customData = headItem.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = customData.copyTag();
        helper.assertTrue(nbt.getInt("MercantileDataVersion") == 1, "Data version should be 1");
        helper.assertFalse(nbt.contains("UUID"),
                "Stored NBT should not include UUID to avoid collisions on placement");
        helper.assertTrue(nbt.getString("id").equals("minecraft:wandering_trader"),
                "Stored entity id should be wandering_trader");
        helper.assertTrue(nbt.contains("DespawnDelay"),
                "Stored NBT should include DespawnDelay");
        helper.assertTrue(nbt.getInt("DespawnDelay") == snapshotDespawn,
                "Stored DespawnDelay should match snapshot (expected " + snapshotDespawn
                        + ", was " + nbt.getInt("DespawnDelay") + ")");

        WanderingTrader restored = EntityType.WANDERING_TRADER.create(helper.getLevel());
        helper.assertTrue(restored != null, "Restored trader should be created");
        restored.load(nbt);

        helper.assertTrue(restored.getOffers().size() == origOfferCount,
                "Offer count should survive round-trip");
        boolean hasWheatTrade = restored.getOffers().stream()
                .anyMatch(o -> o.getResult().is(Items.WHEAT));
        helper.assertTrue(hasWheatTrade, "Custom wheat trade should survive round-trip");
        MerchantOffer restoredOffer = restored.getOffers().stream()
                .filter(o -> o.getResult().is(Items.WHEAT))
                .findFirst().orElse(null);
        helper.assertTrue(restoredOffer != null, "Restored offer must be present");
        helper.assertTrue(restoredOffer.getUses() == 2,
                "Offer uses count should survive round-trip (expected 2, was " + restoredOffer.getUses() + ")");
        helper.assertTrue(restored.getDespawnDelay() == snapshotDespawn,
                "Despawn delay should survive round-trip (expected " + snapshotDespawn
                        + ", was " + restored.getDespawnDelay() + ")");
        helper.assertTrue(restored.getCustomName() != null
                        && "Testname".equals(restored.getCustomName().getString()),
                "Custom name should survive round-trip");

        restored.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pickupDeductsXpForTrader(GameTestHelper helper) {
        int savedXpCost = MercantileConfig.get().pickupXpCost;
        boolean savedEnabled = MercantileConfig.get().enableVillagerPickup;
        try {
            MercantileConfig.get().enableVillagerPickup = true;
            MercantileConfig.get().pickupXpCost = 3;

            int xpCost = MercantileConfig.get().pickupXpCost;
            int startXp = xpCost + 5;

            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getAbilities().instabuild = false;
            player.experienceLevel = startXp;
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.moveTo(trader.position().add(1, 0, 0));

            trader.interact(player, InteractionHand.MAIN_HAND);

            int expected = startXp - xpCost;
            helper.assertTrue(player.experienceLevel == expected,
                    "XP should be reduced by pickup cost (" + xpCost + " levels), expected "
                            + expected + " was " + player.experienceLevel);
            helper.assertTrue(player.getMainHandItem().is(Items.PLAYER_HEAD),
                    "Player should hold the trader head");
            helper.assertTrue(trader.isRemoved(),
                    "Trader should be removed after pickup");

            player.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().pickupXpCost = savedXpCost;
            MercantileConfig.get().enableVillagerPickup = savedEnabled;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pickupDisabledByConfig(GameTestHelper helper) {
        boolean savedEnabled = MercantileConfig.get().enableVillagerPickup;
        try {
            MercantileConfig.get().enableVillagerPickup = false;

            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getAbilities().instabuild = false;
            player.experienceLevel = 10;
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.moveTo(trader.position().add(1, 0, 0));

            trader.interact(player, InteractionHand.MAIN_HAND);

            helper.assertFalse(trader.isRemoved(),
                    "Trader must NOT be removed when enableVillagerPickup is false");
            helper.assertFalse(player.getMainHandItem().is(Items.PLAYER_HEAD),
                    "Player must NOT receive a head item when pickup is disabled");
            helper.assertTrue(player.experienceLevel == 10,
                    "XP must not be deducted when pickup is disabled");

            player.discard();
            trader.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().enableVillagerPickup = savedEnabled;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void placementHandlerSpawnsWanderingTrader(GameTestHelper helper) {
        WanderingTrader original = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        original.setCustomName(Component.literal("PlacedTrader"));
        original.setDespawnDelay(8888);

        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 4),
                new ItemStack(Items.CARROT, 6),
                16, 1, 0.05f);
        offer.increaseUses();
        original.getOffers().add(offer);

        int snapshotDespawn = original.getDespawnDelay();
        int origOfferCount = original.getOffers().size();

        ItemStack headItem = VillagerPickupHelper.createHeadItem(original);
        original.discard();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, headItem);

        // Click UP on the floor at (0,0,0) — same pattern as malformedNbtKeepsItem.
        // The placement handler will spawn the trader at the position above (rel 0,1,0).
        BlockPos target = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target), Direction.UP, target, false);
        player.gameMode.useItemOn(player, helper.getLevel(), headItem, InteractionHand.MAIN_HAND, hit);

        AABB box = new AABB(target.above()).inflate(2.0);
        List<WanderingTrader> placedList = helper.getLevel().getEntitiesOfClass(
                WanderingTrader.class, box);
        helper.assertTrue(placedList.size() == 1,
                "Exactly one trader should be placed by the handler (was " + placedList.size() + ")");
        WanderingTrader placed = placedList.get(0);

        helper.assertTrue(placed.getCustomName() != null
                        && "PlacedTrader".equals(placed.getCustomName().getString()),
                "Placed trader should have the custom name from the item");
        helper.assertTrue(placed.getDespawnDelay() == snapshotDespawn,
                "Placed trader should resume with snapshot despawn delay (was "
                        + placed.getDespawnDelay() + ")");
        helper.assertTrue(placed.getOffers().size() == origOfferCount,
                "Placed trader should preserve offers (was " + placed.getOffers().size() + ")");
        boolean hasCarrotOffer = placed.getOffers().stream()
                .anyMatch(o -> o.getResult().is(Items.CARROT));
        helper.assertTrue(hasCarrotOffer, "Carrot offer should be preserved on placement");
        helper.assertTrue(player.getMainHandItem().isEmpty()
                        || player.getMainHandItem().getCount() == 0,
                "Item should be consumed after placement");

        placed.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void multiCyclePickupKeepsIdentityStable(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        trader.setCustomName(Component.literal("Caravan-Joe"));
        trader.setDespawnDelay(5432);

        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 5),
                new ItemStack(Items.PUMPKIN, 1),
                12, 1, 0.05f);
        offer.increaseUses();
        trader.getOffers().add(offer);

        int snapshotDespawn = trader.getDespawnDelay();
        int origOfferCount = trader.getOffers().size();

        ItemStack head1 = VillagerPickupHelper.createHeadItem(trader);
        trader.discard();

        CompoundTag nbt1 = head1.get(DataComponents.CUSTOM_DATA).copyTag();
        WanderingTrader cycle1 = EntityType.WANDERING_TRADER.create(helper.getLevel());
        helper.assertTrue(cycle1 != null, "Cycle 1 restored trader should be created");
        cycle1.load(nbt1);

        helper.assertTrue(cycle1.getCustomName() != null
                        && "Caravan-Joe".equals(cycle1.getCustomName().getString()),
                "Cycle 1: custom name should be preserved");
        helper.assertTrue(cycle1.getOffers().size() == origOfferCount,
                "Cycle 1: offer count should be preserved");
        MerchantOffer cycle1Offer = cycle1.getOffers().stream()
                .filter(o -> o.getResult().is(Items.PUMPKIN))
                .findFirst().orElse(null);
        helper.assertTrue(cycle1Offer != null, "Cycle 1: pumpkin offer should exist");
        helper.assertTrue(cycle1Offer.getUses() == 1,
                "Cycle 1: uses should remain 1, was " + cycle1Offer.getUses());
        helper.assertTrue(cycle1.getDespawnDelay() == snapshotDespawn,
                "Cycle 1: despawn delay should match snapshot");

        ItemStack head2 = VillagerPickupHelper.createHeadItem(cycle1);
        cycle1.discard();

        CompoundTag nbt2 = head2.get(DataComponents.CUSTOM_DATA).copyTag();
        WanderingTrader cycle2 = EntityType.WANDERING_TRADER.create(helper.getLevel());
        helper.assertTrue(cycle2 != null, "Cycle 2 restored trader should be created");
        cycle2.load(nbt2);

        helper.assertTrue(cycle2.getCustomName() != null
                        && "Caravan-Joe".equals(cycle2.getCustomName().getString()),
                "Cycle 2: custom name should still be preserved");
        helper.assertTrue(cycle2.getOffers().size() == origOfferCount,
                "Cycle 2: offer count should still be preserved");
        MerchantOffer cycle2Offer = cycle2.getOffers().stream()
                .filter(o -> o.getResult().is(Items.PUMPKIN))
                .findFirst().orElse(null);
        helper.assertTrue(cycle2Offer != null, "Cycle 2: pumpkin offer should exist");
        helper.assertTrue(cycle2Offer.getUses() == 1,
                "Cycle 2: uses should still be 1, was " + cycle2Offer.getUses());
        helper.assertTrue(cycle2.getDespawnDelay() == snapshotDespawn,
                "Cycle 2: despawn delay should still match snapshot (item NBT does not tick)");

        cycle2.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void despawnTimerSnapshotCapturesLiveRemainingTime(GameTestHelper helper) {
        // Verify the despawn-lock contract: the snapshot stored on the item must reflect
        // the trader's actual remaining despawn time at pickup, NOT the vanilla default.
        // Lets the trader tick down in the world, picks it up via the real mixin path,
        // and asserts the item NBT matches the live entity value captured just before pickup.
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        trader.setDespawnDelay(4242);

        helper.runAfterDelay(40L, () -> {
            int liveDespawnBeforePickup = trader.getDespawnDelay();
            helper.assertTrue(liveDespawnBeforePickup < 4242,
                    "Despawn delay should have ticked down while in world (was "
                            + liveDespawnBeforePickup + ", initial 4242)");

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getAbilities().instabuild = false;
            player.experienceLevel = 10;
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.moveTo(trader.position().add(1, 0, 0));

            trader.interact(player, InteractionHand.MAIN_HAND);

            helper.assertTrue(trader.isRemoved(), "Trader should be removed after pickup");
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(held.is(Items.PLAYER_HEAD) && held.has(DataComponents.CUSTOM_DATA),
                    "Player should hold head item with custom data after pickup");

            CompoundTag nbt = held.get(DataComponents.CUSTOM_DATA).copyTag();
            int snapshotted = nbt.getInt("DespawnDelay");
            helper.assertTrue(snapshotted == liveDespawnBeforePickup,
                    "Snapshot must equal live entity value at pickup (expected "
                            + liveDespawnBeforePickup + ", was " + snapshotted
                            + "); a vanilla-default snapshot would silently regenerate the timer on placement");

            WanderingTrader restored = EntityType.WANDERING_TRADER.create(helper.getLevel());
            helper.assertTrue(restored != null, "Restored trader should be created");
            restored.load(nbt);
            helper.assertTrue(restored.getDespawnDelay() == liveDespawnBeforePickup,
                    "Placed trader should resume with the snapshot value (was "
                            + restored.getDespawnDelay() + ")");

            restored.discard();
            player.discard();
            helper.succeed();
        });
    }

    private int mercantile$countLeadItems(GameTestHelper helper) {
        net.minecraft.core.BlockPos min = helper.absolutePos(new net.minecraft.core.BlockPos(-32, -8, -32));
        net.minecraft.core.BlockPos max = helper.absolutePos(new net.minecraft.core.BlockPos(32, 24, 32));
        AABB box = new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ());
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, box, e -> e.getItem().is(Items.LEAD));
        return items.size();
    }
}
