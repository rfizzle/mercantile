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
        PlayerData data = new PlayerData(100, 3000, -1L, Set.of(cured), Map.of(trader, 5));

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

        PlayerData data = new PlayerData(0, 0, -1L, oversized, Map.of());
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

        PlayerData data = new PlayerData(0, 0, -1L, Set.of(), oversized);
        assertEquals(PlayerData.MAX_TRADE_STATS, data.getTradeStats().size(),
                "constructor must clamp oversized tradeStats input to MAX_TRADE_STATS");
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
