package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public class MercantileNetworking {

    private static final double MAX_INTERACTION_DISTANCE_SQR = 100.0; // 10 blocks

    public static void init() {
        registerPayloadTypes();
        registerServerHandlers();
    }

    private static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(CycleTradesC2SPayload.TYPE, CycleTradesC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FollowVillagerC2SPayload.TYPE, FollowVillagerC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestWorkstationMapC2SPayload.TYPE, RequestWorkstationMapC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestVillageBoundsC2SPayload.TYPE, RequestVillageBoundsC2SPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncReputationS2CPayload.TYPE, SyncReputationS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FollowStateS2CPayload.TYPE, FollowStateS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RestockTimerS2CPayload.TYPE, RestockTimerS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DemandPriceS2CPayload.TYPE, DemandPriceS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VillagerInfoPanelS2CPayload.TYPE, VillagerInfoPanelS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WorkstationMapS2CPayload.TYPE, WorkstationMapS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VillageBoundsS2CPayload.TYPE, VillageBoundsS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncS2CPayload.TYPE, ConfigSyncS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PylonStateS2CPayload.TYPE, PylonStateS2CPayload.CODEC);
    }

    private static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTradesC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handleCycleTrades(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(FollowVillagerC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handleFollowVillager(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestWorkstationMapC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handleRequestWorkstationMap(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestVillageBoundsC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handleRequestVillageBounds(player));
        });
    }

    private static void handleCycleTrades(ServerPlayer player, CycleTradesC2SPayload payload) {
        if (!MercantileConfig.get().enableTradeCycling) return;

        Villager villager = resolveVillager(player, payload.villagerEntityId());
        if (villager == null) return;

        if (villager.getTradingPlayer() != player) {
            Mercantile.LOGGER.warn("Player {} tried to cycle trades for a villager they aren't trading with", player.getName().getString());
            return;
        }

        // TODO: Validate emerald cost, check for unlockable trades, perform cycle, sync offers
    }

    private static void handleFollowVillager(ServerPlayer player, FollowVillagerC2SPayload payload) {
        if (!MercantileConfig.get().enableFollowMode) return;

        Villager villager = resolveVillager(player, payload.villagerEntityId());
        if (villager == null) return;

        // TODO: Toggle follow state, validate follower limits, send FollowStateS2CPayload
    }

    private static void handleRequestWorkstationMap(ServerPlayer player) {
        // TODO: Query villager-workstation POI bindings, build map, send WorkstationMapS2CPayload
    }

    private static void handleRequestVillageBounds(ServerPlayer player) {
        // TODO: Query POI data, compute village bounds, send VillageBoundsS2CPayload
    }

    private static Villager resolveVillager(ServerPlayer player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return null;
        }
        if (player.distanceToSqr(villager) > MAX_INTERACTION_DISTANCE_SQR) {
            Mercantile.LOGGER.warn("Player {} sent packet for villager beyond interaction range", player.getName().getString());
            return null;
        }
        return villager;
    }
}
