package com.rfizzle.mercantile.memorial;

import com.rfizzle.mercantile.block.SentryPylonScanner;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.FearEntry;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

/**
 * Temporary per-player, per-village price markup after a killing spree. A village is keyed
 * by its nearest bell POI — the same locality primitive the pylon alarm and the vanilla
 * gathering radius use. Kills without a bell in range belong to no village and are never
 * feared; the markup decays linearly to zero over {@code fearMarkupDurationDays} and is
 * evaluated lazily at trade open, so there is no tick loop.
 */
public final class FearManager {

    /** Bell POI search radius in blocks — matches the vanilla bell gathering radius. */
    public static final int VILLAGE_BELL_RADIUS = 48;
    /** Upper bound on kill timestamps kept per village entry. */
    public static final int MAX_TRACKED_KILLS = 32;

    private FearManager() {
    }

    /**
     * Records a villager kill against the killer; activates or refreshes the village's fear.
     * The kill is recorded under <em>every</em> bell in range, not just the nearest — a decoy
     * bell planted at the kill site must not shield the real village's key.
     */
    public static void recordKill(ServerLevel level, ServerPlayer killer, BlockPos deathPos) {
        MercantileConfig config = MercantileConfig.get();
        List<BlockPos> bells = bellsInRange(level, deathPos, VILLAGE_BELL_RADIUS);
        if (bells.isEmpty()) return;

        PlayerData data = killer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        long now = level.getGameTime();
        long windowTicks = config.fearKillWindowMinutes * FearMath.TICKS_PER_MINUTE;
        for (BlockPos bell : bells) {
            FearEntry entry = data.getOrCreateFearEntry(villageKey(level, bell));
            List<Long> kills = FearMath.recordKill(entry.getRecentKillTimes(), now, windowTicks, MAX_TRACKED_KILLS);
            entry.setRecentKillTimes(kills);
            if (FearMath.thresholdReached(kills, config.fearKillThreshold)) {
                // Every kill at or past the threshold restarts the decay clock.
                entry.setFearStartGameTime(now);
            }
        }
    }

    /**
     * Current fear strength in {@code [0, 1]} this player faces at this villager's village.
     * Computed once per trade open and shared across the offer loop, so the bell POI lookup
     * runs once, not per offer. Expired entries are pruned here to keep the map bounded.
     */
    public static double fearFraction(Villager villager, ServerPlayer player, MercantileConfig config) {
        if (!config.enableFearMarkup) return 0.0;
        if (!(villager.level() instanceof ServerLevel level)) return 0.0;

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        if (!data.hasFearEntries()) return 0.0;

        Optional<BlockPos> bell = SentryPylonScanner.findNearestBell(
                level, villager.blockPosition(), VILLAGE_BELL_RADIUS);
        if (bell.isEmpty()) return 0.0;

        String key = villageKey(level, bell.get());
        FearEntry entry = data.getFearEntry(key);
        if (entry == null) return 0.0;

        long now = level.getGameTime();
        long durationTicks = config.fearMarkupDurationDays * FearMath.TICKS_PER_DAY;
        double fraction = FearMath.fraction(entry.getFearStartGameTime(), now, durationTicks);
        if (fraction <= 0.0
                && entry.isStale(now, config.fearKillWindowMinutes * FearMath.TICKS_PER_MINUTE, durationTicks)) {
            data.removeFearEntry(key);
        }
        return fraction;
    }

    /** Fear price markup for one offer; 0 when no fear is active. */
    public static int priceModifier(int basePrice, double fearFraction, MercantileConfig config) {
        return FearMath.markup(basePrice, config.fearMarkupPercent, fearFraction);
    }

    private static List<BlockPos> bellsInRange(ServerLevel level, BlockPos center, int radius) {
        return level.getPoiManager().getInRange(holder ->
                        holder.value().matchingStates().stream().anyMatch(s -> s.is(Blocks.BELL)),
                center, radius, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .toList();
    }

    private static String villageKey(ServerLevel level, BlockPos bell) {
        return level.dimension().location() + "@" + bell.getX() + "," + bell.getY() + "," + bell.getZ();
    }
}
