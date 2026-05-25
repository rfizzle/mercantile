package com.rfizzle.mercantile.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MercantileConfigTest {
    private static final Gson GSON = MercantileConfig.GSON;

    @Test
    void defaultValues() {
        MercantileConfig config = new MercantileConfig();

        assertTrue(config.enableVillagerPickup);
        assertEquals(5, config.pickupXpCost);
        assertTrue(config.enableNames);
        assertTrue(config.enableTradeCycling);
        assertEquals(6, config.tradeCycleEmeraldCost);
        assertTrue(config.enableReputation);
        assertEquals(1, config.reputationTradeGain);
        assertEquals(5, config.reputationCureGain);
        assertEquals(15, config.reputationAttackLoss);
        assertEquals(40, config.reputationKillLoss);
        assertEquals(1, config.reputationCycleGain);
        assertEquals(5, config.reputationDailyCap);
        assertEquals(5, config.reputationTradesPerGain);
        assertEquals(2, config.reputationDailyMaxTradeRep);
        assertEquals(1, config.reputationDailyMaxCycleRep);
        assertTrue(config.enableFollowMode);
        assertEquals(3, config.maxFollowingVillagers);
        assertTrue(config.enablePathfindingFixes);
        assertTrue(config.enablePathfindingDoors);
        assertTrue(config.enablePathfindingStairs);
        assertTrue(config.enablePathfindingLadders);
        assertTrue(config.enablePathfindingWater);
        assertTrue(config.enableBulkTrading);
        assertTrue(config.enableProfessionLock);
        assertTrue(config.enableHealing);
        assertEquals(2.0f, config.healingMultiplier);
        assertTrue(config.enableRestockIndicator);
        assertTrue(config.enableDemandTransparency);
        assertTrue(config.enableSentryPylon);
        assertEquals(32, config.pylonDetectionRadius);
        assertEquals(8, config.pylonMaxFuel);
        assertEquals(3, config.pylonMaxGolems);
        assertEquals(30, config.sentryDespawnSeconds);
        assertEquals(1.0f, config.villagerSoundVolume);
        assertTrue(config.enableWorkstationVis);
        assertTrue(config.enableBellRadiusVis);
        assertTrue(config.enableVillageBoundaryVis);
        assertTrue(config.enableInfoPanel);
        assertTrue(config.enableReputationHud);
    }

    @Test
    void roundTrip() {
        MercantileConfig original = new MercantileConfig();
        original.enableVillagerPickup = false;
        original.pickupXpCost = 10;
        original.tradeCycleEmeraldCost = 12;
        original.reputationKillLoss = 50;
        original.enableHealing = false;
        original.healingMultiplier = 3.5f;
        original.villagerSoundVolume = 0.25f;
        original.enablePathfindingLadders = false;
        original.pylonDetectionRadius = 64;
        original.enableReputationHud = false;

        String json = GSON.toJson(original);
        MercantileConfig restored = GSON.fromJson(json, MercantileConfig.class);

        assertFalse(restored.enableVillagerPickup);
        assertEquals(10, restored.pickupXpCost);
        assertEquals(12, restored.tradeCycleEmeraldCost);
        assertEquals(50, restored.reputationKillLoss);
        assertFalse(restored.enableHealing);
        assertEquals(3.5f, restored.healingMultiplier);
        assertEquals(0.25f, restored.villagerSoundVolume);
        assertFalse(restored.enablePathfindingLadders);
        assertEquals(64, restored.pylonDetectionRadius);
        assertFalse(restored.enableReputationHud);
    }

    @Test
    void missingKeysGetDefaults() {
        String json = """
                {
                  "enableVillagerPickup": false,
                  "pickupXpCost": 99
                }
                """;

        MercantileConfig config = GSON.fromJson(json, MercantileConfig.class);

        assertFalse(config.enableVillagerPickup);
        assertEquals(99, config.pickupXpCost);

        // Everything else should be at defaults
        MercantileConfig defaults = new MercantileConfig();
        assertTrue(config.enableNames);
        assertEquals(defaults.tradeCycleEmeraldCost, config.tradeCycleEmeraldCost);
        assertEquals(defaults.reputationTradeGain, config.reputationTradeGain);
        assertEquals(defaults.healingMultiplier, config.healingMultiplier);
        assertEquals(defaults.villagerSoundVolume, config.villagerSoundVolume);
        assertEquals(defaults.pylonDetectionRadius, config.pylonDetectionRadius);
        assertEquals(defaults.maxFollowingVillagers, config.maxFollowingVillagers);
        assertEquals(defaults.sentryDespawnSeconds, config.sentryDespawnSeconds);
        assertTrue(config.enableInfoPanel);
    }

    @Test
    void unknownKeysIgnored() {
        String json = """
                {
                  "enableVillagerPickup": false,
                  "totallyFakeKey": 42,
                  "anotherUnknown": "hello",
                  "nestedUnknown": { "a": 1 }
                }
                """;

        MercantileConfig config = GSON.fromJson(json, MercantileConfig.class);

        assertFalse(config.enableVillagerPickup);
        assertEquals(new MercantileConfig().pickupXpCost, config.pickupXpCost);
    }

    @Test
    void emptyJsonReturnsDefaults() {
        MercantileConfig config = GSON.fromJson("{}", MercantileConfig.class);
        MercantileConfig defaults = new MercantileConfig();

        assertEquals(defaults.enableVillagerPickup, config.enableVillagerPickup);
        assertEquals(defaults.pickupXpCost, config.pickupXpCost);
        assertEquals(defaults.healingMultiplier, config.healingMultiplier);
        assertEquals(defaults.villagerSoundVolume, config.villagerSoundVolume);
    }

    @Test
    void nullJsonReturnsNull() {
        MercantileConfig config = GSON.fromJson("null", MercantileConfig.class);
        assertNull(config);
    }

    @Test
    void fileRoundTrip(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");

        MercantileConfig original = new MercantileConfig();
        original.enableVillagerPickup = false;
        original.pylonMaxGolems = 7;
        original.villagerSoundVolume = 0.0f;

        Files.writeString(configFile, GSON.toJson(original));

        MercantileConfig loaded = MercantileConfig.load(configFile);

        assertFalse(loaded.enableVillagerPickup);
        assertEquals(7, loaded.pylonMaxGolems);
        assertEquals(0.0f, loaded.villagerSoundVolume);
    }

    @Test
    void missingFileReturnsDefaults(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("nonexistent.json");

        MercantileConfig loaded = MercantileConfig.load(configFile);
        MercantileConfig defaults = new MercantileConfig();

        assertEquals(defaults.enableVillagerPickup, loaded.enableVillagerPickup);
        assertEquals(defaults.pickupXpCost, loaded.pickupXpCost);
        assertEquals(defaults.healingMultiplier, loaded.healingMultiplier);
    }

    @Test
    void corruptedFileReturnsDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, "this is not valid json {{{");

        MercantileConfig loaded = MercantileConfig.load(configFile);
        MercantileConfig defaults = new MercantileConfig();

        assertEquals(defaults.enableVillagerPickup, loaded.enableVillagerPickup);
        assertEquals(defaults.pickupXpCost, loaded.pickupXpCost);
    }

    @Test
    void healingMultiplierClampedBelowMinimum() {
        MercantileConfig config = new MercantileConfig();
        config.healingMultiplier = 0.5f;
        config.clamp();
        assertEquals(1.0f, config.healingMultiplier);
    }

    @Test
    void healingMultiplierClampedAboveMaximum() {
        MercantileConfig config = new MercantileConfig();
        config.healingMultiplier = 15.0f;
        config.clamp();
        assertEquals(10.0f, config.healingMultiplier);
    }

    @Test
    void healingMultiplierValidValueUnchanged() {
        MercantileConfig config = new MercantileConfig();
        config.healingMultiplier = 3.5f;
        config.clamp();
        assertEquals(3.5f, config.healingMultiplier);
    }

    @Test
    void healingMultiplierClampedOnLoad(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "healingMultiplier": 0.1
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1.0f, loaded.healingMultiplier);
    }

    @Test
    void healingMultiplierClampedOnLoadAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "healingMultiplier": 99.0
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(10.0f, loaded.healingMultiplier);
    }

    @Test
    void pickupXpCostClampedFromNegative(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "pickupXpCost": -5
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(0, loaded.pickupXpCost);
    }

    @Test
    void pylonDetectionRadiusClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "pylonDetectionRadius": 999999
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(128, loaded.pylonDetectionRadius);
    }

    @Test
    void pylonDetectionRadiusClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "pylonDetectionRadius": 0
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(4, loaded.pylonDetectionRadius);
    }

    @Test
    void maxFollowingVillagersClampedFromZero(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "maxFollowingVillagers": 0
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1, loaded.maxFollowingVillagers);
    }

    @Test
    void villagerSoundVolumeClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "villagerSoundVolume": 2.5
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1.0f, loaded.villagerSoundVolume);
    }

    @Test
    void villagerSoundVolumeClampedFromNegative(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "villagerSoundVolume": -1.0
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(0.0f, loaded.villagerSoundVolume);
    }

    @Test
    void reputationDailyCapClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyCap": 0
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1, loaded.reputationDailyCap);
    }

    @Test
    void reputationDailyCapClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyCap": 9999
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(50, loaded.reputationDailyCap);
    }

    @Test
    void reputationTradesPerGainClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationTradesPerGain": 0
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1, loaded.reputationTradesPerGain);
    }

    @Test
    void reputationTradesPerGainClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationTradesPerGain": 999
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(20, loaded.reputationTradesPerGain);
    }

    @Test
    void reputationDailyMaxTradeRepClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyMaxTradeRep": 0
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1, loaded.reputationDailyMaxTradeRep);
    }

    @Test
    void reputationDailyMaxTradeRepClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyMaxTradeRep": 99
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(10, loaded.reputationDailyMaxTradeRep);
    }

    @Test
    void reputationDailyMaxCycleRepClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyMaxCycleRep": 0
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(1, loaded.reputationDailyMaxCycleRep);
    }

    @Test
    void reputationDailyMaxCycleRepClampedAboveMax(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "reputationDailyMaxCycleRep": 99
                }
                """);
        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(10, loaded.reputationDailyMaxCycleRep);
    }

    @Test
    void sentryDespawnSecondsClampedBelowMin(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "sentryDespawnSeconds": 1
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        assertEquals(5, loaded.sentryDespawnSeconds);
    }

    @Test
    void firstLoadCreatesDefaultsFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        assertFalse(Files.exists(configFile));

        MercantileConfig loaded = MercantileConfig.load(configFile);

        assertTrue(Files.exists(configFile), "load() should create defaults file when missing");

        MercantileConfig defaults = new MercantileConfig();
        assertEquals(defaults.enableVillagerPickup, loaded.enableVillagerPickup);
        assertEquals(defaults.pickupXpCost, loaded.pickupXpCost);
        assertEquals(defaults.healingMultiplier, loaded.healingMultiplier);

        MercantileConfig roundTripped = MercantileConfig.load(configFile);
        assertEquals(defaults.pickupXpCost, roundTripped.pickupXpCost);
        assertEquals(defaults.tradeCycleEmeraldCost, roundTripped.tradeCycleEmeraldCost);
        assertEquals(defaults.healingMultiplier, roundTripped.healingMultiplier);
        assertEquals(defaults.pylonDetectionRadius, roundTripped.pylonDetectionRadius);
    }

    @Test
    void saveIsAtomic(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, "{ \"pickupXpCost\": 1 }");

        MercantileConfig config = new MercantileConfig();
        config.pickupXpCost = 42;
        config.save(configFile);

        String contents = Files.readString(configFile);
        assertTrue(contents.contains("\"pickupXpCost\": 42"), "file should reflect new value, got: " + contents);

        Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        assertFalse(Files.exists(tmp), "atomic save should leave no orphan .tmp sibling");
    }

    @Test
    void corruptedFileDoesNotOverwriteUserFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        String corrupted = "not json {{{";
        Files.writeString(configFile, corrupted);

        MercantileConfig loaded = MercantileConfig.load(configFile);
        MercantileConfig defaults = new MercantileConfig();

        assertEquals(defaults.pickupXpCost, loaded.pickupXpCost);
        assertEquals(defaults.healingMultiplier, loaded.healingMultiplier);

        assertEquals(corrupted, Files.readString(configFile),
                "corrupted user file must be preserved for inspection, not overwritten with defaults");
    }

    @Test
    void partialFilePreservesDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "enableSentryPylon": false,
                  "pylonMaxFuel": 16
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);

        assertFalse(loaded.enableSentryPylon);
        assertEquals(16, loaded.pylonMaxFuel);

        MercantileConfig defaults = new MercantileConfig();
        assertTrue(loaded.enableVillagerPickup);
        assertEquals(defaults.tradeCycleEmeraldCost, loaded.tradeCycleEmeraldCost);
        assertEquals(defaults.reputationTradeGain, loaded.reputationTradeGain);
    }

    @Test
    void enableReputationHudPartialFilePreservesDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "pickupXpCost": 7
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);

        assertEquals(7, loaded.pickupXpCost);
        assertTrue(loaded.enableReputationHud,
                "enableReputationHud should default to true when not in file");
    }

    @Test
    void enableReputationHudExplicitFalseRoundTripsThroughFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mercantile.json");
        Files.writeString(configFile, """
                {
                  "enableReputationHud": false
                }
                """);

        MercantileConfig loaded = MercantileConfig.load(configFile);

        assertFalse(loaded.enableReputationHud,
                "explicit false must survive file-load + clamp pipeline");
    }
}
