package com.rfizzle.mercantile.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MercantileVillagerDataCodecTest {
    @Test
    void defaultValues() {
        MercantileVillagerData data = new MercantileVillagerData();
        assertFalse(data.isProfessionLocked());
        assertTrue(data.getLockedTrades().isEmpty());
        assertFalse(data.isNameAssigned());
        assertFalse(data.isHealBoosted());
    }

    @Test
    void roundTrip() {
        MercantileVillagerData original = new MercantileVillagerData();
        original.setProfessionLocked(true);
        original.addLockedTrade("hash_abc");
        original.addLockedTrade("hash_def");
        original.setNameAssigned(true);

        JsonElement encoded = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        MercantileVillagerData decoded = MercantileVillagerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.isProfessionLocked());
        assertEquals(2, decoded.getLockedTrades().size());
        assertTrue(decoded.isTradeLocked("hash_abc"));
        assertTrue(decoded.isTradeLocked("hash_def"));
        assertTrue(decoded.isNameAssigned());
        assertFalse(decoded.isHealBoosted());
    }

    @Test
    void missingKeysGetDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("professionLocked", true);

        MercantileVillagerData decoded = MercantileVillagerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(decoded.isProfessionLocked());
        assertTrue(decoded.getLockedTrades().isEmpty());
        assertFalse(decoded.isNameAssigned());
        assertFalse(decoded.isHealBoosted());
    }

    @Test
    void emptyJsonUsesDefaults() {
        MercantileVillagerData decoded = MercantileVillagerData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        assertFalse(decoded.isProfessionLocked());
        assertTrue(decoded.getLockedTrades().isEmpty());
        assertFalse(decoded.isNameAssigned());
        assertFalse(decoded.isHealBoosted());
    }

    @Test
    void constructorWithValues() {
        MercantileVillagerData data = new MercantileVillagerData(true, Set.of("h1", "h2"), true, false, false);

        assertTrue(data.isProfessionLocked());
        assertEquals(2, data.getLockedTrades().size());
        assertTrue(data.isTradeLocked("h1"));
        assertTrue(data.isTradeLocked("h2"));
        assertTrue(data.isNameAssigned());
    }

    @Test
    void lockedTradeTracking() {
        MercantileVillagerData data = new MercantileVillagerData();

        assertFalse(data.isTradeLocked("hash1"));
        assertTrue(data.addLockedTrade("hash1"));
        assertTrue(data.isTradeLocked("hash1"));
        assertFalse(data.addLockedTrade("hash1"));
        assertTrue(data.addLockedTrade("hash2"));
        assertEquals(2, data.getLockedTrades().size());
    }

    @Test
    void unmodifiableLockedTrades() {
        MercantileVillagerData data = new MercantileVillagerData();
        assertThrows(UnsupportedOperationException.class,
                () -> data.getLockedTrades().add("sneaky"));
    }

    @Test
    void lockedTradesSerializedSorted() {
        MercantileVillagerData data = new MercantileVillagerData();
        data.addLockedTrade("zzz");
        data.addLockedTrade("aaa");
        data.addLockedTrade("mmm");

        JsonElement encoded = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        JsonArray lockedTradesArr = encoded.getAsJsonObject().getAsJsonArray("lockedTrades");

        assertEquals(3, lockedTradesArr.size());
        assertEquals("aaa", lockedTradesArr.get(0).getAsString());
        assertEquals("mmm", lockedTradesArr.get(1).getAsString());
        assertEquals("zzz", lockedTradesArr.get(2).getAsString());
    }

    @Test
    void lockedTradesSerializationIsDeterministic() {
        // Two independently-built sets with the same contents must encode to byte-identical JSON.
        // This is the actual invariant that prevents non-deterministic chunk dirtying.
        MercantileVillagerData a = new MercantileVillagerData();
        for (String h : List.of("delta", "alpha", "charlie", "bravo")) a.addLockedTrade(h);

        MercantileVillagerData b = new MercantileVillagerData();
        for (String h : List.of("bravo", "charlie", "alpha", "delta")) b.addLockedTrade(h);

        // Sanity check: in-memory sets are equal but underlying HashSet iteration order may differ.
        assertEquals(((Set<String>) new HashSet<>(a.getLockedTrades())),
                ((Set<String>) new HashSet<>(b.getLockedTrades())));

        JsonElement encA = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        JsonElement encB = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow();

        assertEquals(encA, encB,
                "Identical sets must encode to byte-identical JSON to avoid spurious chunk-dirtying");
    }

    @Test
    void lockedTradesEmptySetSerializes() {
        MercantileVillagerData data = new MercantileVillagerData();
        JsonElement encoded = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        // Empty set is represented by lockedTrades being absent (optionalFieldOf default) — assert no crash on round-trip.
        MercantileVillagerData decoded = MercantileVillagerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(decoded.getLockedTrades().isEmpty());
    }

    @Test
    void hashSetInputProducesSortedEncoding() {
        // Constructor uses HashSet internally; ensure encoding still sorts regardless of input set impl.
        Set<String> input = new LinkedHashSet<>();
        input.add("xray");
        input.add("alpha");
        input.add("november");

        MercantileVillagerData data = new MercantileVillagerData(false, input, false, false, false);
        JsonElement encoded = MercantileVillagerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        JsonArray arr = encoded.getAsJsonObject().getAsJsonArray("lockedTrades");

        assertEquals("alpha", arr.get(0).getAsString());
        assertEquals("november", arr.get(1).getAsString());
        assertEquals("xray", arr.get(2).getAsString());
    }
}
