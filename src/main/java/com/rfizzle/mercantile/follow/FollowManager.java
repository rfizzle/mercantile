package com.rfizzle.mercantile.follow;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.FollowStateS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FollowManager {

    private static final Map<UUID, UUID> villagerToPlayer = new HashMap<>();
    private static final Map<UUID, Set<UUID>> playerToVillagers = new HashMap<>();

    public static boolean startFollowing(Villager villager, ServerPlayer player) {
        UUID villagerUuid = villager.getUUID();
        UUID playerUuid = player.getUUID();

        UUID currentTarget = villagerToPlayer.get(villagerUuid);
        if (currentTarget != null && !currentTarget.equals(playerUuid)) {
            return false;
        }

        if (currentTarget != null && currentTarget.equals(playerUuid)) {
            return false;
        }

        int count = getFollowerCount(playerUuid);
        if (count >= MercantileConfig.get().maxFollowingVillagers) {
            return false;
        }

        villagerToPlayer.put(villagerUuid, playerUuid);
        playerToVillagers.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(villagerUuid);
        ((FollowableVillager) villager).mercantile$setFollowingSync(true);
        broadcastFollowState(villager, true);
        return true;
    }

    public static boolean stopFollowing(Villager villager) {
        UUID villagerUuid = villager.getUUID();
        UUID playerUuid = villagerToPlayer.remove(villagerUuid);
        if (playerUuid == null) {
            return false;
        }
        Set<UUID> followers = playerToVillagers.get(playerUuid);
        if (followers != null) {
            followers.remove(villagerUuid);
            if (followers.isEmpty()) {
                playerToVillagers.remove(playerUuid);
            }
        }
        ((FollowableVillager) villager).mercantile$setFollowingSync(false);
        broadcastFollowState(villager, false);
        return true;
    }

    public static void stopFollowing(UUID villagerUuid) {
        UUID playerUuid = villagerToPlayer.remove(villagerUuid);
        if (playerUuid != null) {
            Set<UUID> followers = playerToVillagers.get(playerUuid);
            if (followers != null) {
                followers.remove(villagerUuid);
                if (followers.isEmpty()) {
                    playerToVillagers.remove(playerUuid);
                }
            }
        }
    }

    public static boolean isFollowing(Villager villager) {
        return villagerToPlayer.containsKey(villager.getUUID());
    }

    public static boolean isFollowing(UUID villagerUuid) {
        return villagerToPlayer.containsKey(villagerUuid);
    }

    public static @Nullable UUID getFollowTarget(Villager villager) {
        return villagerToPlayer.get(villager.getUUID());
    }

    public static @Nullable UUID getFollowTarget(UUID villagerUuid) {
        return villagerToPlayer.get(villagerUuid);
    }

    public static int getFollowerCount(UUID playerUuid) {
        Set<UUID> followers = playerToVillagers.get(playerUuid);
        return followers == null ? 0 : followers.size();
    }

    public static Set<UUID> getFollowers(UUID playerUuid) {
        Set<UUID> followers = playerToVillagers.get(playerUuid);
        return followers == null ? Set.of() : Collections.unmodifiableSet(followers);
    }

    public static void removePlayer(UUID playerUuid) {
        Set<UUID> followers = playerToVillagers.remove(playerUuid);
        if (followers != null) {
            for (UUID villagerUuid : followers) {
                villagerToPlayer.remove(villagerUuid);
            }
        }
    }

    public static void clearAll() {
        villagerToPlayer.clear();
        playerToVillagers.clear();
    }

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            removePlayer(handler.getPlayer().getUUID());
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Villager) {
                stopFollowing(entity.getUUID());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
    }

    private static void broadcastFollowState(Villager villager, boolean following) {
        if (villager.level() instanceof ServerLevel serverLevel) {
            FollowStateS2CPayload payload = new FollowStateS2CPayload(villager.getId(), following);
            for (ServerPlayer player : serverLevel.players()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private FollowManager() {
    }
}
