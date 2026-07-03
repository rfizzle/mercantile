package com.rfizzle.mercantile.follow;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.FollowCountS2CPayload;
import com.rfizzle.mercantile.network.FollowStateS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FollowManager {

    private static final Map<UUID, UUID> villagerToPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> playerToVillagers = new ConcurrentHashMap<>();

    // Guards compound mutations that span both maps. Reads stay lock-free.
    private static final Object LOCK = new Object();

    public static boolean startFollowing(Villager villager, ServerPlayer player) {
        UUID villagerUuid = villager.getUUID();
        UUID playerUuid = player.getUUID();

        // Already claimed — by this player or another — so there is nothing to start.
        if (villagerToPlayer.containsKey(villagerUuid)) {
            return false;
        }

        int max = MercantileConfig.get().maxFollowingVillagers;
        if (!tryRegister(villagerUuid, playerUuid, max)) {
            return false;
        }

        ((FollowableVillager) villager).mercantile$setFollowingSync(true);
        ((FollowableVillager) villager).mercantile$setReturningHomeSync(false);
        broadcastFollowState(villager, true);
        sendFollowCount(player.server, playerUuid);
        return true;
    }

    // Package-private for unit testing — performs only the map work, no Villager interaction.
    // Claim-check-add is atomic under LOCK so the two-map invariant cannot be broken by a
    // concurrent stopFollowing between the claim and the set add.
    static boolean tryRegister(UUID villagerUuid, UUID playerUuid, int max) {
        synchronized (LOCK) {
            if (villagerToPlayer.containsKey(villagerUuid)) {
                return false;
            }
            Set<UUID> set = playerToVillagers.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
            if (set.size() >= max) {
                if (set.isEmpty()) {
                    playerToVillagers.remove(playerUuid, set);
                }
                return false;
            }
            set.add(villagerUuid);
            villagerToPlayer.put(villagerUuid, playerUuid);
            return true;
        }
    }

    public static boolean stopFollowing(Villager villager) {
        UUID villagerUuid = villager.getUUID();
        UUID playerUuid;
        synchronized (LOCK) {
            playerUuid = villagerToPlayer.remove(villagerUuid);
            if (playerUuid != null) {
                removeFromPlayerSetLocked(playerUuid, villagerUuid);
            }
        }
        if (playerUuid == null) {
            return false;
        }
        ((FollowableVillager) villager).mercantile$setFollowingSync(false);
        if (MercantileConfig.get().enableSendHome) {
            boolean hasBed = villager.getBrain().hasMemoryValue(MemoryModuleType.HOME);
            boolean hasWorkstation = villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE);
            if (hasBed || hasWorkstation) {
                ((FollowableVillager) villager).mercantile$setReturningHomeSync(true);
            }
        }
        broadcastFollowState(villager, false);
        if (villager.level() instanceof ServerLevel serverLevel) {
            sendFollowCount(serverLevel.getServer(), playerUuid);
        }
        return true;
    }

    // Map-only cleanup. Does NOT clear synced data or broadcast S2C state.
    // Only use when the live Villager reference is unavailable (e.g. cross-map race cleanup
    // where the villager has already been GC'd or unloaded). Returns the player the
    // villager was following, if any, so callers with a server handle can resync counts.
    public static @Nullable UUID stopFollowing(UUID villagerUuid) {
        synchronized (LOCK) {
            UUID playerUuid = villagerToPlayer.remove(villagerUuid);
            if (playerUuid != null) {
                removeFromPlayerSetLocked(playerUuid, villagerUuid);
            }
            return playerUuid;
        }
    }

    // Removes villagerUuid from the player's set; cleans up the outer entry if empty.
    // Caller must hold LOCK.
    private static void removeFromPlayerSetLocked(UUID playerUuid, UUID villagerUuid) {
        Set<UUID> set = playerToVillagers.get(playerUuid);
        if (set == null) return;
        set.remove(villagerUuid);
        if (set.isEmpty()) {
            playerToVillagers.remove(playerUuid, set);
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
        return followers == null ? Set.of() : Set.copyOf(followers);
    }

    public static void removePlayer(UUID playerUuid) {
        synchronized (LOCK) {
            Set<UUID> followers = playerToVillagers.remove(playerUuid);
            if (followers != null) {
                for (UUID villagerUuid : followers) {
                    villagerToPlayer.remove(villagerUuid, playerUuid);
                }
            }
        }
    }

    public static void clearAll() {
        synchronized (LOCK) {
            villagerToPlayer.clear();
            playerToVillagers.clear();
        }
    }

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            removePlayer(handler.getPlayer().getUUID());
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Villager villager) {
                UUID playerUuid = stopFollowing(villager.getUUID());
                if (playerUuid != null) {
                    sendFollowCount(world.getServer(), playerUuid);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
    }

    /**
     * Push the owning player's follower count to just that player, if online.
     * Count is read outside LOCK — safe because all follow mutations run on the
     * server thread, so no interleaving can reorder the pushes.
     */
    private static void sendFollowCount(MinecraftServer server, UUID playerUuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null || player.connection == null) return;
        ServerPlayNetworking.send(player, new FollowCountS2CPayload(getFollowerCount(playerUuid)));
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
