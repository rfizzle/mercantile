package com.rfizzle.mercantile.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.ConfigSyncS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.reputation.ReputationTier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MercantileCommands {

    // Bound on /mercantile reputation add — wide enough to traverse the full score range in one call,
    // narrow enough that Brigadier rejects Integer.MAX_VALUE / MIN_VALUE noise at parse time.
    private static final int ADD_MIN_DELTA = PlayerData.MIN_SCORE - PlayerData.MAX_SCORE;
    private static final int ADD_MAX_DELTA = PlayerData.MAX_SCORE - PlayerData.MIN_SCORE;

    private MercantileCommands() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mercantile")
                .then(Commands.literal("reputation")
                        .executes(ctx -> showOwnReputation(ctx.getSource()))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value",
                                                        IntegerArgumentType.integer(PlayerData.MIN_SCORE, PlayerData.MAX_SCORE))
                                                .executes(ctx -> setReputation(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value",
                                                        IntegerArgumentType.integer(ADD_MIN_DELTA, ADD_MAX_DELTA))
                                                .executes(ctx -> addReputation(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showPlayerReputation(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> reloadConfig(ctx.getSource()))));
    }

    private static int showOwnReputation(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mercantile.reputation.not_player"));
            return 0;
        }
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        ReputationManager.rolloverIfNewDay(data, currentDay);
        int score = data.getScore();
        Component tier = ReputationTier.fromScore(score).displayName();
        int earned = data.getDailyReputationEarned();
        int cap = MercantileConfig.get().reputationDailyCap;
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.self",
                score, tier, earned, cap), false);
        return score;
    }

    private static int showPlayerReputation(CommandSourceStack source, ServerPlayer target) {
        PlayerData data = target.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        long currentDay = target.serverLevel().getGameTime() / 24_000L;
        ReputationManager.rolloverIfNewDay(data, currentDay);
        int score = data.getScore();
        Component tier = ReputationTier.fromScore(score).displayName();
        int earned = data.getDailyReputationEarned();
        int cap = MercantileConfig.get().reputationDailyCap;
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.other",
                target.getDisplayName(), score, tier, earned, cap), false);
        return score;
    }

    private static int setReputation(CommandSourceStack source, ServerPlayer target, int value) {
        PlayerData data = target.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(value);
        Component tier = ReputationTier.fromScore(value).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.set",
                target.getDisplayName(), value, tier), true);
        return value;
    }

    private static int addReputation(CommandSourceStack source, ServerPlayer target, int amount) {
        PlayerData data = target.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.addScore(amount);
        int newScore = data.getScore();
        Component tier = ReputationTier.fromScore(newScore).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.add",
                amount, target.getDisplayName(), newScore, tier), true);
        return newScore;
    }

    private static int reloadConfig(CommandSourceStack source) {
        MercantileConfig.reload();
        String configJson = MercantileConfig.get().toJson();
        ConfigSyncS2CPayload payload = new ConfigSyncS2CPayload(configJson);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
        source.sendSuccess(() -> Component.translatable("command.mercantile.reload"), true);
        return 1;
    }
}
