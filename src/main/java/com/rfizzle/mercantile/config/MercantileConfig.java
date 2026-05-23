package com.rfizzle.mercantile.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MercantileConfig {
    private static volatile MercantileConfig INSTANCE;

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();

    // --- Server Config ---

    // Villager Pickup
    public boolean enableVillagerPickup = true;
    public int pickupXpCost = 5;

    // Villager Names
    public boolean enableNames = true;

    // Trade Cycling
    public boolean enableTradeCycling = true;
    public int tradeCycleEmeraldCost = 6;

    // Reputation
    public boolean enableReputation = true;
    public int reputationTradeGain = 1;
    public int reputationCureGain = 15;
    public int reputationAttackLoss = 10;
    public int reputationKillLoss = 25;
    public int reputationCycleGain = 2;

    // Follow Mode
    public boolean enableFollowMode = true;
    public int maxFollowingVillagers = 3;

    // Pathfinding
    public boolean enablePathfindingFixes = true;
    public boolean enablePathfindingDoors = true;
    public boolean enablePathfindingStairs = true;
    public boolean enablePathfindingLadders = true;
    public boolean enablePathfindingWater = true;

    // Bulk Trading
    public boolean enableBulkTrading = true;

    // Profession Lock
    public boolean enableProfessionLock = true;

    // Healing
    public boolean enableHealing = true;
    public float healingMultiplier = 2.0f;

    // Trade GUI
    public boolean enableRestockIndicator = true;
    public boolean enableDemandTransparency = true;

    // Sentry Pylon
    public boolean enableSentryPylon = true;
    public int pylonDetectionRadius = 32;
    public int pylonMaxFuel = 8;
    public int pylonMaxGolems = 3;
    public int sentryDespawnSeconds = 30;

    // --- Client Config ---

    public float villagerSoundVolume = 1.0f;
    public boolean enableWorkstationVis = true;
    public boolean enableBellRadiusVis = true;
    public boolean enableVillageBoundaryVis = true;
    public boolean enableInfoPanel = true;

    public void clamp() {
        pickupXpCost = Math.clamp(pickupXpCost, 0, Integer.MAX_VALUE);
        tradeCycleEmeraldCost = Math.clamp(tradeCycleEmeraldCost, 0, Integer.MAX_VALUE);
        reputationTradeGain = Math.clamp(reputationTradeGain, 0, Integer.MAX_VALUE);
        reputationCureGain = Math.clamp(reputationCureGain, 0, Integer.MAX_VALUE);
        reputationAttackLoss = Math.clamp(reputationAttackLoss, 0, Integer.MAX_VALUE);
        reputationKillLoss = Math.clamp(reputationKillLoss, 0, Integer.MAX_VALUE);
        reputationCycleGain = Math.clamp(reputationCycleGain, 0, Integer.MAX_VALUE);
        maxFollowingVillagers = Math.clamp(maxFollowingVillagers, 1, Integer.MAX_VALUE);
        healingMultiplier = Math.clamp(healingMultiplier, 1.0f, 10.0f);
        pylonDetectionRadius = Math.clamp(pylonDetectionRadius, 4, 128);
        pylonMaxFuel = Math.clamp(pylonMaxFuel, 1, Integer.MAX_VALUE);
        pylonMaxGolems = Math.clamp(pylonMaxGolems, 1, Integer.MAX_VALUE);
        sentryDespawnSeconds = Math.clamp(sentryDespawnSeconds, 5, Integer.MAX_VALUE);
        villagerSoundVolume = Math.clamp(villagerSoundVolume, 0.0f, 1.0f);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static MercantileConfig fromJson(String json) {
        MercantileConfig config = GSON.fromJson(json, MercantileConfig.class);
        if (config == null) return new MercantileConfig();
        config.clamp();
        return config;
    }

    public static MercantileConfig get() {
        MercantileConfig local = INSTANCE;
        if (local == null) {
            synchronized (MercantileConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = load();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    public static void reload() {
        synchronized (MercantileConfig.class) {
            INSTANCE = load();
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Mercantile.LOGGER.error("Failed to save config", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Mercantile.LOGGER.warn("Failed to delete orphan config tmp file {}", tmp, cleanup);
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("mercantile.json");
    }

    private static MercantileConfig load() {
        return load(configPath());
    }

    static MercantileConfig load(Path path) {
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                MercantileConfig config = GSON.fromJson(json, MercantileConfig.class);
                if (config == null) {
                    config = new MercantileConfig();
                }
                config.clamp();
                return config;
            } catch (Exception e) {
                Mercantile.LOGGER.error("Failed to load config, using defaults (corrupted file preserved at {})", path, e);
                return new MercantileConfig();
            }
        }
        MercantileConfig defaults = new MercantileConfig();
        defaults.save(path);
        return defaults;
    }
}
