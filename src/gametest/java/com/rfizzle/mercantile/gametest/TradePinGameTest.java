package com.rfizzle.mercantile.gametest;

import com.mojang.authlib.GameProfile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PinnedTrade;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.gametest.util.MockPlayers;
import com.rfizzle.mercantile.network.TradePinsS2CPayload;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import com.rfizzle.mercantile.trade.TradePinManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.UUID;

public class TradePinGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void togglePinAddsAndRemovesPin(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = directPlayer(helper);

        try {
            config.enableTradePinning = true;
            String hash = OfferIdentityHash.compute(villager.getOffers().get(0));
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);

            TradePinManager.togglePin(player, villager, 0);
            helper.assertTrue(data.isTradePinned(villager.getUUID(), hash),
                    "first toggle should pin the offer");
            PinnedTrade pin = data.getPinnedTrades().get(0);
            helper.assertTrue(!pin.tradeSummary().isEmpty(),
                    "pin should snapshot a trade summary; got empty");

            TradePinManager.togglePin(player, villager, 0);
            helper.assertTrue(!data.isTradePinned(villager.getUUID(), hash),
                    "second toggle should unpin the offer");

            TradePinManager.togglePin(player, villager, 99);
            helper.assertTrue(data.getPinnedTrades().isEmpty(),
                    "an out-of-bounds offer index must be ignored");
        } finally {
            config.enableTradePinning = savedEnable;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pinCapEnforced(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        int savedCap = config.maxPinnedTradesPerPlayer;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD, 2), 16, 1, 0.0f));
        ServerPlayer player = directPlayer(helper);

        try {
            config.enableTradePinning = true;
            config.maxPinnedTradesPerPlayer = 1;
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);

            TradePinManager.togglePin(player, villager, 0);
            TradePinManager.togglePin(player, villager, 1);
            helper.assertTrue(data.getPinnedTrades().size() == 1,
                    "the second pin must be denied at cap 1; got " + data.getPinnedTrades().size());

            // Unpinning at the cap must still work (toggle of an existing pin, not an add).
            TradePinManager.togglePin(player, villager, 0);
            helper.assertTrue(data.getPinnedTrades().isEmpty(),
                    "unpinning must not be blocked by the cap");
        } finally {
            config.enableTradePinning = savedEnable;
            config.maxPinnedTradesPerPlayer = savedCap;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void restockNotifiesNearbyPinnedPlayer(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        int savedRange = config.pinRestockNotifyRange;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        EmbeddedChannel channel = connected.channel();

        try {
            config.enableTradePinning = true;
            config.pinRestockNotifyRange = 128;
            player.teleportTo(villager.getX() + 2, villager.getY(), villager.getZ());

            MerchantOffer offer = villager.getOffers().get(0);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), OfferIdentityHash.compute(offer),
                    "Test Villager", "3 Emerald -> Apple"));

            offer.setToOutOfStock();
            channel.outboundMessages().clear();

            villager.restock();
            helper.assertTrue(countActionBarMessages(channel) == 1,
                    "restocking an out-of-stock pinned trade must send one action-bar notification");

            // A second restock with the offer already in stock must stay silent.
            channel.outboundMessages().clear();
            villager.restock();
            helper.assertTrue(countActionBarMessages(channel) == 0,
                    "restocking a fully stocked villager must not notify");
        } finally {
            config.enableTradePinning = savedEnable;
            config.pinRestockNotifyRange = savedRange;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void restockDoesNotNotifyOutOfRangePlayer(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        int savedRange = config.pinRestockNotifyRange;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        EmbeddedChannel channel = connected.channel();

        try {
            config.enableTradePinning = true;
            config.pinRestockNotifyRange = 16;
            player.teleportTo(villager.getX() + 200, villager.getY(), villager.getZ());

            MerchantOffer offer = villager.getOffers().get(0);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), OfferIdentityHash.compute(offer),
                    "Test Villager", "3 Emerald -> Apple"));

            offer.setToOutOfStock();
            channel.outboundMessages().clear();

            villager.restock();
            helper.assertTrue(countActionBarMessages(channel) == 0,
                    "a player outside pinRestockNotifyRange must not be notified");
        } finally {
            config.enableTradePinning = savedEnable;
            config.pinRestockNotifyRange = savedRange;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerDeathPrunesOnlinePlayersPins(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            config.enableTradePinning = true;
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(),
                    OfferIdentityHash.compute(villager.getOffers().get(0)), "Doomed", ""));
            data.addPinnedTrade(new PinnedTrade(UUID.randomUUID(), "other-hash", "Elsewhere", ""));

            villager.hurt(helper.getLevel().damageSources().playerAttack(player), 1_000.0f);
            helper.assertTrue(villager.isDeadOrDying(), "villager should be dead");

            helper.assertTrue(data.getPinnedTrades().size() == 1,
                    "pins on the dead villager must be pruned; got " + data.getPinnedTrades().size());
            helper.assertTrue(data.getPinnedTrades().get(0).villagerName().equals("Elsewhere"),
                    "pins on other villagers must survive the prune");
        } finally {
            config.enableTradePinning = savedEnable;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sendPinsToPrunesOffersTheVillagerNoLongerSells(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        Villager villager = spawnTrader(helper,
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            config.enableTradePinning = true;
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), "stale-hash-of-cycled-offer", "V", ""));
            String liveHash = OfferIdentityHash.compute(villager.getOffers().get(0));
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), liveHash, "V", ""));

            TradePinManager.sendPinsTo(player, villager);

            helper.assertTrue(data.getPinnedTrades().size() == 1,
                    "a pin for an offer the villager no longer sells must be pruned");
            helper.assertTrue(data.isTradePinned(villager.getUUID(), liveHash),
                    "the pin for a still-sold offer must survive");
        } finally {
            config.enableTradePinning = savedEnable;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pinBeyondPayloadCapSurvivesSync(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean savedEnable = config.enableTradePinning;
        MerchantOffer[] many = new MerchantOffer[TradePinsS2CPayload.MAX_OFFERS + 3];
        for (int i = 0; i < many.length; i++) {
            many[i] = new MerchantOffer(new ItemCost(Items.EMERALD, i + 1),
                    new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        }
        Villager villager = spawnTrader(helper, many);
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        EmbeddedChannel channel = connected.channel();

        try {
            config.enableTradePinning = true;
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            String beyondCapHash = OfferIdentityHash.compute(
                    villager.getOffers().get(TradePinsS2CPayload.MAX_OFFERS + 1));
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), beyondCapHash, "V", ""));

            channel.outboundMessages().clear();
            TradePinManager.sendPinsTo(player, villager);

            helper.assertTrue(data.isTradePinned(villager.getUUID(), beyondCapHash),
                    "a pin on an offer past MAX_OFFERS must not be pruned by the sync");
            TradePinsS2CPayload payload = GametestNetUtil.findUniquePayload(
                    helper, channel, TradePinsS2CPayload.class);
            helper.assertTrue(payload.pinnedByIndex().size() == TradePinsS2CPayload.MAX_OFFERS,
                    "the index-aligned payload must cap at MAX_OFFERS; got "
                            + payload.pinnedByIndex().size());
        } finally {
            config.enableTradePinning = savedEnable;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    private static int countActionBarMessages(EmbeddedChannel channel) {
        int n = 0;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
                n++;
            }
        }
        return n;
    }

    private static Villager spawnTrader(GameTestHelper helper, MerchantOffer... offerList) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setNoAi(true);
        MerchantOffers offers = new MerchantOffers();
        for (MerchantOffer offer : offerList) {
            offers.add(offer);
        }
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
