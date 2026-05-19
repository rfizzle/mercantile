package com.rfizzle.mercantile.gametest;

import com.mojang.authlib.GameProfile;
import com.rfizzle.mercantile.command.MercantileCommands;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class CommandGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void commandTreeFullyRegistered(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var mercantile = dispatcher.getRoot().getChild("mercantile");
        helper.assertTrue(mercantile != null, "/mercantile not registered");
        helper.assertTrue(mercantile.getChild("reputation") != null, "reputation subcommand missing");
        helper.assertTrue(mercantile.getChild("village") != null, "village subcommand missing");
        helper.assertTrue(mercantile.getChild("reload") != null, "reload subcommand missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void adminCommandsRequireOperator(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var mercantile = dispatcher.getRoot().getChild("mercantile");
        var nonOp = server.createCommandSourceStack().withPermission(0);
        var op = server.createCommandSourceStack().withPermission(2);

        helper.assertTrue(mercantile.getChild("reputation").canUse(nonOp),
                "/mercantile reputation should be public");
        helper.assertTrue(mercantile.getChild("village").canUse(nonOp),
                "/mercantile village should be public");

        helper.assertFalse(mercantile.getChild("reload").canUse(nonOp),
                "reload should deny non-ops");
        helper.assertTrue(mercantile.getChild("reload").canUse(op),
                "reload should allow ops");

        var playerArg = mercantile.getChild("reputation").getChild("player");
        helper.assertTrue(playerArg != null, "player argument node should exist");
        helper.assertFalse(playerArg.canUse(nonOp),
                "reputation <player> should deny non-ops");
        helper.assertTrue(playerArg.canUse(op),
                "reputation <player> should allow ops");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationFlowPlayerToTier(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "FlowPlayer"), ClientInformation.createDefault());

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(75);

        helper.assertTrue(data.getScore() == 75, "score should read back from attachment");
        helper.assertTrue("Trusted".equals(MercantileCommands.getTierName(data.getScore())),
                "score 75 should map to Trusted tier");

        var source = player.createCommandSourceStack();
        helper.assertTrue(source.getPlayer() == player,
                "command source should resolve to the player");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationSetAndReadBack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "SetRepPlayer"), ClientInformation.createDefault());

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        helper.assertTrue(data.getScore() == 0, "initial score should be 0");

        data.setScore(75);
        helper.assertTrue(data.getScore() == 75, "score should be 75 after set");

        data.setScore(-50);
        helper.assertTrue(data.getScore() == -50, "score should be -50 after set");

        data.setScore(0);
        helper.assertTrue(data.getScore() == 0, "score should be 0 after reset");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationAddClampsToValidRange(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "ClampPlayer"), ClientInformation.createDefault());

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);

        data.setScore(190);
        int clamped = Math.max(-100, Math.min(200, data.getScore() + 50));
        data.setScore(clamped);
        helper.assertTrue(data.getScore() == 200, "should clamp to 200 max");

        data.setScore(-90);
        clamped = Math.max(-100, Math.min(200, data.getScore() - 50));
        data.setScore(clamped);
        helper.assertTrue(data.getScore() == -100, "should clamp to -100 min");

        data.setScore(50);
        clamped = Math.max(-100, Math.min(200, data.getScore() + 10));
        data.setScore(clamped);
        helper.assertTrue(data.getScore() == 60, "should not clamp when in range");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierNameBoundaries(GameTestHelper helper) {
        helper.assertTrue("Reviled".equals(MercantileCommands.getTierName(-100)), "-100 -> Reviled");
        helper.assertTrue("Reviled".equals(MercantileCommands.getTierName(-50)), "-50 -> Reviled");
        helper.assertTrue("Distrusted".equals(MercantileCommands.getTierName(-49)), "-49 -> Distrusted");
        helper.assertTrue("Distrusted".equals(MercantileCommands.getTierName(-1)), "-1 -> Distrusted");
        helper.assertTrue("Neutral".equals(MercantileCommands.getTierName(0)), "0 -> Neutral");
        helper.assertTrue("Liked".equals(MercantileCommands.getTierName(1)), "1 -> Liked");
        helper.assertTrue("Liked".equals(MercantileCommands.getTierName(49)), "49 -> Liked");
        helper.assertTrue("Trusted".equals(MercantileCommands.getTierName(50)), "50 -> Trusted");
        helper.assertTrue("Trusted".equals(MercantileCommands.getTierName(99)), "99 -> Trusted");
        helper.assertTrue("Honored".equals(MercantileCommands.getTierName(100)), "100 -> Honored");
        helper.assertTrue("Honored".equals(MercantileCommands.getTierName(200)), "200 -> Honored");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reloadRefreshesConfigFromDisk(GameTestHelper helper) {
        int original = MercantileConfig.get().pickupXpCost;

        MercantileConfig.get().pickupXpCost = 999;
        MercantileConfig.get().save();
        MercantileConfig.reload();
        helper.assertTrue(MercantileConfig.get().pickupXpCost == 999,
                "config should reflect saved value after reload");

        MercantileConfig.get().pickupXpCost = original;
        MercantileConfig.get().save();
        MercantileConfig.reload();
        helper.succeed();
    }
}
