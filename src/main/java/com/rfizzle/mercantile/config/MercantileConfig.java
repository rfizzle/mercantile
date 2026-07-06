package com.rfizzle.mercantile.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    // Schema version of the on-disk file. Bumped by ConfigMigrator when the shape changes; a
    // freshly constructed config is already current. Not player-tunable — leave it out of clamp().
    public int configVersion = ConfigMigrator.CURRENT_VERSION;

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
    public boolean enableWanderingTraderRep = true;
    public int reputationTradeGain = 1;
    public int reputationCureGain = 5;
    public int reputationAttackLoss = 15;
    public int reputationKillLoss = 40;
    public int reputationCycleGain = 1;
    public int reputationDailyCap = 5;
    public int reputationTradesPerGain = 5;
    public int reputationDailyMaxTradeRep = 2;
    public int reputationDailyMaxCycleRep = 1;
    public boolean enableRaidReputation = true;
    public int reputationRaidWinGain = 10;

    // Gifting
    public boolean enableGifting = true;
    public int reputationGiftGain = 1;
    public int reputationDailyMaxGiftRep = 2;
    public int reputationNegativeDecayPerDay = 1;

    // Gratitude Gifts
    public boolean enableGratitudeGifts = true;
    public int gratitudeGiftsPerDay = 1;

    // Follow Mode
    public boolean enableFollowMode = true;
    public int maxFollowingVillagers = 3;
    public boolean enableSendHome = true;

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

    // Breeding Tooltip (Jade/WTHIT)
    public boolean enableBreedingTooltip = true;

    // Baby Feeding
    public boolean enableBabyFeeding = true;
    public int babyFeedPercentPerFeed = 10;
    public int babyFeedMaxReductionPercent = 50;

    // State Indicators (Jade/WTHIT)
    public boolean enableStateIndicators = true;

    // Sentry Pylon
    public boolean enableSentryPylon = true;
    public boolean enablePylonBellAlarm = true;
    public int pylonDetectionRadius = 32;
    public int pylonMaxFuel = 8;
    public int pylonMaxGolems = 3;
    public int sentryDespawnSeconds = 30;
    // Sentry Pylon × Tribulation scaling — only active when Tribulation is installed.
    public int pylonTribulationGolemBonusPerTier = 1;
    public int pylonTribulationRadiusBonusPerTier = 4;
    public int pylonTribulationMaxGolems = 6;

    // Mood
    public boolean enableMood = true;
    public int moodPriceModifierPercent = 5;
    public int moodRestockSpeedPercent = 20;
    public int moodRecalcIntervalTicks = 100;
    public boolean moodAmbientParticles = true;

    // Market Day
    public boolean enableMarketDay = true;
    public int marketDayIntervalDays = 7;
    public int marketDayDiscountPercent = 5;

    // Nitwit Rehabilitation
    public boolean enableNitwitRehab = true;
    public int nitwitRehabEmeraldCost = 16;

    // Work Orders
    public boolean enableWorkOrders = true;
    public int workOrderEmeraldCost = 1;

    // Memorials & Mourning
    public boolean enableMemorials = true;
    public boolean enableMourning = true;

    // Fear Markup
    public boolean enableFearMarkup = true;
    public int fearKillThreshold = 3;
    public int fearKillWindowMinutes = 10;
    public int fearMarkupPercent = 25;
    public int fearMarkupDurationDays = 3;

    // Trade Pinning
    public boolean enableTradePinning = true;
    public int maxPinnedTradesPerPlayer = 10;
    public int pinRestockNotifyRange = 128;

    // Delivery Contracts (gated behind enableReputation)
    public boolean enableContracts = true;
    public int contractOfferChance = 50;
    public int contractPaymentScale = 100;
    public int contractRepGain = 3;
    public int contractRepPerDay = 3;
    public int contractDeadlineDays = 2;

    // --- Client Config ---

    public float villagerSoundVolume = 1.0f;
    public boolean enableWorkstationVis = true;
    public boolean enableBellRadiusVis = true;
    public boolean enableInfoPanel = true;
    public boolean enableReputationHud = true;
    // Client-side chat notice when the player's reputation tier changes.
    public boolean enableTierChangeMessages = true;
    // Reputation HUD placement per Concord HUD-STANDARD §4: corner anchor plus
    // pixel offsets measured inward from the anchored edges.
    public Anchor hudAnchor = Anchor.TOP_LEFT;
    public int hudOffsetX = 4;
    public int hudOffsetY = 4;

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public void clamp() {
        pickupXpCost = clampInt("pickupXpCost", pickupXpCost, 0, Integer.MAX_VALUE);
        tradeCycleEmeraldCost = clampInt("tradeCycleEmeraldCost", tradeCycleEmeraldCost, 0, Integer.MAX_VALUE);
        reputationTradeGain = clampInt("reputationTradeGain", reputationTradeGain, 0, Integer.MAX_VALUE);
        reputationCureGain = clampInt("reputationCureGain", reputationCureGain, 0, Integer.MAX_VALUE);
        reputationAttackLoss = clampInt("reputationAttackLoss", reputationAttackLoss, 0, Integer.MAX_VALUE);
        reputationKillLoss = clampInt("reputationKillLoss", reputationKillLoss, 0, Integer.MAX_VALUE);
        reputationCycleGain = clampInt("reputationCycleGain", reputationCycleGain, 0, Integer.MAX_VALUE);
        reputationRaidWinGain = clampInt("reputationRaidWinGain", reputationRaidWinGain, 0, Integer.MAX_VALUE);
        reputationDailyCap = clampInt("reputationDailyCap", reputationDailyCap, 1, 50);
        reputationTradesPerGain = clampInt("reputationTradesPerGain", reputationTradesPerGain, 1, 20);
        reputationDailyMaxTradeRep = clampInt("reputationDailyMaxTradeRep", reputationDailyMaxTradeRep, 1, 10);
        reputationDailyMaxCycleRep = clampInt("reputationDailyMaxCycleRep", reputationDailyMaxCycleRep, 1, 10);
        reputationGiftGain = clampInt("reputationGiftGain", reputationGiftGain, 0, Integer.MAX_VALUE);
        reputationDailyMaxGiftRep = clampInt("reputationDailyMaxGiftRep", reputationDailyMaxGiftRep, 1, 10);
        reputationNegativeDecayPerDay = clampInt("reputationNegativeDecayPerDay", reputationNegativeDecayPerDay, 0, Integer.MAX_VALUE);
        gratitudeGiftsPerDay = clampInt("gratitudeGiftsPerDay", gratitudeGiftsPerDay, 0, 10);
        maxFollowingVillagers = clampInt("maxFollowingVillagers", maxFollowingVillagers, 1, Integer.MAX_VALUE);
        healingMultiplier = clampFloat("healingMultiplier", healingMultiplier, 1.0f, 10.0f);
        babyFeedPercentPerFeed = clampInt("babyFeedPercentPerFeed", babyFeedPercentPerFeed, 1, 100);
        babyFeedMaxReductionPercent = clampInt("babyFeedMaxReductionPercent", babyFeedMaxReductionPercent, 0, 100);
        pylonDetectionRadius = clampInt("pylonDetectionRadius", pylonDetectionRadius, 4, 128);
        pylonMaxFuel = clampInt("pylonMaxFuel", pylonMaxFuel, 1, Integer.MAX_VALUE);
        pylonMaxGolems = clampInt("pylonMaxGolems", pylonMaxGolems, 1, Integer.MAX_VALUE);
        sentryDespawnSeconds = clampInt("sentryDespawnSeconds", sentryDespawnSeconds, 5, Integer.MAX_VALUE);
        pylonTribulationGolemBonusPerTier = clampInt("pylonTribulationGolemBonusPerTier", pylonTribulationGolemBonusPerTier, 0, Integer.MAX_VALUE);
        pylonTribulationRadiusBonusPerTier = clampInt("pylonTribulationRadiusBonusPerTier", pylonTribulationRadiusBonusPerTier, 0, Integer.MAX_VALUE);
        // The Tribulation cap can never sit below the un-integrated golem cap it extends.
        if (pylonTribulationMaxGolems < pylonMaxGolems) {
            Mercantile.LOGGER.warn("Config 'pylonTribulationMaxGolems' value {} is below pylonMaxGolems {}; raised to {}",
                    pylonTribulationMaxGolems, pylonMaxGolems, pylonMaxGolems);
            pylonTribulationMaxGolems = pylonMaxGolems;
        }
        moodPriceModifierPercent = clampInt("moodPriceModifierPercent", moodPriceModifierPercent, 0, 50);
        moodRestockSpeedPercent = clampInt("moodRestockSpeedPercent", moodRestockSpeedPercent, 0, 80);
        moodRecalcIntervalTicks = clampInt("moodRecalcIntervalTicks", moodRecalcIntervalTicks, 20, 24_000);
        marketDayIntervalDays = clampInt("marketDayIntervalDays", marketDayIntervalDays, 1, 1_000);
        marketDayDiscountPercent = clampInt("marketDayDiscountPercent", marketDayDiscountPercent, 0, 100);
        nitwitRehabEmeraldCost = clampInt("nitwitRehabEmeraldCost", nitwitRehabEmeraldCost, 0, Integer.MAX_VALUE);
        workOrderEmeraldCost = clampInt("workOrderEmeraldCost", workOrderEmeraldCost, 0, Integer.MAX_VALUE);
        fearKillThreshold = clampInt("fearKillThreshold", fearKillThreshold, 1, 20);
        fearKillWindowMinutes = clampInt("fearKillWindowMinutes", fearKillWindowMinutes, 1, 120);
        fearMarkupPercent = clampInt("fearMarkupPercent", fearMarkupPercent, 0, 200);
        fearMarkupDurationDays = clampInt("fearMarkupDurationDays", fearMarkupDurationDays, 1, 30);
        // Upper bound matches PlayerData.MAX_PINNED_TRADES so the configurable cap can never
        // exceed the persisted hard bound.
        maxPinnedTradesPerPlayer = clampInt("maxPinnedTradesPerPlayer", maxPinnedTradesPerPlayer, 1, 64);
        pinRestockNotifyRange = clampInt("pinRestockNotifyRange", pinRestockNotifyRange, 8, 256);
        contractOfferChance = clampInt("contractOfferChance", contractOfferChance, 0, 100);
        contractPaymentScale = clampInt("contractPaymentScale", contractPaymentScale, 0, 1_000);
        contractRepGain = clampInt("contractRepGain", contractRepGain, 0, Integer.MAX_VALUE);
        contractRepPerDay = clampInt("contractRepPerDay", contractRepPerDay, 0, 50);
        contractDeadlineDays = clampInt("contractDeadlineDays", contractDeadlineDays, 1, 30);
        villagerSoundVolume = clampFloat("villagerSoundVolume", villagerSoundVolume, 0.0f, 1.0f);
        // Gson leaves enum fields null on unknown/missing values.
        if (hudAnchor == null) hudAnchor = Anchor.TOP_LEFT;
        hudOffsetX = clampInt("hudOffsetX", hudOffsetX, 0, 10_000);
        hudOffsetY = clampInt("hudOffsetY", hudOffsetY, 0, 10_000);
    }

    /**
     * Clamp {@code value} into {@code [min, max]}, logging a warning when the
     * hand-edited value was actually out of range (warn-and-clamp — a player
     * can see exactly which field their edit overrode).
     */
    private static int clampInt(String name, int value, int min, int max) {
        int clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Mercantile.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
    }

    /** Float counterpart of {@link #clampInt}. */
    private static float clampFloat(String name, float value, float min, float max) {
        float clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Mercantile.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
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
        if (!Files.exists(path)) {
            MercantileConfig defaults = new MercantileConfig();
            defaults.save(path);
            return defaults;
        }
        try {
            // Migrate the raw JSON before Gson so renamed/restructured fields survive the upgrade,
            // then deserialize, clamp, and persist the upgraded schema back to disk.
            String json = Files.readString(path);
            JsonElement element = JsonParser.parseString(json);
            if (element == null || !element.isJsonObject()) {
                Mercantile.LOGGER.error("Config at {} is not a JSON object; using defaults (existing file left untouched)", path);
                return new MercantileConfig();
            }
            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);

            MercantileConfig config = GSON.fromJson(raw, MercantileConfig.class);
            if (config == null) {
                config = new MercantileConfig();
            }
            config.clamp();
            if (migrated) {
                config.save(path);
            }
            return config;
        } catch (Exception e) {
            Mercantile.LOGGER.error("Failed to load config, using defaults (corrupted file preserved at {})", path, e);
            return new MercantileConfig();
        }
    }
}
