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
import java.util.concurrent.ConcurrentHashMap;

public final class FollowManager {

    private static final Map<UUID, UUID> villagerToPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> playerToVillagers = new ConcurrentHashMap<>();

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

        int max = MercantileConfig.get().maxFollowingVillagers;
        if (!tryRegister(villagerUuid, playerUuid, max)) {
            return false;
        }

        ((FollowableVillager) villager).mercantile$setFollowingSync(true);
        broadcastFollowState(villager, true);
        return true;
    }

    // Package-private for unit testing — performs only the map work, no Villager interaction.
    static boolean tryRegister(UUID villagerUuid, UUID playerUuid, int max) {
        // Atomically claim this villager; fail fast if already owned by any player
        if (villagerToPlayer.putIfAbsent(villagerUuid, playerUuid) != null) {
            return false;
        }
        // Atomically add to the player set and check capacity; roll back on overflow
        boolean[] accepted = {false};
        playerToVillagers.compute(playerUuid, (k, set) -> {
            if (set == null) set = ConcurrentHashMap.newKeySet();
            if (set.size() < max) {
                set.add(villagerUuid);
                accepted[0] = true;
            }
            return set;
        });
        if (!accepted[0]) {
            villagerToPlayer.remove(villagerUuid, playerUuid);
            return false;
        }
        // If stopFollowing ran between putIfAbsent and compute, it removed villagerUuid from
        // villagerToPlayer but couldn't yet remove it from playerToVillagers (not there yet).
        // Detect and undo to maintain the two-map invariant.
        if (!villagerToPlayer.containsKey(villagerUuid)) {
            removeFromPlayerSet(playerUuid, villagerUuid);
            return false;
        }
        return true;
    }

    public static boolean stopFollowing(Villager villager) {
        UUID villagerUuid = villager.getUUID();
        UUID playerUuid = villagerToPlayer.remove(villagerUuid);
        if (playerUuid == null) {
            return false;
        }
        removeFromPlayerSet(playerUuid, villagerUuid);
        ((FollowableVillager) villager).mercantile$setFollowingSync(false);
        broadcastFollowState(villager, false);
        return true;
    }

    public static void stopFollowing(UUID villagerUuid) {
        UUID playerUuid = villagerToPlayer.remove(villagerUuid);
        if (playerUuid != null) {
            removeFromPlayerSet(playerUuid, villagerUuid);
        }
    }

    // Atomically removes villagerUuid from the player's set; cleans up the outer entry if empty.
    private static void removeFromPlayerSet(UUID playerUuid, UUID villagerUuid) {
        playerToVillagers.compute(playerUuid, (k, set) -> {
            if (set == null) return null;
            set.remove(villagerUuid);
            return set.isEmpty() ? null : set;
        });
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
        return followers == null ? Set.of() : Set.copyOf(followers);
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
