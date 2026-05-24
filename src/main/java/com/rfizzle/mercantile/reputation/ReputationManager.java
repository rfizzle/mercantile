package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class ReputationManager {

    private static final int PROXIMITY_CHECK_INTERVAL = 20;
    private static final int PROXIMITY_THRESHOLD = 12_000;
    private static final double PROXIMITY_RANGE = 16.0;

    private static int tickCounter;

    private ReputationManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> tickCounter = 0);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            migrateIfNeeded(data);
            syncToClient(player, data);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!MercantileConfig.get().enableReputation) return;

            tickCounter++;
            if (tickCounter < PROXIMITY_CHECK_INTERVAL) return;
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickProximity(player);
            }
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!MercantileConfig.get().enableReputation) return;
            if (!(entity instanceof Villager)) return;
            if (damageTaken <= 0) return;
            if (!entity.isAlive() || entity.isDeadOrDying()) return;
            if (source.getEntity() instanceof ServerPlayer player) {
                modifyScore(player, -MercantileConfig.get().reputationAttackLoss);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!MercantileConfig.get().enableReputation) return;
            if (!(entity instanceof Villager)) return;
            if (source.getEntity() instanceof ServerPlayer player) {
                modifyScore(player, -MercantileConfig.get().reputationKillLoss);
            }
        });
    }

    /**
     * One-shot S-040 score migration: pre-S-040 scores were on a 1× scale; new tier
     * spacing requires 10× values. Idempotent via the {@code reputationMigrated} flag.
     *
     * <p>The migration uses a flat 10× multiplier, but the new tier ranges are not
     * 10× wider symmetrically — DISTRUSTED is ~3× wider and REVILED only ~2× wider.
     * This produces silent tier shifts on legacy saves that the spec accepts:
     * <ul>
     *   <li><b>Upper clamp:</b> legacy scores above 150 clamp at {@link PlayerData#MAX_SCORE}
     *       (1500, the HONORED ceiling). Anything {@code >=100} legacy lands in HONORED.</li>
     *   <li><b>Lower clamp:</b> legacy scores below -20 clamp at {@link PlayerData#MIN_SCORE}
     *       (-200, REVILED floor). Legacy DISTRUSTED scores {@code -15..-19} migrate to REVILED
     *       (e.g. -15 → -150 → REVILED) instead of remaining DISTRUSTED.</li>
     *   <li><b>Positive demotion:</b> legacy LIKED scores in the {@code +1..+7} range migrate to
     *       NEUTRAL after scaling (e.g. +5 → +50, below the new LIKED threshold of +75).</li>
     * </ul>
     * These shifts are accepted as a one-time correction: borderline players recover within
     * a day or two of normal play under the new gain rates, and a per-tier remap would add
     * complexity without addressing the underlying scale change. See S-040 spec notes.
     */
    public static void migrateIfNeeded(PlayerData data) {
        if (data.isReputationMigrated()) return;
        data.setScore(data.getScore() * 10);
        data.setReputationMigrated(true);
    }

    public static void modifyScore(ServerPlayer player, int amount) {
        if (!MercantileConfig.get().enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        data.addScore(amount);
        syncToClient(player, data);
    }

    public enum CapDecision {
        AWARDED,
        BELOW_TRADE_THRESHOLD,
        SUBCAP_HIT,
        TOTAL_CAP_HIT
    }

    /**
     * Cap decision for trade-source rep (stateful — mutates {@code data}). Advances the trade
     * pulse counter on every call, awards daily totals on the gain pulse, and rolls daily
     * counters over when {@code currentDay} is newer than {@code data.lastCapResetDay}. No
     * game-side calls; safe to unit-test.
     */
    public static CapDecision evaluateTradeGain(PlayerData data, MercantileConfig config, long currentDay) {
        rolloverIfNewDay(data, currentDay);
        data.incrementTradesSinceLastRepGain();
        if (data.getTradesSinceLastRepGain() < config.reputationTradesPerGain) {
            return CapDecision.BELOW_TRADE_THRESHOLD;
        }
        if (data.getDailyReputationEarned() >= config.reputationDailyCap) {
            data.resetTradesSinceLastRepGain();
            return CapDecision.TOTAL_CAP_HIT;
        }
        if (data.getDailyTradeRep() >= config.reputationDailyMaxTradeRep) {
            data.resetTradesSinceLastRepGain();
            return CapDecision.SUBCAP_HIT;
        }
        data.addDailyTradeRep(config.reputationTradeGain);
        data.resetTradesSinceLastRepGain();
        return CapDecision.AWARDED;
    }

    /**
     * Cap decision for cycle-source rep (stateful — mutates {@code data}). Awards daily totals
     * on success and rolls daily counters over when {@code currentDay} is newer than
     * {@code data.lastCapResetDay}.
     */
    public static CapDecision evaluateCycleGain(PlayerData data, MercantileConfig config, long currentDay) {
        rolloverIfNewDay(data, currentDay);
        if (data.getDailyReputationEarned() >= config.reputationDailyCap) {
            return CapDecision.TOTAL_CAP_HIT;
        }
        if (data.getDailyCycleRep() >= config.reputationDailyMaxCycleRep) {
            return CapDecision.SUBCAP_HIT;
        }
        data.addDailyCycleRep(config.reputationCycleGain);
        return CapDecision.AWARDED;
    }

    public static void rolloverIfNewDay(PlayerData data, long currentDay) {
        if (currentDay > data.getLastCapResetDay()) {
            data.resetDailyCounters(currentDay);
        }
    }

    public static void tryGainTradeRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        CapDecision decision = evaluateTradeGain(data, config, currentDay);
        switch (decision) {
            case AWARDED -> {
                data.addScore(config.reputationTradeGain);
                syncToClient(player, data);
            }
            case SUBCAP_HIT, TOTAL_CAP_HIT -> sendDailyCapMessage(player, data);
            case BELOW_TRADE_THRESHOLD -> { /* no-op: still earning toward the next threshold */ }
        }
    }

    public static void tryGainCycleRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        CapDecision decision = evaluateCycleGain(data, config, currentDay);
        switch (decision) {
            case AWARDED -> {
                data.addScore(config.reputationCycleGain);
                syncToClient(player, data);
            }
            case SUBCAP_HIT, TOTAL_CAP_HIT -> sendDailyCapMessage(player, data);
            case BELOW_TRADE_THRESHOLD -> { /* unreachable for cycles */ }
        }
    }

    // Cure rep is an intentional bypass: it skips both the daily total cap and the per-source
    // sub-caps, and does NOT contribute to dailyReputationEarned (so the HUD's "earned/cap"
    // readout will not reflect cure gains). Curing a zombie villager is a rare, high-effort
    // act that the spec rewards in full regardless of the day's trade/cycle budget.
    public static void gainCureRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(data, currentDay);
        data.addScore(config.reputationCureGain);
        syncToClient(player, data);
    }

    private static void sendDailyCapMessage(ServerPlayer player, PlayerData data) {
        if (player.connection == null) return;
        // Dedup: with reputationTradesPerGain=5 and active trading, an undeduped message would
        // spam the action bar on every Nth trade after the cap. Reset on day rollover.
        if (data.isDailyCapNotified()) return;
        data.setDailyCapNotified(true);
        player.displayClientMessage(Component.translatable("mercantile.message.reputation_daily_cap"), true);
    }

    public static void syncToClient(ServerPlayer player) {
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        syncToClient(player, data);
    }

    private static void syncToClient(ServerPlayer player, PlayerData data) {
        if (player.connection == null) return;
        // Roll daily counters before sending so the HUD reflects a fresh day immediately,
        // even if no rep-gain helper has run yet today.
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(data, currentDay);
        String tierKey = ReputationTier.fromScore(data.getScore()).translationKey();
        MercantileConfig config = MercantileConfig.get();
        ServerPlayNetworking.send(player, new SyncReputationS2CPayload(
                data.getScore(), tierKey, data.getDailyReputationEarned(), config.reputationDailyCap));
    }

    public static int getPriceModifier(int score, int basePrice) {
        return ReputationTier.priceModifierForScore(score, basePrice);
    }

    public static boolean isReviled(int score) {
        return ReputationTier.fromScore(score) == ReputationTier.REVILED;
    }

    private static void tickProximity(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB searchBox = player.getBoundingBox().inflate(PROXIMITY_RANGE);
        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, searchBox, villager -> true);

        if (nearby.isEmpty()) return;

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int ticks = data.getProximityTicks() + PROXIMITY_CHECK_INTERVAL;

        if (ticks < PROXIMITY_THRESHOLD) {
            data.setProximityTicks(ticks);
            return;
        }

        long currentDay = level.getGameTime() / 24_000L;
        if (data.getLastProximityDay() >= currentDay) {
            data.setProximityTicks(0);
            return;
        }

        data.setProximityTicks(0);
        data.setLastProximityDay(currentDay);
        data.addScore(1);
        syncToClient(player, data);
    }
}
