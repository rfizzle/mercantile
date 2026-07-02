package com.rfizzle.mercantile.compat.tribulation;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft integration with Tribulation: when it is loaded, the Sentry Pylon reads the local threat
 * tier and scales its golem cap and detection radius upward so village defense keeps pace with
 * Tribulation's harder raids. Without Tribulation every accessor returns the configured defaults.
 *
 * <p>Tribulation publishes no maven artifact, so its {@code api} package is consumed by
 * reflection — each target method is resolved once into a cached {@link MethodHandle} and any
 * failure (mod absent, older API, a throwing call) falls back to the configured defaults. A
 * misbehaving integration never crashes the host (Concord API Standard).
 *
 * <p>Tier resolution: Tribulation has no position-only level accessor, so the pylon derives its
 * local threat from the nearest player within its configured detection radius via
 * {@code TribulationAPI.getEffectiveLevel(Entity)}, mapped to a tier against
 * {@code TribulationAPI.getTierThresholds()} (inclusive thresholds, tiers 0–5). No player in
 * range means no scaling.
 */
public final class TribulationCompat {

    /** The pylon limits in effect for one scan cycle. */
    public record EffectivePylonLimits(int maxGolems, int detectionRadius) {}

    private static final String MOD_ID = "tribulation";
    private static final String API_CLASS = "com.rfizzle.tribulation.api.TribulationAPI";
    /** Mirror of the config clamp ceiling on {@code pylonDetectionRadius}. */
    static final int MAX_DETECTION_RADIUS = 128;

    private TribulationCompat() {}

    /**
     * Resolve the pylon limits to use for a scan cycle at {@code pos}. Returns the configured
     * defaults when Tribulation is absent, its API is unavailable, no player is within the
     * configured detection radius, or the API call fails.
     */
    public static EffectivePylonLimits effectiveLimits(ServerLevel level, BlockPos pos, MercantileConfig config) {
        EffectivePylonLimits base = new EffectivePylonLimits(config.pylonMaxGolems, config.pylonDetectionRadius);
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return base;
        }
        MethodHandle effectiveLevel = EFFECTIVE_LEVEL.resolve();
        MethodHandle tierThresholds = TIER_THRESHOLDS.resolve();
        if (effectiveLevel == null || tierThresholds == null) {
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
            int threatLevel = (int) effectiveLevel.invokeExact((Entity) player);
            int[] thresholds = (int[]) tierThresholds.invokeExact();
            return scaledLimits(tierFor(threatLevel, thresholds), config);
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

    private static final AtomicBoolean CALL_FAILURE_LOGGED = new AtomicBoolean(false);

    private static final ApiAccessor EFFECTIVE_LEVEL =
            new ApiAccessor("getEffectiveLevel", MethodType.methodType(int.class, Entity.class));
    private static final ApiAccessor TIER_THRESHOLDS =
            new ApiAccessor("getTierThresholds", MethodType.methodType(int[].class));

    /**
     * Resolve-once, memoized handle to a static {@code TribulationAPI} method. The first
     * resolution failure is logged; thereafter {@code null} is returned silently so the per-scan
     * hot path never re-pays reflection cost.
     */
    private static final class ApiAccessor {
        private final String methodName;
        private final MethodType type;
        private final AtomicBoolean logged = new AtomicBoolean(false);
        private volatile boolean resolved;
        private volatile MethodHandle handle;

        ApiAccessor(String methodName, MethodType type) {
            this.methodName = methodName;
            this.type = type;
        }

        MethodHandle resolve() {
            if (resolved) {
                return handle;
            }
            synchronized (this) {
                if (resolved) {
                    return handle;
                }
                MethodHandle resolvedHandle = null;
                try {
                    Class<?> api = Class.forName(API_CLASS);
                    resolvedHandle = MethodHandles.publicLookup().findStatic(api, methodName, type);
                } catch (Throwable t) {
                    if (logged.compareAndSet(false, true)) {
                        Mercantile.LOGGER.warn("Tribulation accessor {}.{} unavailable; sentry pylon using configured defaults",
                                API_CLASS, methodName, t);
                    }
                }
                handle = resolvedHandle;
                resolved = true;
                return handle;
            }
        }
    }
}
