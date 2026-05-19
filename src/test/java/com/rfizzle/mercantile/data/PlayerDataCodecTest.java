package com.rfizzle.mercantile.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

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
        PlayerData data = new PlayerData(100, 3000, Set.of(cured), Map.of(trader, 5));

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
}
