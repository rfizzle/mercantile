package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.api.ReputationTier;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class ReputationHudSyncGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationChangeSendsSyncPayload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        ReputationManager.modifyScore(player, 5);

        int after = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(after - before == 1,
                "modifyScore must send exactly 1 sync payload; saw delta " + (after - before));

        SyncReputationS2CPayload payload = findLastPayload(channel);
        helper.assertTrue(payload != null, "expected at least one SyncReputationS2CPayload on channel");
        helper.assertTrue(payload.score() == data.getScore(),
                "payload score " + payload.score() + " must match PlayerData score " + data.getScore());
        String expectedTier = ReputationTier.fromScore(data.getScore()).translationKey();
        helper.assertTrue(payload.tierKey().equals(expectedTier),
                "payload tier " + payload.tierKey() + " must equal " + expectedTier);

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void explicitSyncToClientSendsCurrentState(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(60);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        ReputationManager.syncToClient(player);

        int after = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(after - before == 1,
                "syncToClient must send exactly 1 sync payload; saw delta " + (after - before));

        SyncReputationS2CPayload payload = findLastPayload(channel);
        helper.assertTrue(payload != null, "expected at least one SyncReputationS2CPayload");
        helper.assertTrue(payload.score() == 60,
                "payload score should equal 60, got " + payload.score());
        String expectedTier = ReputationTier.fromScore(60).translationKey();
        helper.assertTrue(payload.tierKey().equals(expectedTier),
                "payload tier should be " + expectedTier + ", got " + payload.tierKey());

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void syncPayloadContainsTranslationKey(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        ReputationManager.syncToClient(player);

        SyncReputationS2CPayload payload = findLastPayload(channel);
        helper.assertTrue(payload != null, "expected at least one SyncReputationS2CPayload");
        helper.assertTrue(payload.tierKey().startsWith("mercantile.tier."),
                "tierKey must be a translation key starting with 'mercantile.tier.', got: " + payload.tierKey());

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void noSyncWhenReputationDisabled(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        boolean saved = MercantileConfig.get().enableReputation;
        try {
            MercantileConfig.get().enableReputation = false;
            ReputationManager.modifyScore(player, 5);
        } finally {
            MercantileConfig.get().enableReputation = saved;
        }

        int after = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(after - before == 0,
                "modifyScore must not send any sync payload when enableReputation=false; saw delta " + (after - before));
        helper.assertTrue(data.getScore() == 0,
                "score should be unchanged when enableReputation=false, got " + data.getScore());

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeNotificationFlowsThroughToHudPayload(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        // S-040 introduced pulse-based gain: only every Nth trade awards rep + sync. Pre-align
        // the day so the implicit rollover in tryGainTradeRep doesn't reset our seeded state.
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);
        data.setReputationMigrated(true);
        data.setScore(0);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        villager.setTradingPlayer(player);
        int tradesPerGain = MercantileConfig.get().reputationTradesPerGain;
        for (int i = 0; i < tradesPerGain; i++) {
            villager.notifyTrade(offer);
        }

        int after = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(after - before >= 1,
                "pulse trade should trigger at least 1 sync payload to the HUD; saw delta " + (after - before));

        SyncReputationS2CPayload payload = findLastPayload(channel);
        helper.assertTrue(payload != null, "expected at least one SyncReputationS2CPayload after trade");
        helper.assertTrue(payload.score() > 0,
                "trade payload should reflect positive score, got " + payload.score());

        helper.succeed();
    }

    private static SyncReputationS2CPayload findLastPayload(EmbeddedChannel channel) {
        SyncReputationS2CPayload last = null;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof SyncReputationS2CPayload sync) {
                last = sync;
            }
        }
        return last;
    }

    private Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.overrideOffers(offers);
        return villager;
    }
}
