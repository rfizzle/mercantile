package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.ContractService;
import com.rfizzle.mercantile.contract.DeliveryContract;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.TradeCycleManager;
import com.rfizzle.mercantile.trade.TradePinManager;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import com.rfizzle.mercantile.visualization.WorkstationMapService;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MercantileNetworking {

    private static final double MAX_INTERACTION_DISTANCE_SQR = 100.0; // 10 blocks

    // Per-player C2S cooldowns. Reads happen on the netty thread before scheduling
    // work on the main thread, so a spammed packet is dropped before it can queue
    // expensive POI queries or trade-pool regeneration.
    private static final Map<UUID, Long> LAST_CYCLE_TRADES_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WORKSTATION_MAP_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PIN_TRADE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CONTRACT_TARGET_MS = new ConcurrentHashMap<>();

    private static final long CYCLE_TRADES_COOLDOWN_MS = 500;
    private static final long REQUEST_QUERY_COOLDOWN_MS = 2000;
    private static final long PIN_TRADE_COOLDOWN_MS = 200;
    // Slightly under the client's 40-tick (2 s) request interval so tick jitter can't starve it.
    private static final long CONTRACT_TARGET_COOLDOWN_MS = 1500;

    /** Villagers within this range of the player are scanned for a held contract's payee. */
    private static final double CONTRACT_TARGET_SCAN_RANGE = 64.0;

    // Tracks whether the trade index payload size has been logged at least once per session.
    private static final AtomicBoolean tradeIndexSizeLogged = new AtomicBoolean(false);

    public static void init() {
        registerPayloadTypes();
        registerServerHandlers();
        registerJoinSync();
        registerReloadSync();
        registerDisconnectCleanup();
    }

    private static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(CycleTradesC2SPayload.TYPE, CycleTradesC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestWorkstationMapC2SPayload.TYPE, RequestWorkstationMapC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PinTradeC2SPayload.TYPE, PinTradeC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestContractTargetC2SPayload.TYPE, RequestContractTargetC2SPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncReputationS2CPayload.TYPE, SyncReputationS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FollowStateS2CPayload.TYPE, FollowStateS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FollowCountS2CPayload.TYPE, FollowCountS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RestockTimerS2CPayload.TYPE, RestockTimerS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DemandPriceS2CPayload.TYPE, DemandPriceS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VillagerInfoPanelS2CPayload.TYPE, VillagerInfoPanelS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WorkstationMapS2CPayload.TYPE, WorkstationMapS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BellRingS2CPayload.TYPE, BellRingS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncS2CPayload.TYPE, ConfigSyncS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TradeIndexS2CPayload.TYPE, TradeIndexS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TradePinsS2CPayload.TYPE, TradePinsS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PinnedTradesSummaryS2CPayload.TYPE, PinnedTradesSummaryS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContractTargetS2CPayload.TYPE, ContractTargetS2CPayload.CODEC);
    }

    private static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTradesC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_CYCLE_TRADES_MS, player.getUUID(), CYCLE_TRADES_COOLDOWN_MS)) return;
            player.server.execute(() -> handleCycleTrades(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestWorkstationMapC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_WORKSTATION_MAP_MS, player.getUUID(), REQUEST_QUERY_COOLDOWN_MS)) return;
            player.server.execute(() -> handleRequestWorkstationMap(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PinTradeC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_PIN_TRADE_MS, player.getUUID(), PIN_TRADE_COOLDOWN_MS)) return;
            player.server.execute(() -> handlePinTrade(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestContractTargetC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!checkCooldown(LAST_CONTRACT_TARGET_MS, player.getUUID(), CONTRACT_TARGET_COOLDOWN_MS)) return;
            player.server.execute(() -> handleRequestContractTarget(player, payload));
        });
    }

    private static void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendJoinSync(handler.getPlayer()));
    }

    private static void registerReloadSync() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) return;
            TradeIndexS2CPayload payload = new TradeIndexS2CPayload(TradeIndexDataSource.snapshot());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.connection != null) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
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
        TradePinManager.syncPinsSummary(player);
        ServerPlayNetworking.send(player, new FollowCountS2CPayload(FollowManager.getFollowerCount(player.getUUID())));
        List<TradeIndexEntry> snapshot = TradeIndexDataSource.snapshot();
        if (tradeIndexSizeLogged.compareAndSet(false, true) && Mercantile.LOGGER.isDebugEnabled()) {
            RegistryFriendlyByteBuf probe = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), player.server.registryAccess());
            try {
                TradeIndexS2CPayload.CODEC.encode(probe, new TradeIndexS2CPayload(snapshot));
                Mercantile.LOGGER.debug("Trade index payload: {} bytes ({} entries)",
                        probe.readableBytes(), snapshot.size());
            } catch (Exception e) {
                Mercantile.LOGGER.debug("Trade index size probe failed: {}", e.getMessage());
            } finally {
                probe.release();
            }
        }
        ServerPlayNetworking.send(player, new TradeIndexS2CPayload(snapshot));
    }

    private static void registerDisconnectCleanup() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            LAST_CYCLE_TRADES_MS.remove(id);
            LAST_WORKSTATION_MAP_MS.remove(id);
            LAST_PIN_TRADE_MS.remove(id);
            LAST_CONTRACT_TARGET_MS.remove(id);
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

    private static void handlePinTrade(ServerPlayer player, PinTradeC2SPayload payload) {
        if (!MercantileConfig.get().enableTradePinning) return;

        Villager villager = resolveVillager(player, payload.villagerEntityId());
        if (villager == null) return;

        if (villager.getTradingPlayer() != player) {
            Mercantile.LOGGER.warn("Player {} tried to pin a trade of a villager they aren't trading with", player.getName().getString());
            return;
        }

        TradePinManager.togglePin(player, villager, payload.offerIndex());
    }

    private static void handleRequestContractTarget(ServerPlayer player, RequestContractTargetC2SPayload payload) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableContracts || !config.enableReputation) return;
        if (player.connection == null) return;
        // The glow is intel for the contract *holder* only — a client that merely learned a
        // contract's UUID (shared chest, tooltip mod) must not get a villager tracker out of it.
        if (!holdsContract(player, payload.contractId())) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int target = ContractTargetS2CPayload.NONE;
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(CONTRACT_TARGET_SCAN_RANGE), Villager::isAlive)) {
            // getAttached, not getAttachedOrCreate: a read path must not persist empty data.
            MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
            DeliveryContract contract = data == null ? null : data.getContract();
            if (contract != null && contract.accepted() && !contract.isExpired(now)
                    && contract.id().equals(payload.contractId())) {
                target = villager.getId();
                break;
            }
        }
        // Always reply — a NONE clears the client's stale glow target.
        ServerPlayNetworking.send(player, new ContractTargetS2CPayload(target));
    }

    private static boolean holdsContract(ServerPlayer player, UUID contractId) {
        for (ItemStack stack : player.getInventory().items) {
            if (isContractWithId(stack, contractId)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isContractWithId(stack, contractId)) return true;
        }
        return false;
    }

    private static boolean isContractWithId(ItemStack stack, UUID contractId) {
        return stack.is(MercantileRegistry.DELIVERY_CONTRACT)
                && ContractService.readContractId(stack).map(contractId::equals).orElse(false);
    }

    private static void handleRequestWorkstationMap(ServerPlayer player) {
        if (!MercantileConfig.get().enableWorkstationVis) return;
        if (player.connection == null) return;
        ServerLevel level = player.serverLevel();
        WorkstationMapS2CPayload payload = WorkstationMapService.build(level, player.blockPosition());
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
