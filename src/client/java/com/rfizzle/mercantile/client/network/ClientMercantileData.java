package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.*;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientMercantileData {

    private static int reputationScore = 0;
    private static String reputationTier = "tooltip.mercantile.tier.neutral";
    private static int reputationDailyEarned = 0;
    private static int reputationDailyCap = 0;
    // False until the first reputation sync of the session lands; gates the
    // tier-change notice so joining a world never fires a spurious message.
    private static boolean hasReputationBaseline = false;

    private static @Nullable RestockTimerS2CPayload restockTimer;
    private static @Nullable DemandPriceS2CPayload demandPrice;
    private static @Nullable VillagerInfoPanelS2CPayload villagerInfo;
    private static @Nullable TradePinsS2CPayload tradePins;

    private static @Nullable WorkstationMapS2CPayload workstationMap;
    private static final Map<Integer, Boolean> followStates = new HashMap<>();

    private static @Nullable MercantileConfig serverConfig;

    // The player's full pinned-trade list with stock status, player-scoped and persistent
    // (unlike the screen-scoped tradePins above). Drives the reputation detail panel.
    private static volatile List<PinnedTradesSummaryS2CPayload.Entry> pinnedTradesSummary = List.of();

    // --- Trade index ---

    private static volatile List<TradeIndexEntry> tradeIndex = List.of();

    public static List<TradeIndexEntry> getTradeIndex() {
        return tradeIndex;
    }

    public static void setTradeIndex(List<TradeIndexEntry> entries) {
        tradeIndex = entries;
    }

    // --- Reputation ---

    public static int getReputationScore() {
        return reputationScore;
    }

    public static String getReputationTier() {
        return reputationTier;
    }

    public static int getReputationDailyEarned() {
        return reputationDailyEarned;
    }

    public static int getReputationDailyCap() {
        return reputationDailyCap;
    }

    public static void setReputation(int score, String tier, int dailyEarned, int dailyCap) {
        int previousScore = reputationScore;
        boolean hadBaseline = hasReputationBaseline;
        reputationScore = score;
        reputationTier = tier;
        reputationDailyEarned = dailyEarned;
        reputationDailyCap = dailyCap;
        hasReputationBaseline = true;
        ReputationTierNotifier.onScoreSynced(hadBaseline, previousScore, score);
    }

    // --- Merchant screen data ---

    public static @Nullable RestockTimerS2CPayload getRestockTimer() {
        return restockTimer;
    }

    /**
     * Protocol invariant: when the user switches from villager A to B, the server
     * MUST send {@link VillagerInfoPanelS2CPayload}(B) before any
     * {@link RestockTimerS2CPayload}(B). Payloads whose villagerEntityId does not
     * match the currently stored {@link #villagerInfo} are dropped — otherwise the
     * panel would show B's info alongside A's restock timer. If info has not yet
     * arrived (null), the payload is accepted and reconciled on info arrival.
     */
    public static void setRestockTimer(@Nullable RestockTimerS2CPayload payload) {
        if (payload != null && villagerInfo != null
                && villagerInfo.villagerEntityId() != payload.villagerEntityId()) {
            return;
        }
        restockTimer = payload;
    }

    public static @Nullable DemandPriceS2CPayload getDemandPrice() {
        return demandPrice;
    }

    /**
     * Protocol invariant: when the user switches from villager A to B, the server
     * MUST send {@link VillagerInfoPanelS2CPayload}(B) before any
     * {@link DemandPriceS2CPayload}(B). Payloads whose villagerEntityId does not
     * match the currently stored {@link #villagerInfo} are dropped — otherwise the
     * panel would show B's info alongside A's price breakdown. If info has not yet
     * arrived (null), the payload is accepted and reconciled on info arrival.
     */
    public static void setDemandPrice(@Nullable DemandPriceS2CPayload payload) {
        if (payload != null && villagerInfo != null
                && villagerInfo.villagerEntityId() != payload.villagerEntityId()) {
            return;
        }
        demandPrice = payload;
    }

    public static @Nullable TradePinsS2CPayload getTradePins() {
        return tradePins;
    }

    /**
     * Protocol invariant: same villager-switch rule as {@link #setRestockTimer} —
     * a pin payload for a villager other than the currently stored {@link #villagerInfo}
     * is dropped; if info has not yet arrived it is accepted and reconciled on arrival.
     */
    public static void setTradePins(@Nullable TradePinsS2CPayload payload) {
        if (payload != null && villagerInfo != null
                && villagerInfo.villagerEntityId() != payload.villagerEntityId()) {
            return;
        }
        tradePins = payload;
    }

    public static @Nullable VillagerInfoPanelS2CPayload getVillagerInfo() {
        return villagerInfo;
    }

    public static void setVillagerInfo(@Nullable VillagerInfoPanelS2CPayload payload) {
        if (payload != null) {
            if (restockTimer != null && restockTimer.villagerEntityId() != payload.villagerEntityId()) {
                restockTimer = null;
            }
            if (demandPrice != null && demandPrice.villagerEntityId() != payload.villagerEntityId()) {
                demandPrice = null;
            }
            if (tradePins != null && tradePins.villagerEntityId() != payload.villagerEntityId()) {
                tradePins = null;
            }
        }
        villagerInfo = payload;
    }

    public static void clearMerchantScreenData() {
        restockTimer = null;
        demandPrice = null;
        villagerInfo = null;
        tradePins = null;
    }

    // --- Visualization ---

    public static @Nullable WorkstationMapS2CPayload getWorkstationMap() {
        return workstationMap;
    }

    public static void setWorkstationMap(@Nullable WorkstationMapS2CPayload payload) {
        workstationMap = payload;
    }

    // --- Follow state ---

    /**
     * The local player's follower count as reported by {@link
     * com.rfizzle.mercantile.network.FollowCountS2CPayload}. Server-authoritative
     * — unlike {@link #followStates}, which aggregates the level-wide broadcast
     * across all players' followers.
     */
    private static int followCount = 0;

    public static int getFollowCount() {
        return followCount;
    }

    public static void setFollowCount(int count) {
        followCount = Math.max(0, count);
    }

    public static boolean isFollowing(int entityId) {
        return followStates.getOrDefault(entityId, false);
    }

    public static void setFollowing(int entityId, boolean following) {
        if (following) {
            followStates.put(entityId, true);
        } else {
            followStates.remove(entityId);
        }
    }

    // --- Pinned trades summary (player-scoped, persistent) ---

    public static List<PinnedTradesSummaryS2CPayload.Entry> getPinnedTradesSummary() {
        return pinnedTradesSummary;
    }

    public static void setPinnedTradesSummary(List<PinnedTradesSummaryS2CPayload.Entry> pins) {
        pinnedTradesSummary = List.copyOf(pins);
    }

    // --- Server config ---

    public static @Nullable MercantileConfig getServerConfig() {
        return serverConfig;
    }

    public static void setServerConfig(MercantileConfig config) {
        serverConfig = config;
    }

    public static void clear() {
        reputationScore = 0;
        reputationTier = "tooltip.mercantile.tier.neutral";
        reputationDailyEarned = 0;
        reputationDailyCap = 0;
        hasReputationBaseline = false;
        restockTimer = null;
        demandPrice = null;
        villagerInfo = null;
        tradePins = null;
        workstationMap = null;
        followStates.clear();
        followCount = 0;
        serverConfig = null;
        tradeIndex = List.of();
        pinnedTradesSummary = List.of();
    }
}
