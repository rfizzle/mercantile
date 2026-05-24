package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.ConfigSyncS2CPayload;
import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

public class PlayerJoinSyncGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void joinSendsConfigSyncPayload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, ConfigSyncS2CPayload.class);

        MercantileNetworking.sendJoinSync(player);

        int after = GametestNetUtil.countPayloads(channel, ConfigSyncS2CPayload.class);
        helper.assertTrue(after - before == 1,
                "sendJoinSync must send exactly 1 ConfigSyncS2CPayload; saw delta " + (after - before));

        ConfigSyncS2CPayload payload = findLastConfigPayload(channel);
        helper.assertTrue(payload != null, "expected at least one ConfigSyncS2CPayload");
        helper.assertTrue(payload.configJson() != null && !payload.configJson().isEmpty(),
                "config JSON must not be empty");
        helper.assertTrue(payload.configJson().equals(MercantileConfig.get().toJson()),
                "config JSON must match the live MercantileConfig.toJson()");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void joinSendsReputationSyncPayload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(42);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int before = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        MercantileNetworking.sendJoinSync(player);

        int after = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(after - before == 1,
                "sendJoinSync must send exactly 1 SyncReputationS2CPayload; saw delta " + (after - before));

        SyncReputationS2CPayload payload = findLastRepPayload(channel);
        helper.assertTrue(payload != null, "expected at least one SyncReputationS2CPayload");
        helper.assertTrue(payload.score() == 42,
                "rep payload score should equal 42, got " + payload.score());

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void joinSendsBothPayloads(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        int configBefore = GametestNetUtil.countPayloads(channel, ConfigSyncS2CPayload.class);
        int repBefore = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);

        MercantileNetworking.sendJoinSync(player);

        int configAfter = GametestNetUtil.countPayloads(channel, ConfigSyncS2CPayload.class);
        int repAfter = GametestNetUtil.countPayloads(channel, SyncReputationS2CPayload.class);
        helper.assertTrue(configAfter - configBefore == 1,
                "sendJoinSync must send exactly 1 ConfigSyncS2CPayload");
        helper.assertTrue(repAfter - repBefore == 1,
                "sendJoinSync must send exactly 1 SyncReputationS2CPayload");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void joinTriggersReputationMigration(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        // Simulate legacy save: score on the pre-S-040 1x scale, not yet migrated.
        data.setReputationMigrated(false);
        data.setScore(5);

        MercantileNetworking.sendJoinSync(player);

        helper.assertTrue(data.isReputationMigrated(),
                "join sync must trigger the S-040 migration for legacy data");
        helper.assertTrue(data.getScore() == 50,
                "legacy score 5 must scale to 50 after migration; got " + data.getScore());

        helper.succeed();
    }

    private static ConfigSyncS2CPayload findLastConfigPayload(EmbeddedChannel channel) {
        ConfigSyncS2CPayload last = null;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof ConfigSyncS2CPayload config) {
                last = config;
            }
        }
        return last;
    }

    private static SyncReputationS2CPayload findLastRepPayload(EmbeddedChannel channel) {
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
