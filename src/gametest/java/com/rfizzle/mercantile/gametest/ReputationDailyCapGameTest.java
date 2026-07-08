package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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

public class ReputationDailyCapGameTest implements FabricGameTest {

    private static final String CAP_MSG_KEY = "notification.mercantile.reputation_daily_cap";

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradingPastDailyCapDoesNotIncreaseScore(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);
            villager.setTradingPlayer(player);

            // Enough trades to far exceed any plausible award budget.
            for (int i = 0; i < 25; i++) {
                villager.notifyTrade(offer);
            }

            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            // Whichever cap binds first stops the trade-rep stream; assert the invariant rather than
            // a hardcoded number so the test survives config-default drift.
            helper.assertTrue(data.getDailyTradeRep() >= config.reputationDailyMaxTradeRep,
                    "dailyTradeRep must hit at least the sub-cap; got " + data.getDailyTradeRep());
            helper.assertTrue(data.getDailyReputationEarned() <= config.reputationDailyCap,
                    "dailyReputationEarned must never exceed total cap; got " + data.getDailyReputationEarned());
            int maxAwardable = Math.min(config.reputationDailyCap, config.reputationDailyMaxTradeRep)
                    * config.reputationTradeGain;
            // Account for overshoot: the gain that triggers saturation may push slightly past the cap.
            helper.assertTrue(data.getScore() <= maxAwardable + config.reputationTradeGain,
                    "score must not exceed cap-bounded budget; got " + data.getScore()
                            + " expected <= " + (maxAwardable + config.reputationTradeGain));
            helper.assertTrue(data.getScore() >= maxAwardable,
                    "score must reach cap-bounded budget; got " + data.getScore()
                            + " expected >= " + maxAwardable);

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void attackPenaltyBypassesDailyCap(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(100);

            villager.setTradingPlayer(player);
            for (int i = 0; i < 25; i++) {
                villager.notifyTrade(offer);
            }
            int scoreAfterTrades = data.getScore();
            int earnedAfterTrades = data.getDailyReputationEarned();
            helper.assertTrue(earnedAfterTrades > 0, "cap should have been hit at least once");

            DamageSource source = player.damageSources().playerAttack(player);
            villager.hurt(source, 1.0f);

            int expectedScore = scoreAfterTrades - config.reputationAttackLoss;
            helper.assertTrue(data.getScore() == expectedScore,
                    "attack penalty must apply past the cap: expected=" + expectedScore + " got=" + data.getScore());
            helper.assertTrue(data.getDailyReputationEarned() == earnedAfterTrades,
                    "attack penalty must NOT consume cap budget: expected dailyEarned=" + earnedAfterTrades
                            + " got=" + data.getDailyReputationEarned());

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void killPenaltyBypassesDailyCap(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(500);

            villager.setTradingPlayer(player);
            for (int i = 0; i < 25; i++) {
                villager.notifyTrade(offer);
            }
            int scoreAfterTrades = data.getScore();
            int earnedAfterTrades = data.getDailyReputationEarned();

            DamageSource source = player.damageSources().playerAttack(player);
            villager.hurt(source, 1000.0f); // lethal — only kill loss applies (see killingVillagerAppliesOnlyKillLoss)

            int expectedScore = scoreAfterTrades - config.reputationKillLoss;
            helper.assertTrue(data.getScore() == expectedScore,
                    "kill penalty must apply past the cap: expected=" + expectedScore + " got=" + data.getScore());
            helper.assertTrue(data.getDailyReputationEarned() == earnedAfterTrades,
                    "kill penalty must NOT consume cap budget: expected dailyEarned=" + earnedAfterTrades
                            + " got=" + data.getDailyReputationEarned());

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cureGainBypassesDailyCap(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(50);

            villager.setTradingPlayer(player);
            for (int i = 0; i < 25; i++) {
                villager.notifyTrade(offer);
            }
            int scoreAfterTrades = data.getScore();
            int earnedAfterTrades = data.getDailyReputationEarned();

            // Cure path bypasses evaluateCycle/TradeGain entirely (heavyweight zombie conversion not needed
            // — the bypass property is in the helper, not the mixin entrypoint).
            ReputationManager.gainCureRep(player);

            int expectedScore = scoreAfterTrades + config.reputationCureGain;
            helper.assertTrue(data.getScore() == expectedScore,
                    "cure must apply past the cap: expected=" + expectedScore + " got=" + data.getScore());
            helper.assertTrue(data.getDailyReputationEarned() == earnedAfterTrades,
                    "cure must NOT consume cap budget: expected dailyEarned=" + earnedAfterTrades
                            + " got=" + data.getDailyReputationEarned());

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void actionBarShownOnSubCapHit(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);

            // Saturate trade sub-cap (defaults: 2 * 5 = 10 trades).
            villager.setTradingPlayer(player);
            for (int i = 0; i < config.reputationDailyMaxTradeRep * config.reputationTradesPerGain; i++) {
                villager.notifyTrade(offer);
            }

            // Clear channel AFTER cap is hit so the next packet is unambiguous.
            EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
            channel.outboundMessages().clear();

            // Run one more 5-trade window — final trade must hit SUBCAP_HIT and send action bar.
            for (int i = 0; i < config.reputationTradesPerGain; i++) {
                villager.notifyTrade(offer);
            }

            helper.assertTrue(hasCapActionBar(channel),
                    "expected cap-message overlay system chat packet with key '" + CAP_MSG_KEY + "' after sub-cap hit");

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void actionBarShownOnTotalCapHit(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            // Force total cap to be the binding constraint by lowering it below the sub-cap.
            config.reputationDailyCap = 1;
            config.reputationDailyMaxCycleRep = 5;

            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(50);

            // First cycle gain saturates the total cap.
            ReputationManager.tryGainCycleRep(player);
            helper.assertTrue(data.getDailyReputationEarned() >= config.reputationDailyCap,
                    "expected daily total cap saturated after first cycle; got "
                            + data.getDailyReputationEarned() + "/" + config.reputationDailyCap);

            EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
            channel.outboundMessages().clear();

            ReputationManager.tryGainCycleRep(player); // must hit TOTAL_CAP_HIT and send action bar
            helper.assertTrue(hasCapActionBar(channel),
                    "expected cap-message overlay system chat packet with key '" + CAP_MSG_KEY + "' after total-cap hit");

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void actionBarNotSentBelowThreshold(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);

            EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
            channel.outboundMessages().clear();

            villager.setTradingPlayer(player);
            int below = config.reputationTradesPerGain - 1;
            for (int i = 0; i < below; i++) {
                villager.notifyTrade(offer);
            }

            helper.assertFalse(hasCapActionBar(channel),
                    "no cap-message overlay expected after only " + below + " trades");

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cycleRepRespectsDailyMaxCycleRep(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(0);

            // First cycle gain — should award and move dailyCycleRep into the sub-cap zone.
            ReputationManager.tryGainCycleRep(player);
            int cycleRepAfterFirst = data.getDailyCycleRep();
            int scoreAfterFirst = data.getScore();
            int earnedAfterFirst = data.getDailyReputationEarned();
            helper.assertTrue(cycleRepAfterFirst >= MercantileConfig.get().reputationDailyMaxCycleRep,
                    "first cycle gain must reach the sub-cap; got " + cycleRepAfterFirst);

            // Second cycle gain must be a no-op — sub-cap blocks the award.
            ReputationManager.tryGainCycleRep(player);
            helper.assertTrue(data.getDailyCycleRep() == cycleRepAfterFirst,
                    "second cycle gain must NOT advance dailyCycleRep past saturation: before="
                            + cycleRepAfterFirst + " after=" + data.getDailyCycleRep());
            helper.assertTrue(data.getScore() == scoreAfterFirst,
                    "second cycle gain must NOT advance score: before=" + scoreAfterFirst
                            + " after=" + data.getScore());
            helper.assertTrue(data.getDailyReputationEarned() == earnedAfterFirst,
                    "second cycle gain must NOT advance dailyEarned: before=" + earnedAfterFirst
                            + " after=" + data.getDailyReputationEarned());

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void actionBarNotRepeatedAfterFirstCapHit(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);

            // Saturate trade sub-cap and trigger the first sub-cap hit so dailyCapNotified=true.
            villager.setTradingPlayer(player);
            int tradesToFirstSubCapHit =
                    (config.reputationDailyMaxTradeRep + 1) * config.reputationTradesPerGain;
            for (int i = 0; i < tradesToFirstSubCapHit; i++) {
                villager.notifyTrade(offer);
            }

            EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
            channel.outboundMessages().clear();

            // Two more pulse windows of trades — each pulse would hit SUBCAP_HIT again, but
            // the dedup must suppress duplicate cap messages for the rest of the day.
            for (int i = 0; i < config.reputationTradesPerGain * 2; i++) {
                villager.notifyTrade(offer);
            }

            helper.assertFalse(hasCapActionBar(channel),
                    "cap-message must NOT repeat after the first hit on the same day");

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void actionBarReNotifiesAfterDayRollover(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            // Force total cap to be the binding constraint by lowering it below the sub-cap.
            config.reputationDailyCap = 1;
            config.reputationDailyMaxCycleRep = 5;

            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(50);

            // Day N: trip the cap once and consume the notification.
            ReputationManager.tryGainCycleRep(player); // AWARDED, saturates cap
            ReputationManager.tryGainCycleRep(player); // TOTAL_CAP_HIT, sets dailyCapNotified
            helper.assertTrue(data.isDailyCapNotified(),
                    "first cap hit on day N must set notified flag");

            // Roll the day so the notify flag clears.
            long currentDay = player.serverLevel().getGameTime() / 24_000L;
            data.resetDailyCounters(currentDay - 1);

            EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
            channel.outboundMessages().clear();

            // Day N+1 effective: re-saturate and re-hit the cap; message MUST fire again.
            ReputationManager.tryGainCycleRep(player);
            ReputationManager.tryGainCycleRep(player);

            helper.assertTrue(hasCapActionBar(channel),
                    "cap-message must re-fire on a new day after rollover clears the notified flag");

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dayRolloverInLiveServerResetsCounters(GameTestHelper helper) {
        ConfigSnapshot snap = ConfigSnapshot.applySpecDefaults();
        try {
            MercantileConfig config = MercantileConfig.get();
            MerchantOffer offer = farmerOffer();
            Villager villager = spawnTrader(helper, offer);
            ServerPlayer player = makePreparedPlayer(helper);
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            data.setScore(0);

            villager.setTradingPlayer(player);
            for (int i = 0; i < 25; i++) {
                villager.notifyTrade(offer);
            }
            helper.assertTrue(data.getDailyReputationEarned() > 0, "cap should be hit on day N");
            int scoreOnDayN = data.getScore();

            // Simulate the live-server day boundary: roll lastCapResetDay back by 1 so the next
            // tryGainTradeRep call sees currentDay > lastCapResetDay and rolls daily counters over.
            long currentDay = player.serverLevel().getGameTime() / 24_000L;
            data.resetDailyCounters(currentDay - 1);

            // Drive enough trades to land exactly one award after the rollover.
            for (int i = 0; i < config.reputationTradesPerGain; i++) {
                villager.notifyTrade(offer);
            }

            helper.assertTrue(data.getDailyReputationEarned() == config.reputationTradeGain,
                    "dailyReputationEarned must reset across day boundary; got " + data.getDailyReputationEarned());
            helper.assertTrue(data.getDailyTradeRep() == config.reputationTradeGain,
                    "dailyTradeRep must reset across day boundary; got " + data.getDailyTradeRep());
            helper.assertTrue(data.getLastCapResetDay() == currentDay,
                    "lastCapResetDay must advance to current day; got " + data.getLastCapResetDay());
            helper.assertTrue(data.getScore() == scoreOnDayN + config.reputationTradeGain,
                    "score must increase by tradeGain after rollover; expected=" + (scoreOnDayN + config.reputationTradeGain)
                            + " got=" + data.getScore());

            player.discard();
            helper.succeed();
        } finally {
            snap.restore();
        }
    }

    // --- helpers ---

    private static MerchantOffer farmerOffer() {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 99, 1, 0.0f);
    }

    private static Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.overrideOffers(offers);
        return villager;
    }

    /**
     * Mock player with reputationMigrated=true (avoid the 10× score scaling on first access)
     * and lastCapResetDay aligned to the current in-game day (so sync's rollover doesn't wipe
     * seeded counters mid-test).
     */
    private static ServerPlayer makePreparedPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);
        return player;
    }

    /**
     * The cap-message goes through {@code displayClientMessage(component, true)} which serializes
     * as a {@link ClientboundSystemChatPacket} with {@code overlay=true} (the action-bar variant),
     * NOT a {@code ClientboundSetActionBarTextPacket}.
     */
    private static boolean hasCapActionBar(EmbeddedChannel channel) {
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundSystemChatPacket pkt
                    && pkt.overlay()
                    && pkt.content().getContents() instanceof TranslatableContents tc
                    && CAP_MSG_KEY.equals(tc.getKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Snapshots the reputation-related config keys and overwrites them with spec defaults so the
     * test runs against a known config regardless of what other tests (or a stale on-disk config)
     * may have left behind. {@link #restore()} reverts every captured key.
     */
    private static final class ConfigSnapshot {
        private final int dailyCap;
        private final int dailyMaxTradeRep;
        private final int dailyMaxCycleRep;
        private final int tradeGain;
        private final int cycleGain;
        private final int cureGain;
        private final int attackLoss;
        private final int killLoss;
        private final int tradesPerGain;

        private ConfigSnapshot() {
            MercantileConfig c = MercantileConfig.get();
            this.dailyCap = c.reputationDailyCap;
            this.dailyMaxTradeRep = c.reputationDailyMaxTradeRep;
            this.dailyMaxCycleRep = c.reputationDailyMaxCycleRep;
            this.tradeGain = c.reputationTradeGain;
            this.cycleGain = c.reputationCycleGain;
            this.cureGain = c.reputationCureGain;
            this.attackLoss = c.reputationAttackLoss;
            this.killLoss = c.reputationKillLoss;
            this.tradesPerGain = c.reputationTradesPerGain;
        }

        static ConfigSnapshot applySpecDefaults() {
            ConfigSnapshot snap = new ConfigSnapshot();
            MercantileConfig c = MercantileConfig.get();
            c.reputationDailyCap = 5;
            c.reputationDailyMaxTradeRep = 2;
            c.reputationDailyMaxCycleRep = 1;
            c.reputationTradeGain = 1;
            c.reputationCycleGain = 1;
            c.reputationCureGain = 5;
            c.reputationAttackLoss = 15;
            c.reputationKillLoss = 40;
            c.reputationTradesPerGain = 5;
            return snap;
        }

        void restore() {
            MercantileConfig c = MercantileConfig.get();
            c.reputationDailyCap = dailyCap;
            c.reputationDailyMaxTradeRep = dailyMaxTradeRep;
            c.reputationDailyMaxCycleRep = dailyMaxCycleRep;
            c.reputationTradeGain = tradeGain;
            c.reputationCycleGain = cycleGain;
            c.reputationCureGain = cureGain;
            c.reputationAttackLoss = attackLoss;
            c.reputationKillLoss = killLoss;
            c.reputationTradesPerGain = tradesPerGain;
        }
    }
}
