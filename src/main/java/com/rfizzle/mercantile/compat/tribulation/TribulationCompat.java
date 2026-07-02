package com.rfizzle.mercantile.compat.tribulation;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.tribulation.api.TribulationAPI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft integration with Tribulation: when it is loaded, the Sentry Pylon reads the local threat
 * tier and scales its golem cap and detection radius upward so village defense keeps pace with
 * Tribulation's harder raids. Without Tribulation every accessor returns the configured defaults,
 * and any API failure falls back to them too — a misbehaving integration never crashes the host
 * (Concord API Standard). All {@code TribulationAPI} references live in the nested {@link Api}
 * holder, which is only class-loaded once the {@code isModLoaded} guard has passed.
 *
 * <p>Tier resolution: Tribulation has no position-only level accessor, so the pylon derives its
 * local threat from the nearest survival-mode player within its configured detection radius via
 * {@link TribulationAPI#getEffectiveLevel}, mapped to a tier against
 * {@link TribulationAPI#getTierThresholds} (inclusive thresholds, tiers 0–5). No player in range
 * means no scaling.
 */
public final class TribulationCompat {

    /** The pylon limits in effect for one scan cycle. */
    public record EffectivePylonLimits(int maxGolems, int detectionRadius) {}

    private static final String MOD_ID = "tribulation";
    /** Mirror of the config clamp ceiling on {@code pylonDetectionRadius}. */
    static final int MAX_DETECTION_RADIUS = 128;

    private static final AtomicBoolean CALL_FAILURE_LOGGED = new AtomicBoolean(false);

    private TribulationCompat() {}

    /**
     * Resolve the pylon limits to use for a scan cycle at {@code pos}. Returns the configured
     * defaults when Tribulation is absent, no player is within the configured detection radius,
     * or the API call fails.
     */
    public static EffectivePylonLimits effectiveLimits(ServerLevel level, BlockPos pos, MercantileConfig config) {
        EffectivePylonLimits base = new EffectivePylonLimits(config.pylonMaxGolems, config.pylonDetectionRadius);
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return base;
        }
        try {
            // Ignore creative/spectator players: a spectating or flying-past admin must not drive
            // the pylon's threat tier.
            Player nearest = level.getNearestPlayer(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    config.pylonDetectionRadius, true);
            if (!(nearest instanceof ServerPlayer player)) {
                return base;
            }
            return scaledLimits(tierFor(Api.effectiveLevel(player), Api.tierThresholds()), config);
        } catch (Throwable t) {
            if (CALL_FAILURE_LOGGED.compareAndSet(false, true)) {
                Mercantile.LOGGER.warn("Tribulation API call failed; sentry pylon using configured defaults", t);
            }
            return base;
        }
    }

    /**
     * Map a Tribulation level to a tier by counting the thresholds it meets. The check is
     * inclusive, matching Tribulation's own tier semantics: a player at exactly the tier-1
     * threshold is tier 1.
     */
    static int tierFor(int level, int[] thresholds) {
        int tier = 0;
        for (int threshold : thresholds) {
            if (level >= threshold) {
                tier++;
            }
        }
        return tier;
    }

    /**
     * Pure per-tier scaling math: each tier adds the configured golem and radius bonuses on top
     * of the configured base values. The golem count is hard-capped at
     * {@code pylonTribulationMaxGolems} (never below the un-integrated base, which stays the
     * floor) and the radius at {@link #MAX_DETECTION_RADIUS}.
     */
    static EffectivePylonLimits scaledLimits(int tier, MercantileConfig config) {
        int golemCap = Math.max(config.pylonMaxGolems, config.pylonTribulationMaxGolems);
        int maxGolems = Math.clamp(
                (long) config.pylonMaxGolems + (long) tier * config.pylonTribulationGolemBonusPerTier,
                config.pylonMaxGolems, golemCap);
        int detectionRadius = Math.clamp(
                (long) config.pylonDetectionRadius + (long) tier * config.pylonTribulationRadiusBonusPerTier,
                config.pylonDetectionRadius, MAX_DETECTION_RADIUS);
        return new EffectivePylonLimits(maxGolems, detectionRadius);
    }

    /**
     * The only class that touches {@code TribulationAPI}. Kept nested so the JVM never resolves
     * the Tribulation classes unless the {@code isModLoaded} guard in
     * {@link #effectiveLimits} has already passed.
     */
    private static final class Api {
        private Api() {}

        static int effectiveLevel(ServerPlayer player) {
            return TribulationAPI.getEffectiveLevel(player);
        }

        static int[] tierThresholds() {
            return TribulationAPI.getTierThresholds();
        }
    }
}
