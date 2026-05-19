package com.rfizzle.mercantile.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VillagerDataCodecTest {
    @Test
    void defaultValues() {
        VillagerData data = new VillagerData();
        assertFalse(data.isProfessionLocked());
        assertTrue(data.getLockedTrades().isEmpty());
        assertFalse(data.isNameAssigned());
    }

    @Test
    void roundTrip() {
        VillagerData original = new VillagerData();
        original.setProfessionLocked(true);
        original.addLockedTrade("hash_abc");
        original.addLockedTrade("hash_def");
        original.setNameAssigned(true);

        JsonElement encoded = VillagerData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        VillagerData decoded = VillagerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.isProfessionLocked());
        assertEquals(2, decoded.getLockedTrades().size());
        assertTrue(decoded.isTradeLocked("hash_abc"));
        assertTrue(decoded.isTradeLocked("hash_def"));
        assertTrue(decoded.isNameAssigned());
    }

    @Test
    void missingKeysGetDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("professionLocked", true);

        VillagerData decoded = VillagerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(decoded.isProfessionLocked());
        assertTrue(decoded.getLockedTrades().isEmpty());
        assertFalse(decoded.isNameAssigned());
    }

    @Test
    void emptyJsonUsesDefaults() {
        VillagerData decoded = VillagerData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        assertFalse(decoded.isProfessionLocked());
        assertTrue(decoded.getLockedTrades().isEmpty());
        assertFalse(decoded.isNameAssigned());
    }

    @Test
    void constructorWithValues() {
        VillagerData data = new VillagerData(true, Set.of("h1", "h2"), true);

        assertTrue(data.isProfessionLocked());
        assertEquals(2, data.getLockedTrades().size());
        assertTrue(data.isTradeLocked("h1"));
        assertTrue(data.isTradeLocked("h2"));
        assertTrue(data.isNameAssigned());
    }

    @Test
    void lockedTradeTracking() {
        VillagerData data = new VillagerData();

        assertFalse(data.isTradeLocked("hash1"));
        assertTrue(data.addLockedTrade("hash1"));
        assertTrue(data.isTradeLocked("hash1"));
        assertFalse(data.addLockedTrade("hash1"));
        assertTrue(data.addLockedTrade("hash2"));
        assertEquals(2, data.getLockedTrades().size());
    }

    @Test
    void unmodifiableLockedTrades() {
        VillagerData data = new VillagerData();
        assertThrows(UnsupportedOperationException.class,
                () -> data.getLockedTrades().add("sneaky"));
    }
}
