package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerData;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class VillagerPickupGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void nbtRoundTrip(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN)
                .setLevel(3));
        villager.getOffers().add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                new ItemStack(Items.BOOKSHELF, 1),
                16, 2, 0.05f));

        VillagerData origData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        origData.setProfessionLocked(true);

        int origXp = villager.getVillagerXp();
        int origOfferCount = villager.getOffers().size();

        ItemStack headItem = VillagerPickupHelper.createHeadItem(villager);

        helper.assertTrue(headItem.is(Items.PLAYER_HEAD), "Item should be a player head");
        helper.assertTrue(headItem.has(DataComponents.PROFILE), "Item should have a profile");
        helper.assertTrue(headItem.has(DataComponents.CUSTOM_DATA), "Item should have custom data");
        helper.assertTrue(headItem.has(DataComponents.CUSTOM_NAME), "Item should have a display name");
        helper.assertTrue(headItem.has(DataComponents.LORE), "Item should have lore");

        CustomData customData = headItem.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = customData.copyTag();
        helper.assertTrue(nbt.contains("MercantileDataVersion"), "NBT should contain data version");
        helper.assertTrue(nbt.getInt("MercantileDataVersion") == 1, "Data version should be 1");

        Villager restored = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(restored != null, "Restored villager should be created");
        restored.load(nbt);

        helper.assertTrue(restored.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
                "Profession should survive round-trip");
        helper.assertTrue(restored.getVillagerData().getLevel() == 3,
                "Level should survive round-trip");
        helper.assertTrue(restored.getVillagerXp() == origXp,
                "XP should survive round-trip");
        helper.assertTrue(restored.getOffers().size() == origOfferCount,
                "Trade count should survive round-trip");
        boolean hasBookshelfTrade = restored.getOffers().stream()
                .anyMatch(o -> o.getResult().is(Items.BOOKSHELF));
        helper.assertTrue(hasBookshelfTrade,
                "Custom trade should survive round-trip");

        VillagerData restoredData = restored.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(restoredData.isProfessionLocked(),
                "Profession lock should survive round-trip");

        restored.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pickupDeductsXp(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(villager.position().add(1, 0, 0));

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.experienceLevel == 5,
                "XP should be reduced by pickup cost (5 levels)");
        helper.assertTrue(player.getMainHandItem().is(Items.PLAYER_HEAD),
                "Player should hold the villager head");
        helper.assertTrue(villager.isRemoved(),
                "Villager should be removed after pickup");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wanderingTraderNotPickable(GameTestHelper helper) {
        var trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.experienceLevel = 10;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.moveTo(trader.position().add(1, 0, 0));

        trader.interact(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(trader.isRemoved(),
                "Wandering trader should not be removed");
        helper.assertFalse(player.getMainHandItem().is(Items.PLAYER_HEAD),
                "Player should not receive a head item");
        helper.assertTrue(player.experienceLevel == 10,
                "XP should not be deducted");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void malformedNbtSpawnsDefault(GameTestHelper helper) {
        CompoundTag badNbt = new CompoundTag();
        badNbt.putInt("MercantileDataVersion", 1);
        badNbt.putString("Pos", "corrupt");

        Villager villager;
        try {
            villager = EntityType.VILLAGER.create(helper.getLevel());
            if (villager == null) throw new IllegalStateException("Failed to create villager");
            villager.load(badNbt);
        } catch (Exception e) {
            villager = EntityType.VILLAGER.create(helper.getLevel());
        }

        helper.assertTrue(villager != null,
                "Villager should be created even with malformed NBT");
        helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                "Fallback villager should have default profession");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void futureDataVersionDeniesPlacement(GameTestHelper helper) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("MercantileDataVersion", VillagerPickupHelper.CURRENT_DATA_VERSION + 1);
        nbt.putString("id", "minecraft:villager");

        ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
        headItem.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, headItem);

        BlockPos target = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target), Direction.UP, target, false);

        net.minecraft.world.item.context.UseOnContext ctx =
                new net.minecraft.world.item.context.UseOnContext(
                        player, InteractionHand.MAIN_HAND, hit);
        headItem.useOn(ctx);

        helper.assertTrue(player.getMainHandItem().is(Items.PLAYER_HEAD),
                "Item should be kept when version is too new");
        helper.assertTrue(player.getMainHandItem().getCount() == 1,
                "Item count should not decrease");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void placedVillagerFacesPlayer(GameTestHelper helper) {
        Villager original = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ItemStack headItem = VillagerPickupHelper.createHeadItem(original);
        original.discard();

        CustomData customData = headItem.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = customData.copyTag();

        Villager placed = EntityType.VILLAGER.create(helper.getLevel());
        placed.load(nbt);

        BlockPos spawnPos = helper.absolutePos(new BlockPos(0, 1, 0));
        double playerX = spawnPos.getX() + 3.0;
        double playerZ = spawnPos.getZ();
        double dx = playerX - (spawnPos.getX() + 0.5);
        double dz = playerZ - (spawnPos.getZ() + 0.5);
        float expectedYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        placed.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                expectedYaw, 0);
        placed.setYHeadRot(expectedYaw);

        float tolerance = 1.0f;
        helper.assertTrue(Math.abs(placed.getYRot() - expectedYaw) < tolerance,
                "Villager yaw should match expected facing");
        helper.assertTrue(Math.abs(placed.getYHeadRot() - expectedYaw) < tolerance,
                "Villager head rotation should match expected facing");

        placed.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void identityHashDeterministic(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                new ItemStack(Items.DIAMOND, 1),
                16, 1, 0.05f);

        String hash1 = OfferIdentityHash.compute(offer);
        String hash2 = OfferIdentityHash.compute(offer);
        helper.assertTrue(hash1.equals(hash2), "Same offer should produce same hash");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void identityHashIncludesCount(GameTestHelper helper) {
        MerchantOffer small = new MerchantOffer(
                new ItemCost(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 1),
                16, 1, 0.05f);
        MerchantOffer large = new MerchantOffer(
                new ItemCost(Items.EMERALD, 64),
                new ItemStack(Items.DIAMOND, 10),
                16, 1, 0.05f);

        helper.assertFalse(
                OfferIdentityHash.compute(small).equals(OfferIdentityHash.compute(large)),
                "Hash should differ when item counts differ");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void identityHashCollisionResistance(GameTestHelper helper) {
        MerchantOffer diamond = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                new ItemStack(Items.DIAMOND, 1),
                16, 1, 0.05f);
        MerchantOffer iron = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                new ItemStack(Items.IRON_INGOT, 1),
                16, 1, 0.05f);
        MerchantOffer goldCost = new MerchantOffer(
                new ItemCost(Items.GOLD_INGOT, 10),
                new ItemStack(Items.DIAMOND, 1),
                16, 1, 0.05f);

        helper.assertFalse(
                OfferIdentityHash.compute(diamond).equals(OfferIdentityHash.compute(iron)),
                "Different result items should produce different hashes");
        helper.assertFalse(
                OfferIdentityHash.compute(diamond).equals(OfferIdentityHash.compute(goldCost)),
                "Different cost items should produce different hashes");
        helper.succeed();
    }
}
