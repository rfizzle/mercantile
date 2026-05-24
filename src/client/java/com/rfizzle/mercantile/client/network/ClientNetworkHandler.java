package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.client.visualization.BellGlowTracker;
import com.rfizzle.mercantile.client.visualization.BellRadiusRenderer;
import com.rfizzle.mercantile.client.visualization.VillageBoundsRenderer;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class ClientNetworkHandler {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SyncReputationS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setReputation(payload.score(), payload.tierKey(),
                            payload.dailyEarned(), payload.dailyCap()));
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
            context.client().execute(() -> {
                ClientMercantileData.setVillageBounds(payload);
                VillageBoundsRenderer.notePacketArrived();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setServerConfig(MercantileConfig.fromJson(payload.configJson())));
        });

        ClientPlayNetworking.registerGlobalReceiver(PylonStateS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientMercantileData.setPylonState(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(BellRingS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft client = context.client();
                long now = client.level == null ? 0L : client.level.getGameTime();
                for (var id : payload.villagerIds()) {
                    BellGlowTracker.markGlowing(id, now);
                }
                BellRadiusRenderer.queueBoundaryBurst(payload.bellPos());
            });
        });
    }
}
