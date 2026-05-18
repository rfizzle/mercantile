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
        assertEquals(15, config.reputationCureGain);
        assertEquals(10, config.reputationAttackLoss);
        assertEquals(25, config.reputationKillLoss);
        assertEquals(2, config.reputationCycleGain);
        assertTrue(config.enableFollowMode);
        assertEquals(3, config.maxFollowingVillagers);
        assertTrue(config.enablePathfindingFixes);
        assertTrue(config.enablePathfindingDoors);
        assertTrue(config.enablePathfindingStairs);
        assertTrue(config.enablePathfindingLadders);
        assertTrue(config.enablePathfindingWater);
        assertTrue(config.enableBulkTrading);
        assertTrue(config.enableProfessionLock);
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
    }

    @Test
    void roundTrip() {
        MercantileConfig original = new MercantileConfig();
        original.enableVillagerPickup = false;
        original.pickupXpCost = 10;
        original.tradeCycleEmeraldCost = 12;
        original.reputationKillLoss = 50;
        original.healingMultiplier = 3.5f;
        original.villagerSoundVolume = 0.25f;
        original.enablePathfindingLadders = false;
        original.pylonDetectionRadius = 64;

        String json = GSON.toJson(original);
        MercantileConfig restored = GSON.fromJson(json, MercantileConfig.class);

        assertFalse(restored.enableVillagerPickup);
        assertEquals(10, restored.pickupXpCost);
        assertEquals(12, restored.tradeCycleEmeraldCost);
        assertEquals(50, restored.reputationKillLoss);
        assertEquals(3.5f, restored.healingMultiplier);
        assertEquals(0.25f, restored.villagerSoundVolume);
        assertFalse(restored.enablePathfindingLadders);
        assertEquals(64, restored.pylonDetectionRadius);
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
}
