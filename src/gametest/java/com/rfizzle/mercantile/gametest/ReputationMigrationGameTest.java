package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

public class ReputationMigrationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void legacyPlayerDataMigratesOnAccess(GameTestHelper helper) {
        // Mock players don't fire ServerPlayConnectionEvents.JOIN, so drive migration directly.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(80);
        data.setReputationMigrated(false);

        ReputationManager.migrateIfNeeded(data);

        helper.assertTrue(data.getScore() == 800,
                "legacy score 80 should scale to 800; got " + data.getScore());
        helper.assertTrue(data.isReputationMigrated(),
                "reputationMigrated flag should be set after migrate");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void migratedPlayerSyncPayloadCarriesDailyFields(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(305);
        // Push a known daily count, and align lastCapResetDay so the sync-time rollover
        // doesn't reset it before the payload is built.
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);
        data.addDailyTradeRep(2);  // dailyReputationEarned += 2

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        ReputationManager.syncToClient(player);

        SyncReputationS2CPayload payload = findLastPayload(channel);
        helper.assertTrue(payload != null, "expected a SyncReputationS2CPayload");
        helper.assertTrue(payload.score() == 305,
                "payload score should be 305, got " + payload.score());
        helper.assertTrue(payload.dailyEarned() == 2,
                "payload dailyEarned should be 2, got " + payload.dailyEarned());
        helper.assertTrue(payload.dailyCap() == MercantileConfig.get().reputationDailyCap,
                "payload dailyCap should match config; got " + payload.dailyCap());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void migrationIsIdempotentWhenCalledFromGainHelpers(GameTestHelper helper) {
        // modifyScore calls migrateIfNeeded defensively; a player who's already been migrated
        // must NOT have their score multiplied again on subsequent modify calls.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(50);
        data.setReputationMigrated(false);

        ReputationManager.modifyScore(player, 0);
        helper.assertTrue(data.getScore() == 500,
                "first modifyScore should migrate 50 -> 500; got " + data.getScore());

        ReputationManager.modifyScore(player, 10);
        helper.assertTrue(data.getScore() == 510,
                "second modifyScore must not re-migrate; expected 510, got " + data.getScore());

        player.discard();
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
}
