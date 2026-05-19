package com.rfizzle.mercantile.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MercantileConfig {
    private static MercantileConfig INSTANCE;

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

    public String toJson() {
        return GSON.toJson(this);
    }

    public static MercantileConfig fromJson(String json) {
        MercantileConfig config = GSON.fromJson(json, MercantileConfig.class);
        return config != null ? config : new MercantileConfig();
    }

    public static MercantileConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        try {
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            Mercantile.LOGGER.error("Failed to save config", e);
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
                return config;
            } catch (Exception e) {
                Mercantile.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        return new MercantileConfig();
    }
}
