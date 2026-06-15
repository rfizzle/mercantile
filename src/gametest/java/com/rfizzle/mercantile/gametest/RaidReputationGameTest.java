package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.core.BlockPos;

public class RaidReputationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void raidWinReputationGain(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);

        MercantileConfig config = MercantileConfig.get();
        int initialScore = data.getScore();

        ReputationManager.gainRaidWinRep(player);

        int expected = initialScore + config.reputationRaidWinGain;
        helper.assertTrue(data.getScore() == expected,
                "Expected score " + expected + " after raid win, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void raidWinExemptFromDailyCap(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);

        MercantileConfig config = MercantileConfig.get();
        // Hit the daily cap
        data.resetDailyCounters(player.serverLevel().getGameTime() / 24_000L);
        data.addDailyTradeRep(config.reputationDailyCap);
        helper.assertTrue(data.getDailyReputationEarned() >= config.reputationDailyCap, "Should be at daily cap");

        int scoreBefore = data.getScore();
        ReputationManager.gainRaidWinRep(player);

        int expected = scoreBefore + config.reputationRaidWinGain;
        helper.assertTrue(data.getScore() == expected,
                "Raid win should bypass daily cap. Expected " + expected + " but got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void raidWinDisabledToggle(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);

        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableRaidReputation;
        try {
            config.enableRaidReputation = false;
            ReputationManager.gainRaidWinRep(player);
            helper.assertTrue(data.getScore() == 0, "Reputation gain should be disabled when enableRaidReputation=false");
        } finally {
            config.enableRaidReputation = saved;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void raidMixinHook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);
        data.setReputationMigrated(true);

        // Instantiate a Raid object. Raid(int id, ServerLevel level, BlockPos pos)
        Raid raid = new Raid(1, helper.getLevel(), BlockPos.ZERO);

        raid.addHeroOfTheVillage(player);

        int expected = MercantileConfig.get().reputationRaidWinGain;
        helper.assertTrue(data.getScore() == expected,
                "Mixin should have hooked addHeroOfTheVillage and awarded reputation. Expected " + expected + " but got " + data.getScore());
        helper.succeed();
    }
}
