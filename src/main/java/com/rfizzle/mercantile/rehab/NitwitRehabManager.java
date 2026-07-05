package com.rfizzle.mercantile.rehab;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks paid-for nitwit rehabilitations through their short conversion delay and lands the
 * profession swap on the server tick after the delay elapses. In-memory only: the delay is
 * {@link NitwitRehab#CONVERSION_DELAY_TICKS} (3 seconds), so persisting the pending state across a
 * server crash is deliberately not worth the schema cost. If the villager's chunk unloads
 * mid-delay the entry is retried until {@link #GRACE_TICKS} past its deadline, then dropped so the
 * map stays bounded.
 */
public final class NitwitRehabManager {

    /** How long past the deadline an unloaded villager is waited for before the entry is dropped. */
    private static final long GRACE_TICKS = 12_000;

    private record Pending(long convertAtGameTime, UUID playerUuid) {
    }

    // Villager UUID -> pending conversion. Mutated only on the server thread; concurrent map so
    // isPending() reads from mixin code never race a tick-loop rehash.
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private NitwitRehabManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            // Game time is shared across dimensions; the overworld copy is the canonical one.
            long gameTime = server.overworld().getGameTime();
            Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Pending> entry = it.next();
                Pending pending = entry.getValue();
                if (gameTime < pending.convertAtGameTime()) continue;
                Villager villager = findVillager(server, entry.getKey());
                if (villager != null) {
                    it.remove();
                    convert((ServerLevel) villager.level(), villager, pending.playerUuid());
                } else if (gameTime >= pending.convertAtGameTime() + GRACE_TICKS) {
                    it.remove();
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
    }

    /** Schedules the conversion of an already-paid-for nitwit. */
    public static void schedule(Villager villager, ServerPlayer player) {
        PENDING.put(villager.getUUID(), new Pending(
                villager.level().getGameTime() + NitwitRehab.CONVERSION_DELAY_TICKS,
                player.getUUID()));
    }

    public static boolean isPending(UUID villagerUuid) {
        return PENDING.containsKey(villagerUuid);
    }

    // Searched across all levels so a nether-portal trip mid-delay doesn't strand the conversion.
    private static @Nullable Villager findVillager(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    private static void convert(ServerLevel level, Villager villager, UUID playerUuid) {
        if (!villager.isAlive()) return;
        if (villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) return;

        // VillagerProfessionLockMixin reverts any profession -> NONE transition on a locked
        // villager; clear the flag first so the rehabilitation always sticks.
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        if (data.isProfessionLocked()) {
            data.setProfessionLocked(false);
            villager.setAttached(MercantileAttachments.VILLAGER_DATA, data);
        }

        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        // Rebuild the brain's goal packages so the now-unemployed villager starts job hunting,
        // mirroring what vanilla does after a zombie-villager cure.
        villager.refreshBrain(level);

        villager.playSound(SoundEvents.VILLAGER_YES, 1.0f, villager.getVoicePitch());
        // Entity event 14 = vanilla green "happy villager" particles.
        level.broadcastEntityEvent(villager, (byte) 14);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null && player.connection != null) {
            player.displayClientMessage(
                    Component.translatable("mercantile.rehab.success")
                            .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
