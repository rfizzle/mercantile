package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationChangedCallback;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        changeScore(player, data, data.getScore() + amount);
        syncToClient(player, data);
    }

    /**
     * Admin path ({@code /mercantile reputation set}): sets an absolute score,
     * bypassing the {@code enableReputation} gate. Migrates first so a later
     * lazy migration cannot 10x an admin-set value, fires the change event,
     * and syncs the HUD.
     *
     * @return the applied (clamped) score
     */
    public static int setScore(ServerPlayer player, int value) {
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        changeScore(player, data, value);
        syncToClient(player, data);
        return data.getScore();
    }

    /**
     * Admin path ({@code /mercantile reputation add}): adds a delta, bypassing
     * the {@code enableReputation} gate. See {@link #setScore}.
     *
     * @return the applied (clamped) score
     */
    public static int addScore(ServerPlayer player, int amount) {
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        changeScore(player, data, data.getScore() + amount);
        syncToClient(player, data);
        return data.getScore();
    }

    /**
     * The single choke point through which every reputation score change
     * flows (trade/cycle/cure/proximity gains, attack/kill losses, bulk
     * trades, admin commands). Applies the clamped score and fires
     * {@link ReputationChangedCallback} when the score actually changed.
     * The one-shot legacy migration in {@link #migrateIfNeeded} intentionally
     * does not flow through here — it is a save-format rescale, not a
     * gameplay change.
     */
    private static void changeScore(ServerPlayer player, PlayerData data, int newScore) {
        int oldScore = data.getScore();
        data.setScore(newScore);
        int applied = data.getScore();
        if (applied == oldScore) return;
        try {
            ReputationChangedCallback.EVENT.invoker().onReputationChanged(player, oldScore, applied);
        } catch (Exception e) {
            // Error isolation per Concord API-STANDARD §3: a misbehaving
            // listener must never corrupt the reputation flow.
            Mercantile.LOGGER.warn("ReputationChangedCallback listener threw", e);
        }
    }

    public enum CapDecision {
        AWARDED,
        BELOW_TRADE_THRESHOLD,
        SUBCAP_HIT,
        TOTAL_CAP_HIT
    }

    /**
     * Cap decision for gift-source rep (stateful — mutates {@code data}). Awards daily totals
     * on success and rolls daily counters over when {@code currentDay} is newer than
     * {@code data.lastCapResetDay}.
     */
    public static CapDecision evaluateGiftGain(PlayerData data, MercantileConfig config, long currentDay) {
        rolloverIfNewDay(data, currentDay);
        if (data.getDailyReputationEarned() >= config.reputationDailyCap) {
            return CapDecision.TOTAL_CAP_HIT;
        }
        if (data.getDailyGiftRep() >= config.reputationDailyMaxGiftRep) {
            return CapDecision.SUBCAP_HIT;
        }
        data.addDailyGiftRep(config.reputationGiftGain);
        return CapDecision.AWARDED;
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
            if (data.getLastDecayDay() != -1L && data.getScore() < 0) {
                int daysPassed = (int) (currentDay - data.getLastDecayDay());
                if (daysPassed > 0) {
                    int decay = daysPassed * MercantileConfig.get().reputationNegativeDecayPerDay;
                    data.setScore(Math.min(0, data.getScore() + decay));
                }
            }
            data.setLastDecayDay(currentDay);
            data.resetDailyCounters(currentDay);
        }
    }

    // Player-aware overload: routes decay through changeScore so ReputationChangedCallback fires.
    public static void rolloverIfNewDay(ServerPlayer player, PlayerData data, long currentDay) {
        if (currentDay > data.getLastCapResetDay()) {
            if (data.getLastDecayDay() != -1L && data.getScore() < 0) {
                int daysPassed = (int) (currentDay - data.getLastDecayDay());
                if (daysPassed > 0) {
                    int decay = daysPassed * MercantileConfig.get().reputationNegativeDecayPerDay;
                    changeScore(player, data, Math.min(0, data.getScore() + decay));
                }
            }
            data.setLastDecayDay(currentDay);
            data.resetDailyCounters(currentDay);
        }
    }

    public static void tryGainTradeRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(player, data, currentDay);
        CapDecision decision = evaluateTradeGain(data, config, currentDay);
        switch (decision) {
            case AWARDED -> {
                changeScore(player, data, data.getScore() + config.reputationTradeGain);
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
        rolloverIfNewDay(player, data, currentDay);
        CapDecision decision = evaluateCycleGain(data, config, currentDay);
        switch (decision) {
            case AWARDED -> {
                changeScore(player, data, data.getScore() + config.reputationCycleGain);
                syncToClient(player, data);
            }
            case SUBCAP_HIT, TOTAL_CAP_HIT -> sendDailyCapMessage(player, data);
            case BELOW_TRADE_THRESHOLD -> { /* unreachable for cycles */ }
        }
    }

    /**
     * Awards gift-source rep and returns the cap decision so the caller can tailor feedback.
     * On a cap hit this already sends the daily-cap action-bar notice, so a caller that shows
     * its own confirmation must only do so on {@link CapDecision#AWARDED} to avoid two competing
     * action-bar lines for one gift. Returns {@code null} when rep or gifting is disabled.
     */
    public static CapDecision tryGainGiftRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableGifting) return null;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(player, data, currentDay);
        CapDecision decision = evaluateGiftGain(data, config, currentDay);
        switch (decision) {
            case AWARDED -> {
                changeScore(player, data, data.getScore() + config.reputationGiftGain);
                syncToClient(player, data);
            }
            case SUBCAP_HIT, TOTAL_CAP_HIT -> sendDailyCapMessage(player, data);
            case BELOW_TRADE_THRESHOLD -> { /* unreachable for gifts */ }
        }
        return decision;
    }

    // Raid win rep is an intentional bypass similar to cure rep: it skips both the daily total
    // cap and the per-source sub-caps, and does NOT contribute to dailyReputationEarned.
    // Defending a village is a rare, heroic act that is rewarded in full.
    public static void gainRaidWinRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableRaidReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(player, data, currentDay);
        changeScore(player, data, data.getScore() + config.reputationRaidWinGain);
        syncToClient(player, data);
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
        rolloverIfNewDay(player, data, currentDay);
        changeScore(player, data, data.getScore() + config.reputationCureGain);
        syncToClient(player, data);
    }

    // Contract rep is an intentional bypass like cure/raid rep: it skips both the daily total
    // cap and the per-source sub-caps, and does NOT contribute to dailyReputationEarned. Unlike
    // cure/raid, contract supply scales with villager count (a bred villager hall would mint
    // uncapped rep), so the bypass is bounded by its own per-day award count instead — the
    // gratitude-gift pattern: the first contractRepPerDay deliveries a day earn rep, later
    // ones still pay emeralds (issue #86).
    public static void gainContractRep(ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableContracts) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        migrateIfNeeded(data);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(player, data, currentDay);
        if (data.getDailyContractRepAwards() >= config.contractRepPerDay) return;
        data.incrementDailyContractRepAwards();
        changeScore(player, data, data.getScore() + config.contractRepGain);
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
        migrateIfNeeded(data);
        syncToClient(player, data);
    }

    private static void syncToClient(ServerPlayer player, PlayerData data) {
        if (player.connection == null) return;
        // Roll daily counters before sending so the HUD reflects a fresh day immediately,
        // even if no rep-gain helper has run yet today.
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        rolloverIfNewDay(player, data, currentDay);
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

        GratitudeGiftManager.maybeGift(player, nearby);

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
        changeScore(player, data, data.getScore() + 1);
        syncToClient(player, data);
    }
}
