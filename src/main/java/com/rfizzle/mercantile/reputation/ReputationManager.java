package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.SyncReputationS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    public static void modifyScore(ServerPlayer player, int amount) {
        if (!MercantileConfig.get().enableReputation) return;
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.addScore(amount);
        syncToClient(player, data);
    }

    public static void syncToClient(ServerPlayer player) {
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        syncToClient(player, data);
    }

    private static void syncToClient(ServerPlayer player, PlayerData data) {
        if (player.connection == null) return;
        String tierKey = ReputationTier.fromScore(data.getScore()).translationKey();
        ServerPlayNetworking.send(player, new SyncReputationS2CPayload(data.getScore(), tierKey));
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
