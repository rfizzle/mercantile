package com.rfizzle.mercantile.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataCodecTest {
    @Test
    void defaultValues() {
        PlayerData data = new PlayerData();
        assertEquals(0, data.getScore());
        assertEquals(0, data.getProximityTicks());
        assertTrue(data.getCuredVillagers().isEmpty());
        assertTrue(data.getTradeStats().isEmpty());
    }

    @Test
    void roundTrip() {
        PlayerData original = new PlayerData();
        original.setScore(42);
        original.setProximityTicks(6000);

        UUID cured1 = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        UUID cured2 = UUID.fromString("abcdefab-abcd-abcd-abcd-abcdefabcdef");
        original.addCuredVillager(cured1);
        original.addCuredVillager(cured2);

        UUID trader = UUID.fromString("11111111-1111-1111-1111-111111111111");
        original.incrementTradesWithVillager(trader);
        original.incrementTradesWithVillager(trader);

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(42, decoded.getScore());
        assertEquals(6000, decoded.getProximityTicks());
        assertEquals(2, decoded.getCuredVillagers().size());
        assertTrue(decoded.hasCuredVillager(cured1));
        assertTrue(decoded.hasCuredVillager(cured2));
        assertEquals(2, decoded.getTradesWithVillager(trader));
    }

    @Test
    void missingKeysGetDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("score", 50);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(50, decoded.getScore());
        assertEquals(0, decoded.getProximityTicks());
        assertTrue(decoded.getCuredVillagers().isEmpty());
        assertTrue(decoded.getTradeStats().isEmpty());
    }

    @Test
    void emptyJsonUsesDefaults() {
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        assertEquals(0, decoded.getScore());
        assertEquals(0, decoded.getProximityTicks());
        assertTrue(decoded.getCuredVillagers().isEmpty());
        assertTrue(decoded.getTradeStats().isEmpty());
    }

    @Test
    void negativeScoreRoundTrips() {
        PlayerData original = new PlayerData();
        original.setScore(-75);

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(-75, decoded.getScore());
    }

    @Test
    void constructorWithValues() {
        UUID cured = UUID.randomUUID();
        UUID trader = UUID.randomUUID();
        PlayerData data = PlayerData.builder()
                .score(100)
                .proximityTicks(3000)
                .curedVillagers(Set.of(cured))
                .tradeStats(Map.of(trader, 5))
                .build();

        assertEquals(100, data.getScore());
        assertEquals(3000, data.getProximityTicks());
        assertTrue(data.hasCuredVillager(cured));
        assertEquals(5, data.getTradesWithVillager(trader));
    }

    @Test
    void curedVillagerTracking() {
        PlayerData data = new PlayerData();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        assertFalse(data.hasCuredVillager(v1));
        assertTrue(data.addCuredVillager(v1));
        assertTrue(data.hasCuredVillager(v1));
        assertFalse(data.addCuredVillager(v1));
        assertTrue(data.addCuredVillager(v2));
        assertEquals(2, data.getCuredVillagers().size());
    }

    @Test
    void tradeStatsTracking() {
        PlayerData data = new PlayerData();
        UUID v1 = UUID.randomUUID();

        assertEquals(0, data.getTradesWithVillager(v1));
        data.incrementTradesWithVillager(v1);
        assertEquals(1, data.getTradesWithVillager(v1));
        data.incrementTradesWithVillager(v1);
        data.incrementTradesWithVillager(v1);
        assertEquals(3, data.getTradesWithVillager(v1));
    }

    @Test
    void unmodifiableViews() {
        PlayerData data = new PlayerData();
        assertThrows(UnsupportedOperationException.class,
                () -> data.getCuredVillagers().add(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class,
                () -> data.getTradeStats().put(UUID.randomUUID(), 1));
    }

    @Test
    void curedVillagersCappedAtMax() {
        PlayerData data = new PlayerData();
        for (int i = 0; i < PlayerData.MAX_CURED_VILLAGERS + 100; i++) {
            data.addCuredVillager(UUID.randomUUID());
        }
        assertEquals(PlayerData.MAX_CURED_VILLAGERS, data.getCuredVillagers().size(),
                "curedVillagers must not exceed MAX_CURED_VILLAGERS");
    }

    @Test
    void curedVillagersCapSurvivesRoundTrip() {
        PlayerData original = new PlayerData();
        List<UUID> inserted = new ArrayList<>();
        for (int i = 0; i < PlayerData.MAX_CURED_VILLAGERS; i++) {
            UUID id = new UUID(0L, i); // deterministic, ordered
            original.addCuredVillager(id);
            inserted.add(id);
        }
        assertEquals(PlayerData.MAX_CURED_VILLAGERS, original.getCuredVillagers().size());

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(PlayerData.MAX_CURED_VILLAGERS, decoded.getCuredVillagers().size(),
                "cap must be preserved after codec round-trip");

        // FIFO eviction must survive the round-trip: the first-inserted UUID must be evicted
        UUID overflow = new UUID(1L, 0L);
        decoded.addCuredVillager(overflow);
        assertEquals(PlayerData.MAX_CURED_VILLAGERS, decoded.getCuredVillagers().size(),
                "adding beyond cap after round-trip must evict oldest");
        assertFalse(decoded.hasCuredVillager(inserted.get(0)),
                "FIFO eviction must remove the first-inserted entry after round-trip");
        assertTrue(decoded.hasCuredVillager(inserted.get(PlayerData.MAX_CURED_VILLAGERS - 1)),
                "most-recently-inserted entry must survive eviction");
        assertTrue(decoded.hasCuredVillager(overflow),
                "newly added entry must be present");
    }

    @Test
    void curedVillagersConstructorClampsOversizedInput() {
        Set<UUID> oversized = new LinkedHashSet<>();
        for (int i = 0; i < PlayerData.MAX_CURED_VILLAGERS + 50; i++) {
            oversized.add(UUID.randomUUID());
        }

        PlayerData data = PlayerData.builder()
                .curedVillagers(oversized)
                .build();
        assertEquals(PlayerData.MAX_CURED_VILLAGERS, data.getCuredVillagers().size(),
                "constructor must clamp oversized curedVillagers input to MAX_CURED_VILLAGERS");
    }

    @Test
    void scoreOutOfBoundsClampedOnDeserialization() {
        JsonObject high = new JsonObject();
        high.addProperty("score", 99999);
        PlayerData decodedHigh = PlayerData.CODEC.parse(JsonOps.INSTANCE, high).getOrThrow();
        assertEquals(PlayerData.MAX_SCORE, decodedHigh.getScore(),
                "out-of-range high score must clamp to MAX_SCORE on deserialization");

        JsonObject low = new JsonObject();
        low.addProperty("score", -99999);
        PlayerData decodedLow = PlayerData.CODEC.parse(JsonOps.INSTANCE, low).getOrThrow();
        assertEquals(PlayerData.MIN_SCORE, decodedLow.getScore(),
                "out-of-range low score must clamp to MIN_SCORE on deserialization");
    }

    @Test
    void tradeStatsCappedAtMax() {
        PlayerData data = new PlayerData();
        for (int i = 0; i < PlayerData.MAX_TRADE_STATS + 50; i++) {
            data.incrementTradesWithVillager(UUID.randomUUID());
        }
        assertEquals(PlayerData.MAX_TRADE_STATS, data.getTradeStats().size(),
                "tradeStats must not exceed MAX_TRADE_STATS");
    }

    @Test
    void tradeStatsCapSurvivesRoundTrip() {
        PlayerData original = new PlayerData();
        List<UUID> inserted = new ArrayList<>();
        for (int i = 0; i < PlayerData.MAX_TRADE_STATS; i++) {
            UUID id = new UUID(0L, i);
            original.incrementTradesWithVillager(id);
            inserted.add(id);
        }
        assertEquals(PlayerData.MAX_TRADE_STATS, original.getTradeStats().size());

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(PlayerData.MAX_TRADE_STATS, decoded.getTradeStats().size(),
                "tradeStats cap must be preserved after codec round-trip");

        UUID overflow = new UUID(1L, 0L);
        decoded.incrementTradesWithVillager(overflow);
        assertEquals(PlayerData.MAX_TRADE_STATS, decoded.getTradeStats().size(),
                "adding beyond cap after round-trip must evict eldest");
        assertEquals(0, decoded.getTradesWithVillager(inserted.get(0)),
                "FIFO eviction must remove the first-inserted entry after round-trip");
        assertEquals(1, decoded.getTradesWithVillager(overflow),
                "newly added entry must be present with count 1");
    }

    @Test
    void tradeStatsConstructorClampsOversizedInput() {
        java.util.LinkedHashMap<UUID, Integer> oversized = new java.util.LinkedHashMap<>();
        for (int i = 0; i < PlayerData.MAX_TRADE_STATS + 50; i++) {
            oversized.put(UUID.randomUUID(), i + 1);
        }

        PlayerData data = PlayerData.builder()
                .tradeStats(oversized)
                .build();
        assertEquals(PlayerData.MAX_TRADE_STATS, data.getTradeStats().size(),
                "constructor must clamp oversized tradeStats input to MAX_TRADE_STATS");
    }

    @Test
    void defaultValuesForDailyFields() {
        PlayerData data = new PlayerData();
        assertEquals(0, data.getDailyReputationEarned());
        assertEquals(-1L, data.getLastCapResetDay());
        assertEquals(0, data.getDailyTradeRep());
        assertEquals(0, data.getDailyCycleRep());
        assertEquals(0, data.getTradesSinceLastRepGain());
        assertFalse(data.isDailyCapNotified());
    }

    @Test
    void dailyCapNotifiedRoundTripsAndResetsOnRollover() {
        PlayerData original = new PlayerData();
        original.resetDailyCounters(5L);
        original.setDailyCapNotified(true);

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.isDailyCapNotified(),
                "dailyCapNotified flag must survive a codec round-trip");

        decoded.resetDailyCounters(6L);
        assertFalse(decoded.isDailyCapNotified(),
                "resetDailyCounters must clear the notified flag so a new day can re-notify");
    }

    @Test
    void roundTripWithDailyFields() {
        PlayerData original = PlayerData.builder()
                .score(100)
                .proximityTicks(4000)
                .lastProximityDay(7L)
                .dailyReputationEarned(3)
                .lastCapResetDay(12L)
                .dailyTradeRep(2)
                .dailyCycleRep(1)
                .tradesSinceLastRepGain(4)
                .build();

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(3, decoded.getDailyReputationEarned());
        assertEquals(12L, decoded.getLastCapResetDay());
        assertEquals(2, decoded.getDailyTradeRep());
        assertEquals(1, decoded.getDailyCycleRep());
        assertEquals(4, decoded.getTradesSinceLastRepGain());
    }

    @Test
    void preS040SaveDeserializesWithDailyDefaults() {
        // Simulate a saved PlayerData from before S-040 (no daily-cap keys).
        JsonObject legacy = new JsonObject();
        legacy.addProperty("score", 250);
        legacy.addProperty("proximityTicks", 1000);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();

        assertEquals(250, decoded.getScore());
        assertEquals(0, decoded.getDailyReputationEarned());
        assertEquals(-1L, decoded.getLastCapResetDay());
        assertEquals(0, decoded.getDailyTradeRep());
        assertEquals(0, decoded.getDailyCycleRep());
        assertEquals(0, decoded.getTradesSinceLastRepGain());
    }

    @Test
    void resetDailyCountersZeroesAllAndSetsDay() {
        PlayerData data = PlayerData.builder()
                .score(500)
                .dailyReputationEarned(5)
                .lastCapResetDay(3L)
                .dailyTradeRep(2)
                .dailyCycleRep(1)
                .tradesSinceLastRepGain(3)
                .build();

        data.resetDailyCounters(42L);

        assertEquals(42L, data.getLastCapResetDay());
        assertEquals(0, data.getDailyReputationEarned());
        assertEquals(0, data.getDailyTradeRep());
        assertEquals(0, data.getDailyCycleRep());
        assertEquals(0, data.getTradesSinceLastRepGain());
        assertEquals(500, data.getScore(), "resetDailyCounters must not touch the persistent score");
    }

    @Test
    void addDailyTradeAndCycleIncrementBothCounters() {
        PlayerData data = new PlayerData();
        data.addDailyTradeRep(1);
        assertEquals(1, data.getDailyTradeRep());
        assertEquals(1, data.getDailyReputationEarned());

        data.addDailyCycleRep(1);
        assertEquals(1, data.getDailyCycleRep());
        assertEquals(2, data.getDailyReputationEarned());
    }

    @Test
    void reputationMigratedDefaultsFalseOnLegacyJson() {
        // Legacy JSON (no reputationMigrated key) must decode with the flag set to false
        // so the migration helper runs once on first JOIN.
        JsonObject legacy = new JsonObject();
        legacy.addProperty("score", 80);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertFalse(decoded.isReputationMigrated());
    }

    @Test
    void reputationMigratedRoundTrips() {
        PlayerData original = new PlayerData();
        original.setScore(305);
        original.setReputationMigrated(true);

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.isReputationMigrated(),
                "reputationMigrated flag must survive a codec round-trip");
        assertEquals(305, decoded.getScore());
    }

    @Test
    void fearByVillageRoundTrips() {
        PlayerData original = new PlayerData();
        FearEntry entry = original.getOrCreateFearEntry("minecraft:overworld@1,64,2");
        entry.setRecentKillTimes(List.of(100L, 200L, 300L));
        entry.setFearStartGameTime(300L);
        original.getOrCreateFearEntry("minecraft:overworld@9,70,9");

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(2, decoded.getFearByVillage().size());
        FearEntry decodedEntry = decoded.getFearEntry("minecraft:overworld@1,64,2");
        assertNotNull(decodedEntry);
        assertEquals(List.of(100L, 200L, 300L), decodedEntry.getRecentKillTimes());
        assertEquals(300L, decodedEntry.getFearStartGameTime());

        FearEntry blank = decoded.getFearEntry("minecraft:overworld@9,70,9");
        assertNotNull(blank);
        assertTrue(blank.getRecentKillTimes().isEmpty());
        assertEquals(-1L, blank.getFearStartGameTime(),
                "the never-activated sentinel must survive a round-trip");
    }

    @Test
    void legacySaveWithoutFearFieldDecodesEmpty() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("score", 80);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertFalse(decoded.hasFearEntries());
    }

    @Test
    void fearByVillageCappedAtMax() {
        PlayerData data = new PlayerData();
        for (int i = 0; i < PlayerData.MAX_FEAR_VILLAGES + 20; i++) {
            data.getOrCreateFearEntry("village-" + i);
        }
        assertEquals(PlayerData.MAX_FEAR_VILLAGES, data.getFearByVillage().size(),
                "fearByVillage must not exceed MAX_FEAR_VILLAGES");
    }

    @Test
    void fearEvictionSparesActiveEntries() {
        // An active markup must not be laundered out of the map by touching many other
        // villages: eviction picks the least-recently-active entry, not insertion order.
        PlayerData data = new PlayerData();
        FearEntry active = data.getOrCreateFearEntry("feared-village");
        active.setRecentKillTimes(List.of(9_000L, 9_100L, 9_200L));
        active.setFearStartGameTime(9_200L);

        for (int i = 0; i < PlayerData.MAX_FEAR_VILLAGES + 10; i++) {
            FearEntry blank = data.getOrCreateFearEntry("decoy-" + i);
            blank.setRecentKillTimes(List.of((long) i));
        }

        assertEquals(PlayerData.MAX_FEAR_VILLAGES, data.getFearByVillage().size());
        assertNotNull(data.getFearEntry("feared-village"),
                "the entry with the most recent activity must survive eviction");
        assertEquals(9_200L, data.getFearEntry("feared-village").getFearStartGameTime());
    }

    @Test
    void dailyCountersNestUnderSingleKey() {
        PlayerData original = new PlayerData();
        original.addDailyTradeRep(2);

        JsonObject encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow().getAsJsonObject();
        assertTrue(encoded.has("dailyCounters"), "daily counters must serialize as a nested sub-record");
        assertFalse(encoded.has("dailyTradeRep"), "legacy flat daily keys must no longer be written");
    }

    @Test
    void preDailyCountersSaveDecodesWithFreshDay() {
        // A save from before the DailyCounters extraction carries flat daily keys; they are
        // intentionally dropped (at most one in-progress day of cap tracking resets).
        JsonObject legacy = new JsonObject();
        legacy.addProperty("score", 90);
        legacy.addProperty("dailyTradeRep", 7);
        legacy.addProperty("lastCapResetDay", 11L);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(90, decoded.getScore(), "non-daily fields must survive");
        assertEquals(0, decoded.getDailyTradeRep());
        assertEquals(-1L, decoded.getLastCapResetDay(),
                "stale flat daily keys decode to defaults so the next day rollover starts fresh");
    }

    @Test
    void decodedInstancesDoNotShareDailyCounters() {
        // The codec's optionalFieldOf default is a single shared DailyCounters instance;
        // the PlayerData constructor must copy it or every legacy-save player aliases
        // the same counters. This pins that load-bearing copy.
        PlayerData first = PlayerData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        PlayerData second = PlayerData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        first.addDailyTradeRep(3);

        assertEquals(3, first.getDailyTradeRep());
        assertEquals(0, second.getDailyTradeRep(),
                "mutating one decoded PlayerData must not leak into another");
    }

    @Test
    void pinnedTradesRoundTrip() {
        UUID villager = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PlayerData original = new PlayerData();
        assertTrue(original.addPinnedTrade(new PinnedTrade(villager, "hash-a", "Aldric", "1 Emerald -> Mending I")));
        assertTrue(original.addPinnedTrade(new PinnedTrade(villager, "hash-b", "Aldric", "32 Stick -> 1 Emerald")));

        JsonElement encoded = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(2, decoded.getPinnedTrades().size());
        assertTrue(decoded.isTradePinned(villager, "hash-a"));
        assertTrue(decoded.isTradePinned(villager, "hash-b"));
        PinnedTrade first = decoded.getPinnedTrades().get(0);
        assertEquals("Aldric", first.villagerName());
        assertEquals("1 Emerald -> Mending I", first.tradeSummary());
    }

    @Test
    void legacySaveWithoutPinnedTradesDecodesEmpty() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("score", 80);

        PlayerData decoded = PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertTrue(decoded.getPinnedTrades().isEmpty());
    }

    @Test
    void pinnedTradesDeduplicateAndRemove() {
        UUID villager = UUID.randomUUID();
        PlayerData data = new PlayerData();
        assertTrue(data.addPinnedTrade(new PinnedTrade(villager, "hash-a", "Aldric", "x")));
        assertFalse(data.addPinnedTrade(new PinnedTrade(villager, "hash-a", "Aldric", "x")),
                "duplicate (villager, offer) pins must be rejected");
        assertEquals(1, data.getPinnedTrades().size());

        assertTrue(data.removePinnedTrade(villager, "hash-a"));
        assertFalse(data.removePinnedTrade(villager, "hash-a"));
        assertTrue(data.getPinnedTrades().isEmpty());
    }

    @Test
    void pinnedTradesPruneByVillagerAndClear() {
        UUID dead = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        PlayerData data = new PlayerData();
        data.addPinnedTrade(new PinnedTrade(dead, "hash-a", "A", ""));
        data.addPinnedTrade(new PinnedTrade(dead, "hash-b", "A", ""));
        data.addPinnedTrade(new PinnedTrade(alive, "hash-c", "B", ""));

        assertEquals(2, data.removePinnedTradesFor(dead));
        assertEquals(1, data.getPinnedTrades().size());
        assertTrue(data.isTradePinned(alive, "hash-c"));

        assertEquals(1, data.clearPinnedTrades());
        assertTrue(data.getPinnedTrades().isEmpty());
    }

    @Test
    void pinnedTradesHardBoundClampsOnConstructAndAdd() {
        List<PinnedTrade> oversized = new ArrayList<>();
        for (int i = 0; i < PlayerData.MAX_PINNED_TRADES + 10; i++) {
            oversized.add(new PinnedTrade(new UUID(0L, i), "hash-" + i, "V" + i, ""));
        }

        PlayerData data = PlayerData.builder().pinnedTrades(oversized).build();
        assertEquals(PlayerData.MAX_PINNED_TRADES, data.getPinnedTrades().size(),
                "constructor must clamp oversized pin lists to MAX_PINNED_TRADES");
        assertFalse(data.isTradePinned(new UUID(0L, 0L), "hash-0"),
                "oldest pins must evict first");
        assertFalse(data.addPinnedTrade(new PinnedTrade(UUID.randomUUID(), "hash-x", "", "")),
                "adds at the hard bound must be rejected");
    }

    @Test
    void pinnedTradeSnapshotsClampLength() {
        String longHash = "h".repeat(PinnedTrade.MAX_HASH_LENGTH + 50);
        String longName = "n".repeat(PinnedTrade.MAX_NAME_LENGTH + 50);
        String longSummary = "s".repeat(PinnedTrade.MAX_SUMMARY_LENGTH + 50);
        PinnedTrade pin = new PinnedTrade(UUID.randomUUID(), longHash, longName, longSummary);
        assertEquals(PinnedTrade.MAX_HASH_LENGTH, pin.offerHash().length());
        assertEquals(PinnedTrade.MAX_NAME_LENGTH, pin.villagerName().length());
        assertEquals(PinnedTrade.MAX_SUMMARY_LENGTH, pin.tradeSummary().length());
    }

    @Test
    void incrementTradesMovesEntryToEndForLruEviction() {
        // Fill to cap, then increment the first-inserted entry — it should move to end
        // so the *second*-inserted entry becomes the eviction candidate on overflow.
        PlayerData data = new PlayerData();
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        data.incrementTradesWithVillager(first);
        data.incrementTradesWithVillager(second);
        for (int i = 3; i <= PlayerData.MAX_TRADE_STATS; i++) {
            data.incrementTradesWithVillager(new UUID(0L, i));
        }
        assertEquals(PlayerData.MAX_TRADE_STATS, data.getTradeStats().size());

        // Touch `first` again — move to end (LRU).
        data.incrementTradesWithVillager(first);
        assertEquals(2, data.getTradesWithVillager(first));

        // New entry should now evict `second`, not `first`.
        UUID overflow = new UUID(1L, 0L);
        data.incrementTradesWithVillager(overflow);
        assertEquals(2, data.getTradesWithVillager(first),
                "Touched entry must survive eviction");
        assertEquals(0, data.getTradesWithVillager(second),
                "Eldest non-touched entry must be evicted");
        assertEquals(1, data.getTradesWithVillager(overflow));
    }
}
