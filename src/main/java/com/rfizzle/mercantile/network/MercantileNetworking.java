package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.TradeCycleManager;
import com.rfizzle.mercantile.visualization.VillageBoundsService;
import com.rfizzle.mercantile.visualization.WorkstationMapService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MercantileNetworking {

    private static final double MAX_INTERACTION_DISTANCE_SQR = 100.0; // 10 blocks

    // Per-player C2S cooldowns. Reads happen on the netty thread before scheduling
    // work on the main thread, so a spammed packet is dropped before it can queue
    // expensive POI queries or trade-pool regeneration.
    private static final Map<UUID, Long> LAST_CYCLE_TRADES_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WORKSTATION_MAP_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_VILLAGE_BOUNDS_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_FOLLOW_TOGGLE_MS = new ConcurrentHashMap<>();

    private static final long CYCLE_TRADES_COOLDOWN_MS = 500;
    private static final long REQUEST_QUERY_COOLDOWN_MS = 2000;
    private static final long FOLLOW_TOGGLE_COOLDOWN_MS = 500;

    public static void init() {
        registerPayloadTypes();
        registerServerHandlers();
        registerJoinSync();
        registerDisconnectCleanup();
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
        PayloadTypeRegistry.playS2C().register(BellRingS2CPayload.TYPE, BellRingS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VillageBoundsS2CPayload.TYPE, VillageBoundsS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncS2CPayload.TYPE, ConfigSyncS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PylonStateS2CPayload.TYPE, PylonStateS2CPayload.CODEC);
    }

    private static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTradesC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_CYCLE_TRADES_MS, player.getUUID(), CYCLE_TRADES_COOLDOWN_MS)) return;
            player.server.execute(() -> handleCycleTrades(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(FollowVillagerC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_FOLLOW_TOGGLE_MS, player.getUUID(), FOLLOW_TOGGLE_COOLDOWN_MS)) return;
            player.server.execute(() -> handleFollowVillager(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestWorkstationMapC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_WORKSTATION_MAP_MS, player.getUUID(), REQUEST_QUERY_COOLDOWN_MS)) return;
            player.server.execute(() -> handleRequestWorkstationMap(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestVillageBoundsC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handleRequestVillageBounds(player));
        });
    }

    private static void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendJoinSync(handler.getPlayer()));
    }

    // Public so gametests can drive the same emission without the real
    // ServerPlayConnectionEvents.JOIN event firing on mock players, and so future
    // admin commands could resync a specific player without restarting their session.
    public static void sendJoinSync(ServerPlayer player) {
        if (player.connection == null) return;
        // Send config first — the client uses config gates when interpreting subsequent
        // payloads (e.g. enableReputationHud), so landing config before rep avoids a
        // one-frame mismatch on the HUD at login.
        ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(MercantileConfig.get().toJson()));
        ReputationManager.syncToClient(player);
    }

    private static void registerDisconnectCleanup() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            LAST_CYCLE_TRADES_MS.remove(id);
            LAST_WORKSTATION_MAP_MS.remove(id);
            LAST_VILLAGE_BOUNDS_MS.remove(id);
            LAST_FOLLOW_TOGGLE_MS.remove(id);
        });
    }

    private static boolean checkCooldown(Map<UUID, Long> map, UUID id, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = map.get(id);
        if (last != null && now - last < cooldownMs) return false;
        map.put(id, now);
        return true;
    }

    private static void handleCycleTrades(ServerPlayer player, CycleTradesC2SPayload payload) {
        Villager villager = resolveVillager(player, payload.villagerEntityId());
        if (villager == null) return;

        if (villager.getTradingPlayer() != player) {
            Mercantile.LOGGER.warn("Player {} tried to cycle trades for a villager they aren't trading with", player.getName().getString());
            return;
        }

        TradeCycleManager.cycle(player, villager);
    }

    private static void handleFollowVillager(ServerPlayer player, FollowVillagerC2SPayload payload) {
        if (!MercantileConfig.get().enableFollowMode) return;

        Villager villager = resolveVillager(player, payload.villagerEntityId());
        if (villager == null) return;

        if (FollowManager.isFollowing(villager)) {
            UUID currentTarget = FollowManager.getFollowTarget(villager);
            if (currentTarget == null || !currentTarget.equals(player.getUUID())) {
                Mercantile.LOGGER.warn("Player {} tried to stop-follow a villager owned by another player",
                        player.getName().getString());
                return;
            }
            FollowManager.stopFollowing(villager);
            return;
        }

        if (villager.isBaby()) return;
        if (FollowManager.getFollowerCount(player.getUUID()) >= MercantileConfig.get().maxFollowingVillagers) return;
        if (!player.getMainHandItem().is(Items.EMERALD) && !player.getAbilities().instabuild) {
            Mercantile.LOGGER.warn("Player {} attempted follow without emerald via C2S",
                    player.getName().getString());
            return;
        }

        boolean started = FollowManager.startFollowing(villager, player);
        if (started && !player.getAbilities().instabuild) {
            player.getMainHandItem().shrink(1);
        }
    }

    private static void handleRequestWorkstationMap(ServerPlayer player) {
        if (!MercantileConfig.get().enableWorkstationVis) return;
        if (player.connection == null) return;
        ServerLevel level = player.serverLevel();
        WorkstationMapS2CPayload payload = WorkstationMapService.build(level, player.blockPosition());
        ServerPlayNetworking.send(player, payload);
    }

    // Public so /mercantile village shares the same per-player cooldown as the C2S path —
    // otherwise a macro'd command can outrun the rate limit that exists precisely because the
    // POI query is expensive.
    public static void handleRequestVillageBounds(ServerPlayer player) {
        if (!MercantileConfig.get().enableVillageBoundaryVis) return;
        if (player.connection == null) return;
        if (!checkCooldown(LAST_VILLAGE_BOUNDS_MS, player.getUUID(), REQUEST_QUERY_COOLDOWN_MS)) return;
        ServerLevel level = player.serverLevel();
        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, player.blockPosition());
        ServerPlayNetworking.send(player, payload);
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
