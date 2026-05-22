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

    public static void setReputation(int score, String tier) {
        reputationScore = score;
        reputationTier = tier;
    }

    // --- Merchant screen data ---

    public static @Nullable RestockTimerS2CPayload getRestockTimer() {
        return restockTimer;
    }

    public static void setRestockTimer(@Nullable RestockTimerS2CPayload payload) {
        restockTimer = payload;
    }

    public static @Nullable DemandPriceS2CPayload getDemandPrice() {
        return demandPrice;
    }

    public static void setDemandPrice(@Nullable DemandPriceS2CPayload payload) {
        demandPrice = payload;
    }

    public static @Nullable VillagerInfoPanelS2CPayload getVillagerInfo() {
        return villagerInfo;
    }

    public static void setVillagerInfo(@Nullable VillagerInfoPanelS2CPayload payload) {
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
