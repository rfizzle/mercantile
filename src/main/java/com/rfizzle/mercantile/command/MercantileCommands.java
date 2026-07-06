package com.rfizzle.mercantile.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PinnedTrade;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.ConfigSyncS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.TradePinManager;
import com.rfizzle.mercantile.trade.TradePinManager.PinStock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

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
                .then(Commands.literal("pins")
                        .executes(ctx -> listPins(ctx.getSource()))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index",
                                                IntegerArgumentType.integer(1, PlayerData.MAX_PINNED_TRADES))
                                        .executes(ctx -> removePin(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index")))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPins(ctx.getSource()))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> reloadConfig(ctx.getSource()))));
    }

    private static int listPins(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mercantile.reputation.not_player"));
            return 0;
        }
        if (!MercantileConfig.get().enableTradePinning) {
            source.sendFailure(Component.translatable("command.mercantile.pins.disabled"));
            return 0;
        }

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);

        // Lazy prune: a pin whose villager is loaded here but no longer sells the offer is dead.
        boolean pruned = false;
        for (PinnedTrade pin : List.copyOf(data.getPinnedTrades())) {
            if (TradePinManager.resolveStock(player, pin) == PinStock.OFFER_GONE) {
                data.removePinnedTrade(pin.villagerUuid(), pin.offerHash());
                pruned = true;
            }
        }
        if (pruned) {
            TradePinManager.syncPinsSummary(player);
        }

        List<PinnedTrade> pins = data.getPinnedTrades();
        if (pins.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mercantile.pins.empty"), false);
            return 0;
        }

        int cap = MercantileConfig.get().maxPinnedTradesPerPlayer;
        source.sendSuccess(() -> Component.translatable("command.mercantile.pins.header",
                pins.size(), cap), false);
        for (int i = 0; i < pins.size(); i++) {
            PinnedTrade pin = pins.get(i);
            PinStock target = TradePinManager.resolveStock(player, pin);
            Component status = Component.translatable(switch (target) {
                case IN_STOCK -> "command.mercantile.pins.status.in_stock";
                case OUT_OF_STOCK -> "command.mercantile.pins.status.out_of_stock";
                default -> "command.mercantile.pins.status.unknown";
            });
            int index = i + 1;
            source.sendSuccess(() -> Component.translatable("command.mercantile.pins.entry",
                    index, pin.villagerName(), pin.tradeSummary(), status), false);
        }
        return pins.size();
    }

    // Not gated on enableTradePinning: remove/clear stay available as an escape hatch so
    // pins (which occupy cap slots) can always be shed, even while the feature is off.
    private static int removePin(CommandSourceStack source, int index) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mercantile.reputation.not_player"));
            return 0;
        }
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        List<PinnedTrade> pins = data.getPinnedTrades();
        if (index > pins.size()) {
            source.sendFailure(Component.translatable("command.mercantile.pins.bad_index", pins.size()));
            return 0;
        }
        PinnedTrade pin = pins.get(index - 1);
        data.removePinnedTrade(pin.villagerUuid(), pin.offerHash());
        TradePinManager.syncPinsSummary(player);
        source.sendSuccess(() -> Component.translatable("command.mercantile.pins.removed",
                pin.villagerName(), pin.tradeSummary()), false);
        return 1;
    }

    private static int clearPins(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mercantile.reputation.not_player"));
            return 0;
        }
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int cleared = data.clearPinnedTrades();
        TradePinManager.syncPinsSummary(player);
        source.sendSuccess(() -> Component.translatable("command.mercantile.pins.cleared", cleared), false);
        return cleared;
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
        // Routed through ReputationManager so the change fires
        // ReputationChangedCallback and syncs the target's HUD.
        int newScore = ReputationManager.setScore(target, value);
        Component tier = ReputationTier.fromScore(newScore).displayName();
        source.sendSuccess(() -> Component.translatable("command.mercantile.reputation.set",
                target.getDisplayName(), newScore, tier), true);
        return newScore;
    }

    private static int addReputation(CommandSourceStack source, ServerPlayer target, int amount) {
        // Routed through ReputationManager so the change fires
        // ReputationChangedCallback and syncs the target's HUD.
        int newScore = ReputationManager.addScore(target, amount);
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
            // Re-push the pins summary so a flipped enableTradePinning is reflected right away
            // (an EMPTY clear when now off, fresh entries when now on) instead of at next sync.
            TradePinManager.syncPinsSummary(player);
        }
        source.sendSuccess(() -> Component.translatable("command.mercantile.reload"), true);
        return 1;
    }
}
