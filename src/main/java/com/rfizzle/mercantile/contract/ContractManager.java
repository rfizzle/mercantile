package com.rfizzle.mercantile.contract;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The delivery-contract heartbeat (issue #86): a throttled server-tick sweep over villagers near
 * players that rolls fresh offers, retracts expired offers and lapsed contracts, and re-emits the
 * speech-bubble cue above villagers with an offer waiting. Only villagers near a player are swept
 * — a distant villager's stale contract is instead expired lazily on the next interaction or
 * approach, which is sound because delivery itself requires proximity.
 */
public final class ContractManager {

    /** Sweep cadence; also the cue-marker re-emit interval (marker lifetime is 30 ticks). */
    public static final int SWEEP_INTERVAL_TICKS = 40;
    /** Villagers within this range of any player participate in the sweep. */
    private static final double SWEEP_RANGE = 32.0;
    private static final double CUE_Y_OFFSET = 0.75;

    private static int tickCounter;

    private ContractManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> tickCounter = 0);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MercantileConfig config = MercantileConfig.get();
            if (!config.enableContracts || !config.enableReputation) return;

            tickCounter++;
            if (tickCounter < SWEEP_INTERVAL_TICKS) return;
            tickCounter = 0;

            // Players' sweep ranges can overlap; visit each villager once per sweep.
            Set<UUID> visited = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                AABB searchBox = player.getBoundingBox().inflate(SWEEP_RANGE);
                List<Villager> nearby = level.getEntitiesOfClass(Villager.class, searchBox, Villager::isAlive);
                for (Villager villager : nearby) {
                    if (!visited.add(villager.getUUID())) continue;
                    sweepVillager(level, villager, config);
                }
            }
        });
    }

    private static void sweepVillager(ServerLevel level, Villager villager, MercantileConfig config) {
        // getAttached, not getAttachedOrCreate: the sweep mostly reads, and a read path must not
        // persist empty attachment data on every nearby villager. rollOffer attaches on write.
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        long now = level.getGameTime();

        DeliveryContract contract = data == null ? null : data.getContract();
        if (contract != null && contract.isExpired(now)) {
            data.setContract(null);
            contract = null;
        }

        if (contract == null) {
            if (!ContractService.isEligible(villager)) return;
            if (level.random.nextDouble() >= offerChancePerSweep(config.contractOfferChance)) return;
            contract = ContractService.rollOffer(level, villager, config);
            if (contract == null) return; // profession has no pool
        }

        if (!contract.accepted()) {
            level.sendParticles(MercantileParticles.CONTRACT_AVAILABLE,
                    villager.getX(), villager.getY() + villager.getBbHeight() + CUE_Y_OFFSET, villager.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Converts the configured percent-per-in-game-day offer chance into a per-sweep probability
     * ({@code chance/100} spread across the day's sweeps). Pure for unit tests.
     */
    public static double offerChancePerSweep(int offerChancePercentPerDay) {
        return offerChancePercentPerDay / 100.0 * SWEEP_INTERVAL_TICKS / 24_000.0;
    }
}
