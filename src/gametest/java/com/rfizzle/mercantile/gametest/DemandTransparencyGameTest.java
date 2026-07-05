package com.rfizzle.mercantile.gametest;

import com.mojang.authlib.GameProfile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.mixin.MerchantOfferDemandAccessor;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class DemandTransparencyGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadRoundTripsCorrectFields(GameTestHelper helper) {
        DemandPriceS2CPayload original = new DemandPriceS2CPayload(42,
                List.of(new DemandPriceS2CPayload.PriceComponent(3, 2, -1, 0, 0, 0, 0, 0, 4),
                        new DemandPriceS2CPayload.PriceComponent(10, 0, 0, 0, -2, 0, 5, 0, 8)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DemandPriceS2CPayload.CODEC.encode(buf, original);
            DemandPriceS2CPayload decoded = DemandPriceS2CPayload.CODEC.decode(buf);
            helper.assertTrue(decoded.villagerEntityId() == 42,
                    "entityId should round-trip; got " + decoded.villagerEntityId());
            helper.assertTrue(decoded.components().size() == 2,
                    "should round-trip 2 components; got " + decoded.components().size());
            DemandPriceS2CPayload.PriceComponent first = decoded.components().get(0);
            helper.assertTrue(first.basePrice() == 3 && first.demandAdjust() == 2
                            && first.reputationModifier() == -1 && first.gossipModifier() == 0
                            && first.otherAdjust() == 0 && first.finalPrice() == 4,
                    "first component fields should round-trip");
            helper.assertTrue(decoded.components().get(1).fearModifier() == 5,
                    "fearModifier should round-trip");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void accessorReadsDemandField(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 12, 1, 0.05f);
        MerchantOfferDemandAccessor accessor = (MerchantOfferDemandAccessor) (Object) offer;
        helper.assertTrue(accessor.mercantile$getDemand() == 0,
                "fresh offer demand should be 0; got " + accessor.mercantile$getDemand());

        for (int i = 0; i < 10; i++) offer.increaseUses();
        offer.updateDemand();
        helper.assertTrue(accessor.mercantile$getDemand() == offer.getDemand(),
                "accessor should match getDemand(); accessor="
                        + accessor.mercantile$getDemand() + " getDemand=" + offer.getDemand());
        helper.assertTrue(accessor.mercantile$getDemand() > 0,
                "demand should be positive after uses; got " + accessor.mercantile$getDemand());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void buildForReturnsBaseForFreshOffer(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 7), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        List<DemandPriceS2CPayload.PriceComponent> components = PriceBreakdownBuilder.buildFor(villager, player);
        helper.assertTrue(components.size() == 1,
                "expected 1 component for our single override-offer; got " + components.size());
        DemandPriceS2CPayload.PriceComponent c = components.get(0);
        helper.assertTrue(c.basePrice() == 7, "basePrice should be 7; got " + c.basePrice());
        helper.assertTrue(c.demandAdjust() == 0, "demandAdjust should be 0; got " + c.demandAdjust());
        helper.assertTrue(c.reputationModifier() == 0, "reputationModifier should be 0; got " + c.reputationModifier());
        helper.assertTrue(c.gossipModifier() == 0, "gossipModifier should be 0; got " + c.gossipModifier());
        helper.assertTrue(c.finalPrice() == 7, "finalPrice should equal basePrice for fresh; got " + c.finalPrice());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void demandAdjustReflectsDemandField(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 13), new ItemStack(Items.APPLE, 1), 12, 1, 1.0f);
        Villager villager = spawnTrader(helper, offer);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        DemandPriceS2CPayload.PriceComponent before = PriceBreakdownBuilder.buildFor(villager, player).get(0);
        helper.assertTrue(before.demandAdjust() == 0,
                "fresh demandAdjust should be 0; got " + before.demandAdjust());

        for (int i = 0; i < 10; i++) offer.increaseUses();
        offer.updateDemand();

        DemandPriceS2CPayload.PriceComponent after = PriceBreakdownBuilder.buildFor(villager, player).get(0);
        helper.assertTrue(after.demandAdjust() > 0,
                "demandAdjust should be positive after uses+updateDemand; got " + after.demandAdjust());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void componentsForPositiveGossip(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 9), new ItemStack(Items.APPLE, 1), 16, 1, 1.0f);
        Villager villager = spawnTrader(helper, offer);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        villager.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 20);

        DemandPriceS2CPayload.PriceComponent c = PriceBreakdownBuilder.buildFor(villager, player).get(0);
        helper.assertTrue(c.basePrice() == 9, "basePrice should be 9; got " + c.basePrice());
        helper.assertTrue(c.reputationModifier() == 0,
                "reputationModifier should be 0 for NEUTRAL tier; got " + c.reputationModifier());
        helper.assertTrue(c.gossipModifier() < 0,
                "gossipModifier should be negative (discount) for positive gossip; got " + c.gossipModifier());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void componentsForNegativeGossip(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 11), new ItemStack(Items.APPLE, 1), 16, 1, 1.0f);
        Villager villager = spawnTrader(helper, offer);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, 25);

        DemandPriceS2CPayload.PriceComponent c = PriceBreakdownBuilder.buildFor(villager, player).get(0);
        helper.assertTrue(c.gossipModifier() > 0,
                "gossipModifier should be positive (markup) for negative gossip; got " + c.gossipModifier());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeOpenSendsPayload(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        try {
            invokeStartTrading(helper, villager, player);

            DemandPriceS2CPayload payload = GametestNetUtil.findUniquePayload(
                    helper, channel, DemandPriceS2CPayload.class);
            helper.assertTrue(payload.villagerEntityId() == villager.getId(),
                    "payload entityId should match villager; got " + payload.villagerEntityId());
            helper.assertTrue(!payload.components().isEmpty(),
                    "expected at least one component in the breakdown");
        } finally {
            player.closeContainer();
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void featureDisabledSendsNoPayload(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableDemandTransparency;
        try {
            config.enableDemandTransparency = false;
            invokeStartTrading(helper, villager, player);

            int count = GametestNetUtil.countPayloads(channel, DemandPriceS2CPayload.class);
            helper.assertTrue(count == 0,
                    "no DemandPriceS2CPayload should be sent when feature disabled; got " + count);
        } finally {
            config.enableDemandTransparency = saved;
            player.closeContainer();
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    private static void invokeStartTrading(GameTestHelper helper, Villager villager, ServerPlayer player) {
        Method method;
        try {
            method = Villager.class.getDeclaredMethod("startTrading", Player.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            helper.fail("Villager.startTrading not found — signature changed? " + e);
            throw new AssertionError(e);
        }
        try {
            method.invoke(villager, player);
        } catch (InvocationTargetException e) {
            helper.fail("Villager.startTrading threw: " + e.getCause());
            throw new AssertionError(e.getCause());
        } catch (IllegalAccessException e) {
            helper.fail("Could not invoke Villager.startTrading: " + e);
            throw new AssertionError(e);
        }
    }

    private static Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        // Villager.setOffers actually persists; AbstractVillager.overrideOffers is a vanilla no-op.
        villager.setOffers(offers);
        return villager;
    }

    // Direct ServerPlayer construction avoids PlayerList.placeNewPlayer, which schedules
    // server work that ticks the villager's brain and wipes overridden offers.
    private static ServerPlayer directPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TestPlayer"),
                ClientInformation.createDefault());
    }
}
