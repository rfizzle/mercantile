package com.rfizzle.mercantile.gametest;

import com.mojang.authlib.GameProfile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationTier;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
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

        var reputation = mercantile.getChild("reputation");
        var setNode = reputation.getChild("set");
        helper.assertTrue(setNode != null, "reputation set literal should exist");
        helper.assertFalse(setNode.canUse(nonOp), "reputation set should deny non-ops");
        helper.assertTrue(setNode.canUse(op), "reputation set should allow ops");

        var addNode = reputation.getChild("add");
        helper.assertTrue(addNode != null, "reputation add literal should exist");
        helper.assertFalse(addNode.canUse(nonOp), "reputation add should deny non-ops");
        helper.assertTrue(addNode.canUse(op), "reputation add should allow ops");

        var playerArg = reputation.getChild("player");
        helper.assertTrue(playerArg != null, "reputation <player> argument node should exist");
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
        // S-040 tier scale: TRUSTED starts at 300.
        data.setScore(300);

        helper.assertTrue(data.getScore() == 300, "score should read back from attachment");
        helper.assertTrue(ReputationTier.TRUSTED == ReputationTier.fromScore(data.getScore()),
                "score 300 should map to Trusted tier");

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

        // S-040 widened the range: MAX_SCORE=1500, MIN_SCORE=-200.
        data.setScore(PlayerData.MAX_SCORE - 10);
        data.addScore(50);
        helper.assertTrue(data.getScore() == PlayerData.MAX_SCORE,
                "should clamp to MAX_SCORE (" + PlayerData.MAX_SCORE + "), got " + data.getScore());

        data.setScore(PlayerData.MIN_SCORE + 10);
        data.addScore(-50);
        helper.assertTrue(data.getScore() == PlayerData.MIN_SCORE,
                "should clamp to MIN_SCORE (" + PlayerData.MIN_SCORE + "), got " + data.getScore());

        data.setScore(50);
        data.addScore(10);
        helper.assertTrue(data.getScore() == 60, "should not clamp when in range");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void showVillageSendsPayload(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var player = helper.makeMockServerPlayerInLevel();

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean saved = MercantileConfig.get().enableVillageBoundaryVis;
        try {
            MercantileConfig.get().enableVillageBoundaryVis = true;
            int result;
            try {
                result = dispatcher.execute("mercantile village", player.createCommandSourceStack());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                helper.fail("/mercantile village failed to parse: " + e.getMessage());
                return;
            }
            helper.assertTrue(result == 1, "/mercantile village should return 1 (success); got " + result);

            int packetCount = GametestNetUtil.countPayloads(channel, VillageBoundsS2CPayload.class);
            helper.assertTrue(packetCount == 1,
                    "Exactly one VillageBoundsS2CPayload should be queued; saw " + packetCount);
        } finally {
            MercantileConfig.get().enableVillageBoundaryVis = saved;
        }

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void showVillageReturnsZeroWhenConfigDisabled(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var player = helper.makeMockServerPlayerInLevel();

        boolean saved = MercantileConfig.get().enableVillageBoundaryVis;
        try {
            MercantileConfig.get().enableVillageBoundaryVis = false;
            int result;
            try {
                result = dispatcher.execute("mercantile village", player.createCommandSourceStack());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                helper.fail("/mercantile village failed to parse: " + e.getMessage());
                return;
            }
            helper.assertTrue(result == 0, "/mercantile village should return 0 when disabled; got " + result);
        } finally {
            MercantileConfig.get().enableVillageBoundaryVis = saved;
        }

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierNameBoundaries(GameTestHelper helper) {
        // S-040 tier scale: REVILED -200..-150, DISTRUSTED -149..-1, NEUTRAL 0..74,
        // LIKED 75..299, TRUSTED 300..999, HONORED 1000+.
        helper.assertTrue(ReputationTier.REVILED == ReputationTier.fromScore(-200), "-200 -> REVILED");
        helper.assertTrue(ReputationTier.REVILED == ReputationTier.fromScore(-150), "-150 -> REVILED");
        helper.assertTrue(ReputationTier.DISTRUSTED == ReputationTier.fromScore(-149), "-149 -> DISTRUSTED");
        helper.assertTrue(ReputationTier.DISTRUSTED == ReputationTier.fromScore(-1), "-1 -> DISTRUSTED");
        helper.assertTrue(ReputationTier.NEUTRAL == ReputationTier.fromScore(0), "0 -> NEUTRAL");
        helper.assertTrue(ReputationTier.NEUTRAL == ReputationTier.fromScore(74), "74 -> NEUTRAL");
        helper.assertTrue(ReputationTier.LIKED == ReputationTier.fromScore(75), "75 -> LIKED");
        helper.assertTrue(ReputationTier.LIKED == ReputationTier.fromScore(299), "299 -> LIKED");
        helper.assertTrue(ReputationTier.TRUSTED == ReputationTier.fromScore(300), "300 -> TRUSTED");
        helper.assertTrue(ReputationTier.TRUSTED == ReputationTier.fromScore(999), "999 -> TRUSTED");
        helper.assertTrue(ReputationTier.HONORED == ReputationTier.fromScore(1000), "1000 -> HONORED");
        helper.assertTrue(ReputationTier.HONORED == ReputationTier.fromScore(1500), "1500 -> HONORED");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationCommandIncludesDailyEarnedAndCap(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var player = helper.makeMockServerPlayerInLevel();

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        // Pre-align rollover so subsequent rolloverIfNewDay calls don't wipe our preset count.
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);
        data.setReputationMigrated(true);
        data.setScore(305);
        data.addDailyTradeRep(3);  // -> dailyReputationEarned = 3
        int cap = MercantileConfig.get().reputationDailyCap;

        List<Component> captured = new ArrayList<>();
        CommandSource src = new CommandSource() {
            @Override public void sendSystemMessage(Component component) { captured.add(component); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        CommandSourceStack stack = new CommandSourceStack(
                src, player.position(), Vec2.ZERO, player.serverLevel(),
                4, "TestSource", Component.literal("TestSource"), server, player);

        int result;
        try {
            result = dispatcher.execute("mercantile reputation", stack);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("/mercantile reputation failed to parse: " + e.getMessage());
            return;
        }
        helper.assertTrue(result == 305, "command should return the score (305); got " + result);
        helper.assertTrue(captured.size() == 1,
                "expected 1 chat message; saw " + captured.size());
        String text = captured.get(0).getString();
        helper.assertTrue(text.contains("3/" + cap),
                "command output should contain '3/" + cap + "'; got: " + text);

        player.discard();
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
