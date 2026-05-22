package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworkHandler {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SyncReputationS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setReputation(payload.score(), payload.tierKey()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FollowStateS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setFollowing(payload.villagerEntityId(), payload.following()));
        });

        ClientPlayNetworking.registerGlobalReceiver(RestockTimerS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setRestockTimer(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(DemandPriceS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setDemandPrice(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(VillagerInfoPanelS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setVillagerInfo(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(WorkstationMapS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setWorkstationMap(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(VillageBoundsS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setVillageBounds(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setServerConfig(MercantileConfig.fromJson(payload.configJson())));
        });

        ClientPlayNetworking.registerGlobalReceiver(PylonStateS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setPylonState(payload));
        });
    }
}
