package com.rfizzle.mercantile.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.ConfigSyncS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationTier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MercantileCommands {

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
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showPlayerReputation(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 200))
                                                .executes(ctx -> setReputation(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> addReputation(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("village")
                        .executes(ctx -> showVillage(ctx.getSource())))
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
        int score = data.getScore();
        Component tier = ReputationTier.fromScore(score).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.self", score, tier), false);
        return score;
    }

    private static int showPlayerReputation(CommandSourceStack source, ServerPlayer target) {
        PlayerData data = target.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int score = data.getScore();
        Component tier = ReputationTier.fromScore(score).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.other",
                target.getDisplayName(), score, tier), false);
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
        int newScore = Math.max(-100, Math.min(200, data.getScore() + amount));
        data.setScore(newScore);
        Component tier = ReputationTier.fromScore(newScore).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.add",
                amount, target.getDisplayName(), newScore, tier), true);
        return newScore;
    }

    private static int showVillage(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mercantile.village.not_player"));
            return 0;
        }
        // Delegate to the same handler the client packet uses
        handleRequestVillageBounds(player);
        return 1;
    }

    private static void handleRequestVillageBounds(ServerPlayer player) {
        // TODO: Query POI data, compute village bounds, send VillageBoundsS2CPayload
        // This will be implemented alongside the village bounds feature
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
