package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.*;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ClientMercantileData {

    private static int reputationScore = 0;
    private static String reputationTier = "mercantile.tier.neutral";
    private static int reputationDailyEarned = 0;
    private static int reputationDailyCap = 0;

    private static @Nullable RestockTimerS2CPayload restockTimer;
    private static @Nullable DemandPriceS2CPayload demandPrice;
    private static @Nullable VillagerInfoPanelS2CPayload villagerInfo;

    private static @Nullable WorkstationMapS2CPayload workstationMap;
    private static @Nullable VillageBoundsS2CPayload villageBounds;

    private static final Map<Integer, Boolean> followStates = new HashMap<>();
    private static final Map<BlockPos, PylonStateS2CPayload> pylonStates = new HashMap<>();

    private static @Nullable MercantileConfig serverConfig;

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
        reputationScore = score;
        reputationTier = tier;
        reputationDailyEarned = dailyEarned;
        reputationDailyCap = dailyCap;
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
        }
        villagerInfo = payload;
    }

    public static void clearMerchantScreenData() {
        restockTimer = null;
        demandPrice = null;
        villagerInfo = null;
    }

    // --- Visualization ---

    public static @Nullable WorkstationMapS2CPayload getWorkstationMap() {
        return workstationMap;
    }

    public static void setWorkstationMap(@Nullable WorkstationMapS2CPayload payload) {
        workstationMap = payload;
    }

    public static @Nullable VillageBoundsS2CPayload getVillageBounds() {
        return villageBounds;
    }

    public static void setVillageBounds(@Nullable VillageBoundsS2CPayload payload) {
        villageBounds = payload;
    }

    // --- Follow state ---

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

    // --- Pylon state ---

    public static @Nullable PylonStateS2CPayload getPylonState(BlockPos pos) {
        return pylonStates.get(pos);
    }

    public static void setPylonState(PylonStateS2CPayload payload) {
        pylonStates.put(payload.pylonPos(), payload);
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
        reputationTier = "mercantile.tier.neutral";
        reputationDailyEarned = 0;
        reputationDailyCap = 0;
        restockTimer = null;
        demandPrice = null;
        villagerInfo = null;
        workstationMap = null;
        villageBounds = null;
        followStates.clear();
        pylonStates.clear();
        serverConfig = null;
    }
}
