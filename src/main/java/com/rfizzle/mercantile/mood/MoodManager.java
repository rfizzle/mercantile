package com.rfizzle.mercantile.mood;

import com.rfizzle.mercantile.compat.shared.BreedingTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Villager mood derived from ongoing living conditions (bed, workstation, recent sleep,
 * food, recent harm, witnessed deaths). Mood is a property of the villager, identical for
 * all players; the score drifts gradually toward what conditions dictate rather than
 * snapping. All effects are gentle nudges gated on {@code enableMood} — pricing and
 * restock hooks read through {@link #priceModifier} and {@link #restockIntervalTicks}.
 */
public final class MoodManager {

    /** A villager counts as recently hurt for this long after taking damage (2 minutes). */
    public static final long RECENT_HURT_WINDOW_TICKS = 2_400L;
    /** A witnessed villager death weighs on nearby villagers for one full day. */
    public static final long WITNESSED_DEATH_WINDOW_TICKS = 24_000L;
    /** "Slept recently" means within the last day. */
    public static final long RECENT_SLEEP_WINDOW_TICKS = 24_000L;
    /** Villagers within this range of a villager death witness it. */
    public static final double DEATH_WITNESS_RANGE = 16.0;

    private static final int AMBIENT_PARTICLE_INTERVAL = 200;
    private static final double AMBIENT_PARTICLE_RANGE = 16.0;
    private static final float AMBIENT_PARTICLE_CHANCE = 0.25f;
    private static final byte ENTITY_EVENT_HAPPY = 14;
    private static final byte ENTITY_EVENT_ANGRY = 13;

    private static int particleTickCounter;

    private MoodManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> particleTickCounter = 0);

        // Any damage sours the villager itself, regardless of source — reputation already
        // handles the player-facing consequence; mood tracks how the villager feels.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!MercantileConfig.get().enableMood) return;
            if (!(entity instanceof Villager villager)) return;
            if (damageTaken <= 0) return;
            villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA)
                    .setLastHurtGameTime(villager.level().getGameTime());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!MercantileConfig.get().enableMood) return;
            if (!(entity instanceof Villager dead)) return;
            if (!(dead.level() instanceof ServerLevel level)) return;
            long now = level.getGameTime();
            AABB range = dead.getBoundingBox().inflate(DEATH_WITNESS_RANGE);
            for (Villager witness : level.getEntitiesOfClass(Villager.class, range, v -> v != dead && v.isAlive())) {
                witness.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA)
                        .setLastWitnessedDeathGameTime(now);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MercantileConfig config = MercantileConfig.get();
            if (!config.enableMood || !config.moodAmbientParticles) return;

            particleTickCounter++;
            if (particleTickCounter < AMBIENT_PARTICLE_INTERVAL) return;
            particleTickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                AABB searchBox = player.getBoundingBox().inflate(AMBIENT_PARTICLE_RANGE);
                List<Villager> nearby = level.getEntitiesOfClass(Villager.class, searchBox, Villager::isAlive);
                for (Villager villager : nearby) {
                    if (level.random.nextFloat() >= AMBIENT_PARTICLE_CHANCE) continue;
                    switch (tier(villager)) {
                        case HAPPY -> level.broadcastEntityEvent(villager, ENTITY_EVENT_HAPPY);
                        case MISERABLE -> level.broadcastEntityEvent(villager, ENTITY_EVENT_ANGRY);
                        case UNHAPPY, CONTENT -> { /* neutral tiers emit nothing */ }
                    }
                }
            }
        });
    }

    /**
     * Current mood for the villager, drifting the stored value toward what its living
     * conditions dictate based on the time elapsed since the last evaluation. Lazy: the
     * recalculation happens on read (trade open, tooltip, restock check, particle tick),
     * throttled to at most once per {@code moodRecalcIntervalTicks}.
     */
    public static int getMood(Villager villager) {
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        long now = villager.level().getGameTime();
        int interval = MercantileConfig.get().moodRecalcIntervalTicks;

        if (data.getLastMoodUpdateTime() < 0) {
            // First evaluation: start the drift clock at the neutral default rather than
            // snapping to conditions — a freshly spawned villager isn't instantly Miserable.
            data.setLastMoodUpdateTime(now);
            return data.getMood();
        }

        long elapsed = now - data.getLastMoodUpdateTime();
        if (elapsed < interval) return data.getMood();

        int drifted = MoodMath.drift(data.getMood(), computeTarget(villager, now), elapsed, interval);
        data.setMood(drifted);
        data.setLastMoodUpdateTime(now);
        return drifted;
    }

    public static MoodTier tier(Villager villager) {
        return MoodTier.fromMood(getMood(villager));
    }

    /** Mood price nudge for one offer; 0 when the mood system is disabled. */
    public static int priceModifier(Villager villager, int basePrice, MercantileConfig config) {
        if (!config.enableMood) return 0;
        return MoodMath.priceModifier(tier(villager), basePrice, config.moodPriceModifierPercent);
    }

    /** Mood-scaled restock gap; the vanilla interval when the mood system is disabled. */
    public static long restockIntervalTicks(Villager villager, long baseInterval) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableMood) return baseInterval;
        return MoodMath.restockIntervalTicks(tier(villager), baseInterval, config.moodRestockSpeedPercent);
    }

    private static int computeTarget(Villager villager, long now) {
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        boolean hasBed = villager.getBrain().hasMemoryValue(MemoryModuleType.HOME);
        boolean hasWorkstation = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
        boolean sleptRecently = villager.getBrain().getMemory(MemoryModuleType.LAST_SLEPT)
                .map(lastSlept -> now - lastSlept <= RECENT_SLEEP_WINDOW_TICKS)
                .orElse(false);
        boolean wellFed = BreedingTooltipData.computeFoodPoints(villager) >= BreedingTooltipData.WILLING_FOOD_THRESHOLD;
        boolean recentlyHurt = data.getLastHurtGameTime() >= 0
                && now - data.getLastHurtGameTime() <= RECENT_HURT_WINDOW_TICKS;
        boolean witnessedDeath = data.getLastWitnessedDeathGameTime() >= 0
                && now - data.getLastWitnessedDeathGameTime() <= WITNESSED_DEATH_WINDOW_TICKS;
        return MoodMath.computeTarget(hasBed, hasWorkstation, sleptRecently, wellFed, recentlyHurt, witnessedDeath);
    }
}
